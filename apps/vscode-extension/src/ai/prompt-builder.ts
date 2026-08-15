import type { GuardianCheckResult } from "../guardian/models";
import type { GroundedPrompt, GroundedRagContext } from "./models";

export class GroundedPromptBuilder {
  public build(context: GroundedRagContext, guardianResult: GuardianCheckResult): GroundedPrompt {
    return {
      system: SYSTEM_INSTRUCTIONS,
      user: [
        "User question:",
        context.question,
        "",
        "Waymark evidence:",
        ...context.evidence.map(formatEvidence),
        "",
        "Deterministic Guardian findings:",
        ...formatGuardianFindings(guardianResult),
        "",
        "Instructions:",
        "- Answer the user question concisely.",
        "- Cite evidence IDs in square brackets when making project-specific claims.",
        "- If evidence is insufficient, say: I don't have enough project evidence to determine that.",
        "- Separate general programming knowledge from project-specific evidence."
      ].join("\n"),
      evidence: context.evidence
    };
  }
}

const SYSTEM_INSTRUCTIONS = [
  "You are assisting with software engineering.",
  "Project evidence supplied by Waymark is authoritative for project-specific claims.",
  "Do not invent missing project information.",
  "Distinguish project evidence from general programming knowledge.",
  "If evidence is insufficient, say so.",
  "Never claim that a rule exists unless it is supplied.",
  "Never claim that a decision exists unless it is supplied.",
  "Do not fabricate Git history.",
  "Do not fabricate source files.",
  "Keep answers concise and actionable.",
  "Cite relevant Waymark evidence IDs when possible.",
  "Do not override deterministic Guardian findings."
].join("\n");

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

function formatGuardianFindings(guardianResult: GuardianCheckResult): readonly string[] {
  if (guardianResult.findings.length === 0) {
    return ["(none)"];
  }

  return guardianResult.findings.map((finding) =>
    `- ${finding.title}: ${finding.message} Rule=${finding.ruleTitle} Matched=${finding.matchedTerms.join(", ")}`
  );
}
