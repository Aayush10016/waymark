import {
  AUTH_ONBOARDING_MODES,
  type AuthOnboardingAction,
  type AuthOnboardingState
} from "./auth-onboarding-state";
import type { AuthOnboardingClient, AuthOnboardingMutationResult } from "./auth-onboarding-client";
import type { AuthSessionSink } from "./auth-session-sink";

export interface AuthOnboardingSubmitDependencies {
  readonly client: AuthOnboardingClient;
  readonly sessionSink: AuthSessionSink;
}

export async function submitAuthOnboardingState(
  state: AuthOnboardingState,
  dependencies: AuthOnboardingSubmitDependencies
): Promise<AuthOnboardingAction> {
  if (state.mode === AUTH_ONBOARDING_MODES.signIn) {
    const result = await dependencies.client.signIn({ email: state.email, password: state.password });
    if (result.status !== "AUTHENTICATED") {
      return failureAction(result.status);
    }

    const sessionResult = await dependencies.sessionSink.establish(result.tokens);
    return sessionResult.status === "ESTABLISHED" ? { type: "AUTHENTICATED" } : { type: "GENERIC_FAILURE" };
  }

  if (state.mode === AUTH_ONBOARDING_MODES.createAccount) {
    return mutationAction(
      await dependencies.client.register({ email: state.email, password: state.password }),
      "REGISTRATION_ACCEPTED"
    );
  }

  if (state.mode === AUTH_ONBOARDING_MODES.verifyEmail) {
    return mutationAction(await dependencies.client.verifyEmail({ token: state.verificationToken }), "EMAIL_VERIFIED");
  }

  if (state.mode === AUTH_ONBOARDING_MODES.resetPassword) {
    return mutationAction(
      await dependencies.client.requestPasswordReset({ email: state.email }),
      "PASSWORD_RESET_REQUEST_ACCEPTED"
    );
  }

  if (state.mode === AUTH_ONBOARDING_MODES.resetRequested) {
    return mutationAction(
      await dependencies.client.resetPassword({ token: state.resetToken, newPassword: state.newPassword }),
      "PASSWORD_RESET_ACCEPTED"
    );
  }

  return { type: "GENERIC_FAILURE" };
}

function mutationAction(
  result: AuthOnboardingMutationResult,
  acceptedAction: "EMAIL_VERIFIED" | "PASSWORD_RESET_ACCEPTED" | "PASSWORD_RESET_REQUEST_ACCEPTED" | "REGISTRATION_ACCEPTED"
): AuthOnboardingAction {
  return result.status === "ACCEPTED" ? { type: acceptedAction } : failureAction(result.status);
}

function failureAction(status: "REJECTED" | "SERVICE_UNAVAILABLE"): AuthOnboardingAction {
  return status === "SERVICE_UNAVAILABLE" ? { type: "SERVICE_UNAVAILABLE_FAILURE" } : { type: "GENERIC_FAILURE" };
}
