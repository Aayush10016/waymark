create table ai_derivations (
    id uuid primary key,
    user_id uuid not null,
    problem_session_id uuid not null,
    derivation_type text not null,
    provider text not null,
    model text not null,
    prompt_key text not null,
    prompt_version integer not null,
    prompt_hash text not null,
    output_schema_hash text not null,
    policy_version text not null,
    input_fingerprint text not null,
    status text not null,
    safe_failure_code text,
    generated_summary text,
    technologies text[] not null default array[]::text[],
    frameworks text[] not null default array[]::text[],
    error_phrases text[] not null default array[]::text[],
    problem_type text,
    input_tokens bigint not null default 0,
    output_tokens bigint not null default 0,
    total_tokens bigint not null default 0,
    estimated_cost numeric(12,6) not null default 0,
    cost_currency text not null default 'USD',
    completed_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint fk_ai_derivations_problem_session
        foreign key (user_id, problem_session_id)
        references problem_sessions (user_id, id)
        on delete cascade,
    constraint ck_ai_derivations_type check (
        derivation_type in ('PROBLEM_SESSION_ENRICHMENT')
    ),
    constraint ck_ai_derivations_provider_bounded check (length(btrim(provider)) between 1 and 64),
    constraint ck_ai_derivations_model_bounded check (length(btrim(model)) between 1 and 128),
    constraint ck_ai_derivations_prompt_key_bounded check (length(btrim(prompt_key)) between 1 and 128),
    constraint ck_ai_derivations_prompt_version_positive check (prompt_version > 0),
    constraint ck_ai_derivations_prompt_hash_sha256 check (prompt_hash ~ '^[0-9a-f]{64}$'),
    constraint ck_ai_derivations_output_schema_hash_sha256 check (output_schema_hash ~ '^[0-9a-f]{64}$'),
    constraint ck_ai_derivations_policy_version_bounded check (length(btrim(policy_version)) between 1 and 128),
    constraint ck_ai_derivations_input_fingerprint_sha256 check (input_fingerprint ~ '^[0-9a-f]{64}$'),
    constraint ck_ai_derivations_status check (
        status in (
            'SUCCEEDED',
            'FAILED_RETRYABLE',
            'FAILED_TERMINAL',
            'REFUSED',
            'INVALID_OUTPUT'
        )
    ),
    constraint ck_ai_derivations_failure_code_bounded check (
        safe_failure_code is null
        or length(btrim(safe_failure_code)) between 1 and 128
    ),
    constraint ck_ai_derivations_failure_code_status check (
        (status = 'SUCCEEDED' and safe_failure_code is null)
        or (status <> 'SUCCEEDED' and safe_failure_code is not null)
    ),
    constraint ck_ai_derivations_summary_bounded check (
        generated_summary is null
        or length(generated_summary) <= 4000
    ),
    constraint ck_ai_derivations_problem_type_bounded check (
        problem_type is null
        or length(btrim(problem_type)) between 1 and 64
    ),
    constraint ck_ai_derivations_array_bounds check (
        cardinality(technologies) <= 32
        and cardinality(frameworks) <= 32
        and cardinality(error_phrases) <= 32
    ),
    constraint ck_ai_derivations_tokens_non_negative check (
        input_tokens >= 0
        and output_tokens >= 0
        and total_tokens >= 0
        and total_tokens >= input_tokens
        and total_tokens >= output_tokens
    ),
    constraint ck_ai_derivations_cost_non_negative check (estimated_cost >= 0),
    constraint ck_ai_derivations_currency_bounded check (cost_currency ~ '^[A-Z]{3}$'),
    constraint ck_ai_derivations_completed_state check (
        completed_at is null
        or status in (
            'SUCCEEDED',
            'FAILED_TERMINAL',
            'REFUSED',
            'INVALID_OUTPUT'
        )
    ),
    constraint ck_ai_derivations_updated_after_created check (updated_at >= created_at)
);

create unique index uq_ai_derivations_success_fingerprint
    on ai_derivations (
        user_id,
        problem_session_id,
        derivation_type,
        provider,
        model,
        prompt_key,
        prompt_version,
        prompt_hash,
        output_schema_hash,
        policy_version,
        input_fingerprint
    )
    where status = 'SUCCEEDED';

create index idx_ai_derivations_problem_type_created
    on ai_derivations (user_id, problem_session_id, derivation_type, created_at desc, id desc);

create index idx_ai_derivations_status_created
    on ai_derivations (status, created_at desc, id desc);
