export {
  createUrlQuerySanitizer,
  UrlQuerySanitizer,
  type AllowedCapturePolicyDecision,
  type CapturePolicyDecision,
  type CapturePolicyRejectionReason,
  type CaptureSanitationPolicy,
  type RawCaptureCandidate,
  type RejectedCapturePolicyDecision,
  type SearchAllowlistEntry
} from "./url-query-sanitizer";
export { defaultCaptureSanitationPolicy } from "./default-capture-sanitation-policy";
export {
  LocalCapturePolicyAdapter,
  type CapturePolicyAdapterResult,
  type SanitizedCaptureEvent,
  type SanitizedCaptureEventQueue
} from "./local-capture-policy-adapter";
