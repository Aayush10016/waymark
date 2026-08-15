import { basename } from "node:path";

import type { CodeContext, SourceSelection, SourceSymbol } from "./models";

const DEFAULT_SURROUNDING_LINE_RADIUS = 10;
const DEFAULT_SELECTION_PREVIEW_LIMIT = 2_000;

export interface CodeContextBuilderOptions {
  readonly surroundingLineRadius?: number;
  readonly selectionPreviewLimit?: number;
}

export class CodeContextBuilder {
  private readonly surroundingLineRadius: number;
  private readonly selectionPreviewLimit: number;

  public constructor(options: CodeContextBuilderOptions = {}) {
    this.surroundingLineRadius = options.surroundingLineRadius ?? DEFAULT_SURROUNDING_LINE_RADIUS;
    this.selectionPreviewLimit = options.selectionPreviewLimit ?? DEFAULT_SELECTION_PREVIEW_LIMIT;
  }

  public build(selection: SourceSelection): CodeContext {
    const lines = selection.fullText.split(/\r?\n/);
    const surroundingStartLine = Math.max(1, selection.startLine - this.surroundingLineRadius);
    const surroundingEndLine = Math.min(lines.length, selection.endLine + this.surroundingLineRadius);
    const containingSymbol = findSmallestContainingSymbol(selection.symbols, selection.startLine, selection.endLine);
    const selectedTextPreview = selection.selectedText.slice(0, this.selectionPreviewLimit);

    return {
      workspaceFolder: selection.workspaceFolder,
      filePath: selection.filePath,
      relativePath: selection.relativePath,
      fileName: selection.fileName || basename(selection.filePath),
      language: selection.language,
      symbolName: containingSymbol?.name,
      symbolKind: containingSymbol?.kind,
      startLine: selection.startLine,
      endLine: selection.endLine,
      selectedText: selection.selectedText,
      selectedTextPreview,
      isSelectionPreviewTruncated: selection.selectedText.length > selectedTextPreview.length,
      surroundingCode: lines.slice(surroundingStartLine - 1, surroundingEndLine).join("\n"),
      surroundingStartLine,
      surroundingEndLine,
      relatedImports: extractImports(selection.fullText)
    };
  }
}

export function extractImports(sourceText: string): readonly string[] {
  return sourceText
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => isImportLikeLine(line))
    .slice(0, 50);
}

function isImportLikeLine(line: string): boolean {
  return (
    /^import\s+.+/.test(line) ||
    /^from\s+[\w.]+\s+import\s+.+/.test(line) ||
    /^using\s+.+;?$/.test(line) ||
    /^#include\s+[<"].+[>"]/.test(line) ||
    /^require\(.+\)/.test(line) ||
    /^const\s+.+\s*=\s*require\(.+\)/.test(line)
  );
}

function findSmallestContainingSymbol(
  symbols: readonly SourceSymbol[],
  startLine: number,
  endLine: number
): SourceSymbol | undefined {
  const containingSymbols = flattenSymbols(symbols).filter(
    (symbol) => symbol.startLine <= startLine && symbol.endLine >= endLine
  );

  return containingSymbols.sort((left, right) => {
    const leftSize = left.endLine - left.startLine;
    const rightSize = right.endLine - right.startLine;
    return leftSize - rightSize;
  })[0];
}

function flattenSymbols(symbols: readonly SourceSymbol[]): readonly SourceSymbol[] {
  return symbols.flatMap((symbol) => [symbol, ...flattenSymbols(symbol.children ?? [])]);
}
