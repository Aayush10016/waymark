export function normalizeText(value: string): string {
  return value.toLowerCase();
}

export function tokenize(value: string): readonly string[] {
  return [...new Set(normalizeText(value).match(/[a-z0-9]+/g) ?? [])];
}

export function includesPhrase(fieldValue: string, query: string): boolean {
  const normalizedQuery = normalizeText(query).trim();
  return normalizedQuery.length > 0 && normalizeText(fieldValue).includes(normalizedQuery);
}

export function formatMetadata(metadata: Readonly<Record<string, string>>): string {
  return Object.entries(metadata)
    .map(([key, value]) => `${key}: ${value}`)
    .join("\n");
}
