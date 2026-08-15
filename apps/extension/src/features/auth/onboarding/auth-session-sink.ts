import {
  AUTH_MESSAGE_TYPES,
  AUTH_RESPONSE_TYPES,
  AUTH_STATUS,
  type AuthEstablishSessionResponseMessage
} from "@trail/extension-messages";
import type { IssuedAuthTokens } from "../model/auth-session";

export interface AuthSessionSink {
  establish(tokens: IssuedAuthTokens): Promise<AuthSessionEstablishResult>;
}

export type AuthSessionEstablishResult = {
  readonly status: "ESTABLISHED" | "FAILED";
};

export class RuntimeAuthSessionSink implements AuthSessionSink {
  async establish(tokens: IssuedAuthTokens): Promise<AuthSessionEstablishResult> {
    if (!isRuntimeMessagingAvailable()) {
      return { status: "FAILED" };
    }

    try {
      const response = (await chrome.runtime.sendMessage({
        schemaVersion: 1,
        type: AUTH_MESSAGE_TYPES.establishSession,
        requestId: crypto.randomUUID(),
        source: "onboarding",
        payload: tokens
      })) as unknown;

      return isEstablishedResponse(response) ? { status: "ESTABLISHED" } : { status: "FAILED" };
    } catch {
      return { status: "FAILED" };
    }
  }
}

function isRuntimeMessagingAvailable(): boolean {
  return typeof chrome !== "undefined" && chrome.runtime?.sendMessage !== undefined;
}

function isEstablishedResponse(input: unknown): input is AuthEstablishSessionResponseMessage {
  if (!isRecord(input) || !isRecord(input.payload)) {
    return false;
  }

  return (
    input.schemaVersion === 1 &&
    input.type === AUTH_RESPONSE_TYPES.establishSession &&
    input.payload.status === AUTH_STATUS.authenticated
  );
}

function isRecord(input: unknown): input is Record<string, unknown> {
  return typeof input === "object" && input !== null && !Array.isArray(input);
}
