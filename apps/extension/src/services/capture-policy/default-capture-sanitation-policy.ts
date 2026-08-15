import type { CaptureSanitationPolicy } from "./url-query-sanitizer";

export const defaultCaptureSanitationPolicy: CaptureSanitationPolicy = {
  policyVersion: "url-query-sanitation-v1",
  searchAllowlist: [
    {
      engine: "google",
      hosts: ["google.com", "www.google.com"],
      pathPrefixes: ["/search"],
      queryParameters: ["q"]
    },
    {
      engine: "bing",
      hosts: ["bing.com", "www.bing.com"],
      pathPrefixes: ["/search"],
      queryParameters: ["q"]
    },
    {
      engine: "duckduckgo",
      hosts: ["duckduckgo.com", "www.duckduckgo.com"],
      pathPrefixes: ["/"],
      queryParameters: ["q"]
    },
    {
      engine: "stackoverflow",
      hosts: ["stackoverflow.com"],
      pathPrefixes: ["/search"],
      queryParameters: ["q"]
    },
    {
      engine: "github",
      hosts: ["github.com"],
      pathPrefixes: ["/search"],
      queryParameters: ["q"]
    }
  ]
};
