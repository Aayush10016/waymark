import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";
import {
  createUrlQuerySanitizer,
  defaultCaptureSanitationPolicy,
  LocalCapturePolicyAdapter,
  type SanitizedCaptureEvent,
  type SanitizedCaptureEventQueue,
  type SearchAllowlistEntry
} from "../src/services/capture-policy";

const corpus = loadCorpus();
const sanitizer = createUrlQuerySanitizer({
  policyVersion: corpus.policyVersion,
  searchAllowlist: corpus.searchAllowlist
});

describe("extension URL/query sanitizer", () => {
  it("uses the ENG-026 corpus policy as the runtime default", () => {
    expect(defaultCaptureSanitationPolicy).toEqual({
      policyVersion: corpus.policyVersion,
      searchAllowlist: corpus.searchAllowlist
    });
  });

  it.each(corpus.cases)("matches the shared ENG-026 fixture corpus for $id", (fixture) => {
    const decision = sanitizer.sanitize(fixture.input);

    expect(decision.decision).toBe(fixture.expected.decision);
    expect(decision.policyVersion).toBe(fixture.expected.policyVersion);

    if (fixture.expected.decision === "reject") {
      expect(decision).toMatchObject({
        rejectionReason: fixture.expected.rejectionReason
      });
      return;
    }

    if (decision.decision !== "allow") {
      throw new Error(`Expected fixture ${fixture.id} to be allowed.`);
    }

    expect(decision).toMatchObject({
      canonicalUrl: fixture.expected.canonicalUrl,
      hostname: fixture.expected.hostname,
      removedQueryParameters: fixture.expected.removedQueryParameters,
      searchQuery: fixture.expected.searchQuery
    });

    if (fixture.expected.searchEngine !== undefined) {
      expect(decision.searchEngine).toBe(fixture.expected.searchEngine);
    }

    if (fixture.expected.titleMaxLength !== undefined) {
      expect(decision.title?.length ?? 0).toBeLessThanOrEqual(fixture.expected.titleMaxLength);
    }
  });
});

describe("local capture policy adapter", () => {
  it("does not queue corpus events rejected by local policy", async () => {
    const queue = new InMemorySanitizedCaptureEventQueue();
    const adapter = new LocalCapturePolicyAdapter(sanitizer, queue);
    const rejectedFixtures = corpus.cases.filter((fixture) => fixture.expected.decision === "reject");

    for (const fixture of rejectedFixtures) {
      const result = await adapter.sanitizeAndEnqueue(fixture.input);
      expect(result.queued).toBe(false);
    }

    expect(queue.events()).toEqual([]);
  });

  it("queues only sanitized events and records the policy version", async () => {
    const queue = new InMemorySanitizedCaptureEventQueue();
    const adapter = new LocalCapturePolicyAdapter(sanitizer, queue);
    const allowedFixture = corpus.cases.find(
      (fixture) => fixture.expected.decision === "allow" && fixture.expected.removedQueryParameters.length > 0
    );

    if (allowedFixture === undefined || allowedFixture.expected.decision !== "allow") {
      throw new Error("Sanitation corpus must include at least one allowed fixture.");
    }

    const result = await adapter.sanitizeAndEnqueue(allowedFixture.input);
    const queuedEvent = queue.events()[0];

    expect(result.queued).toBe(true);
    expect(queue.events()).toEqual([
      expect.objectContaining({
        canonicalUrl: allowedFixture.expected.canonicalUrl,
        hostname: allowedFixture.expected.hostname,
        policyVersion: corpus.policyVersion
      })
    ]);
    expect(queuedEvent?.canonicalUrl).not.toContain("#");
    expect(queuedEvent?.canonicalUrl).not.toContain("utm_");
  });
});

class InMemorySanitizedCaptureEventQueue implements SanitizedCaptureEventQueue {
  private readonly queuedEvents: SanitizedCaptureEvent[] = [];

  enqueue(event: SanitizedCaptureEvent): Promise<void> {
    this.queuedEvents.push(event);
    return Promise.resolve();
  }

  events(): readonly SanitizedCaptureEvent[] {
    return this.queuedEvents;
  }
}

interface UrlQuerySanitationCorpus {
  readonly corpusVersion: number;
  readonly policyVersion: string;
  readonly searchAllowlist: readonly SearchAllowlistEntry[];
  readonly cases: readonly UrlQuerySanitationFixture[];
}

interface UrlQuerySanitationFixture {
  readonly id: string;
  readonly input: {
    readonly url: string;
    readonly title: string;
  };
  readonly expected: UrlQuerySanitationExpectedDecision;
}

type UrlQuerySanitationExpectedDecision = UrlQuerySanitationExpectedAllowedDecision | UrlQuerySanitationExpectedRejectedDecision;

interface UrlQuerySanitationExpectedAllowedDecision {
  readonly decision: "allow";
  readonly canonicalUrl: string;
  readonly hostname: string;
  readonly policyVersion: string;
  readonly removedQueryParameters: readonly string[];
  readonly searchEngine?: string;
  readonly searchQuery: string | null;
  readonly titleMaxLength?: number;
}

interface UrlQuerySanitationExpectedRejectedDecision {
  readonly decision: "reject";
  readonly policyVersion: string;
  readonly rejectionReason: string;
}

function loadCorpus(): UrlQuerySanitationCorpus {
  const corpusPath = resolve(process.cwd(), "..", "..", "contracts", "fixtures", "url-query-sanitation", "v1", "corpus.json");
  const parsedCorpus: unknown = JSON.parse(readFileSync(corpusPath, "utf8"));

  if (!isCorpus(parsedCorpus)) {
    throw new Error("URL/query sanitation corpus does not match the expected ENG-026 contract.");
  }

  return parsedCorpus;
}

function isCorpus(value: unknown): value is UrlQuerySanitationCorpus {
  if (!isRecord(value)) {
    return false;
  }

  return (
    typeof value.corpusVersion === "number" &&
    typeof value.policyVersion === "string" &&
    Array.isArray(value.searchAllowlist) &&
    value.searchAllowlist.every(isSearchAllowlistEntry) &&
    Array.isArray(value.cases) &&
    value.cases.every(isFixture)
  );
}

function isSearchAllowlistEntry(value: unknown): value is SearchAllowlistEntry {
  if (!isRecord(value)) {
    return false;
  }

  return (
    typeof value.engine === "string" &&
    isStringArray(value.hosts) &&
    isStringArray(value.pathPrefixes) &&
    isStringArray(value.queryParameters)
  );
}

function isFixture(value: unknown): value is UrlQuerySanitationFixture {
  if (!isRecord(value) || !isRecord(value.input) || !isRecord(value.expected)) {
    return false;
  }

  return (
    typeof value.id === "string" &&
    typeof value.input.url === "string" &&
    typeof value.input.title === "string" &&
    isExpectedDecision(value.expected)
  );
}

function isExpectedDecision(value: unknown): value is UrlQuerySanitationExpectedDecision {
  if (!isRecord(value)) {
    return false;
  }

  if (value.decision === "reject") {
    return typeof value.policyVersion === "string" && typeof value.rejectionReason === "string";
  }

  return (
    value.decision === "allow" &&
    typeof value.canonicalUrl === "string" &&
    typeof value.hostname === "string" &&
    typeof value.policyVersion === "string" &&
    Array.isArray(value.removedQueryParameters) &&
    value.removedQueryParameters.every((parameter) => typeof parameter === "string") &&
    (typeof value.searchQuery === "string" || value.searchQuery === null) &&
    (value.searchEngine === undefined || typeof value.searchEngine === "string") &&
    (value.titleMaxLength === undefined || typeof value.titleMaxLength === "number")
  );
}

function isStringArray(value: unknown): value is readonly string[] {
  return Array.isArray(value) && value.every((item) => typeof item === "string");
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}
