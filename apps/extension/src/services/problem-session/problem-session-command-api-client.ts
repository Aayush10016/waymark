import type { components } from "@trail/api-client";
import { defaultFetchImplementation, executeFetch, type FetchImplementation } from "../../shared/http/safe-fetch";

export type StartProblemSessionRequest = components["schemas"]["StartProblemSessionRequest"];
export type FinishProblemSessionRequest = components["schemas"]["FinishProblemSessionRequest"];
export type SaveProblemSessionSolutionRequest = components["schemas"]["SaveProblemSessionSolutionRequest"];
export type ProblemSessionCommandResponse = components["schemas"]["ProblemSessionCommandResponse"];
export type ProblemSessionSolutionResponse = components["schemas"]["ProblemSessionSolutionResponse"];

export interface ProblemSessionCommand {
  readonly accessToken: string;
}

export interface StartProblemSessionCommand extends ProblemSessionCommand {
  readonly request: StartProblemSessionRequest;
}

export interface FinishProblemSessionCommand extends ProblemSessionCommand {
  readonly etag: string;
  readonly problemSessionId: string;
  readonly request: FinishProblemSessionRequest;
}

export interface SaveProblemSessionSolutionCommand extends ProblemSessionCommand {
  readonly etag: string;
  readonly idempotencyKey: string;
  readonly problemSessionId: string;
  readonly request: SaveProblemSessionSolutionRequest;
}

export type ProblemSessionCommandApiResult =
  | {
      readonly etag: string;
      readonly response: ProblemSessionCommandResponse;
      readonly status: "processed";
    }
  | {
      readonly status:
        | "authentication_rejected"
        | "conflict"
        | "invalid_request"
        | "malformed_response"
        | "network_error"
        | "not_found"
        | "precondition_required"
        | "retryable_failure"
        | "stale_version";
    };

export type ProblemSessionSolutionApiResult =
  | {
      readonly etag: string;
      readonly response: ProblemSessionSolutionResponse;
      readonly status: "processed";
    }
  | Exclude<ProblemSessionCommandApiResult, { readonly status: "processed" }>;

export interface ProblemSessionCommandApiClient {
  finish(command: FinishProblemSessionCommand): Promise<ProblemSessionCommandApiResult>;
  saveSolution(command: SaveProblemSessionSolutionCommand): Promise<ProblemSessionSolutionApiResult>;
  start(command: StartProblemSessionCommand): Promise<ProblemSessionCommandApiResult>;
}

export interface FetchProblemSessionCommandApiClientOptions {
  readonly baseUrl: string;
  readonly fetchImplementation?: FetchImplementation;
}

/**
 * Fetch adapter for Trail's OpenAPI-defined Problem Session command endpoints.
 */
export class FetchProblemSessionCommandApiClient implements ProblemSessionCommandApiClient {
  private readonly baseUrl: string;
  private readonly fetchImplementation: FetchImplementation;

  constructor(options: FetchProblemSessionCommandApiClientOptions) {
    this.baseUrl = options.baseUrl.replace(/\/$/, "");
    this.fetchImplementation = options.fetchImplementation ?? defaultFetchImplementation();
  }

  async start(command: StartProblemSessionCommand): Promise<ProblemSessionCommandApiResult> {
    try {
      return await this.send(`${this.baseUrl}/problem-sessions`, {
        body: JSON.stringify(command.request),
        headers: this.headers(command.accessToken),
        method: "POST"
      });
    } catch {
      return { status: "network_error" };
    }
  }

  async finish(command: FinishProblemSessionCommand): Promise<ProblemSessionCommandApiResult> {
    try {
      return await this.send(`${this.baseUrl}/problem-sessions/${command.problemSessionId}/finish`, {
        body: JSON.stringify(command.request),
        headers: {
          ...this.headers(command.accessToken),
          "If-Match": command.etag
        },
        method: "POST"
      });
    } catch {
      return { status: "network_error" };
    }
  }

  async saveSolution(command: SaveProblemSessionSolutionCommand): Promise<ProblemSessionSolutionApiResult> {
    try {
      return await this.sendSolution(`${this.baseUrl}/problem-sessions/${command.problemSessionId}/solution`, {
        body: JSON.stringify(command.request),
        headers: {
          ...this.headers(command.accessToken),
          "Idempotency-Key": command.idempotencyKey,
          "If-Match": command.etag
        },
        method: "PUT"
      });
    } catch {
      return { status: "network_error" };
    }
  }

  private headers(accessToken: string): Record<string, string> {
    return {
      Authorization: `Bearer ${accessToken}`,
      "Content-Type": "application/json"
    };
  }

  private async send(input: RequestInfo | URL, init: RequestInit): Promise<ProblemSessionCommandApiResult> {
    const response = await executeFetch(this.fetchImplementation, input, init);
    if (response.status === 400) {
      return { status: "invalid_request" };
    }
    if (response.status === 401) {
      return { status: "authentication_rejected" };
    }
    if (response.status === 404) {
      return { status: "not_found" };
    }
    if (response.status === 409) {
      return { status: "conflict" };
    }
    if (response.status === 412) {
      return { status: "stale_version" };
    }
    if (response.status === 428) {
      return { status: "precondition_required" };
    }
    if (!response.ok) {
      return { status: "retryable_failure" };
    }

    const body = await safelyReadJson(response);
    const etag = response.headers.get("ETag");
    if (!isProblemSessionCommandResponse(body) || etag === null || etag.trim().length === 0) {
      return { status: "malformed_response" };
    }

    return {
      etag,
      response: body,
      status: "processed"
    };
  }

  private async sendSolution(input: RequestInfo | URL, init: RequestInit): Promise<ProblemSessionSolutionApiResult> {
    const response = await executeFetch(this.fetchImplementation, input, init);
    const failure = mapFailureResponse(response);
    if (failure !== null) {
      return failure;
    }

    const body = await safelyReadJson(response);
    const etag = response.headers.get("ETag");
    if (!isProblemSessionSolutionResponse(body) || etag === null || etag.trim().length === 0) {
      return { status: "malformed_response" };
    }

    return {
      etag,
      response: body,
      status: "processed"
    };
  }
}

function mapFailureResponse(response: Response): Exclude<ProblemSessionCommandApiResult, { readonly status: "processed" }> | null {
  if (response.status === 400) {
    return { status: "invalid_request" };
  }
  if (response.status === 401) {
    return { status: "authentication_rejected" };
  }
  if (response.status === 404) {
    return { status: "not_found" };
  }
  if (response.status === 409) {
    return { status: "conflict" };
  }
  if (response.status === 412) {
    return { status: "stale_version" };
  }
  if (response.status === 428) {
    return { status: "precondition_required" };
  }
  if (!response.ok) {
    return { status: "retryable_failure" };
  }

  return null;
}

async function safelyReadJson(response: Response): Promise<unknown> {
  try {
    return await response.json();
  } catch {
    return null;
  }
}

function isProblemSessionCommandResponse(value: unknown): value is ProblemSessionCommandResponse {
  return (
    isRecord(value) &&
    isUuidLike(value.id) &&
    isProblemSessionStatus(value.status) &&
    (value.source === "DETECTED" || value.source === "MANUAL") &&
    typeof value.version === "number" &&
    Number.isInteger(value.version) &&
    value.version >= 0 &&
    (typeof value.title === "string" || value.title === null || value.title === undefined) &&
    isRfc3339Instant(value.startedAt) &&
    (typeof value.endedAt === "string" || value.endedAt === null || value.endedAt === undefined) &&
    (value.endedAt === null || value.endedAt === undefined || isRfc3339Instant(value.endedAt)) &&
    isRfc3339Instant(value.updatedAt)
  );
}

function isProblemSessionSolutionResponse(value: unknown): value is ProblemSessionSolutionResponse {
  const record = isRecord(value) ? value : null;
  return (
    record !== null &&
    isProblemSessionCommandResponse(value) &&
    value.status === "SOLVED" &&
    typeof record.finalSolution === "string" &&
    record.finalSolution.trim().length > 0 &&
    record.finalSolutionAuthor === "USER" &&
    isRfc3339Instant(record.solutionAuthoredAt)
  );
}

function isProblemSessionStatus(value: unknown): value is ProblemSessionCommandResponse["status"] {
  return (
    value === "CANDIDATE" ||
    value === "REVIEWABLE" ||
    value === "SOLVED" ||
    value === "DISMISSED" ||
    value === "UNSOLVED" ||
    value === "DELETED"
  );
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
