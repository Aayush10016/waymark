import type { GhostReplayResourcePayload } from "@trail/extension-messages";

export interface GhostReplayProgressState {
  readonly activeResourceId: string | null;
  readonly resources: readonly GhostReplayResourcePayload[];
}

export function initialGhostReplayProgress(resources: readonly GhostReplayResourcePayload[]): GhostReplayProgressState {
  const copiedResources = resources.map((resource) => Object.freeze({ ...resource }));
  return Object.freeze({
    activeResourceId: firstActionableResourceId(copiedResources),
    resources: Object.freeze(copiedResources)
  });
}

export function markGhostReplayResourceOpened(
  state: GhostReplayProgressState,
  resourceId: string
): GhostReplayProgressState {
  const resources = state.resources.map((resource) =>
    resource.id === resourceId ? Object.freeze({ ...resource, openedNow: true }) : resource
  );
  return Object.freeze({
    activeResourceId: nextActionableResourceId(resources, resourceId),
    resources: Object.freeze(resources)
  });
}

export function selectNextGhostReplayResource(state: GhostReplayProgressState): GhostReplayProgressState {
  if (state.activeResourceId === null) {
    return state;
  }

  return Object.freeze({
    ...state,
    activeResourceId: nextActionableResourceId(state.resources, state.activeResourceId)
  });
}

function firstActionableResourceId(resources: readonly GhostReplayResourcePayload[]): string | null {
  return (
    resources.find((resource) => resource.decisive && isActionable(resource))?.id ??
    resources.find(isActionable)?.id ??
    null
  );
}

function nextActionableResourceId(resources: readonly GhostReplayResourcePayload[], currentResourceId: string): string | null {
  const currentIndex = resources.findIndex((resource) => resource.id === currentResourceId);
  const searchFrom = currentIndex < 0 ? 0 : currentIndex + 1;
  return resources.slice(searchFrom).find(isActionable)?.id ?? null;
}

function isActionable(resource: GhostReplayResourcePayload): boolean {
  return resource.url !== null && resource.url !== undefined && !resource.openedNow;
}
