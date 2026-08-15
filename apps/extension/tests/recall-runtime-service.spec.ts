import { describe, expect, it } from "vitest";
import type { ProblemSessionResponsePayload, RecallStatePayload } from "@trail/extension-messages";
import type { ProblemSessionRuntimeService } from "../src/services/problem-session";
import { RecallRuntimeService, type ProblemRecommendationApiClient, type RecallSidePanelController } from "../src/services/recall";
import type {
  ProblemSessionDetailApiClient,
  ProblemSessionDetailApiResult,
  ProblemSessionDetailResponse
} from "../src/services/recall/problem-session-detail-api-client";
import type { ProblemSessionSearchApiClient } from "../src/services/recall/problem-session-search-api-client";
import type { RecallRouteStorage } from "../src/storage/recall";

describe("RecallRuntimeService", () => {
  it("routes popup Recall without opening the side panel from background", async () => {
    const sidePanel = new RecordingSidePanelController();
    const storage = new InMemoryRecallRouteStorage();
    const service = createService({
      problemSessionState: problemSessionState(null),
      routeStorage: storage,
      sidePanelController: sidePanel
    });

    await expect(service.invoke("popup")).resolves.toMatchObject({
      commandStatus: "no_active_problem",
      route: { reason: "NO_ACTIVE_PROBLEM", route: "search" },
      source: "popup"
    });
    expect(sidePanel.events).toEqual([]);
    await expect(storage.read()).resolves.toMatchObject({ route: { route: "search" } });
  });

  it("opens side panel from command-origin Recall and restores source focus", async () => {
    const sidePanel = new RecordingSidePanelController();
    const service = createService({
      problemSessionState: problemSessionState(null),
      sidePanelController: sidePanel
    });

    await expect(service.invoke("command")).resolves.toMatchObject({
      commandStatus: "no_active_problem",
      route: { reason: "NO_ACTIVE_PROBLEM", route: "search" },
      source: "command"
    });
    expect(sidePanel.events).toEqual(["open", "restore-focus"]);
  });

  it("routes no match responses to search", async () => {
    const service = createService({
      recommendationClient: {
        getRecommendation: () => Promise.resolve({ status: "no_match" })
      }
    });

    await expect(service.invoke("command")).resolves.toMatchObject({
      commandStatus: "no_match",
      route: { reason: "NO_MATCH", route: "search" },
      source: "command"
    });
  });

  it("routes matched recommendations to the similar problem boundary", async () => {
    const service = createService({
      recommendationClient: {
        getRecommendation: () =>
          Promise.resolve({
            response: {
              algorithmVersion: "hybrid-similarity-v1",
              currentProblemSessionId: "77000000-0000-4000-8000-000000000001",
              evidence: [{ evidenceCode: "ERROR_PHRASE", problemSessionId: "77000000-0000-4000-8000-000000000002" }],
              finalSolution: "Allowed localhost in CorsConfiguration.",
              recommendationId: "77000000-0000-4000-8000-000000000003",
              recommendedProblemSessionId: "77000000-0000-4000-8000-000000000002",
              strength: "STRONG",
              whyThis: [
                {
                  evidenceCode: "ERROR_PHRASE",
                  message: "Both problems mention CORS.",
                  problemSessionId: "77000000-0000-4000-8000-000000000002"
                }
              ]
            },
            status: "matched"
          })
      }
    });

    await expect(service.invoke("popup")).resolves.toMatchObject({
      commandStatus: "matched",
      route: {
        currentProblemSessionId: "77000000-0000-4000-8000-000000000001",
        recommendationId: "77000000-0000-4000-8000-000000000003",
        recommendedProblemSessionId: "77000000-0000-4000-8000-000000000002",
        recommendation: {
          resources: [
            expect.objectContaining({
              alreadyVisited: true,
              decisive: true,
              title: "Spring CORS docs",
              url: "https://docs.spring.io/cors"
            }),
            expect.objectContaining({
              decisive: false,
              queryText: "spring security cors",
              url: null
            })
          ]
        },
        route: "similar_problem"
      }
    });
  });

  it("opens a stored replay resource only after an explicit resource command", async () => {
    const sidePanel = new RecordingSidePanelController();
    const storage = new InMemoryRecallRouteStorage();
    const service = createService({
      recommendationClient: matchedRecommendationClient(),
      routeStorage: storage,
      sidePanelController: sidePanel
    });

    await service.invoke("popup");
    expect(sidePanel.openedUrls).toEqual([]);

    const state = await service.openResource("77000000-0000-4000-8000-000000000004");

    expect(sidePanel.openedUrls).toEqual(["https://docs.spring.io/cors"]);
    expect(state.route.route).toBe("similar_problem");
    if (state.route.route !== "similar_problem") {
      throw new Error("expected similar problem route");
    }
    expect(state.route.recommendation.resources).toContainEqual(
      expect.objectContaining({ id: "77000000-0000-4000-8000-000000000004", openedNow: true })
    );
  });

  it("does not open unsafe or non-openable replay resources", async () => {
    const sidePanel = new RecordingSidePanelController();
    const service = createService({
      recommendationClient: matchedRecommendationClient(),
      sidePanelController: sidePanel
    });

    await service.invoke("popup");
    await service.openResource("77000000-0000-4000-8000-000000000005");

    expect(sidePanel.openedUrls).toEqual([]);
  });

  it("routes missing authentication to search without calling recommendation API", async () => {
    let called = false;
    const service = createService({
      accessToken: null,
      recommendationClient: {
        getRecommendation: () => {
          called = true;
          return Promise.resolve({ status: "no_match" });
        }
      }
    });

    await expect(service.invoke("popup")).resolves.toMatchObject({
      commandStatus: "authentication_required",
      route: { reason: "AUTHENTICATION_REQUIRED", route: "search" }
    });
    expect(called).toBe(false);
  });

  it("searches solved Problem Sessions through the authenticated background runtime", async () => {
    const service = createService({
      problemSessionSearchClient: {
        search: () =>
          Promise.resolve({
            response: {
              degradationMode: "LEXICAL",
              results: [
                {
                  errorPhrases: ["cors"],
                  evidenceCodes: ["ERROR_PHRASE"],
                  finalSolution: "Allowed localhost in CorsConfiguration.",
                  problemSessionId: "77000000-0000-4000-8000-000000000020",
                  technologies: ["Spring Security"],
                  title: "Spring CORS",
                  updatedAt: "2026-07-13T08:05:00.000Z"
                }
              ]
            },
            status: "received"
          })
      }
    });

    await expect(service.search("spring cors")).resolves.toMatchObject({
      degradationMode: "LEXICAL",
      query: "spring cors",
      results: [expect.objectContaining({ finalSolution: "Allowed localhost in CorsConfiguration." })],
      status: "received"
    });
  });

  it("opens a searched solved Problem Session as deterministic detail with replayable resources", async () => {
    const sidePanel = new RecordingSidePanelController();
    const storage = new InMemoryRecallRouteStorage();
    const service = createService({
      routeStorage: storage,
      sidePanelController: sidePanel
    });

    const state = await service.openSolvedProblem({
      errorPhrases: [],
      evidenceCodes: [],
      finalSolution: "JWT on gpt",
      problemSessionId: "77000000-0000-4000-8000-000000000020",
      technologies: [],
      title: "JWT issue",
      updatedAt: "2026-07-31T08:05:00.000Z"
    });

    expect(state).toMatchObject({
      commandStatus: "opened",
      route: {
        finalSolution: "JWT on gpt",
        problemSessionId: "77000000-0000-4000-8000-000000000020",
        resources: [
          expect.objectContaining({
            hostname: "chatgpt.com",
            url: "https://chatgpt.com/"
          }),
          expect.objectContaining({
            hostname: "spring.io",
            url: "https://spring.io/projects/spring-security"
          })
        ],
        route: "solved_problem"
      }
    });
    await expect(storage.read()).resolves.toMatchObject({ route: { route: "solved_problem" } });
  });

  it("opens safe resources from solved-problem detail without semantic matching", async () => {
    const sidePanel = new RecordingSidePanelController();
    const service = createService({ routeStorage: new InMemoryRecallRouteStorage(), sidePanelController: sidePanel });

    await service.openSolvedProblem({
      errorPhrases: [],
      evidenceCodes: [],
      finalSolution: "JWT on gpt",
      problemSessionId: "77000000-0000-4000-8000-000000000020",
      technologies: [],
      title: "JWT issue",
      updatedAt: "2026-07-31T08:05:00.000Z"
    });

    const state = await service.openResource("77000000-0000-4000-8000-000000000030");

    expect(sidePanel.openedUrls).toEqual(["https://chatgpt.com/"]);
    expect(state.route.route).toBe("solved_problem");
    if (state.route.route !== "solved_problem") {
      throw new Error("Expected solved problem route.");
    }
    expect(state.route.resources[0]).toMatchObject({ openedNow: true });
  });

  it("covers the Recall Ghost Replay critical flow from strong match to focused resource replay", async () => {
    const sidePanel = new RecordingSidePanelController();
    const service = createService({
      recommendationClient: matchedRecommendationClient(),
      sidePanelController: sidePanel
    });

    const matchedState = await service.invoke("popup");

    expect(matchedState).toMatchObject({
      commandStatus: "matched",
      route: {
        recommendation: {
          finalSolution: "Allowed localhost in CorsConfiguration.",
          resources: [
            expect.objectContaining({ alreadyVisited: true, decisive: true }),
            expect.objectContaining({ alreadyVisited: false, decisive: false })
          ],
          strength: "STRONG"
        },
        route: "similar_problem"
      }
    });
    expect(sidePanel.events).toEqual([]);

    const replayState = await service.openResource("77000000-0000-4000-8000-000000000004");

    expect(sidePanel.openedUrls).toEqual(["https://docs.spring.io/cors"]);
    expect(replayState.route.route).toBe("similar_problem");
    if (replayState.route.route !== "similar_problem") {
      throw new Error("Expected similar problem route.");
    }
    const openedResource = (replayState.route.recommendation.resources ?? []).find(
      (resource) => resource.id === "77000000-0000-4000-8000-000000000004"
    );
    expect(openedResource).toMatchObject({ openedNow: true });
  });

  it("falls back to search for no-match, offline, and AI-failure recommendation states", async () => {
    const noMatch = createService({
      recommendationClient: {
        getRecommendation: () => Promise.resolve({ status: "no_match" })
      }
    });
    await expect(noMatch.invoke("popup")).resolves.toMatchObject({
      commandStatus: "no_match",
      route: { reason: "NO_MATCH", route: "search" }
    });

    const offline = createService({
      recommendationClient: {
        getRecommendation: () => Promise.resolve({ status: "network_error" })
      }
    });
    await expect(offline.invoke("popup")).resolves.toMatchObject({
      commandStatus: "unavailable",
      route: { reason: "UNAVAILABLE", route: "search" }
    });

    const aiFailure = createService({
      recommendationClient: {
        getRecommendation: () => Promise.resolve({ status: "retryable_failure" })
      }
    });
    await expect(aiFailure.invoke("popup")).resolves.toMatchObject({
      commandStatus: "unavailable",
      route: { reason: "UNAVAILABLE", route: "search" }
    });
  });
});

function createService(options: Partial<CreateServiceOptions> = {}): RecallRuntimeService {
  return new RecallRuntimeService({
    authTokenProvider: {
      getValidAccessToken: () => Promise.resolve(options.accessToken === undefined ? "access-token" : options.accessToken)
    },
    now: () => new Date("2026-07-13T08:00:00.000Z"),
    problemSessionService: {
      readState: () => Promise.resolve(options.problemSessionState ?? problemSessionState(activeSession()))
    } as ProblemSessionRuntimeService,
    problemSessionDetailClient: options.problemSessionDetailClient ?? new InMemoryProblemSessionDetailApiClient(),
    problemSessionSearchClient: options.problemSessionSearchClient ?? {
      search: () => Promise.resolve({ response: { degradationMode: "LEXICAL", results: [] }, status: "received" })
    },
    recommendationClient: options.recommendationClient ?? {
      getRecommendation: () => Promise.resolve({ status: "no_match" })
    },
    routeStorage: options.routeStorage ?? new InMemoryRecallRouteStorage(),
    sidePanelController: options.sidePanelController ?? new RecordingSidePanelController()
  });
}

function problemSessionState(
  activeSession: ProblemSessionResponsePayload["state"]["activeSession"]
): ProblemSessionResponsePayload {
  return {
    commandStatus: "received",
    state: {
      activeSession,
      dismissedSession: null
    }
  };
}

function activeSession(): NonNullable<ProblemSessionResponsePayload["state"]["activeSession"]> {
  return {
    detectionReasons: ["manual_session"],
    endedAt: null,
    etag: "1",
    id: "77000000-0000-4000-8000-000000000001",
    source: "MANUAL",
    startedAt: "2026-07-13T08:00:00.000Z",
    status: "CANDIDATE",
    title: "Spring Security CORS not working",
    updatedAt: "2026-07-13T08:00:00.000Z",
    version: 1
  };
}

interface CreateServiceOptions {
  readonly accessToken: string | null;
  readonly problemSessionState: ProblemSessionResponsePayload;
  readonly problemSessionDetailClient: ProblemSessionDetailApiClient;
  readonly problemSessionSearchClient: ProblemSessionSearchApiClient;
  readonly recommendationClient: ProblemRecommendationApiClient;
  readonly routeStorage: RecallRouteStorage;
  readonly sidePanelController: RecallSidePanelController;
}

class InMemoryRecallRouteStorage implements RecallRouteStorage {
  private state: RecallStatePayload | null = null;

  clear(): Promise<void> {
    this.state = null;
    return Promise.resolve();
  }

  read(): Promise<RecallStatePayload> {
    return Promise.resolve(
      this.state ?? {
        commandStatus: "opened",
        invokedAt: null,
        route: { reason: "NO_ACTIVE_PROBLEM", route: "search" },
        source: null
      }
    );
  }

  write(state: RecallStatePayload): Promise<void> {
    this.state = state;
    return Promise.resolve();
  }
}

class RecordingSidePanelController implements RecallSidePanelController {
  readonly events: string[] = [];
  readonly openedUrls: string[] = [];

  openResource(url: string): Promise<"opened"> {
    this.openedUrls.push(url);
    return Promise.resolve("opened");
  }

  openForCurrentWindow(): Promise<"opened"> {
    this.events.push("open");
    return Promise.resolve("opened");
  }

  restoreSourceFocus(): Promise<void> {
    this.events.push("restore-focus");
    return Promise.resolve();
  }
}

function matchedRecommendationClient(): ProblemRecommendationApiClient {
  return {
    getRecommendation: () =>
      Promise.resolve({
        response: {
          algorithmVersion: "hybrid-similarity-v1",
          currentProblemSessionId: "77000000-0000-4000-8000-000000000001",
          evidence: [{ evidenceCode: "ERROR_PHRASE", problemSessionId: "77000000-0000-4000-8000-000000000002" }],
          finalSolution: "Allowed localhost in CorsConfiguration.",
          recommendationId: "77000000-0000-4000-8000-000000000003",
          recommendedProblemSessionId: "77000000-0000-4000-8000-000000000002",
          strength: "STRONG",
          whyThis: [
            {
              evidenceCode: "ERROR_PHRASE",
              message: "Both problems mention CORS.",
              problemSessionId: "77000000-0000-4000-8000-000000000002"
            }
          ]
        },
        status: "matched"
      })
  };
}

class InMemoryProblemSessionDetailApiClient implements ProblemSessionDetailApiClient {
  getProblemSessionDetail(command: { readonly problemSessionId: string }): Promise<ProblemSessionDetailApiResult> {
    if (command.problemSessionId === "77000000-0000-4000-8000-000000000001") {
      return Promise.resolve({
        response: detail(command.problemSessionId, [
          artifact("77000000-0000-4000-8000-000000000010", 1, "https://docs.spring.io/cors", "spring cors", false)
        ]),
        status: "found"
      });
    }

    if (command.problemSessionId === "77000000-0000-4000-8000-000000000002") {
      return Promise.resolve({
        response: detail(command.problemSessionId, [
          artifact("77000000-0000-4000-8000-000000000004", 1, "https://docs.spring.io/cors", null, true),
          artifact("77000000-0000-4000-8000-000000000005", 2, null, "spring security cors", false)
        ]),
        status: "found"
      });
    }

    if (command.problemSessionId === "77000000-0000-4000-8000-000000000020") {
      return Promise.resolve({
        response: detail(command.problemSessionId, [
          artifact("77000000-0000-4000-8000-000000000030", 1, "https://chatgpt.com/", null, false),
          artifact(
            "77000000-0000-4000-8000-000000000031",
            2,
            "https://spring.io/projects/spring-security",
            null,
            false
          )
        ]),
        status: "found"
      });
    }

    return Promise.resolve({ status: "not_found" });
  }
}

function detail(id: string, artifacts: ProblemSessionDetailResponse["artifacts"]): ProblemSessionDetailResponse {
  return {
    artifacts,
    id,
    omittedArtifactCount: 0,
    source: "MANUAL" as const,
    startedAt: "2026-07-13T08:00:00.000Z",
    status: "SOLVED" as const,
    updatedAt: "2026-07-13T08:00:00.000Z",
    version: 1
  };
}

function artifact(id: string, position: number, url: string | null, queryText: string | null, decisive: boolean) {
  return {
    artifactType: url === null ? ("SEARCH_QUERY" as const) : ("DOCUMENTATION" as const),
    decisive,
    hostname: hostnameForUrl(url),
    id,
    occurredAt: "2026-07-13T08:05:00.000Z",
    position,
    queryText,
    sanitizedUrl: url,
    title: url === null ? null : "Spring CORS docs"
  };
}

function hostnameForUrl(url: string | null): string | null {
  if (url === null) {
    return null;
  }
  return new URL(url).hostname;
}
