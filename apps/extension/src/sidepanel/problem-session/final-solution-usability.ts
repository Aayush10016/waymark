export const FINAL_SOLUTION_USABILITY_TARGET_SECONDS = 30;

export interface FinalSolutionUsabilityEstimate {
  readonly outcomeChoiceSeconds: number;
  readonly saveChoiceSeconds: number;
  readonly textEntrySeconds: number;
}

export function estimateFinalSolutionCompletionSeconds(estimate: FinalSolutionUsabilityEstimate): number {
  return estimate.outcomeChoiceSeconds + estimate.textEntrySeconds + estimate.saveChoiceSeconds;
}

export function meetsFinalSolutionUsabilityTarget(estimate: FinalSolutionUsabilityEstimate): boolean {
  return estimateFinalSolutionCompletionSeconds(estimate) < FINAL_SOLUTION_USABILITY_TARGET_SECONDS;
}
