# WP-203 evidence contract

Repository `cbattlegear/MeshCoreOne-Android`; owner `data-persistence-engineer`.
Initial base `e697823c8937eb5b12a40362ca2c5aae4c45f56a`.
Frozen source `db14559b39d32322b06477c6ae676112f583db50`,
tree `8918fdc604341e6996a68c88f6bb1c02b9c2f87e`.
Manifest `78a22920beaa5899f9618806b5cd2b27d50399a9b29b4d8dbd79f755717ec746`;
semantic policy `56bdc53548bc86d631245795dfa38b4fc86048e0e7cbe1c7d5695879b035b42a`.
Receipt `autonomous-WP-203-e697823c` binds the initial owner and subsequent
explicit A/B/C/D producer amendments. It does not publish a formal gate.

## Declared commands

All local Java, including preflight, is invoked inside the coordinator's
private `Invoke-MeshCoreNativeBuild -Owner WP-203` gate. Each invocation sets
the read-only approved SDK/JDK paths and this owner's private Gradle user,
Gradle project and Android user caches. The existing
`android\scaffold\invoke-gradle.ps1` strips credentials. Local budgets are
512MiB build heap/metaspace, 256MiB test heap/metaspace, one worker, in-process
Kotlin, SerialGC and two active CPUs. A busy/capacity refusal is **BLOCKED**,
not a successful runner result.

| Command/task | Purpose | Result |
| --- | --- | --- |
| `:core:data:dependencies --write-locks --dependency-verification strict --quiet` | Generate only the admitted owner-local dependency state | Passed through registered private gate: five existing serialization1.7.3 records gain four compile configurations each; no new version or shared content delta |
| `:core:data:compileDebugKotlin --dependency-verification strict --quiet` | Compile the actual production backup and admitted producer seams | Pending |
| `:core:data:testDebugUnitTest --dependency-verification strict --no-build-cache --rerun-tasks --quiet` | Complete actual Room/codec module suite, not a map surrogate | Historical Linux run37332274169/head852792fe: 348 passed, zero failed/errors/skips; final-head refresh required |
| `:core:data:verifyBackupTests --dependency-verification strict --quiet` | Fail-closed original-family and raw nonzero/unskipped backup proof | Same actual Linux run: 194 native backup cases / 171 distinct original bindings passed |
| `:core:data:verifyPersistenceRepositoryTests --dependency-verification strict --quiet` | Preserve original WP-202 producer behavior and separate all-module evidence | Same actual Linux run: 154 repository methods / 208 original dispositions passed separately |
| `verifyScaffoldTests validateModuleGraph --dependency-verification strict --no-build-cache --rerun-tasks --quiet` | Complete native integration/graph regression proof | Pending |
| `:core:protocol:test --dependency-verification strict --no-build-cache --rerun-tasks --quiet` | Complete frozen protocol regression proof | Pending |
| `:app:assembleDebug lintScaffold --dependency-verification strict --quiet` | Debug APK/native lint; no release or device claim | Pending |
| `python .\tools\android-port\controller\validate.py` | Frozen ownership/manifest graph validation | Passed before edits: 65 WPs, 185 edges, eight gates |
| `python .\tools\android-port\test_inventory.py --check` | Frozen original inventory drift | Passed: 468 paths, 5,133 declarations; no feature-pass claim |
| `python .\tools\android-port\extract_vectors.py --check` | Independent immutable vector drift | Passed: 49 immutable vectors |
| `python .\docs\android\evidence\WP-203\collect_evidence.py --inventory-only` | Original case/native declaration binding | 171/171 original bindings, 194 native declarations; not execution |
| `python -m unittest discover -s .\docs\android\evidence\WP-202 -p test_collect_evidence.py -q` | Frozen producer collector guards | 19 passed, zero failures/errors/skips |
| `python -m unittest discover -s .\docs\android\evidence\WP-203 -p test_collect_evidence.py -q` | Raw backup evidence guard negatives | 9 passed, zero failures/errors/skips; synthetic inputs not native proof |
| `python -m unittest discover -s .\tools\android-port\oracle\tests -p test_wp203_*.py -q` | Cross-direction artifact/source/XML guard negatives | 20 passed, zero failures/errors/skips; not Swift execution |
| `python .\tools\android-port\controller\workflows.py` | Declared workflow trust-boundary assertions | Passed; no live publisher/gate claim |
| `python .\tools\android-port\controller\verification_config.py --check` | Unchanged semantic manifest/overlay policy | Passed; feature acceptance remains unconfigured in canonical manifest |

The admitted new hook does not count the existing 154 repository tests as
backup proof. Every original backup declaration/family must bind to an
actually executed native assertion. Source headers are traceability only.
Complete raw XML and test input blobs are also independently retained by
the existing whole-module CI reader.

The subsequent actual gated targeted command
`:core:data:testDebugUnitTest --tests
com.meshcoreone.android.core.data.backup.AppBackupEnvelopeTest
--dependency-verification strict --quiet` passed production compilation,
then failed unit-test compilation because a protected test helper exposed
the internal hook type. The helper is now internal; that repair is not
reported passed until re-executed. A single later repair probe was refused
as `NATIVE_BUILD_BUSY` before starting Gradle. No filtered suite, compile
failure or resource refusal is complete native or original-case evidence.

The real full historical local run discovered 348 methods, passed 347 and
failed one added exact-cap-prefix assertion, with zero skips. The codec now
delivers the entire permitted prefix and probes a private single overflow
byte; it never exposes that byte to a reader. The prior real non-JSON bomb
failure is also repaired without whole expanded-buffer allocation: a known
JSON/UTF-8 decode failure drains the still-bounded inflater so source
decompression/size error precedence remains exact. The subsequent actual
Linux producer run passed all 348 methods and retained the complete raw
module XML, 171 original bindings, input blobs and measured export hashes.

## Real cross-direction boundary

The three-job admitted candidate workflow must execute an actual Room
Kotlin export, frozen macOS Swift decoder and **SwiftData restore/export**,
then actual Room restore of that Swift output. Until those commands and
same-head/run/attempt/data hashes exist and pass, bidirectional acceptance
is **BLOCKED**. Windows source staging, candidate round trips and the
existing actual WP-004 fixture do not substitute for this new execution.
Only data, XML, logs and source provenance are uploaded, not executable
artifacts, credentials or user content.

Actual new pipeline commands are
`python tools/android-port/oracle/wp203_ci.py producer --state <private
environment.json> --output <new runner-temp output>`,
`python tools/android-port/oracle/wp203_interop.py --input <same-run producer
data> --output <new runner-temp output> --stage-root <new frozen-source
directory>` and `python tools/android-port/oracle/wp203_ci.py consumer
--state <private environment.json> --input <same-run Swift data> --output
<new runner-temp output>`. Each native stage executes the existing module's
actual `verifyBackupTests`, `verifyPersistenceRepositoryTests` and
`validateModuleGraph`, with strict dependency verification and forced tests.
Swift executes existing WP-004 codecs plus real `swift test --package-path
<isolated MC1Services> --filter WP203InteropTests --parallel --num-workers 1
--disable-swift-testing --verbose --xunit-output <raw XML> --scratch-path
<isolated build>` on frozen production code. This uses exactly one XCTest
worker, not parallel agent work.

Run37332274169/head852792fe actually compiled the full frozen source and
executed the owned SwiftData XCTest with **one test / zero failures**,
restoring Kotlin's export and producing genuine Swift export/proof bytes.
Its pipeline still failed closed: Darwin SwiftPM's non-parallel XCTest
runner does not emit the requested xUnit file; only a separate zero-case
Swift Testing report was emitted. That zero report was **not accepted**.
The real SwiftPM parallel runner is required to produce actual XCTest XML.
Its measured generator has no skipped-count attribute, so the reader also
requires the hash-bound framework trace identifying the exact mandatory
test as passed and `Executed 1 test, with 0 failures (0 unexpected)`.
Missing/renamed/zero/skipped/malformed evidence remains blocked.
The consumer was skipped in this historical failed pipeline, so this does
not claim completed bidirectional acceptance. Raw measured hashes are in
`hosted-history.json`.

The producer separately generates only its admitted module-local lock with
the declared `:core:data:dependencies --write-locks` command. Any real local
lock delta is retained as mechanical data and **blocks** exact-head native
acceptance until committed; no generated working-tree state is falsely
bound to an unchanged candidate. Missing/zero/skipped or stale-hash data
stages cannot progress through the dependent jobs.
