import { parseIssuedAuthTokens, type IssuedAuthTokens } from "../model/auth-session";
import { defaultFetchImplementation, executeFetch, type FetchImplementation } from "../../../shared/http/safe-fetch";

export interface AuthOnboardingClient {
  register(command: AuthCredentialsCommand): Promise<AuthOnboardingMutationResult>;
  signIn(command: AuthCredentialsCommand): Promise<AuthOnboardingSignInResult>;
  requestEmailVerification(command: AuthEmailCommand): Promise<AuthOnboardingMutationResult>;
  verifyEmail(command: AuthTokenCommand): Promise<AuthOnboardingMutationResult>;
  requestPasswordReset(command: AuthEmailCommand): Promise<AuthOnboardingMutationResult>;
  resetPassword(command: AuthPasswordResetCommand): Promise<AuthOnboardingMutationResult>;
}

export interface AuthCredentialsCommand {
  readonly email: string;
  readonly password: string;
}

export interface AuthEmailCommand {
  readonly email: string;
}

export interface AuthTokenCommand {
  readonly token: string;
}

export interface AuthPasswordResetCommand {
  readonly token: string;
  readonly newPassword: string;
}

export type AuthOnboardingMutationResult = {
  readonly status: "ACCEPTED" | "REJECTED" | "SERVICE_UNAVAILABLE";
};

export type AuthOnboardingSignInResult =
  | {
      readonly status: "AUTHENTICATED";
      readonly tokens: IssuedAuthTokens;
    }
  | {
      readonly status: "REJECTED" | "SERVICE_UNAVAILABLE";
    };

export interface FetchAuthOnboardingClientOptions {
  readonly baseUrl: string;
  readonly fetchImplementation?: FetchImplementation;
}

export class FetchAuthOnboardingClient implements AuthOnboardingClient {
  private readonly baseUrl: string;
  private readonly fetchImplementation: FetchImplementation;

  constructor(options: FetchAuthOnboardingClientOptions) {
    this.baseUrl = options.baseUrl.replace(/\/$/, "");
    this.fetchImplementation = options.fetchImplementation ?? defaultFetchImplementation();
  }

  async register(command: AuthCredentialsCommand): Promise<AuthOnboardingMutationResult> {
    return this.postMutation("/registrations", {
      email: command.email,
      password: command.password
    });
  }

  async signIn(command: AuthCredentialsCommand): Promise<AuthOnboardingSignInResult> {
    const response = await this.postJson("/auth-sessions", {
      email: command.email,
      password: command.password
    });

    if (response.status === "SERVICE_UNAVAILABLE" || !response.ok) {
      return { status: response.status === "SERVICE_UNAVAILABLE" || isRetryableStatus(response.httpStatus) ? "SERVICE_UNAVAILABLE" : "REJECTED" };
    }

    const parsed = parseIssuedAuthTokens(response.body);
    if (parsed === null) {
      return { status: "REJECTED" };
    }

    return { status: "AUTHENTICATED", tokens: parsed };
  }

  async requestEmailVerification(command: AuthEmailCommand): Promise<AuthOnboardingMutationResult> {
    return this.postMutation("/email-verification-requests", { email: command.email });
  }

  async verifyEmail(command: AuthTokenCommand): Promise<AuthOnboardingMutationResult> {
    return this.postMutation("/email-verifications", { token: command.token });
  }

  async requestPasswordReset(command: AuthEmailCommand): Promise<AuthOnboardingMutationResult> {
    return this.postMutation("/password-reset-requests", { email: command.email });
  }

  async resetPassword(command: AuthPasswordResetCommand): Promise<AuthOnboardingMutationResult> {
    return this.postMutation("/password-resets", {
      token: command.token,
      newPassword: command.newPassword
    });
  }

  private async postMutation(path: string, body: Record<string, string>): Promise<AuthOnboardingMutationResult> {
    const response = await this.postJson(path, body);
    if (response.status === "SERVICE_UNAVAILABLE") {
      return { status: "SERVICE_UNAVAILABLE" };
    }

    return { status: response.ok ? "ACCEPTED" : isRetryableStatus(response.httpStatus) ? "SERVICE_UNAVAILABLE" : "REJECTED" };
  }

  private async postJson(path: string, body: Record<string, string>): Promise<PostJsonResult> {
    try {
      const response = await executeFetch(this.fetchImplementation, `${this.baseUrl}${path}`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json"
        },
        body: JSON.stringify(body)
      });

      return {
        status: "RECEIVED",
        httpStatus: response.status,
        ok: response.ok,
        body: await safelyReadJson(response)
      };
    } catch {
      return { status: "SERVICE_UNAVAILABLE" };
    }
  }
}

type PostJsonResult =
  | {
      readonly status: "RECEIVED";
      readonly httpStatus: number;
      readonly ok: boolean;
      readonly body: unknown;
    }
  | {
      readonly status: "SERVICE_UNAVAILABLE";
    };

function isRetryableStatus(status: number): boolean {
  return status === 408 || status === 429 || status >= 500;
}

async function safelyReadJson(response: Response): Promise<unknown> {
  try {
    return await response.json();
  } catch {
    return null;
  }
}
