import type {
  ActiveProblemSessionPayload,
  GhostReplayResourcePayload,
  RecallInvocationSource,
  RecallSearchReason,
  RecallSearchResultPayload,
  RecallSearchResultPayloadEnvelope,
  RecallStatePayload,
  SimilarProblemRecommendationPayload
} from "@trail/extension-messages";
import type { ProblemSessionRuntimeService } from "../problem-session";
import type {
  ProblemRecommendationApiClient,
  ProblemRecommendationApiResult,
  ProblemRecommendationDetailResponse
} from "./recommendation-api-client";
import type { ProblemSessionDetailApiClient, ProblemSessionDetailResponse } from "./problem-session-detail-api-client";
import type { ProblemSessionSearchApiClient, ProblemSessionSearchApiResult } from "./problem-session-search-api-client";
import type { RecallRouteStorage } from "../../storage/recall";

export interface RecallAuthTokenProvider {
  getValidAccessToken(): Promise<string | null>;
}

export interface RecallSidePanelController {
  openResource(url: string): Promise<"blocked" | "opened">;
  openForCurrentWindow(): Promise<"blocked" | "opened">;
  restoreSourceFocus(): Promise<void>;
}

export interface RecallRuntimeServiceOptions {
  readonly authTokenProvider: RecallAuthTokenProvider;
  readonly now?: () => Date;
  readonly problemSessionService: ProblemSessionRuntimeService;
  readonly problemSessionDetailClient: ProblemSessionDetailApiClient;
  readonly problemSessionSearchClient: ProblemSessionSearchApiClient;
  readonly recommendationClient: ProblemRecommendationApiClient;
  readonly routeStorage: RecallRouteStorage;
  readonly sidePanelController: RecallSidePanelController;
}

/**
 * Coordinates explicit Recall invocation without coupling UI surfaces to tokens, HTTP, or browser APIs.
 */
export class RecallRuntimeService {
  private readonly authTokenProvider: RecallAuthTokenProvider;
  private readonly now: () => Date;
  private readonly problemSessionService: ProblemSessionRuntimeService;
  private readonly problemSessionDetailClient: ProblemSessionDetailApiClient;
  private readonly problemSessionSearchClient: ProblemSessionSearchApiClient;
  private readonly recommendationClient: ProblemRecommendationApiClient;
  private readonly routeStorage: RecallRouteStorage;
  private readonly sidePanelController: RecallSidePanelController;

  constructor(options: RecallRuntimeServiceOptions) {
    this.authTokenProvider = options.authTokenProvider;
    this.now = options.now ?? (() => new Date());
    this.problemSessionService = options.problemSessionService;
    this.problemSessionDetailClient = options.problemSessionDetailClient;
    this.problemSessionSearchClient = options.problemSessionSearchClient;
    this.recommendationClient = options.recommendationClient;
    this.routeStorage = options.routeStorage;
    this.sidePanelController = options.sidePanelController;
  }

  async readState(): Promise<RecallStatePayload> {
    return this.routeStorage.read();
  }

  async invoke(source: RecallInvocationSource): Promise<RecallStatePayload> {
    const activeSession = (await this.problemSessionService.readState()).state.activeSession;
    if (activeSession === null) {
      return this.open(searchState("no_active_problem", "NO_ACTIVE_PROBLEM", source, this.now()));
    }

    const accessToken = await this.authTokenProvider.getValidAccessToken();
    if (accessToken === null) {
      return this.open(searchState("authentication_required", "AUTHENTICATION_REQUIRED", source, this.now()));
    }

    const recommendation = await this.recommendationClient.getRecommendation({
      accessToken,
      problemSessionId: activeSession.id
    });
    const resources = await this.resourcesForRecommendation(accessToken, activeSession.id, recommendation);
    return this.open(stateForRecommendationResult(activeSession, recommendation, source, this.now(), resources));
  }

  async openResource(resourceId: string): Promise<RecallStatePayload> {
    const currentState = await this.routeStorage.read();
    const resources = resourcesForRoute(currentState);
    if (resources === null) {
      return currentState;
    }

    const selectedResource = resources.find((resource) => resource.id === resourceId);
    if (selectedResource?.url === undefined || selectedResource.url === null || !isSafeHttpUrl(selectedResource.url)) {
      return currentState;
    }

    const result = await this.sidePanelController.openResource(selectedResource.url);
    if (result !== "opened") {
      return currentState;
    }

    const updatedResources = resources.map((resource) =>
      resource.id === resourceId ? Object.freeze({ ...resource, openedNow: true }) : resource
    );
    const updatedState = withResources(currentState, updatedResources);
    await this.routeStorage.write(updatedState);
    return updatedState;
  }

  async openSolvedProblem(result: RecallSearchResultPayload): Promise<RecallStatePayload> {
    const accessToken = await this.authTokenProvider.getValidAccessToken();
    if (accessToken === null) {
      return this.open(searchState("authentication_required", "AUTHENTICATION_REQUIRED", "popup", this.now()));
    }

    const detail = await this.problemSessionDetailClient.getProblemSessionDetail({
      accessToken,
      problemSessionId: result.problemSessionId
    });
    if (detail.status === "authentication_rejected") {
      return this.open(searchState("authentication_required", "AUTHENTICATION_REQUIRED", "popup", this.now()));
    }
    if (detail.status === "not_found") {
      return this.open(searchState("not_found", "NOT_FOUND", "popup", this.now()));
    }
    if (detail.status !== "found") {
      return this.open(searchState("unavailable", "UNAVAILABLE", "popup", this.now()));
    }

    return this.open(Object.freeze({
      commandStatus: "opened",
      invokedAt: this.now().toISOString(),
      route: Object.freeze({
        finalSolution: result.finalSolution,
        problemSessionId: result.problemSessionId,
        resources: toDetailResources(detail.response),
        route: "solved_problem",
        title: result.title ?? detail.response.title ?? null,
        updatedAt: result.updatedAt
      }),
      source: "popup"
    }));
  }

  async search(query: string | null): Promise<RecallSearchResultPayloadEnvelope> {
    const accessToken = await this.authTokenProvider.getValidAccessToken();
    if (accessToken === null) {
      return searchResult("authentication_required", query, null);
    }

    return searchResultForApiResult(
      query,
      await this.problemSessionSearchClient.search({
        accessToken,
        query
      })
    );
  }

  private async resourcesForRecommendation(
    accessToken: string,
    currentProblemSessionId: string,
    recommendation: ProblemRecommendationApiResult
  ): Promise<readonly GhostReplayResourcePayload[]> {
    if (recommendation.status !== "matched") {
      return Object.freeze([]);
    }

    const [currentDetail, recommendedDetail] = await Promise.all([
      this.problemSessionDetailClient.getProblemSessionDetail({ accessToken, problemSessionId: currentProblemSessionId }),
      this.problemSessionDetailClient.getProblemSessionDetail({
        accessToken,
        problemSessionId: recommendation.response.recommendedProblemSessionId
      })
    ]);
    if (recommendedDetail.status !== "found") {
      return Object.freeze([]);
    }

    return toGhostReplayResources(
      recommendedDetail.response,
      currentDetail.status === "found" ? currentDetail.response : null
    );
  }

  private async open(state: RecallStatePayload): Promise<RecallStatePayload> {
    await this.routeStorage.write(state);
    if (state.source !== "popup") {
      const result = await this.sidePanelController.openForCurrentWindow();
      if (result === "opened") {
        await this.sidePanelController.restoreSourceFocus();
      }
    }
    return state;
  }
}

function resourcesForRoute(state: RecallStatePayload): readonly GhostReplayResourcePayload[] | null {
  if (state.route.route === "similar_problem") {
    return state.route.recommendation.resources ?? [];
  }
  if (state.route.route === "solved_problem") {
    return state.route.resources;
  }
  return null;
}

function searchResultForApiResult(
  query: string | null,
  result: ProblemSessionSearchApiResult
): RecallSearchResultPayloadEnvelope {
  if (result.status === "received") {
    return searchResult("received", query, result.response);
  }

  if (result.status === "authentication_rejected") {
    return searchResult("authentication_required", query, null);
  }

  return searchResult("unavailable", query, null);
}

function searchResult(
  status: RecallSearchResultPayloadEnvelope["status"],
  query: string | null,
  response: Extract<ProblemSessionSearchApiResult, { readonly status: "received" }>["response"] | null
): RecallSearchResultPayloadEnvelope {
  return Object.freeze({
    degradationMode: response?.degradationMode ?? null,
    query: query === null || query.trim().length === 0 ? null : query,
    results: Object.freeze(
      (response?.results ?? []).map((result) =>
        Object.freeze({
          errorPhrases: Object.freeze([...result.errorPhrases]),
          evidenceCodes: Object.freeze([...result.evidenceCodes]),
          finalSolution: result.finalSolution,
          generatedSummary: result.generatedSummary ?? null,
          problemSessionId: result.problemSessionId,
          problemType: result.problemType ?? null,
          technologies: Object.freeze([...result.technologies]),
          title: result.title ?? null,
          updatedAt: result.updatedAt
        })
      )
    ),
    status
  });
}

function stateForRecommendationResult(
  activeSession: ActiveProblemSessionPayload,
  result: ProblemRecommendationApiResult,
  source: RecallInvocationSource,
  invokedAt: Date,
  resources: readonly GhostReplayResourcePayload[]
): RecallStatePayload {
  if (result.status === "matched") {
    return Object.freeze({
      commandStatus: "matched",
      invokedAt: invokedAt.toISOString(),
      route: Object.freeze({
        currentProblemSessionId: activeSession.id,
        recommendation: toSimilarProblemRecommendation(result.response, resources),
        recommendationId: result.response.recommendationId,
        recommendedProblemSessionId: result.response.recommendedProblemSessionId,
        route: "similar_problem"
      }),
      source
    });
  }

  return searchState(mapResultStatus(result.status), mapResultReason(result.status), source, invokedAt);
}

function toSimilarProblemRecommendation(
  response: ProblemRecommendationDetailResponse,
  resources: readonly GhostReplayResourcePayload[]
): SimilarProblemRecommendationPayload {
  return Object.freeze({
    algorithmVersion: response.algorithmVersion,
    durationSeconds: response.durationSeconds ?? null,
    evidence: Object.freeze(response.evidence.map((evidence) => Object.freeze({ ...evidence }))),
    finalSolution: response.finalSolution,
    recommendationId: response.recommendationId,
    recommendedProblemSessionId: response.recommendedProblemSessionId,
    resources: Object.freeze(resources.map((resource) => Object.freeze({ ...resource }))),
    solvedAt: response.solvedAt ?? null,
    strength: response.strength,
    title: response.title ?? null,
    whyThis: Object.freeze(response.whyThis.map((message) => Object.freeze({ ...message })))
  });
}

function toGhostReplayResources(
  recommendedDetail: ProblemSessionDetailResponse,
  currentDetail: ProblemSessionDetailResponse | null
): readonly GhostReplayResourcePayload[] {
  const currentEvidence = currentDetail === null ? new Set<string>() : evidenceKeys(currentDetail);
  return Object.freeze(
    recommendedDetail.artifacts
      .filter((artifact) => artifact.sanitizedUrl !== null || artifact.queryText !== null)
      .sort((left, right) => {
        if (left.decisive !== right.decisive) {
          return left.decisive ? -1 : 1;
        }
        return left.position - right.position;
      })
      .slice(0, 5)
      .map((artifact) =>
        Object.freeze({
          alreadyVisited: artifactEvidenceKeys(artifact).some((key) => currentEvidence.has(key)),
          decisive: artifact.decisive,
          hostname: artifact.hostname ?? null,
          id: artifact.id,
          kind: artifact.artifactType,
          occurredAt: artifact.occurredAt,
          openedNow: false,
          queryText: artifact.queryText ?? null,
          title: artifact.title ?? null,
          url: artifact.sanitizedUrl ?? null
        })
      )
  );
}

function toDetailResources(detail: ProblemSessionDetailResponse): readonly GhostReplayResourcePayload[] {
  return Object.freeze(
    detail.artifacts
      .filter((artifact) => artifact.sanitizedUrl !== null || artifact.queryText !== null)
      .sort((left, right) => left.position - right.position)
      .map((artifact) =>
        Object.freeze({
          alreadyVisited: false,
          decisive: artifact.decisive,
          hostname: artifact.hostname ?? null,
          id: artifact.id,
          kind: artifact.artifactType,
          occurredAt: artifact.occurredAt,
          openedNow: false,
          queryText: artifact.queryText ?? null,
          title: artifact.title ?? null,
          url: artifact.sanitizedUrl ?? null
        })
      )
  );
}

function evidenceKeys(detail: ProblemSessionDetailResponse): Set<string> {
  return new Set(detail.artifacts.flatMap(artifactEvidenceKeys));
}

function artifactEvidenceKeys(artifact: ProblemSessionDetailResponse["artifacts"][number]): string[] {
  const keys: string[] = [];
  if (artifact.sanitizedUrl !== null && artifact.sanitizedUrl !== undefined) {
    keys.push(`url:${artifact.sanitizedUrl}`);
  }
  if (artifact.queryText !== null && artifact.queryText !== undefined && artifact.queryText.trim().length > 0) {
    keys.push(`query:${artifact.queryText.trim().toLocaleLowerCase()}`);
  }
  return keys;
}

function withResources(state: RecallStatePayload, resources: readonly GhostReplayResourcePayload[]): RecallStatePayload {
  if (state.route.route === "solved_problem") {
    return Object.freeze({
      ...state,
      route: Object.freeze({
        ...state.route,
        resources: Object.freeze(resources.map((resource) => Object.freeze({ ...resource })))
      })
    });
  }

  if (state.route.route !== "similar_problem") {
    return state;
  }

  return Object.freeze({
    ...state,
    route: Object.freeze({
      ...state.route,
      recommendation: Object.freeze({
        ...state.route.recommendation,
        resources: Object.freeze(resources.map((resource) => Object.freeze({ ...resource })))
      })
    })
  });
}

function isSafeHttpUrl(value: string): boolean {
  try {
    const parsed = new URL(value);
    return parsed.protocol === "https:" || parsed.protocol === "http:";
  } catch {
    return false;
  }
}

function searchState(
  commandStatus: RecallStatePayload["commandStatus"],
  reason: RecallSearchReason,
  source: RecallInvocationSource,
  invokedAt: Date
): RecallStatePayload {
  return Object.freeze({
    commandStatus,
    invokedAt: invokedAt.toISOString(),
    route: Object.freeze({
      reason,
      route: "search"
    }),
    source
  });
}

function mapResultStatus(status: Exclude<ProblemRecommendationApiResult["status"], "matched">): RecallStatePayload["commandStatus"] {
  switch (status) {
    case "authentication_rejected":
      return "authentication_required";
    case "no_match":
      return "no_match";
    case "not_found":
      return "not_found";
    case "malformed_response":
    case "network_error":
    case "retryable_failure":
      return "unavailable";
  }
}

function mapResultReason(status: Exclude<ProblemRecommendationApiResult["status"], "matched">): RecallSearchReason {
  switch (status) {
    case "authentication_rejected":
      return "AUTHENTICATION_REQUIRED";
    case "no_match":
      return "NO_MATCH";
    case "not_found":
      return "NOT_FOUND";
    case "malformed_response":
    case "network_error":
    case "retryable_failure":
      return "UNAVAILABLE";
  }
}
