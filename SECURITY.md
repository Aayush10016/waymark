# Security Policy

Trail handles sensitive engineering metadata. Do not report suspected vulnerabilities in a public issue.

## Reporting

The private reporting address and response SLA must be configured before private beta as part of operational-owner blocker B8. Until then, report directly to the Technical Lead through the private project channel.

Do not include real URLs, search queries, problem descriptions, final solutions, tokens, prompts, model output, account identifiers, or customer data in the initial report. Use an opaque request/diagnostic ID and synthetic reproduction where possible.

## Supported versions

Trail is pre-release. No public version is currently supported. The supported Chrome/backend compatibility window will be published with the first private-beta release.

## Response priorities

- Critical: active exploitation, sensitive-data exposure, authentication/authorization bypass, or widespread unrecoverable loss.
- High: serious integrity/privacy failure or core security control bypass.
- Medium: meaningful security weakness requiring pre-release correction.
- Low: hardening opportunity with limited practical impact.

Security fixes follow the same protected pull request and verification process, expedited according to incident policy. Secrets, capture permissions, deletion, authentication, and cross-user search paths require heightened review.
