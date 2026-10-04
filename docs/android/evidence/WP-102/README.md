# WP-102 wire-crypto evidence

**Implementation in progress; acceptance blocked on serialized dependency
wiring. No candidate crypto test run has passed yet.**

Repository: `cbattlegear/MeshCoreOne-Android`. Owner: `protocol-porter`.
Session: `dbffc569-419e-4dcc-ace0-836b58f6435c`.
Managed branch: `cbattlegear-legendary-adventure`.
Initial merged base: `4331a4dddd13126ab05f4b4d74e654f313c5d414`.
Read-only source: `db14559b39d32322b06477c6ae676112f583db50`.
Manifest canonical SHA256:
`78a22920beaa5899f9618806b5cd2b27d50399a9b29b4d8dbd79f755717ec746`.
Actual canonical policy SHA256 at that base:
`3a96956793753399f3fe9bfb06927df3ad70998478731f3f29889a57407bac20`.
The kickoff supplied
`56bdc53548bc86d631245795dfa38b4fc86048e0e7cbe1c7d5695879b035b42a`;
the difference was reported to the coordinator. No policy pin/file, automation
setting, fleet, protected gate or self-approval was changed. The coordinator
subsequently reaffirmed this worktree's active scoped coding authorization.

The kickoff/renewal authorizes the four exact manifest crypto/evidence scopes.
No shared Gradle/catalog/lock/checksum path has been edited under that scope.
The coordinator records the lease identity; an actual formal receipt is not
manufactured here. A later implementation commit/PR/check suite supplies the
exact candidate head, not a fabricated self-referential evidence SHA.

## Behavioral implementation

The seven production files provide source channel/direct decrypt outcomes,
strict UTF8/NUL semantics, full-secret HMAC-SHA256 and two-byte constant-time tag
comparison, source AES128 ECB/no-padding/zero-extension, X25519 agreement,
validated Ed-public-to-X conversion, SHA512 seed expansion, scalar extraction,
and real seed/**expanded** Ed25519 signing/verification. Existing immutable
`Bytes` and `model.sha256` are reused.

Read consumers include WP-212 `RxLogService` (64-byte firmware key truncation,
converted Ed contact keys, nullable DM text with useful timestamp), WP-204
`KeyGenerationService` (SHA512 expansion/clamping and 64-byte validation), and
WP-107 `MeshCoreSession+Signing` (radio-driven signing, not a local fake).
The [deviations](../../deviations/WP-102.md) specify typed invalid-key adaptations,
known failure ordering, native types and genuine platform limits.

| Acceptance | Evidence and current status |
| --- | --- |
| `WP-102-behavior` | Original scenarios and independent byte assertions implemented; **not passed**, candidate build lacks the vetted dependency |
| `WP-102-boundaries` | Explicit errors, malformed/tag/key/curve/UTF8/padding/format tests implemented; **not passed** pending runnable candidate JUnit |
| `WP-102-source-test-parity` | All 22 original declarations mapped below, type-loop rows retained, pinned-input/reflection assertions added; **not passed** until the actual native cases execute |

## Complete original case mapping

All source paths below are under `MeshCore/Tests/MeshCoreTests/` at the exact
reference. Production inputs are the three corresponding protocol files in
the frozen manifest; all six owning blobs, MIT license and Python fixture
cross-reference are checked by `PinnedCryptoInputsTest`.

| Original suite/case | Concrete Kotlin assertion / independent fixture |
| --- | --- |
| Channel `Decrypt success` | `ChannelCryptoTest`; `channel-normal`, also the actual compiled Swift packet |
| Channel `Decrypt wrong key` | Exact `.HmacFailed` against `channel-wrong-key` with original wrong secret |
| Channel `Decrypt corrupted MAC` | Both source MAC bytes XORed with FF; exact `.HmacFailed` |
| Channel `Decrypt payload too short` | Original `00010203`; exact `.PayloadTooShort` |
| Channel `Decrypt empty message` | `channel-empty`; timestamp0/type0/empty text |
| Channel `Decrypt long message` | `channel-long`; same original full text, multi-block independent ciphertext |
| Channel `Decrypt unicode message` | `channel-unicode`; same original CJK/emoji UTF8 |
| Channel `constants` | All five original constants |
| Channel `Decrypt with different txtTypes` | Same source loop **0,1,2**, independently specified `channel-type-*` rows |
| Channel `Decrypt with 32-byte secret uses first 16 bytes for AES` | Same original 32-byte secret and text; independent full-key HMAC and first16 AES; truncating the HMAC key fails |
| Direct `Decrypt success` | `DirectMessageCryptoTest`; `direct-normal`, with fixed independently proven RFC7748 keys replacing source random keys |
| Direct `Decrypt wrong key` | Deterministic distinct private key; exact `.MacMismatch` |
| Direct `Decrypt corrupted MAC` | Same source offsets2/3 and FF mutations; exact `.MacMismatch` |
| Direct `Decrypt payload too short` | Original `00010203`; exact `.InvalidPayload` |
| Direct `Decrypt empty message` | `direct-empty`; same timestamp0/type0/empty text |
| Direct `Decrypt unicode message` | `direct-unicode`; same original UTF8 |
| Direct `Extract timestamp` | `direct-extract`; original timestamp via convenience consumer API |
| Direct `constants` | All six original constants |
| Direct `Invalid key length` | Same 24-byte packet, two-byte private input and zero32 public input; exact `.KeyError` |
| Ed `Public key conversion round-trip with CryptoKit` | `Ed25519ToX25519Test` uses independently specified RFC keys/scalars, both ECDH directions and expected conversions; no claim Kotlin executes CryptoKit |
| Ed `DM decrypt with converted Ed25519 keys` | `ed-converted-direct`; same original Hello/timestamp semantics and independently produced full packet |
| Ed `Conversion rejects invalid inputs` | Original 0/16-byte families map to explicit typed length failure, not silent nil |

There are **10 + 9 + 3 = 22 original declarations**, not 22 generated files or a
claim that each loop row is a separate declaration. Additional native factories
exercise every independent fixture, every MAC bit, truncation/alignment,
secret/key/signature lengths, noncanonical/low-order peers, expanded clamp bits,
nullable invalid direct text, NULs, high-bit timestamps/type bytes, source error
precedence and untouched global providers. Real JUnit testcase counts are still
required; filenames/reflection alone are not acceptance.

## Independent bytes and provenance

`generate_vectors.py` imports only existing Python `cryptography`48.0.0 plus
standard-library SHA512/HMAC. It never invokes/imports Kotlin or asks candidate
code for expectations. It uses three official RFC8032 section7.1 vectors,
RFC7748 section6.1 keys/shared secret, and source test parameters. The library's
public keys/signatures/shared secret must independently match the literal
standards before fixture generation succeeds.

Generated, fully consumed fixtures:

| File | Rows | Contents |
| --- | ---: | --- |
| `message-vectors.tsv` | 74 | All source messages plus zero-length/boundary/UTF8/NUL/non-block/short and variable-secret cases |
| `key-vectors.tsv` | 19 | RFC plus sixteen deterministic independent seeds; full SHA512-expanded64, Ed/X public keys and X private32 |
| `signature-vectors.tsv` | 13 | Three official standards and independent byte inputs of lengths16/31/32/33/120/121/255/256/1024/65536 |

`vector-generation.json` records the source blobs, independent tool/version,
counts and complete canonical-LF fixture SHA256 values. Only CRLF/LF Git text
normalization differs across hosts; hex wire bytes are never normalized.
The Kotlin test loader fails on missing/extra/duplicate/malformed/zero-count
cases. No `@Ignored`, `@Disabled` or candidate-generated expected packet exists.
JDK21 Ed25519 is an **independent test verifier only**, not production Android
provider proof.

Both existing actual Swift oracle packets match the independent Python bytes:
normal and high-bit UTF8, from
`android/core/testing/fixtures/reference-codec/channel-crypto-oracle.json`
with SHA256
`f6a323cf0e351d2dc7eb5bda26cb808ede7fb03dcb2bbe132e7735726deaa1f7`.
WP-004 executed that source oracle; **Swift was not executed here**. The
cross-reference `PythonReferenceBytes.swift` contains command/LPP/control
fixtures, not invented crypto ciphertext; its bytes and canonical owner are
unchanged. Production has no `core:testing` dependency.

An initial RFC8032 test3 literal transcription was rejected by the independent
library before producing fixtures. It was corrected against the actual RFC
signature, not against candidate Kotlin. All three standard literals then
passed, as did both prior Swift oracle comparisons.

## Actual commands and outcomes so far

All Gradle calls use the existing allowlist launcher, exact existing verified
JDK21.0.12.1/SDK37.2/build-tools37.0.0, bytecode17 and separate private WP-102
user/Android caches. Environment paths are session-local, not committed.
Memory settings are build-process options, not application runtime behavior.

| Actual command | Result |
| --- | --- |
| `python android\scaffold\check_environment.py` | **Passed**, exact tools and isolated environment; no installation/license/device approval |
| `invoke-gradle.ps1 -ConstrainedMemory -BuildHeap 640m -GradleArguments @(':core:protocol:test','--dependency-verification','strict','--quiet')` | **Passed on clean initial base only**: 522 testcase nodes, 0 failures/errors/skips; not candidate crypto acceptance |
| `python tools\android-port\controller\validate.py` | **Passed**: pinned1,866 reference files, 65 WPs, 185 edges and expected manifest digest |
| `python tools\android-port\controller\verification_config.py --check` | **Passed**; owning feature verification remains unconfigured in the canonical manifest, not silently amended |
| `python tools\android-port\portmap.py` | **Passed** for current source/test traceability, not a behavioral verdict |
| `python tools\android-port\test_inventory.py --check` | **Passed**: all468 input paths, 5,133 original declarations, 70 parameterized declarations and 382 declared parameter rows; **0 claimed ported cases** in the canonical frozen catalog |
| `python tools\android-port\extract_vectors.py --check` | **Passed**: all49 preexisting pinned independent fixture entries; no candidate Kotlin executed and no shared expectations changed |
| `python docs\android\evidence\WP-102\generate_vectors.py --regenerate` | **Passed** after correcting the rejected RFC transcription: 74/19/13 complete independent rows |
| `python docs\android\evidence\WP-102\generate_vectors.py --check` | **Passed** against those independently produced bytes and prior Swift oracle |
| `invoke-gradle.ps1 -ConstrainedMemory -BuildHeap 640m -GradleArguments @(':core:protocol:test','--tests','com.meshcoreone.android.core.protocol.crypto.*','--dependency-verification','strict','--quiet')` | **Failed/blocked at compileKotlin**: unresolved `org.bouncycastle` APIs, because the vetted artifact is not yet admitted/wired. No candidate JUnit results produced |

Existing baseline XML under
`android/core/protocol/build/test-results/test/` is **old baseline evidence**.
It must not be reused to claim crypto cases passed after a compilation failure.
The normal hosted protocol workflow runs all cases on Windows/Linux and retains
raw JUnit; it needs no workflow edit for this WP.

## Exact dependency gap / amendment

Required inspected artifact: `org.bouncycastle:bcprov-jdk18on:1.86`.
Origin: Maven Central publisher directory
`https://repo.maven.apache.org/maven2/org/bouncycastle/bcprov-jdk18on/1.86/`.
The downloaded JAR/POM independently match the publisher `.sha256` responses:

| Artifact | SHA256 |
| --- | --- |
| JAR | `2af190b300cbb0b35e248ccf5f4a06b6072030aeb3da7a98ec73abe5b4cb371f` |
| POM | `970b70cb75a184639b6fb9ba3c465aca6a4e1a5f3fff4d65ce8574b2fd79ef38` |
| Publisher sources JAR checksum, not downloaded/executed | `34a8b7dcb030a08ddd5356d3d5be962f9299ca5275e1460273186f8fdbe44c34` |

The exact artifact has no POM dependencies; base curve classes are major52
(Java8). Actual `javap` inspection verifies `X25519.calculateAgreement`,
`Ed25519.validatePublicKeyFull`, field operations, and
`Ed25519.ExpandedKey.expandPrivateKey/generatePublicKey/sign`. The last API
supports actual firmware-expanded signing without treating expanded bytes as a
seed or implementing curve/signature arithmetic locally.

`META-INF/LICENSE.md` contains the MIT copyright/permission/disclaimer,
Copyright(c)2000-2026 The Legion of the Bouncy Castle Inc. Preserve it for
distribution. Artifact facts are not fabricated legal/release approval.
Existing GPL application/MIT MeshCore notices are untouched.

Only the coordinator can relay the currently unleased shared amendment:
`android/core/protocol/build.gradle.kts` (private implementation dependency),
`android/gradle/libs.versions.toml` (exact artifact/version),
`android/gradle/verification-metadata.xml` (only verified POM/JAR entries, after
WP-005's serialized annotation repair), and actual affected existing runtime
lockfiles in `android/gradle/dependency-locks/`. No broad lock/checksum reset,
disabled verification, new dependency version, provider replacement or shared
build/workflow tooling change is proposed.

After admission, run actual targeted/full `:core:protocol:test`, then the
existing strict `validateModuleGraph`, `resolveScaffoldDependencies`,
`runtimeDependencyInventory` and dependent Android assembly. Record complete
new raw JUnit and exact-head normal hosted results before publishing a completed
PR/handoff. Physical API31/API37, HIL, legal release and signing gates have not
been executed and are not claimed from these JVM/inspection steps.
