export function trailApiBaseUrl(): string {
  const configuredValue = readViteEnvironmentValue("VITE_TRAIL_API_BASE_URL");
  return configuredValue ?? "http://localhost:8080/api/v1";
}

function readViteEnvironmentValue(key: string): string | null {
  const environment: unknown = import.meta.env;
  if (!isRecord(environment)) {
    return null;
  }

  const value = environment[key];
  if (typeof value !== "string" || value.trim().length === 0) {
    return null;
  }

  return value;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}
