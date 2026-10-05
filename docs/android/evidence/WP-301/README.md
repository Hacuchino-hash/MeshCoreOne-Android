# WP-301 evidence

**Implementation candidate; final current-head native/root proof is pending.** The
earlier bounded local execution discovered/passed **87 tests**, zero
failures/errors/skips, and retained **39 native PNG renders**. Those captures
include the historical scroll-state limitation described below; they are not
current proof for the review repairs. The latest completed repair run failed
one of 92 cases on each host. Its identified external test-query correction
has been adopted preserving authorship, and actual approved generated consumer
locks are now persisted. Complete current-head Windows/Linux/root/APK evidence
remains pending. No device, hardware, signing,
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

No app/feature/UI/manifest/localization/catalog/checksum policy was changed.
The exact ten-consumer locks, one auxiliary candidate workflow and frozen
three-file executor carry are separate amendments in the receipt; all other
shared paths remain unowned. No new agents or owning worktrees were launched.

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
| `:core:designsystem:resolveThemeDependencies --write-locks --dependency-verification strict` | Passed through the documented constrained launcher; actual owned module-local lock and 1,575 module/configuration/component rows generated, shared records unchanged. |
| `:core:designsystem:inspectThemeConsumerGraphs --dependency-verification strict` | Failed closed as expected on unadmitted shared constraints; actual ten modules/80 configurations/12,704 selected/failure rows retained. Not a passing graph. |
| `:core:designsystem:verifyThemeTests --dependency-verification strict` | Passed after genuine host/render/listener fixes: **87 discovered/passed**, zero failures/errors/skips; all 58 original families accounted for, 39 actual native PNGs. |
| `:core:designsystem:verifyThemeTests :core:designsystem:lintDebug --dependency-verification strict` | Passed; complete native suite confirmed, owned lint **0 Error, 0 Fatal, 3 Warning**. |
| `:convention:test --dependency-verification strict` with `-BuildLogic` | Passed in an independently declared private cache; actual positive/reverse-cycle/forbidden graph fixtures executed. |
| `python .\docs\android\evidence\WP-301\verify_consumer_locks.py --self-test` | Passed: **8 reader regressions**; incomplete additions, changed versions, other configuration changes and malformed/zero records are rejected. |
| `python .\docs\android\evidence\WP-301\verify_consumer_locks.py --check --self-test` | Initially failed closed on unregenerated state. After independently authorized actual byte persistence, passed: **8 reader tests**, exact ten locks/80 configurations/sixteen additions; every original version and other configuration preserved. |
| `Invoke-MeshCoreNativeBuild -Owner WP-301 -Action { ... :core:designsystem:resolveAdmittedThemeConsumerGraphs --write-locks --dependency-verification strict }` | Gate blocked **before action/JVM** with `NATIVE_BUILD_CAPACITY`, virtual KiB **1,608,564** below **2,097,152**, physical KiB **8,272,312**. No lock generation occurred. |
| `python .\docs\android\evidence\WP-301\retain_native_junit.py --self-test` | Passed: **10 reader regressions**; complete failure/malformed/zero/skipped XML is copied before validation and remains blocked, with no overwrite or repository-overlap fallback. |
| `python .\android\core\designsystem\verification\generate_consumer_locks.py --self-test --workflow-check` | Passed: **11 helper regressions** plus existing trust-boundary rules; closed/merged/wrong-base candidates, extra commands and unknown writes fail closed. An unchanged regenerated state still requires the full independent delta check. |
| `python .\tools\android-port\controller\ci.py python --output <own-private-evidence-directory>` | After exact committed producer carry at `661736a4...`, passed **222 controller + 15 original scaffold tests**, zero failures/errors/skips. |

These local Gradle tasks use `android\scaffold\invoke-gradle.ps1` with
`-ConstrainedMemory -BuildHeap 512m -BuildMetaspace 512m -TestHeap 256m`,
one worker, in-process Kotlin, SerialGC, two CPUs and explicit private
user/project/Android caches. [Complete local native evidence](local-native/result.json)
contains actual raw JUnit gzip records, counters, PNGs, native inputs and owned
lint. Its execution distinguishes working-tree repairs from the observed
pre-repair commit; final current-head hosted proof remains mandatory.

[Exact consumer amendment request](dependency-amendment-request.json) and
[verbatim graph](consumer-dependency-graphs.tsv) identify only sixteen added
DataStore1.2.1/Okio3.9.1/serialization-json1.7.3 components, all already admitted
in the existing checksum XML. The coordinator independently admitted exactly
these ten paths/eight configurations each/sixteen additions. The initial local
resolver did not run: the local APK packaging command failed
to receive a daemon response under critically low OS commit/pagefile headroom.
Only the specifically confirmed own idle/teardown daemon PID 4396 was stopped;
the known private-cache PID scan then proved zero own JVMs and the slot was
released to WP-202. The coordinator then delivered a new exact targeted
resolver grant after WP-202's explicit zero-JVM release. Its exclusive
FileStream/capacity/known-port-JVM gate rejected the first attempt before any
native command ran. No unwrapped Java, spin retry, manual lock records,
memory-limit waiver or global OS change is used.

The source-review fixes and meaningful screenshot preset correction are now
written with five real regressions. Both hosts actually discovered 92 methods,
but one failed in each host's run as recorded below; no current pass is claimed.
Historical 87-pass/39-image evidence is preserved, not presented as current
proof for those repairs. `resolveAdmittedThemeConsumerGraphs` is
the declared targeted writer; it requires explicit `--write-locks`, preserves
old selected versions, and visits only the admitted runtime/lint configurations.
No global XML/catalog/other-lock exception is granted.
`verifyThemeConsumerLocks` checks actual generated files against immutable
initial Git blobs and the exact admitted delta. It is a fail-closed root
verification hook, not a generator or an admission/merge authority.

After unchanged local capacity failures, the coordinator explicitly admitted
one auxiliary ordinary candidate workflow at
`.github\workflows\android-theme-dependency-generation.yml`, with its helper/tests
in the owned module. A single pinned Ubuntu24.04 ephemeral runner/20min budget
checks actual candidate/head/base/run/attempt/source/manifest/policy, invokes
only the declared resolver with strict verification, rejects unknown writes or
version/configuration deltas, and uploads exact generated locks as a data
proposal. This neither replaces mandatory CI nor commits/pushes/merges anything.
[Actual successful generation](hosted-lock-generation/run.json) at
run **37324980133/attempt1**, head
`661736a4797947430564edccce3dd1cb1cfed1d0`, base
`e697823c8937eb5b12a40362ca2c5aae4c45f56a`, produced all ten exact lock files.
The raw ZIP is **69,846 bytes**, SHA-256
`275497901b1f9920539b47ecfeae89c52715975dd4e6a91f983a7f350679ed3e`
(reported and independently recomputed). All 14 CRC-valid proposal entries
are preserved byte-for-byte under deterministic gzip. The actual selected
graph has **10,144 rows**, SHA-256
`3192576b10a4bf256f871f1aac059022f056bf3c99989196519f1e33462c0653`.
After independent complete coordinator readback, only these actual approved
bytes were persisted in the ten leased paths. No manual/inferred lock records
or generation-as-native-pass label is used. Later unchanged resolution still
executes the same strict resolver and requires full delta/input validation.
The owning branch includes the guarded WP-202 main merge
`e697823c8937eb5b12a40362ca2c5aae4c45f56a`; earlier run/base bindings stay
historical rather than being retroactively rebound to that newer base.

An external same-repository CLI conversation advanced the remote branch to
`738509bb380bffe51a7cd9cc17ba64327619b9c9`, parent exactly `661736a4...`, with
one owned test-line unmerged-semantics query. Source writes stopped while the
coordinator identified the actor; this worker did not author or push that
commit. The separately authorized clean fast-forward preserves external
authorship and leaves visibility/contrast assertions, source/production/floors
and all 92 method declarations unchanged. The original `661736a4...` normal
run was canceled by the successor, not relabeled as passing.

The exclusive WP-109 owner froze the reviewed `81f677` producer; the coordinator
admitted carrying its full self-contained three-file delta from e697, not a
bare two-file stub or unrelated CLI production. The supplied automatic patch
is **15,262 bytes**, SHA-256
`f5b472b69b62a3e072089c2bf6b0544a565244ae09f73a77df3acc1d77946066`.
`git apply --check` preceded the carry, and the exact required normalized Git
blobs are recorded in [authorization](authorization.json). It forwards
`wp301EvidenceDirectory` only for the original verify stage; inherited
credentials, mandatory tasks, original budgets, source pins and evidence
schema are unchanged. These three CI files are frozen after carry.

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
`inspectThemeConsumerGraphs`, `resolveAdmittedThemeConsumerGraphs` and
`verifyThemeConsumerLocks`. Its real
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

Complete current-head root verification, final APK/alignment inspection, all
lint reports and passing hosted run/attempt identities remain **pending**.
The first real hosted repair also compiled production code and inspected a
Linux debug APK, but unit preparation failed on unrecorded new locked
dependencies; that is compile-only evidence, not native test success.

[Run 37311053309, attempt 1](https://github.com/cbattlegear/MeshCoreOne-Android/actions/runs/37311053309)
at `386d318b541b1bedf0d7d4136bcde2bfc76db6b5` actually ran the older module
suite on both hosts: each reported 87 discovered/passed, zero
failures/errors/skips, all 58 source dispositions and 39 native captures.
Both then failed closed at `:resolveScaffoldDependencies` because the sixteen
added DataStore/Okio/serialization components were absent from the ten
consumer locks. That hosted module result does not validate the newer
review/capture fixes, complete root graph, standalone stage or final lint.

[Run 37317787426, attempt 1](https://github.com/cbattlegear/MeshCoreOne-Android/actions/runs/37317787426)
at `d149117e83cff8586288c223744b5f20c4112b66` compiled the repairs and actually
discovered **92 tests, one failed on each host**. The original quiet logs and
success-only root collector did not retain the failed identity/raw XML.
Complete extracted failure bundles are [retained verbatim](hosted-repair-failure/run.json); they are not
passing suite evidence. Owned error-level test diagnostics and an independent
raw-JUnit finalizer now preserve produced bytes before any success validator.
The finalizer requires an explicit bounded external `wp301EvidenceDirectory`;
the exact producer forwarding carry is now separately admitted.
No source/contrast expectation is changed without the actual failure trace.

That run inspected actual debug APKs before the failing suite: Linux
**43,024,076 bytes**, SHA-256
`d4b8afea01c8dc082a549de680933b943ec9b338b6c8ff639815230ca59122a6`;
Windows **43,024,396 bytes**, SHA-256
`c40a51ed80a3818ad2f0e02319c33f2a6e3c5f701f65d40b02eb2398accb3bb0`.
Correct package/min31/target37/notices/source-map/fixture absence do not turn
the failed 92-case suite, skipped final lint or incomplete root run into success.
Capture scope is identity-avatar/message/status/theme/error content only;
unseen category-avatar renders are not claimed.

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
