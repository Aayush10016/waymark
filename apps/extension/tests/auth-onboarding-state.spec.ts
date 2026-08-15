import { describe, expect, it } from "vitest";
import {
  AUTH_ONBOARDING_MODES,
  AUTH_ONBOARDING_NOTICE_CODES,
  canStartTrail,
  initialAuthOnboardingState,
  reduceAuthOnboardingState
} from "../src/features/auth/onboarding/auth-onboarding-state";

describe("auth onboarding state", () => {
  it("starts signed out before the consent step", () => {
    const state = initialAuthOnboardingState();

    expect(state.mode).toBe(AUTH_ONBOARDING_MODES.signIn);
    expect(canStartTrail(state)).toBe(false);
  });

  it("keeps the user in verification after registration with a neutral notice", () => {
    const state = reduceAuthOnboardingState(
      reduceAuthOnboardingState(initialAuthOnboardingState(), {
        type: "CHANGE_PASSWORD",
        value: "correct horse battery staple"
      }),
      { type: "REGISTRATION_ACCEPTED" }
    );

    expect(state.mode).toBe(AUTH_ONBOARDING_MODES.verifyEmail);
    expect(state.password).toBe("");
    expect(state.noticeCode).toBe(AUTH_ONBOARDING_NOTICE_CODES.verificationRequired);
  });

  it("uses generic failures without resetting email or password drafts", () => {
    const withEmail = reduceAuthOnboardingState(initialAuthOnboardingState(), {
      type: "CHANGE_EMAIL",
      value: "dev@example.com"
    });
    const withPassword = reduceAuthOnboardingState(withEmail, {
      type: "CHANGE_PASSWORD",
      value: "correct horse battery staple"
    });
    const failed = reduceAuthOnboardingState(withPassword, { type: "GENERIC_FAILURE" });

    expect(failed.email).toBe("dev@example.com");
    expect(failed.password).toBe("correct horse battery staple");
    expect(failed.noticeCode).toBe(AUTH_ONBOARDING_NOTICE_CODES.genericFailure);
  });

  it("preserves progress during offline failures", () => {
    const withEmail = reduceAuthOnboardingState(initialAuthOnboardingState(), {
      type: "CHANGE_EMAIL",
      value: "dev@example.com"
    });
    const offline = reduceAuthOnboardingState(withEmail, { type: "OFFLINE_FAILURE" });

    expect(offline.email).toBe("dev@example.com");
    expect(offline.noticeCode).toBe(AUTH_ONBOARDING_NOTICE_CODES.offline);
  });

  it("distinguishes service-unavailable failures from browser-offline failures", () => {
    const withEmail = reduceAuthOnboardingState(initialAuthOnboardingState(), {
      type: "CHANGE_EMAIL",
      value: "dev@example.com"
    });
    const unavailable = reduceAuthOnboardingState(withEmail, { type: "SERVICE_UNAVAILABLE_FAILURE" });

    expect(unavailable.email).toBe("dev@example.com");
    expect(unavailable.noticeCode).toBe(AUTH_ONBOARDING_NOTICE_CODES.serviceUnavailable);
  });

  it("routes authenticated users to the dedicated consent step", () => {
    const authenticated = reduceAuthOnboardingState(initialAuthOnboardingState(), { type: "AUTHENTICATED" });

    expect(authenticated.mode).toBe(AUTH_ONBOARDING_MODES.startTrail);
    expect(canStartTrail(authenticated)).toBe(true);
  });

  it("handles password reset request and completion without leaking account existence", () => {
    const resetRequested = reduceAuthOnboardingState(initialAuthOnboardingState(), {
      type: "PASSWORD_RESET_REQUEST_ACCEPTED"
    });

    expect(resetRequested.mode).toBe(AUTH_ONBOARDING_MODES.resetRequested);
    expect(resetRequested.noticeCode).toBe(AUTH_ONBOARDING_NOTICE_CODES.passwordResetAccepted);

    const complete = reduceAuthOnboardingState(resetRequested, { type: "PASSWORD_RESET_ACCEPTED" });

    expect(complete.mode).toBe(AUTH_ONBOARDING_MODES.signIn);
    expect(complete.resetToken).toBe("");
    expect(complete.newPassword).toBe("");
  });
});
