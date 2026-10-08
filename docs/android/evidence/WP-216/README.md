# WP-216 evidence

**External contribution:** this was prepared outside the dispatch controller, with
no lease or receipt. This document lists the commands that actually ran and their
observed results.

- **Reference:** `db14559b39d32322b06477c6ae676112f583db50`
- **Base:** WP-213 branch (#52), itself on `main` @ `7237727f`
- **Host:** macOS 26.5.1, JDK 21.0.12.1, Gradle 9.8.0 via the wrapper, Swift 6.3.2 (Command Line Tools)

| Command | Observed |
| --- | --- |
| `./gradlew :core:services:test validateModuleGraph --rerun`, three forced runs | `BUILD SUCCESSFUL` each time; **493 tests, 0 failures**, of which **194** are in `reactions/` |
| Compile `:core:services` main with `-Xjdk-release=17` | succeeds |
| `python tools/android-port/portmap.py` | exit 0 |
| `python tools/android-port/controller/validate.py` | exit 0 |

All **136** source case ids that `docs/android/test-cases.json` assigns to
WP-216's test files run under their exact ids. None are missing. The breakdown is
ReactionParser 44, MeshCoreOpenReactionParser 51, ReactionService 19 and
HeardRepeatsService 22. The other 58 reactions cases are native (`WP-216::`).

- **Swift oracle:** parser outputs, hashes and normalisation come from the frozen Swift sources run under `swiftc`.
- **Agreement:** WP-213's `ReactionWireFormat` agrees with `ReactionParser` on parse, DM parse and hash. `adoptInboundHop` agrees with `core:data`'s logic on a 2,744-input grid.
- **Mutation checks:** twelve deliberate breaks were each caught. They include Crockford `L`→`1` decoding, hash endianness, queue eviction order, identical-path extras, the U+200B whitespace member, fullwidth hex, a failed duplicate check, swallowed cancellation, canonical summary grouping, empty v1 pieces, the same-instant tie-break and second collection of the event stream.
- **Review:** an independent review found no critical or high issues. Its three medium findings were fixed with tests: single-shot event streams, the same-instant tie-break, and decrypted text in logs. The two low ones are documented: the adversarial grapheme gap and the store-only test.
