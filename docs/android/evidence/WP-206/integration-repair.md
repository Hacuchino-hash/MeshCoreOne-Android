# WP-206 supervised connectivity integration repair

Repository: `cbattlegear/MeshCoreOne-Android`. Owner: `connectivity-engineer`.
Base: `372fbc5866305e045025472ba555e7941873000f` (merged PR #58).
Source remains `db14559b39d32322b06477c6ae676112f583db50`.
Manifest semantic SHA-256:
`78a22920beaa5899f9618806b5cd2b27d50399a9b29b4d8dbd79f755717ec746`.
Policy revision:
`56bdc53548bc86d631245795dfa38b4fc86048e0e7cbe1c7d5695879b035b42a`.
This is `controller.gates.policy_revision`: canonical digest of the manifest
digest, source pin and policy semantics excluding operational stop/start fields.
The unchanged committed automation policy has canonical JSON digest
`3a96956793753399f3fe9bfb06927df3ad70998478731f3f29889a57407bac20`
and raw Git-byte digest
`e9bb06975d07cd6c1757f1b28aa20c6596a5fa2957524ef82c7f4f22e2f979cd`.
These are distinct algorithms/inputs, not a policy revision mismatch.
CLI session: `12b3e244-81e4-4706-b0ae-8d59d35ed718`.
App session: `28750e92-d46a-4261-aabd-d82dd7aeb799`.
Managed branch: `cbattlegear-wp-206-connectivity-integration-repair`.

The coordinator explicitly granted the user-authorized supervised exclusive
repair lease for `android/core/connectivity/`,
`docs/android/deviations/WP-206.md` and `docs/android/evidence/WP-206/`.
Verified durable receipt:
`~/meshcoreone-work/leases/wp206-connectivity-repair/receipt.json`,
identity `wp206-repair-12b3e244-372fbc58-20261007`. Its session, branch, worktree,
base, manifest and both committed policy digests match the trusted base.
The user subsequently issued an exclusive supervised amendment for exactly
`docs/android/evidence/WP-211/collect_evidence.py` and
`docs/android/evidence/WP-211/test_collect_evidence.py`, assigned to this repair
session to resolve the actual full-gate producer failure. All changed files are
within these five authorized paths. The operational shared-ledger alias is
`supervised-WP206-connectivity-repair-372fbc`.
This is manual supervised authorization, not dispatcher activation or formal
acceptance of the original unleased external contribution.

Merged prerequisites verified as ancestors of the base:
WP-205 PR #18, `e303e8795ab4e7cc45e02d791e6a2829085c2288`;
WP-207 runtime seams PR #55, `38f6f825bc94ef1d379108dc61900caeb4a3a2e6`.

## Reproduced failure and source behavior

The installed official local runner was invoked nonquietly in the live visible
terminal from this worktree:

```powershell
$gate = Join-Path (git rev-parse --path-format=absolute --git-path hooks) 'meshcore-local\check.py'
python $gate --distribution Ubuntu-22.04 --stages preflight,verify
```

Preflight passed. Verification failed at `:core:connectivity:testDebugUnitTest`:
174 discovered, 171 passed, 3 failed, 0 errors, 0 skipped. Actual JUnit XML
identified these failures:

| Class | Method | Incorrect expectation |
| --- | --- | --- |
| `ConnectionManagerAuthFailureRoutingTest` | `opportunistic reconnect surfaces authenticationFailed thrown by connect` | `BleTransportException` thrown by health check; actual `null` |
| `ConnectionManagerAuthFailureRoutingTest` | `opportunistic reconnect stays silent for non-auth connect failures` | `ConnectionError.DeviceNotFound` thrown by health check; actual `null` |
| `ConnectionManagerBLEHealthTest` | `detects stale state when connectionState is ready but BLE disconnected` | `ConnectionError.DeviceNotFound` thrown by health check; actual `null` |

Baseline reports:
`~/meshcoreone-work/local-checks/run-zpn1PImt/gradle-verify.log`.
Raw XML at the time of reproduction:
`~/meshcoreone-work/local-checks/repos/1059cdbfd240fcfc/android/core/connectivity/build/test-results/testDebugUnitTest/`.
That managed snapshot is reused by subsequent checks. The original two failed
suite XML documents were preserved verbatim from the tool's captured output as
session artifact `wp206-baseline-failed-junit-output.txt` (SHA-256
`17950fe2d2795b95ee4ebf056e533f7e052c86862784979745b5d7946ce68ae9`).
The baseline Gradle log was also preserved as `wp206-baseline-gradle-verify.log`
(SHA-256 `67f264ec9982950a9d7f331f70e94ba9b96f5c00ffc8b130247206eec0eef710`).

The merged runtime's `reportHealthReconnectFailure` reports
`RuntimeDiagnostic.Failure("health.reconnect", cause)` instead of throwing.
This matches pinned `ConnectionManager.swift::attemptOpportunisticReconnect`,
`ConnectionManager+BLE.swift::checkBLEConnectionHealth`, and the original
`ConnectionManagerAuthFailureRoutingTests` / `ConnectionManagerBLEHealthTests`.
The inspected source and original test files have no drift from the pin.

The repaired assertions require normal completion, exactly one typed diagnostic,
authentication throwable identity, the recovery callback, disconnected state,
preserved intent and exactly one stale-state loss callback. Removing the
catch-and-ignore health helper also makes unexpected exceptions fail the other
health cases directly. No cases, bindings, goldens or production contracts were
removed or changed.

## Verification

`python .\tools\android-port\portmap.py` and
`python .\tools\android-port\controller\validate.py` passed in the live visible
terminal before the repair. Actual repair iterations are recorded below; they
never substitute for the complete successful exact-commit submission gate.

The first repaired full run was:

```powershell
python $gate --distribution Ubuntu-22.04 --worktree
```

Reports: `~/meshcoreone-work/local-checks/run-fs5dWutf/`. Python passed:
231 controller cases, 15 scaffold cases and 14 installed local-tool cases,
with no failures/errors/skips. Preflight passed. Actual connectivity XML contains
15 suites and 174 discovered/passed cases, 0 failures/errors/skips. The original
three failing cases are included, not excluded or retried individually.

That full attempt failed: verification stopped at
`:core:data:retainDeviceSettingsRoomEvidence`. The then-out-of-scope WP-211
`collect_evidence.py::invocation` rejects the actual local invocation JSON with
`ValueError: Hosted invocation lacks actual run identity`. The data build reuses
`meshCliInvocationFile`, whose local runner record honestly has no hosted identity.
The services and data suites completed with 306 and 381 passing cases respectively,
0 failures/errors/skips, but this is not a full root or runtime result.
The precise producer handoff was sent to the coordinator, who subsequently
granted the two-file amendment above. No hosted identity was fabricated,
collector validation skipped or push allowed on the strength of this partial result.

The unchanged eight auth-routing and 26 BLE-health original-case bindings and
test method identities were compared directly between the base Git blobs and
the repair commit. All match exactly.

Before the producer amendment, this visible iteration ran:

```powershell
python $gate --distribution Ubuntu-22.04 --worktree --stages standalone,assemble,lint,inspect
```

Standalone, assemble and lint passed. Inspect could not copy
`android/build/reports/scaffold/test-discovery.tsv` because the earlier failed
root verification never generated it. This partial iteration is not substituted
for the required complete cycle and does not establish overall inspection success.

## Authorized producer interoperability repair

The WP-211 reader now accepts only the standard typed schema-1/Linux/verify
record with an explicitly present `identity: null` as local-only execution.
It preserves null hosted run/attempt IDs, the current Git HEAD, the complete
actual invocation, and the existing unauthenticated local-reader authority.
Missing identities, malformed hosted dictionaries, boolean/nonpositive/missing
hosted IDs and malformed bindings fail rather than falling back to local.

Hosted records retain the exact root binding, source/manifest/policy and current
HEAD. Their actual base must be a full SHA naming an existing commit, inherit
the frozen integration baseline, and be an ancestor of the actual HEAD; it is
no longer required to equal the first producer PR's historical base. The
historical receipt/integration baselines, reviewed producer freeze and case
floors remain unchanged. Validation checks the retained actual invocation
itself and requires any supplied invocation to match it completely; external
input cannot conceal a changed or missing retained record. Trusted root CI
still independently binds its own actual event/run/head/base.

```powershell
python .\docs\android\evidence\WP-211\test_collect_evidence.py -v
```

Passed in the visible terminal: 46 reader/producer regressions, 0 failures,
errors or skips. The added cases use a real temporary Git history for baseline,
later ancestor, unrelated, missing and non-commit base validation. Synthetic
reader fixtures never count as native service/Room evidence. Existing malformed,
zero, failed, skipped, duplicate, hash/freeze and exact partition guards remain.
No runtime, business code, data build, shared runner, source pin, policy or
producer freeze was changed.

The required submission verification is the installed full committed-head gate:

```powershell
python $gate --distribution Ubuntu-22.04 --commit HEAD
```

All seven default stages (`python,preflight,verify,standalone,assemble,lint,inspect`)
must pass before a remote update. Its immutable HEAD, complete result and retained
run logs belong in the follow-up PR; the normal installed pre-push hook remains
enabled and independently executes the complete committed-tip gate.

## Evidence boundary

This repairs WP-206 source-test-parity and boundary assertions against the merged
runtime. It does not claim full WP-206 acceptance, physical-device/OEM/radio
behavior, iOS execution, hosted/cold-cache CI, licensing, signing or release gates.
