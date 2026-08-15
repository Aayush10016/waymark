import { FetchAuthRefreshClient, AuthTokenService, handleAuthMessage } from "../features/auth";
import {
  BrowserCurrentTabReader,
  BrowserNetworkStateReader,
  BrowserPermissionReader,
  CaptureStateService,
  handleCaptureStateMessage
} from "../services/capture-state";
import { CaptureControlRuntimeService } from "../services/capture-controls";
import { CaptureConsentRuntimeService } from "../services/consent";
import { CaptureBatchSyncService, FetchCaptureBatchApiClient, SyncingCaptureEventSink } from "../services/capture-sync";
import { ProblemSessionCaptureEventSink } from "../services/navigation-capture";
import {
  FetchProblemSessionCommandApiClient,
  ProblemSessionRuntimeService,
  handleProblemSessionMessage
} from "../services/problem-session";
import {
  BrowserRecallSidePanelController,
  FetchProblemSessionDetailApiClient,
  FetchProblemSessionSearchApiClient,
  FetchProblemRecommendationApiClient,
  RecallRuntimeService,
  handleRecallMessage
} from "../services/recall";
import { createDefaultNavigationCaptureServiceWithSink, installNavigationCaptureListeners } from "./navigation-capture-listener";
import { trailApiBaseUrl } from "../shared/config/api-base-url";
import { ChromeLocalAuthSessionStorage } from "../storage/auth/auth-session-storage";
import { ChromeLocalCaptureControlStorage } from "../storage/capture-controls";
import { IndexedDbCaptureEventQueue } from "../storage/capture-queue";
import { ChromeLocalCaptureConsentStorage } from "../storage/consent/capture-consent-storage";
import { ChromeLocalProblemSessionRuntimeStorage } from "../storage/problem-session";
import { ChromeLocalRecallRouteStorage } from "../storage/recall";

const captureQueue = new IndexedDbCaptureEventQueue();
const apiBaseUrl = trailApiBaseUrl();

const authTokenService = new AuthTokenService({
  refreshClient: new FetchAuthRefreshClient({ baseUrl: apiBaseUrl }),
  storage: new ChromeLocalAuthSessionStorage(),
  now: () => new Date()
});

const captureSyncService = new CaptureBatchSyncService({
  apiClient: new FetchCaptureBatchApiClient({ baseUrl: apiBaseUrl }),
  authTokenProvider: authTokenService,
  clientInstanceId: chrome.runtime.id,
  queue: captureQueue
});

const captureStateService = new CaptureStateService({
  authReader: authTokenService,
  consentReader: new CaptureConsentRuntimeService({
    storage: new ChromeLocalCaptureConsentStorage()
  }),
  controlService: new CaptureControlRuntimeService({
    storage: new ChromeLocalCaptureControlStorage()
  }),
  currentTabReader: new BrowserCurrentTabReader(chrome),
  networkStateReader: new BrowserNetworkStateReader(),
  permissionReader: new BrowserPermissionReader(chrome),
  queue: captureQueue
});

const problemSessionRuntimeService = new ProblemSessionRuntimeService({
  apiClient: new FetchProblemSessionCommandApiClient({ baseUrl: apiBaseUrl }),
  authTokenProvider: authTokenService,
  captureSynchronizer: captureSyncService,
  storage: new ChromeLocalProblemSessionRuntimeStorage()
});

const recallRuntimeService = new RecallRuntimeService({
  authTokenProvider: authTokenService,
  problemSessionService: problemSessionRuntimeService,
  problemSessionDetailClient: new FetchProblemSessionDetailApiClient({ baseUrl: apiBaseUrl }),
  problemSessionSearchClient: new FetchProblemSessionSearchApiClient({ baseUrl: apiBaseUrl }),
  recommendationClient: new FetchProblemRecommendationApiClient({ baseUrl: apiBaseUrl }),
  routeStorage: new ChromeLocalRecallRouteStorage(),
  sidePanelController: new BrowserRecallSidePanelController(chrome)
});

chrome.runtime.onMessage.addListener((message: unknown, _sender, sendResponse) => {
  void handleRuntimeMessage(message).then((response) => {
    if (response !== undefined) {
      sendResponse(response);
    }
  });

  return true;
});

installNavigationCaptureListeners(
  chrome,
  createDefaultNavigationCaptureServiceWithSink(new ProblemSessionCaptureEventSink({
    delegate: new SyncingCaptureEventSink({
      delegate: captureQueue,
      synchronizer: captureSyncService
    }),
    problemSessionReader: problemSessionRuntimeService
  }))
);

chrome.commands.onCommand.addListener((command) => {
  if (command === "trail-recall") {
    void recallRuntimeService.invoke("command");
  }
});

async function handleRuntimeMessage(message: unknown): Promise<unknown> {
  const authResult = await handleAuthMessage(authTokenService, message);
  if (authResult.handled) {
    return authResult.response;
  }

  const captureStateResult = await handleCaptureStateMessage(captureStateService, message);
  if (captureStateResult.handled) {
    return captureStateResult.response;
  }

  const problemSessionResult = await handleProblemSessionMessage(problemSessionRuntimeService, message);
  if (problemSessionResult.handled) {
    return problemSessionResult.response;
  }

  const recallResult = await handleRecallMessage(recallRuntimeService, message);
  if (recallResult.handled) {
    return recallResult.response;
  }

  return authResult.response ?? captureStateResult.response ?? problemSessionResult.response ?? recallResult.response;
}
