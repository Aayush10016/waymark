# Trail

Trail is a personal engineering memory built around one promise:

> **Never solve the same engineering problem twice.**

## Repository status

Trail Architecture v1.1 is frozen. Infrastructure implementation has started; product features have not.

Engineering work must begin with Milestone M0 and the blocking decisions in the readiness report.

The ENG-002 toolchain baseline is Eclipse Temurin 17.0.19+10, Maven 3.9.16 through the committed Wrapper, and Spring Boot 3.5.15. See the [development environment guide](docs/23-development-environment.md) for clean-machine setup.

The ENG-003 TypeScript baseline is Node.js 24.18.0, pnpm 11.10.0, TypeScript 5.9.3, React 19.2.7, and Vite 8.1.4. See the [TypeScript toolchain guide](docs/24-typescript-toolchain.md) for lock strategy and supported Chrome versions.

The ENG-009 local database baseline is PostgreSQL 17 with pgvector 0.8.2 through Docker Compose. See the [local database guide](docs/25-local-database.md) for startup, verification, shutdown, and reset commands.

The ENG-010 CI baseline is the pinned GitHub Actions `quality` workflow. See the [CI quality pipeline guide](docs/26-ci-quality-pipeline.md) for required checks and action pins.

## Start here

1. [Product and architecture index](docs/README.md)
2. [Experience specification](docs/17-experience-specification.md)
3. [Engineering blueprint](docs/18-engineering-blueprint.md)
4. [Engineering milestones](docs/19-engineering-milestones.md)
5. [Ready for Development report](docs/20-ready-for-development-report.md)
6. [CTO Architecture Readiness Review](docs/21-architecture-readiness-review.md)
7. [Architecture Decision Records](docs/adr/README.md)
8. [Final Architecture v1.1 Report](docs/22-final-architecture-v1.1.md)
9. [Contributing](CONTRIBUTING.md)
10. [Security policy](SECURITY.md)
11. [Development environment](docs/23-development-environment.md)
12. [TypeScript toolchain](docs/24-typescript-toolchain.md)
13. [Local database](docs/25-local-database.md)
14. [CI quality pipeline](docs/26-ci-quality-pipeline.md)
15. [Password security policy](docs/27-password-security-policy.md)

Do not implement product features until the M0 architecture gates are complete.
