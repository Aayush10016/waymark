create index idx_background_jobs_retry_claim
    on background_jobs (next_attempt_at, created_at, id)
    where state = 'FAILED_RETRYABLE';
