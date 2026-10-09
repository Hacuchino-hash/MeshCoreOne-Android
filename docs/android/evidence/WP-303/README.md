# WP-303 evidence

External contribution, no lease. These are local macOS runs only, not CI or acceptance
receipts. Frozen source: `db14559b39d32322b06477c6ae676112f583db50`. Base: `main` @
`d2b01374`. Design, adaptations and coordinator notes are in
[`../../deviations/WP-303.md`](../../deviations/WP-303.md).

## Commands

Environment: JDK 21 (`/opt/homebrew/opt/openjdk@21`), the Android command-line SDK and
private Gradle/Android user homes. `--dependency-verification lenient` is used because the
macOS aapt2 artifacts are not in the verification metadata; dependency locking stayed STRICT.
`:core:ui:verifySharedUiInputs` is excluded (it does not run on macOS). The WP-302 navigation
evidence tasks (`prepareWp302NavigationInputs`, `verifyWp302NavigationTests`) were *not*
excluded in the final runs: they compare compiled bytes with Git, so they pass only on a
committed checkout.

```
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug validateModuleGraph \
  --dependency-verification lenient --console=plain -x :core:ui:verifySharedUiInputs \
  --rerun-tasks "-Dorg.gradle.jvmargs=-Xmx3g -XX:MaxMetaspaceSize=1g"      # x2, committed tree
python3.13 tools/android-port/portmap.py
python3.13 tools/android-port/controller/validate.py
```

## Observed results

- `:app:testDebugUnitTest`: 21 suites, 365 tests, 0 failures, 0 errors, 0 skipped, identical
  in two full `--rerun-tasks` runs. 150 are the pre-existing WP-302/launcher tests; the 215
  added by WP-303 are `AppStateBehaviorTest` 34, `ConnectionUiStateTest` 51,
  `DevicePlatformDetectionTest` 49, `SessionStateTest` 21, `StateNativeTest` 15,
  `ChatPrewarmTest` 9, `ProcessContainerTest` 7, `SessionLifecycleTest` 7,
  `HostBindingTest` 6, `PermissionRevocationGuardTest` 4, `DataStoreAdaptersTest` 4,
  `SessionReadinessTest` 3, `DurableStoresTest` 3, `ContainerSmokeTest` 1,
  `DisconnectReconciliationTest` 1.
- Owned source cases: 169 in `docs/android/test-cases.json`. 163 are bound with
  `@OriginalCase` and passed in the JUnit XML (142 source-behavior, 11 platform-adaptation,
  10 native-equivalent). Six are deferred with reasons (iOS toolbar diagnostic; five need
  WP-307 `ChatTimeline` bake output). Per-case class, method, disposition and result are in
  [`source-cases.json`](source-cases.json). The other 52 tests are native WP-303 cases.
- The container tests run the production `AppContainer` over an in-memory Room store
  (`RoomPersistenceStore`), a protocol-speaking fake radio on the real `MeshCoreSession`, the
  real `ConnectionManager` and `RadioSessionContainerFactory`, so every connection builds the
  real service graph (sync, messaging, remote, diagnostics, notifications). Virtual time is
  the test scheduler; the two real-time dependencies (Room, `Dispatchers.Default` services)
  are polled with a five-second bound.
- Mutation checks (production line changed, relevant tests rerun, line restored); every
  mutant failed the named test:

  | Behavior | Mutation | Failing test |
  | --- | --- | --- |
  | reconnect does not duplicate monitors | dispatcher `wire` no longer cancels the previous consumers | `ProcessContainerTest.rewiringTheSameGraphNeitherBumpsVersionsNorAccumulatesCollectors`, `StateNativeTest.rewiringCancelsThePreviousStreamsAndNeverAccumulatesCollectors` |
  | app-state rewire does not duplicate collectors | `replaceJob` no longer cancels the previous job | `ProcessContainerTest.rewiringTheSameGraphNeitherBumpsVersionsNorAccumulatesCollectors` |
  | teardown leaks a stream | `finishDataEvents()` removed from `tearDown` | `SessionLifecycleTest.teardownFinishesEveryEventStreamAndClearsNotificationForwarders`, `reconnectCyclesKeepOneLiveGraphAndNeverGrowSubscribersOrJobs` |
  | teardown leaks a forwarder cycle | `onQuickReply = null` removed | `SessionLifecycleTest.teardownFinishesEveryEventStreamAndClearsNotificationForwarders`, `SessionStateTest.teardownClearsForwarders` |
  | closed graph restarts a monitor | `closed` check removed from `startEventMonitoring` | `SessionLifecycleTest.tearDownIsIdempotentAndRefusesToRestartMonitors` |
  | cold-start send | `hydrate()` does nothing | `SessionReadinessTest.coldStartPendingSendWaitsForReadyThenDrainsExactlyOnce` |
  | resync exhaustion disconnects | `requestDisconnect(RESYNC_FAILED)` removed | `SessionReadinessTest.exhaustedResyncNeverClaimsReadyAndDisconnectsWithTheSyncFailedPill` |
  | battery duplicate bootstrap | `start` no longer replaces the bootstrap job | `StateNativeTest.startingTwiceKeepsOneBootstrapAndOneRefreshLoop` |
  | foreground ordering | background transition no longer awaited | `AppStateBehaviorTest.foregroundWaitsForBackground`, `rapidBouncesStayOrdered` |

  One candidate mutant survived and was discarded as equivalent: making an exhausted resync
  return `Usable` is still fenced by the runtime, because the same failure has already
  requested the disconnect and the runtime refuses to publish `READY` for a generation that
  is no longer current.
- Defects the tests exposed while they were written (each now has a regression): the settings services cannot
  be built before the runtime publishes the generation; `advanceUntilIdle` ignores
  background-scope work; a single `launch` per preference write reorders writes on a
  multi-thread dispatcher (demo-mode defaults and region selection now drain one queue); the
  first low-battery test helper picked the lowest instead of the highest voltage.
- `:app:lintDebug`: 0 errors, 23 warnings, none in WP-303 files (all pre-existing:
  `localeConfig`, version-catalog and dependency-update notices). An earlier run flagged six
  `UseKtx` warnings in the new stores; they were fixed.
- `validateModuleGraph` passed. `:app:assembleDebug` succeeded.
- `tools/android-port/portmap.py` exited 0 and `controller/validate.py` returned `"result": "valid"` on the committed tree.

## Parity oracle

`wp303_foundation_oracle.swift.txt` (renamed from `.swift`) and `wp303_foundation_oracle.out`
are a `swiftc` 6.3.2 program and its output: `Date(timeIntervalSinceReferenceDate: 0)` is
`978307200` epoch seconds; `1704067200.5` is `725760000.5` reference seconds;
`726019200.25` is `1704326400.25` epoch seconds; `JSONEncoder` writes `/` as `\/`, omits nil
members and decodes `P` as `P`. `StateNativeTest` asserts exactly these values.

## Unverified here

No device or emulator run. The production factory, `MeshCoreApplication`, the foreground
service, companion/presence, bonding and permission routes, and every Wi-Fi/BLE link path are
compiled and unit-tested at their seams only. The BLE `RuntimeLink`, WP-401 notification
adapter, WP-218 content cache and WP-307 `ChatTimeline` do not exist on main (coordinator
notes C-03, C-06, C-07).

## BLE runtime link addendum (C-06)

`BleRuntimeLink` (+ factory branch, `BleReconnectMemory`) is covered by `BleRuntimeLinkTest`:
12 tests, 0 failures, 0 errors, 0 skipped (JUnit XML), over the real `BleTransport` with a
scripted `GattFacade` fake. Cases: connect then firmware frame capacity 20 to 172; connect
authentication failure classifies as `AuthenticationFailed`; lost bond after connect delivers
`LinkFailure.AuthenticationFailed`; link loss delivered once and the next link uses
`Reconnect`; clean remote disconnect delivers a null cause; user disconnect reports nothing;
auto-reconnect callbacks in order then loss; bond refresh and `mayRefreshBond` validity;
close unregisters the collector and bond handler; re-register replaces rather than
accumulates; a throwing callback is contained; factory builds Wi-Fi and Bluetooth links.
Mutation checks (production line changed, tests rerun, line restored):

| Behavior | Mutation | Failing test |
| --- | --- | --- |
| user stop is not a loss | `!stoppedByUser` guard removed | `userDisconnectIsNotReportedAndClearsReconnectMemory` |
| no leaked collector | registration `close()` no longer cancels its job | `closeUnregistersHandlersAndLeavesNoCollector`, `reRegisteringReplacesTheCollectorInsteadOfAccumulating` |

Not verifiable without a radio/device: real GATT callback ordering, OEM bond behavior, the
172-byte frame limit against firmware, and `AndroidGattFacade` under the main looper.
