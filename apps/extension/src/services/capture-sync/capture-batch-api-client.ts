import type { components } from "@trail/api-client";
import { defaultFetchImplementation, executeFetch, type FetchImplementation } from "../../shared/http/safe-fetch";

export type CaptureBatchRequest = components["schemas"]["CaptureBatchRequest"];
export type CaptureBatchResponse = components["schemas"]["CaptureBatchResponse"];
export type CaptureEventRejectionCode = components["schemas"]["CaptureEventRejectionCode"];

export interface CaptureBatchUploadCommand {
  readonly accessToken: string;
  readonly idempotencyKey: string;
  readonly request: CaptureBatchRequest;
}

export type CaptureBatchUploadResult =
  | { readonly status: "processed"; readonly response: CaptureBatchResponse }
  | { readonly status: "authentication_rejected" }
  | { readonly status: "payload_too_large" }
  | { readonly status: "rate_limited" }
  | { readonly status: "bad_request" }
  | { readonly status: "retryable_failure" }
  | { readonly status: "network_error" }
  | { readonly status: "malformed_response" };

export interface CaptureBatchApiClient {
  upload(command: CaptureBatchUploadCommand): Promise<CaptureBatchUploadResult>;
}

export interface FetchCaptureBatchApiClientOptions {
  readonly baseUrl: string;
  readonly fetchImplementation?: FetchImplementation;
}

/**
 * Fetch adapter for Trail's OpenAPI-defined capture batch endpoint.
 */
export class FetchCaptureBatchApiClient implements CaptureBatchApiClient {
  private readonly baseUrl: string;
  private readonly fetchImplementation: FetchImplementation;

  constructor(options: FetchCaptureBatchApiClientOptions) {
    this.baseUrl = options.baseUrl.replace(/\/$/, "");
    this.fetchImplementation = options.fetchImplementation ?? defaultFetchImplementation();
  }

  async upload(command: CaptureBatchUploadCommand): Promise<CaptureBatchUploadResult> {
    try {
      const response = await executeFetch(this.fetchImplementation, `${this.baseUrl}/capture-batches`, {
        body: JSON.stringify(command.request),
        headers: {
          Authorization: `Bearer ${command.accessToken}`,
          "Content-Type": "application/json",
          "Idempotency-Key": command.idempotencyKey
        },
        method: "POST"
      });

      if (response.status === 401) {
        return { status: "authentication_rejected" };
      }
      if (response.status === 413) {
        return { status: "payload_too_large" };
      }
      if (response.status === 429) {
        return { status: "rate_limited" };
      }
      if (response.status === 400) {
        return { status: "bad_request" };
      }
      if (!response.ok) {
        return { status: "retryable_failure" };
      }

      const body = await safelyReadJson(response);
      if (!isCaptureBatchResponse(body)) {
        return { status: "malformed_response" };
      }

      return { status: "processed", response: body };
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

function isCaptureBatchResponse(value: unknown): value is CaptureBatchResponse {
  if (!isRecord(value)) {
    return false;
  }

  return (
    isAcceptedEventArray(value.accepted) &&
    isAcceptedEventArray(value.duplicate) &&
    isRejectedEventArray(value.rejected)
  );
}

function isAcceptedEventArray(value: unknown): value is CaptureBatchResponse["accepted"] {
  return Array.isArray(value) && value.every((item) => isRecord(item) && typeof item.clientEventId === "string");
}

function isRejectedEventArray(value: unknown): value is CaptureBatchResponse["rejected"] {
  return (
    Array.isArray(value) &&
    value.every(
      (item) =>
        isRecord(item) &&
        (typeof item.clientEventId === "string" || item.clientEventId === null) &&
        isCaptureEventRejectionCode(item.code)
    )
  );
}

function isCaptureEventRejectionCode(value: unknown): value is CaptureEventRejectionCode {
  return (
    value === "CONSENT_REQUIRED" ||
    value === "INVALID_CLIENT_EVENT_ID" ||
    value === "INVALID_CLIENT_INSTANCE_ID" ||
    value === "INVALID_EVENT_TIME" ||
    value === "INVALID_METADATA" ||
    value === "INVALID_URL" ||
    value === "UNSAFE_URL"
  );
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}
