import {
  CAPTURE_STATE_MESSAGE_TYPES,
  captureStateResponse,
  parseCaptureStateRequestMessage,
  type CaptureStateResponseMessage
} from "@trail/extension-messages";
import type { CaptureStateService } from "./capture-state-service";

export interface CaptureStateMessageHandlerResult {
  readonly handled: boolean;
  readonly response?: CaptureStateResponseMessage | { readonly errorCode: "INVALID_MESSAGE_SCHEMA" };
}

export async function handleCaptureStateMessage(
  service: CaptureStateService,
  rawMessage: unknown
): Promise<CaptureStateMessageHandlerResult> {
  const parsed = parseCaptureStateRequestMessage(rawMessage);
  if (!parsed.ok) {
    return {
      handled: false,
      response: { errorCode: parsed.errorCode }
    };
  }

  const message = parsed.message;
  if (message.type === CAPTURE_STATE_MESSAGE_TYPES.pause) {
    return { handled: true, response: captureStateResponse(message, await service.pause()) };
  }

  if (message.type === CAPTURE_STATE_MESSAGE_TYPES.resume) {
    return { handled: true, response: captureStateResponse(message, await service.resume()) };
  }

  if (message.type === CAPTURE_STATE_MESSAGE_TYPES.excludeCurrentDomain) {
    return { handled: true, response: captureStateResponse(message, await service.excludeCurrentDomain()) };
  }

  if (message.type === CAPTURE_STATE_MESSAGE_TYPES.undoDomainExclusion) {
    return { handled: true, response: captureStateResponse(message, await service.undoDomainExclusion()) };
  }

  return { handled: true, response: captureStateResponse(message, await service.readState()) };
}
