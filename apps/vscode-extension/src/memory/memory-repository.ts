import type { DeterministicMemoryRetriever } from "./memory-retriever";
import type { ProjectMemoryCollector } from "./memory-collector";
import type { ProjectMemoryIndex, ProjectMemorySearchResult } from "./models";
import type { WorkspaceFolderInfo } from "../workspace/project-marker";

export class ProjectMemoryRepository {
  private readonly collector: ProjectMemoryCollector;
  private readonly retriever: DeterministicMemoryRetriever;
  private index: ProjectMemoryIndex | undefined;

  public constructor(collector: ProjectMemoryCollector, retriever: DeterministicMemoryRetriever) {
    this.collector = collector;
    this.retriever = retriever;
  }

  public clear(): void {
    this.index = undefined;
  }

  public async refresh(workspaceFolders: readonly WorkspaceFolderInfo[]): Promise<ProjectMemoryIndex> {
    this.index = await this.collector.collect(workspaceFolders);
    return this.index;
  }

  public async search(
    query: string,
    workspaceFolders: readonly WorkspaceFolderInfo[]
  ): Promise<readonly ProjectMemorySearchResult[]> {
    const index = this.index ?? (await this.refresh(workspaceFolders));
    return this.retriever.search(query, index.documents);
  }
}
