export const DEFAULT_REPLAY_CONFIRMATION_THRESHOLD = 2;
export const DEFAULT_REPLAY_HARD_CAP = 5;

export interface ReplayResource {
  readonly id: string;
  readonly sanitizedUrl: string;
}

export interface ReplayResourceOpener {
  open(url: string): Promise<void>;
}

export type ResourceReplayFailureCode =
  | "CONFIRMATION_REQUIRED"
  | "COUNT_EXCEEDS_LIMIT"
  | "EMPTY_SELECTION"
  | "PRIVATE_HOST"
  | "UNSAFE_URL";

export type ResourceReplayResult =
  | {
      readonly openedCount: number;
      readonly openedUrls: readonly string[];
      readonly status: "opened";
    }
  | {
      readonly code: ResourceReplayFailureCode;
      readonly resourceId?: string;
      readonly status: "rejected";
    };

export interface ResourceReplayOptions {
  readonly confirmationThreshold?: number;
  readonly confirmed: boolean;
  readonly hardCap?: number;
}

/**
 * Validates and opens sanitized HTTP(S) Problem Session resources in historical order.
 */
export class ResourceReplayService {
  constructor(private readonly opener: ReplayResourceOpener) {}

  async replay(resources: readonly ReplayResource[], options: ResourceReplayOptions): Promise<ResourceReplayResult> {
    const hardCap = options.hardCap ?? DEFAULT_REPLAY_HARD_CAP;
    const confirmationThreshold = options.confirmationThreshold ?? DEFAULT_REPLAY_CONFIRMATION_THRESHOLD;
    if (resources.length === 0) {
      return { code: "EMPTY_SELECTION", status: "rejected" };
    }
    if (resources.length > hardCap) {
      return { code: "COUNT_EXCEEDS_LIMIT", status: "rejected" };
    }
    if (!options.confirmed && resources.length >= confirmationThreshold) {
      return { code: "CONFIRMATION_REQUIRED", status: "rejected" };
    }

    const validated = resources.map((resource) => validateResource(resource));
    const rejected = validated.find((candidate): candidate is RejectedReplayResource => candidate.status === "rejected");
    if (rejected !== undefined) {
      return {
        code: rejected.code,
        resourceId: rejected.resourceId,
        status: "rejected"
      };
    }

    const accepted = validated.filter((candidate): candidate is ValidReplayResource => candidate.status === "accepted");
    const openedUrls: string[] = [];
    for (const resource of accepted) {
      await this.opener.open(resource.url);
      openedUrls.push(resource.url);
    }

    return Object.freeze({
      openedCount: openedUrls.length,
      openedUrls: Object.freeze(openedUrls),
      status: "opened"
    });
  }
}

/**
 * Browser tabs adapter for explicit resource replay.
 */
export class BrowserTabResourceOpener implements ReplayResourceOpener {
  constructor(private readonly chromeApi: Pick<typeof chrome, "tabs">) {}

  async open(url: string): Promise<void> {
    await this.chromeApi.tabs.create({ active: false, url });
  }
}

type ValidReplayResource = {
  readonly status: "accepted";
  readonly url: string;
};

type RejectedReplayResource = {
  readonly code: Exclude<ResourceReplayFailureCode, "CONFIRMATION_REQUIRED" | "COUNT_EXCEEDS_LIMIT" | "EMPTY_SELECTION">;
  readonly resourceId: string;
  readonly status: "rejected";
};

function validateResource(resource: ReplayResource): ValidReplayResource | RejectedReplayResource {
  let parsed: URL;
  try {
    parsed = new URL(resource.sanitizedUrl);
  } catch {
    return { code: "UNSAFE_URL", resourceId: resource.id, status: "rejected" };
  }

  if (parsed.protocol !== "http:" && parsed.protocol !== "https:") {
    return { code: "UNSAFE_URL", resourceId: resource.id, status: "rejected" };
  }
  if (parsed.username !== "" || parsed.password !== "" || parsed.hash !== "") {
    return { code: "UNSAFE_URL", resourceId: resource.id, status: "rejected" };
  }
  if (isPrivateHostname(parsed.hostname)) {
    return { code: "PRIVATE_HOST", resourceId: resource.id, status: "rejected" };
  }

  return { status: "accepted", url: parsed.toString() };
}

function isPrivateHostname(hostname: string): boolean {
  const normalized = hostname.toLowerCase();
  if (
    normalized === "localhost" ||
    normalized.endsWith(".localhost") ||
    normalized.endsWith(".local") ||
    normalized.endsWith(".internal")
  ) {
    return true;
  }

  return isPrivateIpv4(normalized) || isPrivateIpv6(normalized);
}

function isPrivateIpv4(hostname: string): boolean {
  const parts = hostname.split(".");
  if (parts.length !== 4) {
    return false;
  }
  const octets = parts.map((part) => Number(part));
  if (octets.some((octet) => !Number.isInteger(octet) || octet < 0 || octet > 255)) {
    return false;
  }

  const first = octets[0] ?? -1;
  const second = octets[1] ?? -1;
  return (
    first === 10 ||
    first === 127 ||
    (first === 169 && second === 254) ||
    (first === 172 && second >= 16 && second <= 31) ||
    (first === 192 && second === 168)
  );
}

function isPrivateIpv6(hostname: string): boolean {
  const normalized = hostname.replace(/^\[/u, "").replace(/\]$/u, "");
  return normalized === "::1" || normalized.startsWith("fc") || normalized.startsWith("fd") || normalized.startsWith("fe80");
}
