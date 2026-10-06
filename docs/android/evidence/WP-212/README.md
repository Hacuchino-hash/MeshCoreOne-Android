# WP-212 evidence

**External contribution:** this was prepared outside the dispatch controller, with
no lease or receipt. This document lists the commands that actually ran and their
observed results. It isn't a controller acceptance record.

- **Reference:** `db14559b39d32322b06477c6ae676112f583db50`
- **Base:** `main` @ `7237727f`
- **Host:** macOS 26, JDK 21.0.12.1, Gradle wrapper 9.8.0, invoked directly (`check_environment.py` cannot pass on macOS, which injects `__CF_USER_TEXT_ENCODING`)

| Command | Observed |
| --- | --- |
| `./gradlew :core:services:test validateModuleGraph --rerun` | `BUILD SUCCESSFUL`; `:core:services` **224 tests, 0 failures, 0 errors, 0 skipped** |
| `python tools/android-port/portmap.py` | exit 0 |
| `python tools/android-port/controller/validate.py` | exit 0 (`valid`) |

## Original test coverage

All **83** source cases that `docs/android/test-cases.json` assigns to WP-212's
nine test files run under their exact ids, as JUnit display names
`Suite::name()`. None are missing.

| Area | Source cases | Native `WP-212::` cases | Total |
| --- | ---: | ---: | ---: |
| RF (`RFCalculator`, characterization, segments) | 55 | 14 | 69 |
| Logging core (buffer, prune, persistent logger, redaction, audit) | 12 | 90 | 102 |
| RX log (advert hop, reprocess, region reprocess, DM decrypt, export) | 16 | 35 | 51 |
| Seam wiring | 0 | 2 | 2 |

The native cases cover the following:
- **Logging core:** every audit format string, grapheme redaction, the requeue boundary (99 entries kept, 100 dropped), timer and prune edges, cancellation, and concurrent hub use.
- **RF:** the antimeridian, NaN guards, the diffraction threshold, and signed zero.
- **RX log:** overlapping and cancelled region reprocess callers, stale-monitor guards, the 60 s reprocess window, real X25519/AES/HMAC DM fixtures, and export formatting.

Mutation checks on the logging core (requeue `<`→`<=`, drop-summary guard
removed, prune `>=`→`>`, cancellation restore removed) were each caught by 1–5
failing tests.
