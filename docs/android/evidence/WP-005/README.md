# WP-005 execution evidence

**Actual strict local resource assembly, 25 Android tests and lint pass.**
New-head hosted CI and independent review are separate evidence, not presumed
from these results. System/device language UX, iOS runtime, hardware, signing,
provider/license approval and full-feature completion are not claimed.

Repository `cbattlegear/MeshCoreOne-Android`; owner `localization-engineer`;
branch `cbattlegear-redesigned-fishstick`. The initial head was merged foundation
`979dd73b2fc3cb5cbeea07b1c809f5c9283289ee`; only this feature worktree was
fast-forwarded to verified merged main
`050ac6909c65af8e3fc63e5aaf44b1d240f1d8ac`, preserving own work.
Swift reference remains `db14559b39d32322b06477c6ae676112f583db50`.

Kickoff reservation `autonomous-WP-005-979dd73b` binds CLI session
`d474b8d5-32c2-4222-b958-2a8d50afe48c` and native/project alias
`09e1cb9b-b5f4-4e71-9aef-8340ac40e55f`. The coordinator explicitly amended it
for `android/gradle/verification-metadata.xml`'s preexisting annotation JAR
checksum, then for `android/app/src/main/AndroidManifest.xml` solely to add the
application localeConfig attribute. The delivered checksum is now read-only;
the later exact `.github/workflows/android-ci.yml` grant adds only the validated
post-verify feature-evidence retention step. No other shared-file permission,
live-ledger or gate receipt is asserted.

## Measured implementation and original families

| Item | Actual count |
| --- | --- |
| Parsed inputs | 169: 120 strings, 48 dictionaries, one catalog |
| Distinct source keys / exact billing exclusions | 2,406 / 32 |
| Canonical resources | 2,374: 2,363 strings, 11 plurals |
| String kinds per effective locale | 2,061 plain, 302 formatted |
| Locales / generated files | 12 / 63 |
| Regional family | 59 codes by 12 locales: 51 US and 8 AU |
| Named framework phrases | Two Tools summaries and seven shortcut phrases |
| Real missing translations | Seven Italian shortcut phrases only |
| Native source corrections | One exact blob/value/argument-bound Chinese numeric token |

The [full key map](../../generated/l10n-key-map.json) contains source input
blobs/SHA256/size/record counts, original and forward/reverse domain keys, typed
positions, quantities, effective locale input indexes/fallbacks, correction
fingerprints and output hashes. File/header counts are not parity.

| Real JUnit suite | Discovered / passed |
| --- | --- |
| `AirtimePercentLabelTest` | 2 / 2 |
| `RegionalSubdivisionLocalizationTest` | 3 / 3 |
| `LocalizationResourceTest` | 7 / 7 |
| `L10nFormattingTest` | 13 / 13 |
| **Total** | **25 / 25; zero failed/errors/skipped** |

The two original source families execute against compiled resources: literal
English airtime percent and all 59 subdivision names in every locale. Additional
cases compare all 28,356 effective plain/formatted string values to original
decoded Apple text, all 11 plural resources across locales/boundary vectors,
second-argument selection, Long extrema, CJK/long German, named phrases/fallback,
locale XML/legacy IDs, finite locale-decimal floats and explicit failures.
Assertions are not replaced by filename/header accounting or the old 47 scaffold
cases. [Case map](../../localization/cases.json);
[sanitized source-bound result evidence](local-verification.json).

## Exact commands and actual outcomes

Gradle selections use the documented isolated
`.\android\scaffold\invoke-gradle.ps1 -ConstrainedMemory -BuildHeap 640m -GradleArguments @(...)`,
except actual app assembly at 768m. Installed JDK21.0.12.1, Gradle9.8.0,
AGP9.4.1, compile37.2/build-tools37.0.0, bytecode17 and simulated SDK31;
one worker/in-process Kotlin, 512MiB build metaspace and 256MiB test heap.
Preflight passed and removed non-allowlisted variables. No private paths/cache
binaries/debug keys or host PATH/pagefile/memory changes are committed.

| Exact command/selection | Result |
| --- | --- |
| `python .\tools\android-port\l10n_convert.py --write --self-test` | **Passed**, 74 discovered/passed, zero failed/errors/skipped, 63 regenerated outputs; original expectations unchanged. |
| `python .\tools\android-port\l10n_convert.py --check --self-test` | **Passed**, same 74 cases, no source/output/key-map/correction drift. |
| `python .\tools\android-port\portmap.py` | **Passed**, canonical generated-input dispositions, no conflicting headers. |
| `python .\tools\android-port\controller\verification_config.py --check` | **Passed**, unchanged trusted overlay; no feature acceptance inferred. |
| `python .\tools\android-port\controller\validate.py` | **Passed**, 1,866 pinned files, 65 WPs and 185 edges unchanged. |
| `python .\tools\android-port\controller\ci.py python` | **Passed**, exact hosted Python stage on merged main: 165 controller plus 15 scaffold cases, no failed/error/skipped cases; no provisioning/gate publication. |
| `:core:l10n:assembleDebug :core:l10n:testDebugUnitTest :core:l10n:verifyL10nTests --dependency-verification strict --no-build-cache --rerun-tasks` | **Passed**, fresh actual production/resource compilation, all 25 JUnit cases and strict discovery. |
| `:core:l10n:lintDebug --dependency-verification strict --no-build-cache --rerun-tasks` | **Passed**, zero errors; 486 source-copy/native warnings, no global disable/baseline. |
| `:app:assembleDebug validateModuleGraph --dependency-verification strict` | **Passed**, actual app assembly and 30-module boundary graph. |
| `python .\android\scaffold\inspect_apk.py` | **Passed**, real debug APK package/min/target/notices/fixture-absence contract; not native/device/release proof. |
| `:app:assembleDebug --dependency-verification strict --no-build-cache` | **Passed** after the exact application opt-in; no other app manifest change. |
| `:core:l10n:testDebugUnitTest :core:l10n:verifyL10nTests --dependency-verification strict --no-build-cache --rerun-tasks` | **Passed** after opt-in; unchanged 25 cases/four suites, zero failed/errors/skipped; 65 converter assertions rerun. |
| `:app:lintDebug --dependency-verification strict --no-build-cache` | **Passed** after opt-in; no lint/verification disable or baseline. |
| Pinned `aapt2 dump xmltree` (APK manifest and packaged locale XML), `aapt2 dump resources` | **Passed**, exact manifest-to-XML resource ID match, all twelve tags and twelve actual source string variants. [Static APK proof](apk-locale-registration.json); not physical Settings UI. |

Final lint warnings: MissingQuantity44, PluralsCandidate56, TypographyDashes44,
TypographyEllipsis329 and Typos13. Pinned copy is not rewritten or retranslated
to silence warnings; required plural `other` fallback and every existing category
are preserved and tested. Exactly the seven genuinely absent Italian phrases
carry per-resource MissingTranslation acknowledgment; a regression verifies the
marked set equals the actual missing-key set. No tests/goldens/policy are lowered.

## Publisher-bound shared artifact amendment

The first strict local and both-host hosted verify stages correctly failed on
the existing test alias dependency `androidx.annotation:annotation-jvm:1.7.0`
JAR, whose `.module` was pinned but binary was not. The coordinator explicitly
authorized the exact three-line artifact addition in the preexisting component.

Google publisher URL:
`https://dl.google.com/dl/android/maven2/androidx/annotation/annotation-jvm/1.7.0/annotation-jvm-1.7.0.module`.
Its independently fetched SHA256 exactly matches committed
`07ce60c377ab94e47c8c902589b9776030064fd1a7e4d5a01a38d700e35e5db4`.
Both variant entries declare the 55,232-byte JAR SHA256
`e36b8e4b8393a4adc74e3d4ab22ad5a36396f0cea2e40b5734eae14937dfd224`.
Fresh first-party and isolated Gradle-resolved bytes match hash and size.
No dependency version, repository, broad artifact trust, verification setting or
unrelated checksum is changed. This is provenance, not legal approval.

## Repairs and exact app opt-in handoff

The initial Chinese signature failure is retained as
[historical evidence](blocked-source.json), not current status. Its one native
correction was subsequently explicitly authorized and retains raw copy/source.

The first hosted Python stage exposed noncanonical generated notices. The
generator now emits actual `GeneratedFrom` inputs and a regression executes the
trusted validator. The same PR is repaired, not replaced.

Actual Kotlin execution exposed test-fixture errors (a printf `$` escape,
hard-coded merged resource package, and selecting only plain strings for the
long-source threshold). These were corrected without changing source expectations:
the true longest formatted German source still exceeds 300 characters.
The unchanged signed plural vectors exposed Android's `Int.MIN_VALUE` native
absolute-value overflow. Magnitude/category selection is now safe while the
original signed argument is displayed; the entire family passes.

Initial empty `androidApis` locking required an actual assembly `--write-locks`
initialization, not strict-lock deletion. Content-identical shared bookkeeping
was cleaned up. One shared-host WinError1455 attempt was retried at a lower private
heap; normalized input-index provenance reduced map size from 8.43MB to 3.08MB
without removing origins or weakening assertions. No host/process settings changed.

**Actual APK inspection confirmed and then verified an AGP9.4 app opt-in.**
The app merger explicitly rejects library `android:localeConfig`; all twelve
locale XML/resources/properties exist, but system registration was absent.
The kickoff's app-write handoff was followed: the coordinator received the exact
one-attribute literal on the existing application:
`android:localeConfig="@xml/l10n_locales"`.
The coordinator then explicitly granted only that application attribute. The
one-line integration is applied and ordinary strict app assembly/module cases/app
lint pass. Pinned AAPT37.0.0 reads the compiled APK: localeConfig points to
`xml/l10n_locales`, the APK-internal XML has en/de/es/fr/it/ko/nl/pl/pt-PT/ru/uk/
zh-Hans, and a canonical widget string contains all twelve compiled source variants.
APK-internal entry identifiers use ZIP's slash convention, not host paths.
The ignored library attribute remains removed. Application ID/min31/target37,
permissions/providers/components/backup/label/signing behavior remain unchanged.
This is static compiled-APK verification only, not physical Settings UI.

## Portable localization assertion retention

An independent coordinator replay of both final001978 scaffold bundles found
the legacy raw root/standalone reports but no raw `core:l10n` XML. The actual
25 cases executed successfully; the issue was missing independently replayable
feature proof. No failed-test claim or passing fallback is made.

The exact authorized workflow step after existing `verify` calls:

```powershell
python tools/android-port/l10n_convert.py --check `
  --verify-android-tests android/core/l10n/build/test-results/testDebugUnitTest `
  --copy-android-junit (Join-Path (Join-Path $env:ANDROID_CI_OUTPUT 'l10n') 'junit')
```

Only the existing bundle gains `l10n/junit/TEST-*.xml` (four complete verbatim
reports) and `l10n/junit/summary.json` (typed 25-case counts, class inventory,
head/source/run/attempt, executed-source hashes and report hashes/sizes).
The unchanged inspect collector adds them to its original artifact index and
SHA256SUMS; permissions, checkout credentials, pinned actions/toolchain,
upload prefixes/layout, mandatory legacy root47/raw data and root aggregator
are unchanged. Artifacts are data and are never executed.

Nine added positive/negative Python cases verify byte-identical BOM/CRLF raw
copies and replayable counts, idempotence, missing/zero/skipped/malformed/duplicate/
unknown/stale data, unsafe/repository/link/source-overlap paths, changed-after-
validation bytes, immutable stale destination and invalid CI bindings. All25
Kotlin cases and every expected string/quantity remain unchanged. Fresh strict
actual module tests pass, and the real copy CLI retained all4reports/25cases
byte-for-byte in a private absolute directory.

Precommit `ci.py python` correctly rejected the edited workflow against immutable
HEAD; this was not bypassed. The committed-head stage and both new hosted
bundles must be inspected/replayed before claiming final portable CI proof.

See [deviations](../../deviations/WP-005.md) for native adaptation boundaries.
No new account/billing/analytics/GMS/model/updater/APK-installer code exists.
Actual system language UX remains WP-407; full widget/shortcut execution and
message translation remain their respective WPs.
