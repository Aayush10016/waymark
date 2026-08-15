import type { CSSProperties, ReactElement } from "react";
import { useEffect, useMemo, useState } from "react";
import {
  AUTH_STATUS,
  type ActiveProblemSessionPayload,
  type CaptureStatePayload,
  type ProblemSessionResponsePayload
} from "@trail/extension-messages";
import {
  TRAIL_PRODUCT_NAME,
  trailColors,
  trailFocusRing,
  trailRadii,
  trailSpacing,
  trailTypography
} from "@trail/ui";
import {
  RuntimePopupCaptureStateClient,
  type PopupCaptureStateClient,
  type PopupCaptureStateClientResult
} from "./capture-state/capture-state-client";
import { popupCopyForState } from "./capture-state/popup-copy";
import {
  RuntimePopupProblemSessionClient,
  type PopupProblemSessionClient,
  type PopupProblemSessionClientResult
} from "./problem-session/problem-session-client";
import {
  commandStatusMessage,
  problemSessionSourceLabel,
  problemSessionStatusLabel,
  problemSessionTitle,
  reasonCopy
} from "./problem-session/problem-session-copy";
import {
  RuntimePopupRecallClient,
  recallStatusCopy,
  type PopupRecallClient
} from "./recall";
import {
  ChromePopupSurfaceNavigator,
  type PopupSurfaceNavigator
} from "./popup-surface-navigator";

export interface TrailPopupAppProps {
  readonly client?: PopupCaptureStateClient;
  readonly initialProblemSessionState?: ProblemSessionResponsePayload | null;
  readonly initialState?: CaptureStatePayload | null;
  readonly problemSessionClient?: PopupProblemSessionClient;
  readonly recallClient?: PopupRecallClient;
  readonly surfaceNavigator?: PopupSurfaceNavigator;
}

export function TrailPopupApp(props: TrailPopupAppProps): ReactElement {
  const client = useMemo(
    () => props.client ?? new RuntimePopupCaptureStateClient(),
    [props.client]
  );

  const problemSessionClient = useMemo(
    () => props.problemSessionClient ?? new RuntimePopupProblemSessionClient(),
    [props.problemSessionClient]
  );

  const recallClient = useMemo(
    () => props.recallClient ?? new RuntimePopupRecallClient(),
    [props.recallClient]
  );

  const surfaceNavigator = useMemo(
    () => props.surfaceNavigator ?? new ChromePopupSurfaceNavigator(),
    [props.surfaceNavigator]
  );

  const [state, setState] = useState<CaptureStatePayload | null>(
    props.initialState ?? null
  );

  const [problemSessionState, setProblemSessionState] =
    useState<ProblemSessionResponsePayload | null>(
      props.initialProblemSessionState ?? null
    );

  const [loading, setLoading] = useState(
    props.initialState === undefined
  );

  const [problemSessionLoading, setProblemSessionLoading] = useState(
    props.initialProblemSessionState === undefined
  );

  const [error, setError] = useState(false);
  const [problemSessionError, setProblemSessionError] =
    useState<string | null>(null);
  const [recallMessage, setRecallMessage] =
    useState<string | null>(null);
  const [whyDetectedOpen, setWhyDetectedOpen] = useState(false);
  const [startProblemOpen, setStartProblemOpen] = useState(false);
  const [problemTitleDraft, setProblemTitleDraft] = useState("");

  useEffect(() => {
    let active = true;

    void Promise.all([
      client.getState(),
      problemSessionClient.getState()
    ]).then(([captureResult, problemResult]) => {
      if (!active) {
        return;
      }

      applyCaptureResult(captureResult, setState, setError);
      applyProblemResult(
        problemResult,
        setProblemSessionState,
        setProblemSessionError
      );

      setLoading(false);
      setProblemSessionLoading(false);
    });

    return () => {
      active = false;
    };
  }, [client, problemSessionClient]);

  async function update(
    action: () => Promise<PopupCaptureStateClientResult>
  ): Promise<void> {
    setLoading(true);
    applyCaptureResult(await action(), setState, setError);
    setLoading(false);
  }

  async function updateProblemSession(
    action: () => Promise<PopupProblemSessionClientResult>
  ): Promise<void> {
    setProblemSessionLoading(true);

    applyProblemResult(
      await action(),
      setProblemSessionState,
      setProblemSessionError
    );

    setProblemSessionLoading(false);
  }

  async function startProblem(): Promise<void> {
    const title = problemTitleDraft.replace(/\s+/g, " ").trim();
    if (title.length === 0) {
      setProblemSessionError("Enter a short problem title first.");
      return;
    }

    await updateProblemSession(() => problemSessionClient.start(title));
    setStartProblemOpen(false);
    setProblemTitleDraft("");
  }

  function finishProblem(): void {
    surfaceNavigator.openEndSessionPanel();
    void updateProblemSession(() => problemSessionClient.finish());
  }

  async function invokeRecall(): Promise<void> {
    surfaceNavigator.openRecallPanel();

    const result = await recallClient.invoke();

    setRecallMessage(
      result.status === "received"
        ? recallStatusCopy(result.payload)
        : "Recall is temporarily unavailable."
    );
  }

  function handlePrimaryAction(): void {
    if (state === null) {
      return;
    }

    if (
      state.popup.primaryAction === "open_onboarding" ||
      state.popup.primaryAction === "restore_permission"
    ) {
      surfaceNavigator.openOnboarding();
      return;
    }

    if (state.popup.primaryAction === "resume_capture") {
      void update(() => client.resume());
      return;
    }

    surfaceNavigator.openApp();
  }

  if (loading && state === null) {
    return (
      <main aria-label="Trail extension popup" style={styles.shell}>
        <style>{popupCss}</style>
        <span aria-hidden="true" style={styles.loadingLine} />
        <p style={styles.body}>Loading {TRAIL_PRODUCT_NAME}…</p>
      </main>
    );
  }

  if (state === null) {
    return (
      <main aria-label="Trail extension popup" style={styles.shell}>
        <style>{popupCss}</style>
        <h1 style={styles.title}>Trail unavailable</h1>
        <p role="alert" style={styles.body}>
          Open Trail again. Local capture controls remain on this device.
        </p>
      </main>
    );
  }

  const copy = popupCopyForState(state);
  const pauseButtonLabel = state.controls.paused ? "Resume" : "Pause";

  const activeSession =
    problemSessionState?.state.activeSession ?? null;
  const activeCaptureSession =
    activeSession?.status === "CANDIDATE"
      ? activeSession
      : null;
  const reviewableSession =
    activeSession?.status === "REVIEWABLE"
      ? activeSession
      : null;

  const canUseRecall =
    state.authStatus === AUTH_STATUS.authenticated;

  const canUseProblemSession =
    canUseRecall && state.consentStatus === "accepted";

  const showProblemSessionCard =
    canUseProblemSession &&
    state.permissions.status === "granted" &&
    !state.controls.currentDomainExcluded &&
    activeCaptureSession !== null;

  const showStartProblem =
    canUseProblemSession &&
    state.permissions.status === "granted" &&
    !state.controls.currentDomainExcluded &&
    activeSession === null;

  return (
    <main aria-label="Trail extension popup" style={styles.shell}>
      <style>{popupCss}</style>

      <header style={styles.header}>
        <div style={styles.brandLockup}>
          <span aria-hidden="true" style={styles.mark}>
            T
          </span>
          <span style={styles.brandText}>
            {TRAIL_PRODUCT_NAME}
          </span>
        </div>

        <span
          style={statusPillStyle(
            state,
            showProblemSessionCard
          )}
        >
          <span
            aria-hidden="true"
            style={statusDotStyle(
              state,
              showProblemSessionCard
            )}
          />

          {showProblemSessionCard
            ? "Problem active"
            : userFacingStatusLabel(state)}
        </span>
      </header>

      {showProblemSessionCard ? (
        <ProblemSessionCard
          disabled={problemSessionLoading}
          error={problemSessionError}
          onDismiss={() =>
            void updateProblemSession(() =>
              problemSessionClient.dismiss()
            )
          }
          onFinish={finishProblem}
          onToggleWhyDetected={() =>
            setWhyDetectedOpen((current) => !current)
          }
          session={activeCaptureSession}
          whyDetectedOpen={whyDetectedOpen}
        />
      ) : (
        <section
          aria-labelledby="trail-popup-heading"
          style={styles.card}
        >
          <h1
            id="trail-popup-heading"
            style={styles.title}
          >
            {copy.title}
          </h1>

          <p style={styles.body}>
            {copy.body}
          </p>

          {error ? (
            <p style={styles.error}>
              Capture state could not refresh. Try reopening
              Trail.
            </p>
          ) : null}

          {problemSessionState?.state.dismissedSession !==
            null &&
          problemSessionState?.state.dismissedSession !==
            undefined ? (
            <p style={styles.notice}>
              Not a problem dismissed.{" "}
              <button
                disabled={problemSessionLoading}
                onClick={() =>
                  void updateProblemSession(() =>
                    problemSessionClient.undoDismiss()
                  )
                }
                style={inlineButtonStyle}
                type="button"
              >
                Undo
              </button>
            </p>
          ) : null}

          {showStartProblem ? (
            startProblemOpen ? (
              <form
                aria-label="Start Problem Session"
                onSubmit={(event) => {
                  event.preventDefault();
                  void startProblem();
                }}
                style={styles.startProblemForm}
              >
                <label style={styles.fieldLabel}>
                  Problem title
                  <input
                    autoFocus
                    className="trail-field"
                    maxLength={200}
                    onChange={(event) =>
                      setProblemTitleDraft(
                        event.currentTarget.value.replace(/\s+/g, " ")
                      )
                    }
                    placeholder="JWT authentication failing"
                    style={styles.textField}
                    value={problemTitleDraft}
                  />
                </label>
                <p style={styles.body}>
                  Use the phrase you would search for later.
                </p>
              </form>
            ) : (
              <p style={styles.body}>
                Start a titled Problem Session when you want
                Trail to remember this debugging work.
              </p>
            )
          ) : null}

          {reviewableSession !== null ? (
            <p style={styles.notice}>
              Problem capture is finished. Choose what happened
              in the side panel.
            </p>
          ) : null}

          {problemSessionError !== null ? (
            <p style={styles.error}>
              {problemSessionError}
            </p>
          ) : null}
        </section>
      )}

      {recallMessage !== null ? (
        <p
          aria-live="polite"
          role="status"
          style={styles.notice}
        >
          {recallMessage}
        </p>
      ) : null}

      <section
        aria-label="Capture summary"
        style={styles.summary}
      >
        <span style={styles.summaryValue}>
          {state.controls.currentDomain ??
            "No supported page"}
        </span>

      </section>

      <footer style={styles.actions}>
        {showStartProblem ? (
          <button
            autoFocus
            className="trail-button"
            disabled={
              problemSessionLoading ||
              (startProblemOpen &&
                problemTitleDraft.trim().length === 0)
            }
            onClick={() => {
              if (!startProblemOpen) {
                setStartProblemOpen(true);
                return;
              }
              void startProblem();
            }}
            style={buttonStyle("primary")}
            type="button"
          >
            {problemSessionLoading ? "Starting..." : startProblemOpen ? "Start" : "Start problem"}
          </button>
        ) : (
          <button
            className="trail-button"
            onClick={handlePrimaryAction}
            style={buttonStyle("primary")}
            type="button"
          >
            {copy.primaryLabel}
          </button>
        )}

        {canUseRecall ? (
          <button
            className="trail-button"
            onClick={() => void invokeRecall()}
            style={buttonStyle("secondary")}
            type="button"
          >
            Recall
          </button>
        ) : null}

        <button
          className="trail-button"
          onClick={() => surfaceNavigator.openOnboarding()}
          style={buttonStyle("ghost")}
          type="button"
        >
          Privacy & Consent
        </button>

        <button
          className="trail-button"
          onClick={() =>
            void update(() =>
              state.controls.paused
                ? client.resume()
                : client.pause()
            )
          }
          style={buttonStyle("quiet")}
          type="button"
        >
          {pauseButtonLabel}
        </button>

        <button
          className="trail-button"
          disabled={
            state.controls.currentDomain === null
          }
          onClick={() =>
            void update(() =>
              client.excludeCurrentDomain()
            )
          }
          style={buttonStyle("quiet")}
          type="button"
        >
          Block site
        </button>

        {state.controls.currentDomainExcluded ? (
          <button
            className="trail-button"
            onClick={() =>
              void update(() =>
                client.undoDomainExclusion()
              )
            }
            style={buttonStyle("ghost")}
            type="button"
          >
            Undo block
          </button>
        ) : null}
      </footer>
    </main>
  );
}

function ProblemSessionCard(props: {
  readonly disabled: boolean;
  readonly error: string | null;
  readonly onDismiss: () => void;
  readonly onFinish: () => void;
  readonly onToggleWhyDetected: () => void;
  readonly session: ActiveProblemSessionPayload;
  readonly whyDetectedOpen: boolean;
}): ReactElement {
  return (
    <section
      aria-labelledby="trail-popup-heading"
      style={styles.card}
    >
      <p style={styles.eyebrow}>
        {problemSessionSourceLabel(props.session)}
      </p>

      <h1
        id="trail-popup-heading"
        style={styles.title}
      >
        {problemSessionTitle(props.session)}
      </h1>

      <p style={styles.body}>
        {problemSessionStatusLabel(props.session)}. Trail is
        keeping this session bounded and private.
      </p>

      <div style={styles.actions}>
        <button
          autoFocus
          className="trail-button"
          disabled={props.disabled}
          onClick={props.onFinish}
          style={buttonStyle("primary")}
          type="button"
        >
          {props.disabled
            ? "Finishing..."
            : "Finish problem"}
        </button>

        <button
          className="trail-button"
          disabled={props.disabled}
          onClick={props.onDismiss}
          style={buttonStyle("secondary")}
          type="button"
        >
          Not a problem
        </button>

        <button
          aria-expanded={props.whyDetectedOpen}
          className="trail-button"
          disabled={props.disabled}
          onClick={props.onToggleWhyDetected}
          style={buttonStyle("ghost")}
          type="button"
        >
          Why detected?
        </button>
      </div>

      {props.whyDetectedOpen ? (
        <ul
          aria-label="Detection evidence"
          style={styles.reasonList}
        >
          {props.session.detectionReasons.map((reason) => {
            const copy = reasonCopy(reason);

            return (
              <li key={reason} style={styles.reasonItem}>
                <span style={styles.reasonLabel}>
                  {copy.label}
                </span>

                <span style={styles.reasonText}>
                  {copy.text}
                </span>
              </li>
            );
          })}
        </ul>
      ) : null}

      {props.error !== null ? (
        <p style={styles.error}>
          {props.error}
        </p>
      ) : null}
    </section>
  );
}

function applyCaptureResult(
  result: PopupCaptureStateClientResult,
  setState: (state: CaptureStatePayload) => void,
  setError: (error: boolean) => void
): void {
  if (result.status !== "received") {
    setError(true);
    return;
  }

  setState(result.state);
  setError(false);
}

function applyProblemResult(
  result: PopupProblemSessionClientResult,
  setState: (
    state: ProblemSessionResponsePayload
  ) => void,
  setError: (error: string | null) => void
): void {
  if (result.status !== "received") {
    setError(
      "Problem Session state could not refresh. Try reopening Trail."
    );
    return;
  }

  setState(result.payload);
  setError(
    commandStatusMessage(
      result.payload.commandStatus
    )
  );
}

function statusDotStyle(
  state: CaptureStatePayload,
  problemSessionActive: boolean
): CSSProperties {
  const color = problemSessionActive
    ? trailColors.accent
    : state.popup.state === "recording_ready"
      ? trailColors.success
      : state.popup.state === "offline"
        ? trailColors.warning
        : trailColors.textMuted;

  return {
    background: color,
    borderRadius: trailRadii.pill,
    display: "inline-block",
    height: "8px",
    width: "8px"
  };
}

function statusPillStyle(
  state: CaptureStatePayload,
  problemSessionActive: boolean
): CSSProperties {
  const isAttentionState =
    state.popup.state === "auth_required" ||
    state.popup.state === "permission_missing";

  const borderColor = problemSessionActive
    ? "rgba(139, 124, 255, 0.46)"
    : isAttentionState
      ? "rgba(240, 217, 161, 0.34)"
      : trailColors.borderMuted;

  return {
    alignItems: "center",
    background: problemSessionActive
      ? "rgba(139, 124, 255, 0.12)"
      : isAttentionState
        ? trailColors.warningSurface
        : trailColors.surfaceSubtle,
    border: `1px solid ${borderColor}`,
    borderRadius: trailRadii.pill,
    color: isAttentionState
      ? trailColors.warning
      : trailColors.textSubtle,
    display: "inline-flex",
    fontSize: trailTypography.caption,
    fontWeight: 750,
    gap: trailSpacing.sm,
    padding: "6px 9px"
  };
}

function userFacingStatusLabel(
  state: CaptureStatePayload
): string {
  if (state.popup.state === "auth_required") {
    return "Signed out";
  }

  if (state.popup.state === "domain_excluded") {
    return "Blocked here";
  }

  if (state.popup.state === "recording_ready") {
    return "Ready";
  }

  return state.popup.statusLabel;
}

function buttonStyle(
  variant:
    | "ghost"
    | "primary"
    | "quiet"
    | "secondary"
): CSSProperties {
  const base: CSSProperties = {
    borderRadius: trailRadii.button,
    cursor: "pointer",
    fontSize: trailTypography.control,
    fontWeight: 650,
    lineHeight: 1,
    minHeight: "34px",
    padding: "9px 11px"
  };

  if (variant === "primary") {
    return {
      ...base,
      background: trailColors.accent,
      border: `1px solid ${trailColors.accentStrong}`,
      color: trailColors.background
    };
  }

  if (variant === "secondary") {
    return {
      ...base,
      background: trailColors.surfaceSubtle,
      border: `1px solid ${trailColors.border}`,
      color: trailColors.text
    };
  }

  if (variant === "quiet") {
    return {
      ...base,
      background: "transparent",
      border: `1px solid ${trailColors.borderMuted}`,
      color: trailColors.textSubtle
    };
  }

  return {
    ...base,
    background: "transparent",
    border: "1px solid transparent",
    color: trailColors.textMuted
  };
}

const inlineButtonStyle: CSSProperties = {
  background: "transparent",
  border: 0,
  color: trailColors.accentStrong,
  cursor: "pointer",
  font: "inherit",
  fontWeight: 700,
  padding: 0
};

const popupCss = `
  .trail-button {
    transition:
      background 140ms ease,
      border-color 140ms ease,
      color 140ms ease,
      transform 140ms ease;
  }

  .trail-button:hover:not(:disabled) {
    transform: translateY(-1px);
  }

  .trail-button:focus-visible {
    box-shadow: ${trailFocusRing};
    outline: none;
  }

  .trail-field:focus-visible {
    box-shadow: ${trailFocusRing};
    outline: none;
  }

  .trail-button:disabled {
    cursor: not-allowed;
    opacity: 0.48;
    transform: none;
  }

  @media (prefers-reduced-motion: reduce) {
    .trail-button {
      transition: none;
    }

    .trail-button:hover:not(:disabled) {
      transform: none;
    }
  }
`;

const styles = {
  actions: {
    display: "flex",
    flexWrap: "wrap",
    gap: trailSpacing.sm
  },

  brandLockup: {
    alignItems: "center",
    display: "inline-flex",
    gap: trailSpacing.sm
  },

  brandText: {
    color: trailColors.text,
    fontSize: trailTypography.body,
    fontWeight: 800,
    letterSpacing: "-0.02em"
  },

  body: {
    color: trailColors.textMuted,
    fontSize: trailTypography.body,
    lineHeight: 1.5,
    margin: `${trailSpacing.sm} 0 0`
  },

  card: {
    background: trailColors.surfaceElevated,
    border: `1px solid ${trailColors.border}`,
    borderRadius: trailRadii.field,
    padding: trailSpacing.md
  },

  error: {
    background: trailColors.dangerSurface,
    border: "1px solid rgba(255, 180, 180, 0.26)",
    borderRadius: trailRadii.button,
    color: trailColors.danger,
    fontSize: trailTypography.control,
    margin: `${trailSpacing.sm} 0 0`,
    padding: `${trailSpacing.sm} ${trailSpacing.md}`
  },
  fieldLabel: {
    color: trailColors.textSubtle,
    display: "grid",
    fontSize: trailTypography.control,
    fontWeight: 750,
    gap: "7px"
  },

  eyebrow: {
    color: trailColors.accentStrong,
    fontSize: trailTypography.caption,
    fontWeight: 800,
    letterSpacing: "0.06em",
    margin: `0 0 ${trailSpacing.sm}`,
    textTransform: "uppercase"
  },

  header: {
    alignItems: "center",
    display: "flex",
    gap: trailSpacing.md,
    justifyContent: "space-between"
  },

  loadingLine: {
    background: `linear-gradient(90deg, ${trailColors.accent}, transparent)`,
    borderRadius: trailRadii.pill,
    height: "2px"
  },

  mark: {
    alignItems: "center",
    background: trailColors.surfaceSubtle,
    border: `1px solid ${trailColors.border}`,
    borderRadius: "9px",
    color: trailColors.accentMuted,
    display: "inline-flex",
    fontSize: "12px",
    fontWeight: 900,
    height: "24px",
    justifyContent: "center",
    width: "24px"
  },

  notice: {
    background: trailColors.surface,
    border: `1px solid ${trailColors.borderMuted}`,
    borderRadius: trailRadii.button,
    color: trailColors.textSubtle,
    fontSize: trailTypography.control,
    lineHeight: 1.45,
    margin: 0,
    padding: `${trailSpacing.sm} ${trailSpacing.md}`
  },

  reasonItem: {
    display: "grid",
    gap: "2px"
  },

  reasonLabel: {
    color: trailColors.text,
    fontSize: trailTypography.control,
    fontWeight: 700
  },

  reasonList: {
    color: trailColors.textMuted,
    display: "grid",
    gap: trailSpacing.sm,
    listStyle: "none",
    margin: `${trailSpacing.md} 0 0`,
    padding: 0
  },

  reasonText: {
    color: trailColors.textMuted,
    fontSize: trailTypography.control,
    lineHeight: 1.4
  },

  shell: {
    background: trailColors.background,
    color: trailColors.text,
    display: "grid",
    fontFamily: trailTypography.family,
    gap: trailSpacing.md,
    minWidth: "330px",
    padding: trailSpacing.lg
  },
  startProblemForm: {
    display: "grid",
    gap: trailSpacing.sm
  },

  summary: {
    alignItems: "center",
    color: trailColors.textMuted,
    display: "flex",
    gap: trailSpacing.sm,
    minWidth: 0
  },

  summaryValue: {
    color: trailColors.textSubtle,
    fontSize: trailTypography.control,
    overflow: "hidden",
    textOverflow: "ellipsis",
    whiteSpace: "nowrap"
  },
  textField: {
    background: trailColors.surface,
    border: `1px solid ${trailColors.border}`,
    borderRadius: trailRadii.field,
    color: trailColors.text,
    font: "inherit",
    padding: "10px 11px"
  },

  title: {
    color: trailColors.text,
    fontSize: trailTypography.title,
    letterSpacing: "-0.04em",
    margin: 0
  }
} satisfies Record<string, CSSProperties>;
