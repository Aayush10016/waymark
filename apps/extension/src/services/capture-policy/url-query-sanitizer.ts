const TITLE_MAX_LENGTH = 120;
const SEARCH_QUERY_MAX_LENGTH = 240;

const TRACKING_QUERY_PREFIXES = ["utm_"] as const;
const TRACKING_QUERY_PARAMETERS = new Set(["fbclid", "gclid", "msclkid"]);
const SENSITIVE_QUERY_PARAMETERS = new Set([
  "access_token",
  "api_key",
  "auth",
  "authorization",
  "code",
  "id_token",
  "key",
  "password",
  "refresh_token",
  "secret",
  "state",
  "token"
]);
const NON_ALLOWLISTED_SEARCH_QUERY_PARAMETERS = new Set(["q", "query", "search"]);

export type CapturePolicyRejectionReason = "credentials_present" | "private_or_local_host" | "unsupported_scheme";

export interface SearchAllowlistEntry {
  readonly engine: string;
  readonly hosts: readonly string[];
  readonly pathPrefixes: readonly string[];
  readonly queryParameters: readonly string[];
}

export interface CaptureSanitationPolicy {
  readonly policyVersion: string;
  readonly searchAllowlist: readonly SearchAllowlistEntry[];
}

export interface RawCaptureCandidate {
  readonly url: string;
  readonly title?: string | null;
}

export interface AllowedCapturePolicyDecision {
  readonly decision: "allow";
  readonly canonicalUrl: string;
  readonly hostname: string;
  readonly policyVersion: string;
  readonly removedQueryParameters: readonly string[];
  readonly searchEngine: string | null;
  readonly searchQuery: string | null;
  readonly title: string | null;
}

export interface RejectedCapturePolicyDecision {
  readonly decision: "reject";
  readonly policyVersion: string;
  readonly rejectionReason: CapturePolicyRejectionReason;
}

export type CapturePolicyDecision = AllowedCapturePolicyDecision | RejectedCapturePolicyDecision;

interface RetainedQueryParameter {
  readonly name: string;
  readonly value: string;
}

interface SearchMatch {
  readonly engine: string;
  readonly queryParameters: readonly string[];
}

/**
 * Applies Trail's local URL, query, host, and title sanitation policy before any capture event can be persisted.
 */
export class UrlQuerySanitizer {
  private readonly policy: CaptureSanitationPolicy;

  constructor(policy: CaptureSanitationPolicy) {
    this.policy = policy;
  }

  sanitize(input: RawCaptureCandidate): CapturePolicyDecision {
    const parsedUrl = this.parseUrl(input.url);
    if (parsedUrl === null || !this.hasSupportedScheme(parsedUrl)) {
      return this.reject("unsupported_scheme");
    }

    if (parsedUrl.username.length > 0 || parsedUrl.password.length > 0) {
      return this.reject("credentials_present");
    }

    const hostname = parsedUrl.hostname.toLowerCase();
    if (this.isPrivateOrLocalHost(hostname)) {
      return this.reject("private_or_local_host");
    }

    const searchMatch = this.findSearchMatch(parsedUrl);
    const querySanitation = this.sanitizeQuery(parsedUrl, searchMatch);

    return {
      decision: "allow",
      canonicalUrl: this.buildCanonicalUrl(parsedUrl, querySanitation.retainedParameters),
      hostname,
      policyVersion: this.policy.policyVersion,
      removedQueryParameters: querySanitation.removedParameters,
      searchEngine: searchMatch?.engine ?? null,
      searchQuery: querySanitation.searchQuery,
      title: sanitizeBoundedText(input.title ?? null, TITLE_MAX_LENGTH)
    };
  }

  private parseUrl(rawUrl: string): URL | null {
    try {
      return new URL(rawUrl);
    } catch {
      return null;
    }
  }

  private hasSupportedScheme(parsedUrl: URL): boolean {
    return parsedUrl.protocol === "http:" || parsedUrl.protocol === "https:";
  }

  private reject(rejectionReason: CapturePolicyRejectionReason): RejectedCapturePolicyDecision {
    return {
      decision: "reject",
      policyVersion: this.policy.policyVersion,
      rejectionReason
    };
  }

  private isPrivateOrLocalHost(hostname: string): boolean {
    const normalizedHostname = hostname.replace(/^\[/, "").replace(/\]$/, "").toLowerCase();

    if (
      normalizedHostname === "localhost" ||
      normalizedHostname.endsWith(".localhost") ||
      normalizedHostname === "0.0.0.0" ||
      normalizedHostname === "::" ||
      normalizedHostname === "::1"
    ) {
      return true;
    }

    if (isIpv4Address(normalizedHostname)) {
      return isPrivateIpv4Address(normalizedHostname);
    }

    if (normalizedHostname.includes(":")) {
      return isPrivateIpv6Address(normalizedHostname);
    }

    return false;
  }

  private findSearchMatch(parsedUrl: URL): SearchMatch | null {
    const hostname = parsedUrl.hostname.toLowerCase();
    const pathname = parsedUrl.pathname;

    for (const entry of this.policy.searchAllowlist) {
      const hostMatches = entry.hosts.some((host) => host.toLowerCase() === hostname);
      const pathMatches = entry.pathPrefixes.some((pathPrefix) => pathname.startsWith(pathPrefix));

      if (hostMatches && pathMatches) {
        return {
          engine: entry.engine,
          queryParameters: entry.queryParameters
        };
      }
    }

    return null;
  }

  private sanitizeQuery(
    parsedUrl: URL,
    searchMatch: SearchMatch | null
  ): {
    readonly removedParameters: readonly string[];
    readonly retainedParameters: readonly RetainedQueryParameter[];
    readonly searchQuery: string | null;
  } {
    const removedParameters: string[] = [];
    const retainedParameters: RetainedQueryParameter[] = [];
    let searchQuery: string | null = null;

    for (const [name, value] of parsedUrl.searchParams.entries()) {
      const normalizedName = name.toLowerCase();
      const isAllowlistedSearchParameter = searchMatch?.queryParameters.includes(name) ?? false;

      if (this.shouldRemoveQueryParameter(normalizedName, searchMatch, isAllowlistedSearchParameter)) {
        removedParameters.push(name);
        continue;
      }

      retainedParameters.push({ name, value });

      if (searchQuery === null && searchMatch !== null && isAllowlistedSearchParameter) {
        searchQuery = sanitizeBoundedText(value, SEARCH_QUERY_MAX_LENGTH);
      }
    }

    return {
      removedParameters,
      retainedParameters,
      searchQuery
    };
  }

  private shouldRemoveQueryParameter(
    normalizedName: string,
    searchMatch: SearchMatch | null,
    isAllowlistedSearchParameter: boolean
  ): boolean {
    if (TRACKING_QUERY_PREFIXES.some((prefix) => normalizedName.startsWith(prefix))) {
      return true;
    }

    if (TRACKING_QUERY_PARAMETERS.has(normalizedName) || SENSITIVE_QUERY_PARAMETERS.has(normalizedName)) {
      return true;
    }

    if (searchMatch !== null) {
      return !isAllowlistedSearchParameter;
    }

    return NON_ALLOWLISTED_SEARCH_QUERY_PARAMETERS.has(normalizedName);
  }

  private buildCanonicalUrl(parsedUrl: URL, retainedParameters: readonly RetainedQueryParameter[]): string {
    const query = retainedParameters
      .map((parameter) => `${encodeURIComponent(parameter.name)}=${encodeURIComponent(parameter.value)}`)
      .join("&");

    return `${parsedUrl.protocol}//${parsedUrl.host}${parsedUrl.pathname}${query.length > 0 ? `?${query}` : ""}`;
  }
}

export function createUrlQuerySanitizer(policy: CaptureSanitationPolicy): UrlQuerySanitizer {
  return new UrlQuerySanitizer(policy);
}

function sanitizeBoundedText(value: string | null, maxLength: number): string | null {
  if (value === null) {
    return null;
  }

  const normalizedValue = value.replace(/\s+/g, " ").trim();
  return normalizedValue.length <= maxLength ? normalizedValue : normalizedValue.slice(0, maxLength);
}

function isIpv4Address(value: string): boolean {
  const octets = value.split(".");
  return (
    octets.length === 4 &&
    octets.every((octet) => {
      if (!/^\d{1,3}$/.test(octet)) {
        return false;
      }

      const numericOctet = Number(octet);
      return numericOctet >= 0 && numericOctet <= 255;
    })
  );
}

function isPrivateIpv4Address(value: string): boolean {
  const octets = value.split(".").map(Number);
  const firstOctet = octets[0];
  const secondOctet = octets[1];

  if (firstOctet === undefined || secondOctet === undefined) {
    return false;
  }

  return (
    firstOctet === 0 ||
    firstOctet === 10 ||
    firstOctet === 127 ||
    (firstOctet === 100 && secondOctet >= 64 && secondOctet <= 127) ||
    (firstOctet === 169 && secondOctet === 254) ||
    (firstOctet === 172 && secondOctet >= 16 && secondOctet <= 31) ||
    (firstOctet === 192 && secondOctet === 168)
  );
}

function isPrivateIpv6Address(value: string): boolean {
  return (
    value === "::" ||
    value === "::1" ||
    value.startsWith("fc") ||
    value.startsWith("fd") ||
    value.startsWith("fe8") ||
    value.startsWith("fe9") ||
    value.startsWith("fea") ||
    value.startsWith("feb")
  );
}
