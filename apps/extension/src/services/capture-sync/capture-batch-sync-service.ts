import type { CaptureEventSource, CaptureResourceType } from "../navigation-capture";
import type {
  CaptureEventQueueRepository,
  CaptureQueueStatistics,
  StoredCaptureQueueRecord
} from "../../storage/capture-queue";
import type {
  CaptureBatchApiClient,
  CaptureBatchRequest,
  CaptureBatchResponse,
  CaptureBatchUploadResult
} from "./capture-batch-api-client";

export interface CaptureSyncAuthTokenProvider {
  getValidAccessToken(): Promise<string | null>;
}

export interface CaptureBatchSyncServiceOptions {
  readonly apiClient: CaptureBatchApiClient;
  readonly authTokenProvider: CaptureSyncAuthTokenProvider;
  readonly clientInstanceId: string;
  readonly maxBatchSize?: number;
  readonly queue: CaptureEventQueueRepository;
}

export type CaptureSyncStatus =
  | "empty"
  | "processed"
  | "authentication_required"
  | "retryable_failure"
  | "permanent_failure"
  | "malformed_response";

export type CaptureSyncFailureReason =
  | "missing_access_token"
  | "server_rejected_access_token"
  | "network_error"
  | "rate_limited"
  | "server_unavailable"
  | "bad_request"
  | "payload_too_large"
  | "malformed_response";

export interface CaptureSyncStatistics {
  readonly acceptedCount: number;
  readonly duplicateCount: number;
  readonly rejectedCount: number;
  readonly removedCount: number;
  readonly retainedCount: number;
  readonly selectedCount: number;
  readonly totalRecordsAfter: number;
  readonly totalRecordsBefore: number;
}

export interface CaptureSyncResult {
  readonly failureReason?: CaptureSyncFailureReason;
  readonly queueStatisticsAfter: CaptureQueueStatistics;
  readonly queueStatisticsBefore: CaptureQueueStatistics;
  readonly removedRecordIds: readonly string[];
  readonly retainedRecordIds: readonly string[];
  readonly statistics: CaptureSyncStatistics;
  readonly status: CaptureSyncStatus;
}

/**
 * Uploads one bounded capture batch and reconciles acknowledged local queue records.
 */
export class CaptureBatchSyncService {
  private readonly apiClient: CaptureBatchApiClient;
  private readonly authTokenProvider: CaptureSyncAuthTokenProvider;
  private readonly clientInstanceId: string;
  private readonly maxBatchSize: number | undefined;
  private readonly queue: CaptureEventQueueRepository;
  private pendingSynchronization: Promise<CaptureSyncResult> | null = null;

  constructor(options: CaptureBatchSyncServiceOptions) {
    this.apiClient = options.apiClient;
    this.authTokenProvider = options.authTokenProvider;
    this.clientInstanceId = requireNonBlank(options.clientInstanceId, "clientInstanceId");
    this.maxBatchSize = options.maxBatchSize;
    this.queue = options.queue;
  }

  async synchronize(): Promise<CaptureSyncResult> {
    if (this.pendingSynchronization !== null) {
      return this.pendingSynchronization;
    }

    this.pendingSynchronization = this.performSynchronization();
    try {
      return await this.pendingSynchronization;
    } finally {
      this.pendingSynchronization = null;
    }
  }

  private async performSynchronization(): Promise<CaptureSyncResult> {
    const queueStatisticsBefore = await this.queue.statistics();
    const selectedRecords = await this.queue.selectBatch(
      this.maxBatchSize === undefined ? {} : { maxBatchSize: this.maxBatchSize }
    );

    if (selectedRecords.length === 0) {
      const queueStatisticsAfter = await this.queue.statistics();
      return this.result({
        queueStatisticsAfter,
        queueStatisticsBefore,
        selectedRecords,
        status: "empty"
      });
    }

    const accessToken = await this.authTokenProvider.getValidAccessToken();
    if (accessToken === null) {
      const queueStatisticsAfter = await this.queue.statistics();
      return this.result({
        failureReason: "missing_access_token",
        queueStatisticsAfter,
        queueStatisticsBefore,
        selectedRecords,
        status: "authentication_required"
      });
    }

    const uploadResult = await this.apiClient.upload({
      accessToken,
      idempotencyKey: createIdempotencyKey(selectedRecords),
      request: toCaptureBatchRequest(this.clientInstanceId, selectedRecords)
    });

    if (uploadResult.status !== "processed") {
      return this.failedUploadResult(uploadResult, queueStatisticsBefore, selectedRecords);
    }

    const removedRecordIds = acknowledgedRecordIds(uploadResult.response, selectedRecords);
    await this.queue.remove(removedRecordIds);
    const queueStatisticsAfter = await this.queue.statistics();

    return this.result({
      acceptedCount: uploadResult.response.accepted.length,
      duplicateCount: uploadResult.response.duplicate.length,
      queueStatisticsAfter,
      queueStatisticsBefore,
      rejectedCount: uploadResult.response.rejected.length,
      removedRecordIds,
      selectedRecords,
      status: "processed"
    });
  }

  private async failedUploadResult(
    uploadResult: Exclude<CaptureBatchUploadResult, { readonly status: "processed" }>,
    queueStatisticsBefore: CaptureQueueStatistics,
    selectedRecords: readonly StoredCaptureQueueRecord[]
  ): Promise<CaptureSyncResult> {
    const queueStatisticsAfter = await this.queue.statistics();
    const failure = mapUploadFailure(uploadResult.status);
    return this.result({
      failureReason: failure.failureReason,
      queueStatisticsAfter,
      queueStatisticsBefore,
      selectedRecords,
      status: failure.status
    });
  }

  private result(options: {
    readonly acceptedCount?: number;
    readonly duplicateCount?: number;
    readonly failureReason?: CaptureSyncFailureReason;
    readonly queueStatisticsAfter: CaptureQueueStatistics;
    readonly queueStatisticsBefore: CaptureQueueStatistics;
    readonly rejectedCount?: number;
    readonly removedRecordIds?: readonly string[];
    readonly selectedRecords: readonly StoredCaptureQueueRecord[];
    readonly status: CaptureSyncStatus;
  }): CaptureSyncResult {
    const removedRecordIds = options.removedRecordIds ?? [];
    const retainedRecordIds = options.selectedRecords
      .map((record) => record.id)
      .filter((recordId) => !removedRecordIds.includes(recordId));
    const acceptedCount = options.acceptedCount ?? 0;
    const duplicateCount = options.duplicateCount ?? 0;
    const rejectedCount = options.rejectedCount ?? 0;

    return Object.freeze({
      ...(options.failureReason === undefined ? {} : { failureReason: options.failureReason }),
      queueStatisticsAfter: options.queueStatisticsAfter,
      queueStatisticsBefore: options.queueStatisticsBefore,
      removedRecordIds: Object.freeze([...removedRecordIds]),
      retainedRecordIds: Object.freeze(retainedRecordIds),
      statistics: Object.freeze({
        acceptedCount,
        duplicateCount,
        rejectedCount,
        removedCount: removedRecordIds.length,
        retainedCount: retainedRecordIds.length,
        selectedCount: options.selectedRecords.length,
        totalRecordsAfter: options.queueStatisticsAfter.totalRecords,
        totalRecordsBefore: options.queueStatisticsBefore.totalRecords
      }),
      status: options.status
    });
  }
}

function toCaptureBatchRequest(
  clientInstanceId: string,
  records: readonly StoredCaptureQueueRecord[]
): CaptureBatchRequest {
  return {
    clientInstanceId,
    events: records.map((record) => ({
      browserEventTimeMilliseconds: record.browserEventTime,
      clientEventId: record.id,
      clientSequence: null,
      navigationType: record.navigationType,
      parentClientEventId: null,
      problemSessionId: record.problemSessionId,
      resourceType: toApiResourceType(record.resourceType),
      source: toApiSource(record.source),
      tabSessionId: null,
      title: record.title,
      transitionQualifiers: [...record.transitionQualifiers],
      url: record.canonicalUrl,
      occurredAt: record.capturedAt
    }))
  };
}

function toApiSource(source: CaptureEventSource): CaptureBatchRequest["events"][number]["source"] {
  switch (source) {
    case "tab_updated":
      return "TAB_UPDATED";
    case "web_navigation_committed":
      return "WEB_NAVIGATION_COMMITTED";
    case "web_navigation_history_state":
      return "WEB_NAVIGATION_HISTORY_STATE";
  }
}

function toApiResourceType(resourceType: CaptureResourceType): CaptureBatchRequest["events"][number]["resourceType"] {
  switch (resourceType) {
    case "ai_chat":
      return "AI_CHAT";
    case "documentation":
      return "DOCUMENTATION";
    case "forum":
      return "FORUM";
    case "other":
      return "OTHER";
    case "repository":
      return "REPOSITORY";
    case "search":
      return "SEARCH";
    case "video":
      return "VIDEO";
  }
}

function acknowledgedRecordIds(
  response: CaptureBatchResponse,
  selectedRecords: readonly StoredCaptureQueueRecord[]
): readonly string[] {
  const selectedRecordIds = new Set(selectedRecords.map((record) => record.id));
  const acknowledgedIds = new Set<string>();
  for (const acceptedEvent of response.accepted) {
    if (selectedRecordIds.has(acceptedEvent.clientEventId)) {
      acknowledgedIds.add(acceptedEvent.clientEventId);
    }
  }
  for (const duplicateEvent of response.duplicate) {
    if (selectedRecordIds.has(duplicateEvent.clientEventId)) {
      acknowledgedIds.add(duplicateEvent.clientEventId);
    }
  }
  return [...acknowledgedIds];
}

function mapUploadFailure(status: Exclude<CaptureBatchUploadResult["status"], "processed">): {
  readonly failureReason: CaptureSyncFailureReason;
  readonly status: Exclude<CaptureSyncStatus, "empty" | "processed">;
} {
  switch (status) {
    case "authentication_rejected":
      return { failureReason: "server_rejected_access_token", status: "authentication_required" };
    case "bad_request":
      return { failureReason: "bad_request", status: "permanent_failure" };
    case "malformed_response":
      return { failureReason: "malformed_response", status: "malformed_response" };
    case "network_error":
      return { failureReason: "network_error", status: "retryable_failure" };
    case "payload_too_large":
      return { failureReason: "payload_too_large", status: "permanent_failure" };
    case "rate_limited":
      return { failureReason: "rate_limited", status: "retryable_failure" };
    case "retryable_failure":
      return { failureReason: "server_unavailable", status: "retryable_failure" };
  }
}

function createIdempotencyKey(records: readonly StoredCaptureQueueRecord[]): string {
  return `capture-batch:${records.map((record) => record.id).join(",")}`;
}

function requireNonBlank(value: string, name: string): string {
  if (value.trim().length === 0) {
    throw new Error(`${name} must not be blank.`);
  }

  return value;
}
