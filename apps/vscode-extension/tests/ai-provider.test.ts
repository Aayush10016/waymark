import { describe, expect, it } from "vitest";

import { AiProviderError } from "../src/ai/provider";
import { NvidiaAiProvider, configurationFromEnvironment, diagnosticsForConfiguration, isNvidiaAiReady } from "../src/ai/nvidia-provider";

import type { DelayScheduler, HttpTransport, NvidiaProviderConfiguration } from "../src/ai/nvidia-provider";

describe("NvidiaAiProvider", () => {
  it("reads provider configuration from environment without requiring startup failure", () => {
    const configuration = configurationFromEnvironment({
      NVIDIA_API_KEY: "local-secret",
      NVIDIA_MODEL: "model-name",
      NVIDIA_BASE_URL: "https://example.test/v1",
      NVIDIA_TIMEOUT_MS: "1000",
      NVIDIA_MAX_RETRIES: "1",
      NVIDIA_RETRY_BASE_DELAY_MS: "10",
      NVIDIA_MAX_TOKENS: "256",
      NVIDIA_TEMPERATURE: "0.1"
    });

    expect(configuration).toMatchObject({
      apiKey: "local-secret",
      model: "model-name",
      baseUrl: "https://example.test/v1",
      timeoutMs: 1000,
      maxRetries: 1,
      retryBaseDelayMs: 10,
      maxTokens: 256,
      temperature: 0.1
    });
  });

  it("classifies missing API key", async () => {
    await expect(provider({ apiKey: undefined }).generateText(request())).rejects.toMatchObject({
      code: "MISSING_API_KEY"
    });
  });

  it("reports safe configuration diagnostics without exposing the API key", () => {
    const diagnostics = diagnosticsForConfiguration(configurationFromEnvironment({
      NVIDIA_API_KEY: "super-secret-value",
      NVIDIA_MODEL: "model-name"
    }));

    expect(diagnostics.apiKeyStatus).toBe("Configured");
    expect(diagnostics.model).toBe("model-name");
    expect(JSON.stringify(diagnostics)).not.toContain("super-secret-value");
    expect(isNvidiaAiReady(diagnostics)).toBe(true);
  });

  it("reports missing and empty configuration safely", () => {
    const diagnostics = diagnosticsForConfiguration(configurationFromEnvironment({
      NVIDIA_API_KEY: "   "
    }));

    expect(diagnostics.apiKeyStatus).toBe("Invalid/empty");
    expect(diagnostics.modelStatus).toBe("Missing");
    expect(isNvidiaAiReady(diagnostics)).toBe(false);
  });

  it("classifies missing model configuration", async () => {
    await expect(provider({ model: undefined }).generateText(request())).rejects.toMatchObject({
      code: "MISSING_MODEL"
    });
  });

  it("sends a successful OpenAI-compatible chat completion request", async () => {
    const transport = new FakeTransport([jsonResponse(200, { choices: [{ message: { content: "Answer [CODE-SELECTED]" } }] })]);
    const result = await provider({}, transport).generateText(request());

    expect(result).toEqual({
      text: "Answer [CODE-SELECTED]",
      provider: "nvidia",
      model: "test-model"
    });
    expect(transport.requests[0]?.url).toBe("https://integrate.api.nvidia.com/v1/chat/completions");
    expect(transport.requests[0]?.authorization).toBe("Bearer test-key");
    expect(transport.requests[0]?.body).toMatchObject({
      model: "test-model",
      messages: [
        { role: "system", content: "system" },
        { role: "user", content: "user" }
      ],
      stream: false
    });
  });

  it("classifies malformed responses", async () => {
    await expect(provider({}, new FakeTransport([jsonResponse(200, { choices: [] })])).generateText(request())).rejects.toMatchObject({
      code: "MALFORMED_RESPONSE"
    });
  });

  it.each([
    [401, "AUTHENTICATION_FAILED"],
    [403, "FORBIDDEN"],
    [429, "RATE_LIMITED"],
    [503, "UNAVAILABLE"]
  ] as const)("classifies HTTP %s", async (status, code) => {
    await expect(provider({}, new FakeTransport([jsonResponse(status, {})])).generateText(request())).rejects.toMatchObject({
      code
    });
  });

  it("classifies network failure", async () => {
    await expect(provider({}, new FakeTransport([new Error("network down")])).generateText(request())).rejects.toMatchObject({
      code: "NETWORK"
    });
  });

  it("classifies timeout", async () => {
    await expect(provider({}, new FakeTransport([new DOMException("aborted", "AbortError")])).generateText(request())).rejects.toMatchObject({
      code: "TIMEOUT"
    });
  });

  it("retries retryable failures and stops at maximum retry limit", async () => {
    const transport = new FakeTransport([
      jsonResponse(429, {}),
      jsonResponse(429, {}),
      jsonResponse(200, { choices: [{ message: { content: "ok" } }] })
    ]);
    const delay = new FakeDelayScheduler();
    const result = await provider({ maxRetries: 2 }, transport, delay).generateText(request());

    expect(result.text).toBe("ok");
    expect(transport.requests).toHaveLength(3);
    expect(delay.delays).toEqual([1, 2]);
  });

  it("does not retry past the configured retry limit", async () => {
    const transport = new FakeTransport([jsonResponse(429, {}), jsonResponse(429, {})]);

    await expect(provider({ maxRetries: 1 }, transport).generateText(request())).rejects.toMatchObject({
      code: "RATE_LIMITED"
    });
    expect(transport.requests).toHaveLength(2);
  });

  it("supports cancellation", async () => {
    const abortController = new AbortController();
    abortController.abort();

    await expect(provider({}, new FakeTransport([new DOMException("aborted", "AbortError")])).generateText({
      ...request(),
      signal: abortController.signal
    })).rejects.toMatchObject({
      code: "CANCELLED"
    });
  });

  it("does not expose API keys in classified error messages", async () => {
    const secret = "super-secret-value";
    try {
      await provider({ apiKey: secret }, new FakeTransport([jsonResponse(401, {})])).generateText(request());
      throw new Error("expected failure");
    } catch (error) {
      expect(error).toBeInstanceOf(AiProviderError);
      expect((error as Error).message).not.toContain(secret);
    }
  });
});

class FakeTransport implements HttpTransport {
  public readonly requests: Array<{ readonly url: string; readonly authorization: string | undefined; readonly body: Record<string, unknown> }> = [];
  private readonly responses: Array<Response | Error>;

  public constructor(responses: Array<Response | Error>) {
    this.responses = responses;
  }

  public async fetch(url: string, init: RequestInit): Promise<Response> {
    await Promise.resolve();
    this.requests.push({
      url,
      authorization: typeof init.headers === "object" && init.headers !== null && "Authorization" in init.headers
        ? String(init.headers.Authorization)
        : undefined,
      body: parseRequestBody(init.body)
    });

    const response = this.responses.shift();
    if (response === undefined) {
      throw new Error("No fake response configured.");
    }
    if (response instanceof Error) {
      throw response;
    }
    return response;
  }
}

class FakeDelayScheduler implements DelayScheduler {
  public readonly delays: number[] = [];

  public async wait(milliseconds: number): Promise<void> {
    await Promise.resolve();
    this.delays.push(milliseconds);
  }
}

function provider(
  overrides: Partial<NvidiaProviderConfiguration> = {},
  transport: HttpTransport = new FakeTransport([]),
  delay: DelayScheduler = new FakeDelayScheduler()
): NvidiaAiProvider {
  return new NvidiaAiProvider({
    apiKey: "test-key",
    model: "test-model",
    baseUrl: "https://integrate.api.nvidia.com/v1",
    timeoutMs: 100,
    maxRetries: 0,
    retryBaseDelayMs: 1,
    maxTokens: 100,
    temperature: 0.2,
    ...overrides
  }, transport, delay);
}

function request(): { readonly systemPrompt: string; readonly userPrompt: string } {
  return {
    systemPrompt: "system",
    userPrompt: "user"
  };
}

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" }
  });
}

function parseRequestBody(body: BodyInit | null | undefined): Record<string, unknown> {
  if (typeof body !== "string") {
    throw new Error("Expected request body to be a string.");
  }

  const parsed: unknown = JSON.parse(body);
  if (typeof parsed !== "object" || parsed === null || Array.isArray(parsed)) {
    throw new Error("Expected request body to be a JSON object.");
  }

  return parsed as Record<string, unknown>;
}
