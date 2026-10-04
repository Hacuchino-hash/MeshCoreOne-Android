# WP-107 complete JVM session component

**Independent review repair in progress:** the coordinator's read-only review
of historical ce93/0394 identified eight real lifecycle/correlation/cache
defects. Normal pre-repair protocol run37212608599/attempt1 at
`0a94e1386f41b7b35dcb150574add0964af79a21` executed22 deterministic
regressions: **22 failed on each host**, while all4,674 prior cases still
passed. This supersedes any implication that the old green suite established
complete correctness. Scoped repairs and additional contract cases are being
implemented on the same PR; final positive exact-head proof remains pending.
The local resource hold and separately owned WP-201 collector repair are not
permission to waive validation or mark the component complete.

**Current reconciliation:** the owning branch has been rebased onto the
coordinator-verified actual main `dc15f1ba445acf3230383ea68d4827c592f3fafa`.
Protocol production and test bytes are unchanged from historical head
`ce93bfaa22b438aed7e8b7a903a611270a985cd3`. The prior0394-base results below
remain historical, not current-base authority. Fresh proof is retained
separately in [reconciled-dc15](reconciled-dc15/README.md).

The full current-base protocol, model166, Room45, neutral4, locale25 and
actual integrated APK pass locally. **The root aggregate remains blocked**:
the newly merged WP-201 collector's global WP-201-only candidate-write guard
rejects this correctly scoped WP-107 PR. No unowned guard/hook was removed or
bypassed; its exact source blob/failure and coordinator amendment request
are recorded separately. Current-head ordinary hosted proof must not be
represented by the old successful0394-base runs.

Repository `cbattlegear/MeshCoreOne-Android`; owner `protocol-porter`.
Runtime owner `ab9b53e0-bebf-422f-980b-819caef0b1cf`, native project-session
alias `44411fac-bbc0-4adc-bed3-39cd46866936`, dedicated branch
`cbattlegear-bookish-lamp`. The ACTIVE coordinator receipt
`autonomous-WP-107-01d2852a` preceded the first edit; see
[authorization.json](authorization.json). No shared write amendment or
additional worker was used.

| Binding | Revision |
| --- | --- |
| Initial clean/leased actual main | `01d2852a45e152efcf1176607ddebd26ff1bc672` |
| Coordinator-verified actual integration base (CI-only PR16) | `0394b83c9b47fa0d7198e2d631f7ddb313cd370d` |
| Read-only source | `db14559b39d32322b06477c6ae676112f583db50` |
| Read-only tree | `8918fdc604341e6996a68c88f6bb1c02b9c2f87e` |
| Semantic manifest | `78a22920beaa5899f9618806b5cd2b27d50399a9b29b4d8dbd79f755717ec746` |
| Semantic policy | `56bdc53548bc86d631245795dfa38b4fc86048e0e7cbe1c7d5695879b035b42a` |

All five prerequisites were verified as actual merged ancestors before work.
Only this owning branch was fast-forwarded to the coordinator's CI-only base;
the initial binding is retained. Exact final PR-head hosted evidence is separate
from this local record. Kotlin input fingerprints avoid a self-referential
head/evidence-only commit cycle.

## Concrete behavior and source accounting

The session package implements all source role interfaces, `MeshCoreSession`,
contact management and source helpers: startup/stop/connection observations,
one actual raw drain through the real parser/dispatcher, tracked/filtered
streams, pending waits, request serialization and correlation, config/capability
queries and updates, full/incremental contacts/cache/flags/path/card operations,
channel configuration/pipelines/messages/datagrams, coalesced/manual/automatic
message drains, login/logout/commands/keep-alive, status/telemetry/owner/MMA/ACL/
neighbour pagination/regions, discovery/control/trace and real radio-command
signing/private-key workflows.

Whole exchanges and compounds hold a real mutex across suspension. Waiters,
ACK listeners and explicit binary contexts register before sends. Connection
generations include jobs, queues, contexts, retired tags, caches and listeners.
Cancellation, send uncertainty, terminal IO and failed restorations are explicit;
no packet-layout guessing, invented sequence, provider replacement, no-op
method or unowned background worker substitutes for behavior.

[source-map.json](source-map.json) accounts for all24 production,13 test and1
support input with pinned blobs and original declarations/defaults/overloads.
It explicitly retains GPL app-error provenance, canonical MIT error reuse and
test-only support adaptations. [source-cases.json](source-cases.json) binds the
actual original IDs to real JUnit names/classes.

**73/73 owned original declarations pass. 12/12 real deferred seams pass**:
the seven WP-103 Session scenarios, two WP-104 ContactManager scenarios, two
WP-106 filtered-session wrappers and WP-104's legacy asynchronous flag-update
scenario. Previously executed198/205 parser cases and50 parser-owned cross
seams are unchanged baseline evidence, not relabeled as new WP-107 assertions.
The global frozen catalog and historically pending manifest are unchanged.

## Actual local commands and results

Windows, Python3.12.4, checksum-pinned Temurin21.0.12.1+1, Gradle9.8.0,
Kotlin2.3.20, AGP9.4.1, bytecode17, coroutines1.10.2/BC1.86,
SDK37.2/rev1 and build-tools37.0.0. Every Gradle shell declares private JDK/SDK,
Gradle/Android user directories and uses the credential-stripped launcher.
One worker, in-process Kotlin, build640m/metaspace512m, test256m/metaspace256m,
SerialGC and two processors preserve shared-host limits.

The initial declared readiness check failed for missing explicit toolchain
state. The existing controller provisioned only the exact locked archives
privately; no Android CLI bootstrap/helper was executed. Current-base preflight
and strict dependency preparation then passed.

```powershell
python .\tools\android-port\controller\ci.py provision `
  --root <private-toolchain> --accept-sdk-license
python .\tools\android-port\controller\ci.py preflight `
  --state <private-environment.json> --output <private-evidence> --local
python .\tools\android-port\controller\ci.py run --stage prepare `
  --state <private-environment.json> --output <private-evidence> --local

& .\android\scaffold\invoke-gradle.ps1 -ConstrainedMemory -BuildHeap 640m `
  -GradleArguments @(':core:protocol:test', '--tests', `
    'com.meshcoreone.android.core.protocol.session.*', `
    '--dependency-verification', 'strict', '--no-build-cache', '--quiet', `
    '--project-cache-dir', '<private-project-cache>')

& .\android\scaffold\invoke-gradle.ps1 -ConstrainedMemory -BuildHeap 640m `
  -GradleArguments @(':core:protocol:test', 'validateModuleGraph', `
    'runtimeDependencyInventory', 'resolveScaffoldDependencies', `
    '--dependency-verification', 'strict', '--no-build-cache', `
    '--rerun-tasks', '--quiet', '--project-cache-dir', '<private-project-cache>')

& .\android\scaffold\invoke-gradle.ps1 -ConstrainedMemory -BuildHeap 640m `
  -GradleArguments @(':app:assembleDebug', '--dependency-verification', `
    'strict', '--no-build-cache', '--quiet', `
    '--project-cache-dir', '<private-project-cache>')

python .\android\scaffold\inspect_apk.py
python .\docs\android\evidence\WP-107\verify_evidence.py `
  --base-sha 0394b83c9b47fa0d7198e2d631f7ddb313cd370d --check
```

All final commands above passed. Source-role/API, concurrency, malformed,
unsigned, UTF8, timeout and cancellation assertions execute with deterministic
coroutine scheduling, without sleeps/xfails/ignored cases. TCP tests use genuine
loopback sockets, not connected-state mocks. Independent packet expectations
come from immutable original Git objects, Python `hashlib` and unchanged
WP-102 RFC8032 fixtures, never session output.

| Actual suite | Discovered/passed | Failed/errors/skipped |
| --- | ---: | ---: |
| Five owned original suites | 73 | 0/0/0 |
| Real deferred cross-component seams | 12 | 0/0/0 |
| Immutable Python session packet bridges | 31 | 0/0/0 |
| Complete remaining API/compound operations | 26 | 0/0/0 |
| Lifecycle/correlation/capability boundaries | 48 | 0/0/0 |
| Additional compound/text/list/ownership families | 27 | 0/0/0 |
| GPL error-code test/reference assertions | 7 | 0/0/0 |
| Real role-backed support consumers | 2 | 0/0/0 |
| Actual TCP session integration | 3 | 0/0/0 |
| **WP-107 total** | **229** | **0/0/0** |
| **Full protocol (56 nonzero suites)** | **4,674** | **0/0/0** |

The first compile caught return-type inference in recursive drain launchers;
the first test compile caught a sealed-state assertion's generic type. The
first original run discovered73 cases and20 reported failures: expected
suspended exceptions were also reported by `backgroundScope`. The corrected
test driver retains the actual failure for explicit typed assertions and
propagates cancellation; it does not ignore errors. The expanded190-case run
found four supplementary failures: two coroutine stack-recovery cause-chain
assertions and a miscopied new RFC literal used by two tests. The assertions now
inspect the real canonical cause and reuse the already independently verified
immutable crypto fixture. A later added test needed its config import.
Actual failure logs/XML remain in session artifacts; no original expected
packet, protected fixture, task, dependency, budget or policy was weakened.

Additional final validations all passed:

| Exact command | Actual outcome |
| --- | --- |
| `python .\tools\android-port\controller\verification_config.py --check` | Unchanged supervised overlay valid |
| `python .\tools\android-port\controller\validate.py` | Complete1,866 pinned inputs /65 WPs /185 edges valid |
| `python .\tools\android-port\controller\workflows.py` | Trusted workflow boundary checks valid |
| `python .\tools\android-port\portmap.py` | All production/test provenance valid |
| `python .\tools\android-port\test_inventory.py --check` | Unchanged468 paths /5,133 original declarations |
| `python .\tools\android-port\extract_vectors.py --check` | Unchanged49 original independent vectors |
| `python .\android\scaffold\sync_notices.py` | Pinned GPL/MIT/Apache/artwork match; not legal admission |
| `python .\tools\android-port\controller\test_runner.py --quiet` |187 discovered/run/passed,0 failed/errors/skips |
| `python .\tools\android-port\oracle\run_tests.py --quiet` |83 discovered/run/passed,0 failed/errors/skips |
| `python -m unittest discover -s .\android\scaffold -p 'test_*.py' -q` |15 passed |
| Actual `controller.apk_alignment.inspect_alignment(...)` | zipalign `-c -P 16 4` and all four ELF PT_LOAD families passed |

## Persistent artifacts and boundaries

[local-results.json](local-results.json) binds every Kotlin input and all56
complete raw XML reports in `junit/protocol/`, including the entire unchanged
baseline. Stored text is canonical LF; original runner byte sizes/SHA256 values
are retained separately. Every testcase/outcome/log node is preserved and
reparsed; no XML was trimmed or a case counter fabricated.

`reports/` preserves actual strict graph/runtime TSV (470 edge rows/114 runtime
artifact/POM rows). The actual APK is38,792,206 bytes, SHA256
`8494a033a7312890d467578bf2c01e523f7a5d9610b535cd5bdb1ce159d2b0fa`.
Inspection verifies `.debug`, min31/target37, launcher/notices and fixture
exclusion. The evidence verifier finds the concrete session/core/contact/
serializer classes in actual DEX, rejects test/reference classes and GPL
test-error prose, and checks the existing BC MIT notice. APK bytes/private
toolchains remain outside Git. [apk-alignment.json](apk-alignment.json) records
actual static16KiB zip/ELF proof only.

See [WP-107 adaptations](../../deviations/WP-107.md), especially uncorrelated
late-reply quarantine, retained-link ownership and partial-channel reconciliation.
Windows/Linux normal same-head CI/raw artifact proof belongs to the final PR;
this local record is not a fabricated hosted run or a formal parity verdict.
No iOS/macOS, physical API/radio, backup, license, hardware, release signing,
activation, protected publisher, completed app graph or downstream WP claim
is made. The coordinator independently reviews and lands the exact published
head; this worker never self-approves or merges.
