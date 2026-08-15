create table consent_grants (
    id uuid primary key,
    user_id uuid not null references users (id) on delete cascade,
    policy_version text not null,
    capture_scope text not null,
    ai_scope text not null,
    decision text not null,
    granted_at timestamptz,
    withdrawn_at timestamptz,
    created_at timestamptz not null default now(),
    constraint ck_consent_grants_policy_version_not_blank check (length(btrim(policy_version)) > 0),
    constraint ck_consent_grants_capture_scope check (capture_scope = 'BROWSER_NAVIGATION_METADATA'),
    constraint ck_consent_grants_ai_scope check (ai_scope in ('NONE', 'PROBLEM_MEMORY')),
    constraint ck_consent_grants_decision check (decision in ('GRANTED', 'WITHDRAWN')),
    constraint ck_consent_grants_decision_timestamp check (
        (decision = 'GRANTED' and granted_at is not null and withdrawn_at is null)
        or (decision = 'WITHDRAWN' and granted_at is null and withdrawn_at is not null)
    )
);

create index idx_consent_grants_user_created
    on consent_grants (user_id, created_at desc, id desc);

create index idx_consent_grants_user_policy_created
    on consent_grants (user_id, policy_version, created_at desc, id desc);

create table capture_events (
    id uuid primary key,
    user_id uuid not null references users (id) on delete cascade,
    client_event_id text not null,
    client_sequence bigint,
    client_instance_id text not null,
    tab_session_id text,
    parent_client_event_id text,
    source text not null,
    resource_type text not null,
    navigation_type text,
    sanitized_url text not null,
    canonical_url_hash bytea not null,
    hostname text not null,
    title text,
    search_engine text,
    search_query text,
    removed_query_parameters text[] not null default array[]::text[],
    transition_qualifiers text[] not null default array[]::text[],
    policy_version text not null,
    browser_event_time_ms double precision,
    occurred_at timestamptz not null,
    received_at timestamptz not null default now(),
    candidate_problem_session_id uuid,
    quality_flags text[] not null default array[]::text[],
    retention_expires_at timestamptz not null,
    deleted_at timestamptz,
    created_at timestamptz not null default now(),
    constraint ck_capture_events_client_event_id_not_blank check (
        length(btrim(client_event_id)) between 1 and 128
    ),
    constraint ck_capture_events_client_sequence_non_negative check (
        client_sequence is null
        or client_sequence >= 0
    ),
    constraint ck_capture_events_client_instance_id_not_blank check (
        length(btrim(client_instance_id)) between 1 and 128
    ),
    constraint ck_capture_events_tab_session_id_not_blank check (
        tab_session_id is null
        or length(btrim(tab_session_id)) between 1 and 128
    ),
    constraint ck_capture_events_parent_client_event_id_not_blank check (
        parent_client_event_id is null
        or length(btrim(parent_client_event_id)) between 1 and 128
    ),
    constraint ck_capture_events_source check (
        source in (
            'TAB_UPDATED',
            'WEB_NAVIGATION_COMMITTED',
            'WEB_NAVIGATION_HISTORY_STATE'
        )
    ),
    constraint ck_capture_events_resource_type check (
        resource_type in (
            'AI_CHAT',
            'DOCUMENTATION',
            'FORUM',
            'OTHER',
            'REPOSITORY',
            'SEARCH',
            'VIDEO'
        )
    ),
    constraint ck_capture_events_navigation_type_not_blank check (
        navigation_type is null
        or length(btrim(navigation_type)) between 1 and 64
    ),
    constraint ck_capture_events_sanitized_url_http check (
        length(sanitized_url) between 1 and 2048
        and sanitized_url ~ '^https?://'
    ),
    constraint ck_capture_events_canonical_url_hash_length check (octet_length(canonical_url_hash) = 32),
    constraint ck_capture_events_hostname_normalized check (
        hostname = lower(hostname)
        and length(btrim(hostname)) between 1 and 253
    ),
    constraint ck_capture_events_title_bounded check (
        title is null
        or length(title) <= 120
    ),
    constraint ck_capture_events_search_engine_not_blank check (
        search_engine is null
        or length(btrim(search_engine)) between 1 and 64
    ),
    constraint ck_capture_events_search_query_bounded check (
        search_query is null
        or length(search_query) <= 240
    ),
    constraint ck_capture_events_policy_version_not_blank check (length(btrim(policy_version)) > 0),
    constraint ck_capture_events_browser_event_time_non_negative check (
        browser_event_time_ms is null
        or browser_event_time_ms >= 0
    ),
    constraint ck_capture_events_retention_after_occurrence check (retention_expires_at > occurred_at),
    constraint ck_capture_events_deleted_after_received check (
        deleted_at is null
        or deleted_at >= received_at
    )
);

create unique index uq_capture_events_user_client_event_id
    on capture_events (user_id, client_event_id);

create index idx_capture_events_user_occurred
    on capture_events (user_id, occurred_at desc, id desc)
    where deleted_at is null;

create index idx_capture_events_user_candidate_occurred
    on capture_events (user_id, candidate_problem_session_id, occurred_at)
    where candidate_problem_session_id is not null
        and deleted_at is null;

create index idx_capture_events_retention_expires
    on capture_events (retention_expires_at)
    where deleted_at is null;
