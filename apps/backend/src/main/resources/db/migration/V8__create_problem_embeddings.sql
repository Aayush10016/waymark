create extension if not exists vector;

create table embedding_model_generations (
    id uuid primary key,
    generation_key text not null,
    generation_version integer not null,
    provider text not null,
    model text not null,
    dimensions integer not null,
    active boolean not null default false,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uq_embedding_model_generations_key_version unique (generation_key, generation_version),
    constraint ck_embedding_model_generations_key_bounded check (length(btrim(generation_key)) between 1 and 128),
    constraint ck_embedding_model_generations_provider_bounded check (length(btrim(provider)) between 1 and 64),
    constraint ck_embedding_model_generations_model_bounded check (length(btrim(model)) between 1 and 128),
    constraint ck_embedding_model_generations_version_positive check (generation_version > 0),
    constraint ck_embedding_model_generations_dimensions_positive check (dimensions > 0),
    constraint ck_embedding_model_generations_updated_after_created check (updated_at >= created_at)
);

create unique index uq_embedding_model_generations_one_active
    on embedding_model_generations (generation_key)
    where active;

create table problem_embeddings (
    id uuid primary key,
    user_id uuid not null,
    problem_session_id uuid not null,
    generation_key text not null,
    generation_version integer not null,
    provider text not null,
    model text not null,
    dimensions integer not null,
    input_fingerprint text not null,
    policy_version text not null,
    embedding vector not null,
    active boolean not null default true,
    superseded_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint fk_problem_embeddings_problem_session
        foreign key (user_id, problem_session_id)
        references problem_sessions (user_id, id)
        on delete cascade,
    constraint fk_problem_embeddings_generation
        foreign key (generation_key, generation_version)
        references embedding_model_generations (generation_key, generation_version),
    constraint ck_problem_embeddings_generation_key_bounded check (length(btrim(generation_key)) between 1 and 128),
    constraint ck_problem_embeddings_provider_bounded check (length(btrim(provider)) between 1 and 64),
    constraint ck_problem_embeddings_model_bounded check (length(btrim(model)) between 1 and 128),
    constraint ck_problem_embeddings_generation_version_positive check (generation_version > 0),
    constraint ck_problem_embeddings_dimensions_positive check (dimensions > 0),
    constraint ck_problem_embeddings_fingerprint_sha256 check (input_fingerprint ~ '^[0-9a-f]{64}$'),
    constraint ck_problem_embeddings_policy_version_bounded check (length(btrim(policy_version)) between 1 and 128),
    constraint ck_problem_embeddings_vector_dimension check (vector_dims(embedding) = dimensions),
    constraint ck_problem_embeddings_active_superseded check (
        (active and superseded_at is null)
        or (not active and superseded_at is not null)
    ),
    constraint ck_problem_embeddings_superseded_after_created check (
        superseded_at is null
        or superseded_at >= created_at
    ),
    constraint ck_problem_embeddings_updated_after_created check (updated_at >= created_at)
);

create unique index uq_problem_embeddings_active_generation
    on problem_embeddings (user_id, problem_session_id, generation_key, generation_version)
    where active;

create unique index uq_problem_embeddings_fingerprint
    on problem_embeddings (user_id, problem_session_id, generation_key, generation_version, input_fingerprint);

create index idx_problem_embeddings_user_generation_active
    on problem_embeddings (user_id, generation_key, generation_version, active, problem_session_id);
