import {
  CAPTURE_STATE_MESSAGE_TYPES,
  parseCaptureStateResponseMessage,
  type CaptureStateMessageType,
  type CaptureStatePayload,
  type CaptureStateRequestMessage
} from "@trail/extension-messages";

export type PopupCaptureStateClientResult =
  | { readonly status: "received"; readonly state: CaptureStatePayload }
  | { readonly status: "unavailable" };

export interface PopupCaptureStateClient {
  excludeCurrentDomain(): Promise<PopupCaptureStateClientResult>;
  getState(): Promise<PopupCaptureStateClientResult>;
  pause(): Promise<PopupCaptureStateClientResult>;
  resume(): Promise<PopupCaptureStateClientResult>;
  undoDomainExclusion(): Promise<PopupCaptureStateClientResult>;
}

/**
 * Popup adapter for the background-owned capture-state message API.
 */
export class RuntimePopupCaptureStateClient implements PopupCaptureStateClient {
  excludeCurrentDomain(): Promise<PopupCaptureStateClientResult> {
    return this.send(CAPTURE_STATE_MESSAGE_TYPES.excludeCurrentDomain);
  }

  getState(): Promise<PopupCaptureStateClientResult> {
    return this.send(CAPTURE_STATE_MESSAGE_TYPES.get);
  }

  pause(): Promise<PopupCaptureStateClientResult> {
    return this.send(CAPTURE_STATE_MESSAGE_TYPES.pause);
  }

  resume(): Promise<PopupCaptureStateClientResult> {
    return this.send(CAPTURE_STATE_MESSAGE_TYPES.resume);
  }

  undoDomainExclusion(): Promise<PopupCaptureStateClientResult> {
    return this.send(CAPTURE_STATE_MESSAGE_TYPES.undoDomainExclusion);
  }

  private send(type: CaptureStateMessageType): Promise<PopupCaptureStateClientResult> {
    return new Promise((resolve) => {
      chrome.runtime.sendMessage(createCaptureStateRequest(type), (response: unknown) => {
        const parsed = parseCaptureStateResponseMessage(response);
        if (!parsed.ok) {
          resolve({ status: "unavailable" });
          return;
        }

        resolve({ status: "received", state: parsed.message.payload });
      });
    });
  }
}

function createCaptureStateRequest(type: CaptureStateMessageType): CaptureStateRequestMessage {
  return {
    schemaVersion: 1,
    type,
    requestId: createRequestId(),
    source: "popup",
    payload: {}
  };
}

function createRequestId(): string {
  return crypto.randomUUID();
}
