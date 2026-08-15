# Incident Response Runbook

## Owners

- Primary: Trail on-call engineer.
- Backup: Trail CTO.
- Security escalation: security owner listed in the private incident contact roster.

## Detection

- Actuator health/readiness failure.
- Elevated 5xx responses or authentication failures.
- Database connection saturation.
- Structured logs showing repeated application exceptions.
- User reports through the private-beta support channel.

## Mitigation

1. Classify severity and start an incident note with timeline, owner, and affected capability.
2. Preserve evidence before changing state.
3. Disable non-critical background processing if it increases user impact.
4. Roll back the latest deploy if the incident correlates with a release.
5. Communicate user-visible impact only after facts are verified.

## Recovery

1. Restore the affected service to healthy readiness.
2. Confirm database migrations are complete and not partially applied.
3. Re-enable disabled background processing only after the service is stable.
4. Record root cause, corrective action, and follow-up owner.

## Verification

- Readiness and liveness endpoints are healthy.
- Error rate returns to baseline.
- No sensitive data was emitted in logs.
- Affected user operation succeeds end-to-end.
- Follow-up issue is created for any unresolved prevention work.
