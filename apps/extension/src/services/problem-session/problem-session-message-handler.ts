import {
  PROBLEM_SESSION_MESSAGE_TYPES,
  parseProblemSessionRequestMessage,
  problemSessionResponse,
  type ProblemSessionResponseMessage
} from "@trail/extension-messages";
import type { ProblemSessionRuntimeService } from "./problem-session-runtime-service";

export interface ProblemSessionMessageHandlerResult {
  readonly handled: boolean;
  readonly response?: ProblemSessionResponseMessage | { readonly errorCode: "INVALID_MESSAGE_SCHEMA" };
}

export async function handleProblemSessionMessage(
  service: ProblemSessionRuntimeService,
  rawMessage: unknown
): Promise<ProblemSessionMessageHandlerResult> {
  const parsed = parseProblemSessionRequestMessage(rawMessage);
  if (!parsed.ok) {
    return {
      handled: false,
      response: { errorCode: parsed.errorCode }
    };
  }

  const message = parsed.message;
  if (message.type === PROBLEM_SESSION_MESSAGE_TYPES.start) {
    return { handled: true, response: problemSessionResponse(message, await service.start(message.payload.title)) };
  }

  if (message.type === PROBLEM_SESSION_MESSAGE_TYPES.finish) {
    return { handled: true, response: problemSessionResponse(message, await service.finish()) };
  }

  if (message.type === PROBLEM_SESSION_MESSAGE_TYPES.saveSolution) {
    const result = await service.saveSolution(message.payload.finalSolution);
    return {
      handled: true,
      response: problemSessionResponse(message, {
        commandStatus: result.commandStatus,
        state: result.state
      })
    };
  }

  if (message.type === PROBLEM_SESSION_MESSAGE_TYPES.dismiss) {
    return { handled: true, response: problemSessionResponse(message, await service.dismiss()) };
  }

  if (message.type === PROBLEM_SESSION_MESSAGE_TYPES.undoDismiss) {
    return { handled: true, response: problemSessionResponse(message, await service.undoDismiss()) };
  }

  return { handled: true, response: problemSessionResponse(message, await service.readState()) };
}
