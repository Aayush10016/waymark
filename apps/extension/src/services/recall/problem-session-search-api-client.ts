import type { components } from "@trail/api-client";
import { defaultFetchImplementation, executeFetch, type FetchImplementation } from "../../shared/http/safe-fetch";

export type ProblemSessionSearchResponse = components["schemas"]["ProblemSessionSearchResponse"];

export interface SearchProblemSessionsCommand {
  readonly accessToken: string;
  readonly query: string | null;
}

export type ProblemSessionSearchApiResult =
  | {
      readonly response: ProblemSessionSearchResponse;
      readonly status: "received";
    }
  | {
      readonly status: "authentication_rejected" | "malformed_response" | "network_error" | "retryable_failure";
    };

export interface ProblemSessionSearchApiClient {
  search(command: SearchProblemSessionsCommand): Promise<ProblemSessionSearchApiResult>;
}

export interface FetchProblemSessionSearchApiClientOptions {
  readonly baseUrl: string;
  readonly fetchImplementation?: FetchImplementation;
}

/**
 * Fetch adapter for the OpenAPI-defined solved Problem Session search endpoint.
 */
export class FetchProblemSessionSearchApiClient implements ProblemSessionSearchApiClient {
  private readonly baseUrl: string;
  private readonly fetchImplementation: FetchImplementation;

  constructor(options: FetchProblemSessionSearchApiClientOptions) {
    this.baseUrl = options.baseUrl.replace(/\/$/, "");
    this.fetchImplementation = options.fetchImplementation ?? defaultFetchImplementation();
  }

  async search(command: SearchProblemSessionsCommand): Promise<ProblemSessionSearchApiResult> {
    try {
      const normalizedQuery = command.query === null || command.query.trim().length === 0 ? null : command.query.trim();
      const response = await executeFetch(this.fetchImplementation, `${this.baseUrl}/problem-session-searches`, {
        body: JSON.stringify(normalizedQuery === null ? { pageSize: 20 } : { pageSize: 20, query: normalizedQuery }),
        headers: {
          Authorization: `Bearer ${command.accessToken}`,
          "Content-Type": "application/json"
        },
        method: "POST"
      });
      return await mapResponse(response);
    } catch {
      return { status: "network_error" };
    }
  }
}

async function mapResponse(response: Response): Promise<ProblemSessionSearchApiResult> {
  if (response.status === 401) {
    return { status: "authentication_rejected" };
  }
  if (!response.ok) {
    return { status: "retryable_failure" };
  }

  const body = await safelyReadJson(response);
  if (!isProblemSessionSearchResponse(body)) {
    return { status: "malformed_response" };
  }

  return { response: body, status: "received" };
}

async function safelyReadJson(response: Response): Promise<unknown> {
  try {
    return await response.json();
  } catch {
    return null;
  }
}

function isProblemSessionSearchResponse(value: unknown): value is ProblemSessionSearchResponse {
  return (
    isRecord(value) &&
    Array.isArray(value.results) &&
    value.results.length <= 50 &&
    value.results.every(isProblemSessionSearchResult) &&
    (value.nextCursor === null || value.nextCursor === undefined || typeof value.nextCursor === "string") &&
    value.degradationMode === "LEXICAL"
  );
}

function isProblemSessionSearchResult(value: unknown): boolean {
  return (
    isRecord(value) &&
    isUuidLike(value.problemSessionId) &&
    (typeof value.title === "string" || value.title === null || value.title === undefined) &&
    typeof value.finalSolution === "string" &&
    value.finalSolution.trim().length > 0 &&
    (typeof value.generatedSummary === "string" || value.generatedSummary === null || value.generatedSummary === undefined) &&
    Array.isArray(value.technologies) &&
    value.technologies.every(isNonBlankString) &&
    Array.isArray(value.errorPhrases) &&
    value.errorPhrases.every(isNonBlankString) &&
    (typeof value.problemType === "string" || value.problemType === null || value.problemType === undefined) &&
    Array.isArray(value.evidenceCodes) &&
    value.evidenceCodes.every(isSearchEvidenceCode) &&
    typeof value.updatedAt === "string" &&
    !Number.isNaN(Date.parse(value.updatedAt))
  );
}

function isSearchEvidenceCode(value: unknown): boolean {
  return value === "ERROR_PHRASE" || value === "LEXICAL_TEXT" || value === "TECHNOLOGY";
}

function isUuidLike(value: unknown): value is string {
  return (
    typeof value === "string" &&
    /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/iu.test(value)
  );
}

function isNonBlankString(value: unknown): value is string {
  return typeof value === "string" && value.trim().length > 0;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}
