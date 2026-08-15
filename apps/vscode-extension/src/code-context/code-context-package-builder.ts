import { GitReadError } from "../git/git-client";

import type { GitClient } from "../git/git-client";
import type { GitCommitSummary } from "../git/models";
import type { CodeContext, CodeContextPackage, CodeGitContext, SourceSelection } from "./models";
import type { CodeContextBuilder } from "./code-context-builder";
import type { ProjectMemorySearchResult } from "../memory/models";
import type { WorkspaceFolderInfo } from "../workspace/project-marker";

const DEFAULT_MEMORY_RESULT_LIMIT = 5;

export interface CodeContextPackageBuilderOptions {
  readonly memoryResultLimit?: number;
  readonly now?: () => Date;
}

export interface ProjectMemorySearcher {
  search(query: string, workspaceFolders: readonly WorkspaceFolderInfo[]): Promise<readonly ProjectMemorySearchResult[]>;
}

export class CodeContextPackageBuilder {
  private readonly codeContextBuilder: CodeContextBuilder;
  private readonly memoryRepository: ProjectMemorySearcher;
  private readonly gitClient: GitClient;
  private readonly memoryResultLimit: number;
  private readonly now: () => Date;

  public constructor(
    codeContextBuilder: CodeContextBuilder,
    memoryRepository: ProjectMemorySearcher,
    gitClient: GitClient,
    options: CodeContextPackageBuilderOptions = {}
  ) {
    this.codeContextBuilder = codeContextBuilder;
    this.memoryRepository = memoryRepository;
    this.gitClient = gitClient;
    this.memoryResultLimit = options.memoryResultLimit ?? DEFAULT_MEMORY_RESULT_LIMIT;
    this.now = options.now ?? (() => new Date());
  }

  public async build(selection: SourceSelection): Promise<CodeContextPackage> {
    const codeContext = this.codeContextBuilder.build(selection);
    const query = buildDeterministicCodeQuery(codeContext);
    const memoryResults = query.length === 0
      ? []
      : (await this.memoryRepository.search(query, [selection.workspaceFolder])).slice(0, this.memoryResultLimit);
    const gitEvidence = await this.gitContextForSelection(selection);

    return {
      codeContext,
      projectEvidence: memoryResults.filter((result) =>
        ["PROJECT", "CONSTITUTION", "RULE", "DECISION"].includes(result.document.sourceType)
      ),
      gitEvidence,
      memoryResults,
      createdAt: this.now().toISOString()
    };
  }

  private async gitContextForSelection(selection: SourceSelection): Promise<CodeGitContext> {
    try {
      const [repository, status, commits] = await Promise.all([
        this.gitClient.getRepositoryInfo(selection.workspaceFolder),
        this.gitClient.getStatus(selection.workspaceFolder),
        this.gitClient.getRecentCommits(selection.workspaceFolder)
      ]);
      const fileStatus = status.find((statusEntry) => statusEntry.path === selection.relativePath);
      const relevantCommits = await this.filterRelevantCommits(selection.relativePath, selection.workspaceFolder, commits);

      return {
        repository,
        fileStatus,
        relevantCommits,
        unavailableReason: undefined
      };
    } catch (error) {
      return {
        repository: undefined,
        fileStatus: undefined,
        relevantCommits: [],
        unavailableReason: error instanceof GitReadError ? error.message : "Git context is unavailable."
      };
    }
  }

  private async filterRelevantCommits(
    relativePath: string,
    workspaceFolder: SourceSelection["workspaceFolder"],
    commits: readonly GitCommitSummary[]
  ): Promise<readonly GitCommitSummary[]> {
    const relevantCommits: GitCommitSummary[] = [];
    for (const commit of commits) {
      try {
        const detail = await this.gitClient.getCommitDetails(workspaceFolder, commit.sha);
        if (detail.changedFiles.includes(relativePath)) {
          relevantCommits.push(commit);
        }
      } catch {
        continue;
      }
    }

    return relevantCommits.slice(0, 5);
  }
}

export function buildDeterministicCodeQuery(codeContext: CodeContext): string {
  const terms = [
    codeContext.symbolName,
    codeContext.fileName.replace(/\.[^.]+$/, ""),
    ...identifierTerms(codeContext.selectedTextPreview)
  ].filter((term): term is string => term !== undefined && term.length > 1);

  return [...new Set(terms)].slice(0, 12).join(" ");
}

function identifierTerms(sourceText: string): readonly string[] {
  return (sourceText.match(/[A-Za-z_][A-Za-z0-9_]*/g) ?? [])
    .filter((term) => !COMMON_CODE_WORDS.has(term))
    .slice(0, 10);
}

const COMMON_CODE_WORDS = new Set([
  "if",
  "else",
  "for",
  "while",
  "return",
  "const",
  "let",
  "var",
  "class",
  "public",
  "private",
  "protected",
  "static",
  "void",
  "new",
  "import",
  "from"
]);
