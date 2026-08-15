import {
  AUTH_MESSAGE_TYPES,
  authClearSessionResponse,
  authEstablishSessionResponse,
  authStatusResponse,
  parseAuthRequestMessage,
  type AuthResponseMessage
} from "@trail/extension-messages";
import { toAuthStatusPayload } from "../model/auth-session";
import type { AuthTokenService } from "./auth-token-service";

export interface AuthMessageHandlerResult {
  readonly handled: boolean;
  readonly response?: AuthResponseMessage | { readonly errorCode: "INVALID_MESSAGE_SCHEMA" };
}

export async function handleAuthMessage(
  service: AuthTokenService,
  rawMessage: unknown
): Promise<AuthMessageHandlerResult> {
  const parsed = parseAuthRequestMessage(rawMessage);
  if (!parsed.ok) {
    return {
      handled: false,
      response: { errorCode: parsed.errorCode }
    };
  }

  const message = parsed.message;
  if (message.type === AUTH_MESSAGE_TYPES.getStatus) {
    return {
      handled: true,
      response: authStatusResponse(message, toAuthStatusPayload(await service.status()))
    };
  }

  if (message.type === AUTH_MESSAGE_TYPES.establishSession) {
    return {
      handled: true,
      response: authEstablishSessionResponse(
        message,
        toAuthStatusPayload(await service.establishSession(message.payload))
      )
    };
  }

  await service.clearSession();
  return {
    handled: true,
    response: authClearSessionResponse(message)
  };
}
