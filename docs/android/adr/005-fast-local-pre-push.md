# ADR-005: Fast local checks do not gate feature-branch publication

## Decision

Feature branches and pull requests may be published from every processor and host.
The pre-push fast check is an optimization, not a publication gate. If the
configured runner is unavailable or fails, the hook exits successfully, records
the exact commit and tree as `verification-pending`, and prints
`UNVERIFIED CANDIDATE — merge verification still required`. It never emits a
passing receipt for that candidate.

Merge-to-main remains fail closed, but verification is change-scoped. Full
repository verification is required only for broad or unclassifiable Android
changes, dependency/toolchain/build-graph changes, release candidates, or an
explicit maintainer request. Other candidates run the relevant declared jobs and
must report positive discovery and zero failures/errors/skips for selected work.
A later commit invalidates earlier evidence.

Path/job scoping, normal task reuse, parallel workers, and verified caches are
allowed. Strict Gradle dependency locks, checksums, and
`--dependency-verification strict` remain mandatory. Cache warmth is not
correctness evidence; a cold-cache dependency audit targets dependency/toolchain
changes rather than every ordinary pull request.

## Native adapters

Contributors may provide an explicit JSON toolchain state with an adapter command.
The adapter can use a native macOS ARM64 toolchain; it need not use archives
downloaded by the x64 provisioner. It must execute the exact committed candidate
and produce the applicable change-scoped result schema. Candidate runner changes are ordinary
reviewed branch changes and cannot authorize themselves: merge handling validates
the result with trusted default-branch policy.

Hosted CI, an x64 relay, Docker, Rosetta, and remote devices are not prerequisites
for publishing a branch. None substitutes for merge verification or protected
human gates.
