create table recommendation_events (
    id uuid primary key,
    user_id uuid not null,
    recommendation_id uuid not null,
    current_problem_session_id uuid not null,
    recommended_problem_session_id uuid not null,
    outcome text not null,
    retrieved_problem_sessions jsonb not null default '[]'::jsonb,
    evidence jsonb not null default '[]'::jsonb,
    embedding_generation_key text not null,
    embedding_generation_version integer not null,
    embedding_provider text not null,
    embedding_model text not null,
    embedding_input_fingerprint text,
    algorithm_version text not null,
    created_at timestamptz not null default now(),
    constraint fk_recommendation_events_current_session
        foreign key (user_id, current_problem_session_id)
        references problem_sessions (user_id, id)
        on delete cascade,
    constraint fk_recommendation_events_recommended_session
        foreign key (user_id, recommended_problem_session_id)
        references problem_sessions (user_id, id)
        on delete cascade,
    constraint fk_recommendation_events_embedding_generation
        foreign key (embedding_generation_key, embedding_generation_version)
        references embedding_model_generations (generation_key, generation_version),
    constraint ck_recommendation_events_distinct_sessions check (
        current_problem_session_id <> recommended_problem_session_id
    ),
    constraint ck_recommendation_events_outcome check (
        outcome in (
            'SHOWN',
            'OPENED',
            'HELPFUL',
            'NOT_RELATED',
            'DISMISSED'
        )
    ),
    constraint ck_recommendation_events_retrieved_array check (
        jsonb_typeof(retrieved_problem_sessions) = 'array'
        and jsonb_array_length(retrieved_problem_sessions) between 1 and 50
    ),
    constraint ck_recommendation_events_evidence_array check (
        jsonb_typeof(evidence) = 'array'
        and jsonb_array_length(evidence) between 1 and 50
    ),
    constraint ck_recommendation_events_generation_key_bounded check (
        length(btrim(embedding_generation_key)) between 1 and 128
    ),
    constraint ck_recommendation_events_generation_version_positive check (
        embedding_generation_version > 0
    ),
    constraint ck_recommendation_events_provider_bounded check (
        length(btrim(embedding_provider)) between 1 and 64
    ),
    constraint ck_recommendation_events_model_bounded check (
        length(btrim(embedding_model)) between 1 and 128
    ),
    constraint ck_recommendation_events_embedding_fingerprint_sha256 check (
        embedding_input_fingerprint is null
        or embedding_input_fingerprint ~ '^[0-9a-f]{64}$'
    ),
    constraint ck_recommendation_events_algorithm_bounded check (
        length(btrim(algorithm_version)) between 1 and 128
    )
);

create unique index uq_recommendation_events_outcome_once
    on recommendation_events (user_id, recommendation_id, outcome);

create index idx_recommendation_events_user_current_created
    on recommendation_events (user_id, current_problem_session_id, created_at desc, id desc);

create index idx_recommendation_events_user_pair_outcome
    on recommendation_events (
        user_id,
        current_problem_session_id,
        recommended_problem_session_id,
        outcome,
        created_at desc
    );

create index idx_recommendation_events_user_recommendation_created
    on recommendation_events (user_id, recommendation_id, created_at desc, id desc);
