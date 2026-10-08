# WP-214 evidence

**External contribution:** prepared outside the dispatch controller, with no lease
or receipt. This lists the commands that actually ran and their observed results.

- **Reference:** `db14559b39d32322b06477c6ae676112f583db50`
- **Base:** `main` @ `7237727f`
- **Host:** macOS 26, JDK 21 (Homebrew `openjdk@21`), Gradle 9.8.0 via the wrapper

| Command | Observed |
| --- | --- |
| `./gradlew :core:services:test validateModuleGraph --rerun` | `BUILD SUCCESSFUL`; `:core:services` **202 tests, 0 failures, 0 errors, 0 skipped** |
| `./gradlew :core:services:test --rerun` (3 forced runs) | 202 / 202 each time |
| `./gradlew -I jdk17-release.init.gradle :core:services:compileKotlin --rerun-tasks` | `BUILD SUCCESSFUL` (`-Xjdk-release=17`) |
| `python tools/android-port/portmap.py` | exit 0 |
| `python tools/android-port/controller/validate.py` | exit 0 |
| `sh swift-oracle/run.sh` | outputs in `swift-oracle/*.out` |

All **155** source case ids that `docs/android/test-cases.json` assigns to WP-214's
seven test files run under their exact ids. None are missing or duplicated. The one
parameterized family (`performAdvertContactSync with zero watermark does not
sync(fullRefetch : Bool)`) runs once and iterates both Swift arguments.

| Swift file | Ids | Kotlin test class |
| --- | ---: | --- |
| `SyncCoordinatorTests.swift` | 57 | `SyncCoordinatorTests`, `SyncCoordinatorContactTests` |
| `SyncCoordinatorTimestampTests.swift` | 35 | `SyncCoordinatorTimestampTests` |
| `SyncCoordinatorMessageHandlerTests.swift` | 26 | `SyncCoordinatorMessageHandlerTests` |
| `SyncCoordinatorChannelSkipTests.swift` | 13 | `SyncCoordinatorChannelSkipTests` |
| `DevicePlatformSyncThrottlingTests.swift` | 13 | `DevicePlatformSyncThrottlingTests` |
| `ConnectionManagerResyncLoopTests.swift` | 9 | `ConnectionManagerResyncLoopTests` |
| `SyncCoordinatorDataEventTests.swift` | 2 | `SyncCoordinatorDataEventTests` |
| native `WP-214::` cases | 47 | `SyncNativeLifecycleTests`, `SyncNativeIngestionTests` |

- **Timing:** every throttle, watchdog, retry and backoff test runs on a virtual clock
  driven by a single-thread dispatcher. There are no wall-clock sleeps. The suite
  fails on deadlock and on uncaught service-scope exceptions.
- **Independent review:** a read-only review of the port against the Swift found no
  behavioral divergence. Its cancellation-robustness findings were fixed and have native
  regressions; the remaining notes are documented in `deviations/WP-214.md` A-08.
- **Swift oracle:** `swift-oracle/` compiles three tiny programs against Foundation:
  `CharacterSet.whitespaces` enumerated over all scalars, `split(maxSplits:)` counting,
  and `parseChannelMessage` edge cases. The Kotlin native case pins the same outputs.
- **Mutation checks:** each behavior below was broken on purpose and the suite was
  re-run with `--rerun`. The named tests failed, then the source was restored.
  The script and the full output are in `mutations/`. All 15 mutations were detected.
  - Channel-only retry backoff fixed at 2 s; the ESP32 skip window set to zero; skip
    ignoring the last *attempted* sync.
  - Watermark: no one-second overlap; the invalid-watermark recovery latch removed.
  - Timestamp: the future tolerance made inclusive.
  - Resync loop: no max-attempt exit; no service-identity fence; no catch-all bracket close.
  - Message dedup lookup broken.
  - Sync-claim generation check removed; full sync no longer waits for the advert claim.
  - A foreign `CancellationException` treated as caller cancellation; `onDisconnected`
    made cancellable; the channel-retry cancel path leaving the bracket open.
- **Not covered here:** the real WP-208/209/210/212/215/216 services. Fakes stand in
  for them through the ports; see `deviations/WP-214.md` A-09 and the WP-303 wiring
  list. There is no Android, radio, UI or release claim.
