alter table background_jobs
    add column lease_owner text,
    add column lease_expires_at timestamptz;

alter table background_jobs
    add constraint ck_background_jobs_lease_owner_bounded check (
        lease_owner is null
        or length(btrim(lease_owner)) between 1 and 128
    ),
    add constraint ck_background_jobs_lease_state_consistent check (
        (
            state = 'LEASED'
            and lease_owner is not null
            and lease_expires_at is not null
        )
        or (
            state <> 'LEASED'
            and lease_owner is null
            and lease_expires_at is null
        )
    );

create index idx_background_jobs_ready_claim
    on background_jobs (next_attempt_at, created_at, id)
    where state = 'READY';

create index idx_background_jobs_expired_lease
    on background_jobs (lease_expires_at, id)
    where state = 'LEASED';
