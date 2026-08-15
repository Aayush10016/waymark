import { readdirSync, readFileSync } from "node:fs";
import { join, relative } from "node:path";

const root = process.cwd();
const ignoredDirectories = new Set([
  ".git",
  ".idea",
  ".vscode",
  "dist",
  "node_modules",
  "target"
]);
const binaryExtensions = new Set([
  ".gif",
  ".ico",
  ".jar",
  ".jpeg",
  ".jpg",
  ".pdf",
  ".png",
  ".webp",
  ".zip"
]);
const allowedSecretLikeFiles = new Set(["infra/docker/.env.example"]);

const patterns = [
  {
    name: "AWS access key",
    regex: /AKIA[0-9A-Z]{16}/g
  },
  {
    name: "GitHub token",
    regex: /gh[pousr]_[A-Za-z0-9_]{36,}/g
  },
  {
    name: "Google API key",
    regex: /AIza[0-9A-Za-z_-]{35}/g
  },
  {
    name: "OpenAI-style API key",
    regex: /sk-[A-Za-z0-9]{20,}/g
  },
  {
    name: "private key block",
    regex: /-----BEGIN [A-Z ]*PRIVATE KEY-----/g
  },
  {
    name: "Slack token",
    regex: /xox[baprs]-[A-Za-z0-9-]{20,}/g
  }
];

const findings = [];

function isBinaryPath(path) {
  return [...binaryExtensions].some((extension) => path.endsWith(extension));
}

function walk(directory) {
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    if (entry.isDirectory() && ignoredDirectories.has(entry.name)) {
      continue;
    }

    const absolutePath = join(directory, entry.name);
    if (entry.isDirectory()) {
      walk(absolutePath);
      continue;
    }

    if (!entry.isFile() || isBinaryPath(absolutePath)) {
      continue;
    }

    scanFile(absolutePath);
  }
}

function scanFile(absolutePath) {
  const path = relative(root, absolutePath).replaceAll("\\", "/");
  if (path.endsWith(".env") && !allowedSecretLikeFiles.has(path)) {
    findings.push(`${path}: committed .env file is not allowed`);
    return;
  }

  const buffer = readFileSync(absolutePath);
  if (buffer.includes(0)) {
    return;
  }

  const text = buffer.toString("utf8");
  const lines = text.split(/\n/);
  for (const pattern of patterns) {
    pattern.regex.lastIndex = 0;
    for (const match of text.matchAll(pattern.regex)) {
      const lineNumber = lineNumberForIndex(lines, match.index ?? 0);
      findings.push(`${path}:${lineNumber}: possible ${pattern.name}`);
    }
  }
}

function lineNumberForIndex(lines, index) {
  let offset = 0;
  for (let i = 0; i < lines.length; i += 1) {
    offset += lines[i].length + 1;
    if (offset > index) {
      return i + 1;
    }
  }
  return lines.length;
}

walk(root);

if (findings.length > 0) {
  console.error("Secret scan failed:");
  for (const finding of findings) {
    console.error(`- ${finding}`);
  }
  process.exit(1);
}

console.log("Secret scan passed.");

