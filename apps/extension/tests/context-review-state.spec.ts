import { describe, expect, it } from "vitest";
import {
  createContextReviewState,
  meetsFinalSolutionUsabilityTarget,
  removeContextArtifact,
  undoRemoveContextArtifact
} from "../src/sidepanel/problem-session";

describe("context review state", () => {
  it("orders artifacts deterministically by occurrence time", () => {
    const state = createContextReviewState([artifact("second", "2026-07-16T08:02:00.000Z"), artifact("first", "2026-07-16T08:01:00.000Z")]);

    expect(state.visibleArtifacts.map((candidate) => candidate.id)).toEqual(["first", "second"]);
  });

  it("removes and restores artifacts locally without deleting durable backend state", () => {
    const initial = createContextReviewState([artifact("one", "2026-07-16T08:01:00.000Z"), artifact("two", "2026-07-16T08:02:00.000Z")]);
    const removed = removeContextArtifact(initial, "one");

    expect(removed.visibleArtifacts.map((candidate) => candidate.id)).toEqual(["two"]);
    expect(removed.removedArtifacts.map((candidate) => candidate.id)).toEqual(["one"]);

    const restored = undoRemoveContextArtifact(removed, "one");
    expect(restored.visibleArtifacts.map((candidate) => candidate.id)).toEqual(["one", "two"]);
    expect(restored.removedArtifacts).toEqual([]);
  });

  it("keeps unknown artifact removals idempotent", () => {
    const initial = createContextReviewState([artifact("one", "2026-07-16T08:01:00.000Z")]);

    expect(removeContextArtifact(initial, "missing")).toBe(initial);
    expect(undoRemoveContextArtifact(initial, "missing")).toBe(initial);
  });

  it("keeps the target solution-entry path under 30 seconds", () => {
    expect(
      meetsFinalSolutionUsabilityTarget({
        outcomeChoiceSeconds: 2,
        saveChoiceSeconds: 2,
        textEntrySeconds: 24
      })
    ).toBe(true);
  });
});

function artifact(id: string, occurredAt: string) {
  return {
    artifactType: "DOCUMENTATION",
    hostname: "docs.example.test",
    id,
    occurredAt,
    title: id
  };
}
