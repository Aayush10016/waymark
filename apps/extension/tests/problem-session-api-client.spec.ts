import { describe, expect, it } from "vitest";
import { FetchProblemSessionCommandApiClient } from "../src/services/problem-session";

const RESPONSE_BODY = {
  endedAt: null,
  id: "10000000-0000-4000-8000-000000000001",
  source: "MANUAL",
  startedAt: "2026-07-16T08:00:00.000Z",
  status: "CANDIDATE",
  title: null,
  updatedAt: "2026-07-16T08:00:00.000Z",
  version: 0
};

describe("FetchProblemSessionCommandApiClient", () => {
  it("starts with generated OpenAPI request shape and bearer auth", async () => {
    const requests: RequestLog[] = [];
    const client = new FetchProblemSessionCommandApiClient({
      baseUrl: "/api/v1/",
      fetchImplementation: recordingFetch(requests, Response.json(RESPONSE_BODY, { headers: { ETag: "\"0\"" } }))
    });

    await expect(
      client.start({
        accessToken: "access-token",
        request: {
          problemSessionId: "10000000-0000-4000-8000-000000000001",
          startedAt: "2026-07-16T08:00:00.000Z"
        }
      })
    ).resolves.toMatchObject({ status: "processed", etag: "\"0\"", response: RESPONSE_BODY });

    expect(requests).toEqual([
      {
        body: JSON.stringify({
          problemSessionId: "10000000-0000-4000-8000-000000000001",
          startedAt: "2026-07-16T08:00:00.000Z"
        }),
        headers: {
          Authorization: "Bearer access-token",
          "Content-Type": "application/json"
        },
        input: "/api/v1/problem-sessions",
        method: "POST"
      }
    ]);
  });

  it("finishes with If-Match and maps stale versions safely", async () => {
    const requests: RequestLog[] = [];
    const client = new FetchProblemSessionCommandApiClient({
      baseUrl: "/api/v1",
      fetchImplementation: recordingFetch(requests, new Response(null, { status: 412 }))
    });

    await expect(
      client.finish({
        accessToken: "access-token",
        etag: "\"0\"",
        problemSessionId: "10000000-0000-4000-8000-000000000001",
        request: {
          finishedAt: "2026-07-16T08:05:00.000Z"
        }
      })
    ).resolves.toEqual({ status: "stale_version" });

    expect(requests[0]).toMatchObject({
      headers: {
        Authorization: "Bearer access-token",
        "Content-Type": "application/json",
        "If-Match": "\"0\""
      },
      input: "/api/v1/problem-sessions/10000000-0000-4000-8000-000000000001/finish",
      method: "POST"
    });
  });

  it("saves a final solution with If-Match and Idempotency-Key", async () => {
    const requests: RequestLog[] = [];
    const client = new FetchProblemSessionCommandApiClient({
      baseUrl: "/api/v1",
      fetchImplementation: recordingFetch(
        requests,
        Response.json(
          {
            ...RESPONSE_BODY,
            finalSolution: "Allowed localhost:3000 in CorsConfiguration.",
            finalSolutionAuthor: "USER",
            solutionAuthoredAt: "2026-07-16T08:10:00.000Z",
            status: "SOLVED",
            version: 2
          },
          { headers: { ETag: "\"2\"" } }
        )
      )
    });

    await expect(
      client.saveSolution({
        accessToken: "access-token",
        etag: "\"1\"",
        idempotencyKey: "solution-key",
        problemSessionId: "10000000-0000-4000-8000-000000000001",
        request: {
          finalSolution: "Allowed localhost:3000 in CorsConfiguration."
        }
      })
    ).resolves.toMatchObject({ status: "processed", etag: "\"2\"" });

    expect(requests[0]).toMatchObject({
      body: JSON.stringify({
        finalSolution: "Allowed localhost:3000 in CorsConfiguration."
      }),
      headers: {
        Authorization: "Bearer access-token",
        "Content-Type": "application/json",
        "Idempotency-Key": "solution-key",
        "If-Match": "\"1\""
      },
      input: "/api/v1/problem-sessions/10000000-0000-4000-8000-000000000001/solution",
      method: "PUT"
    });
  });

  it("returns bounded statuses for auth, conflict, malformed, and network failures", async () => {
    await expect(clientFor(new Response(null, { status: 401 })).start(command())).resolves.toEqual({
      status: "authentication_rejected"
    });
    await expect(clientFor(new Response(null, { status: 409 })).start(command())).resolves.toEqual({
      status: "conflict"
    });
    await expect(clientFor(Response.json({ invalid: true }, { headers: { ETag: "\"0\"" } })).start(command())).resolves.toEqual({
      status: "malformed_response"
    });

    const networkClient = new FetchProblemSessionCommandApiClient({
      baseUrl: "/api/v1",
      fetchImplementation: () => Promise.reject(new TypeError("offline"))
    });
    await expect(networkClient.start(command())).resolves.toEqual({ status: "network_error" });
  });
});

function clientFor(response: Response): FetchProblemSessionCommandApiClient {
  return new FetchProblemSessionCommandApiClient({
    baseUrl: "/api/v1",
    fetchImplementation: () => Promise.resolve(response)
  });
}

function command() {
  return {
    accessToken: "access-token",
    request: {
      problemSessionId: "10000000-0000-4000-8000-000000000001"
    }
  };
}

function recordingFetch(requests: RequestLog[], response: Response): typeof fetch {
  return (input, init) => {
    requests.push({
      body: typeof init?.body === "string" ? init.body : null,
      headers: init?.headers as Record<string, string>,
      input: stringifyRequestInput(input),
      method: init?.method ?? "GET"
    });
    return Promise.resolve(response);
  };
}

function stringifyRequestInput(input: RequestInfo | URL): string {
  if (typeof input === "string") {
    return input;
  }
  if (input instanceof URL) {
    return input.toString();
  }
  return input.url;
}

interface RequestLog {
  readonly body: string | null;
  readonly headers: Record<string, string>;
  readonly input: string;
  readonly method: string;
}
