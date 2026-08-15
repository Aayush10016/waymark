import type { components } from "@trail/api-client";
import { defaultFetchImplementation, executeFetch, type FetchImplementation } from "../../shared/http/safe-fetch";

export type CaptureConsentDecisionRequest = components["schemas"]["CaptureConsentDecisionRequest"];
export type CaptureConsentDecisionResponse = components["schemas"]["CaptureConsentDecisionResponse"];

export interface CaptureConsentDecisionCommand {
  readonly accessToken: string;
  readonly request: CaptureConsentDecisionRequest;
}

export type CaptureConsentDecisionResult =
  | { readonly response: CaptureConsentDecisionResponse; readonly status: "processed" }
  | { readonly status: "authentication_rejected" }
  | { readonly status: "bad_request" }
  | { readonly status: "retryable_failure" }
  | { readonly status: "network_error" }
  | { readonly status: "malformed_response" };

export interface CaptureConsentApiClient {
  grant(command: CaptureConsentDecisionCommand): Promise<CaptureConsentDecisionResult>;
  withdraw(command: CaptureConsentDecisionCommand): Promise<CaptureConsentDecisionResult>;
}

export interface FetchCaptureConsentApiClientOptions {
  readonly baseUrl: string;
  readonly fetchImplementation?: FetchImplementation;
}

/**
 * Fetch adapter for Trail's OpenAPI-defined capture consent endpoints.
 */
export class FetchCaptureConsentApiClient implements CaptureConsentApiClient {
  private readonly baseUrl: string;
  private readonly fetchImplementation: FetchImplementation;

  constructor(options: FetchCaptureConsentApiClientOptions) {
    this.baseUrl = options.baseUrl.replace(/\/$/, "");
    this.fetchImplementation = options.fetchImplementation ?? defaultFetchImplementation();
  }

  grant(command: CaptureConsentDecisionCommand): Promise<CaptureConsentDecisionResult> {
    return this.record("consent-grants", command);
  }

  withdraw(command: CaptureConsentDecisionCommand): Promise<CaptureConsentDecisionResult> {
    return this.record("consent-withdrawals", command);
  }

  private async record(
    path: "consent-grants" | "consent-withdrawals",
    command: CaptureConsentDecisionCommand
  ): Promise<CaptureConsentDecisionResult> {
    try {
      const response = await executeFetch(this.fetchImplementation, `${this.baseUrl}/${path}`, {
        body: JSON.stringify(command.request),
        headers: {
          Authorization: `Bearer ${command.accessToken}`,
          "Content-Type": "application/json"
        },
        method: "POST"
      });

      if (response.status === 401) {
        return { status: "authentication_rejected" };
      }
      if (response.status === 400) {
        return { status: "bad_request" };
      }
      if (!response.ok) {
        return { status: "retryable_failure" };
      }

      const body = await safelyReadJson(response);
      if (!isCaptureConsentDecisionResponse(body)) {
        return { status: "malformed_response" };
      }

      return { response: body, status: "processed" };
    } catch {
      return { status: "network_error" };
    }
  }
}

async function safelyReadJson(response: Response): Promise<unknown> {
  try {
    return await response.json();
  } catch {
    return null;
  }
}

function isCaptureConsentDecisionResponse(value: unknown): value is CaptureConsentDecisionResponse {
  if (!isRecord(value)) {
    return false;
  }

  return (
    (value.status === "GRANTED" || value.status === "WITHDRAWN") &&
    value.policyVersion === "capture-consent-v1" &&
    typeof value.decidedAt === "string"
  );
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}
