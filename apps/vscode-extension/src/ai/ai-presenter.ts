import type { GroundedAiResponse } from "./models";
import type { NvidiaConfigurationDiagnostics } from "./nvidia-provider";

export function formatGroundedAiResponse(response: GroundedAiResponse): string {
  return [
    "Waymark AI",
    "",
    "Answer",
    response.answer,
    "",
    "Grounded in:",
    ...formatEvidenceReferences(response),
    "",
    `Provider: ${response.provider}`,
    `Model: ${response.model}`,
    `Grounded citations: ${response.grounded ? "yes" : "no"}`,
    ...formatWarnings(response)
  ].join("\n");
}

export function formatGroundedAiResponseHtml(response: GroundedAiResponse): string {
  return `<!doctype html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Waymark AI</title>
  <style>
    body { font-family: var(--vscode-font-family); color: var(--vscode-foreground); background: var(--vscode-editor-background); line-height: 1.5; padding: 24px; }
    h1, h2 { margin: 0 0 12px; }
    section { margin: 0 0 24px; }
    pre { white-space: pre-wrap; background: var(--vscode-textBlockQuote-background); padding: 16px; border-radius: 6px; }
    li { margin-bottom: 6px; }
    .meta { color: var(--vscode-descriptionForeground); }
    .warning { color: var(--vscode-editorWarning-foreground); }
  </style>
</head>
<body>
  <h1>Waymark AI</h1>
  <section>
    <h2>Answer</h2>
    <pre>${escapeHtml(response.answer)}</pre>
  </section>
  <section>
    <h2>Evidence used</h2>
    ${formatEvidenceReferencesHtml(response)}
  </section>
  <section class="meta">
    <div>Provider: ${escapeHtml(response.provider)}</div>
    <div>Model: ${escapeHtml(response.model)}</div>
    <div>Grounded citations: ${response.grounded ? "yes" : "no"}</div>
  </section>
  ${formatWarningsHtml(response)}
</body>
</html>`;
}

export function formatAiConfigurationDiagnostics(diagnostics: NvidiaConfigurationDiagnostics): string {
  return [
    "Waymark AI Configuration",
    "",
    `Provider: ${diagnostics.provider}`,
    `API key: ${diagnostics.apiKeyStatus}`,
    `Model: ${diagnostics.model ?? "(missing)"}`,
    `Model status: ${diagnostics.modelStatus}`,
    `Base URL: ${diagnostics.baseUrl}`,
    `Timeout: ${diagnostics.timeoutMs}ms`,
    `Max retries: ${diagnostics.maxRetries}`,
    `Retry base delay: ${diagnostics.retryBaseDelayMs}ms`,
    `Max tokens: ${diagnostics.maxTokens}`,
    `Temperature: ${diagnostics.temperature}`,
    "",
    diagnostics.apiKeyStatus === "Configured"
      ? "Waymark AI can see an API key in the current VS Code process environment."
      : "Waymark AI is not configured. Set NVIDIA_API_KEY and restart VS Code.",
    diagnostics.modelStatus === "Configured"
      ? "Waymark AI model is configured."
      : "Set NVIDIA_MODEL and restart VS Code."
  ].join("\n");
}

function formatEvidenceReferences(response: GroundedAiResponse): readonly string[] {
  if (response.evidenceReferences.length === 0) {
    return ["(no evidence IDs cited by provider response)"];
  }

  return response.evidenceReferences.map((reference) =>
    `- ${reference.sourceType}: ${reference.title} [${reference.evidenceId}] ${reference.sourcePath ?? ""}`.trim()
  );
}

function formatWarnings(response: GroundedAiResponse): readonly string[] {
  if (response.warnings.length === 0) {
    return [];
  }

  return ["", "Warnings:", ...response.warnings.map((warning) => `- ${warning}`)];
}

function formatEvidenceReferencesHtml(response: GroundedAiResponse): string {
  if (response.evidenceReferences.length === 0) {
    return "<p>(no evidence IDs cited by provider response)</p>";
  }

  return `<ul>${response.evidenceReferences.map((reference) =>
    `<li><strong>${escapeHtml(reference.sourceType)}</strong>: ${escapeHtml(reference.title)} <code>${escapeHtml(reference.evidenceId)}</code>${reference.sourcePath === undefined ? "" : ` <span class="meta">${escapeHtml(reference.sourcePath)}</span>`}</li>`
  ).join("")}</ul>`;
}

function formatWarningsHtml(response: GroundedAiResponse): string {
  if (response.warnings.length === 0) {
    return "";
  }

  return `<section class="warning"><h2>Warnings</h2><ul>${response.warnings.map((warning) => `<li>${escapeHtml(warning)}</li>`).join("")}</ul></section>`;
}

function escapeHtml(value: string): string {
  return value
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll("\"", "&quot;");
}
