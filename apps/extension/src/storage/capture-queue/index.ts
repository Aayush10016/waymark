export {
  CAPTURE_QUEUE_DATABASE_NAME,
  CAPTURE_QUEUE_DATABASE_VERSION,
  CAPTURE_QUEUE_ENQUEUED_AT_INDEX,
  CAPTURE_QUEUE_RECORD_SCHEMA_VERSION,
  CAPTURE_QUEUE_STORE_NAME,
  DEFAULT_CAPTURE_QUEUE_MAX_BATCH_SIZE,
  DEFAULT_CAPTURE_QUEUE_MAX_RECORDS,
  freezeStoredCaptureQueueRecord,
  IndexedDbCaptureEventQueue,
  type CaptureQueueBatchSelectionOptions,
  type CaptureEventQueueRepository,
  type CaptureQueueReadOptions,
  type CaptureQueueStatistics,
  type CaptureQueueWriteResult,
  type IndexedDbCaptureEventQueueOptions,
  type StoredCaptureQueueRecord
} from "./capture-event-queue-storage";
