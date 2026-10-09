# WP-309 acceptance evidence

- Repository: `cbattlegear/MeshCoreOne-Android`
- Work package / owner: `WP-309` / `chats-ui-engineer`
- Integrated base: `f34f68d0416da113c2cd0d274c44937f6c41a5d3`
- Frozen Swift source: `db14559b39d32322b06477c6ae676112f583db50`
- Acceptance: `WP-309-behavior`, `WP-309-boundaries`, `WP-309-source-test-parity`
- Tracking issue: `#117`

## Implemented behavior

The feature provides source-equivalent action availability and ordering, recent reaction actions and
reaction-detail selection, scoped/deduplicated reaction indexing, path/repeat arrival ordering, hash-sized
hop decoding, sender/repeater resolution, SNR and repeat details, block/mute operations, and textual
delivery feedback. The adaptive Compose surface exposes accessible action, reaction, path-list, path-map,
metadata, and destructive-confirmation states.

Path rendering uses the merged WP-312 MapLibre/OpenFreeMap contracts. It retains stable endpoint/hop IDs,
draws only the selected arrival, distinguishes exact/fallback/unresolved hops, reports incomplete distance,
and keeps OpenFreeMap/OpenMapTiles/OpenStreetMap attribution visible. No merged WP-307 timeline,
WP-310 room/channel, WP-312 provider, or WP-003 CI simplification was replaced.

## Original-case mapping

The frozen inventory assigns 102 Swift cases to WP-309. `OriginalCaseInventoryTest` pins every original
case name exactly once. The 30 WP-309 JVM assertions exercise their behavior as parameter families:
formatter/direct/flood modes and truncation; action/path-detail visibility; canonical/repeat arrival order;
selection; sender/hop exact, fallback, and unresolved resolution; map ID/camera/line/distance/readiness;
reaction selection/grouping/indexing; typed failure/cancellation; and block/mute writes. The inventory is
an accounting guard, not a claim that source-name presence alone proves parity.

## Local verification

All Gradle commands used the repository launcher with checksum-pinned Temurin 21.0.12.1, Android SDK 37.2,
build-tools 37.0.0, explicit session-local caches, constrained memory, and strict dependency verification.
The final run is performed after integrating the stated base.

| Command/check | Result |
| --- | --- |
| `:feature:chats:testDebugUnitTest` | Passed: 188 discovered/run/passed, including 30 WP-309 cases; 0 failed/errors/skipped. |
| `:feature:chats:lintDebug :app:compileDebugKotlin` | Passed; 0 lint errors, with 3 pre-existing WP-306 `LocalContextResourcesRead` warnings. |
| `:validateModuleGraph` | Passed; chats retains allowed feature-to-core edges and does not depend on concrete `core:services`. |
| `python tools/android-port/controller/validate.py` | Passed trusted manifest/ownership/traceability validation. |

JUnit discovery counts come from Gradle `TEST-*.xml`, not source-file counts. Hosted exact-head
`android-ci`, `parity-review`, and `gate-integrity` remain authoritative.

## Limits

No physical device, hardware radio, TalkBack, screenshot, iOS execution, signing, release APK, or protected
gate result is claimed. See `docs/android/deviations/WP-309.md` for the native boundaries.
