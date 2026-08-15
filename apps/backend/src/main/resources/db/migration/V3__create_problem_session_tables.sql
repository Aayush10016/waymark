create table problem_sessions (
    id uuid primary key,
    user_id uuid not null references users (id) on delete cascade,
    status text not null,
    source text not null,
    version bigint not null default 0,
    title text,
    description text,
    local_note text,
    generated_summary text,
    technologies text[] not null default array[]::text[],
    frameworks text[] not null default array[]::text[],
    error_phrases text[] not null default array[]::text[],
    tags text[] not null default array[]::text[],
    problem_type text,
    detector_version text,
    prompt_state text,
    final_solution text,
    final_solution_author text,
    solution_authored_at timestamptz,
    started_at timestamptz not null,
    ended_at timestamptz,
    duration_seconds integer,
    search_document text not null default '',
    search_vector tsvector generated always as (to_tsvector('simple', search_document)) stored,
    deleted_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uq_problem_sessions_user_id_id unique (user_id, id),
    constraint ck_problem_sessions_status check (
        status in (
            'CANDIDATE',
            'REVIEWABLE',
            'SOLVED',
            'DISMISSED',
            'UNSOLVED',
            'DELETED'
        )
    ),
    constraint ck_problem_sessions_source check (source in ('MANUAL', 'DETECTED')),
    constraint ck_problem_sessions_version_non_negative check (version >= 0),
    constraint ck_problem_sessions_title_bounded check (
        title is null
        or length(title) <= 200
    ),
    constraint ck_problem_sessions_description_bounded check (
        description is null
        or length(description) <= 4000
    ),
    constraint ck_problem_sessions_local_note_bounded check (
        local_note is null
        or length(local_note) <= 4000
    ),
    constraint ck_problem_sessions_generated_summary_bounded check (
        generated_summary is null
        or length(generated_summary) <= 4000
    ),
    constraint ck_problem_sessions_problem_type_bounded check (
        problem_type is null
        or length(btrim(problem_type)) between 1 and 64
    ),
    constraint ck_problem_sessions_detector_version_bounded check (
        detector_version is null
        or length(btrim(detector_version)) between 1 and 128
    ),
    constraint ck_problem_sessions_prompt_state_bounded check (
        prompt_state is null
        or length(btrim(prompt_state)) between 1 and 64
    ),
    constraint ck_problem_sessions_solution_invariant check (
        (
            status = 'SOLVED'
            and final_solution is not null
            and length(btrim(final_solution)) > 0
            and final_solution_author = 'USER'
            and solution_authored_at is not null
        )
        or (
            status <> 'SOLVED'
            and final_solution is null
            and final_solution_author is null
            and solution_authored_at is null
        )
    ),
    constraint ck_problem_sessions_ended_after_started check (
        ended_at is null
        or ended_at >= started_at
    ),
    constraint ck_problem_sessions_duration_non_negative check (
        duration_seconds is null
        or duration_seconds >= 0
    ),
    constraint ck_problem_sessions_deleted_status check (
        deleted_at is null
        or status = 'DELETED'
    ),
    constraint ck_problem_sessions_updated_after_created check (updated_at >= created_at)
);

create index idx_problem_sessions_user_status_updated
    on problem_sessions (user_id, status, updated_at desc, id desc)
    where deleted_at is null;

create index idx_problem_sessions_user_active
    on problem_sessions (user_id, status, started_at desc, id desc)
    where deleted_at is null
        and status in ('CANDIDATE', 'REVIEWABLE');

create index idx_problem_sessions_search_vector
    on problem_sessions using gin (search_vector);

create table session_artifacts (
    id uuid primary key,
    user_id uuid not null,
    problem_session_id uuid not null,
    position integer not null,
    artifact_type text not null,
    sanitized_url text,
    canonical_url_hash bytea,
    hostname text,
    title text,
    query_text text,
    source_capture_event_id uuid,
    occurred_at timestamptz not null,
    decisive boolean not null default false,
    decisive_source text,
    removed_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint fk_session_artifacts_problem_session
        foreign key (user_id, problem_session_id)
        references problem_sessions (user_id, id)
        on delete cascade,
    constraint ck_session_artifacts_position_positive check (position > 0),
    constraint ck_session_artifacts_type check (
        artifact_type in (
            'PAGE',
            'SEARCH_QUERY',
            'DOCUMENTATION',
            'GITHUB_REPOSITORY',
            'GITHUB_ISSUE',
            'DISCUSSION',
            'VIDEO',
            'AI_CHAT_LINK',
            'USER_NOTE_REFERENCE'
        )
    ),
    constraint ck_session_artifacts_sanitized_url_http check (
        sanitized_url is null
        or (
            length(sanitized_url) between 1 and 2048
            and sanitized_url ~ '^https?://'
        )
    ),
    constraint ck_session_artifacts_url_hash_length check (
        canonical_url_hash is null
        or octet_length(canonical_url_hash) = 32
    ),
    constraint ck_session_artifacts_hostname_normalized check (
        hostname is null
        or (
            hostname = lower(hostname)
            and length(btrim(hostname)) between 1 and 253
        )
    ),
    constraint ck_session_artifacts_title_bounded check (
        title is null
        or length(title) <= 120
    ),
    constraint ck_session_artifacts_query_bounded check (
        query_text is null
        or length(query_text) <= 240
    ),
    constraint ck_session_artifacts_decisive_source_bounded check (
        decisive_source is null
        or length(btrim(decisive_source)) between 1 and 64
    ),
    constraint ck_session_artifacts_removed_after_created check (
        removed_at is null
        or removed_at >= created_at
    ),
    constraint ck_session_artifacts_updated_after_created check (updated_at >= created_at)
);

create unique index uq_session_artifacts_active_position
    on session_artifacts (problem_session_id, position)
    where removed_at is null;

create index idx_session_artifacts_problem_position
    on session_artifacts (problem_session_id, position)
    where removed_at is null;

create index idx_session_artifacts_user_problem_position
    on session_artifacts (user_id, problem_session_id, position)
    where removed_at is null;

alter table capture_events
    add constraint fk_capture_events_candidate_problem_session
    foreign key (user_id, candidate_problem_session_id)
    references problem_sessions (user_id, id);
