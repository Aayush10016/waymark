import type { ExplicitRule } from "../project-source/rules";

export type GuardianRulePatternKind =
  | "FORBIDDEN_TERM"
  | "REQUIRED_TECHNOLOGY"
  | "FORBIDDEN_IMPORT"
  | "FORBIDDEN_DEPENDENCY"
  | "REQUIRED_ARCHITECTURAL_COMPONENT";

export interface GuardianRulePattern {
  readonly kind: GuardianRulePatternKind;
  readonly primaryTerm: string;
  readonly aliases: readonly string[];
  readonly requiresPersistenceContext: boolean;
}

export function extractRulePatterns(rule: ExplicitRule): readonly GuardianRulePattern[] {
  const source = `${rule.title}\n${rule.ruleText}`.trim();
  const patterns: GuardianRulePattern[] = [];

  for (const term of extractForbiddenTerms(source)) {
    patterns.push({
      kind: forbiddenPatternKind(source),
      primaryTerm: term,
      aliases: aliasesFor(term),
      requiresPersistenceContext: false
    });
  }

  const requiredTechnology = extractRequiredTechnology(source);
  if (requiredTechnology !== undefined) {
    patterns.push({
      kind: "REQUIRED_TECHNOLOGY",
      primaryTerm: requiredTechnology.term,
      aliases: aliasesFor(requiredTechnology.term),
      requiresPersistenceContext: requiredTechnology.requiresPersistenceContext
    });
  }

  const requiredComponent = extractRequiredArchitecturalComponent(source);
  if (requiredComponent !== undefined) {
    patterns.push({
      kind: "REQUIRED_ARCHITECTURAL_COMPONENT",
      primaryTerm: requiredComponent,
      aliases: [requiredComponent.toLowerCase()],
      requiresPersistenceContext: false
    });
  }

  return uniquePatterns(patterns);
}

export function meaningfulRuleTerms(rule: ExplicitRule): readonly string[] {
  return uniqueStrings(
    `${rule.title} ${rule.ruleText} ${rule.scope} ${rule.type}`
      .match(/[A-Za-z][A-Za-z0-9_.#-]*/g)
      ?.map((term) => term.toLowerCase())
      .filter((term) => term.length > 2 && !COMMON_RULE_WORDS.has(term)) ?? []
  );
}

export function aliasesFor(term: string): readonly string[] {
  const normalized = normalizeTerm(term);
  const aliases = TECHNOLOGY_ALIASES.get(normalized) ?? [normalized];
  return uniqueStrings([normalized, ...aliases]);
}

export function databaseConflictTermsFor(requiredTerm: string): readonly string[] {
  const normalized = normalizeTerm(requiredTerm);
  if (normalized !== "postgresql" && normalized !== "postgres") {
    return [];
  }

  return ["mongodb", "mongo", "mongoclient", "mysql", "sqlite", "dynamodb", "redis"];
}

export function normalizeTerm(term: string): string {
  return term.trim().replace(/^[^A-Za-z0-9]+|[^A-Za-z0-9]+$/g, "").toLowerCase();
}

function extractForbiddenTerms(source: string): readonly string[] {
  const terms: string[] = [];
  const patterns = [
    /\b([A-Za-z][A-Za-z0-9_.#-]*)\s+must\s+not\s+be\s+used\b/gi,
    /\b([A-Za-z][A-Za-z0-9_.#-]*)\s+(?:is|are)\s+not\s+allowed\b/gi,
    /\b(?:do\s+not|don't|must\s+not|never)\s+(?:use|reference|import|depend\s+on|add|introduce)\s+(?:the\s+)?(?:(?:library|dependency|technology|package)\s+)?([A-Za-z][A-Za-z0-9_.#-]*)/gi,
    /\b(?:forbid|forbids|forbidden|prohibit|prohibits|prohibited)\s+(?:the\s+)?(?:use\s+of\s+)?(?:(?:library|dependency|technology|package)\s+)?([A-Za-z][A-Za-z0-9_.#-]*)/gi,
    /\bno\s+([A-Za-z][A-Za-z0-9_.#-]*)\b/gi
  ];

  for (const pattern of patterns) {
    for (const match of source.matchAll(pattern)) {
      const term = match[1];
      if (term !== undefined) {
        const normalized = normalizeTerm(term);
        if (normalized.length > 1 && !COMMON_RULE_WORDS.has(normalized)) {
          terms.push(normalized);
        }
      }
    }
  }

  return uniqueStrings(terms);
}

function extractRequiredTechnology(source: string): { readonly term: string; readonly requiresPersistenceContext: boolean } | undefined {
  const match = /\b(?:use|must\s+use|required|require)\s+(?:the\s+)?([A-Za-z][A-Za-z0-9_.#-]*)\s+for\s+(persistent\s+storage|persistence|database|storage)\b/i.exec(source);
  const term = match?.[1];
  if (term === undefined) {
    return undefined;
  }

  const normalized = normalizeTerm(term);
  if (normalized.length < 2 || COMMON_RULE_WORDS.has(normalized)) {
    return undefined;
  }

  return {
    term: normalized,
    requiresPersistenceContext: true
  };
}

function extractRequiredArchitecturalComponent(source: string): string | undefined {
  const match = /\b[A-Za-z][A-Za-z0-9_-]*\s+must\s+go\s+through\s+([A-Za-z][A-Za-z0-9_]*)\b/i.exec(source);
  const component = match?.[1];
  if (component === undefined || !/[A-Z]/.test(component)) {
    return undefined;
  }

  return component;
}

function forbiddenPatternKind(source: string): GuardianRulePatternKind {
  const normalized = source.toLowerCase();
  if (normalized.includes("import")) {
    return "FORBIDDEN_IMPORT";
  }
  if (normalized.includes("dependency") || normalized.includes("library") || normalized.includes("package")) {
    return "FORBIDDEN_DEPENDENCY";
  }
  return "FORBIDDEN_TERM";
}

function uniquePatterns(patterns: readonly GuardianRulePattern[]): readonly GuardianRulePattern[] {
  const seen = new Set<string>();
  return patterns.filter((pattern) => {
    const key = `${pattern.kind}:${pattern.primaryTerm}`;
    if (seen.has(key)) {
      return false;
    }
    seen.add(key);
    return true;
  });
}

function uniqueStrings(values: readonly string[]): readonly string[] {
  return [...new Set(values.filter((value) => value.length > 0))];
}

const TECHNOLOGY_ALIASES = new Map<string, readonly string[]>([
  ["mongodb", ["mongodb", "mongo", "mongoclient"]],
  ["mongo", ["mongodb", "mongo", "mongoclient"]],
  ["postgresql", ["postgresql", "postgres"]],
  ["postgres", ["postgresql", "postgres"]],
  ["redis", ["redis", "redistemplate", "rediscache", "enableredisrepositories"]],
  ["jwt", ["jwt"]]
]);

const COMMON_RULE_WORDS = new Set([
  "the",
  "and",
  "for",
  "use",
  "uses",
  "using",
  "must",
  "not",
  "never",
  "with",
  "through",
  "should",
  "shall",
  "library",
  "dependency",
  "technology",
  "package",
  "persistent",
  "storage",
  "database",
  "project",
  "rule",
  "active",
  "scope"
]);
