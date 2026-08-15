import { constants } from "node:fs";
import { access, mkdir, readdir, readFile, unlink, writeFile } from "node:fs/promises";
import { join } from "node:path";

import { UserFacingError } from "../shared/errors";
import { resolveWaymarkWorkspacePaths } from "../workspace/trail-paths";
import { parseColonMetadata, parseMarkdownTitle, parseSectionText, slugifyTitle, titleFromFileName } from "./markdown";

import type { WorkspaceFolderInfo } from "../workspace/project-marker";

export const RULE_STATUSES = ["active", "deprecated"] as const;
export const RULE_TYPES = ["architecture", "technology", "security", "coding", "process", "other"] as const;

export type RuleStatus = (typeof RULE_STATUSES)[number] | "unknown";
export type RuleType = (typeof RULE_TYPES)[number] | "unknown";

export interface ExplicitRule {
  readonly id: string;
  readonly title: string;
  readonly status: RuleStatus;
  readonly type: RuleType;
  readonly rawStatus: string | undefined;
  readonly rawType: string | undefined;
  readonly scope: string;
  readonly ruleText: string;
  readonly reason: string;
  readonly source: string;
  readonly path: string;
  readonly isActive: boolean;
  readonly hasMetadataWarning: boolean;
}

export interface RuleCreationInput {
  readonly title: string;
  readonly type: (typeof RULE_TYPES)[number];
  readonly scope?: string;
  readonly status?: (typeof RULE_STATUSES)[number];
  readonly ruleText?: string;
  readonly reason?: string;
  readonly source?: string;
}

export class RuleStore {
  public async list(workspaceFolder: WorkspaceFolderInfo): Promise<readonly ExplicitRule[]> {
    const rulesDirectory = resolveWaymarkWorkspacePaths(workspaceFolder).rulesDirectoryPath;
    if (!(await pathExists(rulesDirectory))) {
      return [];
    }

    const entries = await readdir(rulesDirectory, { withFileTypes: true });
    const markdownFiles = entries
      .filter((entry) => entry.isFile() && entry.name.toLowerCase().endsWith(".md"))
      .map((entry) => entry.name)
      .sort((left, right) => left.localeCompare(right));

    return Promise.all(markdownFiles.map((fileName) => this.readRule(join(rulesDirectory, fileName), fileName)));
  }

  public async create(
    workspaceFolder: WorkspaceFolderInfo,
    title: string,
    type: (typeof RULE_TYPES)[number],
    input: Omit<RuleCreationInput, "title" | "type"> = {}
  ): Promise<ExplicitRule> {
    return this.createFromInput(workspaceFolder, { title, type, ...input });
  }

  public async createFromInput(workspaceFolder: WorkspaceFolderInfo, input: RuleCreationInput): Promise<ExplicitRule> {
    const normalized = normalizeRuleCreationInput(input);
    const rulesDirectory = resolveWaymarkWorkspacePaths(workspaceFolder).rulesDirectoryPath;
    const fileName = await nextRuleFileName(rulesDirectory, slugifyTitle(normalized.title));
    const path = join(rulesDirectory, fileName);

    await mkdir(rulesDirectory, { recursive: true });

    try {
      await writeFile(path, ruleTemplate(normalized), {
        encoding: "utf8",
        flag: "wx"
      });
    } catch (error) {
      if (isNodeError(error) && error.code === "EEXIST") {
        throw new UserFacingError(`A rule named ${fileName} already exists.`);
      }

      throw error;
    }

    return this.readRule(path, fileName);
  }

  public async delete(workspaceFolder: WorkspaceFolderInfo, ruleId: string): Promise<void> {
    const rules = await this.list(workspaceFolder);
    const rule = rules.find((candidate) => candidate.id === ruleId);
    if (rule === undefined) {
      throw new UserFacingError("Rule was not found.");
    }

    await unlink(rule.path);
  }

  private async readRule(path: string, fileName: string): Promise<ExplicitRule> {
    const content = await readFile(path, "utf8");
    const rawStatus = parseColonMetadata(content, "Status");
    const rawType = parseColonMetadata(content, "Type");
    const status = normalizeRuleStatus(rawStatus);
    const type = normalizeRuleType(rawType);

    return {
      id: fileName.replace(/\.md$/i, ""),
      title: parseMarkdownTitle(content, titleFromFileName(fileName)),
      status,
      type,
      rawStatus,
      rawType,
      scope: parseColonMetadata(content, "Scope") ?? "",
      ruleText: parseSectionText(content, "Rule"),
      reason: parseSectionText(content, "Reason"),
      source: parseSectionText(content, "Source"),
      path,
      isActive: status === "active",
      hasMetadataWarning: status === "unknown" || type === "unknown"
    };
  }
}

export function ruleTemplate(
  titleOrInput: string | Required<RuleCreationInput>,
  type?: (typeof RULE_TYPES)[number]
): string {
  const input = typeof titleOrInput === "string"
    ? normalizeRuleCreationInput({ title: titleOrInput, type: requireRuleType(type) })
    : titleOrInput;
  return formattedRuleTemplate(input);
}

function formattedRuleTemplate(input: Required<RuleCreationInput>): string {
  return `# ${input.title}

Status: ${input.status}
Type: ${input.type}
Scope: ${input.scope}

## Rule

${input.ruleText}

## Reason

${input.reason}

## Source

${input.source}
`;
}

function normalizeRuleCreationInput(input: RuleCreationInput): Required<RuleCreationInput> {
  const title = input.title.trim();
  if (title.length === 0) {
    throw new UserFacingError("Rule title is required.");
  }

  const ruleText = nonEmpty(input.ruleText);
  const reason = nonEmpty(input.reason);
  const source = nonEmpty(input.source);

  return {
    title,
    type: input.type,
    scope: nonEmpty(input.scope) ?? "project-wide",
    status: input.status ?? "active",
    ruleText: ruleText ?? "<Enter rule>",
    reason: reason ?? "<Why this rule exists>",
    source: source ?? "<Source>"
  };
}

function requireRuleType(type: (typeof RULE_TYPES)[number] | undefined): (typeof RULE_TYPES)[number] {
  if (type === undefined) {
    throw new UserFacingError("Rule type is required.");
  }

  return type;
}

function nonEmpty(value: string | undefined): string | undefined {
  const trimmed = value?.trim();
  return trimmed === undefined || trimmed.length === 0 ? undefined : trimmed;
}

function normalizeRuleStatus(value: string | undefined): RuleStatus {
  if (value === undefined) {
    return "unknown";
  }

  const normalized = value.trim().toLowerCase();
  return RULE_STATUSES.includes(normalized as (typeof RULE_STATUSES)[number])
    ? (normalized as (typeof RULE_STATUSES)[number])
    : "unknown";
}

function normalizeRuleType(value: string | undefined): RuleType {
  if (value === undefined) {
    return "unknown";
  }

  const normalized = value.trim().toLowerCase();
  return RULE_TYPES.includes(normalized as (typeof RULE_TYPES)[number])
    ? (normalized as (typeof RULE_TYPES)[number])
    : "unknown";
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

async function nextRuleFileName(rulesDirectory: string, baseSlug: string): Promise<string> {
  if (!(await pathExists(rulesDirectory))) {
    return `${baseSlug}.md`;
  }

  const entries = await readdir(rulesDirectory, { withFileTypes: true });
  const usedNames = new Set(entries.filter((entry) => entry.isFile()).map((entry) => entry.name.toLowerCase()));
  let candidate = `${baseSlug}.md`;
  let suffix = 2;
  while (usedNames.has(candidate.toLowerCase())) {
    candidate = `${baseSlug}-${suffix}.md`;
    suffix += 1;
  }
  return candidate;
}

function isNodeError(error: unknown): error is NodeJS.ErrnoException {
  return error instanceof Error && "code" in error;
}
