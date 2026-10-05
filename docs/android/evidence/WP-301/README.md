# WP-301 evidence

**Implementation candidate; native verification has failed, not passed.** The
separate local Java-slot receipt is pending. No device, hardware, signing,
formal licensing/gate, iOS/UIKit, complete-app or merged-WP acceptance is claimed.

Repository: `cbattlegear/MeshCoreOne-Android`; owner `design-system-engineer`;
lease `autonomous-WP-301-2cf00464`; managed branch
`cbattlegear-native-themes-and-identity`. App session
`0273a25a-e3b9-4950-9464-0a586b9a8b93`, active native alias
`74633b51-98c1-4c1c-b17f-2b90760edf1f`. Initial clean full HEAD/main:
`2cf00464950e1fb9aae0dd913402eb3e12dc0044`.

Frozen source `db14559b39d32322b06477c6ae676112f583db50`, tree
`8918fdc604341e6996a68c88f6bb1c02b9c2f87e`; manifest
`78a22920beaa5899f9618806b5cd2b27d50399a9b29b4d8dbd79f755717ec746`;
policy revision `56bdc53548bc86d631245795dfa38b4fc86048e0e7cbe1c7d5695879b035b42a`.
All 65 IDs, 185 WP edges, eight gates and canonical pending states are unchanged.
Actual prerequisites were merged before this candidate: WP-003
[#4](https://github.com/cbattlegear/MeshCoreOne-Android/pull/4) and its
[#16](https://github.com/cbattlegear/MeshCoreOne-Android/pull/16) evidence follow-up,
WP-005 [#8](https://github.com/cbattlegear/MeshCoreOne-Android/pull/8), and WP-204
[#21](https://github.com/cbattlegear/MeshCoreOne-Android/pull/21), whose merge is
the initial full HEAD.

## Ownership and boundaries

[Authorization](authorization.json) acknowledges the actual coordinator-delivered
write receipt and its exact three-file graph amendment. The initial five
canonical paths are designsystem, its converter, the icon map, this evidence
directory and the WP deviation document. The separately granted graph files
only add the actual preference consumer edge, preserve every old guard, add real
Gradle fixtures and describe the process lifetime.

No app/feature/UI/manifest/localization/catalog/controller/workflow/global lock/
checksum policy was changed. Proposed transitive consumer locks remain unowned
until actual strict per-module/configuration graph evidence and an exact
coordinator amendment admit their deltas. No new agents or owning worktrees
were launched.

The first real both-host attempt reached resource parsing and rejected absent
strict state in the newly declared owned local lock. [Lock seed](lock-seed.json)
records the byte-identical migration of the initial vetted, Gradle-generated
`core-designsystem.lockfile` into the owned module. It adds no speculative new
component/configuration/version record and does not rewrite the shared file.
Current dependency additions still require actual resolution.

## Executed read-only and Python commands

| Exact command | Actual result |
| --- | --- |
| `python .\tools\android-port\controller\verification_config.py --check` | Passed on clean initial full HEAD; canonical overlay unchanged. |
| `python .\tools\android-port\controller\validate.py` | Passed on clean initial full HEAD; original ownership and source pins valid. |
| `python .\tools\android-port\portmap.py` | Passed on clean initial HEAD and after the new source/header work; headers are traceability, not parity. |
| `python .\android\scaffold\sync_notices.py` | Passed on clean initial full HEAD; no shared notices regenerated. |
| `python .\tools\android-port\theme_convert.py --write` | Passed; only declared owned outputs written from frozen Git inputs. |
| `python .\tools\android-port\theme_convert.py --check --self-test` | Passed: **21 discovered/passed, zero failures/errors/skips**; ten themes, 44 named colors, 89 primary inputs, 12 exact recovery locales. |
| `python .\docs\android\evidence\WP-301\collect_evidence.py --inventory --self-test` | Passed: **8 evidence-reader tests**, 58 source families, 55 mapped and three explicitly removed billing-only families; static inventory, not native execution. |
| `python .\docs\android\evidence\WP-301\verify_packaging.py --normalize --self-test` | Passed: **4 notice-reader tests** and exact bytes/hashes of all four new frozen notices; APK inspection not yet run. |
| `git --no-pager diff --check` | Passed after the inventory/reader repair. |

## Actual initial hosted failure

[Run 37284231819, attempt 1](https://github.com/cbattlegear/MeshCoreOne-Android/actions/runs/37284231819)
is bound to base `2cf00464950e1fb9aae0dd913402eb3e12dc0044` and head
`bc2f107d43a118b38f68e53635b4083e0c7a3977`. Both Linux and Windows provisioned
the exact publisher-pinned JDK/SDK archives, passed independent readiness and
the real Python stage, then failed at
`:core:designsystem:parseDebugLocalResources`: `androidApis` is strict-locked
but the new module-local file has no lock state. This is not a passing
native build/test or consumer dependency-delta report.

[Immutable failure record](hosted-initial/run.json) retains all 13 Linux and
14 Windows extracted artifact files verbatim under deterministic gzip, with
original file size/SHA-256, run/attempt/head/base and reported archive identities.
Full Gradle failure logs are preserved, not replaced by a summary. Independent
standalone, APK, lint and alignment steps were skipped after the failure;
the fail-closed aggregator failed too. These are explicit incomplete results.

The converter source map is
`android\core\designsystem\src\main\assets\theme-source-map.json`. The separate
test oracle is a verbatim copy of pinned raw asset JSON and its actual Git blob
IDs, not expected colors generated by candidate Kotlin. All 59 resource
dispositions and all 20 primary production sources are accounted for.

## Native verification contract

The module declares `verifyThemeConversion`, `verifyThemeTests`,
`verifyThemeNotices`, `verifyThemePackaging`, `resolveThemeDependencies` and
`inspectThemeConsumerGraphs`. Its real
`testDebugUnitTest`/case collector is attached to the existing root
`verifyScaffoldTests`; the generic current-head module JUnit collector retains
the complete suite on both hosted operating systems.

Mandatory original loop families retain 606/407 identities, all 38 effective
theme/scheme/contrast states, 34 non-default outgoing/hashtag states, 102 category
states, 3,800 glyph states, and 50 hue-stability identities. Actual runtime
parameter/assertion counters must agree with the frozen source-derived family
size; zero, missing, narrowed, skipped, failed or malformed evidence fails.

The module tests use actual file-backed merged preference APIs and actual
native Compose/Material resources. They include observation/restore, concurrent
selection, typed corruption/I/O/locked errors, cancellation, process/consumer
close isolation, stable composition across theme changes, 49dp targets,
200% font, CJK/RTL/resize and platform contrast/motion listener disposal.
API31 and the already-admitted SDK37 Robolectric coordinate are declared; neither
is physical-device evidence.

Native preview captures are required for all 38 effective theme/light-dark/contrast
combinations plus expanded 200% CJK/RTL (39 captures). Complete PNG bytes, dimensions and SHA-256 are retained
in raw JUnit `system-out`, so the existing bundle transports actual evidence
without a controller/workflow amendment. The reader validates and reconstructs
those bytes into the module's owned report directory; no expected golden is
created or updated.

Native commands, current-head raw JUnit, PNGs, root verification, APK/notice/
alignment inspection, all lint reports and hosted run/attempt identities remain
**not run/pending** until their actual execution. This document will record
their exact results rather than treating declared tasks or static mappings as
success.

## Deviations and acceptance

See [WP-301 deviations](../../deviations/WP-301.md). The original System
high-contrast white-outgoing ratio **4.080562510192782** and legacy category
glyph exceptions remain explicit raw source policies; accessible native Material
foregrounds are separate adaptations, never lowered original expectations.
All four palette/app GPL/MIT notices are generated from actual pinned inputs.
Source symbol meanings map to original GPLv3 vector geometry; no SF artwork,
fonts, dynamic wallpaper colors, billing or new backend behavior is included.

`WP-301-behavior`, `WP-301-boundaries` and `WP-301-source-test-parity` require
actual passing evidence and independent review. They are not marked accepted by
this candidate document.
