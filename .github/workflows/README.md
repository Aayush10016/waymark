# GitHub Actions

ENG-010 defines the initial required quality workflow.

## Required check

- `quality / format-lint-test-contract-build-security`

Repository branch protection for `main` must require the check above before merge.

## Action pinning

Every action in workflow YAML must use an immutable commit SHA with a comment identifying the upstream tag used for review. Floating tags such as `@v4` are not allowed.

