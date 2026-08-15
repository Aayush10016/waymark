import type { CaptureEvent, CaptureEventSink } from "../navigation-capture";

export interface CaptureQueueSynchronizer {
  synchronize(): Promise<unknown>;
}

/**
 * Persists a capture event through the delegated sink and triggers best-effort synchronization.
 */
export class SyncingCaptureEventSink implements CaptureEventSink {
  private readonly delegate: CaptureEventSink;
  private readonly synchronizer: CaptureQueueSynchronizer;

  constructor(options: { readonly delegate: CaptureEventSink; readonly synchronizer: CaptureQueueSynchronizer }) {
    this.delegate = options.delegate;
    this.synchronizer = options.synchronizer;
  }

  async emit(event: CaptureEvent): Promise<void> {
    await this.delegate.emit(event);
    void this.synchronizer.synchronize().catch(() => undefined);
  }
}
