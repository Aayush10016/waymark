create index idx_problem_sessions_solved_user_updated
    on problem_sessions (user_id, updated_at desc, id desc)
    where deleted_at is null
        and status = 'SOLVED';

create index idx_problem_sessions_technologies_gin
    on problem_sessions using gin (technologies)
    where deleted_at is null
        and status = 'SOLVED';

create index idx_problem_sessions_error_phrases_gin
    on problem_sessions using gin (error_phrases)
    where deleted_at is null
        and status = 'SOLVED';
