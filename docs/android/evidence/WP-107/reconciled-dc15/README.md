# WP-107 current-base reconciliation

**Local component proof passed; root aggregate is blocked by a concrete
merged collector-scope defect.** This is separate evidence, not an overwrite
of the original head/base results or a formal source acceptance verdict.

| Binding | Actual revision |
| --- | --- |
| Historical component head | `ce93bfaa22b438aed7e8b7a903a611270a985cd3` |
| Historical component base | `0394b83c9b47fa0d7198e2d631f7ddb313cd370d` |
| Coordinator-verified actually merged current main | `dc15f1ba445acf3230383ea68d4827c592f3fafa` |
| Rebased component code commit | `3989604bf24a7b4a306265d4def3bdf49a67592e` |
| Exact old live owning remote tip verified before rebase | `ce93bfaa22b438aed7e8b7a903a611270a985cd3` |
| Source | `db14559b39d32322b06477c6ae676112f583db50` |
| Shared write receipt | `autonomous-WP-107-01d2852a`, unchanged owning scope |

Only branch `cbattlegear-bookish-lamp` in its owning worktree was rebased.
No main checkout/parent/other worker branch was changed, and the publication
uses the exact old tip above as its explicit force-with-lease guard.
The entire `android/core/protocol` tree is byte-identical to the historical
head. Source, manifest and semantic policy pins remain unchanged. The frozen
85 original source IDs and their primary ownership are preserved verbatim;
the new proof does not relabel another worker's existing cases as WP-107.

## Fresh actual local proof

All commands use the existing verified private Temurin21.0.12.1+1/Gradle9.8.0/
Kotlin2.3.20/AGP9.4.1/SDK37.2 inputs and the credential-stripped launcher:
`-ConstrainedMemory -BuildHeap 640m`, one worker, in-process Kotlin,
build metaspace512m, test heap/metaspace256m, SerialGC and two processors.
Every shell redeclares the private JDK/SDK/Gradle/Android-user directories.

| Exact task arguments or command | Actual result |
| --- | --- |
| `python .\tools\android-port\controller\ci.py preflight --state <private-environment.json> --output <private-current-base-proof> --local` | Passed pinned current checkout/runtime readiness |
| Launcher `:core:protocol:test --dependency-verification strict --no-build-cache --quiet --project-cache-dir <private-project-cache>` | **4,674 passed /56 suites /0 failed/errors/skips**, including229 session cases and all85 original source IDs |
| Launcher `validateModuleGraph runtimeDependencyInventory resolveScaffoldDependencies --dependency-verification strict --no-build-cache --quiet --project-cache-dir <private-project-cache>` | Passed actual470-edge graph /114 runtime/POM rows, strict checksum/lock mode |
| Launcher `:core:model:test :core:contracts:test --dependency-verification strict --no-build-cache --quiet --project-cache-dir <private-project-cache>` | **166 model +4 neutral cases passed**,0 failed/errors/skips |
| Launcher `:core:database:verifyDomainRoomTests --dependency-verification strict --no-build-cache --quiet --project-cache-dir <private-project-cache>` | **45 real Room cases passed**; the subsequent source collector failed at the exact unowned scope guard described below |
| Launcher `:core:l10n:testDebugUnitTest --dependency-verification strict --no-build-cache --quiet --project-cache-dir <private-project-cache>` | **25 locale cases passed**,0 failed/errors/skips |
| Launcher `:app:assembleDebug --dependency-verification strict --no-build-cache --quiet --project-cache-dir <private-project-cache>` | Passed actual integrated production APK |
| `python .\android\scaffold\inspect_apk.py` | Passed package/min31/target37/launcher/notices/fixture exclusion |
| Actual existing `controller.apk_alignment.inspect_alignment(...)` | Passed zipalign `-c -P 16 4` and all four ELF load-segment families; static proof only |
| `python .\docs\android\evidence\WP-107\verify_evidence.py --base-sha dc15f1ba445acf3230383ea68d4827c592f3fafa --check` | Local protocol/source/lease/fingerprint/complete integration XML/actual APK verification passed; explicitly reports the blocked root aggregate |
| `python .\tools\android-port\controller\runtime_inputs.py` |77 exact committed runtime inputs match checkout |
| `python .\tools\android-port\controller\validate.py` | Complete1,866 pinned inputs /65 WPs /185 edges valid |
| `python .\tools\android-port\test_inventory.py --check` | Unchanged468 paths /5,133 original declarations |
| `python .\tools\android-port\extract_vectors.py --check` | Unchanged49 independent original vectors |
| `python .\tools\android-port\portmap.py` | Current checkout source provenance valid |

[local-results.json](local-results.json) retains every full protocol XML file,
all integration module XML, original runner hashes, canonical stored hashes,
exact current committed generic module input blobs and the original unchanged
protocol fingerprint. No XML/counter is fabricated, truncated or skipped.
Model6 + Room4 + neutral1 + locale4 reports contain the240 real module cases;
these are integration evidence, not240 additional WP-107 original cases.

The new actual APK is **40,140,274 bytes**, SHA256
`ad407741b63487e581e8c195618aed83fceb3c99b68ccebbfc51004830ff2b1a`.
Its DEX contains the real session/core/contact/serializer implementation and
excludes the test/reference classes and GPL app-error test prose. Existing BC
MIT and GPL/MIT/Apache notices remain. [apk-alignment.json](apk-alignment.json)
binds the new APK's actual static16KiB zip/ELF inspection.

## Exact root blocker and retained failures

The declared `verifyScaffoldTests` depends on the actual
`:core:database:verifyDomainRoomTests` hook. That hook invokes
`docs/android/evidence/WP-201/collect_evidence.py`, whose current-base Git blob
is `496282236cf91596af2ce9cc3f7518f81a91cf47`.
After confirming all166/4/45 cases and the real17-table schema, `report()`
compares the **entire** candidate diff against old0394 and asserts every path
belongs to its hardcoded WP-201 `SCOPES` at line127. Correctly leased WP-107
session/docs writes therefore fail the root task.

This is a concrete shared collector boundary defect, not a failed native
session/Room assertion, missing input, proposed skipped suite or permission
to edit another WP. A narrow coordinator amendment/verified merged repair
was requested on the existing PR. No scope check/hook, shared build/dependency,
workflow, source pin or golden was removed, bypassed or changed here.
The root aggregate and new exact-head normal root artifacts cannot be claimed
successful until that defect is repaired under its actual owner authority.

Shared-host Windows commit pressure also produced a formatting OOM, Git
child process error1455/loader failures in a forced protocol attempt, and a
native metaspace commit failure in the first root aggregate. Those failed
logs, complete failing XML and the two named JVM crash logs remain in this
session's private artifacts. Isolated protocol/graph/model/Room/locale/APK
work then produced the genuine passing results above; no test, expectation,
checksum, heap/test policy, ownership rule or source case was weakened.

Normal final protocol/root run IDs, attempts and complete uploaded raw bundle
digests are recorded on the same PR after publication. Historical run
37189885789 /37189885825 success at old ce93/0394 is explicitly not current-base
proof. The independent source reviewer remains pinned to its actual reviewed
head until the coordinator reconciles that review; this worker claims no
review acceptance, self-approval or merge.
