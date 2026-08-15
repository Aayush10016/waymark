import { parseIssuedAuthTokens, type IssuedAuthTokens } from "../model/auth-session";
import { defaultFetchImplementation, executeFetch, type FetchImplementation } from "../../../shared/http/safe-fetch";

export interface AuthRefreshClient {
  refresh(refreshToken: string): Promise<AuthRefreshResult>;
}

export type AuthRefreshResult =
  | {
      readonly status: "ROTATED";
      readonly tokens: IssuedAuthTokens;
    }
  | {
      readonly status: "REJECTED" | "NETWORK_ERROR";
    };

export interface FetchAuthRefreshClientOptions {
  readonly baseUrl: string;
  readonly fetchImplementation?: FetchImplementation;
}

export class FetchAuthRefreshClient implements AuthRefreshClient {
  private readonly baseUrl: string;
  private readonly fetchImplementation: FetchImplementation;

  constructor(options: FetchAuthRefreshClientOptions) {
    this.baseUrl = options.baseUrl.replace(/\/$/, "");
    this.fetchImplementation = options.fetchImplementation ?? defaultFetchImplementation();
  }

  async refresh(refreshToken: string): Promise<AuthRefreshResult> {
    try {
      const response = await executeFetch(this.fetchImplementation, `${this.baseUrl}/auth-sessions/refresh`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json"
        },
        body: JSON.stringify({ refreshToken })
      });

      if (!response.ok) {
        return { status: "REJECTED" };
      }

      const body = await safelyReadJson(response);
      const tokens = parseIssuedAuthTokens(body);
      return tokens === null ? { status: "REJECTED" } : { status: "ROTATED", tokens };
    } catch {
      return { status: "NETWORK_ERROR" };
    }
  }
}

async function safelyReadJson(response: Response): Promise<unknown> {
  try {
    return await response.json();
  } catch {
    return null;
  }
}
