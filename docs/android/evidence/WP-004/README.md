# WP-004 executable parity foundation evidence

Repository: `cbattlegear/MeshCoreOne-Android`; owner: `test-parity-engineer`.
Source: `db14559b39d32322b06477c6ae676112f583db50`.
Initial merged base: `979dd73b2fc3cb5cbeea07b1c809f5c9283289ee`.
Coordinator-authorized rebase/integration base:
`050ac6909c65af8e3fc63e5aaf44b1d240f1d8ac`.
Native project session: `29b449ac-7564-4788-b175-75f96804eed5`.
Manifest revision:
`78a22920beaa5899f9618806b5cd2b27d50399a9b29b4d8dbd79f755717ec746`.
Policy revision:
`56bdc53548bc86d631245795dfa38b4fc86048e0e7cbe1c7d5695879b035b42a`.
Scoped kickoff lease: `autonomous-WP-004-979dd73b`, including the exact new
foundation workflow and the later explicitly granted module lock
`android/gradle/dependency-locks/core-testing.lockfile`; no live controller
ledger/approval is manufactured.
Implementation head and hosted run/attempt are recorded by actual PR/CI
artifacts, avoiding a self-referential committed head SHA.

## Original inventory, not claimed feature parity

`test-cases.json` is schema-compatible with
`controller/gates.py::validate_cases`:

```json
{
  "schema_version": 1,
  "source_sha": "db14559b39d32322b06477c6ae676112f583db50",
  "entries": [
    {
      "path": "pinned Git identifier",
      "blob_sha": "exact original blob",
      "has_assertions": true,
      "cases": [{"id": "suite::method(signature)", "parameter_family": "single or declared-family digest"}]
    }
  ]
}
```

The generator validates this exact schema; `inventory-details.json` is a
separate version-1, strictly validated case extension, not a loosened controller
schema. Every catalog identity resolves to detailed annotations, real input
expressions/rows or pinned named/enum definitions, suite/name/signature/lines,
assertion sites, UTF-8 byte/hash provenance and primary WP owner. Non-test
methods and support-only files are retained. Conditional compilation and trait
arguments remain visible. Counts are derived from parsed declarations:

| Original accounting | Count |
| --- | ---: |
| Paths / manifest-kind test / support | 468 / 428 / 40 |
| Registered case declarations | 5,133 |
| Parameterized declarations / declared input rows | 70 / 382 |
| Direct / helper / no-assertion case declarations | 5,105 / 13 / 15 |
| Non-test methods | 2,131 |
| Original cases marked ported or passed by this inventory | 0 |

The seven explicitly named non-suite test-kind paths and assertion-bearing
support exceptions are explained in `deviations/WP-004.md`. Unknown source,
unconsumed annotations, unknown input families, duplicate cases, zero suites,
renames, stale pin/blobs and changed checkout bytes fail. Comments, nested block
comments, escaped/backtick names, strings/raw strings/interpolations are not
counted as tests or byte literals.
Helper classifications include source-backed uncaught `try await waitUntil`
eventual-condition assertions, with the original helper blob and invocation
lines; swallowed `try?` failures are not called assertions.

## Local commands and actual outcomes

The documented WP-003 local-input/preflight commands passed with the existing
JDK21.0.12.1 and SDK37.2/build-tools37.0.0, private per-session caches and unchanged
credential allowlist. No SDK/JDK repin or global host modification occurred.

| Exact command | Actual outcome |
| --- | --- |
| `python .\tools\android-port\test_inventory.py --regenerate` | Generated all 468 paths / 5,133 declarations against exact frozen blobs |
| `python .\tools\android-port\test_inventory.py --check` | Passed deterministic source/catalog/detail drift check |
| `python .\tools\android-port\extract_vectors.py --regenerate` | Copied 37 Python-reference fields + 12 Swift edge literals: 49 vectors |
| `python .\tools\android-port\extract_vectors.py --check` | Passed independent provenance/byte/count/digest drift check |
| `python .\tools\android-port\oracle\run_tests.py --quiet` | 83 discovered/run/passed; 0 failed/errors/skipped |
| `python .\tools\android-port\controller\verification_config.py --check` | Passed; only existing WP-002/003 overlays, source/graph unchanged |
| `python .\tools\android-port\controller\validate.py` | Passed; 65 WPs / 185 edges / 428 test + 40 support paths unchanged |
| `python .\tools\android-port\controller\workflows.py` | Passed unchanged candidate/trusted workflow boundaries |
| `python .\tools\android-port\controller\test_runner.py --quiet` | 165 discovered/run/passed on the merged integration base; 0 failed/errors/skipped |
| `python -m unittest discover -s .\android\scaffold -p test_*.py -q` | 15 run/passed; 0 failures/errors/skips |
| `python .\tools\android-port\oracle\codec_harness.py stage --output <new absolute private directory>` | Source-backed fragments staged successfully on Windows; **not Swift execution** |
| `:core:testing:dependencies --write-locks --dependency-verification strict --quiet` through `invoke-gradle.ps1 -ConstrainedMemory -BuildHeap 768m` | Passed normal scoped generation: only two existing coroutine-test 1.10.2 components added in the granted module lock |
| `:core:testing:testDebugUnitTest validateModuleGraph --dependency-verification strict --no-build-cache --rerun-tasks --quiet` through `invoke-gradle.ps1 -ConstrainedMemory -BuildHeap 768m` | Passed: 35 actual cases in six suites, 0 failed/errors/skipped; production isolation graph passed |
| `python .\tools\android-port\oracle\foundation_ci.py junit --junit .\android\core\testing\build\test-results\testDebugUnitTest` | Passed independent XML case/name/outcome validation for all 35 cases |

The first Gradle attempt exposed AGP9's legacy generated source-set accessor
cast; the module now uses the typed `LibraryExtension` DSL and reached actual
dependency resolution. That implementation-caused issue is fixed. No failed
Kotlin run is reported as passing. The initial strict-lock failure was resolved
only after the exact path grant and authorized main rebase. The new JVM fixture
probe initially exposed Foundation JSON's escaped slash (`AID\\/`); its wire
assertion was corrected to match the immutable actual export, without changing
any golden bytes. The final local run discovered/executed/passed all 35 cases.

## Real macOS codec/oracle command and output contract

The external-oracle scope of the regular read-only `android-ci.yml` runs:

```text
python tools/android-port/oracle/codec_harness.py run --output <new absolute runner-temp directory>
```

It verifies source blobs/checkout bytes, stages unchanged production code and
hashed exact codec fragments with GPL/MIT notices, requires Swift6.2+, compiles
with `-swift-version 6 -strict-concurrency=complete -parse-as-library`, and
executes the new assertion harness. Missing compiler, compile/execution error,
missing/duplicate/zero/skipped case or malformed/count-mismatched report fails.
Only **28 actual specific cases with nonzero assertions** can produce the
codec evidence; staging and Python contract tests cannot substitute.

Data artifacts: `reference-envelope.json`, actual Foundation `.zlib`
`reference-envelope.meshcoreone`, `channel-crypto-oracle.json`,
`test-results.json`, `codec-evidence.json` and `sources/source-map.json`.
The evidence includes source/fragment/data digests, runtime Swift/macOS,
candidate head and actual hosted run/attempt. Artifacts contain data/logs only;
no compiled binary or Swift source is uploaded/executed by a downstream reader.
The Python consumer compares decoded export semantics and explicitly records
the observed compression container.

**Actual first hosted source execution:** [run37167976130 / macOS job111334847461](https://github.com/cbattlegear/MeshCoreOne-Android/actions/runs/37167976130/job/111334847461),
attempt1, head `6e22d4768e80af943887fe4e9f48c12074f4f89f`, base979dd73b,
macOS26.6.2 arm64, Apple Swift6.3.3. **28 cases / 66 assertions passed; zero
failures/skips.** Source/map/data digests and exact base/head/manifest/policy
binding were read and verified from the actual successful job, not fabricated.
The real exports, crypto output, source map and full evidence are persisted at
`android/core/testing/fixtures/reference-codec/`.

**Critical measured framing:** the actual 1,835-byte compressed source fixture
is **raw DEFLATE**. The RFC1950/zlib probe failed; the raw probe decoded exactly
the 5,544-byte Swift JSON export (SHA256
`a953245efdc5c6f913914fe9c40928eb12332b68dc34ebcacd3df0c00eb1cc8c`).
The compressed SHA256 is
`32e5898f8254eed7ca2744e5206698c00c95e5a1ee8767ece187b8599a012ffc`.
WP-203 must use explicit raw/nowrap framing, such as `Inflater(true)`, rather
than guessing default zlib framing from the Swift `.zlib` algorithm name.
The executed test-only JVM probe consumes these real Swift bytes and asserts
exact export equality, Unix fractions, binary/UUID/UTF-8 wire fields and
truncation rejection. It is not the Android backup DTO/restore implementation.

The compiled binary also exposes
`decode COMPRESSED_INPUT OUTPUT_JSON` for WP-203 to submit **actual** future
Kotlin exports to the same production parser. No Kotlin export or Room restore
is fabricated here. Source transactional restore/deduplication/remapping cases
remain catalogued and pending with WP-203.

## Hosted and final evidence

Final-head hosted outcomes are updated after the same PR reruns on the real
integration base. The initial old-base bootstrap missing-PyYAML/Python-version
failure was fixed in merged main, not waived or edited here. The owned oracle's
SwiftLint function-length error was fixed by extracting its crypto test group,
without disabling the rule. This record is not a privileged parity check,
human/protected approval, dependency license decision, hardware result, signed
upgrade or accepted feature-complete app.
