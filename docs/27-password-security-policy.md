# Password Security Policy

Status: ENG-012 baseline.

## Purpose

Trail uses a versioned Argon2id password hashing policy so password hashes can be upgraded after successful sign-in without forcing password resets.

## Current policy

| Field | Value |
|---|---|
| Algorithm metadata | `ARGON2ID` |
| Policy version | `1` |
| Spring Security encoder | `Argon2PasswordEncoder` |
| Salt length | `16` bytes |
| Hash length | `32` bytes |
| Memory cost | `19,456` KiB |
| Iterations | `2` |
| Parallelism | `1` |
| Canonical parameter string | `m=19456,t=2,p=1,salt=16,hash=32` |
| Maximum raw password input | `1,024` UTF-8 bytes |

The memory and iteration values are selected as the v1.1 baseline after local benchmarking on the development workstation class available during ENG-012.

Benchmark run:

- Date: 2026-07-10.
- Runtime: Java 17.
- Method: two warmup hashes, then five measured hashes through `Argon2idPasswordPolicy`.
- Timings: `101 ms`, `103 ms`, `115 ms`, `134 ms`, `148 ms`.
- Median: `115 ms`.

Production hardware must be rebenchmarked before beta traffic; changing any parameter requires incrementing the policy version and rehashing on successful login.

## Rehash strategy

After a password is successfully verified, Trail must rehash and persist the new hash when any of the following is true:

- stored algorithm metadata is not `ARGON2ID`;
- stored policy version is not the current version;
- stored canonical parameter string differs from the current policy;
- Spring Security reports the encoded hash should be upgraded.

Rehashing happens only after a valid password is proven. Failed login attempts must not reveal whether a rehash would be required.

## Input size rule

Trail rejects password inputs above `1,024` UTF-8 bytes before hashing. The cap protects the authentication boundary from avoidable CPU and memory amplification while still supporting long passphrases.

## Tests

ENG-012 is covered by `Argon2idPasswordPolicyTest`, which verifies:

- current algorithm/version/parameter metadata;
- maximum UTF-8 input size behavior;
- Argon2id encode and match behavior;
- metadata-based rehash decisions;
- Spring Security upgrade detection for weaker Argon2id parameters;
- invalid metadata rejection.
