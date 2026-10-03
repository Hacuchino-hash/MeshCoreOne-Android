# WP-002 fresh-cache verification follow-up

**Same supervised dependent draft, not another WP or a gate/readiness receipt.**
Repair base/head before this follow-up:
`d5ee909c6faf9b33440e8249ec0cb774644a381b`; parent remains
`1214b5cf009705232907790d49d966823e7f410a`. During that repair, source,
manifest/policy, the `draft-WP-002-af6f5c6e-1214b5cf` reservation and managed
PR3/base were unchanged. That broad reservation has since been released;
the later narrow classifier grant is recorded below.
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

## Linux AAPT2 publication follow-up

Repair base is `ac227f7e372cb7e20881ba0c9a2c1887695732bb`, under the new
four-file grant `draft-WP-002-linux-af6f5c6e-ac227f7e`; the earlier broad lease
remains released. Same owner/worktree/branch/draft PR3 and architecture parent;
no CI worktree, settings, source pin, human review or activation is changed.

Root metadata previously pinned only the Windows JAR and POM for
`com.android.tools.build:aapt2:9.4.1-15978811`. Linux AGP selects the different
`linux` classifier, whose absence would fail strict verification. The official
Google Maven Linux JAR was independently downloaded and inspected:

- **2,385,035 bytes**, SHA-256
  `f5bebd466ecf14d341fd465f2756a16d86052f29eb4532003d5ff7bcffd08de5`.
- Downloaded publisher `.sha1` matched
  `6ad07b566daaefa1c2721255637ec137c93580a6`; this is supplementary provenance,
  not a SHA-256 publisher/signature or legal-approval claim.
- JAR CRC and `aapt2` member checked without extracting or executing the binary.
  The existing Windows SHA-256 and POM, catalog/lock versions and strict
  verification settings remain unchanged.

Actual Gradle **strict detached resolution on Windows** also passed. A private
temporary init script registered `verifyLinuxAapt2Publication` only for this
invocation; it is not a new product task or committed source file. Its action
required strict mode, resolved the exact nontransitive
`com.android.tools.build:aapt2:9.4.1-15978811:linux@jar`, and asserted the single
JAR's filename, size and SHA-256 above. The essential resolver input was:

```groovy
def dependency = root.dependencies.create(
    'com.android.tools.build:aapt2:9.4.1-15978811:linux@jar')
def classifier = root.configurations.detachedConfiguration(dependency)
classifier.transitive = false
def archives = classifier.resolve()
```

```powershell
& .\android\scaffold\invoke-gradle.ps1 -ConstrainedMemory -BuildHeap 640m `
  -BuildMetaspace 512m -BuildCodeCache 96m -GradleArguments @(
    'verifyLinuxAapt2Publication', '--init-script', $privatePublicationProbe,
    '--dependency-verification', 'strict', '--no-build-cache', '--quiet')

python -m unittest discover -s .\android\scaffold -p test_*.py -v
```

The root/build cache was reused for this bounded probe; the Linux classifier
was initially absent and **not copied** from the independent publisher download.
Gradle downloaded and verified it into the private cache. No metadata or lock
generation flags were used. This is actual publication-resolution evidence,
**not Linux execution/build, native loading, signing or device/HIL proof**.

All **15 metadata/environment assertions passed**, 0 failures/errors/skips:
the original twelve plus three classifier regressions that preserve both host
pins, reject missing Linux evidence despite a valid Windows pin, and reject
substitution of Windows bytes for the Linux checksum. Only the four granted
tracked files change. Root metadata SHA-256 is now
`0637626aeec4b9285081027de49229683e790bfd522f0c3fe64e638a1e85ba23`;
standalone metadata and all dependency locks are unchanged. Actual Linux CI
execution remains the separately owned future evidence, not an inferred pass.
