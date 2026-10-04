# Read-only upstream resync proposals

`tools/android-port/resync.py` compares the frozen iOS reference with an
**explicit full upstream commit**, including modified/renamed/deleted/added
files and executable-mode-only changes. It reads immutable Git objects,
validates source blobs against ownership, and routes complete changes to each
primary owner and declared consumer. `PortedFrom` and generated many-to-many
provenance identify affected current Android files; headers do not establish
feature completion.

The comparator does **not** move the source pin, rewrite Swift/Android/fixtures,
create an issue, launch a worker, change a policy, or start monitoring.
Reference/ownership advances retain WP-006's separate review gate. A new
unowned source or renamed destination needing a mapping is an explicit blocker.
Changed exclusions remain visible for review instead of being silently dropped.

At the repository root, with an already available candidate object:

```powershell
python .\tools\android-port\resync.py --candidate '<full-lowercase-commit-sha>' `
  --output 'C:\your-private-artifacts\upstream-report.json'
```

`--fetch-candidate` additionally fetches only that explicit object from the
fixed `https://github.com/Avi0n/MeshCoreOne.git` origin, without writing
FETCH_HEAD or changing any branch/working tree. It never executes candidate
code. No candidate/default branch/HEAD is inferred from an API or guessed.

`--existing 'C:\your-private-artifacts\prior-report.json'` accepts a previously
generated complete report with the same repository/reference/manifest/semantic
policy binding. Fingerprints use the complete source delta and WP, not a moving
candidate HEAD or current implementation filenames. A repeat remains fully
visible with `already_proposed: true`; it is not dropped and does not claim an
issue exists, review was granted, or work completed.

Full reports require a new **absolute artifact path outside the checkout**.
Existing files are not overwritten. Output keeps every path/blob/mode/owner/
consumer/implementation/acceptance and focused prompt, including catalogs
beyond inline check-output bounds; no catalog is truncated for a publisher.
The console prints only binding, status and counts. `blocked` exits2 with a
complete blocked report when an output path is supplied; `review_required`
exits0 but means a proposal, never automatic reference approval.

Run the real local fixtures without contacting upstream:

```powershell
python .\tools\android-port\resync.py --self-test
```

The runner requires positive full discovery with no failures/errors/skips,
including real temporary Git commits, modified renames and mode changes.
The source checkout remains untouched. Optional `--output` stores its exact
assertion counts in a new private artifact.

## Workflow boundary

`android-upstream-sync.yml` has two distinct modes. Path-scoped PR events run
fixture assertions on Windows/Linux against an exact read-only candidate,
with pinned checkout/Python/action identities and the existing checksum-pinned
parser requirement. These jobs do not contact upstream.

Only explicit `workflow_dispatch` with `candidate_commit` runs the actual
comparison, using trusted **default-branch tool code**, read-only credentials
and the fixed upstream origin. Candidate source is data, not executable input.
It retains the entire JSON proposal as an artifact, including blockers.
There is no `schedule`, issue-write token, deployment, privileged gate publisher,
source-update action, approval or worker-launch path. The manual comparison job
is intentionally not run during PR fixture checks; that is not a skipped
mandatory assertion suite.
