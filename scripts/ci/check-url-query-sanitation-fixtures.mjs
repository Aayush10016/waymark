import { readFileSync } from "node:fs";

const corpusPath = "contracts/fixtures/url-query-sanitation/v1/corpus.json";
const corpus = JSON.parse(readFileSync(corpusPath, "utf8"));

const requiredCategories = new Set([
  "schemes",
  "credentials",
  "private-hosts",
  "idn",
  "ip",
  "trackers",
  "secrets",
  "search-allowlist"
]);
const allowedDecisions = new Set(["allow", "reject"]);
const allowedSchemes = new Set(["http:", "https:"]);
const disallowedExpectedParameters = new Set([
  "access_token",
  "api_key",
  "auth",
  "authorization",
  "code",
  "fbclid",
  "gclid",
  "password",
  "state",
  "token"
]);
const findings = [];

if (corpus.corpusVersion !== 1) {
  findings.push("corpusVersion must be 1");
}

if (corpus.policyVersion !== "url-query-sanitation-v1") {
  findings.push("policyVersion must be url-query-sanitation-v1");
}

if (!Array.isArray(corpus.searchAllowlist) || corpus.searchAllowlist.length === 0) {
  findings.push("searchAllowlist must be a non-empty array");
}

if (!Array.isArray(corpus.cases) || corpus.cases.length === 0) {
  findings.push("cases must be a non-empty array");
}

const ids = new Set();
const coveredCategories = new Set();

for (const testCase of corpus.cases ?? []) {
  validateCase(testCase);
}

for (const category of requiredCategories) {
  if (!coveredCategories.has(category)) {
    findings.push(`missing required category coverage: ${category}`);
  }
}

if (findings.length > 0) {
  console.error("URL/query sanitation fixture check failed:");
  for (const finding of findings) {
    console.error(`- ${finding}`);
  }
  process.exit(1);
}

console.log("URL/query sanitation fixtures passed.");

function validateCase(testCase) {
  if (!testCase || typeof testCase !== "object") {
    findings.push("case must be an object");
    return;
  }

  if (!isNonEmptyString(testCase.id)) {
    findings.push("case id must be a non-empty string");
  } else if (ids.has(testCase.id)) {
    findings.push(`duplicate case id: ${testCase.id}`);
  } else {
    ids.add(testCase.id);
  }

  if (!Array.isArray(testCase.categories) || testCase.categories.length === 0) {
    findings.push(`${caseLabel(testCase)} categories must be a non-empty array`);
  } else {
    for (const category of testCase.categories) {
      if (!isNonEmptyString(category)) {
        findings.push(`${caseLabel(testCase)} category must be a non-empty string`);
        continue;
      }
      coveredCategories.add(category);
    }
  }

  if (!testCase.input || !isNonEmptyString(testCase.input.url)) {
    findings.push(`${caseLabel(testCase)} input.url must be a non-empty string`);
  }

  if (!testCase.expected || !allowedDecisions.has(testCase.expected.decision)) {
    findings.push(`${caseLabel(testCase)} expected.decision must be allow or reject`);
    return;
  }

  if (testCase.expected.policyVersion !== corpus.policyVersion) {
    findings.push(`${caseLabel(testCase)} expected.policyVersion must match corpus policyVersion`);
  }

  if (testCase.expected.decision === "reject") {
    validateRejectedCase(testCase);
    return;
  }

  validateAllowedCase(testCase);
}

function validateRejectedCase(testCase) {
  if (!isNonEmptyString(testCase.expected.rejectionReason)) {
    findings.push(`${caseLabel(testCase)} rejected case must include rejectionReason`);
  }

  if ("canonicalUrl" in testCase.expected || "hostname" in testCase.expected) {
    findings.push(`${caseLabel(testCase)} rejected case must not include canonicalUrl or hostname`);
  }
}

function validateAllowedCase(testCase) {
  if (!isNonEmptyString(testCase.expected.canonicalUrl)) {
    findings.push(`${caseLabel(testCase)} allowed case must include canonicalUrl`);
    return;
  }

  let parsedUrl;
  try {
    parsedUrl = new URL(testCase.expected.canonicalUrl);
  } catch {
    findings.push(`${caseLabel(testCase)} canonicalUrl must be a valid URL`);
    return;
  }

  if (!allowedSchemes.has(parsedUrl.protocol)) {
    findings.push(`${caseLabel(testCase)} canonicalUrl must use http or https`);
  }

  if (parsedUrl.username || parsedUrl.password) {
    findings.push(`${caseLabel(testCase)} canonicalUrl must not contain credentials`);
  }

  if (parsedUrl.hash) {
    findings.push(`${caseLabel(testCase)} canonicalUrl must not contain a fragment`);
  }

  if (testCase.expected.hostname !== parsedUrl.hostname) {
    findings.push(`${caseLabel(testCase)} hostname must match canonicalUrl hostname`);
  }

  for (const parameter of parsedUrl.searchParams.keys()) {
    if (isTrackerParameter(parameter) || disallowedExpectedParameters.has(parameter.toLowerCase())) {
      findings.push(`${caseLabel(testCase)} canonicalUrl contains unsafe query parameter: ${parameter}`);
    }
  }

  if (!Array.isArray(testCase.expected.removedQueryParameters)) {
    findings.push(`${caseLabel(testCase)} removedQueryParameters must be an array`);
  }
}

function isTrackerParameter(parameter) {
  const normalized = parameter.toLowerCase();
  return normalized.startsWith("utm_") || normalized === "fbclid" || normalized === "gclid";
}

function isNonEmptyString(value) {
  return typeof value === "string" && value.trim().length > 0;
}

function caseLabel(testCase) {
  return testCase?.id ?? "<unknown case>";
}
