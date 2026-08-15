# Data Deletion Incident Runbook

## Owners

- Primary: privacy owner.
- Backup: Trail CTO.
- Engineering support: backend owner for identity and retention systems.

## Detection

- Account deletion job failure.
- Retention cleanup failure.
- User report that deleted data remains visible.
- Database audit showing orphaned user-owned records.
- Backup/restore workflow that could reintroduce deleted user data.

## Mitigation

1. Pause affected deletion or restore workflow if it could worsen data retention risk.
2. Identify the user scope and affected data classes.
3. Prevent product surfaces from returning data marked for deletion.
4. Preserve a minimal operational incident record without copying user content.

## Recovery

1. Complete deletion for all source and derived records in the documented dependency order.
2. Verify retention markers and deletion timestamps are consistent.
3. Confirm restored backups do not rehydrate deleted user data without reapplying deletion records.
4. Notify the user only with verified scope and completion status.

## Verification

- User-owned rows are absent or tombstoned according to the deletion policy.
- Derived AI, embedding, recommendation, and capture artifacts are no longer returned.
- Deletion jobs have no failed or stuck records.
- Logs contain identifiers only, not user-authored content.
- A prevention issue is filed for any missing automation.
