# WP-215 evidence

**External contribution:** prepared outside the dispatch controller, with no lease
or receipt. This lists the commands that actually ran and their observed results.

- **Reference:** `db14559b39d32322b06477c6ae676112f583db50`
- **Base:** `main` @ `7237727f`
- **Host:** macOS 26, JDK 21 (Homebrew `openjdk@21`), Gradle 9.8.0 via the wrapper

| Command | Observed |
| --- | --- |
| `./gradlew :core:services:test --rerun :core:contracts:test --rerun validateModuleGraph --rerun` (3 forced runs) | `BUILD SUCCESSFUL` each time; `:core:services` **85 tests, 0 failures, 0 errors, 0 skipped**; `:core:contracts` 4 tests, 0 failures |
| `./gradlew -I jdk17-release.init.gradle :core:services:compileKotlin :core:contracts:compileKotlin --rerun-tasks` | `BUILD SUCCESSFUL` (JDK 17 API surface) |
| `python tools/android-port/portmap.py` (after commit) | exit 0 |
| `python tools/android-port/controller/validate.py` (after commit) | exit 0 |

All **20** source case ids that `docs/android/test-cases.json` assigns to WP-215 run
under their exact display names. None are missing or duplicated.

| Swift file | Ids | Kotlin test class |
| --- | ---: | --- |
| `NotificationServiceTests.swift` | 11 | `NotificationServiceTests` |
| `NotificationActionHandlerTests.swift` | 8 | `NotificationActionHandlerTests` |
| `NotificationStringProviderTests.swift` | 1 | `NotificationStringProviderTests` |
| native `WP-215::` cases | 65 | `NotificationPolicyNativeTests`, `NotificationTransactionNativeTests` |

- **Native coverage:** the native cases cover:
  - posting gates for every kind, with Swift identifiers, threads and categories;
  - badge policy, the 100 ms debounce and serialization;
  - foreground presentation for the active conversation;
  - response routing (reply, mark-read, taps) and delivered-notification removal;
  - setup and authorization;
  - every action transaction, including the step order and cancellation after commit;
  - cold-start and stale-radio cases;
  - reaction notifications;
  - containment of collaborator failures without swallowing cancellation;
  - one-line adapters for the WP-209 and WP-214 ports.
- **Timing:** the tests run on `runBlocking`'s single thread. They use a virtual clock
  for the debounce and gates (`CompletableDeferred`) for overlap and cancellation.
  There are no wall-clock sleeps. `withTimeout` is only a hang guard.
- **Swift oracle:** `wp215_text_oracle.swift.txt` was compiled with `swiftc` 6.3.2, and
  its output is `wp215_text_oracle.out`. It covers the `reactionPreview` Character
  counts (ZWJ families, flags, combining marks, CRLF, mixed) and `String ==`
  canonical equivalence. A native case pins the same values.
- **Mutation checks:** each behavior below was broken on purpose. The suite was then
  re-run with `--rerun`, the named tests failed, and the source was restored. All 19
  mutations were detected. The script is in `mutations/wp215_mutate.py.txt` and the
  output in `mutations/wp215_mutations.out`.
  - Suppression: room and direct-message posts.
  - The active-conversation skip; an active channel that ignores its radio.
  - Preview truncation: off by one, and counting UTF-16 units.
  - Mark-read written to the session radio; the stale-radio reply guard removed.
  - Badge: the channel preference ignored.
  - Tails made cancellable: direct reply, channel reply and mark-read.
  - A draft or channel failure lost on a cancelled send.
  - Badge: computations not serialized, made cancellable, or debounced at 50 ms.
  - `contained()` swallowing cancellation; drafts keyed by bare UUID.
- **Independent review:** a read-only line-by-line review against the Swift raised one
  MEDIUM finding, four LOW findings and several test gaps. All were fixed with
  regressions; see `deviations/WP-215.md` A-09.
- **Not covered here:** the real WP-208/210/214 services and the WP-401 platform
  adapter. Fakes stand in for them through the ports listed in
  `deviations/WP-215.md` A-08. There is no Android, radio, UI or release claim.
