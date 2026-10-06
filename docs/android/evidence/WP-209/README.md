# WP-209 evidence

**External contribution:** this was prepared outside the dispatch controller, with
no lease or receipt. This document lists the commands that actually ran and their
observed results.

- **Reference:** `db14559b39d32322b06477c6ae676112f583db50`
- **Base:** `main` @ `7237727f`
- **Host:** macOS 26, JDK 21.0.12.1, Gradle 9.8.0 via the wrapper

| Command | Observed |
| --- | --- |
| `./gradlew :core:services:test validateModuleGraph --rerun` | `BUILD SUCCESSFUL`; `:core:services` **253 tests, 0 failures, 0 errors, 0 skipped** |
| `python tools/android-port/portmap.py` | exit 0 |
| `python tools/android-port/controller/validate.py` | exit 0 |

All **176** source case ids that `docs/android/test-cases.json` assigns to
WP-209's test files run under their exact ids. None are missing.

| Area | Source ids | Total tests |
| --- | ---: | ---: |
| ContactService, sync, share utilities | 48 | 83 (incl. 26 native + 3 real-session) |
| ChannelService, pipeline, flood scope, hashtags | 53 | 84 (incl. 22 native) |
| AdvertisementService + delta sync | 75 | 86 (incl. 11 native) |

- **Repeat runs:** the advertisement suite passed 7 consecutive times, including forced reruns. The channel suite passed 3 consecutive forced reruns.
- **Mutation checks:**
  - **Caught:** path-update escalation, stop not waiting for a delete flush, nickname trimming, and clearing a cancellation-safe claim.
  - **Not caught:** recording the busy-round sync end time. The source suite doesn't pin it either.
