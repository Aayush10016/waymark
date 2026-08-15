export function parseMarkdownTitle(markdown: string, fallback: string): string {
  const titleLine = markdown
    .split(/\r?\n/)
    .map((line) => line.trim())
    .find((line) => line.startsWith("# "));

  if (titleLine === undefined) {
    return fallback;
  }

  const title = titleLine.replace(/^#\s+/, "").trim();
  return title.length > 0 ? title : fallback;
}

export function parseColonMetadata(markdown: string, fieldName: string): string | undefined {
  const escapedFieldName = escapeRegExp(fieldName);
  const pattern = new RegExp(`^${escapedFieldName}:\\s*(.+?)\\s*$`, "im");
  const match = pattern.exec(markdown);
  return match?.[1]?.trim();
}

export function parseSectionText(markdown: string, heading: string): string {
  const lines = markdown.split(/\r?\n/);
  const headingIndex = lines.findIndex((line) => line.trim().toLowerCase() === `## ${heading}`.toLowerCase());
  if (headingIndex < 0) {
    return "";
  }

  const sectionLines: string[] = [];
  for (let index = headingIndex + 1; index < lines.length; index += 1) {
    const line = lines[index];
    if (line !== undefined && line.trim().startsWith("## ")) {
      break;
    }

    sectionLines.push(line ?? "");
  }

  return sectionLines.join("\n").trim();
}

export function parseHeadingSectionValue(markdown: string, heading: string): string | undefined {
  const value = parseSectionText(markdown, heading)
    .split(/\r?\n/)
    .map((line) => line.trim())
    .find((line) => line.length > 0);
  return value;
}

export function slugifyTitle(title: string): string {
  const slug = title
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
  return slug.length > 0 ? slug : "untitled";
}

export function titleFromFileName(fileName: string): string {
  const baseName = fileName.replace(/\.md$/i, "");
  const withoutAdrPrefix = baseName.replace(/^adr-\d+-?/i, "");
  const words = withoutAdrPrefix.split(/[-_]+/).filter((part) => part.length > 0);
  if (words.length === 0) {
    return baseName;
  }

  return words.map((word) => `${word.charAt(0).toUpperCase()}${word.slice(1)}`).join(" ");
}

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}
