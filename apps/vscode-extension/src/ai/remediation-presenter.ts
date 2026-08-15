import { basename } from "node:path";

import type { RemediationProposal, RemediationResponse } from "./remediation-models";
import type { GuardianFinding } from "../guardian/models";

export function formatRemediationProposal(proposal: RemediationProposal): string {
  return [
    "TRAIL REMEDIATION",
    "",
    "Problem:",
    proposal.finding.message,
    "",
    "Rule:",
    proposal.finding.ruleTitle,
    "",
    "Why:",
    proposal.finding.matchedTerms.length === 0
      ? "Waymark Guardian detected a deterministic project-rule conflict."
      : `This code matched: ${proposal.finding.matchedTerms.join(", ")}`,
    "",
    "Suggested fix:",
    proposal.response.summary,
    "",
    "Rationale:",
    proposal.response.rationale,
    "",
    "Files affected:",
    ...formatAffectedFiles(proposal.response),
    "",
    "Proposed changes:",
    ...proposal.response.changes.flatMap(formatChange),
    "",
    "Evidence:",
    ...formatEvidence(proposal.response),
    "",
    `Confidence: ${proposal.response.confidence}`,
    `Provider: ${proposal.provider}`,
    `Model: ${proposal.model}`,
    ...formatWarnings(proposal.response)
  ].join("\n");
}

export function formatRemediationModalSummary(finding: GuardianFinding, response: RemediationResponse): string {
  return [
    "Waymark Remediation",
    "",
    `Problem: ${finding.message}`,
    `Rule: ${finding.ruleTitle}`,
    "",
    `Suggested fix: ${response.summary}`,
    "",
    `Rationale: ${response.rationale}`,
    "",
    `Files affected: ${formatAffectedFiles(response).join(", ")}`,
    `Confidence: ${response.confidence}`,
    ...formatWarnings(response)
  ].join("\n");
}

function formatAffectedFiles(response: RemediationResponse): readonly string[] {
  if (response.changes.length === 0) {
    return ["(none)"];
  }
  return [...new Set(response.changes.map((change) => basename(change.filePath)))];
}

function formatChange(change: RemediationResponse["changes"][number]): readonly string[] {
  return [
    `- ${basename(change.filePath)}:${change.startLine}-${change.endLine}`,
    `  Reason: ${change.reason}`,
    "  Expected:",
    indent(change.expectedOriginalText),
    "  Replacement:",
    indent(change.replacementText.length === 0 ? "(empty)" : change.replacementText)
  ];
}

function formatEvidence(response: RemediationResponse): readonly string[] {
  if (response.evidenceReferences.length === 0) {
    return ["(none cited)"];
  }
  return response.evidenceReferences.map((reference) => `- ${reference.evidenceId}: ${reference.title}`);
}

function formatWarnings(response: RemediationResponse): readonly string[] {
  if (response.warnings.length === 0) {
    return [];
  }
  return ["", "Warnings:", ...response.warnings.map((warning) => `- ${warning}`)];
}

function indent(value: string): string {
  return value.split(/\r?\n/).map((line) => `    ${line}`).join("\n");
}
