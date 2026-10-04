# WP-005 execution evidence

**Implementation is present; strict Android tests/lint currently await one
coordinator-owned artifact checksum.** This is not a passing Android parity
receipt. Resource assembly and actual nonzero Python assertions passed; no
unexecuted Kotlin suite, device, iOS runtime, signing or legal acceptance is claimed.

Repository `cbattlegear/MeshCoreOne-Android`; owner `localization-engineer`;
branch `cbattlegear-redesigned-fishstick`. The initial head was the actually
merged foundation `979dd73b2fc3cb5cbeea07b1c809f5c9283289ee`; only this feature
worktree was subsequently fast-forwarded to merged main
`050ac6909c65af8e3fc63e5aaf44b1d240f1d8ac`. Own changes were preserved.
The immutable Swift reference remains
`db14559b39d32322b06477c6ae676112f583db50`.

The explicit kickoff reservation is `autonomous-WP-005-979dd73b`, bound to CLI
session `d474b8d5-32c2-4222-b958-2a8d50afe48c` and native/project alias
`09e1cb9b-b5f4-4e71-9aef-8340ac40e55f`. No fabricated live-ledger or independent
gate receipt is asserted. All delivered writes stay in the six leased WP paths.

## Implemented shape and real source accountability

| Measured item | Count |
| --- | --- |
| Parsed localization inputs | 169: 120 strings, 48 dictionaries, one catalog |
| Distinct source keys | 2,406 |
| Exact billing exclusions | 32 |
| Canonical generated resources | 2,374: 2,363 strings, 11 plurals |
| Source string kinds per effective locale | 2,061 plain, 302 formatted |
| Effective locales | 12 |
| Generated source/XML/oracle/key-map files | 63 |
| Regional original family | 59 subdivisions by 12 locales (51 US, 8 AU) |
| Named summaries/phrases | Two Tools summaries, seven shortcut phrases |
| Real missing translations | Seven Italian shortcut phrases only |
| Explicit native source corrections | One blob/value/argument-bound Chinese numeric typo |

The full [key map](../../generated/l10n-key-map.json) records immutable
input blobs, SHA256/size/record counts, original keys, forward/reverse domains,
typed argument positions, quantities, actual locale sources/fallbacks, correction
fingerprints and every generated output digest. Filename/header counts are not
asserted as behavioral parity.

The 65 Python cases exercise grammar/escaping/comments/encoding/continuations,
typed/positional/named/percent/finite-float formats, safe plist and all-six-quantity
shapes, actual xcstrings, complete pinned-source counts, collisions, real fallback,
determinism, output/pin/correction drift and strict JUnit evidence failures.
The original Kotlin airtime and regional families plus additional all-resource
round-trip/quantity/long/CJK/format boundaries are implemented, but have not yet
executed because strict verification stops before test compilation.

## Actual commands and outcomes

The documented isolated launcher uses installed JDK21.0.12.1, compileSDK37.2,
build-tools37.0.0, one worker, in-process Kotlin, 640/768MiB build heap,
512MiB metaspace and 256MiB test heap. Its independent preflight passed and
stripped non-allowlisted variables. Private caches/SDK/JDK paths and keys are
not committed. No host PATH, memory, license or main-checkout setting was changed.

| Exact command/task selection | Actual result |
| --- | --- |
| `python .\tools\android-port\l10n_convert.py --write --self-test` | **Passed**; 65 discovered/passed, zero failures/errors/skips; all 63 outputs regenerated. |
| `python .\tools\android-port\l10n_convert.py --check` | **Passed**; no source/output/key-map drift. |
| `python .\tools\android-port\portmap.py` | **Passed** after correcting generated headers to the validator's canonical `GeneratedFrom` disposition. All 12 generated Kotlin files declare actual pinned inputs; no conflicting handwritten/generated disposition. |
| `python .\tools\android-port\controller\verification_config.py --check` | **Passed** on the initial foundation; trusted overlay intact, no feature acceptance inferred. |
| `python .\tools\android-port\controller\validate.py` | **Passed**; 1,866 pinned files, 65 WPs and 185 edges unchanged. |
| `python .\tools\android-port\controller\test_runner.py --quiet` | **Passed**; 161 discovered/run/passed, zero failed/errors/skipped (controller fixtures, not Android/iOS parity). |
| `python .\tools\android-port\controller\ci.py python` | **Passed** on merged main after the candidate header repair: manifest/traceability/notices, 165 controller and 15 scaffold Python cases, no failure/error/skip. Private output directory, no toolchain provisioning or gate publication. |
| `:core:l10n:dependencies --write-locks --dependency-verification strict` | **Passed**; module-local lock created from existing pinned aliases. |
| `:core:l10n:assembleDebug --write-locks --dependency-verification strict` | **Passed**, including real AAPT resource processing and production Kotlin compilation; initial empty `androidApis` state initialized. |
| `:core:l10n:assembleDebug --dependency-verification strict` | **Passed** on merged main `050ac690`, including the final canonical-header repair and all 65 converter tests rerun; no lock/checksum update flags. Final constrained heap was 640m. |
| `:core:l10n:testDebugUnitTest :core:l10n:verifyL10nTests --dependency-verification strict --no-build-cache --rerun-tasks` | **Failed** before discovery: existing `annotation-jvm:1.7.0` module is pinned but its JAR is absent from shared verification metadata. |
| `:core:l10n:lintDebug --dependency-verification strict` | **Failed** on the same JAR while generating the unit-test lint model; no lint pass claimed. |

Local Gradle invocations use
`.\android\scaffold\invoke-gradle.ps1 -ConstrainedMemory -BuildHeap <640m or 768m> -GradleArguments @(...)`;
initial assembly/lint use 768m and tests/final assembly use 640m. All include strict verification.
Initialization's first assembly without a lock update correctly failed on
missing empty `androidApis` lock state. That local lock issue was fixed by actual
assembly, not by deleting/disabling strict locks. Content-identical shared
bookkeeping rewrites were cleaned up; no shared lock diff is delivered.
One final-assembly attempt hit shared-host Windows commit pressure (`WinError1455`)
while spawning the read-only source checker. No host/pagefile/process setting
was changed. Normalizing the provenance map's repeated locale paths into explicit
input indexes reduced it from 8.43MB to 3.08MB; all origin/blob/fallback assertions
remain. The lower-heap strict retry passed.

## Precise shared checksum handoff

The existing `mesh.android.robolectric`/AndroidX test aliases resolve
`androidx.annotation:annotation-jvm:1.7.0`. Shared metadata contains its `.module`
but not `annotation-jvm-1.7.0.jar`. The actual Google-resolved JAR's SHA256 is
`e36b8e4b8393a4adc74e3d4ab22ad5a36396f0cea2e40b5734eae14937dfd224`.

The exact command, failure and component/artifact XML were reported on the
coordinator's [existing PR6 comment](https://github.com/cbattlegear/MeshCoreOne-Android/pull/6#issuecomment-5975309039).
The merged main was checked: the JAR checksum is still absent. WP-005 does not
edit unleased shared verification metadata, change dependency versions, add
unrelated dependencies, trust whole artifact groups or disable verification.
Once the shared owner supplies the exact checksum, rerun the same module suites
and lint, record discovered XML counts, and repair this same candidate.

The first hosted Android run stopped earlier in the Python stage because the
initial generator notice was not the validator's canonical `GeneratedFrom`
header. This was an in-scope candidate bug, not a reason to weaken the validator.
The generator and native helper disposition were corrected, outputs regenerated,
and the actual trusted traceability validator passed. A new regression case runs
that validator against every generated Kotlin file. This same normal PR is
repaired rather than replaced.

## Boundaries and historical diagnostic

The initial Chinese `%@`/integer conflict was not silently skipped. The coordinator
explicitly authorized its one native numeric correction; original Chinese copy,
Swift and source SHA remain unchanged. The original diagnostic is retained as
[historical evidence](blocked-source.json), not current blocked-source status.
See [deviations](../../deviations/WP-005.md) and
[case-family accountability](../../localization/cases.json).

No iOS formatter/oracle, actual Android device/system language UX, radio/HIL,
release signing, external provider/model/privacy/legal admission or full-feature
completion is claimed. No billing, Google services, analytics, account, updater
or APK-installation implementation was introduced.
