import type { components } from "@trail/api-client";
import { defaultFetchImplementation, executeFetch, type FetchImplementation } from "../../shared/http/safe-fetch";

export type ProblemRecommendationDetailResponse = components["schemas"]["ProblemRecommendationDetailResponse"];

export interface GetProblemRecommendationCommand {
  readonly accessToken: string;
  readonly problemSessionId: string;
}

export type ProblemRecommendationApiResult =
  | {
      readonly response: ProblemRecommendationDetailResponse;
      readonly status: "matched";
    }
  | {
      readonly status:
        | "authentication_rejected"
        | "malformed_response"
        | "network_error"
        | "no_match"
        | "not_found"
        | "retryable_failure";
    };

export interface ProblemRecommendationApiClient {
  getRecommendation(command: GetProblemRecommendationCommand): Promise<ProblemRecommendationApiResult>;
}

export interface FetchProblemRecommendationApiClientOptions {
  readonly baseUrl: string;
  readonly fetchImplementation?: FetchImplementation;
}

/**
 * Fetch adapter for the OpenAPI-defined Problem Session recommendation endpoint.
 */
export class FetchProblemRecommendationApiClient implements ProblemRecommendationApiClient {
  private readonly baseUrl: string;
  private readonly fetchImplementation: FetchImplementation;

  constructor(options: FetchProblemRecommendationApiClientOptions) {
    this.baseUrl = options.baseUrl.replace(/\/$/, "");
    this.fetchImplementation = options.fetchImplementation ?? defaultFetchImplementation();
  }

  async getRecommendation(command: GetProblemRecommendationCommand): Promise<ProblemRecommendationApiResult> {
    try {
      const response = await executeFetch(
        this.fetchImplementation,
        `${this.baseUrl}/problem-sessions/${command.problemSessionId}/recommendation`,
        {
          headers: {
            Authorization: `Bearer ${command.accessToken}`
          },
          method: "GET"
        }
      );
      return await mapResponse(response);
    } catch {
      return { status: "network_error" };
    }
  }
}

async function mapResponse(response: Response): Promise<ProblemRecommendationApiResult> {
  if (response.status === 204) {
    return { status: "no_match" };
  }
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
  if (!isProblemRecommendationDetailResponse(body)) {
    return { status: "malformed_response" };
  }

  return {
    response: body,
    status: "matched"
  };
}

async function safelyReadJson(response: Response): Promise<unknown> {
  try {
    return await response.json();
  } catch {
    return null;
  }
}

function isProblemRecommendationDetailResponse(value: unknown): value is ProblemRecommendationDetailResponse {
  return (
    isRecord(value) &&
    isUuidLike(value.recommendationId) &&
    isUuidLike(value.currentProblemSessionId) &&
    isUuidLike(value.recommendedProblemSessionId) &&
    (value.strength === "STRONG" || value.strength === "POSSIBLE") &&
    typeof value.finalSolution === "string" &&
    value.finalSolution.trim().length > 0 &&
    typeof value.algorithmVersion === "string" &&
    value.algorithmVersion.trim().length > 0 &&
    Array.isArray(value.evidence) &&
    value.evidence.length > 0 &&
    Array.isArray(value.whyThis) &&
    value.whyThis.length > 0
  );
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
