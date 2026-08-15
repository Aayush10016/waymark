export interface ArchitectureRuleCandidate {
  readonly title: string;
  readonly statement: string;
}

const RULE_KEYWORD_PATTERN = /\b(MUST NOT|SHALL NOT|MUST|SHALL|REQUIRED|FORBIDDEN)\b/i;

export function extractArchitectureRuleCandidates(content: string): readonly ArchitectureRuleCandidate[] {
  const candidates = content
    .split(/\r?\n/)
    .map(cleanLine)
    .filter((line) => RULE_KEYWORD_PATTERN.test(line))
    .map((statement) => ({
      title: titleForStatement(statement),
      statement
    }));

  return deduplicateCandidates(candidates);
}

function cleanLine(line: string): string {
  return line
    .trim()
    .replace(/^[-*]\s+/, "")
    .replace(/^\d+[.)]\s+/, "")
    .trim();
}

function titleForStatement(statement: string): string {
  const normalized = statement
    .replace(/\b(MUST NOT|SHALL NOT|MUST|SHALL|REQUIRED|FORBIDDEN)\b/gi, "")
    .replace(/[^A-Za-z0-9 ]+/g, " ")
    .replace(/\s+/g, " ")
    .trim();
  const words = normalized.split(" ").filter((word) => word.length > 0).slice(0, 6);
  return words.length === 0 ? "Imported Architecture Rule" : titleCase(words.join(" "));
}

function titleCase(value: string): string {
  return value
    .split(" ")
    .map((word) => `${word.charAt(0).toUpperCase()}${word.slice(1)}`)
    .join(" ");
}

function deduplicateCandidates(candidates: readonly ArchitectureRuleCandidate[]): readonly ArchitectureRuleCandidate[] {
  const seen = new Set<string>();
  const unique: ArchitectureRuleCandidate[] = [];
  for (const candidate of candidates) {
    const key = candidate.statement.toLowerCase();
    if (!seen.has(key)) {
      seen.add(key);
      unique.push(candidate);
    }
  }
  return unique;
}
