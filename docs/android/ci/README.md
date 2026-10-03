# WP-003 isolated scaffold CI

**Supervised dependent draft, not accepted WP-003 or activation.** The build
workflow runs real scaffold commands. The four trusted-duty workflows remain
manual, deployed-default-branch-only previews and deliberately return `BLOCKED`
for absent integrations. They do not publish `parity-review` or `gate-integrity`.

## Candidate execution and output contract

`android-ci.yml` runs for every pull request, including dependent drafts, plus
merge groups, `main` pushes and manual validation. There is no required-check
workflow path/branch filter. Both `ubuntu-24.04` x64 and `windows-2025` x64 are
mandatory. Module-selection optimization is intentionally deferred: skipping a
host, suite, lint target or evidence artifact is not success.

The always-running `android-ci` job checks the build outcome and downloads only
this run/attempt's two build bundles. It verifies repository, exact base/head,
source, candidate manifest/policy, run/attempt, both hosts, actual discovery
contracts, fresh-cache proofs and every artifact's size/SHA-256. Failure,
cancellation, a skipped required job, a missing bundle or stale/tampered evidence
fails. Cancellation of an entire obsolete workflow is not evidence for a new head.
It also reparses complete verbatim composite/standalone JUnit and all lint XML,
compares actual cases/outcomes with claimed counts and the discovery TSV, checks
typed lint values/hashes, validates graph/runtime TSV contracts, and compares
the full APK-inspection JSON with the result and uploaded APK bytes/native ELF.
No artifact code is executed.

| Stage | Actual executable tasks / mandatory evidence |
| --- | --- |
| Python | Frozen overlay/manifest/traceability/notices, controller regressions and all scaffold Python cases; nonzero discovery and no failed/error/skipped case |
| Preflight | Exact Python/JDK, publisher-pinned wrapper, installed SDK37.2/rev1 and build-tools37.0.0, explicit private caches, unchanged credential allowlist |
| `verify` | `verifyScaffoldTests verifyRoomSchema validateModuleGraph runtimeDependencyInventory resolveScaffoldDependencies`, strict verification, no build cache, forced execution |
| `standalone` | Independent `:convention:test` with its own initially absent Gradle user/project caches and strict included-build metadata |
| `assemble` | `:app:assembleDebug`, never release signing |
| `lint` | `lintScaffold`; all 24 current Android targets must produce readable XML with no errors |
| Inspect | Actual APK min31/target37/debug package, launcher, notices, permissions and test-fixture absence; `zipalign -P 16` and ELF PT_LOAD alignment |

Four Kotlin suites retain at least conventions31, contracts4, app10 and Room2
actual cases. The executor independently compares XML testcase/outcome nodes
with the scaffold TSV. These are scaffold assertions, **not original feature
test parity**. Reports include the 30-module boundary graph, real Room/KSP schema
check, runtime POM/license inputs, per-stage logs, exact debug APK and `SHA256SUMS`.
Each host's debug key/artifact is ephemeral; APK hashes need not match between
hosts. Static 16KB alignment is not real-device/MapLibre/HIL evidence.

No detekt, ktlint, Kover, localization synchronization, full source-case catalog,
instrumentation, Swift/macOS oracle or hardware task is invented here. Their
actual tooling, output contracts and acceptance remain WP-004/005 and the owning
feature/quality WPs. Passing this workflow never configures those future WPs.

## Provisioning and credential boundary

`controller/toolchain-pins.json` pins Temurin21.0.12.1+1 archives separately for
Windows/Linux, exact Python3.12.4, Gradle9.8.0 distribution/JAR, and Google's actual
`platforms;android-37.2`, `build-tools;37.0.0`, `cmdline-tools;23.0` archives.
SDK SHA-256 values were independently computed from exact first-party downloads
and checked against Google's size/SHA-1 metadata. JDK SHA-256 values come from the
publisher release asset digests. Metadata comes from `repository2-3.xml`; the
older repository index and assumed `platforms;android-37` are not used.

Provisioning requires a new absolute ephemeral directory, verifies bytes before
extracting, rejects archive traversal and validates exact package revisions,
hosts and license text. The explicit `--accept-sdk-license` option records only
the pinned SDK terms for this isolated installation; it is not app/dependency
legal approval. Changed SDK terms require human provisioning review.
The command-line-tools archive is installed but its CLI bootstrap is not executed:
an actual Windows probe showed it automatically fetching a separate helper.
Preflight instead reads real installed package metadata and Gradle exercises
the exact platform/build tools. Any separately approved future CLI invocation
must use `--no-metrics`; no CLI login, updater or device operation is a CI step.
Both-host installer regressions forbid every process invocation and every
download beyond the exact four locked archives plus first-party SDK metadata.
Provisioning provenance explicitly records archive-only installation and no
separate CLI-helper download/execution.
No SDK/JDK/cache/debug key or private installation path is committed.
`runtime_inputs.py` checks the immutable committed Git tree and exact checkout
bytes (with Git text CRLF/LF normalization, never binary normalization) for every
required pin/requirement/reader/workflow/build input before
provisioning or verification. A developer-only ignored file cannot satisfy it.

All Actions are pinned to full reviewed commit SHAs. Checkout has
`persist-credentials: false`; workflow/job tokens have only `contents: read`.
No signing, dispatch, merge, Copilot credential, shared runner, service/container
or cross-PR cache is provided. Candidate Gradle receives only the **unchanged**
`android/scaffold/environment-allowlist.json` names, with explicit `JAVA_HOME`,
`ANDROID_HOME`, `ANDROID_SDK_ROOT`, `GRADLE_USER_HOME`, `ANDROID_USER_HOME`.
The Windows PowerShell child strips startup-added variables and repeats the
independent parent preflight before invoking the batch wrapper.

Hosted builds use one worker, in-process Kotlin, a 2GiB build heap and 512MiB
test heap; these are build-process budgets, not app runtime flags or AI budgets.
The separate local `--local` mode retains measured shared-Windows constraints.
PyYAML6.0.3 is hash-pinned MIT-licensed CI parsing tooling, not an Android-linked
dependency. Tool/SDK terms and existing GPLv3/MIT/Apache notices remain distinct
from pending human dependency/license admission.

## Cloud setup and worker readiness

`copilot-setup-steps.yml` has exactly one supported Ubuntu x64
`copilot-setup-steps` job, only supported job properties, and a 59-minute limit.
It provisions the same exact archives and resolves the real scaffold
dependencies strictly. PR/manual execution validates setup code, **not cloud
account assignability**. The file takes effect for Copilot only after a real
default-branch merge; no cloud agent is invoked by this draft.

Setup failure may still allow Copilot to start. Before writing, a worker must
run the explicit preflight and actual strict dependency preparation, not infer
readiness from the setup workflow name or an existing directory.

At the repository root, with `ANDROID_CI_STATE` pointing to the private
provisioner's `environment.json` and `ANDROID_CI_OUTPUT` to a private absolute
evidence directory:

```powershell
python .\tools\android-port\controller\ci.py preflight
python .\tools\android-port\controller\ci.py run --stage prepare
python .\tools\android-port\controller\ci.py python
python .\tools\android-port\controller\ci.py run --stage verify
python .\tools\android-port\controller\ci.py run --stage standalone
python .\tools\android-port\controller\ci.py run --stage assemble
python .\tools\android-port\controller\ci.py run --stage lint
python .\tools\android-port\controller\ci.py inspect
```

Use `--local` for the documented constrained shared host. Local records explicitly
lack hosted run authority. Missing state, SDK, checksum, command, test or output
fails; no fallback installation/cache/pin rewrite occurs.

## Protected verification amendment

Only WP-002/003 executable verification configurations change. Every WP ID,
owner, dependency, human/supervised flag, acceptance, source ownership and pending
feature state remains unchanged. WP-001 stays unconfigured; architecture/license
approval cannot be replaced by a static build command.

`verification_config.py --check` reconstructs the frozen WP-000 generator output,
requires its original semantic digest, applies the two exact controller-owned
overlays, and compares the complete canonical manifest/exclusions. The original
`bootstrap.py`, `inventory_rules.py`, `wp_metadata.py`, plan and schemas are not
edited. **Bare `bootstrap.py` check mode now deliberately rejects the amended
manifest**; the protected overlay command is the coherent new check, not a claim
that the old generator remained unchanged and magically accepted new fields.

```powershell
python .\tools\android-port\controller\verification_config.py --check
python .\tools\android-port\controller\validate.py
python .\tools\android-port\controller\workflows.py
python .\tools\android-port\controller\test_runner.py --quiet
```

## Trusted gates and remaining external proof

The read/search-only reviewer profile and all skills/policy/manifest inputs are
read from immutable **trusted-base Git objects**. Candidate files/instructions
are separately labeled untrusted data; no candidate checkout/script is executed
by trusted-duty workflows. Staging is complete, digest-bound and bounded; excess
files/bytes fail rather than dropping cases. A staged JSON object is not a verdict.

The authority reconciles real PR/check/run/review facts, exact Android Actions
check-suite membership, isolated publisher binding/run/attempt external IDs,
current/historical base, source/manifest/policy and actual bound independent
formal reviews. A rebased head/base, wrong publisher/workflow/repository/attempt,
skipped check, malformed verdict, absent original-case family or model-written
approval cannot complete a WP. Repairs retain the existing worker/open PR and
bounded attempt counter; a cloud comment receipt must match that PR and feedback.

Publisher encoding retains the **complete** bundle/catalog and rejects summary
or text above 65,535 characters **or UTF-8 bytes before publication**. No unchecked
`details_url`, partial catalog or truncation escape exists. Large future catalogs
require a protected immutable-artifact reader/publisher amendment.
[Historical revalidation](historical-revalidation.md) is a versioned **blocked
migration interface**, not a fabricated bootstrap Android check or import.

Still external: independent authenticated publisher/reviewer/native host,
complete live usage/shared ledger, genuine branch-rule/check identities,
real alternate authorized reviewer for self-authored PRs, protected historical
receipt approval, user-selected limits and separate activation. Sole maintainer
`cbattlegear` cannot formally approve their own PR. No attestation alternative is
approved here. Ordinary dependent drafts are not a registered native stack;
later native-stack landing requires its asynchronous native API, not
`MergeBackend`. No settings, auto-merge, stack registration, fleet, signing,
release or downstream WP is authorized by this implementation.
