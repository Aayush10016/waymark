import type { GroundedRagContext } from "./models";
import type { RemediationPrompt } from "./remediation-models";
import type { CodeContextPackage } from "../code-context/models";
import type { GuardianFinding } from "../guardian/models";

export class RemediationPromptBuilder {
  public build(
    contextPackage: CodeContextPackage,
    ragContext: GroundedRagContext,
    finding: GuardianFinding
  ): RemediationPrompt {
    return {
      system: SYSTEM_INSTRUCTIONS,
      user: [
        "Task: propose a safe, targeted remediation for one deterministic Waymark Guardian finding.",
        "",
        "Guardian finding:",
        formatFinding(finding),
        "",
        "Current code context:",
        formatCodeContext(contextPackage),
        "",
        "Waymark evidence:",
        ...ragContext.evidence.map(formatEvidence),
        "",
        "Response format:",
        JSON_SCHEMA_INSTRUCTIONS,
        "",
        "Remediation rules:",
        "- Return only valid JSON. Do not wrap it in markdown.",
        "- Include exact expectedOriginalText for every proposed edit.",
        "- Use 1-based inclusive startLine and endLine values.",
        "- Only propose changes necessary to resolve this Guardian finding.",
        "- If safe remediation is not possible from supplied evidence, return confidence \"low\", no changes, and a warning explaining why.",
        "- Explain why the proposed fix satisfies the violated rule."
      ].join("\n"),
      evidence: ragContext.evidence,
      finding
    };
  }
}

const SYSTEM_INSTRUCTIONS = [
  "You are Waymark's remediation assistant for AI-assisted software development governance.",
  "Project rules supplied by Waymark are authoritative.",
  "Do not invent project rules.",
  "Do not invent architecture.",
  "Do not assume unavailable dependencies.",
  "Do not modify unrelated behavior.",
  "Preserve existing behavior where possible.",
  "Only propose changes necessary to resolve the supplied deterministic Guardian finding.",
  "Do not remove working functionality merely to satisfy the prompt.",
  "If there is insufficient evidence to safely propose a fix, say so in the JSON warnings.",
  "Explain why the proposed fix satisfies the specific rule.",
  "The response is a proposal only. Waymark will not automatically trust or apply it."
].join("\n");

const JSON_SCHEMA_INSTRUCTIONS = [
  "{",
  "  \"summary\": \"short user-facing summary\",",
  "  \"rationale\": \"why this resolves the Guardian finding\",",
  "  \"confidence\": \"low | medium | high\",",
  "  \"evidenceReferences\": [\"CODE-SELECTED\", \"RULE-001\"],",
  "  \"changes\": [",
  "    {",
  "      \"filePath\": \"absolute file path from the supplied code context\",",
  "      \"startLine\": 1,",
  "      \"endLine\": 1,",
  "      \"expectedOriginalText\": \"exact current text in that line range\",",
  "      \"replacementText\": \"replacement text for only that range\",",
  "      \"reason\": \"why this edit is necessary\"",
  "    }",
  "  ],",
  "  \"warnings\": []",
  "}"
].join("\n");

function formatFinding(finding: GuardianFinding): string {
  return [
    `Title: ${finding.title}`,
    `Message: ${finding.message}`,
    `Rule ID: ${finding.ruleId}`,
    `Rule: ${finding.ruleTitle}`,
    `Rule path: ${finding.rulePath}`,
    `Code path: ${finding.codePath ?? "(none)"}`,
    `Matched terms: ${finding.matchedTerms.join(", ")}`,
    "Guardian evidence:",
    ...(finding.evidence.length === 0
      ? ["(none)"]
      : finding.evidence.map((evidence) => `- ${evidence.kind}: ${evidence.title} — ${evidence.detail}`))
  ].join("\n");
}

function formatCodeContext(contextPackage: CodeContextPackage): string {
  const code = contextPackage.codeContext;
  return [
    `File: ${code.filePath}`,
    `Relative path: ${code.relativePath}`,
    `Language: ${code.language}`,
    `Symbol: ${code.symbolName ?? "(selection)"}`,
    `Lines: ${code.startLine}-${code.endLine}`,
    "",
    "Selected/current code:",
    code.selectedText,
    "",
    "Surrounding code:",
    code.surroundingCode,
    "",
    "Imports:",
    ...(code.relatedImports.length === 0 ? ["(none)"] : code.relatedImports),
    "",
    "Git evidence:",
    contextPackage.gitEvidence.fileStatus === undefined
      ? `File status: ${contextPackage.gitEvidence.unavailableReason ?? "(none)"}`
      : `File status: ${contextPackage.gitEvidence.fileStatus.path}`,
    ...contextPackage.gitEvidence.relevantCommits.map((commit) => `Commit ${commit.shortSha}: ${commit.subject}`)
  ].join("\n");
}

function formatEvidence(evidence: GroundedRagContext["evidence"][number]): string {
  return [
    `<evidence id="${evidence.id}" type="${evidence.sourceType}">`,
    `Title: ${evidence.title}`,
    `Path: ${evidence.sourcePath ?? "(none)"}`,
    "Content:",
    evidence.content,
    "</evidence>"
  ].join("\n");
}
