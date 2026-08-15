import type { components } from "@trail/api-client";
import { defaultFetchImplementation, executeFetch, type FetchImplementation } from "../../shared/http/safe-fetch";

export type ProblemSessionDetailResponse = components["schemas"]["ProblemSessionDetailResponse"];

export interface GetProblemSessionDetailCommand {
  readonly accessToken: string;
  readonly problemSessionId: string;
}

export type ProblemSessionDetailApiResult =
  | {
      readonly response: ProblemSessionDetailResponse;
      readonly status: "found";
    }
  | {
      readonly status: "authentication_rejected" | "malformed_response" | "network_error" | "not_found" | "retryable_failure";
    };

export interface ProblemSessionDetailApiClient {
  getProblemSessionDetail(command: GetProblemSessionDetailCommand): Promise<ProblemSessionDetailApiResult>;
}

export interface FetchProblemSessionDetailApiClientOptions {
  readonly baseUrl: string;
  readonly fetchImplementation?: FetchImplementation;
}

/**
 * Fetch adapter for the OpenAPI-defined Problem Session detail endpoint.
 */
export class FetchProblemSessionDetailApiClient implements ProblemSessionDetailApiClient {
  private readonly baseUrl: string;
  private readonly fetchImplementation: FetchImplementation;

  constructor(options: FetchProblemSessionDetailApiClientOptions) {
    this.baseUrl = options.baseUrl.replace(/\/$/, "");
    this.fetchImplementation = options.fetchImplementation ?? defaultFetchImplementation();
  }

  async getProblemSessionDetail(command: GetProblemSessionDetailCommand): Promise<ProblemSessionDetailApiResult> {
    try {
      const response = await executeFetch(this.fetchImplementation, `${this.baseUrl}/problem-sessions/${command.problemSessionId}`, {
        headers: {
          Authorization: `Bearer ${command.accessToken}`
        },
        method: "GET"
      });
      return await mapResponse(response);
    } catch {
      return { status: "network_error" };
    }
  }
}

async function mapResponse(response: Response): Promise<ProblemSessionDetailApiResult> {
  if (response.status === 401) {
    return { status: "authentication_rejected" };
  }
  if (response.status === 404) {
    return { status: "not_found" };
  }
  if (!response.ok) {
    return { status: "retryable_failure" };
  }

  const body = await safelyReadJson(response);
  if (!isProblemSessionDetailResponse(body)) {
    return { status: "malformed_response" };
  }

  return { response: body, status: "found" };
}

async function safelyReadJson(response: Response): Promise<unknown> {
  try {
    return await response.json();
  } catch {
    return null;
  }
}

function isProblemSessionDetailResponse(value: unknown): value is ProblemSessionDetailResponse {
  return (
    isRecord(value) &&
    isUuidLike(value.id) &&
    (value.status === "CANDIDATE" ||
      value.status === "REVIEWABLE" ||
      value.status === "SOLVED" ||
      value.status === "DISMISSED" ||
      value.status === "UNSOLVED" ||
      value.status === "DELETED") &&
    Array.isArray(value.artifacts) &&
    value.artifacts.every(isSessionArtifactDetailResponse)
  );
}

function isSessionArtifactDetailResponse(value: unknown): boolean {
  return (
    isRecord(value) &&
    isUuidLike(value.id) &&
    Number.isInteger(value.position) &&
    isArtifactType(value.artifactType) &&
    (isSafeHttpUrl(value.sanitizedUrl) || value.sanitizedUrl === null || value.sanitizedUrl === undefined) &&
    (typeof value.hostname === "string" || value.hostname === null || value.hostname === undefined) &&
    (typeof value.title === "string" || value.title === null || value.title === undefined) &&
    (typeof value.queryText === "string" || value.queryText === null || value.queryText === undefined) &&
    isRfc3339Instant(value.occurredAt) &&
    typeof value.decisive === "boolean"
  );
}

function isArtifactType(value: unknown): boolean {
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

function isSafeHttpUrl(value: unknown): value is string {
  if (typeof value !== "string" || value.trim().length === 0) {
    return false;
  }

  try {
    const parsed = new URL(value);
    return parsed.protocol === "https:" || parsed.protocol === "http:";
  } catch {
    return false;
  }
}

function isRfc3339Instant(value: unknown): value is string {
  return typeof value === "string" && value.trim().length > 0 && !Number.isNaN(Date.parse(value));
}

function isUuidLike(value: unknown): value is string {
  return (
    typeof value === "string" &&
    /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/iu.test(value)
  );
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}
