import type { ReviewableSessionArtifact } from "./FinalSolutionEntryStep";

export interface ContextReviewState {
  readonly removedArtifacts: readonly ReviewableSessionArtifact[];
  readonly visibleArtifacts: readonly ReviewableSessionArtifact[];
}

export function createContextReviewState(artifacts: readonly ReviewableSessionArtifact[]): ContextReviewState {
  return Object.freeze({
    removedArtifacts: Object.freeze([]),
    visibleArtifacts: Object.freeze([...artifacts].sort(compareArtifacts))
  });
}

export function removeContextArtifact(state: ContextReviewState, artifactId: string): ContextReviewState {
  const artifact = state.visibleArtifacts.find((candidate) => candidate.id === artifactId);
  if (artifact === undefined) {
    return state;
  }

  return Object.freeze({
    removedArtifacts: Object.freeze([...state.removedArtifacts, artifact]),
    visibleArtifacts: Object.freeze(state.visibleArtifacts.filter((candidate) => candidate.id !== artifactId))
  });
}

export function undoRemoveContextArtifact(state: ContextReviewState, artifactId: string): ContextReviewState {
  const artifact = state.removedArtifacts.find((candidate) => candidate.id === artifactId);
  if (artifact === undefined) {
    return state;
  }

  return Object.freeze({
    removedArtifacts: Object.freeze(state.removedArtifacts.filter((candidate) => candidate.id !== artifactId)),
    visibleArtifacts: Object.freeze([...state.visibleArtifacts, artifact].sort(compareArtifacts))
  });
}

function compareArtifacts(left: ReviewableSessionArtifact, right: ReviewableSessionArtifact): number {
  const byTime = Date.parse(left.occurredAt) - Date.parse(right.occurredAt);
  if (byTime !== 0) {
    return byTime;
  }

  return left.id.localeCompare(right.id);
}
