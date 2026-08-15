export type TimelineMode = "candidate" | "ghost" | "historical";

export interface TimelineRow {
  readonly decisive: boolean;
  readonly hostname?: string | null;
  readonly id: string;
  readonly openedNow: boolean;
  readonly occurredAt: string;
  readonly queryText?: string | null;
  readonly title?: string | null;
  readonly type: string;
  readonly url?: string | null;
}

export interface GroupedTimelineRow {
  readonly repeatCount: number;
  readonly row: TimelineRow;
}

export function groupTimelineRows(rows: readonly TimelineRow[]): readonly GroupedTimelineRow[] {
  const grouped: GroupedTimelineRow[] = [];
  for (const row of rows) {
    const previous = grouped[grouped.length - 1];
    if (previous !== undefined && repeatKey(previous.row) === repeatKey(row)) {
      grouped[grouped.length - 1] = { repeatCount: previous.repeatCount + 1, row: previous.row };
    } else {
      grouped.push({ repeatCount: 0, row });
    }
  }
  return Object.freeze(grouped.map((item) => Object.freeze({ ...item })));
}

export function removeTimelineRow(rows: readonly TimelineRow[], rowId: string): readonly TimelineRow[] {
  return Object.freeze(rows.filter((row) => row.id !== rowId).map((row) => Object.freeze({ ...row })));
}

export function restoreTimelineRow(rows: readonly TimelineRow[], row: TimelineRow): readonly TimelineRow[] {
  return Object.freeze([...rows, row].sort((left, right) => Date.parse(left.occurredAt) - Date.parse(right.occurredAt)));
}

export function nextTimelineSelection(current: number | null, rowCount: number, direction: 1 | -1): number | null {
  if (rowCount === 0) {
    return null;
  }
  if (current === null) {
    return direction === 1 ? 0 : rowCount - 1;
  }
  return (current + direction + rowCount) % rowCount;
}

function repeatKey(row: TimelineRow): string {
  return row.url ?? row.queryText ?? row.title ?? row.id;
}
