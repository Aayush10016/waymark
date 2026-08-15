import {
  createDefaultStoredCaptureControls,
  createStoredCaptureControls,
  type CaptureControlStorage,
  type StoredCaptureControls
} from "../../storage/capture-controls";

export type CaptureControlBlockedReason = "domain_excluded" | "paused";

export type CaptureControlDecision =
  | { readonly status: "allowed" }
  | { readonly status: "blocked"; readonly reason: CaptureControlBlockedReason; readonly domain?: string };

export type CaptureControlListener = (state: StoredCaptureControls) => void;

export interface CaptureControlReader {
  evaluateUrl(url: string): Promise<CaptureControlDecision>;
  readState(): Promise<StoredCaptureControls>;
}

export interface CaptureControlRuntimeServiceOptions {
  readonly now?: () => Date;
  readonly storage: CaptureControlStorage;
}

/**
 * Owns local capture pause/resume and domain-exclusion state for the extension.
 */
export class CaptureControlRuntimeService implements CaptureControlReader {
  private readonly listeners = new Set<CaptureControlListener>();
  private readonly now: () => Date;
  private readonly storage: CaptureControlStorage;
  private currentState: StoredCaptureControls;

  constructor(options: CaptureControlRuntimeServiceOptions) {
    this.storage = options.storage;
    this.now = options.now ?? (() => new Date());
    this.currentState = createDefaultStoredCaptureControls(this.now());
  }

  current(): StoredCaptureControls {
    return this.currentState;
  }

  async readState(): Promise<StoredCaptureControls> {
    const nextState = await this.loadState();
    this.setCurrentState(nextState);
    return nextState;
  }

  async pause(): Promise<StoredCaptureControls> {
    const state = await this.loadState();
    return this.persist({ ...state, paused: true, updatedAt: this.now().toISOString() });
  }

  async resume(): Promise<StoredCaptureControls> {
    const state = await this.loadState();
    return this.persist({ ...state, paused: false, updatedAt: this.now().toISOString() });
  }

  async excludeDomain(domainOrUrl: string): Promise<StoredCaptureControls> {
    const domain = normalizeDomainInput(domainOrUrl);
    const state = await this.loadState();
    const existingExclusion = state.excludedDomains.find((exclusion) => exclusion.domain === domain);
    const excludedDomains =
      existingExclusion === undefined
        ? [...state.excludedDomains, { domain, createdAt: this.now().toISOString() }]
        : state.excludedDomains;

    return this.persist({ ...state, excludedDomains, updatedAt: this.now().toISOString() });
  }

  async excludeCurrentDomain(url: string): Promise<StoredCaptureControls> {
    return this.excludeDomain(extractHttpHostname(url));
  }

  async removeDomainExclusion(domainOrUrl: string): Promise<StoredCaptureControls> {
    const domain = normalizeDomainInput(domainOrUrl);
    const state = await this.loadState();
    return this.persist({
      ...state,
      excludedDomains: state.excludedDomains.filter((exclusion) => exclusion.domain !== domain),
      updatedAt: this.now().toISOString()
    });
  }

  async undoLastDomainExclusion(): Promise<StoredCaptureControls> {
    const state = await this.loadState();
    if (state.excludedDomains.length === 0) {
      return state;
    }

    return this.persist({
      ...state,
      excludedDomains: state.excludedDomains.slice(0, -1),
      updatedAt: this.now().toISOString()
    });
  }

  async evaluateUrl(url: string): Promise<CaptureControlDecision> {
    const state = await this.readState();
    if (state.paused) {
      return { status: "blocked", reason: "paused" };
    }

    const hostname = parseHttpHostname(url);
    if (hostname === null) {
      return { status: "allowed" };
    }

    const excludedDomain = state.excludedDomains.find((exclusion) => hostnameMatchesDomain(hostname, exclusion.domain));
    if (excludedDomain !== undefined) {
      return { status: "blocked", reason: "domain_excluded", domain: excludedDomain.domain };
    }

    return { status: "allowed" };
  }

  subscribe(listener: CaptureControlListener): () => void {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  }

  private async persist(state: StoredCaptureControls): Promise<StoredCaptureControls> {
    const nextState = createStoredCaptureControls(state.paused, state.excludedDomains, new Date(state.updatedAt));
    await this.storage.write(nextState);
    this.setCurrentState(nextState);
    return nextState;
  }

  private async loadState(): Promise<StoredCaptureControls> {
    return (await this.storage.read()) ?? createDefaultStoredCaptureControls(this.now());
  }

  private setCurrentState(state: StoredCaptureControls): void {
    if (captureControlStatesEqual(this.currentState, state)) {
      this.currentState = state;
      return;
    }

    this.currentState = state;
    for (const listener of this.listeners) {
      listener(state);
    }
  }
}

export function isCaptureAllowedByControls(decision: CaptureControlDecision): boolean {
  return decision.status === "allowed";
}

function normalizeDomainInput(domainOrUrl: string): string {
  const input = domainOrUrl.trim().toLowerCase();
  if (input.length === 0) {
    throw new Error("Domain exclusion cannot be empty.");
  }

  const hostname = input.includes("://") ? extractHttpHostname(input) : extractHttpHostname(`https://${input}`);
  return normalizeHostname(hostname);
}

function extractHttpHostname(url: string): string {
  const hostname = parseHttpHostname(url);
  if (hostname === null) {
    throw new Error("Domain exclusion requires an HTTP or HTTPS URL or hostname.");
  }

  return hostname;
}

function parseHttpHostname(url: string): string | null {
  try {
    const parsedUrl = new URL(url);
    if (parsedUrl.protocol !== "http:" && parsedUrl.protocol !== "https:") {
      return null;
    }

    return normalizeHostname(parsedUrl.hostname);
  } catch {
    return null;
  }
}

function normalizeHostname(hostname: string): string {
  const normalizedHostname = hostname.toLowerCase().replace(/\.$/, "");
  if (normalizedHostname.length === 0 || normalizedHostname.includes(" ")) {
    throw new Error("Domain exclusion requires a valid hostname.");
  }

  return normalizedHostname;
}

function hostnameMatchesDomain(hostname: string, domain: string): boolean {
  return hostname === domain || hostname.endsWith(`.${domain}`);
}

function captureControlStatesEqual(left: StoredCaptureControls, right: StoredCaptureControls): boolean {
  if (
    left.paused !== right.paused ||
    left.updatedAt !== right.updatedAt ||
    left.excludedDomains.length !== right.excludedDomains.length
  ) {
    return false;
  }

  return left.excludedDomains.every((leftExclusion, index) => {
    const rightExclusion = right.excludedDomains[index];
    return (
      rightExclusion !== undefined &&
      leftExclusion.domain === rightExclusion.domain &&
      leftExclusion.createdAt === rightExclusion.createdAt
    );
  });
}
