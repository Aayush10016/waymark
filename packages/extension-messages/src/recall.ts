import type { MessageEnvelope } from "./auth";

export const RECALL_MESSAGE_TYPES = {
  getState: "RECALL_GET_STATE",
  invoke: "RECALL_INVOKE",
  openResource: "RECALL_OPEN_RESOURCE",
  openSolvedProblem: "RECALL_OPEN_SOLVED_PROBLEM",
  search: "RECALL_SEARCH"
} as const;

export const RECALL_RESPONSE_TYPES = {
  result: "RECALL_RESULT",
  searchResult: "RECALL_SEARCH_RESULT"
} as const;

export type RecallInvocationSource = "command" | "popup" | "toolbar";
export type RecallRoute = "search" | "similar_problem" | "solved_problem";
export type RecallSearchReason =
  | "AUTHENTICATION_REQUIRED"
  | "NO_ACTIVE_PROBLEM"
  | "NO_MATCH"
  | "NOT_FOUND"
  | "UNAVAILABLE";
export type RecallCommandStatus =
  | "authentication_required"
  | "matched"
  | "no_active_problem"
  | "no_match"
  | "not_found"
  | "opened"
  | "unavailable";
export type RecallSearchStatus = "authentication_required" | "received" | "unavailable";

export type SimilarProblemEvidenceCode = "ERROR_PHRASE" | "LEXICAL_TEXT" | "TECHNOLOGY" | "VECTOR_DISTANCE";
export type SimilarProblemStrength = "POSSIBLE" | "STRONG";

export interface SimilarProblemEvidencePayload {
  readonly artifactId?: string | null;
  readonly evidenceCode: SimilarProblemEvidenceCode;
  readonly problemSessionId: string;
}

export interface SimilarProblemWhyThisPayload extends SimilarProblemEvidencePayload {
  readonly message: string;
}

export type GhostReplayResourceKind =
  | "AI_CHAT_LINK"
  | "DISCUSSION"
  | "DOCUMENTATION"
  | "GITHUB_ISSUE"
  | "GITHUB_REPOSITORY"
  | "PAGE"
  | "SEARCH_QUERY"
  | "USER_NOTE_REFERENCE"
  | "VIDEO";

export interface GhostReplayResourcePayload {
  readonly alreadyVisited: boolean;
  readonly decisive: boolean;
  readonly hostname?: string | null;
  readonly id: string;
  readonly kind: GhostReplayResourceKind;
  readonly occurredAt: string;
  readonly openedNow: boolean;
  readonly queryText?: string | null;
  readonly title?: string | null;
  readonly url?: string | null;
}

export interface SimilarProblemRecommendationPayload {
  readonly algorithmVersion: string;
  readonly durationSeconds?: number | null;
  readonly evidence: readonly SimilarProblemEvidencePayload[];
  readonly finalSolution: string;
  readonly recommendationId: string;
  readonly recommendedProblemSessionId: string;
  readonly resources?: readonly GhostReplayResourcePayload[];
  readonly solvedAt?: string | null;
  readonly strength: SimilarProblemStrength;
  readonly title?: string | null;
  readonly whyThis: readonly SimilarProblemWhyThisPayload[];
}

export interface RecallRecommendationRoutePayload {
  readonly currentProblemSessionId: string;
  readonly recommendation: SimilarProblemRecommendationPayload;
  readonly recommendationId: string;
  readonly recommendedProblemSessionId: string;
  readonly route: "similar_problem";
}

export interface RecallSearchRoutePayload {
  readonly reason: RecallSearchReason;
  readonly route: "search";
}

export interface RecallSolvedProblemRoutePayload {
  readonly finalSolution: string;
  readonly problemSessionId: string;
  readonly resources: readonly GhostReplayResourcePayload[];
  readonly route: "solved_problem";
  readonly title?: string | null;
  readonly updatedAt: string;
}

export type RecallRoutePayload = RecallRecommendationRoutePayload | RecallSearchRoutePayload | RecallSolvedProblemRoutePayload;

export interface RecallStatePayload {
  readonly commandStatus: RecallCommandStatus;
  readonly invokedAt: string | null;
  readonly route: RecallRoutePayload;
  readonly source: RecallInvocationSource | null;
}

export type RecallSearchEvidenceCode = "ERROR_PHRASE" | "LEXICAL_TEXT" | "TECHNOLOGY";
export type RecallSearchDegradationMode = "LEXICAL";

export interface RecallSearchResultPayload {
  readonly errorPhrases: readonly string[];
  readonly evidenceCodes: readonly RecallSearchEvidenceCode[];
  readonly finalSolution: string;
  readonly generatedSummary?: string | null;
  readonly problemSessionId: string;
  readonly problemType?: string | null;
  readonly technologies: readonly string[];
  readonly title?: string | null;
  readonly updatedAt: string;
}

export interface RecallSearchResultPayloadEnvelope {
  readonly degradationMode?: RecallSearchDegradationMode | null;
  readonly query: string | null;
  readonly results: readonly RecallSearchResultPayload[];
  readonly status: RecallSearchStatus;
}

export type RecallInvokeMessage = MessageEnvelope<
  typeof RECALL_MESSAGE_TYPES.invoke,
  {
    readonly source: RecallInvocationSource;
  }
>;
export type RecallGetStateMessage = MessageEnvelope<typeof RECALL_MESSAGE_TYPES.getState, Record<string, never>>;
export type RecallOpenResourceMessage = MessageEnvelope<
  typeof RECALL_MESSAGE_TYPES.openResource,
  {
    readonly resourceId: string;
  }
>;
export type RecallOpenSolvedProblemMessage = MessageEnvelope<
  typeof RECALL_MESSAGE_TYPES.openSolvedProblem,
  {
    readonly result: RecallSearchResultPayload;
  }
>;
export type RecallSearchMessage = MessageEnvelope<
  typeof RECALL_MESSAGE_TYPES.search,
  {
    readonly query: string | null;
  }
>;

export type RecallRequestMessage =
  | RecallGetStateMessage
  | RecallInvokeMessage
  | RecallOpenResourceMessage
  | RecallOpenSolvedProblemMessage
  | RecallSearchMessage;
export type RecallResponseMessage = MessageEnvelope<typeof RECALL_RESPONSE_TYPES.result, RecallStatePayload>;
export type RecallSearchResponseMessage = MessageEnvelope<
  typeof RECALL_RESPONSE_TYPES.searchResult,
  RecallSearchResultPayloadEnvelope
>;

export type RecallMessageParseResult =
  | { readonly ok: true; readonly message: RecallRequestMessage }
  | { readonly ok: false; readonly errorCode: "INVALID_MESSAGE_SCHEMA" };

export type RecallResponseParseResult =
  | { readonly ok: true; readonly message: RecallResponseMessage }
  | { readonly ok: false; readonly errorCode: "INVALID_MESSAGE_SCHEMA" };
export type RecallSearchResponseParseResult =
  | { readonly ok: true; readonly message: RecallSearchResponseMessage }
  | { readonly ok: false; readonly errorCode: "INVALID_MESSAGE_SCHEMA" };

export function parseRecallRequestMessage(input: unknown): RecallMessageParseResult {
  if (!isMessageEnvelope(input) || !isRecallRequestPayload(input.type, input.payload)) {
    return { ok: false, errorCode: "INVALID_MESSAGE_SCHEMA" };
  }

  return {
    ok: true,
    message: {
      schemaVersion: 1,
      type: input.type,
      requestId: input.requestId,
      source: input.source,
      payload:
        input.type === RECALL_MESSAGE_TYPES.invoke
          ? { source: input.payload.source as RecallInvocationSource }
          : input.type === RECALL_MESSAGE_TYPES.openResource
            ? { resourceId: input.payload.resourceId as string }
            : input.type === RECALL_MESSAGE_TYPES.openSolvedProblem
              ? { result: input.payload.result as RecallSearchResultPayload }
            : input.type === RECALL_MESSAGE_TYPES.search
              ? { query: input.payload.query === null ? null : (input.payload.query as string) }
          : {}
    } as RecallRequestMessage
  };
}

export function parseRecallResponseMessage(input: unknown): RecallResponseParseResult {
  if (!isMessageEnvelope(input) || input.type !== RECALL_RESPONSE_TYPES.result || !isRecallStatePayload(input.payload)) {
    return { ok: false, errorCode: "INVALID_MESSAGE_SCHEMA" };
  }

  return {
    ok: true,
    message: {
      schemaVersion: 1,
      type: RECALL_RESPONSE_TYPES.result,
      requestId: input.requestId,
      source: input.source,
      payload: input.payload
    }
  };
}

export function parseRecallSearchResponseMessage(input: unknown): RecallSearchResponseParseResult {
  if (
    !isMessageEnvelope(input) ||
    input.type !== RECALL_RESPONSE_TYPES.searchResult ||
    !isRecallSearchResultPayloadEnvelope(input.payload)
  ) {
    return { ok: false, errorCode: "INVALID_MESSAGE_SCHEMA" };
  }

  return {
    ok: true,
    message: {
      schemaVersion: 1,
      type: RECALL_RESPONSE_TYPES.searchResult,
      requestId: input.requestId,
      source: input.source,
      payload: input.payload
    }
  };
}

export function recallResponse(request: RecallRequestMessage, payload: RecallStatePayload): RecallResponseMessage {
  return {
    schemaVersion: 1,
    type: RECALL_RESPONSE_TYPES.result,
    requestId: request.requestId,
    source: "background",
    payload
  };
}

export function recallSearchResponse(
  request: RecallRequestMessage,
  payload: RecallSearchResultPayloadEnvelope
): RecallSearchResponseMessage {
  return {
    schemaVersion: 1,
    type: RECALL_RESPONSE_TYPES.searchResult,
    requestId: request.requestId,
    source: "background",
    payload
  };
}

export function defaultRecallState(): RecallStatePayload {
  return Object.freeze({
    commandStatus: "opened",
    invokedAt: null,
    route: Object.freeze({
      reason: "NO_ACTIVE_PROBLEM",
      route: "search"
    }),
    source: null
  });
}

function isRecallRequestPayload(type: unknown, payload: Record<string, unknown>): boolean {
  if (type === RECALL_MESSAGE_TYPES.getState) {
    return Object.keys(payload).length === 0;
  }

  if (type === RECALL_MESSAGE_TYPES.invoke) {
    return isRecallInvocationSource(payload.source) && Object.keys(payload).length === 1;
  }

  if (type === RECALL_MESSAGE_TYPES.openResource) {
    return isUuidLike(payload.resourceId) && Object.keys(payload).length === 1;
  }

  if (type === RECALL_MESSAGE_TYPES.openSolvedProblem) {
    return isRecallSearchResultPayload(payload.result) && Object.keys(payload).length === 1;
  }

  if (type === RECALL_MESSAGE_TYPES.search) {
    return (payload.query === null || isBoundedString(payload.query, 240)) && Object.keys(payload).length === 1;
  }

  return false;
}

function isRecallStatePayload(value: unknown): value is RecallStatePayload {
  return (
    isRecord(value) &&
    isRecallCommandStatus(value.commandStatus) &&
    (value.invokedAt === null || isRfc3339Instant(value.invokedAt)) &&
    isRecallRoutePayload(value.route) &&
    (value.source === null || isRecallInvocationSource(value.source)) &&
    Object.keys(value).length === 4
  );
}

function isRecallRoutePayload(value: unknown): value is RecallRoutePayload {
  if (!isRecord(value)) {
    return false;
  }

  if (value.route === "search") {
    return isRecallSearchReason(value.reason) && Object.keys(value).length === 2;
  }

  if (value.route === "solved_problem") {
    return (
      isNonBlankString(value.finalSolution) &&
      isUuidLike(value.problemSessionId) &&
      Array.isArray(value.resources) &&
      value.resources.length <= 100 &&
      value.resources.every(isGhostReplayResourcePayload) &&
      (typeof value.title === "string" || value.title === null || value.title === undefined) &&
      isRfc3339Instant(value.updatedAt) &&
      Object.keys(value).every((key) =>
        ["finalSolution", "problemSessionId", "resources", "route", "title", "updatedAt"].includes(key)
      )
    );
  }

  return (
    value.route === "similar_problem" &&
    isUuidLike(value.currentProblemSessionId) &&
    isSimilarProblemRecommendationPayload(value.recommendation) &&
    isUuidLike(value.recommendationId) &&
    isUuidLike(value.recommendedProblemSessionId) &&
    Object.keys(value).length === 5
  );
}

function isRecallSearchResultPayloadEnvelope(value: unknown): value is RecallSearchResultPayloadEnvelope {
  return (
    isRecord(value) &&
    (value.degradationMode === "LEXICAL" || value.degradationMode === null || value.degradationMode === undefined) &&
    (value.query === null || isBoundedString(value.query, 240)) &&
    Array.isArray(value.results) &&
    value.results.length <= 50 &&
    value.results.every(isRecallSearchResultPayload) &&
    isRecallSearchStatus(value.status) &&
    Object.keys(value).length === 4
  );
}

function isRecallSearchResultPayload(value: unknown): value is RecallSearchResultPayload {
  return (
    isRecord(value) &&
    Array.isArray(value.errorPhrases) &&
    value.errorPhrases.every(isNonBlankString) &&
    Array.isArray(value.evidenceCodes) &&
    value.evidenceCodes.every(isRecallSearchEvidenceCode) &&
    isNonBlankString(value.finalSolution) &&
    (typeof value.generatedSummary === "string" || value.generatedSummary === null || value.generatedSummary === undefined) &&
    isUuidLike(value.problemSessionId) &&
    (typeof value.problemType === "string" || value.problemType === null || value.problemType === undefined) &&
    Array.isArray(value.technologies) &&
    value.technologies.every(isNonBlankString) &&
    (typeof value.title === "string" || value.title === null || value.title === undefined) &&
    isRfc3339Instant(value.updatedAt)
  );
}

function isSimilarProblemRecommendationPayload(value: unknown): value is SimilarProblemRecommendationPayload {
  return (
    isRecord(value) &&
    isNonBlankString(value.algorithmVersion) &&
    (typeof value.durationSeconds === "number" || value.durationSeconds === null || value.durationSeconds === undefined) &&
    (value.durationSeconds === null ||
      value.durationSeconds === undefined ||
      (Number.isInteger(value.durationSeconds) && value.durationSeconds >= 0)) &&
    Array.isArray(value.evidence) &&
    value.evidence.length > 0 &&
    value.evidence.every(isSimilarProblemEvidencePayload) &&
    isNonBlankString(value.finalSolution) &&
    isUuidLike(value.recommendationId) &&
    isUuidLike(value.recommendedProblemSessionId) &&
    (value.resources === undefined ||
      (Array.isArray(value.resources) && value.resources.length <= 5 && value.resources.every(isGhostReplayResourcePayload))) &&
    (value.solvedAt === null || value.solvedAt === undefined || isRfc3339Instant(value.solvedAt)) &&
    isSimilarProblemStrength(value.strength) &&
    (typeof value.title === "string" || value.title === null || value.title === undefined) &&
    Array.isArray(value.whyThis) &&
    value.whyThis.length > 0 &&
    value.whyThis.every(isSimilarProblemWhyThisPayload)
  );
}

function isGhostReplayResourcePayload(value: unknown): value is GhostReplayResourcePayload {
  return (
    isRecord(value) &&
    typeof value.alreadyVisited === "boolean" &&
    typeof value.decisive === "boolean" &&
    (typeof value.hostname === "string" || value.hostname === null || value.hostname === undefined) &&
    isUuidLike(value.id) &&
    isGhostReplayResourceKind(value.kind) &&
    isRfc3339Instant(value.occurredAt) &&
    typeof value.openedNow === "boolean" &&
    (typeof value.queryText === "string" || value.queryText === null || value.queryText === undefined) &&
    (typeof value.title === "string" || value.title === null || value.title === undefined) &&
    (isSafeHttpUrl(value.url) || value.url === null || value.url === undefined) &&
    Object.keys(value).every((key) =>
      [
        "alreadyVisited",
        "decisive",
        "hostname",
        "id",
        "kind",
        "occurredAt",
        "openedNow",
        "queryText",
        "title",
        "url"
      ].includes(key)
    )
  );
}

function isGhostReplayResourceKind(value: unknown): value is GhostReplayResourceKind {
  return (
    value === "AI_CHAT_LINK" ||
    value === "DISCUSSION" ||
    value === "DOCUMENTATION" ||
    value === "GITHUB_ISSUE" ||
    value === "GITHUB_REPOSITORY" ||
    value === "PAGE" ||
    value === "SEARCH_QUERY" ||
    value === "USER_NOTE_REFERENCE" ||
    value === "VIDEO"
  );
}

function isSimilarProblemWhyThisPayload(value: unknown): value is SimilarProblemWhyThisPayload {
  return isSimilarProblemEvidencePayload(value) && isRecord(value) && isNonBlankString(value.message);
}

function isSimilarProblemEvidencePayload(value: unknown): value is SimilarProblemEvidencePayload {
  return (
    isRecord(value) &&
    (isUuidLike(value.artifactId) || value.artifactId === null || value.artifactId === undefined) &&
    isSimilarProblemEvidenceCode(value.evidenceCode) &&
    isUuidLike(value.problemSessionId)
  );
}

function isSimilarProblemEvidenceCode(value: unknown): value is SimilarProblemEvidenceCode {
  return value === "ERROR_PHRASE" || value === "LEXICAL_TEXT" || value === "TECHNOLOGY" || value === "VECTOR_DISTANCE";
}

function isSimilarProblemStrength(value: unknown): value is SimilarProblemStrength {
  return value === "POSSIBLE" || value === "STRONG";
}

function isRecallInvocationSource(value: unknown): value is RecallInvocationSource {
  return value === "command" || value === "popup" || value === "toolbar";
}

function isRecallCommandStatus(value: unknown): value is RecallCommandStatus {
  return (
    value === "authentication_required" ||
    value === "matched" ||
    value === "no_active_problem" ||
    value === "no_match" ||
    value === "not_found" ||
    value === "opened" ||
    value === "unavailable"
  );
}

function isRecallSearchStatus(value: unknown): value is RecallSearchStatus {
  return value === "authentication_required" || value === "received" || value === "unavailable";
}

function isRecallSearchEvidenceCode(value: unknown): value is RecallSearchEvidenceCode {
  return value === "ERROR_PHRASE" || value === "LEXICAL_TEXT" || value === "TECHNOLOGY";
}

function isRecallSearchReason(value: unknown): value is RecallSearchReason {
  return (
    value === "AUTHENTICATION_REQUIRED" ||
    value === "NO_ACTIVE_PROBLEM" ||
    value === "NO_MATCH" ||
    value === "NOT_FOUND" ||
    value === "UNAVAILABLE"
  );
}

function isMessageEnvelope(value: unknown): value is MessageEnvelope<string, Record<string, unknown>> {
  return (
    isRecord(value) &&
    value.schemaVersion === 1 &&
    isNonBlankString(value.type) &&
    isNonBlankString(value.requestId) &&
    isSourceSurface(value.source) &&
    isRecord(value.payload)
  );
}

function isSourceSurface(value: unknown): boolean {
  return value === "app" || value === "background" || value === "onboarding" || value === "popup" || value === "sidepanel";
}

function isRfc3339Instant(value: unknown): value is string {
  return isNonBlankString(value) && !Number.isNaN(Date.parse(value));
}

function isUuidLike(value: unknown): value is string {
  return (
    typeof value === "string" &&
    /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/iu.test(value)
  );
}

function isSafeHttpUrl(value: unknown): value is string {
  if (!isNonBlankString(value)) {
    return false;
  }

  try {
    const parsed = new URL(value);
    return parsed.protocol === "https:" || parsed.protocol === "http:";
  } catch {
    return false;
  }
}

function isNonBlankString(value: unknown): value is string {
  return typeof value === "string" && value.trim().length > 0;
}

function isBoundedString(value: unknown, maximumLength: number): value is string {
  return typeof value === "string" && value.length <= maximumLength;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}
