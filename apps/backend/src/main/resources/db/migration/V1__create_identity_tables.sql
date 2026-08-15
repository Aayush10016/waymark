create table users (
    id uuid primary key,
    email_normalized text not null,
    email_display text not null,
    password_hash text not null,
    password_hash_algorithm text not null,
    password_hash_parameters text not null,
    password_hash_version smallint not null,
    status text not null,
    failed_login_count integer not null default 0,
    locked_until_at timestamptz,
    email_verified_at timestamptz,
    delete_requested_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uq_users_email_normalized unique (email_normalized),
    constraint ck_users_email_normalized_format check (
        email_normalized = lower(email_normalized)
        and email_normalized = btrim(email_normalized)
        and length(email_normalized) between 3 and 320
    ),
    constraint ck_users_email_display_not_blank check (length(btrim(email_display)) > 0),
    constraint ck_users_password_hash_not_blank check (length(btrim(password_hash)) > 0),
    constraint ck_users_password_hash_algorithm check (password_hash_algorithm = 'ARGON2ID'),
    constraint ck_users_password_hash_parameters_not_blank check (length(btrim(password_hash_parameters)) > 0),
    constraint ck_users_password_hash_version_positive check (password_hash_version > 0),
    constraint ck_users_status check (
        status in (
            'PENDING_VERIFICATION',
            'ACTIVE',
            'LOCKED',
            'DELETION_REQUESTED',
            'DELETED'
        )
    ),
    constraint ck_users_failed_login_count_non_negative check (failed_login_count >= 0),
    constraint ck_users_updated_after_created check (updated_at >= created_at),
    constraint ck_users_delete_requested_for_terminal_status check (
        delete_requested_at is null
        or status in ('DELETION_REQUESTED', 'DELETED')
    )
);

create index idx_users_active_lock
    on users (locked_until_at)
    where locked_until_at is not null
        and status in ('ACTIVE', 'LOCKED');

create index idx_users_delete_requested
    on users (delete_requested_at)
    where delete_requested_at is not null;

create table auth_sessions (
    id uuid primary key,
    session_family_id uuid not null,
    user_id uuid not null references users (id) on delete cascade,
    refresh_token_hash bytea not null,
    refresh_token_hash_algorithm text not null,
    refresh_rotation_counter integer not null default 0,
    client_type text not null,
    client_version text,
    created_at timestamptz not null default now(),
    last_used_at timestamptz,
    expires_at timestamptz not null,
    revoked_at timestamptz,
    reuse_detected_at timestamptz,
    constraint uq_auth_sessions_session_family_id unique (session_family_id),
    constraint ck_auth_sessions_refresh_token_hash_length check (octet_length(refresh_token_hash) = 32),
    constraint ck_auth_sessions_refresh_token_hash_algorithm check (refresh_token_hash_algorithm = 'SHA-256'),
    constraint ck_auth_sessions_refresh_rotation_counter_non_negative check (refresh_rotation_counter >= 0),
    constraint ck_auth_sessions_client_type check (client_type in ('CHROME_EXTENSION')),
    constraint ck_auth_sessions_client_version_not_blank check (
        client_version is null
        or length(btrim(client_version)) > 0
    ),
    constraint ck_auth_sessions_expiry_after_created check (expires_at > created_at),
    constraint ck_auth_sessions_last_used_after_created check (
        last_used_at is null
        or last_used_at >= created_at
    ),
    constraint ck_auth_sessions_revoked_after_created check (
        revoked_at is null
        or revoked_at >= created_at
    ),
    constraint ck_auth_sessions_reuse_detected_after_created check (
        reuse_detected_at is null
        or reuse_detected_at >= created_at
    )
);

create unique index uq_auth_sessions_active_refresh_token_hash
    on auth_sessions (refresh_token_hash)
    where revoked_at is null;

create index idx_auth_sessions_user_active_expiry
    on auth_sessions (user_id, expires_at)
    where revoked_at is null
        and reuse_detected_at is null;

create index idx_auth_sessions_user_created
    on auth_sessions (user_id, created_at desc);

create table email_action_tokens (
    id uuid primary key,
    user_id uuid not null references users (id) on delete cascade,
    purpose text not null,
    token_hash bytea not null,
    token_hash_algorithm text not null,
    expires_at timestamptz not null,
    consumed_at timestamptz,
    created_at timestamptz not null default now(),
    constraint ck_email_action_tokens_purpose check (purpose in ('VERIFY_EMAIL', 'RESET_PASSWORD')),
    constraint ck_email_action_tokens_hash_length check (octet_length(token_hash) = 32),
    constraint ck_email_action_tokens_hash_algorithm check (token_hash_algorithm = 'SHA-256'),
    constraint ck_email_action_tokens_expiry_after_created check (expires_at > created_at),
    constraint ck_email_action_tokens_consumed_after_created check (
        consumed_at is null
        or consumed_at >= created_at
    )
);

create unique index uq_email_action_tokens_active_token_hash
    on email_action_tokens (token_hash)
    where consumed_at is null;

create index idx_email_action_tokens_active_expiry
    on email_action_tokens (expires_at)
    where consumed_at is null;

create index idx_email_action_tokens_user_purpose_active
    on email_action_tokens (user_id, purpose, expires_at)
    where consumed_at is null;
