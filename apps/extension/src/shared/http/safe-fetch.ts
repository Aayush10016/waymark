export type FetchImplementation = typeof fetch;

export function defaultFetchImplementation(): FetchImplementation {
  return fetch.bind(globalThis);
}

export function executeFetch(
  fetchImplementation: FetchImplementation,
  input: RequestInfo | URL,
  init?: RequestInit
): Promise<Response> {
  return fetchImplementation(input, init);
}
