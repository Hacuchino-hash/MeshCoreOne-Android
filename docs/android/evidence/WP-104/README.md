# WP-104 executable command foundation

Repository: `cbattlegear/MeshCoreOne-Android`; owner: `protocol-porter` role,
implemented by the coordinating worktree under the user's autonomous coding and
merge authorization. Lease: `autonomous-WP-104-050ac690`.
Merged prerequisite/base: `050ac6909c65af8e3fc63e5aaf44b1d240f1d8ac`.
Reference: `db14559b39d32322b06477c6ae676112f583db50`.
Manifest: `78a22920beaa5899f9618806b5cd2b27d50399a9b29b4d8dbd79f755717ec746`.
Policy: `56bdc53548bc86d631245795dfa38b4fc86048e0e7cbe1c7d5695879b035b42a`.
The associated PR and CI artifacts bind the final committed head rather than
introducing an evidence-only commit/rerun cycle.

## Actual local Windows assertions

Using the existing private checksum-pinned Temurin 21.0.12.1, Gradle 9.8.0,
Kotlin 2.3.20, SDK 37.2/build-tools 37.0.0, strict dependency verification,
one worker and the documented constrained-memory launcher:

```powershell
& .\android\scaffold\invoke-gradle.ps1 -ConstrainedMemory -BuildHeap 640m `
  -GradleArguments @(':core:protocol:test', '--tests', `
    'com.meshcoreone.android.core.protocol.command.*', `
    '--dependency-verification', 'strict')

& .\android\scaffold\invoke-gradle.ps1 -ConstrainedMemory -BuildHeap 640m `
  -GradleArguments @(':core:protocol:test', 'validateModuleGraph', `
    '--dependency-verification', 'strict')

& .\android\scaffold\invoke-gradle.ps1 -ConstrainedMemory -BuildHeap 768m `
  -GradleArguments @(':app:assembleDebug', '--dependency-verification', 'strict')

python .\android\scaffold\inspect_apk.py
python .\tools\android-port\portmap.py
git diff --check
```

Each command passed. The targeted suite executed **101 cases**; the full protocol
suite executed **185 cases** (those 101 plus all 84 existing primitive cases),
with **zero failures, errors or skipped cases**. The production dependency graph
checked all 30 modules, with no forbidden edge or Android leakage into JVM code.

| Suite | Actual cases |
| --- | --- |
| `PacketReferenceTest` | 31 independent original Python byte fixtures |
| `NewCommandsTest` | 19 source declarations/families, including all three mode rows |
| `V115CommandsTest` | 14 original channel-data and persisted-scope cases |
| `ContactAndRadioCommandsTest` | 12 contact/radio/date/TX-power cases; legacy session scenario is encoder-only evidence |
| `PacketBoundaryTest` | 25 additional checks, including all 256 hash modes, 4,096 telemetry combinations, every binary/anonymous subtype, clamp boundaries and all 63 method declarations |
| Existing WP-101 suites | 84 |

Expected Python bytes are read from the **immutable original Git object** for
`MeshCore/Tests/MeshCoreTests/Fixtures/PythonReferenceBytes.swift`, not generated
by candidate Kotlin. The suite requires exactly 31 builder fixtures. Direct
source-edge tests use explicit source-layout bytes; the extra `"scope"` digest
was independently computed with Python `hashlib.sha256(b"scope")`:
`5f161c9149882e0e10124bc5dd5c11f0fbe8ec452edd52bcec76b01e9252cb33`.

The actual debug APK is **28,740,465 bytes**, SHA-256
`ab168c8efa89893a64bdad565024088cbc5f1bb75aebed2795f1919698f68105`.
Inspection confirmed `com.meshcoreone.android.debug`, min31/target37, launcher,
unchanged permission surface, original GPL/MIT/Apache notices and absence of
testing/Room-verification classes. The existing
`controller.apk_alignment.inspect_alignment` separately executed pinned
`zipalign -c -P 16 4` and parsed all four native ELF files: each actual `PT_LOAD`
alignment is 16,384. This is **static alignment**, not device/native-runtime
certification. The ordinary scaffold APK inspector alone intentionally makes no
16KB verification claim.

The trusted manifest validator passed with the original 1,866 inputs,
65 work packages, 185 dependency edges and eight preserved human gates.
Traceability derived every new source header. Swift/source tests/resources and
license originals remain unchanged against the reference pin.

## Acceptance limits

`WP-104-behavior` and `WP-104-boundaries` have real command-level evidence.
`WP-104-source-test-parity` remains partial until the 51 cross-component
declarations and the original legacy asynchronous session scenario execute.
See [the native adaptation record](../../deviations/WP-104.md).
No physical transport, radio acknowledgment, iOS simulator, bidirectional
backup restore, release signing, or full-app parity is claimed by these tests.
The existing normal Windows/Linux protocol workflow runs the complete suite;
hosted head-bound results belong in the PR, not in a fabricated local report.
