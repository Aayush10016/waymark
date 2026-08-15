alter table auth_sessions
    drop constraint if exists uq_auth_sessions_session_family_id;

create index if not exists idx_auth_sessions_session_family
    on auth_sessions (session_family_id, created_at desc);
