# WP-202 evidence boundary

Repository `cbattlegear/MeshCoreOne-Android`; owner
`data-persistence-engineer`; branch `cbattlegear-potential-spork`.
App session `e83ac80e-ce2c-45c1-9574-3e04f1de87a6`, project
`663db92c-ed50-4a77-aded-bda85a7c503a`, native project alias
`8a3ef5a7-3f45-4c93-8ecb-e252fa000482`.

Bound initial HEAD/base: `dc15f1ba445acf3230383ea68d4827c592f3fafa`.
Reference: `db14559b39d32322b06477c6ae676112f583db50`,
tree `8918fdc604341e6996a68c88f6bb1c02b9c2f87e`.
Manifest `78a22920beaa5899f9618806b5cd2b27d50399a9b29b4d8dbd79f755717ec746`;
policy `56bdc53548bc86d631245795dfa38b4fc86048e0e7cbe1c7d5695879b035b42a`.
Receipt `autonomous-WP-202-dc15f1ba` is the canonical package/evidence lease,
not permission to edit shared build/lock/DAO/schema/contract inputs.

All 26 primary production, 13 original test and 3 support inputs were
read, including the full 4,200-line store test and 2,362-line support mock.
The frozen catalog contains 208 single declaration families. Current
authored native annotations bind 190 uniquely; the eighteen historical
Apple migration declarations remain explicit review dispositions, not
invented Android upgrades or passing assertions.

[`migration-dispositions.json`](migration-dispositions.json) enumerates every
one of those eighteen frozen IDs/blobs, the original effect, a concrete
authored native assertion, its limits and the retained backup/preference/
sync consumers. Status is **proposed-needs-coordinator-review**. The new
`InitialSchemaMigrationBoundaryTest` exercises actual v1 defaults, all
fourteen radio-owned tables, explicit/nullable keys, badge filters, exact
negative/fractional dates, same-v1 file reopen, and missing/malformed stored
date failures when the admitted test runner becomes available. It does not
relabel any of those as Apple migration or compatible backup execution.

## Actual attempts

| Command / arguments | Actual result |
| --- | --- |
| `python -B tools\android-port\controller\verification_config.py --check` | Passed frozen manifest overlay validation; feature acceptance remains unconfigured |
| `python -B tools\android-port\controller\validate.py` | Passed pinned source, 65 WPs, 185 edges and ownership inventory |
| `python -B tools\android-port\controller\runtime_inputs.py` | Passed all 77 committed/checkout runtime inputs at the initial head |
| Credential-stripped `python -B android\scaffold\check_environment.py` before provision | Failed: missing explicit JDK/SDK/private cache inputs; PATH exposed JDK11 |
| `python -B tools\android-port\controller\ci.py provision --root <new-own-session-toolchain> --accept-sdk-license` | Passed exact checksummed archive-only installation; no SDK CLI/helper bootstrap or global configuration |
| `python -B tools\android-port\controller\ci.py preflight --state <own-environment.json> --output <own-evidence> --local` | Passed actual isolated `check_environment.py`, exact JDK21.0.12.1/SDK37.2/build-tools37.0.0/private cache checks |
| `android\scaffold\invoke-gradle.ps1 -ConstrainedMemory -BuildHeap 512m -BuildMetaspace 512m -TestHeap 256m -TestMetaspace 256m -GradleArguments @(':core:data:compileDebugKotlin', '--project-cache-dir', <own-private-project-cache>, '--dependency-verification', 'strict', '--no-build-cache')` | Failed in 4m33s, 32 executed tasks: missing module compile classpath and two undeclared DAO operations |

The complete initial compile log is retained in the session evidence,
SHA256 `db54f8f544aeee558fe76d429c2b41a2c8312dc11d9445b3a06b7978ce604d7b`.
No native suite was executed, so authored annotations are **not** discovery,
passed tests or acceptance evidence. No candidate APK, lint, full graph,
hosted same-head artifact, signing, device, radio or backup oracle result is
claimed.

The explicit local Gradle/JVM resource hold remains in effect. The completed
first command left no live own JVM: its only private daemon log identified
PID52716, which was verified no longer running without a stop launch or any
global/name-based process kill. Lightweight collector inventory verifies
the exact190 bindings/18 individual proposals; mandatory collection still
fails closed on those unresolved review dispositions.

The exact shared amendment request is
[`amendment-request.json`](amendment-request.json).
It reuses admitted pins and does not request root lock/XML, version/catalog,
schema/model/contract, source/golden, policy/acceptance or activation changes.
See [native adaptations](../../deviations/WP-202.md).
