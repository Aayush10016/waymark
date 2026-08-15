# Similarity Golden Corpus v1

ENG-072 establishes a deterministic regression corpus for Trail's hybrid recommendation ranker.

## Cohorts

- Positive: same engineering problem and compatible final solution.
- Same technology: overlapping stack without problem-specific evidence.
- Same error, different cause: same visible error with a meaningfully different root cause.
- Unrelated: no meaningful engineering similarity.

## Precision gate

The v1 precision gate is `0.90` over the golden corpus. A recommendation is a false positive when a non-positive cohort is returned by the hybrid ranker. The corpus is intentionally small for MVP safety and should grow as real, consented examples are reviewed.

## Scope

This report validates deterministic retrieval behavior only. It does not evaluate AI generation, embeddings provider quality, UI copy, or Ghost Replay.
