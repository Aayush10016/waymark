import type { ActiveProblemSessionPayload, ProblemSessionCommandStatus, ProblemSessionReasonCategory } from "@trail/extension-messages";

export interface ProblemSessionReasonCopy {
  readonly label: string;
  readonly text: string;
}

export function problemSessionTitle(session: ActiveProblemSessionPayload): string {
  return session.title.trim() || "Technical problem";
}

export function problemSessionSourceLabel(session: ActiveProblemSessionPayload): string {
  return session.source === "MANUAL" ? "Started by you" : "Detected from debugging activity";
}

export function problemSessionStatusLabel(session: ActiveProblemSessionPayload): string {
  if (session.status === "REVIEWABLE") {
    return "Ready for review";
  }

  return "Problem session active";
}

export function commandStatusMessage(status: ProblemSessionCommandStatus): string | null {
  switch (status) {
    case "authentication_required":
      return "Sign in again to update this problem session.";
    case "conflict":
      return "This problem session cannot move to that state yet.";
    case "invalid_request":
      return "Trail could not process that action. Try reopening the popup.";
    case "network_error":
      return "Trail could not reach the local service. The session did not change.";
    case "not_found":
      return "Trail could not find this problem session.";
    case "precondition_required":
      return "Trail needs the latest session version before finishing.";
    case "server_error":
      return "Trail could not reach the server. Try again shortly.";
    case "stale_version":
      return "This problem session changed elsewhere. Reopen Trail and try again.";
    case "unavailable":
      return "Trail could not confirm the session state.";
    case "received":
      return null;
  }
}

export function reasonCopy(category: ProblemSessionReasonCategory): ProblemSessionReasonCopy {
  switch (category) {
    case "error_activity":
      return {
        label: "Error-related activity",
        text: "Trail saw bounded technical or error-pattern activity."
      };
    case "manual_session":
      return {
        label: "Manual session",
        text: "You explicitly started this problem session."
      };
    case "multiple_related_sources":
      return {
        label: "Multiple related sources",
        text: "Trail saw activity across more than one technical source."
      };
    case "repeated_activity":
      return {
        label: "Repeated troubleshooting",
        text: "Trail saw repeated activity consistent with debugging."
      };
    case "related_searches":
      return {
        label: "Related searches",
        text: "Trail saw bounded search-style activity without exposing the original text."
      };
    case "session_boundary":
      return {
        label: "Safe session boundary",
        text: "Trail waited for a quiet boundary before surfacing the session."
      };
  }
}
