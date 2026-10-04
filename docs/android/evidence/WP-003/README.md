# WP-003 supervised dependent draft evidence

**Not accepted WP-003, readiness receipt, human approval or activation.**

| Identity | Binding |
| --- | --- |
| Repository / owner | `cbattlegear/MeshCoreOne-Android` / unchanged `android-build-engineer` charter |
| Initial HEAD | `ac227f7e372cb7e20881ba0c9a2c1887695732bb`, verified before edits |
| Coordinator-approved repaired parent | `ba2d3c319b8f58f100c140abedf979ad15c6124f`; own-branch rebase authorized, no parent approval/merge implied |
| Managed branch / dependent base | `cbattlegear-android-ci-foundation` / `cbattlegear-stunning-spork` |
| Source / source tree | `db14559b39d32322b06477c6ae676112f583db50` / `8918fdc604341e6996a68c88f6bb1c02b9c2f87e` |
| Original manifest / amended semantic digest | `f3fd3a0a51841a8fb43d3f2c4b3953e6ef564d4e96e74f4e3035200face5c90e` / `78a22920beaa5899f9618806b5cd2b27d50399a9b29b4d8dbd79f755717ec746` |
| Amended policy semantic revision | `56bdc53548bc86d631245795dfa38b4fc86048e0e7cbe1c7d5695879b035b42a` |
| Prepare-only shared lease | `draft-WP-003-6a475da1-ac227f7e`, all13 canonical WP-003 paths |
| App session / native alias | `64aaae77-f32f-4365-8164-29aa9acd7180` / `6a475da1-7cbc-4c39-bd67-7580cd7830a5` |
| Candidate SHA / PR / final parent | Exact final identities are recorded in the draft PR/handoff; this record does not invent a self-referential commit hash |

The coordinator's receipt was read and its project/worktree/aliases/parent/source
and paths matched before writing. No overlapping active draft writer or private
live ledger was substituted. PR3 and PR2 were independently observed OPEN/DRAFT,
unmerged, at the supplied exact parent/head/base bindings before edits.

## Evidence recorded so far

| Actually executed command | Observed result |
| --- | --- |
| `python .\tools\android-port\controller\verification_config.py --check` | Exit0; reproducible complete frozen generator + exact two-WP verification overlay |
| `python .\tools\android-port\controller\workflows.py` | Exit0; all six scoped YAML workflows parse and satisfy trust-boundary assertions |
| `python .\tools\android-port\controller\test_runner.py --quiet` | 161 discovered/run/passed after runtime-delivery repair, 0 failed/errors/skipped; controller/workflow/evidence/gate fixtures only |
| `python -m unittest discover -s .\android\scaffold -p test_*.py -q` through `ci.py python` | 12 discovered/run/passed, 0 failed/errors/skipped |
| `python .\tools\android-port\controller\ci.py provision --root <new private directory> --accept-sdk-license` | Exit0; actual Windows first-party JDK/SDK downloads, exact size/SHA-256/vendor SHA-1 checks and private installation |
| `python .\tools\android-port\controller\ci.py preflight --state <private environment.json> --output <private evidence>` | Exit0; independent parent allowlist/JDK/SDK/wrapper and real installed package metadata; final preflight never invokes CLI bootstrap |
| `python .\tools\android-port\controller\ci.py run --stage verify --local` | Exit0; genuinely absent root user/project caches, strict/no-build-cache/forced execution; conventions31 + contracts4 + app10 + Room2 = 47 actual cases, all passed |
| `python .\tools\android-port\controller\ci.py run --stage standalone --local` | Exit0; separate genuinely absent user/project caches; 31 actual convention cases, all passed |
| `python .\tools\android-port\controller\ci.py run --stage assemble --local` | Exit0; actual debug APK, no lock/checksum rewrite |
| `python .\tools\android-port\controller\ci.py run --stage lint --local` | Exit0; all24 actual Android lint reports, 0 errors / 7 warnings |
| `python .\tools\android-port\controller\ci.py inspect --local` | Exit0; actual APK identity/notices/fixture absence and static zipalign/ELF16KB checks |
| Hosted Windows/Linux and normal setup validation | Passed at exact head `0ed0520462b2ad1f980c78c38f97db3cd7942fd2`; immutable run identities and results below |

Initial executor validation reproduced a Windows `cmd.exe` double-quoting error
in the local test JVM property, before required assertions. It was corrected with
the JSON-argument PowerShell batch adapter, which re-strips startup variables and
repeats the independent preflight. The failed attempt is not fresh-cache success.
The subsequent full proof uses a different newly provisioned private cache
topology, strict verification, no build cache and forced execution.
Those local Kotlin/build results were produced before the approved parent
Linux-AAPT amendment. The 12 local scaffold Python cases are likewise pre-rebase;
the repaired parent adds three native-metadata regressions. Hosted/final amended
scaffold evidence must discover at least15 and is not inferred from the old12.

The local APK is **28,529,344 bytes**, SHA-256
`b96ab7751fd784785ec895653737158918dc7e07f8e2db5b5b05baf5a14db737`,
min31/target37/package `com.meshcoreone.android.debug`. GPL/MIT/Apache notices
are present, verification fixtures absent, and all four graphics-path ABI ELF
PT_LOAD alignments are16,384. Local per-install debug signing explains a distinct
APK hash; no release certificate, installation or native-runtime proof is implied.

| Local generated output | SHA-256 |
| --- | --- |
| `test-discovery.tsv` | `41fef77457f63589cd064bec6b81ebc8d3fe0aecac540d0ecda682417bb76aff` |
| `module-graph.tsv` | `a5ac81df4e2b670341d1badb5b2dc6754995880fa61b28bad20cd9df2b1c3a23` |
| `runtime-dependencies.tsv` (113 inputs) | `86776ba091b9c8733a28d124bb2042f1e271f8d45d86f884f7d506cc5196b07f` |
| `apk-inspection.json` | `307e813c6293d19bc51cb1cdd53a56df944b7407826d19577e06c1ffdde6c5ff` |

The early native CLI probe fetched an extra unpinned helper into the explicit
private Android-user cache. Final provisioning has always extracted verified
archives directly; final preflight now reads package metadata only. Both-host
regressions prove zero process invocations and no download beyond the locked
archive/metadata set. No helper pin or implicit downloader is carried into CI.

Independent review reproduced an aggregator defect: artifact hashes and lint
key sets alone accepted null lint values and syntactically invalid report data.
The repaired reader requires complete raw composite/standalone JUnit and all24
lint XML, reparses actual case/outcome and lint semantics, compares counts/TSV/
hashes, validates graph/runtime TSV and cross-checks inspection JSON/APK bytes.
Positive fixtures now contain well-formed report data; negatives cover null/
fabricated/skipped/error/stale/missing raw evidence. Fixtures are not live runs.

The first real hosted setup run
[`37154315851`](https://github.com/cbattlegear/MeshCoreOne-Android/actions/runs/37154315851)
at `1d813ceb55c00507cc3ac0343472f92fdd12d89e` failed correctly: inherited
`*lock.json` ignore rules excluded the static toolchain file from the commit.
Local filesystem tests were not clean-export delivery proof. The follow-up
renames it to tracked `toolchain-pins.json` without changing a single pin or the
root ignore policy, and adds exact committed-tree/checkout-byte/clean-export
runtime-input validation. The failed run is not marked setup/Linux success.

## Actual hosted Windows and Linux proof

Normal PR execution at **`0ed0520462b2ad1f980c78c38f97db3cd7942fd2`**, base
**`ba2d3c319b8f58f100c140abedf979ad15c6124f`**:

| Actual run/job | Identity / observed result |
| --- | --- |
| [Android scaffold CI](https://github.com/cbattlegear/MeshCoreOne-Android/actions/runs/37154947050) | Run37154947050, workflow374189832, attempt1, `pull_request`, completed/success; check-suite100641695326 |
| Windows2025 x64 | Job111296319368, success in13m16s; fresh strict composite47 + independently fresh standalone31; Python161 + scaffold15; lint24, 0 errors / 7 warnings |
| Ubuntu24.04 x64 | Job111296319671, success in8m28s; fresh strict composite47 + independently fresh standalone31; Python161 + scaffold15; lint24, 0 errors / 7 warnings |
| Always-reported `android-ci` | Job111298589286, success in20s; reparsed both complete raw report bundles, exact bindings/run/attempt and every artifact digest |
| [Normal cloud setup validation](https://github.com/cbattlegear/MeshCoreOne-Android/actions/runs/37154947058) | Run37154947058, workflow374189834, attempt1, `pull_request`; job111296319526 success in2m36s; actual verified Linux archives/metadata/readiness and strict `resolveScaffoldDependencies` |

All mandatory cases ran/passed with **zero failures, errors or skips**. Both
user and project caches were initially absent in each host's composite and
separate standalone topology. No pin/lock generation, cache restore, signing/
dispatch secret, privileged runner, SDK CLI helper or cloud agent was used.
SDK37.2/rev1, build-tools37.0.0, command-line-tools23.0, Python3.12.4 and
Temurin21.0.12.1+1 were actually present. Setup succeeded as an ordinary workflow;
default-branch cloud deployment/assignability remains unproven.

| Uploaded host artifact | Actual identity / immutable digests |
| --- | --- |
| Linux | Artifact11285461450 / `scaffold-linux-37154947050-1`, archive SHA-256 `564f3f30a7cdff9594bfcbcef045c2e74ffad8037af016dd44e401fe4aef8906`; APK28,529,112 bytes / `d9abe311faa4153692bf72fea849042bc8efbc799db09e965290b96be970b13d` |
| Windows | Artifact11285871525 / `scaffold-windows-37154947050-1`, archive SHA-256 `3463e0062be535f33fb67abc70939960104bc2ff7bd6442d7cc5373c35c8028d`; APK28,529,344 bytes / `7d42ac480a7fea8607dd512bdb6ac76f38daf3497095e09aeba9f8e5475cbf70` |
| Setup | Artifact11285930422 / `cloud-setup-37154947058-1`, archive SHA-256 `2a24282db72792d62365c3e32e9143e1fe31e7586038ee08cd951414b76479dd` |

Downloaded bundles were replayed independently through the same complete
data-only aggregator locally, which again passed at their exact0ed05204 binding.
They retain full raw JUnit/lint XML, typed TSV/inspection JSON, real debug APK,
per-file `SHA256SUMS` and stage/readiness provenance. Both APKs are min31/target37,
`com.meshcoreone.android.debug`, preserve notices, exclude test fixtures and pass
static zipalign/ELF16KB checks; no device/native-runtime/release claim follows.
The normalized clean Git export delivered all76 required runtime inputs and
passed the exported worker preflight, unlike the initial ignored-file attempt.

GitHub reported that these pinned Actions' Node20 targets were forced to Node24
by the runner platform. This annotation was not a build failure or a claim of an
immutable Node/runner image. No action pin/toolchain version was silently changed.
Artifacts have seven-day retention; [sanitized run summary](hosted-37154947050.json)
persists exact identities/digests without private paths or debug keys.

Publisher bounds test exact65,535/overflow/Unicode bytes and a full 2,000-entry
**fixture** catalog without mutation/truncation. This is not an actual WP-004
case inventory or live publisher proof. Historical proposals remain explicitly
BLOCKED and never import, even with real-shaped original bootstrap fixtures.
Tests reject wrong/stale repo/base/head/source/manifest/policy/run/attempt/check
suite/publisher, missing/skipped/zero discovery, false approval/repair receipts,
candidate reviewer replacement, unsafe archive/YAML and native-stack misuse.

`WP-003-behavior`, `WP-003-boundaries`, `WP-003-source-test-parity` remain prepared
for review, not passed. Missing genuine isolated publisher/reviewer/native-host
authentication, cloud assignability, branch rules, alternate human reviewer,
complete live usage/shared ledger, protected historical migration, budgets and
activation remain external. No model/fixture result is live-fleet acceptance.
See [CI contract](../../ci/README.md) and [deviations](../../deviations/WP-003.md).
