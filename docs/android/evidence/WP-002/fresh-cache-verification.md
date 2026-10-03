# WP-002 fresh-cache verification follow-up

**Same supervised dependent draft, not another WP or a gate/readiness receipt.**
Repair base/head before this follow-up:
`d5ee909c6faf9b33440e8249ec0cb774644a381b`; parent remains
`1214b5cf009705232907790d49d966823e7f410a`. Source, manifest/policy, active
`draft-WP-002-af6f5c6e-1214b5cf` reservation and managed PR3/base are unchanged.
The new non-amended commit SHA is reported in the handoff.

## Defect and inspected publication inputs

The coordinator's different cache failed composite `:build-logic:convention:compileTestKotlin`
at `detachedConfiguration2` / `testCompileClasspath`: the JUnit BOM5.10.1 POM
was unverified. Root metadata had only its `.module`; the included metadata
already pinned both. Root execution governs composite verification, even for
included-build tasks, so standalone or warmed `.module` resolution was insufficient.

The first new empty user/project-cache run additionally failed strict plugin
classpath resolution on `kotlinx-coroutines-bom-1.8.0.pom`. Comparing the complete
root/included inventories identified five exact missing POM/module variants.
All were already in standalone metadata; [publisher provenance](verification-publications.json)
records independently downloaded bytes/checksums. No dependency version or
lock changed, including the build tooling's preexisting milestone/transitive BOMs.

Maven Central `.sha256` files matched the downloaded JUnit/coroutines bytes.
Guava parent33.4.0-jre has no `.sha256` (HTTP404); its downloaded SHA-256 matches
the existing included pin and its publisher `.sha1` also matches. Gradle still
checks SHA-256; SHA-1 is supplementary provenance, not an accepted substitute.
No dependency signatures or legal approval are claimed.

Root metadata before/after SHA-256:
`fac805005dbc77f23f601cac520001da6b80441d5ae985a0a9f63ada0e3d2883` /
`54748e59265fa76289ff3d1a8d4154ed1ecf65e9528b1206978b7bdb1ef68e0d`.
Standalone metadata is unchanged at
`0d0edc5230b9cef3a37c3db0e83dee3cfd21e5bf3851a17df18e3dab6a0de5b1`.
Neither strict run rewrote verification metadata or dependency locks.

## Actually executed resolver topologies

Windows/JDK21.0.12.1/SDK37.2 and the existing credential/environment allowlist were
used. Each proof started with **both GRADLE_USER_HOME and project cache absent**.
The two positive proof caches were distinct from each other, the initial failing
cache, the preparation cache and the coordinator's cache. No binary caches were
copied, no standalone warm-up preceded the composite proof, and wrapper downloads
occurred in both. Existing licensed SDK/JDK installations were inputs, not caches
or Linux evidence.

Commands below ran at repository root with the explicit environment from the
build guide. `$freshProjectCache` denotes the respective new private directory,
not a committed absolute installation path.

```powershell
& .\android\scaffold\invoke-gradle.ps1 -ConstrainedMemory -BuildHeap 768m `
  -BuildMetaspace 512m -BuildCodeCache 96m -GradleArguments @(
    'verifyScaffoldTests', 'verifyRoomSchema', 'validateModuleGraph',
    'runtimeDependencyInventory', 'resolveScaffoldDependencies',
    '--dependency-verification', 'strict', '--no-build-cache', '--rerun-tasks',
    '--project-cache-dir', $freshProjectCache, '--quiet')

& .\android\scaffold\invoke-gradle.ps1 -ConstrainedMemory -BuildLogic -BuildHeap 640m `
  -BuildMetaspace 512m -BuildCodeCache 96m -GradleArguments @(
    ':convention:test', '--dependency-verification', 'strict', '--no-build-cache',
    '--rerun-tasks', '--project-cache-dir', $freshProjectCache, '--quiet')

python -m unittest discover -s .\android\scaffold -p test_*.py -v
```

| Invocation | Observed outcome |
| --- | --- |
| Fresh composite root, exact required tasks above | Exit0; conventions31 + contracts4 + app10 + actual Room2 = **47 discovered/run/passed**, 0 failed/errors/skipped; schema/graph/runtime/component checks passed |
| Independent fresh standalone build-logic | Exit0; **31 discovered/run/passed**, 0 failed/errors/skipped |
| Python metadata/environment regressions | **12 discovered/run/passed**, 0 failed/errors/skipped; eight metadata cases plus four isolation cases |
| Existing controller test runner | **111 discovered/run/passed**, 0 failed/errors/skipped |
| Frozen validator/generator check | Exit0; unchanged 1,866 inputs / 65 WPs / 185 edges / eight gates; no refresh |
| Actual APK/notices reinspection | Exit0; existing APK identity/checksum/notices unchanged; no new device/release claim |

Metadata regressions check both JUnit publication formats and every standalone
artifact's root coverage, and reject missing POM, missing classpath BOM, changed
checksum, duplicate component and disabled metadata verification. Pinned versions,
strict mode, assertions and signature settings are not weakened; no trusted-artifact
wildcards, skip flags, broad fallback or verification-generation command is used.

Local diagnostic logs are private session artifacts; this document commits no
host/user/cache path or credential. The original preparation evidence did not
prove fresh-cache portability. This follow-up proves only the two actual Windows
resolver topologies, not Linux/CI, publisher-signature/legal acceptance, real
device/HIL, complete native16KB runtime behavior or any human/feature/release gate.
All parents remain unmerged and automation remains paused/off.
