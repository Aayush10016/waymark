export interface DebouncedFileChangeQueueOptions<T> {
  readonly delayMs: number;
  readonly setTimer: (callback: () => void, delayMs: number) => unknown;
  readonly clearTimer: (handle: unknown) => void;
  readonly onFlush: (items: readonly T[]) => void;
  readonly keyFor: (item: T) => string;
}

export class DebouncedFileChangeQueue<T> {
  private readonly options: DebouncedFileChangeQueueOptions<T>;
  private readonly pending = new Map<string, T>();
  private timer: unknown;

  public constructor(options: DebouncedFileChangeQueueOptions<T>) {
    this.options = options;
  }

  public enqueue(item: T): void {
    this.pending.set(this.options.keyFor(item), item);
    this.reschedule();
  }

  public flush(): void {
    if (this.timer !== undefined) {
      this.options.clearTimer(this.timer);
      this.timer = undefined;
    }

    if (this.pending.size === 0) {
      return;
    }

    const items = [...this.pending.values()];
    this.pending.clear();
    this.options.onFlush(items);
  }

  public dispose(): void {
    if (this.timer !== undefined) {
      this.options.clearTimer(this.timer);
      this.timer = undefined;
    }
    this.pending.clear();
  }

  private reschedule(): void {
    if (this.timer !== undefined) {
      this.options.clearTimer(this.timer);
    }

    this.timer = this.options.setTimer(() => {
      this.timer = undefined;
      this.flush();
    }, this.options.delayMs);
  }
}
