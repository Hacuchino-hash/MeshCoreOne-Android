# WP-006 executable proposal tooling evidence

Repository `cbattlegear/MeshCoreOne-Android`; role `upstream-sync`, implemented
in the coordinator's owning worktree under the user-authorized automation-code
scope. Reservation `autonomous-WP-006-eaf0fdb9`.
Actual merged base `eaf0fdb956afcb20e2de3d7d6550e0cbeeb50730`.
Frozen source `db14559b39d32322b06477c6ae676112f583db50`.
Manifest `78a22920beaa5899f9618806b5cd2b27d50399a9b29b4d8dbd79f755717ec746`;
semantic policy `56bdc53548bc86d631245795dfa38b4fc86048e0e7cbe1c7d5695879b035b42a`.
Only the five declared tooling/workflow/upstream/evidence/deviation scopes
change; source/ownership/policy/fixtures and original 65 IDs/185 edges do not.

Actual local Windows commands:

| Command | Actual outcome |
| --- | --- |
| `python .\tools\android-port\resync.py --self-test` | 27 discovered/run/passed, zero failed/errors/skipped |
| `python .\tools\android-port\resync.py --candidate db14559b39d32322b06477c6ae676112f583db50` | Actual immutable no-change report: zero inputs/proposals, no source/action mutation |
| `python .\tools\android-port\controller\workflows.py` | Passed read-only/default-branch workflow boundaries; no live publisher claim |
| `python .\tools\android-port\controller\test_runner.py --quiet` | 165 discovered/run/passed, zero failures/errors/skips |
| `python .\tools\android-port\controller\validate.py` | Passed frozen 1,866 inputs, source/ownership/65 WPs/185 edges/eight gates |
| `git diff --check` | Passed |

Twenty-seven actual assertions cover real temporary Git trees/commits for
addition/modification/deletion, exact and modified rename, mode-only changes,
missing objects, no-op; primary/consumer dedup, generated provenance,
new/renamed mapping blockers, retained exclusions/resources, stale/invalid
bindings/IDs/paths/modes, stable fingerprints across candidate/implementation
changes, full 1,000-input catalogs, and complete/exclusive/private export.
A report flag never claims a GitHub issue, human approval or accepted port case.

The existing controller regression suite and trusted validators remain
unchanged. No additional Python dependency, model override, global config,
upstream HEAD lookup, issue creation, schedule, reference advance or Android
product code is introduced by the helper. No real incoming upstream revision
has been approved, fetched, applied or asserted compatible locally.

The new path-scoped normal workflow runs all fixtures on Windows/Linux and
retains real counts, while manual comparison uses default-branch code and
data-only candidate objects. Exact hosted head/run artifacts belong to the
associated normal PR; local fixtures are not fabricated hosted Linux proof.
The deliberate manual-job skip during PR tests leaves every mandatory
fixture assertion enabled.

`WP-006-behavior` and `WP-006-boundaries` have executable tooling evidence,
including complete proposal routing and failure paths. This is an Android-only
automation package with no owned Swift behavior to declare ported.
Source/case parity, reference/ownership advance, live publisher/activation,
license/signing and full-app outcomes remain separate actual gates/work.
