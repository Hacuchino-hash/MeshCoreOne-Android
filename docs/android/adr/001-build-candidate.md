# ADR 001: stable build candidate and provisioning

**Status:** Proposed; WP-001 supervised dependent draft, pending human review.
WP-000 is not merged. This is not toolchain activation or a completion receipt.

## Decision

Carry the following **verified candidate**, rather than independently selecting
"latest" components. WP-002 owns the wrapper/catalog and proves the actual scaffold;
WP-003 proves reproducible isolated hosted execution.

| Component | Candidate | Evidence boundary |
| --- | --- | --- |
| Build JDK | Temurin 21.0.12.1+1 | Coordinator's isolated Windows probe; Java/Kotlin bytecode target remains 17 |
| AGP | 9.4.1 | Published Google Maven POM; AGP 9.4 supports major API 37 and requires Gradle >=9.6.0 |
| Gradle | 9.8.0, binary distribution | Publisher SHA-256 below; actual probe used this distribution |
| Kotlin plugins | 2.3.20 | JVM/Compose compiler plugins aligned; explicit plugin classpath worked in the probe |
| Compose BOM | 2026.03.01 | Published Google Maven BOM and successful template build |
| Compile SDK | Major 37, minor 2 | `compileSdk { version = release(37) { minorApiLevel = 2 } }` |
| Runtime SDK bounds | `minSdk = 31`, `targetSdk = 37` | No preview codename or minor-API runtime prerequisite |
| Application identity | `com.meshcoreone.android`; debug suffix `.debug` | Product identity, not the probe's `.sdkprobe` package |

Gradle distribution checksum:

```text
bafd5ce9cfaea0fbccfdc8439a1ac42fbd4cd9c89dc9a988228d8a2639a58e6c
```

AGP's POM declares Kotlin Gradle plugin/stdlib **2.2.10** as its baseline.
That is not proof that arbitrary Kotlin overrides work: **2.3.20** is the explicit
override exercised by this probe. WP-002 must record the effective compiler/plugin
resolution in the multi-module project. Use AGP 9 built-in Kotlin in Android modules;
do **not** also apply `org.jetbrains.kotlin.android`. Pure JVM modules apply
`org.jetbrains.kotlin.jvm`; the Compose compiler plugin is version-aligned.

## Exact provisioning assumptions

Command-line tools **23.0** use the Android CLI (`--no-metrics`); `sdkmanager`
is a deprecated wrapper, not an environment-readiness result. Google's
`repository2-3.xml`, not the older repository index, supplied these stable IDs:

| Package ID | Observed revision | Scaffold use |
| --- | --- | --- |
| `platforms;android-37.2` | 1.0.0 | Selected compile platform |
| `platforms;android-37.0` | 2.0.0 | Observed major-37 baseline; not a substitute for the selected minor |
| `build-tools;37.0.0` | 37.0.0 | Installed candidate, to pin explicitly |
| `platform-tools` | 37.0.1 | Observed tooling; not device-test evidence |

Do not request an assumed `platforms;android-37` package. Provision verified
archives/packages, checksums and SDK-license acceptance explicitly on each host.
Use portable per-process `JAVA_HOME` and SDK environment variables, never private
installation paths or global host settings. The official empty-activity template
initially used target 36 / AGP 9.0.1 and was explicitly upgraded for this proof.

The coordinator's read-only `environment.json` records successful
`:app:assembleDebug`, `:app:testDebugUnitTest` (**2 discovered/passed, 0 failed/skipped**)
and `:app:lintDebug`. It is an isolated vendor-template proof, not MeshCore product
or device evidence; WP-001 did not rerun it. Shared Windows commit headroom required
constrained worker/JVM/JIT settings. Measure suitable CI limits; do not copy those
local VM flags into production or blindly into hosted runners.

## Still unproven

First-party releases identify **Room 2.8.5** and **KSP 2.3.12 (KSP2)** as stable
candidates, not an approved linked/compatible tuple. WP-002 must exercise Room
code generation, schema export and its convention plugin with this exact Kotlin/AGP
combination. WP-201/202 still own the actual schema, transactions and migration
behavior. Linux builds, Windows/Linux wrapper parity, multi-module configuration,
test-runner discovery, other library pins and native 16 KB alignment remain
WP-002/003 and later module evidence. No alpha dependency is a release prerequisite.

## First-party verification

Read on 2026-10-03: [AGP compatibility](https://developer.android.com/build/releases/agp-9-4-0-release-notes),
[AGP 9.4.1 POM](https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/9.4.1/gradle-9.4.1.pom),
[built-in Kotlin](https://developer.android.com/build/migrate-to-built-in-kotlin),
[Kotlin release history](https://kotlinlang.org/docs/releases.html),
[Compose BOM POM](https://dl.google.com/dl/android/maven2/androidx/compose/compose-bom/2026.03.01/compose-bom-2026.03.01.pom),
[Gradle checksum](https://services.gradle.org/distributions/gradle-9.8.0-bin.zip.sha256),
[SDK repository](https://dl.google.com/android/repository/repository2-3.xml),
[Room](https://developer.android.com/jetpack/androidx/releases/room),
[KSP stable release](https://github.com/google/ksp/releases/tag/2.3.12).
Published versions/checksums were checked; publication alone does not prove compatibility.
