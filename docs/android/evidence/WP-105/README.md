# WP-105 Cayenne LPP implementation evidence

Repository: `cbattlegear/MeshCoreOne-Android`. Owner: `protocol-porter`.
Source repository/pin: `Avi0n/MeshCoreOne@db14559b39d32322b06477c6ae676112f583db50`.
Actual merged prerequisite/base: `050ac6909c65af8e3fc63e5aaf44b1d240f1d8ac`
([PR #6](https://github.com/cbattlegear/MeshCoreOne-Android/pull/6)).
Locally verified code head: `9c6134af086e31d3cf68ba19a511253850936729`.
Subsequent evidence-only commits do not change this code; the final PR's hosted
artifacts/checks bind their own exact head/base, not this earlier local record.

Dedicated branch: `cbattlegear-stunning-pancake`.
Project session: `2a3ee5db-fb81-40de-b5b2-b2c11b7fe177`.
Execution session: `b57abb9b-1940-4769-be10-187b79ec6e92`.
Shared coordinator reservation/scope grant: `autonomous-WP-105-050ac690`.
No additional worker, session, live controller action or shared-file amendment
was launched.

Canonical manifest SHA-256:
`78a22920beaa5899f9618806b5cd2b27d50399a9b29b4d8dbd79f755717ec746`.
Controller policy revision:
`56bdc53548bc86d631245795dfa38b4fc86048e0e7cbe1c7d5695879b035b42a`.
Trusted protocol profile SHA-256:
`70f8c0cc336e0d852000c4b8bd0db6b29a43d9afbdc670b6aea25009e1673596`.
The source, manifest, profile, policy, build logic, locks, workflows and broader
WP-004 catalog remain unchanged.

## Implemented behavior and consumer contract

The pure-JVM `lpp` package implements all 27 sensor wire types, widths,
source display names/units, immutable associated values and data points.
All original encoder methods are present; typed `add(LPPDataPoint)` additionally
covers every decoder value shape. Endianness, actual IEEE truncation, signed
16/24-bit values, unsigned four-byte values, GPS axis order, RGB, switch and
unsigned Unix seconds are asserted independently.

`LPPDecoder.decode(Bytes)` returns `Complete` or `Incomplete`. Both expose the
immutable accepted `dataPoints` and `consumedByteCount`; incomplete results also
carry a typed record-offset diagnostic and every unconsumed byte. The source's
partial-prefix stop behavior remains available deliberately, without silent
invalid/empty success. `decodeStrict`/`requireComplete` throw that diagnostic.
WP-106's later telemetry integration must retain the diagnostic when exposing
partial points; no event/parser/session consumer is stubbed here.

```kotlin
val encoder = LPPEncoder()
encoder.addTemperature(1u, 25.5)
encoder.add(2u, LPPSensorType.GENERIC_SENSOR, LPPValue.Integer(0xffffffffL))
val payload = encoder.encode()
val completePoints = LPPDecoder.decodeStrict(payload)
```

See [native adaptations](../../deviations/WP-105.md) for atomic typed failures,
24-bit overflow rejection, signed-zero equality, timestamp precision and the
source's unsigned-encoder/signed-decoder current asymmetry. No new dependency,
Android API, GPL application formatting helper, radio/backend/secret operation
or whole-app implementation is introduced.

## All five pinned inputs and disposition

Each input was read and its checkout blob matched the immutable pin. A runnable
`PinnedLPPInputsTest` also binds these five original Git objects, all six Python
fixtures, all 27 named raw enum values and all 18 runnable original cases.

| Input (Git-relative) | Role | Pinned blob SHA |
| --- | --- | --- |
| `MeshCore/Sources/MeshCore/LPP/LPPEncoder.swift` | Primary production; encoder and helpers | `7ded40d1948b6b2e80d553b08f2731aec640f241` |
| `MeshCore/Sources/MeshCore/LPP/LPPDecoder.swift` | Primary production; decoder, types, values and points | `003a46d4395e8c448fb13ef4eda4dc70aa591a87` |
| `MeshCore/Tests/MeshCoreTests/Validation/LPPPythonReferenceTests.swift` | Primary test; every original case below | `1e9b38e49b66ead6bb051d16c6d84468060d6341` |
| `MeshCore/Tests/MeshCoreTests/Fixtures/PythonReferenceBytes.swift` | Read-only cross-reference; six LPP oracles only | `6535c34bed8e45a5ba9f8cf5b7dbb3b72a244832` |
| `MeshCore/LICENSE` | Read-only cross-reference; existing MIT notice retained | `b81a317438752b8ac23cd7f4ce6e6db1bb58e894` |

Six production and nine test Kotlin files have exactly one disposition:
source-derived `PortedFrom` headers (multiple where appropriate), or a bounded
`AndroidOnly: WP-105` declaration. No file has both. The existing protocol MIT
license is compared to the original notice by an actual test. Header coverage is
not counted as behavioral acceptance.

## Original-case and oracle mapping

Every row maps to an actual `@Test` method in `LPPPythonReferenceTest`; names are
unchanged except the two explicit JVM-safe decimal replacements. This table is
local WP evidence, not an edit/approval of the shared WP-004 catalog.

| Original case / parameter family | Native method or assertion coverage |
| --- | --- |
| Temperature 25.5 matches Python | `Temperature 25_5 matches Python`; exact independent fixture |
| Temperature negative round trip | Same method; exact `ff97` and typed negative float |
| Humidity 65 matches Python | Same method; exact independent fixture |
| Analog input 3.3 matches Python | `Analog input 3_3 matches Python`; exact independent fixture |
| GPS SF matches Python | Same method; exact independent fixture |
| GPS decode round trip | Same method; count/channel/type and all three source tolerances |
| Barometer 1013 matches Python | Same method; 1013.2 exact independent fixture |
| Accelerometer 1g matches Python | Same method; exact independent fixture |
| Accelerometer decode round trip | Same method; count/channel/type and all three source tolerances |
| Multi-sensor payload | Same method; ordered temperature/humidity/barometer assertions plus exact bytes |
| Voltage encoding | Same method; MeshCore `0x74`, exact `017c`, typed decode |
| Illuminance encoding | Same method; exact `03e8`, integer decode |
| Digital IO encoding | Same method; both exact frames and true/false associated values |
| Gyrometer encoding | Same method; exact positive/negative/zero axes and source tolerances |
| Load positive decodes as 3-byte signed divided by 1000 | Same method; independently supplied `003039`, channel/type/value |
| Load negative round trips through 24-bit sign extension | Same method; independently supplied `fffa24`, type/negative value |
| Load consumes three bytes so the next datum stays aligned | Same method; independently supplied load then temperature, count/types/values |
| Generic sensor decodes high bit set as a large positive integer | Same method; independently supplied `80000000` equals positive 2147483648 |

`PythonLPPReferenceBytes` copies the six `cayennelpp` byte arrays verbatim from the
pin, never from the candidate encoder. Those fixtures have six exact encoder
comparisons and six additional decoder cases consuming the independent bytes.
The remaining original hand-authored expected bytes are retained unchanged.
`LPPGoldenVectors` supplies a separate fixed byte/value vector for each of the
27 types: 27 encoder, 27 decoder and 27 raw-record cases. Expected packets are
not produced by Kotlin code under test.

Additional coverage includes all 229 unknown wire IDs, every one of the 85
possible truncated-value lengths both with/without a valid prefix, partial
headers, duplicate channels, high bits, signed/unsigned extrema, every raw
boolean byte, each vector/GPS error field, IEEE truncation, GPS physical/wire
limits, colour, unsigned timestamps, wrong value/raw widths, atomic failures,
reset/snapshot immutability and signed-zero equality/hashing.
Eighteen seeded property cases execute 9000 supplementary samples against
standard arithmetic/JDK big-endian bytes; they do not replace the fixed vectors
or inflate the JUnit discovery count.

## Actual local verification

Toolchain: Windows, Python **3.12.4**, Temurin **21.0.12.1+1**, Gradle **9.8.0**,
Kotlin **2.3.20**, AGP **9.4.1**, bytecode **17**, compile SDK **37.2**, min **31**,
target **37**, coroutines **1.10.2**. The documented launcher ran the real
environment preflight, stripped non-allowlisted variables and used this session's
private caches, one worker, in-process Kotlin, 640MiB build heap, 512MiB build
metaspace and 256MiB test heap. No host, global environment or signing-key setting
was changed. JDK/SDK paths and cache binaries are not committed.

Exact combined command at the verified code head, **passed**:

```powershell
& .\android\scaffold\invoke-gradle.ps1 -ConstrainedMemory -BuildHeap 640m `
  -GradleArguments @(':core:protocol:test', 'validateModuleGraph', `
    'resolveScaffoldDependencies', 'runtimeDependencyInventory', `
    '--dependency-verification', 'strict', '--no-build-cache', `
    '--rerun-tasks', '--quiet')
```

Actual XML testcase/outcome nodes and all suite count attributes were reparsed:
**421 discovered/passed, 0 failed/errors/skipped**, comprising the unchanged
**84 existing** cases and **337 new LPP** cases.

| LPP suite | Discovered/passed |
| --- | ---: |
| Original cases and independent Python decoding | 24 |
| Per-type independent encode/decode/raw vectors and ordering | 83 |
| Metadata and exact lookup | 29 |
| Malformed/unknown/truncated prefix diagnostics | 31 |
| Boundaries, all original wrappers, failures and seeded properties | 161 |
| Immutable/equal values, buffers and snapshots | 5 |
| Immutable inputs, MIT notice and original-case binding | 4 |

| Exact additional command | Actual result |
| --- | --- |
| `python .\tools\android-port\controller\verification_config.py --check` | Passed; frozen overlay/manifest unchanged |
| `python .\tools\android-port\controller\validate.py` | Passed; 1866 pinned inputs, 65 WPs, 185 edges, original ownership/gates unchanged |
| `python .\tools\android-port\portmap.py` | Passed; 65 total mappings, all 15 LPP Kotlin files with unambiguous provenance |
| `python .\tools\android-port\controller\workflows.py` | Passed; YAML/trust-boundary assertions, not live publisher proof |
| `python .\tools\android-port\controller\test_runner.py --quiet` | 165 discovered/run/passed, 0 failures/errors/skips; controller fixtures only |
| `python .\android\scaffold\sync_notices.py` | Passed; pinned GPLv3/MIT/Apache notices/artwork match; no legal approval implied |
| `python -m unittest discover -s .\android\scaffold -p 'test_*.py' -q` | 15 run/passed, 0 failures/errors/skips; environment/metadata/tool regressions |
| `python .\tools\android-port\controller\validate.py --gate-base 050ac6909c65af8e3fc63e5aaf44b1d240f1d8ac --gate-candidate 9c6134af086e31d3cf68ba19a511253850936729` | Passed; no protected paths/human gate required; explicitly nonauthoritative offline audit |
| `git diff --check` | Passed |

An initial test compilation failed because a cross-variant `assertNotEquals`
needed its sealed `LPPValue` type specified explicitly. That was corrected;
no expected packet was changed, test skipped or policy relaxed. A separate
read-only PowerShell startup failure under shared-host memory pressure was
retried successfully without machine/process/pagefile changes.

## Artifacts, acceptance and handoff boundary

[local-verification.json](local-verification.json) records the exact code head,
outcomes and per-suite raw-report SHA-256 values without private paths or host
identifiers. The raw local reports are preserved in the private session bundle
`wp105-local-9c6134af`. Repository-relative generated locations are
`android/core/protocol/build/test-results/test/`,
`android/build/reports/scaffold/module-graph.tsv` and
`android/build/reports/scaffold/runtime-dependencies.tsv`.

The consolidated `android-ci.yml` selects the complete protocol suite in its
single Linux Gradle invocation. The exact-commit job result and log are the
authoritative reproducible proof; no duplicate protocol-result artifact is
retained.

| Acceptance ID | Implemented/tested evidence |
| --- | --- |
| `WP-105-behavior` | Complete vocabulary and methods; independent Python and per-type encode/decode bytes, units/scales/signedness |
| `WP-105-boundaries` | Source partial behavior with explicit diagnostics; malformed, unknown, truncated, signed/high-bit/GPS/range/atomic-failure cases |
| `WP-105-source-test-parity` | All five inputs, all 18 original cases and all six oracle fixtures bound to real runnable assertions; complete file disposition and preserved MIT notice |

No iOS/macOS oracle run, Android instrumentation/physical device, radio,
hardware, backup, release signing or license approval is claimed. Canonical
WP-105 dispatch verification remains unconfigured with its controller owner;
this authorized implementation uses the explicitly requested, actually existing
protocol/scaffold commands. No shared catalog/manifest progress, formal review,
privileged check or merge receipt is fabricated. The coordinator performs the
independent review and serialized merge of the non-draft main-targeted PR.
