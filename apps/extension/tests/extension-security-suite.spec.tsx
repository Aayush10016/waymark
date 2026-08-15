import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import {
  AUTH_MESSAGE_TYPES,
  CAPTURE_STATE_MESSAGE_TYPES,
  RECALL_MESSAGE_TYPES,
  RECALL_RESPONSE_TYPES,
  parseAuthRequestMessage,
  parseCaptureStateRequestMessage,
  parseRecallRequestMessage,
  parseRecallResponseMessage,
  parseRecallSearchResponseMessage
} from "@trail/extension-messages";
import { ProblemSummary, type ReviewableSessionArtifact } from "../src/sidepanel/problem-session";
import { SimilarProblemFoundCard } from "../src/sidepanel/recall";
import { ResourceReplayService, type ReplayResourceOpener } from "../src/services/resource-replay";

const SCRIPT_PAYLOAD = `<img src=x onerror="globalThis.__trailXss = true"><script>alert("xss")</script>`;
const JAVASCRIPT_URL = "javascript:alert(document.cookie)";
const VALID_ID = "77000000-0000-4000-8000-000000000003";

describe("extension XSS, message, and URL security suite", () => {
  it("renders malicious titles, solutions, and why-this messages as inert text", () => {
    const summaryMarkup = renderToStaticMarkup(
      <ProblemSummary
        artifacts={[artifact(SCRIPT_PAYLOAD)]}
        session={{ ...activeSession(), title: SCRIPT_PAYLOAD }}
        solution={SCRIPT_PAYLOAD}
      />
    );
    const recommendationMarkup = renderToStaticMarkup(
      <SimilarProblemFoundCard
        onReplay={() => undefined}
        recommendation={{
          algorithmVersion: "hybrid-similarity-v1",
          evidence: [{ evidenceCode: "LEXICAL_TEXT", problemSessionId: VALID_ID }],
          finalSolution: SCRIPT_PAYLOAD,
          recommendationId: "77000000-0000-4000-8000-000000000002",
          recommendedProblemSessionId: VALID_ID,
          strength: "STRONG",
          title: SCRIPT_PAYLOAD,
          whyThis: [{ evidenceCode: "LEXICAL_TEXT", message: SCRIPT_PAYLOAD, problemSessionId: VALID_ID }]
        }}
      />
    );

    for (const markup of [summaryMarkup, recommendationMarkup]) {
      expect(markup).toContain("&lt;script&gt;alert(&quot;xss&quot;)&lt;/script&gt;");
      expect(markup).not.toContain("<script>");
      expect(markup).not.toContain("onerror=\"");
      expect(markup).not.toContain("<img");
    }
  });

  it("rejects privileged runtime messages with invalid sources, versions, payload shape, or unknown fields", () => {
    expect(parseAuthRequestMessage(message(AUTH_MESSAGE_TYPES.getStatus, {}, "web-page")).ok).toBe(false);
    expect(parseCaptureStateRequestMessage({ ...message(CAPTURE_STATE_MESSAGE_TYPES.get, {}), schemaVersion: 2 }).ok)
      .toBe(false);
    expect(parseRecallRequestMessage(message(RECALL_MESSAGE_TYPES.invoke, { source: "popup", extra: true })).ok)
      .toBe(false);
    expect(parseRecallRequestMessage(message(RECALL_MESSAGE_TYPES.openResource, { resourceId: "not-a-uuid" })).ok)
      .toBe(false);
    expect(parseRecallSearchResponseMessage({
      ...message(RECALL_RESPONSE_TYPES.searchResult, {
        degradationMode: "LEXICAL",
        query: null,
        results: [{ finalSolution: SCRIPT_PAYLOAD }],
        status: "received"
      }),
      source: "background"
    }).ok).toBe(false);
  });

  it("accepts only schema-valid background recommendation responses with safe resource URLs", () => {
    const valid = parseRecallResponseMessage({
      ...message(RECALL_RESPONSE_TYPES.result, {
        commandStatus: "matched",
        invokedAt: "2026-07-20T10:00:00.000Z",
        route: {
          currentProblemSessionId: "77000000-0000-4000-8000-000000000001",
          recommendation: recommendation("https://docs.spring.io/cors"),
          recommendationId: "77000000-0000-4000-8000-000000000002",
          recommendedProblemSessionId: VALID_ID,
          route: "similar_problem"
        },
        source: "popup"
      }),
      source: "background"
    });
    const unsafeUrl = parseRecallResponseMessage({
      ...message(RECALL_RESPONSE_TYPES.result, {
        commandStatus: "matched",
        invokedAt: "2026-07-20T10:00:00.000Z",
        route: {
          currentProblemSessionId: "77000000-0000-4000-8000-000000000001",
          recommendation: recommendation(JAVASCRIPT_URL),
          recommendationId: "77000000-0000-4000-8000-000000000002",
          recommendedProblemSessionId: VALID_ID,
          route: "similar_problem"
        },
        source: "popup"
      }),
      source: "background"
    });

    expect(valid.ok).toBe(true);
    expect(unsafeUrl.ok).toBe(false);
  });

  it("keeps replay URL opening constrained to explicit sanitized HTTP(S) resources", async () => {
    const opener = new CapturingOpener();
    const service = new ResourceReplayService(opener);

    await expect(service.replay([{ id: "safe", sanitizedUrl: "https://docs.spring.io/cors" }], { confirmed: true }))
      .resolves.toEqual({
        openedCount: 1,
        openedUrls: ["https://docs.spring.io/cors"],
        status: "opened"
      });
    await expect(service.replay([{ id: "unsafe", sanitizedUrl: JAVASCRIPT_URL }], { confirmed: true }))
      .resolves.toEqual({
        code: "UNSAFE_URL",
        resourceId: "unsafe",
        status: "rejected"
      });
    expect(opener.opened()).toEqual(["https://docs.spring.io/cors"]);
  });
});

function message(type: string, payload: Record<string, unknown>, source = "popup") {
  return {
    payload,
    requestId: "security-suite-request",
    schemaVersion: 1,
    source,
    type
  };
}

function recommendation(url: string) {
  return {
    algorithmVersion: "hybrid-similarity-v1",
    evidence: [{ evidenceCode: "LEXICAL_TEXT", problemSessionId: VALID_ID }],
    finalSolution: "Allowed localhost in CorsConfiguration.",
    recommendationId: "77000000-0000-4000-8000-000000000002",
    recommendedProblemSessionId: VALID_ID,
    resources: [
      {
        alreadyVisited: false,
        decisive: true,
        hostname: "docs.spring.io",
        id: "77000000-0000-4000-8000-000000000004",
        kind: "DOCUMENTATION",
        occurredAt: "2026-07-20T09:00:00.000Z",
        openedNow: false,
        title: "Spring CORS docs",
        url
      }
    ],
    strength: "STRONG",
    title: "Spring Security CORS",
    whyThis: [{ evidenceCode: "LEXICAL_TEXT", message: "Similar problem text.", problemSessionId: VALID_ID }]
  };
}

function artifact(title: string): ReviewableSessionArtifact {
  return {
    artifactType: "DOCUMENTATION",
    hostname: "docs.spring.io",
    id: "artifact-1",
    occurredAt: "2026-07-20T09:00:00.000Z",
    title
  };
}

function activeSession() {
  return {
    detectionReasons: ["manual_session"],
    endedAt: "2026-07-20T10:00:00.000Z",
    etag: "\"2\"",
    id: "10000000-0000-4000-8000-000000000001",
    source: "MANUAL",
    startedAt: "2026-07-20T09:00:00.000Z",
    status: "SOLVED",
    title: "Spring Security CORS",
    updatedAt: "2026-07-20T10:00:00.000Z",
    version: 2
  } as const;
}

class CapturingOpener implements ReplayResourceOpener {
  private readonly urls: string[] = [];

  open(url: string): Promise<void> {
    this.urls.push(url);
    return Promise.resolve();
  }

  opened(): readonly string[] {
    return this.urls;
  }
}
