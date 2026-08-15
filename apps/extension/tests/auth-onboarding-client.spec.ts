import { describe, expect, it } from "vitest";
import { FetchAuthOnboardingClient } from "../src/features/auth";
import { trailApiBaseUrl } from "../src/shared/config/api-base-url";

const TOKENS = {
  accessToken: "access-token",
  accessTokenExpiresAt: "2026-07-11T08:10:00.000Z",
  refreshToken: "refresh-token",
  refreshTokenExpiresAt: "2026-08-10T08:00:00.000Z"
};

describe("FetchAuthOnboardingClient", () => {
  it("posts sign-in credentials and parses issued tokens", async () => {
    const requests: Array<{ readonly input: RequestInfo | URL; readonly init: RequestInit | undefined }> = [];
    const client = new FetchAuthOnboardingClient({
      baseUrl: "/api/v1/",
      fetchImplementation: (input, init) => {
        requests.push({ input, init });
        return Promise.resolve(Response.json({ tokens: TOKENS }));
      }
    });

    await expect(client.signIn({ email: "dev@example.com", password: "correct horse battery staple" })).resolves.toEqual({
      status: "AUTHENTICATED",
      tokens: TOKENS
    });

    expect(requests).toHaveLength(1);
    expect(requests[0]?.input).toBe("/api/v1/auth-sessions");
    expect(requests[0]?.init?.body).toBe(JSON.stringify({
      email: "dev@example.com",
      password: "correct horse battery staple"
    }));
  });

  it("invokes the fetch boundary without binding the client instance as the receiver", async () => {
    const requests: Array<{ readonly input: RequestInfo | URL; readonly init: RequestInit | undefined }> = [];
    const client = new FetchAuthOnboardingClient({
      baseUrl: "http://localhost:8080/api/v1",
      fetchImplementation: function browserFetchLike(this: unknown, input, init) {
        if (this !== undefined) {
          throw new TypeError("Illegal invocation");
        }

        requests.push({ input, init });
        return Promise.resolve(Response.json({ tokens: TOKENS }));
      }
    });

    await expect(client.signIn({ email: "dev@example.com", password: "correct horse battery staple" })).resolves.toEqual({
      status: "AUTHENTICATED",
      tokens: TOKENS
    });

    expect(requests).toEqual([
      expect.objectContaining({
        input: "http://localhost:8080/api/v1/auth-sessions"
      })
    ]);
  });

  it("returns generic rejection for non-success registration responses", async () => {
    const client = new FetchAuthOnboardingClient({
      baseUrl: "/api/v1",
      fetchImplementation: () => Promise.resolve(new Response(null, { status: 409 }))
    });

    await expect(client.register({ email: "dev@example.com", password: "correct horse battery staple" })).resolves.toEqual({
      status: "REJECTED"
    });
  });

  it("uses the local backend API as the default Chrome extension target", () => {
    expect(trailApiBaseUrl()).toBe("http://localhost:8080/api/v1");
  });

  it("returns service-unavailable errors without exposing exception details", async () => {
    const client = new FetchAuthOnboardingClient({
      baseUrl: "/api/v1",
      fetchImplementation: () => Promise.reject(new TypeError("network unavailable"))
    });

    await expect(client.requestPasswordReset({ email: "dev@example.com" })).resolves.toEqual({
      status: "SERVICE_UNAVAILABLE"
    });
  });

  it("classifies retryable server responses as service unavailable", async () => {
    const client = new FetchAuthOnboardingClient({
      baseUrl: "/api/v1",
      fetchImplementation: () => Promise.resolve(new Response(null, { status: 503 }))
    });

    await expect(client.signIn({ email: "dev@example.com", password: "correct horse battery staple" })).resolves.toEqual({
      status: "SERVICE_UNAVAILABLE"
    });
  });
});
