# ADR-0026: OpenAPI and Runtime-Validated Extension Contracts

- **Status:** Accepted
- **Date:** 2026-07-05
- **Decision owners:** API Owner, Extension Owner

## Context

Backend and extension evolve together but deploy asynchronously. Internal extension contexts also exchange privileged JSON messages.

## Decision

Use version-controlled OpenAPI as the HTTP contract, generate TypeScript client/types into `packages/api-client`, and wrap generated calls in feature adapters. Use separate versioned runtime schemas in `packages/extension-messages` for JSON-serializable service-worker/popup/side-panel messages. Synthetic fixtures validate both sides.

## Alternatives considered

- Handwritten duplicate DTOs: fast initially, drift-prone.
- Shared TypeScript types only: cannot govern Java/runtime validation.
- GraphQL/code-first schema: unnecessary paradigm/tooling.
- Protobuf: strong contracts, poor fit for simple browser REST/JSON and debugging.

## Why selected

It makes distributed-client compatibility testable while keeping HTTP and privileged internal messaging appropriately separate.

## Trade-offs

- Code generation/tooling and review noise.
- OpenAPI cannot express every semantic invariant.
- Runtime message schemas duplicate some static type structure.

## Risks

- Generated files are hand-edited or stale.
- Contract says compatible while behavior changes.
- Unknown message versions trigger unsafe defaults.

## One-million-user review

No runtime bottleneck. Contract count/client diversity increases governance needs.

## Future migration path

Publish versioned contract artifacts/SDKs if more clients appear; add consumer compatibility matrices and schema registry only with actual independent consumers. Preserve REST/message semantics during generator replacement.

## Consequences

CI lints OpenAPI, detects breaking changes, regenerates/compiles client, validates response fixtures, and rejects unknown privileged message versions safely.
