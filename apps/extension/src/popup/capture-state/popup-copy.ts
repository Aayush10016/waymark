import type { CaptureStatePayload, PopupPriorityState } from "@trail/extension-messages";

export interface PopupPriorityCopy {
  readonly body: string;
  readonly primaryLabel: string;
  readonly title: string;
}

export function popupCopyForState(state: CaptureStatePayload): PopupPriorityCopy {
  switch (state.popup.state) {
    case "auth_required":
      return copy("Connection required", "Sign in to continue syncing your Trail memory. Saved local context stays on this device.", "Reconnect");
    case "consent_required":
      return copy("Capture is off", "Trail records nothing until you review the privacy boundary and choose Start Trail.", "Review consent");
    case "permission_missing":
      return copy("Chrome access needed", "Trail cannot recognize debugging sessions until extension access is restored.", "Restore access");
    case "domain_excluded":
      return copy("Site blocked", "Trail is not remembering this site. Other allowed sites can still be captured.", "Open Trail");
    case "paused":
      return copy("Paused", "Trail is quiet on this device until you resume capture.", "Resume capture");
    case "offline":
      return copy("Saved locally", "Trail can keep sanitized context here and sync when the connection returns.", "Open Trail");
    case "recording_ready":
      return copy("Ready", "Debug normally. Trail will ask what solved it when a problem session ends.", "Open Trail");
  }
}

export function popupPriorityOrder(state: PopupPriorityState): number {
  switch (state) {
    case "auth_required":
      return 1;
    case "consent_required":
      return 2;
    case "permission_missing":
      return 3;
    case "domain_excluded":
      return 4;
    case "paused":
      return 5;
    case "offline":
      return 6;
    case "recording_ready":
      return 7;
  }
}

function copy(title: string, body: string, primaryLabel: string): PopupPriorityCopy {
  return { body, primaryLabel, title };
}
