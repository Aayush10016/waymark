# Authentication Incident Runbook

## Owners

- Primary: identity owner.
- Backup: Trail CTO.
- Security escalation: security owner listed in the private incident contact roster.

## Detection

- Spike in failed login, refresh, or password-reset attempts.
- JWT validation failures outside normal clock-skew expectations.
- Suspicious refresh-session reuse or replay indicators.
- User report of account access loss.
- Secret rotation or signing-key deployment anomaly.

## Mitigation

1. Determine whether the issue is availability, abuse, token integrity, or secret exposure.
2. If token integrity is suspected, rotate affected signing material using the documented secret process.
3. Revoke affected refresh sessions when supported by completed identity functionality.
4. Rate-limit or block abusive sources through platform controls.
5. Avoid logging passwords, reset tokens, refresh tokens, authorization headers, or cookies.

## Recovery

1. Confirm registration, verification, login, access-token issuance, refresh, password reset, and extension authentication work.
2. Confirm invalid or expired credentials fail closed.
3. Restore normal rate limits after abuse subsides.
4. Document user impact and required communication.

## Verification

- Identity tests and health checks pass.
- Access tokens validate only under current accepted keys.
- Refresh-session behavior remains replay resistant.
- Sensitive credentials are absent from logs.
- Corrective action issue is filed for any unresolved control gap.
