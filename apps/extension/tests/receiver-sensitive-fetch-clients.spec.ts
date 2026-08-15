import { describe, expect, it } from "vitest";
import { FetchAuthRefreshClient } from "../src/features/auth";
import { FetchCaptureBatchApiClient } from "../src/services/capture-sync";
import { FetchProblemSessionCommandApiClient } from "../src/services/problem-session";
import {
  FetchProblemRecommendationApiClient,
  FetchProblemSessionDetailApiClient,
  FetchProblemSessionSearchApiClient
} from "../src/services/recall";

const TOKENS = {
  accessToken: "access-token",
  accessTokenExpiresAt: "2026-07-11T08:10:00.000Z",
  refreshToken: "refresh-token",
  refreshTokenExpiresAt: "2026-08-10T08:00:00.000Z"
};

const PROBLEM_SESSION_ID = "10000000-0000-4000-8000-000000000001";

describe("post-auth fetch clients", () => {
  it("refreshes auth sessions without binding the client instance as fetch receiver", async () => {
    const requests: RequestLog[] = [];
    const client = new FetchAuthRefreshClient({
      baseUrl: "http://localhost:8080/api/v1",
      fetchImplementation: receiverSensitiveFetch(requests, Response.json({ tokens: TOKENS }))
    });

    await expect(client.refresh("refresh-token")).resolves.toEqual({ status: "ROTATED", tokens: TOKENS });
    expect(requests[0]).toMatchObject({
      input: "http://localhost:8080/api/v1/auth-sessions/refresh",
      method: "POST"
    });
  });

  it("starts Problem Sessions without binding the client instance as fetch receiver", async () => {
    const requests: RequestLog[] = [];
    const client = new FetchProblemSessionCommandApiClient({
      baseUrl: "http://localhost:8080/api/v1",
      fetchImplementation: receiverSensitiveFetch(
        requests,
        Response.json(problemSessionResponse(), { headers: { ETag: "\"0\"" } })
      )
    });

    await expect(
      client.start({
        accessToken: "access-token",
        request: {
          problemSessionId: PROBLEM_SESSION_ID,
          startedAt: "2026-07-16T08:00:00.000Z"
        }
      })
    ).resolves.toMatchObject({ status: "processed" });
    expect(requests[0]).toMatchObject({
      input: "http://localhost:8080/api/v1/problem-sessions",
      method: "POST"
    });
  });

  it("searches Recall results without binding the client instance as fetch receiver", async () => {
    const requests: RequestLog[] = [];
    const client = new FetchProblemSessionSearchApiClient({
      baseUrl: "http://localhost:8080/api/v1",
      fetchImplementation: receiverSensitiveFetch(requests, Response.json(searchResponse()))
    });

    await expect(client.search({ accessToken: "access-token", query: "cors" })).resolves.toMatchObject({
      status: "received"
    });
    expect(requests[0]).toMatchObject({
      input: "http://localhost:8080/api/v1/problem-session-searches",
      method: "POST",
      parsedBody: {
        pageSize: 20,
        query: "cors"
      }
    });
  });

  it("requests recent solved Recall results without sending an explicit null query", async () => {
    const requests: RequestLog[] = [];
    const client = new FetchProblemSessionSearchApiClient({
      baseUrl: "http://localhost:8080/api/v1",
      fetchImplementation: receiverSensitiveFetch(requests, Response.json(searchResponse()))
    });

    await expect(client.search({ accessToken: "access-token", query: "   " })).resolves.toMatchObject({
      status: "received"
    });
    expect(requests[0]).toMatchObject({
      input: "http://localhost:8080/api/v1/problem-session-searches",
      method: "POST",
      parsedBody: {
        pageSize: 20
      }
    });
  });

  it("loads Recall detail without binding the client instance as fetch receiver", async () => {
    const requests: RequestLog[] = [];
    const client = new FetchProblemSessionDetailApiClient({
      baseUrl: "http://localhost:8080/api/v1",
      fetchImplementation: receiverSensitiveFetch(requests, Response.json(problemSessionDetail()))
    });

    await expect(
      client.getProblemSessionDetail({ accessToken: "access-token", problemSessionId: PROBLEM_SESSION_ID })
    ).resolves.toMatchObject({ status: "found" });
    expect(requests[0]).toMatchObject({
      input: `http://localhost:8080/api/v1/problem-sessions/${PROBLEM_SESSION_ID}`,
      method: "GET"
    });
  });

  it("loads recommendations without binding the client instance as fetch receiver", async () => {
    const requests: RequestLog[] = [];
    const client = new FetchProblemRecommendationApiClient({
      baseUrl: "http://localhost:8080/api/v1",
      fetchImplementation: receiverSensitiveFetch(requests, Response.json(recommendationResponse()))
    });

    await expect(
      client.getRecommendation({ accessToken: "access-token", problemSessionId: PROBLEM_SESSION_ID })
    ).resolves.toMatchObject({ status: "matched" });
    expect(requests[0]).toMatchObject({
      input: `http://localhost:8080/api/v1/problem-sessions/${PROBLEM_SESSION_ID}/recommendation`,
      method: "GET"
    });
  });

  it("uploads capture batches without binding the client instance as fetch receiver", async () => {
    const requests: RequestLog[] = [];
    const client = new FetchCaptureBatchApiClient({
      baseUrl: "http://localhost:8080/api/v1",
      fetchImplementation: receiverSensitiveFetch(
        requests,
        Response.json({ accepted: [], duplicate: [], rejected: [] })
      )
    });

    await expect(
      client.upload({
        accessToken: "access-token",
        idempotencyKey: "capture-batch:key",
        request: {
          clientInstanceId: "extension-instance-1",
          events: []
        }
      })
    ).resolves.toMatchObject({ status: "processed" });
    expect(requests[0]).toMatchObject({
      input: "http://localhost:8080/api/v1/capture-batches",
      method: "POST"
    });
  });
});

function receiverSensitiveFetch(requests: RequestLog[], response: Response): typeof fetch {
  return function fetchLikeChrome(this: unknown, input, init) {
    if (this !== undefined) {
      throw new TypeError("Illegal invocation");
    }

    requests.push({
      body: typeof init?.body === "string" ? init.body : null,
      input: stringifyRequestInput(input),
      method: init?.method ?? "GET",
      parsedBody: typeof init?.body === "string" ? JSON.parse(init.body) : null
    });
    return Promise.resolve(response);
  };
}

function problemSessionResponse() {
  return {
    endedAt: null,
    id: PROBLEM_SESSION_ID,
    source: "MANUAL",
    startedAt: "2026-07-16T08:00:00.000Z",
    status: "CANDIDATE",
    title: null,
    updatedAt: "2026-07-16T08:00:00.000Z",
    version: 0
  };
}

function searchResponse() {
  return {
    degradationMode: "LEXICAL",
    nextCursor: null,
    results: [
      {
        errorPhrases: ["CORS"],
        evidenceCodes: ["ERROR_PHRASE"],
        finalSolution: "Allowed localhost in CorsConfiguration.",
        generatedSummary: null,
        problemSessionId: PROBLEM_SESSION_ID,
        problemType: null,
        technologies: ["Spring Security"],
        title: "Spring Security CORS",
        updatedAt: "2026-07-16T08:00:00.000Z"
      }
    ]
  };
}

function problemSessionDetail() {
  return {
    artifacts: [
      {
        artifactType: "DOCUMENTATION",
        decisive: true,
        hostname: "docs.spring.io",
        id: "10000000-0000-4000-8000-000000000002",
        occurredAt: "2026-07-16T08:00:00.000Z",
        position: 1,
        queryText: null,
        sanitizedUrl: "https://docs.spring.io/spring-security/reference/servlet/integrations/cors.html",
        title: "Spring Security CORS"
      }
    ],
    id: PROBLEM_SESSION_ID,
    omittedArtifactCount: 0,
    source: "MANUAL",
    startedAt: "2026-07-16T08:00:00.000Z",
    status: "SOLVED",
    updatedAt: "2026-07-16T08:00:00.000Z",
    version: 1
  };
}

function recommendationResponse() {
  return {
    algorithmVersion: "hybrid-similarity-v1",
    currentProblemSessionId: PROBLEM_SESSION_ID,
    evidence: [{ evidenceCode: "ERROR_PHRASE", problemSessionId: PROBLEM_SESSION_ID }],
    finalSolution: "Allowed localhost in CorsConfiguration.",
    recommendationId: "10000000-0000-4000-8000-000000000003",
    recommendedProblemSessionId: "10000000-0000-4000-8000-000000000004",
    strength: "STRONG",
    whyThis: [{ evidenceCode: "ERROR_PHRASE", message: "Both problems mention CORS.", problemSessionId: PROBLEM_SESSION_ID }]
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
  readonly input: string;
  readonly method: string;
  readonly parsedBody: unknown;
}
