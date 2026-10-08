# ADR-005: Fast local checks do not gate feature-branch publication

## Decision

Feature branches and pull requests may be published from every processor and host.
The pre-push fast check is an optimization, not a publication gate. If the
configured runner is unavailable or fails, the hook exits successfully, records
the exact commit and tree as `verification-pending`, and prints
`UNVERIFIED CANDIDATE — merge verification still required`. It never emits a
passing receipt for that candidate.

Merge-to-main remains fail closed. Trusted-base merge handling accepts only a
result bound to the exact candidate commit and tree, containing all seven stages
(`python`, `preflight`, `verify`, `standalone`, `assemble`, `lint`, `inspect`),
the actual Python/JDK/Android SDK/Gradle versions and package metadata, declared
tasks, positive discovery counts, zero failures/errors/skips, and hashed outputs.
A later commit invalidates an earlier result.

## Native adapters

Contributors may provide an explicit JSON toolchain state with an adapter command.
The adapter can use a native macOS ARM64 toolchain; it need not use archives
downloaded by the x64 provisioner. It must execute the exact committed candidate
and produce the common result schema. Candidate runner changes are ordinary
reviewed branch changes and cannot authorize themselves: merge handling validates
the result with trusted default-branch policy.

Hosted CI, an x64 relay, Docker, Rosetta, and remote devices are not prerequisites
for publishing a branch. None substitutes for merge verification or protected
human gates.
