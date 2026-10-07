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
base, manifest and both committed policy digests match the trusted base; all
changed files are within its three write paths.
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
Raw XML:
`~/meshcoreone-work/local-checks/repos/1059cdbfd240fcfc/android/core/connectivity/build/test-results/testDebugUnitTest/`.

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
terminal before the repair. Full repair verification is pending; this document
will be updated with actual outcomes before submission.

The first repaired full run was:

```powershell
python $gate --distribution Ubuntu-22.04 --worktree
```

Reports: `~/meshcoreone-work/local-checks/run-fs5dWutf/`. Python passed:
231 controller cases, 15 scaffold cases and 14 installed local-tool cases,
with no failures/errors/skips. Preflight passed. Actual connectivity XML contains
15 suites and 174 discovered/passed cases, 0 failures/errors/skips. The original
three failing cases are included, not excluded or retried individually.

The complete gate is **BLOCKED**, not passed: verification stopped at
`:core:data:retainDeviceSettingsRoomEvidence`. The out-of-scope WP-211
`collect_evidence.py::invocation` rejects the actual local invocation JSON with
`ValueError: Hosted invocation lacks actual run identity`. The data build reuses
`meshCliInvocationFile`, whose local runner record honestly has no hosted identity.
The services and data suites completed with 306 and 381 passing cases respectively,
0 failures/errors/skips, but this is not a full root or runtime result.
The precise producer handoff was sent to the coordinator. No hosted identity is
fabricated, collector validation skipped, shared producer edited or push allowed
on the strength of this partial result.

## Evidence boundary

This repairs WP-206 source-test-parity and boundary assertions against the merged
runtime. It does not claim full WP-206 acceptance, physical-device/OEM/radio
behavior, iOS execution, hosted/cold-cache CI, licensing, signing or release gates.
