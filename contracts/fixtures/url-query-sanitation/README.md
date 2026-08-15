# URL and Query Sanitation Fixtures

This directory contains language-neutral JSON fixtures for Trail URL/query sanitation.

The corpus is synthetic. It defines shared expected outcomes for extension-side sanitation, backend ingestion validation, and later AI redaction tests. It does not implement sanitation logic.

Versioned fixture sets live under `v1/`, `v2/`, and so on. Consumers should pin the fixture corpus version they execute.
