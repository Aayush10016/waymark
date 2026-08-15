# Tabletop Exercise v1

## Owners

- Facilitator: Trail CTO.
- Participants: identity owner, privacy owner, AI integration owner, and operations owner.
- Recorder: engineer not currently executing the scenario.

## Detection

The exercise starts from four simulated signals:

1. Readiness intermittently fails after a deploy.
2. A user reports deleted memories are still visible.
3. AI derivation jobs fail from provider rate limiting.
4. Refresh-session failures spike after key rotation.

## Mitigation

- Incident response runbook correctly routes ownership and severity.
- Deletion runbook keeps user content out of incident notes.
- Provider outage runbook preserves saved solution access while AI work is degraded.
- Authentication runbook treats token integrity as a security-sensitive path.

## Recovery

- Recovery steps were walked end-to-end for service health, deletion verification, AI job recovery, and identity validation.
- No missing operational owner was found.
- No product feature work was introduced by the exercise.

## Verification

- All runbooks contain owners, detection, mitigation, recovery, and verification sections.
- Tabletop findings are resolved for v1.1 readiness.
- Remaining improvements are future operational hardening, not blockers for ENG-097.

## Findings resolved

- Added explicit backup owners for every runbook.
- Added log-safety checks to deletion, provider, and authentication verification.
- Added follow-up issue requirement for unresolved prevention work.
