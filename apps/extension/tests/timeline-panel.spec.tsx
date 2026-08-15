import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import { TimelinePanel, groupTimelineRows, nextTimelineSelection, removeTimelineRow, restoreTimelineRow } from "../src/sidepanel/timeline";
import type { TimelineRow } from "../src/sidepanel/timeline";

describe("TimelinePanel", () => {
  it("renders historical mode as immutable evidence without candidate controls", () => {
    const markup = renderToStaticMarkup(
      <TimelinePanel mode="historical" onBack={() => undefined} problemTitle="Spring CORS" rows={rows()} />
    );

    expect(markup).toContain("Historical timeline");
    expect(markup).toContain("User-authored solution remains primary");
    expect(markup).toContain("+1 repeats");
    expect(markup).not.toContain("Remove");
    expect(markup).not.toContain("Opened now");
  });

  it("renders candidate review remove controls and ghost opened-now markers", () => {
    const candidate = renderToStaticMarkup(
      <TimelinePanel mode="candidate" onBack={() => undefined} problemTitle="Spring CORS" rows={rows()} />
    );
    expect(candidate).toContain("Candidate review timeline");
    expect(candidate).toContain("Remove");

    const ghost = renderToStaticMarkup(
      <TimelinePanel mode="ghost" onBack={() => undefined} problemTitle="Spring CORS" rows={rows()} />
    );
    expect(ghost).toContain("Ghost Replay timeline");
    expect(ghost).toContain("Opened now");
  });

  it("groups repeats and preserves deterministic remove, undo, and keyboard behavior", () => {
    const grouped = groupTimelineRows(rows());
    expect(grouped).toHaveLength(2);
    expect(grouped[0]?.repeatCount).toBe(1);

    const removed = removeTimelineRow(rows(), "81000000-0000-4000-8000-000000000001");
    expect(removed).toHaveLength(2);
    expect(removed).not.toContainEqual(expect.objectContaining({ id: "81000000-0000-4000-8000-000000000001" }));

    const restored = restoreTimelineRow(removed, rows()[0]!);
    expect(restored.map((row) => row.id)).toEqual([
      "81000000-0000-4000-8000-000000000001",
      "81000000-0000-4000-8000-000000000002",
      "81000000-0000-4000-8000-000000000003"
    ]);

    expect(nextTimelineSelection(null, 3, 1)).toBe(0);
    expect(nextTimelineSelection(2, 3, 1)).toBe(0);
    expect(nextTimelineSelection(0, 3, -1)).toBe(2);
  });
});

function rows(): readonly TimelineRow[] {
  return [
    {
      decisive: true,
      hostname: "docs.spring.io",
      id: "81000000-0000-4000-8000-000000000001",
      openedNow: true,
      occurredAt: "2026-07-13T08:00:00.000Z",
      title: "Spring CORS docs",
      type: "DOCUMENTATION",
      url: "https://docs.spring.io/cors"
    },
    {
      decisive: false,
      hostname: "docs.spring.io",
      id: "81000000-0000-4000-8000-000000000002",
      openedNow: false,
      occurredAt: "2026-07-13T08:01:00.000Z",
      title: "Spring CORS docs reload",
      type: "DOCUMENTATION",
      url: "https://docs.spring.io/cors"
    },
    {
      decisive: false,
      id: "81000000-0000-4000-8000-000000000003",
      openedNow: false,
      occurredAt: "2026-07-13T08:02:00.000Z",
      queryText: "spring security cors",
      type: "SEARCH_QUERY"
    }
  ];
}
