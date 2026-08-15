import {
  RECALL_MESSAGE_TYPES,
  parseRecallRequestMessage,
  recallResponse,
  recallSearchResponse,
  type RecallResponseMessage
} from "@trail/extension-messages";
import type { RecallRuntimeService } from "./recall-runtime-service";

export interface RecallMessageHandlerResult {
  readonly handled: boolean;
  readonly response?: RecallResponseMessage | ReturnType<typeof recallSearchResponse> | { readonly errorCode: "INVALID_MESSAGE_SCHEMA" };
}

export async function handleRecallMessage(
  service: RecallRuntimeService,
  rawMessage: unknown
): Promise<RecallMessageHandlerResult> {
  const parsed = parseRecallRequestMessage(rawMessage);
  if (!parsed.ok) {
    return {
      handled: false,
      response: { errorCode: parsed.errorCode }
    };
  }

  const message = parsed.message;
  if (message.type === RECALL_MESSAGE_TYPES.invoke) {
    return { handled: true, response: recallResponse(message, await service.invoke(message.payload.source)) };
  }

  if (message.type === RECALL_MESSAGE_TYPES.openResource) {
    return { handled: true, response: recallResponse(message, await service.openResource(message.payload.resourceId)) };
  }

  if (message.type === RECALL_MESSAGE_TYPES.openSolvedProblem) {
    return { handled: true, response: recallResponse(message, await service.openSolvedProblem(message.payload.result)) };
  }

  if (message.type === RECALL_MESSAGE_TYPES.search) {
    return { handled: true, response: recallSearchResponse(message, await service.search(message.payload.query)) };
  }

  return { handled: true, response: recallResponse(message, await service.readState()) };
}
