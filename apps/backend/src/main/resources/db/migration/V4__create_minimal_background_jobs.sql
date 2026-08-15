create table background_jobs (
    id uuid primary key,
    user_id uuid not null references users (id) on delete cascade,
    aggregate_type text not null,
    aggregate_id uuid not null,
    job_type text not null,
    state text not null,
    attempts integer not null default 0,
    next_attempt_at timestamptz not null,
    payload jsonb not null default '{}'::jsonb,
    safe_failure_code text,
    completed_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_background_jobs_aggregate_type check (aggregate_type in ('PROBLEM_SESSION')),
    constraint ck_background_jobs_job_type check (job_type in ('PROBLEM_SESSION_ENRICHMENT')),
    constraint ck_background_jobs_state check (
        state in (
            'READY',
            'LEASED',
            'SUCCEEDED',
            'FAILED_RETRYABLE',
            'FAILED_TERMINAL'
        )
    ),
    constraint ck_background_jobs_attempts_non_negative check (attempts >= 0),
    constraint ck_background_jobs_safe_failure_code_bounded check (
        safe_failure_code is null
        or length(btrim(safe_failure_code)) between 1 and 128
    ),
    constraint ck_background_jobs_completed_state check (
        completed_at is null
        or state in ('SUCCEEDED', 'FAILED_TERMINAL')
    ),
    constraint ck_background_jobs_updated_after_created check (updated_at >= created_at)
);

create unique index uq_background_jobs_user_type_aggregate
    on background_jobs (user_id, job_type, aggregate_type, aggregate_id);
