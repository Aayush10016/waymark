import type { ProblemSessionRuntimePayload } from "@trail/extension-messages";
import type { CaptureEvent, CaptureEventSink } from "./capture-event";

export interface CaptureProblemSessionStateReader {
  readState(): Promise<{
    readonly state: ProblemSessionRuntimePayload;
  }>;
}

/**
 * Adds capture-time Problem Session ownership metadata before durable queue persistence.
 */
export class ProblemSessionCaptureEventSink implements CaptureEventSink {
  private readonly delegate: CaptureEventSink;
  private readonly problemSessionReader: CaptureProblemSessionStateReader;

  constructor(options: {
    readonly delegate: CaptureEventSink;
    readonly problemSessionReader: CaptureProblemSessionStateReader;
  }) {
    this.delegate = options.delegate;
    this.problemSessionReader = options.problemSessionReader;
  }

  async emit(event: CaptureEvent): Promise<void> {
    const activeSession = (await this.problemSessionReader.readState()).state.activeSession;
    await this.delegate.emit(Object.freeze({
      ...event,
      problemSessionId: activeSession?.status === "CANDIDATE" ? activeSession.id : null
    }));
  }
}
