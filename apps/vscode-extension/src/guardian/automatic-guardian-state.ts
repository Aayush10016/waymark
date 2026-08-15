import type { GuardianFinding } from "./models";

export interface AutomaticGuardianFileResult {
  readonly fileKey: string;
  readonly generation: number;
  readonly findings: readonly GuardianFinding[];
}

export interface AutomaticGuardianStateUpdate {
  readonly applied: boolean;
  readonly findings: readonly GuardianFinding[];
}

export class AutomaticGuardianFindingState {
  private readonly latestGenerationsByFile = new Map<string, number>();
  private readonly findingsByFile = new Map<string, readonly GuardianFinding[]>();

  public beginFileCheck(fileKey: string): number {
    const generation = (this.latestGenerationsByFile.get(fileKey) ?? 0) + 1;
    this.latestGenerationsByFile.set(fileKey, generation);
    return generation;
  }

  public applyFileResult(result: AutomaticGuardianFileResult): AutomaticGuardianStateUpdate {
    if (this.latestGenerationsByFile.get(result.fileKey) !== result.generation) {
      return {
        applied: false,
        findings: this.allFindings()
      };
    }

    if (result.findings.length === 0) {
      this.findingsByFile.delete(result.fileKey);
    } else {
      this.findingsByFile.set(result.fileKey, result.findings);
    }

    return {
      applied: true,
      findings: this.allFindings()
    };
  }

  public clearFile(fileKey: string): readonly GuardianFinding[] {
    this.beginFileCheck(fileKey);
    this.findingsByFile.delete(fileKey);
    return this.allFindings();
  }

  public clearAll(): void {
    this.latestGenerationsByFile.clear();
    this.findingsByFile.clear();
  }

  public allFindings(): readonly GuardianFinding[] {
    return [...this.findingsByFile.values()].flat();
  }
}
