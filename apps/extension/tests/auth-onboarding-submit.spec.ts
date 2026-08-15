import { describe, expect, it } from "vitest";
import { submitAuthOnboardingState } from "../src/features/auth/onboarding/auth-onboarding-submit";
import {
  AUTH_ONBOARDING_MODES,
  AUTH_ONBOARDING_STATUS,
  type AuthOnboardingState
} from "../src/features/auth/onboarding/auth-onboarding-state";
import {
  type AuthCredentialsCommand,
  type AuthOnboardingClient,
  type AuthOnboardingMutationResult,
  type AuthOnboardingSignInResult
} from "../src/features/auth";
import type { IssuedAuthTokens } from "../src/features/auth";
import type { AuthSessionSink } from "../src/features/auth";

const TOKENS: IssuedAuthTokens = {
  accessToken: "access-token",
  accessTokenExpiresAt: "2026-07-11T08:10:00.000Z",
  refreshToken: "refresh-token",
  refreshTokenExpiresAt: "2026-08-10T08:00:00.000Z"
};

describe("submitAuthOnboardingState", () => {
  it("invokes the sign-in client boundary with submitted credentials before establishing the session", async () => {
    const client = new RecordingAuthOnboardingClient({ status: "AUTHENTICATED", tokens: TOKENS });
    const sessionSink = new RecordingSessionSink("ESTABLISHED");

    await expect(
      submitAuthOnboardingState(
        stateWithPassword("short"),
        { client, sessionSink }
      )
    ).resolves.toEqual({ type: "AUTHENTICATED" });

    expect(client.signInCommands).toEqual([{ email: "dev@example.com", password: "short" }]);
    expect(sessionSink.establishedTokens).toEqual([TOKENS]);
  });

  it("maps service-unavailable sign-in responses without attempting to establish a session", async () => {
    const client = new RecordingAuthOnboardingClient({ status: "SERVICE_UNAVAILABLE" });
    const sessionSink = new RecordingSessionSink("ESTABLISHED");

    await expect(
      submitAuthOnboardingState(
        stateWithPassword("correct horse battery staple"),
        { client, sessionSink }
      )
    ).resolves.toEqual({ type: "SERVICE_UNAVAILABLE_FAILURE" });

    expect(client.signInCommands).toHaveLength(1);
    expect(sessionSink.establishedTokens).toEqual([]);
  });
});

function stateWithPassword(password: string): AuthOnboardingState {
  return {
    email: "dev@example.com",
    mode: AUTH_ONBOARDING_MODES.signIn,
    newPassword: "",
    noticeCode: null,
    password,
    resetToken: "",
    status: AUTH_ONBOARDING_STATUS.loading,
    verificationToken: ""
  };
}

class RecordingAuthOnboardingClient implements AuthOnboardingClient {
  readonly signInCommands: AuthCredentialsCommand[] = [];

  constructor(private readonly signInResult: AuthOnboardingSignInResult) {}

  register(): Promise<AuthOnboardingMutationResult> {
    return Promise.resolve({ status: "REJECTED" });
  }

  signIn(command: AuthCredentialsCommand): Promise<AuthOnboardingSignInResult> {
    this.signInCommands.push(command);
    return Promise.resolve(this.signInResult);
  }

  requestEmailVerification(): Promise<AuthOnboardingMutationResult> {
    return Promise.resolve({ status: "REJECTED" });
  }

  verifyEmail(): Promise<AuthOnboardingMutationResult> {
    return Promise.resolve({ status: "REJECTED" });
  }

  requestPasswordReset(): Promise<AuthOnboardingMutationResult> {
    return Promise.resolve({ status: "REJECTED" });
  }

  resetPassword(): Promise<AuthOnboardingMutationResult> {
    return Promise.resolve({ status: "REJECTED" });
  }
}

class RecordingSessionSink implements AuthSessionSink {
  readonly establishedTokens: IssuedAuthTokens[] = [];

  constructor(private readonly status: "ESTABLISHED" | "FAILED") {}

  establish(tokens: IssuedAuthTokens): Promise<{ readonly status: "ESTABLISHED" | "FAILED" }> {
    this.establishedTokens.push(tokens);
    return Promise.resolve({ status: this.status });
  }
}
