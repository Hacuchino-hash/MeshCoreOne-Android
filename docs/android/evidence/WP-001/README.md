# WP-001 supervised dependent draft evidence

**Result:** Prepared draft; **human-review pending**. None of the WP-001 acceptance
IDs is marked passed. This is traceability/local verification, not a trusted
publisher bundle, completion receipt or dependency/activation authority.

## Identity and scope

| Field | Recorded value |
| --- | --- |
| Repository / WP / content charter | `cbattlegear/MeshCoreOne-Android` / WP-001 / unchanged `android-architect` profile |
| Execution | Default supervised session; no additional agents/factories/sessions |
| Managed branch | `cbattlegear-android-architecture-contracts` |
| Parent branch / PR | `cbattlegear-android-automation-bootstrap` / #1, open and unmerged when prepared |
| Initial and parent base SHA | `9cd4fbd4f697c285b9729f016d552e693b21eac1` |
| Candidate head | Commit containing this record; exact final SHA is reported in the dependent draft PR/coordinator handoff, avoiding a self-referential commit hash |
| Pinned reference commit / tree | `db14559b39d32322b06477c6ae676112f583db50` / `8918fdc604341e6996a68c88f6bb1c02b9c2f87e` |
| Canonical manifest binding SHA-256 | `f3fd3a0a51841a8fb43d3f2c4b3953e6ef564d4e96e74f4e3035200face5c90e` |
| Trusted policy semantic revision | `1b8db2a4fc8c3049584d8c0bedf03880c6625fc7729b3f84ef26c80eb40ef910` |
| Shared supervised-draft lease | `draft-WP-001-7736e7de-9cd4fbd4`; active coordinator registry/receipt verified before repository writes |
| Session / app alias | `7736e7de-59ee-41bd-adad-65ab75199ee0` / `cfd2108a-85bc-418a-8291-c565b8e9cb7f` |

The lease covers exactly `docs/android/adr/`, `docs/android/architecture/`,
`docs/android/deviations/WP-001.md`, `docs/android/evidence/WP-001/`.
Only the coordinator grants/releases it. It is not an `ANDROID_PORT_LEDGER`
live claim; no fake ledger, budget, human approval or merged-parent receipt exists.

## Seven focused documents and acceptance handoff

| Artifact | Decision / acceptance contribution |
| --- | --- |
| [Build ADR](../../adr/001-build-candidate.md) | Verified Windows candidate versus Room/KSP/Linux/scaffold proof still required; `WP-001-behavior` prepared |
| [Modules](../../architecture/modules.md) | Acyclic foundation/adapters/features, producers/consumers and enforcement plan; `WP-001-behavior` prepared |
| [Lifetimes](../../architecture/lifetimes.md) | Process store, per-generation factories, monitor/cancellation/flush teardown and WP-207/303 split; `WP-001-behavior` prepared |
| [Contracts](../../architecture/contracts.md) | Typed value/error/transport/repository/signal/platform/navigation signatures; `WP-001-boundaries` prepared |
| [Data/platform](../../architecture/data-and-platform.md) | Source backup/identity/crypto/concurrency invariants and native adaptations; `WP-001-boundaries` prepared |
| [License/capability ADR](../../adr/002-licenses-and-capabilities.md) | GPLv3/MIT preservation and gated SDK/model/map/asset/translation admission; `WP-001-boundaries` prepared |
| [Scaffold/CI handoff](../../architecture/scaffold-and-ci-handoff.md) | Real proof requirements, personal-repo blockers, historical receipts and publisher bounds; all acceptance pending |

The manifest assigns WP-001 exactly two primary reference inputs:
`docs/Architecture.md` (`ce4d6834248bd9793e8849c8408bd765b05ad740`) and
`docs/Glossary.md` (`99957d3cff9db3689c7fca7b4733da697c4a9158`).
Both were read unchanged at the pin. Their layer/DTO/localization/data-flow/log/
adaptive/diagnostic concepts map to the seven documents; glossary meanings/units
remain domain inputs. Stale constructor/storage commentary is explicitly recorded
in [deviations](../../deviations/WP-001.md); actual ServiceContainer code controls.
WP-001 owns no original Swift test/resource cases. Read-only cross-WP inputs
include persistence roles, transport/session/error sources, four container-wiring
families and backup malformed/legacy/fractional/remapping/rollback/post-commit cases.
Future WP-004 inventories/owning-WP assertions, not these links or headers, control
`WP-001-source-test-parity`, whose review result remains pending.

## Actual local checks

Executed on Windows with Python **3.12.4** against the working-tree documentation
candidate. [verification.txt](verification.txt) contains exact command output:

| Command | Actual result |
| --- | --- |
| `python .\tools\android-port\controller\test_runner.py --quiet` | 111 discovered/run/passed; 0 failed/errors/skipped; exit0, controller fixtures only |
| `python .\tools\android-port\controller\validate.py` | Valid; 1,866 pinned inputs, 65 WPs, 185 edges, eight human gates; exit0 |
| `python .\tools\android-port\bootstrap.py` | Generator CHECK MODE (`dry_run: true`), canonical outputs unchanged; exit0; no `--write`/refresh |
| `git diff --cached --check` and parent-relative layer inspection | Clean whitespace; only exact leased documentation/evidence paths |
| Pinned reference diff and document inspection | No reference changes; acyclic 18-row library/adapter table, seven focused documents and pinned/relative link targets resolve |

Verification log SHA-256:
`2f5ffd130b9aed85769a50c6caf10af3bc6f463c4dc7bdd1023d0f725733efb9`.
Documentation inspections are not new mandatory suites or Android build evidence.
The canonical WP-001 verification entry remains **unconfigured**; no invented
Gradle task, CI identity, reviewer verdict or human-review pass was added.

The coordinator's isolated Windows Java21/AGP9.4.1/Gradle9.8/Kotlin2.3.20/BOM
template proof (assemble, two JVM tests and lint) was reviewed read-only, not rerun.
No MeshCore Android build, Linux build, iOS/macOS execution, codec oracle,
hardware, license/provider approval, signing or live backend/check activation
occurred in WP-001. Human review/parent disposition and the explicit future
proof requirements remain outstanding; all automation stays off.
