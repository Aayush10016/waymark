import { AiProviderError } from "./provider";

import type { AiProvider, AiProviderRequest, AiProviderResult } from "./provider";

export interface NvidiaProviderConfiguration {
  readonly apiKey: string | undefined;
  readonly model: string | undefined;
  readonly baseUrl: string;
  readonly timeoutMs: number;
  readonly maxRetries: number;
  readonly retryBaseDelayMs: number;
  readonly maxTokens: number;
  readonly temperature: number;
}

export type SecretConfigurationStatus = "Configured" | "Missing" | "Invalid/empty";

export interface NvidiaConfigurationDiagnostics {
  readonly provider: "NVIDIA";
  readonly apiKeyStatus: SecretConfigurationStatus;
  readonly model: string | undefined;
  readonly modelStatus: SecretConfigurationStatus;
  readonly baseUrl: string;
  readonly timeoutMs: number;
  readonly maxRetries: number;
  readonly retryBaseDelayMs: number;
  readonly maxTokens: number;
  readonly temperature: number;
}

export interface HttpTransport {
  fetch(url: string, init: RequestInit): Promise<Response>;
}

export interface DelayScheduler {
  wait(milliseconds: number, signal?: AbortSignal): Promise<void>;
}

export class NvidiaAiProvider implements AiProvider {
  private readonly configuration: NvidiaProviderConfiguration;
  private readonly transport: HttpTransport;
  private readonly delayScheduler: DelayScheduler;

  public constructor(
    configuration: NvidiaProviderConfiguration,
    transport: HttpTransport = new GlobalFetchTransport(),
    delayScheduler: DelayScheduler = new TimeoutDelayScheduler()
  ) {
    this.configuration = configuration;
    this.transport = transport;
    this.delayScheduler = delayScheduler;
  }

  public async generateText(request: AiProviderRequest): Promise<AiProviderResult> {
    const apiKey = this.configuration.apiKey?.trim();
    if (apiKey === undefined || apiKey.length === 0) {
      throw new AiProviderError("MISSING_API_KEY", "Waymark AI is not configured.");
    }

    const model = this.configuration.model?.trim();
    if (model === undefined || model.length === 0) {
      throw new AiProviderError("MISSING_MODEL", "Waymark AI model is not configured.");
    }

    const response = await this.generateWithRetries(apiKey, model, request);
    return {
      text: response,
      provider: "nvidia",
      model
    };
  }

  private async generateWithRetries(apiKey: string, model: string, request: AiProviderRequest): Promise<string> {
    let attempt = 0;
    while (true) {
      try {
        return await this.generateOnce(apiKey, model, request);
      } catch (error) {
        const providerError = toProviderError(error);
        if (!isRetryable(providerError) || attempt >= this.configuration.maxRetries) {
          throw providerError;
        }

        const delayMs = this.configuration.retryBaseDelayMs * 2 ** attempt;
        attempt += 1;
        await this.delayScheduler.wait(delayMs, request.signal);
      }
    }
  }

  private async generateOnce(apiKey: string, model: string, request: AiProviderRequest): Promise<string> {
    const timeoutController = new AbortController();
    const timeout = setTimeout(() => {
      timeoutController.abort();
    }, this.configuration.timeoutMs);
    const signal = linkedSignal(request.signal, timeoutController.signal);

    try {
      const response = await this.transport.fetch(chatCompletionsUrl(this.configuration.baseUrl), {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${apiKey}`
        },
        body: JSON.stringify({
          model,
          messages: [
            { role: "system", content: request.systemPrompt },
            { role: "user", content: request.userPrompt }
          ],
          max_tokens: this.configuration.maxTokens,
          temperature: this.configuration.temperature,
          stream: false
        }),
        signal
      });

      if (!response.ok) {
        throw errorForStatus(response.status);
      }

      return parseCompletionText(await response.json());
    } catch (error) {
      throw classifyRequestError(error, request.signal);
    } finally {
      clearTimeout(timeout);
    }
  }
}

export function configurationFromEnvironment(env: NodeJS.ProcessEnv = process.env): NvidiaProviderConfiguration {
  return {
    apiKey: env.NVIDIA_API_KEY,
    model: env.NVIDIA_MODEL,
    baseUrl: env.NVIDIA_BASE_URL ?? "https://integrate.api.nvidia.com/v1",
    timeoutMs: parsePositiveInteger(env.NVIDIA_TIMEOUT_MS, 30_000),
    maxRetries: parsePositiveInteger(env.NVIDIA_MAX_RETRIES, 2),
    retryBaseDelayMs: parsePositiveInteger(env.NVIDIA_RETRY_BASE_DELAY_MS, 500),
    maxTokens: parsePositiveInteger(env.NVIDIA_MAX_TOKENS, 900),
    temperature: parseTemperature(env.NVIDIA_TEMPERATURE, 0.2)
  };
}

export function diagnosticsForConfiguration(configuration: NvidiaProviderConfiguration): NvidiaConfigurationDiagnostics {
  return {
    provider: "NVIDIA",
    apiKeyStatus: secretStatus(configuration.apiKey),
    model: nonEmpty(configuration.model),
    modelStatus: secretStatus(configuration.model),
    baseUrl: configuration.baseUrl,
    timeoutMs: configuration.timeoutMs,
    maxRetries: configuration.maxRetries,
    retryBaseDelayMs: configuration.retryBaseDelayMs,
    maxTokens: configuration.maxTokens,
    temperature: configuration.temperature
  };
}

export function isNvidiaAiReady(diagnostics: NvidiaConfigurationDiagnostics): boolean {
  return diagnostics.apiKeyStatus === "Configured" && diagnostics.modelStatus === "Configured";
}

class GlobalFetchTransport implements HttpTransport {
  public async fetch(url: string, init: RequestInit): Promise<Response> {
    return fetch(url, init);
  }
}

class TimeoutDelayScheduler implements DelayScheduler {
  public async wait(milliseconds: number, signal?: AbortSignal): Promise<void> {
    await new Promise<void>((resolve, reject) => {
      if (signal?.aborted === true) {
        reject(new AiProviderError("CANCELLED", "Waymark AI request was cancelled."));
        return;
      }

      const timeout = setTimeout(resolve, milliseconds);
      signal?.addEventListener("abort", () => {
        clearTimeout(timeout);
        reject(new AiProviderError("CANCELLED", "Waymark AI request was cancelled."));
      }, { once: true });
    });
  }
}

function chatCompletionsUrl(baseUrl: string): string {
  return `${baseUrl.replace(/\/+$/, "")}/chat/completions`;
}

function parseCompletionText(payload: unknown): string {
  if (!isRecord(payload)) {
    throw new AiProviderError("MALFORMED_RESPONSE", "Waymark AI received a malformed provider response.");
  }

  const choices = payload.choices;
  if (!Array.isArray(choices)) {
    throw new AiProviderError("MALFORMED_RESPONSE", "Waymark AI received a malformed provider response.");
  }

  const firstChoice: unknown = choices[0];
  if (!isRecord(firstChoice) || !isRecord(firstChoice.message)) {
    throw new AiProviderError("MALFORMED_RESPONSE", "Waymark AI received a malformed provider response.");
  }

  const content = firstChoice.message.content;
  if (typeof content !== "string" || content.trim().length === 0) {
    throw new AiProviderError("MALFORMED_RESPONSE", "Waymark AI received a malformed provider response.");
  }

  return content.trim();
}

function errorForStatus(status: number): AiProviderError {
  if (status === 401) {
    return new AiProviderError("AUTHENTICATION_FAILED", "Waymark AI authentication failed.");
  }
  if (status === 403) {
    return new AiProviderError("FORBIDDEN", "Waymark AI access was denied.");
  }
  if (status === 429) {
    return new AiProviderError("RATE_LIMITED", "Waymark AI is temporarily rate limited.");
  }
  if (status >= 500) {
    return new AiProviderError("UNAVAILABLE", "Waymark AI provider is temporarily unavailable.");
  }
  return new AiProviderError("UNAVAILABLE", "Waymark AI provider rejected the request.");
}

function classifyRequestError(error: unknown, requestSignal: AbortSignal | undefined): AiProviderError {
  if (error instanceof AiProviderError) {
    return error;
  }

  if (requestSignal?.aborted === true) {
    return new AiProviderError("CANCELLED", "Waymark AI request was cancelled.");
  }

  if (error instanceof DOMException && error.name === "AbortError") {
    return new AiProviderError("TIMEOUT", "Waymark AI request timed out.");
  }

  if (error instanceof Error && error.name === "AbortError") {
    return new AiProviderError("TIMEOUT", "Waymark AI request timed out.");
  }

  return new AiProviderError("NETWORK", "Waymark AI could not reach the NVIDIA service.");
}

function toProviderError(error: unknown): AiProviderError {
  return error instanceof AiProviderError
    ? error
    : new AiProviderError("NETWORK", "Waymark AI could not reach the NVIDIA service.");
}

function isRetryable(error: AiProviderError): boolean {
  return error.code === "RATE_LIMITED" || error.code === "NETWORK" || error.code === "UNAVAILABLE" || error.code === "TIMEOUT";
}

function linkedSignal(primary: AbortSignal | undefined, secondary: AbortSignal): AbortSignal {
  if (primary === undefined) {
    return secondary;
  }

  const controller = new AbortController();
  const abort = (): void => {
    controller.abort();
  };
  primary.addEventListener("abort", abort, { once: true });
  secondary.addEventListener("abort", abort, { once: true });
  if (primary.aborted || secondary.aborted) {
    controller.abort();
  }
  return controller.signal;
}

function parsePositiveInteger(value: string | undefined, fallback: number): number {
  if (value === undefined) {
    return fallback;
  }

  const parsed = Number.parseInt(value, 10);
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : fallback;
}

function parseTemperature(value: string | undefined, fallback: number): number {
  if (value === undefined) {
    return fallback;
  }

  const parsed = Number.parseFloat(value);
  return Number.isFinite(parsed) && parsed >= 0 && parsed <= 2 ? parsed : fallback;
}

function secretStatus(value: string | undefined): SecretConfigurationStatus {
  if (value === undefined) {
    return "Missing";
  }

  return value.trim().length === 0 ? "Invalid/empty" : "Configured";
}

function nonEmpty(value: string | undefined): string | undefined {
  const trimmed = value?.trim();
  return trimmed === undefined || trimmed.length === 0 ? undefined : trimmed;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}
