# Local checks

Run the relevant declared checks for the change. A full local check remains
available for broad/unclassifiable Android, dependency/toolchain/build-graph, or
release changes:

```text
python <git-common-dir>/hooks/meshcore-local/check.py --commit HEAD --stages all
```

The installed pre-push hook may attempt the full check for each pushed commit as
a development optimization. An
unavailable or failing local runner does not block the push: it records
`verification-pending` and prints an explicit unverified-candidate warning.
Do not describe that status as a pass.

Native hosts may supply `--toolchain-state <json>`. The JSON has
`{"schema_version":1,"adapter":["executable", "..."]}`; the adapter receives
`--repo`, `--commit`, `--tree`, `--stages`, and `--output`. Merge verification
validates the result against the exact candidate using trusted-base policy:

```text
python tools/android-port/local/check.py --commit <sha> --validate-result <result.json>
```

The result must identify the host, exact commit/tree, selected declared tasks,
positive discovery where tests apply, zero failures/errors/skips, and output
digests. Full results use the declared Python, preflight, consolidated scaffold,
and inspection stages only when full verification is required. Verified caches,
task reuse, and parallel workers are allowed; cache
warmth is never correctness evidence. Dependency/toolchain changes additionally
require a targeted cold-cache dependency audit with strict locks/checksums.
