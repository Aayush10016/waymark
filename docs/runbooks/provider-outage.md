# Provider Outage Runbook

## Owners

- Primary: provider integration owner.
- Backup: backend owner.
- Product escalation: Trail CTO for user-facing degradation decisions.

## Detection

- AI or identity-email provider timeout or 5xx rate exceeds baseline.
- AI derivation jobs repeatedly fail, or identity verification/reset deliveries fail above baseline.
- Cost or rate-limit alerts show throttling.
- Health checks for provider-dependent background work degrade.

## Mitigation

1. Confirm provider status and whether failures are timeout, auth, quota, sender/domain verification, or payload related.
2. Stop or reduce provider-dependent background processing if documented limits are exceeded.
3. Preserve raw solved solutions and user-authored data; AI failure must not block saved memory access.
4. For identity email failures, keep authentication HTTP responses generic and do not reveal account existence.
5. Use degraded copy that explains summaries, recommendations, or email delivery may be delayed.

## Recovery

1. Re-enable provider-dependent jobs gradually.
2. Reprocess failed derivations through existing idempotent job behavior.
3. Verify prompts and payload minimization remain unchanged.
4. Record whether a provider configuration or fallback needs follow-up.

## Verification

- Provider-dependent jobs complete successfully.
- Identity verification and reset email delivery succeeds through the configured provider.
- Failed jobs are either recovered or safely retryable.
- Saved solutions remain accessible without AI output.
- Logs exclude prompts, user content, identity email content, recipients, tokens, links, and provider secrets.
- Follow-up issue exists for unresolved provider reliability gaps.
