alter table background_jobs
    drop constraint ck_background_jobs_job_type;

alter table background_jobs
    add constraint ck_background_jobs_job_type check (
        job_type in (
            'PROBLEM_SESSION_ENRICHMENT',
            'SOLVED_PROBLEM_SESSION_EMBEDDING'
        )
    );

insert into embedding_model_generations (
    id,
    generation_key,
    generation_version,
    provider,
    model,
    dimensions,
    active
) values (
    '00000000-0000-4000-8000-000000000065',
    'solved-session',
    1,
    'mock',
    'trail-mock-embedding',
    3,
    true
) on conflict (generation_key, generation_version) do nothing;
