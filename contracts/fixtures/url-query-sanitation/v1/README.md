# URL and Query Sanitation Corpus v1

`corpus.json` is the ENG-026 fixture corpus for Trail v1.1.

It covers:

- allowed and rejected URL schemes;
- credentials in URLs;
- private, local, and prohibited hosts;
- internationalized domains and IP hosts;
- tracker parameter removal;
- secret-like parameter removal;
- search-query extraction from versioned allowlisted search engines.

Future extension and backend sanitizer implementations must consume this corpus directly and must not become more permissive than these expected outcomes.
