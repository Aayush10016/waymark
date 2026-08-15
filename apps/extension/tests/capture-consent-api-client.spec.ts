import { describe, expect, it } from "vitest";
import { FetchCaptureConsentApiClient } from "../src/services/consent";

describe("FetchCaptureConsentApiClient", () => {
  it("records consent grants with bearer authentication", async () => {
    const requests: Request[] = [];
    const client = new FetchCaptureConsentApiClient({
      baseUrl: "https://trail.test/api/v1",
      fetchImplementation: (input, init) => {
        requests.push(new Request(input, init));
        return Promise.resolve(Response.json({
          status: "GRANTED",
          policyVersion: "capture-consent-v1",
          decidedAt: "2026-07-13T08:00:00.000Z"
        }));
      }
    });

    await expect(client.grant(command())).resolves.toMatchObject({
      response: { status: "GRANTED" },
      status: "processed"
    });

    expect(requests).toHaveLength(1);
    const request = firstRequest(requests);
    expect(request.url).toBe("https://trail.test/api/v1/consent-grants");
    expect(request.method).toBe("POST");
    expect(request.headers.get("Authorization")).toBe("Bearer access-token");
    expect(await request.json()).toEqual({
      policyVersion: "capture-consent-v1",
      decidedAt: "2026-07-13T08:00:00.000Z"
    });
  });

  it("records consent withdrawals", async () => {
    const requests: Request[] = [];
    const client = new FetchCaptureConsentApiClient({
      baseUrl: "https://trail.test/api/v1/",
      fetchImplementation: (input, init) => {
        requests.push(new Request(input, init));
        return Promise.resolve(Response.json({
          status: "WITHDRAWN",
          policyVersion: "capture-consent-v1",
          decidedAt: "2026-07-13T08:00:00.000Z"
        }));
      }
    });

    await expect(client.withdraw(command())).resolves.toMatchObject({
      response: { status: "WITHDRAWN" },
      status: "processed"
    });

    const request = firstRequest(requests);
    expect(request.url).toBe("https://trail.test/api/v1/consent-withdrawals");
  });

  it("maps authentication rejection and malformed responses safely", async () => {
    const unauthorized = new FetchCaptureConsentApiClient({
      baseUrl: "https://trail.test/api/v1",
      fetchImplementation: () => Promise.resolve(new Response(null, { status: 401 }))
    });
    const malformed = new FetchCaptureConsentApiClient({
      baseUrl: "https://trail.test/api/v1",
      fetchImplementation: () => Promise.resolve(Response.json({ status: "GRANTED" }))
    });

    await expect(unauthorized.grant(command())).resolves.toEqual({ status: "authentication_rejected" });
    await expect(malformed.grant(command())).resolves.toEqual({ status: "malformed_response" });
  });

  it("maps network failures without exposing response details", async () => {
    const client = new FetchCaptureConsentApiClient({
      baseUrl: "https://trail.test/api/v1",
      fetchImplementation: () => Promise.reject(new Error("connection refused with sensitive local detail"))
    });

    await expect(client.grant(command())).resolves.toEqual({ status: "network_error" });
  });
});

function command() {
  return {
    accessToken: "access-token",
    request: {
      policyVersion: "capture-consent-v1" as const,
      decidedAt: "2026-07-13T08:00:00.000Z"
    }
  };
}

function firstRequest(requests: readonly Request[]): Request {
  const request = requests[0];
  if (request === undefined) {
    throw new Error("Expected one request.");
  }
  return request;
}
