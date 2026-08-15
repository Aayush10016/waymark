export type AiProviderErrorCode =
  | "MISSING_API_KEY"
  | "MISSING_MODEL"
  | "AUTHENTICATION_FAILED"
  | "FORBIDDEN"
  | "RATE_LIMITED"
  | "TIMEOUT"
  | "NETWORK"
  | "MALFORMED_RESPONSE"
  | "UNAVAILABLE"
  | "CANCELLED";

export class AiProviderError extends Error {
  public constructor(
    public readonly code: AiProviderErrorCode,
    message: string
  ) {
    super(message);
    this.name = "AiProviderError";
  }
}

export interface AiProviderRequest {
  readonly systemPrompt: string;
  readonly userPrompt: string;
  readonly signal?: AbortSignal;
}

export interface AiProviderResult {
  readonly text: string;
  readonly provider: string;
  readonly model: string;
}

export interface AiProvider {
  generateText(request: AiProviderRequest): Promise<AiProviderResult>;
}
