import { constants } from "node:fs";
import { access, mkdir, readdir, readFile, writeFile } from "node:fs/promises";
import { join } from "node:path";

import { UserFacingError } from "../shared/errors";
import { resolveWaymarkWorkspacePaths } from "../workspace/trail-paths";
import { parseHeadingSectionValue, parseMarkdownTitle, slugifyTitle, titleFromFileName } from "./markdown";

import type { WorkspaceFolderInfo } from "../workspace/project-marker";

export const DECISION_STATUSES = ["Proposed", "Accepted", "Superseded", "Deprecated"] as const;

export type DecisionStatus = (typeof DECISION_STATUSES)[number] | "Unknown";

export interface ArchitecturalDecision {
  readonly id: string;
  readonly title: string;
  readonly status: DecisionStatus;
  readonly rawStatus: string | undefined;
  readonly path: string;
  readonly hasMetadataWarning: boolean;
}

export interface DecisionCreationInput {
  readonly title: string;
  readonly status?: (typeof DECISION_STATUSES)[number];
  readonly context?: string;
  readonly decision?: string;
  readonly consequences?: string;
}

export class DecisionStore {
  public async list(workspaceFolder: WorkspaceFolderInfo): Promise<readonly ArchitecturalDecision[]> {
    const decisionsDirectory = resolveWaymarkWorkspacePaths(workspaceFolder).decisionsDirectoryPath;
    if (!(await pathExists(decisionsDirectory))) {
      return [];
    }

    const entries = await readdir(decisionsDirectory, { withFileTypes: true });
    const markdownFiles = entries
      .filter((entry) => entry.isFile() && entry.name.toLowerCase().endsWith(".md"))
      .map((entry) => entry.name)
      .sort((left, right) => left.localeCompare(right));

    return Promise.all(markdownFiles.map((fileName) => this.readDecision(join(decisionsDirectory, fileName), fileName)));
  }

  public async create(
    workspaceFolder: WorkspaceFolderInfo,
    title: string,
    input: Omit<DecisionCreationInput, "title"> = {}
  ): Promise<ArchitecturalDecision> {
    return this.createFromInput(workspaceFolder, { title, ...input });
  }

  public async createFromInput(workspaceFolder: WorkspaceFolderInfo, input: DecisionCreationInput): Promise<ArchitecturalDecision> {
    const normalized = normalizeDecisionCreationInput(input);
    const decisionsDirectory = resolveWaymarkWorkspacePaths(workspaceFolder).decisionsDirectoryPath;
    await mkdir(decisionsDirectory, { recursive: true });

    const nextNumber = await this.nextDecisionNumber(decisionsDirectory);
    const fileName = `ADR-${nextNumber.toString().padStart(3, "0")}-${slugifyTitle(normalized.title)}.md`;
    const path = join(decisionsDirectory, fileName);

    try {
      await writeFile(path, decisionTemplate(nextNumber, normalized), {
        encoding: "utf8",
        flag: "wx"
      });
    } catch (error) {
      if (isNodeError(error) && error.code === "EEXIST") {
        throw new UserFacingError(`A decision named ${fileName} already exists.`);
      }

      throw error;
    }

    return this.readDecision(path, fileName);
  }

  private async nextDecisionNumber(decisionsDirectory: string): Promise<number> {
    const entries = await readdir(decisionsDirectory, { withFileTypes: true });
    const usedNumbers = entries
      .filter((entry) => entry.isFile())
      .map((entry) => /^ADR-(\d+)/i.exec(entry.name)?.[1])
      .filter((value): value is string => value !== undefined)
      .map((value) => Number.parseInt(value, 10))
      .filter((value) => Number.isSafeInteger(value) && value > 0);

    const used = new Set(usedNumbers);
    let candidate = 1;
    while (used.has(candidate)) {
      candidate += 1;
    }

    return candidate;
  }

  private async readDecision(path: string, fileName: string): Promise<ArchitecturalDecision> {
    const content = await readFile(path, "utf8");
    const rawStatus = parseHeadingSectionValue(content, "Status");
    const status = normalizeDecisionStatus(rawStatus);
    return {
      id: fileName.replace(/\.md$/i, ""),
      title: parseMarkdownTitle(content, titleFromFileName(fileName)),
      status,
      rawStatus,
      path,
      hasMetadataWarning: status === "Unknown"
    };
  }
}

export function decisionTemplate(decisionNumber: number, titleOrInput: string | Required<DecisionCreationInput>): string {
  const input = typeof titleOrInput === "string"
    ? normalizeDecisionCreationInput({ title: titleOrInput })
    : titleOrInput;
  return `# ADR-${decisionNumber.toString().padStart(3, "0")}: ${input.title}

## Status

${input.status}

## Context

${input.context}

## Decision

${input.decision}

## Consequences

${input.consequences}
`;
}

function normalizeDecisionCreationInput(input: DecisionCreationInput): Required<DecisionCreationInput> {
  const title = input.title.trim();
  if (title.length === 0) {
    throw new UserFacingError("Decision title is required.");
  }

  return {
    title,
    status: input.status ?? "Proposed",
    context: nonEmpty(input.context) ?? "<Why this decision was needed>",
    decision: nonEmpty(input.decision) ?? "<What was decided>",
    consequences: nonEmpty(input.consequences) ?? "<What this decision means for the project>"
  };
}

function nonEmpty(value: string | undefined): string | undefined {
  const trimmed = value?.trim();
  return trimmed === undefined || trimmed.length === 0 ? undefined : trimmed;
}

function normalizeDecisionStatus(value: string | undefined): DecisionStatus {
  if (value === undefined) {
    return "Unknown";
  }

  const normalized = value.trim().toLowerCase();
  const known = DECISION_STATUSES.find((status) => status.toLowerCase() === normalized);
  return known ?? "Unknown";
}

async function pathExists(path: string): Promise<boolean> {
  try {
    await access(path, constants.F_OK);
    return true;
  } catch (error) {
    if (isNodeError(error) && error.code === "ENOENT") {
      return false;
    }

    throw error;
  }
}

function isNodeError(error: unknown): error is NodeJS.ErrnoException {
  return error instanceof Error && "code" in error;
}
