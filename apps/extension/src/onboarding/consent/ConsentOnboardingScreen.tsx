import type { CSSProperties, ReactElement } from "react";
import { useEffect, useMemo, useState } from "react";
import { trailColors, trailFocusRing, trailRadii, trailSpacing } from "@trail/ui";
import { FetchAuthRefreshClient, AuthTokenService } from "../../features/auth";
import {
  CaptureConsentRuntimeService,
  FetchCaptureConsentApiClient,
  type CaptureConsentApiClient,
  type CaptureConsentState
} from "../../services/consent";
import {
  ChromeLocalCaptureConsentStorage,
  CURRENT_CAPTURE_CONSENT_VERSION
} from "../../storage/consent/capture-consent-storage";
import { ChromeLocalAuthSessionStorage } from "../../storage/auth/auth-session-storage";
import { trailApiBaseUrl } from "../../shared/config/api-base-url";
import { ChromeConsentCompletionNavigator, type ConsentCompletionNavigator } from "./consent-completion-navigator";

export interface ConsentAuthTokenProvider {
  getValidAccessToken(): Promise<string | null>;
}

export interface ConsentOnboardingScreenProps {
  readonly apiClient?: CaptureConsentApiClient;
  readonly authTokenProvider?: ConsentAuthTokenProvider;
  readonly completionNavigator?: ConsentCompletionNavigator;
  readonly service?: CaptureConsentRuntimeService;
  readonly onAccepted?: () => void;
  readonly onDeclined?: () => void;
}

export function ConsentOnboardingScreen(props: ConsentOnboardingScreenProps): ReactElement {
  const service = useMemo(
    () =>
      props.service ??
      new CaptureConsentRuntimeService({
        storage: new ChromeLocalCaptureConsentStorage()
      }),
    [props.service]
  );
  const completionNavigator = useMemo(
    () => props.completionNavigator ?? new ChromeConsentCompletionNavigator(),
    [props.completionNavigator]
  );
  const apiClient = useMemo(
    () => props.apiClient ?? new FetchCaptureConsentApiClient({ baseUrl: trailApiBaseUrl() }),
    [props.apiClient]
  );
  const authTokenProvider = useMemo(
    () =>
      props.authTokenProvider ??
      new AuthTokenService({
        refreshClient: new FetchAuthRefreshClient({ baseUrl: trailApiBaseUrl() }),
        storage: new ChromeLocalAuthSessionStorage(),
        now: () => new Date()
      }),
    [props.authTokenProvider]
  );
  const [state, setState] = useState<CaptureConsentState>(service.current());
  const [busy, setBusy] = useState(false);
  const [remoteError, setRemoteError] = useState<string | null>(null);

  useEffect(() => {
    const unsubscribe = service.subscribe(setState);
    void service.readState();
    return unsubscribe;
  }, [service]);

  async function acceptConsent(): Promise<void> {
    setBusy(true);
    setRemoteError(null);
    const accessToken = await authTokenProvider.getValidAccessToken();
    if (accessToken === null) {
      setBusy(false);
      setRemoteError("Sign in again before starting Trail capture.");
      return;
    }

    const grantResult = await apiClient.grant({
      accessToken,
      request: {
        policyVersion: CURRENT_CAPTURE_CONSENT_VERSION,
        decidedAt: new Date().toISOString()
      }
    });

    if (grantResult.status !== "processed") {
      setBusy(false);
      setRemoteError("Trail could not record consent securely. Check your connection and try again.");
      return;
    }

    const nextState = await service.accept();
    setBusy(false);
    if (nextState.status === "accepted") {
      props.onAccepted?.();
      await completionNavigator.completeAcceptedConsent();
    }
  }

  async function declineConsent(): Promise<void> {
    setBusy(true);
    setRemoteError(null);
    await service.decline();
    setBusy(false);
    props.onDeclined?.();
  }

  async function revokeConsent(): Promise<void> {
    setBusy(true);
    setRemoteError(null);
    await service.revoke();
    const accessToken = await authTokenProvider.getValidAccessToken();
    if (accessToken !== null) {
      const withdrawalResult = await apiClient.withdraw({
        accessToken,
        request: {
          policyVersion: CURRENT_CAPTURE_CONSENT_VERSION,
          decidedAt: new Date().toISOString()
        }
      });
      if (withdrawalResult.status !== "processed") {
        setRemoteError("Capture is off locally. Trail could not confirm withdrawal with the server yet.");
      }
    }
    setBusy(false);
  }

  return (
    <section aria-labelledby="consent-heading" style={styles.card}>
      <style>{consentCss}</style>
      <p style={styles.stepLabel}>Privacy setup</p>
      <h2 id="consent-heading" style={styles.title}>
        Choose what Trail can remember.
      </h2>
      <p style={styles.copy}>
        Trail recognizes solved engineering problems from minimal browser metadata. Capture stays disabled until you
        explicitly choose Start Trail.
      </p>

      <div style={styles.grid}>
        <section aria-labelledby="captures-heading" style={styles.panel}>
          <h3 id="captures-heading" style={styles.panelTitle}>
            Trail captures
          </h3>
          <ul style={styles.list}>
            <li>Page title from top-level debugging pages.</li>
            <li>Sanitized URL and domain after local policy checks.</li>
            <li>Search query only from allowlisted search engines.</li>
            <li>Time and navigation relationship needed to form a problem session.</li>
          </ul>
        </section>

        <section aria-labelledby="never-heading" style={styles.panel}>
          <h3 id="never-heading" style={styles.panelTitle}>
            Trail never captures
          </h3>
          <ul style={styles.list}>
            <li>Page content, DOM, screenshots, forms, cookies, or keystrokes.</li>
            <li>Source code, terminal output, clipboard, IDE activity, or repository contents.</li>
            <li>Incognito, localhost, private-network, local-file, iframe, or subresource activity.</li>
            <li>Raw credentials, tokens, private hosts, or sensitive query parameters.</li>
          </ul>
        </section>
      </div>

      <section aria-labelledby="why-heading" style={styles.explanation}>
        <h3 id="why-heading" style={styles.panelTitle}>
          Why capture exists
        </h3>
        <p style={styles.smallCopy}>
          Trail needs lightweight context so it can connect a future debugging session to a solution you already saved.
          Without consent, Trail stays in demo/manual mode and browser capture remains off.
        </p>
      </section>

      <section aria-labelledby="guarantees-heading" style={styles.explanation}>
        <h3 id="guarantees-heading" style={styles.panelTitle}>
          Privacy guarantees
        </h3>
        <p style={styles.smallCopy}>
          Sanitation happens before persistence or upload. Consent is stored locally with a version and timestamp, can be
          revoked, and must be renewed when the capture policy changes.
        </p>
      </section>

      {state.status === "accepted" ? (
        <p aria-live="polite" role="status" style={styles.success}>
          Capture consent is active for {state.consentVersion}. Trail can start only within this consent boundary.
        </p>
      ) : null}
      {state.status === "declined" ? (
        <p aria-live="polite" role="status" style={styles.notice}>
          Capture is disabled. You can start Trail later from onboarding or settings.
        </p>
      ) : null}
      {state.status === "stale" ? (
        <p aria-live="polite" role="status" style={styles.notice}>
          The capture policy changed. Review this page again before Trail can capture browser metadata.
        </p>
      ) : null}
      {remoteError !== null ? (
        <p aria-live="polite" role="alert" style={styles.notice}>
          {remoteError}
        </p>
      ) : null}

      <div style={styles.actions}>
        <button className="trail-consent-button" disabled={busy} onClick={() => void acceptConsent()} style={buttonStyle("primary")} type="button">
          Start Trail
        </button>
        <button className="trail-consent-button" disabled={busy} onClick={() => void declineConsent()} style={buttonStyle("secondary")} type="button">
          Not now
        </button>
        {state.status === "accepted" ? (
          <button className="trail-consent-button" disabled={busy} onClick={() => void revokeConsent()} style={buttonStyle("ghost")} type="button">
            Revoke consent
          </button>
        ) : null}
      </div>
    </section>
  );
}

function buttonStyle(variant: "ghost" | "primary" | "secondary"): CSSProperties {
  const base: CSSProperties = {
    borderRadius: "12px",
    cursor: "pointer",
    fontSize: "14px",
    fontWeight: 650,
    padding: "10px 14px"
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

  return {
    ...base,
    background: "transparent",
    border: `1px solid ${trailColors.border}`,
    color: trailColors.textMuted
  };
}

const consentCss = `
  .trail-consent-button:focus-visible {
    box-shadow: ${trailFocusRing};
    outline: none;
  }
  .trail-consent-button:disabled {
    cursor: not-allowed;
    opacity: 0.54;
  }
`;

const styles = {
  actions: {
    display: "flex",
    flexWrap: "wrap",
    gap: trailSpacing.md,
    marginTop: "20px"
  },
  card: {
    background: "rgba(17, 20, 26, 0.92)",
    border: `1px solid ${trailColors.border}`,
    borderRadius: trailRadii.shell,
    boxShadow: "0 28px 90px rgba(0, 0, 0, 0.42)",
    color: trailColors.text,
    display: "grid",
    gap: trailSpacing.lg,
    maxWidth: "860px",
    padding: "28px"
  },
  copy: {
    color: trailColors.textSubtle,
    fontSize: "15px",
    lineHeight: 1.6,
    margin: 0
  },
  explanation: {
    background: trailColors.surface,
    border: `1px solid ${trailColors.borderMuted}`,
    borderRadius: trailRadii.card,
    padding: "16px"
  },
  grid: {
    display: "grid",
    gap: "14px",
    gridTemplateColumns: "repeat(auto-fit, minmax(260px, 1fr))"
  },
  list: {
    color: "#D7DEE8",
    display: "grid",
    gap: "8px",
    lineHeight: 1.5,
    margin: "10px 0 0",
    paddingLeft: "18px"
  },
  notice: {
    background: trailColors.warningSurface,
    border: "1px solid rgba(240, 217, 161, 0.38)",
    borderRadius: trailRadii.field,
    color: trailColors.warning,
    fontSize: "13px",
    lineHeight: 1.5,
    margin: 0,
    padding: "12px"
  },
  panel: {
    background: trailColors.surface,
    border: `1px solid ${trailColors.borderMuted}`,
    borderRadius: trailRadii.card,
    padding: "16px"
  },
  panelTitle: {
    fontSize: "15px",
    letterSpacing: "-0.02em",
    margin: 0
  },
  smallCopy: {
    color: trailColors.textMuted,
    fontSize: "14px",
    lineHeight: 1.6,
    margin: "8px 0 0"
  },
  stepLabel: {
    color: trailColors.accentStrong,
    fontSize: "13px",
    letterSpacing: "0.12em",
    margin: 0,
    textTransform: "uppercase"
  },
  success: {
    background: trailColors.successSurface,
    border: "1px solid rgba(125, 226, 170, 0.48)",
    borderRadius: trailRadii.field,
    color: trailColors.success,
    fontSize: "13px",
    lineHeight: 1.5,
    margin: 0,
    padding: "12px"
  },
  title: {
    fontSize: "26px",
    letterSpacing: "-0.04em",
    margin: 0
  }
} satisfies Record<string, CSSProperties>;
