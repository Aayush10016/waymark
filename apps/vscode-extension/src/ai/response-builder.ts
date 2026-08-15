import type { AiProviderResult } from "./provider";
import type { GroundedAiResponse, GroundedPrompt } from "./models";

export function buildGroundedAiResponse(providerResult: AiProviderResult, prompt: GroundedPrompt): GroundedAiResponse {
  const references = prompt.evidence
    .filter((evidence) => providerResult.text.includes(evidence.id))
    .map((evidence) => ({
      evidenceId: evidence.id,
      title: evidence.title,
      sourcePath: evidence.sourcePath,
      sourceType: evidence.sourceType
    }));

  return {
    answer: providerResult.text,
    evidenceReferences: references,
    provider: providerResult.provider,
    model: providerResult.model,
    grounded: references.length > 0,
    warnings: references.length === 0
      ? ["The response did not cite supplied Waymark evidence IDs. Treat project-specific claims cautiously."]
      : []
  };
}
