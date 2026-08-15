export type { CaptureEvent, CaptureEventSink, CaptureEventSource, CaptureResourceType } from "./capture-event";
export {
  NavigationCaptureService,
  type CaptureConsentReader,
  type CaptureControlReader,
  type NavigationCaptureCandidate,
  type NavigationCaptureIgnoredReason,
  type NavigationCaptureResult,
  type NavigationCaptureServiceOptions
} from "./navigation-capture-service";
export {
  ProblemSessionCaptureEventSink,
  type CaptureProblemSessionStateReader
} from "./problem-session-capture-event-sink";
