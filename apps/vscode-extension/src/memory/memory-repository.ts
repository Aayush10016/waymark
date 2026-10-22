import type { DeterministicMemoryRetriever } from "./memory-retriever";
import type { ProjectMemoryCollector } from "./memory-collector";
import type { ProjectMemoryIndex, ProjectMemorySearchResult } from "./models";
import type { WorkspaceFolderInfo } from "../workspace/project-marker";

export class ProjectMemoryRepository {
  private readonly collector: ProjectMemoryCollector;
  private readonly retriever: DeterministicMemoryRetriever;
  private index: ProjectMemoryIndex | undefined;
  private workspaceKey: string | undefined;

  public constructor(collector: ProjectMemoryCollector, retriever: DeterministicMemoryRetriever) {
    this.collector = collector;
    this.retriever = retriever;
  }

  public clear(): void {
    this.index = undefined;
    this.workspaceKey = undefined;
  }

  public async refresh(workspaceFolders: readonly WorkspaceFolderInfo[]): Promise<ProjectMemoryIndex> {
    this.index = await this.collector.collect(workspaceFolders);
    this.workspaceKey = JSON.stringify(workspaceFolders.map(folder => folder.fsPath).sort());
    return this.index;
  }

  public async search(
    query: string,
    workspaceFolders: readonly WorkspaceFolderInfo[]
  ): Promise<readonly ProjectMemorySearchResult[]> {
    const key = JSON.stringify(workspaceFolders.map(folder => folder.fsPath).sort());
    const index = this.index !== undefined && this.workspaceKey === key
      ? this.index
      : await this.refresh(workspaceFolders);
    return this.retriever.search(query, index.documents);
  }
}
