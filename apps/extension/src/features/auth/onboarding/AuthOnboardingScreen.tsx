import type { CSSProperties, FormEvent, ReactElement } from "react";
import { useEffect, useMemo, useReducer, useRef, useState } from "react";
import { TRAIL_PRODUCT_NAME, trailColors, trailFocusRing, trailRadii, trailSpacing, trailTypography } from "@trail/ui";
import { trailApiBaseUrl } from "../../../shared/config/api-base-url";
import { FetchAuthOnboardingClient, type AuthOnboardingClient } from "./auth-onboarding-client";
import { RuntimeAuthSessionSink, type AuthSessionSink } from "./auth-session-sink";
import { submitAuthOnboardingState } from "./auth-onboarding-submit";
import {
  AUTH_ONBOARDING_MODES,
  AUTH_ONBOARDING_NOTICE_CODES,
  AUTH_ONBOARDING_STATUS,
  canStartTrail,
  initialAuthOnboardingState,
  reduceAuthOnboardingState
} from "./auth-onboarding-state";
import { passwordVisibilityState, togglePasswordVisibility } from "./password-visibility";

export interface AuthOnboardingScreenProps {
  readonly client?: AuthOnboardingClient;
  readonly sessionSink?: AuthSessionSink;
  readonly apiBaseUrl?: string;
  readonly online?: () => boolean;
  readonly onStartTrail?: () => void;
}

const GENERIC_AUTH_ERROR = "We could not complete that step. Check your details and try again.";
const OFFLINE_ERROR = "You appear offline. Your progress is still here. Try again when connected.";
const SERVICE_UNAVAILABLE_ERROR = "Trail cannot reach the local service. Confirm the backend is running, then try again.";

export function AuthOnboardingScreen(props: AuthOnboardingScreenProps): ReactElement {
  const client = useMemo(
    () => props.client ?? new FetchAuthOnboardingClient({ baseUrl: props.apiBaseUrl ?? trailApiBaseUrl() }),
    [props.apiBaseUrl, props.client]
  );
  const sessionSink = useMemo(() => props.sessionSink ?? new RuntimeAuthSessionSink(), [props.sessionSink]);
  const isOnline = props.online ?? browserOnlineStatus;
  const [state, dispatch] = useReducer(reduceAuthOnboardingState, undefined, initialAuthOnboardingState);
  const [passwordVisible, setPasswordVisible] = useState(false);
  const [newPasswordVisible, setNewPasswordVisible] = useState(false);
  const firstFieldRef = useRef<HTMLInputElement>(null);
  const busy = state.status === AUTH_ONBOARDING_STATUS.loading;

  useEffect(() => {
    firstFieldRef.current?.focus();
  }, [state.mode]);

  function submit(event: FormEvent<HTMLFormElement>): void {
    event.preventDefault();

    if (!isOnline()) {
      dispatch({ type: "OFFLINE_FAILURE" });
      return;
    }

    dispatch({ type: "SUBMIT_STARTED" });
    void submitCurrentMode();
  }

  async function submitCurrentMode(): Promise<void> {
    dispatch(await submitAuthOnboardingState(state, { client, sessionSink }));
  }

  function startTrail(): void {
    if (canStartTrail(state)) {
      props.onStartTrail?.();
    }
  }

  function resendVerification(): void {
    if (!isOnline()) {
      dispatch({ type: "OFFLINE_FAILURE" });
      return;
    }

    dispatch({ type: "SUBMIT_STARTED" });
    void client.requestEmailVerification({ email: state.email }).then((result) => {
      if (result.status === "ACCEPTED") {
        dispatch({ type: "REGISTRATION_ACCEPTED" });
        return;
      }

      dispatch(result.status === "SERVICE_UNAVAILABLE" ? { type: "SERVICE_UNAVAILABLE_FAILURE" } : { type: "GENERIC_FAILURE" });
    });
  }

  return (
    <main aria-label="Trail onboarding" className="trail-auth-screen" style={styles.screen}>
      <style>{authOnboardingCss}</style>
      <section className="trail-auth-hero" style={styles.hero}>
        <p style={styles.eyebrow}>{TRAIL_PRODUCT_NAME}</p>
        <h1 style={styles.title}>Never solve the same engineering problem twice.</h1>
        <p style={styles.copy}>
          Create your private engineering memory. Trail keeps capture explicit and stays quiet while you debug.
        </p>
      </section>

      <section aria-labelledby="auth-heading" className="trail-auth-card" style={styles.card}>
        <div style={styles.headerRow}>
          <div>
            <h2 id="auth-heading" style={styles.cardTitle}>
              {headingForMode(state.mode)}
            </h2>
            <p style={styles.cardCopy}>{subheadingForMode(state.mode)}</p>
          </div>
        </div>

        {state.mode === AUTH_ONBOARDING_MODES.startTrail ? (
          <div>
            <p style={styles.successCopy}>
              Your private memory is connected. Review the capture privacy boundary before Trail records browser
              metadata.
            </p>
            <button className="trail-auth-button" disabled={!canStartTrail(state)} onClick={startTrail} style={buttonStyle("primary")} type="button">
              Continue
            </button>
          </div>
        ) : (
          <form aria-busy={busy} onSubmit={submit} style={styles.form}>
            {renderFields()}
            {renderNotice()}
            <button className="trail-auth-button" disabled={busy} style={buttonStyle("primary")} type="submit">
              {busy ? "Connecting your private memory…" : primaryActionForMode(state.mode)}
            </button>
          </form>
        )}

        {renderSecondaryActions()}
      </section>
    </main>
  );

  function renderFields(): ReactElement {
    if (state.mode === AUTH_ONBOARDING_MODES.verifyEmail) {
      return (
        <fieldset disabled={busy} style={styles.fieldset}>
          <label style={styles.fieldLabel}>
            Verification code
            <input
              ref={firstFieldRef}
              autoComplete="one-time-code"
              className="trail-auth-input"
              onChange={(event) =>
                dispatch({ type: "CHANGE_VERIFICATION_TOKEN", value: event.currentTarget.value })
              }
              required
              style={styles.input}
              value={state.verificationToken}
            />
          </label>
        </fieldset>
      );
    }

    if (state.mode === AUTH_ONBOARDING_MODES.resetRequested) {
      return (
        <fieldset disabled={busy} style={styles.fieldset}>
          <label style={styles.fieldLabel}>
            Reset code
            <input
              ref={firstFieldRef}
              autoComplete="one-time-code"
              className="trail-auth-input"
              onChange={(event) => dispatch({ type: "CHANGE_RESET_TOKEN", value: event.currentTarget.value })}
              required
              style={styles.input}
              value={state.resetToken}
            />
          </label>
          <label style={styles.fieldLabel}>
            New password
            <span style={styles.passwordField}>
              <input
                autoComplete="new-password"
                className="trail-auth-input"
                minLength={12}
                onChange={(event) => dispatch({ type: "CHANGE_NEW_PASSWORD", value: event.currentTarget.value })}
                required
                style={styles.passwordInput}
              type={passwordVisibilityState(newPasswordVisible).inputType}
                value={state.newPassword}
              />
              <PasswordVisibilityButton
                visible={newPasswordVisible}
                onToggle={() => setNewPasswordVisible(togglePasswordVisibility)}
              />
            </span>
          </label>
        </fieldset>
      );
    }

    return (
      <fieldset disabled={busy} style={styles.fieldset}>
        <label style={styles.fieldLabel}>
          Email
          <input
            ref={firstFieldRef}
            autoComplete="email"
            className="trail-auth-input"
            inputMode="email"
            onChange={(event) => dispatch({ type: "CHANGE_EMAIL", value: event.currentTarget.value })}
            required
            style={styles.input}
            type="email"
            value={state.email}
          />
        </label>
        {state.mode !== AUTH_ONBOARDING_MODES.resetPassword ? (
          <label style={styles.fieldLabel}>
            Password
            <span style={styles.passwordField}>
              <input
                autoComplete={state.mode === AUTH_ONBOARDING_MODES.createAccount ? "new-password" : "current-password"}
                className="trail-auth-input"
                minLength={state.mode === AUTH_ONBOARDING_MODES.createAccount ? 12 : undefined}
                onChange={(event) => dispatch({ type: "CHANGE_PASSWORD", value: event.currentTarget.value })}
                required
                style={styles.passwordInput}
                type={passwordVisibilityState(passwordVisible).inputType}
                value={state.password}
              />
              <PasswordVisibilityButton
                visible={passwordVisible}
                onToggle={() => setPasswordVisible(togglePasswordVisibility)}
              />
            </span>
          </label>
        ) : null}
      </fieldset>
    );
  }

  function renderNotice(): ReactElement | null {
    if (state.noticeCode === null) {
      return null;
    }

    const copy =
      state.noticeCode === AUTH_ONBOARDING_NOTICE_CODES.offline
        ? OFFLINE_ERROR
        : state.noticeCode === AUTH_ONBOARDING_NOTICE_CODES.serviceUnavailable
          ? SERVICE_UNAVAILABLE_ERROR
        : noticeCopyForCode(state.noticeCode);

    return (
      <p aria-live="polite" role={state.noticeCode === AUTH_ONBOARDING_NOTICE_CODES.genericFailure ? "alert" : "status"} style={styles.notice}>
        {copy}
      </p>
    );
  }

  function renderSecondaryActions(): ReactElement {
    if (state.mode === AUTH_ONBOARDING_MODES.verifyEmail) {
      return (
        <div style={styles.secondaryRow}>
          <button className="trail-auth-button" disabled={busy} onClick={resendVerification} style={buttonStyle("secondary")} type="button">
            Resend email
          </button>
          <button className="trail-auth-button" onClick={() => dispatch({ type: "SHOW_SIGN_IN" })} style={buttonStyle("ghost")} type="button">
            Sign in
          </button>
        </div>
      );
    }

    if (state.mode === AUTH_ONBOARDING_MODES.signIn) {
      return (
        <div style={styles.secondaryRow}>
          <button className="trail-auth-button" onClick={() => dispatch({ type: "SHOW_CREATE_ACCOUNT" })} style={buttonStyle("secondary")} type="button">
            Create account
          </button>
          <button className="trail-auth-button" onClick={() => dispatch({ type: "SHOW_FORGOT_PASSWORD" })} style={buttonStyle("ghost")} type="button">
            Forgot password
          </button>
        </div>
      );
    }

    if (state.mode === AUTH_ONBOARDING_MODES.resetRequested) {
      return (
        <div style={styles.secondaryRow}>
          <button className="trail-auth-button" onClick={() => dispatch({ type: "SHOW_SIGN_IN" })} style={buttonStyle("ghost")} type="button">
            Back to sign in
          </button>
        </div>
      );
    }

    if (state.mode === AUTH_ONBOARDING_MODES.startTrail) {
      return <p style={styles.hint}>Capture stays disabled until you explicitly consent on the next step.</p>;
    }

    return (
      <div style={styles.secondaryRow}>
        <button className="trail-auth-button" onClick={() => dispatch({ type: "SHOW_SIGN_IN" })} style={buttonStyle("secondary")} type="button">
          Sign in
        </button>
      </div>
    );
  }
}

function browserOnlineStatus(): boolean {
  return typeof navigator === "undefined" ? true : navigator.onLine;
}

function headingForMode(mode: string): string {
  if (mode === AUTH_ONBOARDING_MODES.createAccount) {
    return "Create account";
  }
  if (mode === AUTH_ONBOARDING_MODES.verifyEmail) {
    return "Verify your email";
  }
  if (mode === AUTH_ONBOARDING_MODES.resetPassword) {
    return "Reset your password";
  }
  if (mode === AUTH_ONBOARDING_MODES.resetRequested) {
    return "Check your email";
  }
  if (mode === AUTH_ONBOARDING_MODES.startTrail) {
    return "Ready";
  }
  return "Sign in";
}

function subheadingForMode(mode: string): string {
  if (mode === AUTH_ONBOARDING_MODES.createAccount) {
    return "Start a private memory for solved engineering problems.";
  }
  if (mode === AUTH_ONBOARDING_MODES.verifyEmail) {
    return "Enter the verification code from your Trail email.";
  }
  if (mode === AUTH_ONBOARDING_MODES.resetPassword) {
    return "Request a reset link without exposing account existence.";
  }
  if (mode === AUTH_ONBOARDING_MODES.resetRequested) {
    return "Paste the reset code and choose a new password.";
  }
  if (mode === AUTH_ONBOARDING_MODES.startTrail) {
    return "Review capture privacy before recording begins.";
  }
  return "Use your Trail account to sync your engineering memory.";
}

function primaryActionForMode(mode: string): string {
  if (mode === AUTH_ONBOARDING_MODES.createAccount) {
    return "Create account";
  }
  if (mode === AUTH_ONBOARDING_MODES.verifyEmail) {
    return "Verify email";
  }
  if (mode === AUTH_ONBOARDING_MODES.resetPassword) {
    return "Send reset email";
  }
  if (mode === AUTH_ONBOARDING_MODES.resetRequested) {
    return "Reset password";
  }
  return "Sign in";
}

function noticeCopyForCode(code: string): string {
  if (code === AUTH_ONBOARDING_NOTICE_CODES.passwordResetAccepted) {
    return "If the account can be recovered, reset instructions are on the way.";
  }
  if (code === AUTH_ONBOARDING_NOTICE_CODES.verificationRequired) {
    return "Check your email to verify Trail before capture can begin.";
  }
  if (code === AUTH_ONBOARDING_NOTICE_CODES.ready) {
    return "Your private memory is connected.";
  }
  return GENERIC_AUTH_ERROR;
}

function buttonStyle(variant: "ghost" | "primary" | "secondary"): CSSProperties {
  const base: CSSProperties = {
    borderRadius: "12px",
    cursor: "pointer",
    fontSize: "14px",
    fontWeight: 650,
    minHeight: "42px",
    padding: "10px 14px"
  };

  if (variant === "primary") {
    return {
      ...base,
      background: trailColors.accent,
      border: `1px solid ${trailColors.accentStrong}`,
      color: trailColors.background,
      width: "100%"
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

  return {
    ...base,
    background: "transparent",
    border: "1px solid transparent",
    color: trailColors.textMuted
  };
}

function PasswordVisibilityButton(props: {
  readonly onToggle: () => void;
  readonly visible: boolean;
}): ReactElement {
  const visibility = passwordVisibilityState(props.visible);
  return (
    <button
      aria-label={visibility.label}
      className="trail-auth-password-toggle"
      onClick={props.onToggle}
      style={styles.passwordToggle}
      type="button"
    >
      <span aria-hidden="true">{props.visible ? "Hide" : "Show"}</span>
    </button>
  );
}

const authOnboardingCss = `
  html,
  body,
  #root {
    min-height: 100%;
    margin: 0;
  }
  *,
  *::before,
  *::after {
    box-sizing: border-box;
  }
  .trail-auth-button:focus-visible,
  .trail-auth-input:focus-visible,
  .trail-auth-password-toggle:focus-visible {
    box-shadow: ${trailFocusRing};
    outline: none;
  }
  .trail-auth-button:disabled {
    cursor: not-allowed;
    opacity: 0.54;
  }
  @media (max-width: 860px) {
    .trail-auth-screen {
      align-content: start !important;
      grid-template-columns: minmax(0, 1fr) !important;
    }
    .trail-auth-hero {
      max-width: 440px !important;
    }
    .trail-auth-card {
      max-width: 440px !important;
      width: 100% !important;
    }
  }
  @media (max-height: 720px) and (min-width: 861px) {
    .trail-auth-screen {
      align-items: start !important;
      padding-block: 24px !important;
    }
  }
`;

const styles = {
  screen: {
    alignItems: "center",
    background: trailColors.background,
    boxSizing: "border-box",
    color: trailColors.text,
    display: "grid",
    fontFamily: trailTypography.family,
    gap: trailSpacing["3xl"],
    gridTemplateColumns: "minmax(280px, 1fr) minmax(340px, 400px)",
    justifyContent: "center",
    margin: 0,
    minHeight: "100dvh",
    padding: "clamp(24px, 4vh, 40px) clamp(24px, 5vw, 56px)",
    width: "100%"
  },
  hero: {
    maxWidth: "460px"
  },
  eyebrow: {
    color: trailColors.accentStrong,
    fontSize: trailTypography.caption,
    fontWeight: 850,
    letterSpacing: "0.18em",
    margin: `0 0 ${trailSpacing.md}`,
    textTransform: "uppercase"
  },
  title: {
    fontSize: "clamp(30px, 4vw, 40px)",
    letterSpacing: "-0.055em",
    lineHeight: 1.03,
    margin: `0 0 ${trailSpacing.lg}`
  },
  copy: {
    color: trailColors.textMuted,
    fontSize: "15px",
    lineHeight: 1.55,
    margin: 0,
    maxWidth: "420px"
  },
  card: {
    background: trailColors.surfaceElevated,
    border: `1px solid ${trailColors.border}`,
    borderRadius: trailRadii.card,
    boxShadow: "0 18px 58px rgba(0, 0, 0, 0.34)",
    justifySelf: "end",
    maxWidth: "400px",
    padding: trailSpacing.xl,
    width: "100%"
  },
  headerRow: {
    marginBottom: trailSpacing.xl
  },
  cardCopy: {
    color: trailColors.textMuted,
    fontSize: trailTypography.body,
    lineHeight: 1.5,
    margin: `${trailSpacing.sm} 0 0`
  },
  cardTitle: {
    color: trailColors.text,
    fontSize: "22px",
    letterSpacing: "-0.03em",
    margin: 0
  },
  fieldset: {
    border: 0,
    display: "grid",
    gap: trailSpacing.lg,
    margin: 0,
    padding: 0
  },
  form: {
    display: "grid",
    gap: trailSpacing.lg
  },
  fieldLabel: {
    color: trailColors.textMuted,
    display: "grid",
    fontSize: "13px",
    fontWeight: 650,
    gap: trailSpacing.sm
  },
  input: {
    background: trailColors.background,
    border: `1px solid ${trailColors.border}`,
    borderRadius: trailRadii.button,
    color: trailColors.text,
    font: "inherit",
    minHeight: "44px",
    outline: "none",
    padding: "11px 13px",
    width: "100%"
  },
  notice: {
    background: trailColors.surfaceSubtle,
    border: `1px solid ${trailColors.border}`,
    borderRadius: trailRadii.field,
    color: trailColors.textSubtle,
    fontSize: "13px",
    lineHeight: 1.5,
    margin: 0,
    padding: "12px"
  },
  passwordField: {
    display: "block",
    position: "relative"
  },
  passwordInput: {
    background: trailColors.background,
    border: `1px solid ${trailColors.border}`,
    borderRadius: trailRadii.button,
    color: trailColors.text,
    font: "inherit",
    minHeight: "44px",
    outline: "none",
    padding: "11px 64px 11px 13px",
    width: "100%"
  },
  passwordToggle: {
    alignItems: "center",
    background: "transparent",
    border: "1px solid transparent",
    borderRadius: "9px",
    color: trailColors.textMuted,
    cursor: "pointer",
    display: "inline-flex",
    fontSize: trailTypography.caption,
    fontWeight: 800,
    height: "32px",
    justifyContent: "center",
    minWidth: "48px",
    padding: "0 8px",
    position: "absolute",
    right: "6px",
    top: "6px"
  },
  secondaryRow: {
    display: "flex",
    flexWrap: "wrap",
    gap: trailSpacing.sm,
    justifyContent: "space-between",
    marginTop: trailSpacing.lg
  },
  successCopy: {
    color: trailColors.textSubtle,
    lineHeight: 1.5,
    margin: `0 0 ${trailSpacing.lg}`
  },
  hint: {
    color: trailColors.textMuted,
    fontSize: "13px",
    margin: `${trailSpacing.lg} 0 0`
  }
} satisfies Record<string, CSSProperties>;
