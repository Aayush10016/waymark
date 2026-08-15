import {
  AUTH_STATUS,
  type ActiveProblemSessionPayload,
  type CaptureStatePayload,
  type ProblemSessionResponsePayload
} from "@trail/extension-messages";
import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import type { PopupCaptureStateClient, PopupCaptureStateClientResult } from "../src/popup/capture-state/capture-state-client";
import { popupCopyForState, popupPriorityOrder } from "../src/popup/capture-state/popup-copy";
import type {
  PopupProblemSessionClient,
  PopupProblemSessionClientResult
} from "../src/popup/problem-session/problem-session-client";
import { commandStatusMessage, reasonCopy } from "../src/popup/problem-session/problem-session-copy";
import { TrailPopupApp } from "../src/popup/TrailPopupApp";

describe("TrailPopupApp capture state", () => {
  it("renders the popup priority state without raw activity", () => {
    const markup = renderToStaticMarkup(
      <TrailPopupApp
        client={new StaticPopupClient(captureState())}
        initialProblemSessionState={problemSessionState(null)}
        initialState={captureState()}
        problemSessionClient={new StaticProblemSessionClient(problemSessionState(null))}
      />
    );

    expect(markup).toContain("Ready");
    expect(markup).toContain("docs.example.test");
    expect(markup).toContain("Pause");
    expect(markup).toContain("Block site");
    expect(markup).toContain("Start problem");
    expect(markup).toContain("Privacy &amp; Consent");
    expect(markup).not.toContain("queued locally");
    expect(markup).not.toContain("https://");
    expect(markup).not.toContain("Spring Security");
  });

  it("renders paused and excluded-domain controls", () => {
    const markup = renderToStaticMarkup(
      <TrailPopupApp
        client={new StaticPopupClient(captureState({ currentDomainExcluded: true, paused: true, popupState: "domain_excluded" }))}
        initialProblemSessionState={problemSessionState(null)}
        initialState={captureState({ currentDomainExcluded: true, paused: true, popupState: "domain_excluded" })}
        problemSessionClient={new StaticProblemSessionClient(problemSessionState(null))}
      />
    );

    expect(markup).toContain("Site blocked");
    expect(markup).toContain("Resume");
    expect(markup).toContain("Undo block");
  });

  it("prioritizes reconnect and disables Recall when authentication is required", () => {
    const authRequired = captureState({ authStatus: AUTH_STATUS.signedOut, popupState: "auth_required" });
    const markup = renderToStaticMarkup(
      <TrailPopupApp
        client={new StaticPopupClient(authRequired)}
        initialProblemSessionState={problemSessionState(null)}
        initialState={authRequired}
        problemSessionClient={new StaticProblemSessionClient(problemSessionState(null))}
      />
    );

    expect(markup).toContain("Connection required");
    expect(markup).toContain("Reconnect");
    expect(markup).toContain("Signed out");
    expect(markup).toContain("Saved local context stays on this device");
    expect(markup).not.toContain("Reconnect to search your saved solutions.");
    expect(markup).not.toContain(">Recall</button>");
    expect(markup).not.toContain("0 unsynced");
  });

  it("keeps popup copy and priority order deterministic", () => {
    expect(popupCopyForState(captureState({ popupState: "paused" }))).toMatchObject({
      title: "Paused",
      primaryLabel: "Resume capture"
    });
    expect(popupPriorityOrder("auth_required")).toBeLessThan(popupPriorityOrder("recording_ready"));
  });

  it("renders an active manually started Problem Session without raw captured activity", () => {
    const markup = renderToStaticMarkup(
      <TrailPopupApp
        client={new StaticPopupClient(captureState())}
        initialProblemSessionState={problemSessionState(activeSession())}
        initialState={captureState()}
        problemSessionClient={new StaticProblemSessionClient(problemSessionState(activeSession()))}
      />
    );

    expect(markup).toContain("Problem session active");
    expect(markup).toContain("Started by you");
    expect(markup).toContain("Technical problem");
    expect(markup).toContain("Finish problem");
    expect(markup).toContain("Not a problem");
    expect(markup).toContain("Why detected?");
    expect(markup).not.toContain("CandidateDetectionDecision");
    expect(markup).not.toContain("DetectionFeatureSet");
    expect(markup).not.toContain("https://");
    expect(markup).not.toContain("Spring Security");
  });

  it("does not present a reviewable Problem Session as actively running", () => {
    const reviewableSession = {
      ...activeSession(),
      endedAt: "2026-07-16T08:05:00.000Z",
      etag: "\"1\"",
      status: "REVIEWABLE",
      updatedAt: "2026-07-16T08:05:00.000Z",
      version: 1
    } satisfies ActiveProblemSessionPayload;
    const markup = renderToStaticMarkup(
      <TrailPopupApp
        client={new StaticPopupClient(captureState())}
        initialProblemSessionState={problemSessionState(reviewableSession)}
        initialState={captureState()}
        problemSessionClient={new StaticProblemSessionClient(problemSessionState(null))}
      />
    );

    expect(markup).not.toContain("Problem active");
    expect(markup).not.toContain("Finish problem");
    expect(markup).toContain("Problem capture is finished.");
    expect(markup).toContain("Choose what happened");
  });

  it("renders dismissed Not Problem undo state without mutating server state", () => {
    const markup = renderToStaticMarkup(
      <TrailPopupApp
        client={new StaticPopupClient(captureState())}
        initialProblemSessionState={problemSessionState(null, {
          id: "10000000-0000-4000-8000-000000000001",
          undoExpiresAt: "2026-07-16T08:00:05.000Z"
        })}
        initialState={captureState()}
        problemSessionClient={new StaticProblemSessionClient(problemSessionState(null))}
      />
    );

    expect(markup).toContain("Not a problem dismissed.");
    expect(markup).toContain("Undo");
  });

  it("maps detection reasons and command failures to user-safe copy", () => {
    expect(reasonCopy("multiple_related_sources")).toEqual({
      label: "Multiple related sources",
      text: "Trail saw activity across more than one technical source."
    });
    expect(reasonCopy("related_searches").text).not.toContain("query");
    expect(commandStatusMessage("network_error")).toBe("Trail could not reach the local service. The session did not change.");
    expect(commandStatusMessage("stale_version")).toBe("This problem session changed elsewhere. Reopen Trail and try again.");
  });
});

class StaticPopupClient implements PopupCaptureStateClient {
  constructor(private readonly state: CaptureStatePayload) {}

  excludeCurrentDomain(): Promise<PopupCaptureStateClientResult> {
    return Promise.resolve({ status: "received", state: this.state });
  }

  getState(): Promise<PopupCaptureStateClientResult> {
    return Promise.resolve({ status: "received", state: this.state });
  }

  pause(): Promise<PopupCaptureStateClientResult> {
    return Promise.resolve({ status: "received", state: this.state });
  }

  resume(): Promise<PopupCaptureStateClientResult> {
    return Promise.resolve({ status: "received", state: this.state });
  }

  undoDomainExclusion(): Promise<PopupCaptureStateClientResult> {
    return Promise.resolve({ status: "received", state: this.state });
  }
}

class StaticProblemSessionClient implements PopupProblemSessionClient {
  constructor(private readonly state: ProblemSessionResponsePayload) {}

  dismiss(): Promise<PopupProblemSessionClientResult> {
    return Promise.resolve({ payload: this.state, status: "received" });
  }

  finish(): Promise<PopupProblemSessionClientResult> {
    return Promise.resolve({ payload: this.state, status: "received" });
  }

  getState(): Promise<PopupProblemSessionClientResult> {
    return Promise.resolve({ payload: this.state, status: "received" });
  }

  start(_title: string): Promise<PopupProblemSessionClientResult> {
    return Promise.resolve({ payload: this.state, status: "received" });
  }

  undoDismiss(): Promise<PopupProblemSessionClientResult> {
    return Promise.resolve({ payload: this.state, status: "received" });
  }
}

function captureState(
  overrides: Partial<{
    readonly authStatus: CaptureStatePayload["authStatus"];
    readonly currentDomainExcluded: boolean;
    readonly paused: boolean;
    readonly popupState: CaptureStatePayload["popup"]["state"];
  }> = {}
): CaptureStatePayload {
  const popupState = overrides.popupState ?? (overrides.paused === true ? "paused" : "recording_ready");
  return {
    authStatus: overrides.authStatus ?? AUTH_STATUS.authenticated,
    consentStatus: "accepted",
    controls: {
      currentDomain: "docs.example.test",
      currentDomainExcluded: overrides.currentDomainExcluded ?? false,
      excludedDomainCount: overrides.currentDomainExcluded === true ? 1 : 0,
      paused: overrides.paused ?? false
    },
    network: { online: true },
    permissions: { missing: [], status: "granted" },
    popup: {
      primaryAction: popupState === "paused" ? "resume_capture" : "open_trail",
      state: popupState,
      statusLabel: popupState === "paused" ? "Trail is paused" : "Recording quietly"
    },
    queue: {
      maxBatchSize: 50,
      maxRecords: 1_000,
      oldestEnqueuedAt: null,
      remainingCapacity: 1_000,
      totalRecords: 0
    },
    sync: { status: "idle" }
  };
}

function problemSessionState(
  activeSession: ProblemSessionResponsePayload["state"]["activeSession"],
  dismissedSession: ProblemSessionResponsePayload["state"]["dismissedSession"] = null
): ProblemSessionResponsePayload {
  return {
    commandStatus: "received",
    state: {
      activeSession,
      dismissedSession
    }
  };
}

function activeSession(): ActiveProblemSessionPayload {
  return {
    detectionReasons: ["manual_session"],
    endedAt: null,
    etag: "\"0\"",
    id: "10000000-0000-4000-8000-000000000001",
    source: "MANUAL",
    startedAt: "2026-07-16T08:00:00.000Z",
    status: "CANDIDATE",
    title: "Technical problem",
    updatedAt: "2026-07-16T08:00:00.000Z",
    version: 0
  };
}
