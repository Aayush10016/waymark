import { describe, expect, it } from "vitest";
import {
  RECALL_MESSAGE_TYPES,
  parseRecallRequestMessage,
  parseRecallSearchResponseMessage,
  parseRecallResponseMessage,
  recallSearchResponse,
  recallResponse,
  type RecallStatePayload
} from "../src/recall";

describe("recall extension messages", () => {
  it("accepts valid invoke and get-state request messages", () => {
    expect(
      parseRecallRequestMessage({
        schemaVersion: 1,
        type: RECALL_MESSAGE_TYPES.invoke,
        requestId: "recall-1",
        source: "popup",
        payload: { source: "popup" }
      })
    ).toMatchObject({ ok: true });

    expect(
      parseRecallRequestMessage({
        schemaVersion: 1,
        type: RECALL_MESSAGE_TYPES.getState,
        requestId: "recall-2",
        source: "sidepanel",
        payload: {}
      })
    ).toMatchObject({ ok: true });

    expect(
      parseRecallRequestMessage({
        schemaVersion: 1,
        type: RECALL_MESSAGE_TYPES.openResource,
        requestId: "recall-3",
        source: "sidepanel",
        payload: { resourceId: "77000000-0000-4000-8000-000000000004" }
      })
    ).toMatchObject({ ok: true });

    expect(
      parseRecallRequestMessage({
        schemaVersion: 1,
        type: RECALL_MESSAGE_TYPES.search,
        requestId: "recall-4",
        source: "sidepanel",
        payload: { query: "spring cors" }
      })
    ).toMatchObject({ ok: true });

    expect(
      parseRecallRequestMessage({
        schemaVersion: 1,
        type: RECALL_MESSAGE_TYPES.openSolvedProblem,
        requestId: "recall-5",
        source: "sidepanel",
        payload: {
          result: {
            errorPhrases: [],
            evidenceCodes: [],
            finalSolution: "JWT on gpt",
            problemSessionId: "77000000-0000-4000-8000-000000000020",
            technologies: [],
            title: "JWT issue",
            updatedAt: "2026-07-31T08:05:00.000Z"
          }
        }
      })
    ).toMatchObject({ ok: true });
  });

  it("rejects malformed recall request messages", () => {
    expect(
      parseRecallRequestMessage({
        schemaVersion: 1,
        type: RECALL_MESSAGE_TYPES.invoke,
        requestId: "recall-1",
        source: "popup",
        payload: { source: "content-script" }
      })
    ).toEqual({ ok: false, errorCode: "INVALID_MESSAGE_SCHEMA" });

    expect(
      parseRecallRequestMessage({
        schemaVersion: 1,
        type: RECALL_MESSAGE_TYPES.openResource,
        requestId: "recall-2",
        source: "sidepanel",
        payload: { resourceId: "not-a-uuid" }
      })
    ).toEqual({ ok: false, errorCode: "INVALID_MESSAGE_SCHEMA" });

    expect(
      parseRecallRequestMessage({
        schemaVersion: 1,
        type: RECALL_MESSAGE_TYPES.search,
        requestId: "recall-3",
        source: "sidepanel",
        payload: { query: "x".repeat(241) }
      })
    ).toEqual({ ok: false, errorCode: "INVALID_MESSAGE_SCHEMA" });

    expect(
      parseRecallRequestMessage({
        schemaVersion: 1,
        type: RECALL_MESSAGE_TYPES.openSolvedProblem,
        requestId: "recall-4",
        source: "sidepanel",
        payload: {
          result: {
            errorPhrases: [],
            evidenceCodes: [],
            finalSolution: "",
            problemSessionId: "77000000-0000-4000-8000-000000000020",
            technologies: [],
            updatedAt: "2026-07-31T08:05:00.000Z"
          }
        }
      })
    ).toEqual({ ok: false, errorCode: "INVALID_MESSAGE_SCHEMA" });
  });

  it("round-trips bounded recall route responses", () => {
    const request = parseRecallRequestMessage({
      schemaVersion: 1,
      type: RECALL_MESSAGE_TYPES.invoke,
      requestId: "recall-1",
      source: "popup",
      payload: { source: "popup" }
    });
    if (!request.ok) {
      throw new Error("expected valid request");
    }

    const response = recallResponse(request.message, similarState());

    expect(parseRecallResponseMessage(response)).toMatchObject({ ok: true });

    expect(parseRecallResponseMessage(recallResponse(request.message, solvedProblemState()))).toMatchObject({ ok: true });
  });

  it("round-trips bounded recall search responses", () => {
    const request = parseRecallRequestMessage({
      schemaVersion: 1,
      type: RECALL_MESSAGE_TYPES.search,
      requestId: "recall-search-1",
      source: "sidepanel",
      payload: { query: "spring cors" }
    });
    if (!request.ok) {
      throw new Error("expected valid request");
    }

    const response = recallSearchResponse(request.message, {
      degradationMode: "LEXICAL",
      query: "spring cors",
      results: [
        {
          errorPhrases: ["cors"],
          evidenceCodes: ["ERROR_PHRASE"],
          finalSolution: "Allowed localhost in CorsConfiguration.",
          problemSessionId: "77000000-0000-4000-8000-000000000004",
          technologies: ["Spring Security"],
          title: "Spring CORS",
          updatedAt: "2026-07-13T08:05:00.000Z"
        }
      ],
      status: "received"
    });

    expect(parseRecallSearchResponseMessage(response)).toMatchObject({ ok: true });
  });
});

function similarState(): RecallStatePayload {
  return {
    commandStatus: "matched",
    invokedAt: "2026-07-13T08:00:00.000Z",
    route: {
      currentProblemSessionId: "77000000-0000-4000-8000-000000000001",
      recommendation: {
        algorithmVersion: "hybrid-similarity-v1",
        durationSeconds: 8040,
        evidence: [{ evidenceCode: "ERROR_PHRASE", problemSessionId: "77000000-0000-4000-8000-000000000003" }],
        finalSolution: "Allowed localhost in CorsConfiguration.",
        recommendationId: "77000000-0000-4000-8000-000000000002",
        recommendedProblemSessionId: "77000000-0000-4000-8000-000000000003",
        resources: [
          {
            alreadyVisited: true,
            decisive: true,
            hostname: "docs.spring.io",
            id: "77000000-0000-4000-8000-000000000004",
            kind: "DOCUMENTATION",
            occurredAt: "2026-01-11T08:05:00.000Z",
            openedNow: false,
            title: "Spring CORS docs",
            url: "https://docs.spring.io/cors"
          }
        ],
        solvedAt: "2026-01-11T08:00:00.000Z",
        strength: "STRONG",
        title: "Spring Security CORS",
        whyThis: [
          {
            evidenceCode: "ERROR_PHRASE",
            message: "Both problems mention CORS.",
            problemSessionId: "77000000-0000-4000-8000-000000000003"
          }
        ]
      },
      recommendationId: "77000000-0000-4000-8000-000000000002",
      recommendedProblemSessionId: "77000000-0000-4000-8000-000000000003",
      route: "similar_problem"
    },
    source: "popup"
  };
}

function solvedProblemState(): RecallStatePayload {
  return {
    commandStatus: "opened",
    invokedAt: "2026-07-31T08:00:00.000Z",
    route: {
      finalSolution: "JWT on gpt",
      problemSessionId: "77000000-0000-4000-8000-000000000020",
      resources: [
        {
          alreadyVisited: false,
          decisive: false,
          hostname: "chatgpt.com",
          id: "77000000-0000-4000-8000-000000000030",
          kind: "AI_CHAT_LINK",
          occurredAt: "2026-07-31T08:05:00.000Z",
          openedNow: false,
          title: "ChatGPT",
          url: "https://chatgpt.com/"
        }
      ],
      route: "solved_problem",
      title: "JWT issue",
      updatedAt: "2026-07-31T08:05:00.000Z"
    },
    source: "popup"
  };
}
