# Local checks

Run the configured seven-stage fast check with:

```text
python <git-common-dir>/hooks/meshcore-local/check.py --commit HEAD --stages all
```

The installed pre-push hook attempts the same check for each pushed commit. An
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

The result must identify the host and actual Python/JDK/Android SDK/Gradle
versions/packages, all seven ordered stages and declared tasks, positive discovery
counts, zero failures/errors/skips, and SHA-256 output digests. Reduced, malformed,
failed, stale, or zero-test results block merge.
