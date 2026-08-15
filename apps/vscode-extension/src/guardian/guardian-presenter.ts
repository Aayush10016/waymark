import type { GuardianCheckResult, GuardianEvidence, GuardianFinding } from "./models";

export function formatGuardianCheckResult(result: GuardianCheckResult): string {
  if (result.findings.length === 0) {
    return [
      "Waymark Guardian",
      "",
      "No deterministic project-rule conflicts found.",
      "",
      "Checked:",
      `- ${result.checkedRules} active rules`,
      `- ${result.checkedDecisions} relevant decisions`,
      `- ${result.checkedFiles} ${result.checkedFiles === 1 ? "file" : "files"}`,
      "",
      "This is deterministic project evidence only. No broad correctness claim is made."
    ].join("\n");
  }

  return [
    "Waymark Guardian",
    "",
    `${result.findings.length} potential ${result.findings.length === 1 ? "conflict" : "conflicts"} found.`,
    "",
    ...result.findings.flatMap((finding, index) => formatFindingBlock(finding, index + 1))
  ].join("\n");
}

export function formatGuardianFindingDetail(finding: GuardianFinding): string {
  return [
    finding.title,
    "",
    `Severity: ${finding.severity}`,
    `Workspace: ${finding.workspaceFolder.name}`,
    `Source: ${finding.source}`,
    "",
    finding.message,
    "",
    `Rule: ${finding.ruleTitle}`,
    `Rule source: ${finding.rulePath}`,
    "",
    `Matched terms: ${finding.matchedTerms.join(", ")}`,
    "",
    "Why Waymark showed this:",
    ...finding.evidence.map(formatEvidenceLine)
  ].join("\n");
}

function formatFindingBlock(finding: GuardianFinding, number: number): readonly string[] {
  return [
    `${number}. ${finding.title}`,
    `Rule: ${finding.ruleTitle}`,
    `Message: ${finding.message}`,
    `Matched: ${finding.matchedTerms.join(", ")}`,
    `Source: ${finding.rulePath}`,
    ""
  ];
}

function formatEvidenceLine(evidence: GuardianEvidence): string {
  return `- ${evidence.kind}: ${evidence.title} — ${evidence.detail}`;
}
