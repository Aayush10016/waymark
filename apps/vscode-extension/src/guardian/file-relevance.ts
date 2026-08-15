import { extname } from "node:path";

export const SUPPORTED_AUTOMATIC_GUARDIAN_EXTENSIONS = [
  ".java",
  ".ts",
  ".tsx",
  ".js",
  ".jsx",
  ".py",
  ".cs",
  ".cpp",
  ".h",
  ".hpp",
  ".xml",
  ".yml",
  ".yaml",
  ".json",
  ".properties"
] as const;

export function isAutomaticGuardianRelevantPath(relativePath: string): boolean {
  const normalized = relativePath.replace(/\\/g, "/").toLowerCase();
  if (normalized.length === 0 || normalized.includes("\0")) {
    return false;
  }

  if (EXCLUDED_PATH_PARTS.some((part) => normalized === part || normalized.startsWith(`${part}/`) || normalized.includes(`/${part}/`))) {
    return false;
  }

  return SUPPORTED_AUTOMATIC_GUARDIAN_EXTENSIONS.includes(extname(normalized) as (typeof SUPPORTED_AUTOMATIC_GUARDIAN_EXTENSIONS)[number]);
}

export function languageForPath(relativePath: string): string {
  switch (extname(relativePath).toLowerCase()) {
    case ".java":
      return "java";
    case ".ts":
      return "typescript";
    case ".tsx":
      return "typescriptreact";
    case ".js":
      return "javascript";
    case ".jsx":
      return "javascriptreact";
    case ".py":
      return "python";
    case ".cs":
      return "csharp";
    case ".cpp":
    case ".hpp":
    case ".h":
      return "cpp";
    case ".xml":
      return "xml";
    case ".yml":
    case ".yaml":
      return "yaml";
    case ".json":
      return "json";
    case ".properties":
      return "properties";
    default:
      return "plaintext";
  }
}

const EXCLUDED_PATH_PARTS = [
  ".git",
  "node_modules",
  "dist",
  "build",
  "out",
  "target",
  "coverage"
];
