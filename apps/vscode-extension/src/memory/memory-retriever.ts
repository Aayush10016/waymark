import { compareSearchResults, sourceAuthorityWeight } from "./memory-ranking";
import { formatMetadata, includesPhrase, normalizeText, tokenize } from "./text";

import type { ProjectMemoryDocument, ProjectMemorySearchResult } from "./models";

const TITLE_PHRASE_WEIGHT = 120;
const TITLE_TOKEN_WEIGHT = 35;
const PATH_TOKEN_WEIGHT = 24;
const CONTENT_TOKEN_WEIGHT = 15;
const METADATA_TOKEN_WEIGHT = 8;
const MULTI_TERM_BONUS = 10;

export class DeterministicMemoryRetriever {
  public search(query: string, documents: readonly ProjectMemoryDocument[]): readonly ProjectMemorySearchResult[] {
    const normalizedQuery = normalizeText(query).trim();
    const queryTerms = tokenize(query);

    if (normalizedQuery.length === 0 || queryTerms.length === 0) {
      return [];
    }

    return documents
      .map((document) => scoreDocument(document, normalizedQuery, queryTerms))
      .filter((result): result is ProjectMemorySearchResult => result !== undefined)
      .sort(compareSearchResults);
  }
}

function scoreDocument(
  document: ProjectMemoryDocument,
  normalizedQuery: string,
  queryTerms: readonly string[]
): ProjectMemorySearchResult | undefined {
  let score = sourceAuthorityWeight(document.sourceType);
  const matchedFields = new Set<string>();
  const matchedTerms = new Set<string>();

  if (includesPhrase(document.title, normalizedQuery)) {
    score += TITLE_PHRASE_WEIGHT;
    matchedFields.add("title");
    addMatchingTerms(document.title, queryTerms, matchedTerms);
  }

  for (const term of queryTerms) {
    if (tokenize(document.title).includes(term)) {
      score += TITLE_TOKEN_WEIGHT;
      matchedFields.add("title");
      matchedTerms.add(term);
    }

    if (document.path !== undefined && tokenize(document.path).includes(term)) {
      score += PATH_TOKEN_WEIGHT;
      matchedFields.add("path");
      matchedTerms.add(term);
    }

    if (tokenize(document.content).includes(term)) {
      score += CONTENT_TOKEN_WEIGHT;
      matchedFields.add("content");
      matchedTerms.add(term);
    }

    if (tokenize(formatMetadata(document.metadata)).includes(term)) {
      score += METADATA_TOKEN_WEIGHT;
      matchedFields.add("metadata");
      matchedTerms.add(term);
    }
  }

  if (matchedTerms.size > 1) {
    score += MULTI_TERM_BONUS * matchedTerms.size;
  }

  if (matchedTerms.size === 0) {
    return undefined;
  }

  return {
    document,
    score,
    matchedFields: [...matchedFields].sort(),
    matchedTerms: [...matchedTerms].sort()
  };
}

function addMatchingTerms(value: string, queryTerms: readonly string[], matchedTerms: Set<string>): void {
  const tokens = tokenize(value);
  for (const term of queryTerms) {
    if (tokens.includes(term)) {
      matchedTerms.add(term);
    }
  }
}
