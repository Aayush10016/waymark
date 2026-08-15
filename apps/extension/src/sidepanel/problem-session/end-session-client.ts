import {
  PROBLEM_SESSION_MESSAGE_TYPES,
  parseProblemSessionResponseMessage,
type ProblemSessionMessageType,
  type ProblemSessionRequestMessage,
  type ProblemSessionResponsePayload
} from "@trail/extension-messages";

type EndSessionMessageType = Exclude<
  ProblemSessionMessageType,
  typeof PROBLEM_SESSION_MESSAGE_TYPES.start
>;

export type EndSessionProblemSessionClientResult =
  | {
      readonly payload: ProblemSessionResponsePayload;
      readonly status: "received";
    }
  | {
      readonly status: "unavailable";
    };

export interface EndSessionProblemSessionClient {
  dismiss(): Promise<EndSessionProblemSessionClientResult>;
  finish(): Promise<EndSessionProblemSessionClientResult>;
  getState(): Promise<EndSessionProblemSessionClientResult>;
  saveSolution(finalSolution: string): Promise<EndSessionProblemSessionClientResult>;
}

/**
 * Side-panel adapter for the background-owned Problem Session message API.
 */
export class RuntimeEndSessionProblemSessionClient
  implements EndSessionProblemSessionClient
{
  dismiss(): Promise<EndSessionProblemSessionClientResult> {
    return this.send(PROBLEM_SESSION_MESSAGE_TYPES.dismiss);
  }

  finish(): Promise<EndSessionProblemSessionClientResult> {
    return this.send(PROBLEM_SESSION_MESSAGE_TYPES.finish);
  }

  getState(): Promise<EndSessionProblemSessionClientResult> {
    return this.send(PROBLEM_SESSION_MESSAGE_TYPES.get);
  }

  saveSolution(
    finalSolution: string
  ): Promise<EndSessionProblemSessionClientResult> {
    return this.send(PROBLEM_SESSION_MESSAGE_TYPES.saveSolution, {
      finalSolution
    });
  }

  private send(
    type: EndSessionMessageType,
    payload: ProblemSessionRequestMessage["payload"] = {}
  ): Promise<EndSessionProblemSessionClientResult> {
    return new Promise((resolve) => {
      chrome.runtime.sendMessage(
        createProblemSessionRequest(type, payload),
        (response: unknown) => {
          if (chrome.runtime.lastError !== undefined) {
            resolve({ status: "unavailable" });
            return;
          }

          const parsed = parseProblemSessionResponseMessage(response);

          if (!parsed.ok) {
            resolve({ status: "unavailable" });
            return;
          }

          resolve({
            payload: parsed.message.payload,
            status: "received"
          });
        }
      );
    });
  }
}

function createProblemSessionRequest(
  type: EndSessionMessageType,
  payload: ProblemSessionRequestMessage["payload"]
): ProblemSessionRequestMessage {
  if (type === PROBLEM_SESSION_MESSAGE_TYPES.saveSolution) {
    return {
      schemaVersion: 1,
      type,
      requestId: crypto.randomUUID(),
      source: "sidepanel",
      payload: payload as {
        readonly finalSolution: string;
      }
    };
  }

  return {
    schemaVersion: 1,
    type,
    requestId: crypto.randomUUID(),
    source: "sidepanel",
    payload: {}
  };
}
