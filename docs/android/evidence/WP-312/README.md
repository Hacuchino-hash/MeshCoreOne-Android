# WP-312 provider-neutral candidate evidence

**Status:** partial authorized implementation; protected provider integration
remains blocked and WP-312 acceptance is not complete.

## Revisions and scope

- Repository: `cbattlegear/MeshCoreOne-Android`
- Work package / owner: `WP-312` / `nodes-map-ui-engineer`
- Base: `95a38aada1151cd289b206c5d8adb78d7664bfbc`
- Frozen Swift source: `db14559b39d32322b06477c6ae676112f583db50`
- Manifest entry: 52 owned inputs, dependencies WP-218/WP-303/WP-304,
  `human_gate=false`, provider-specific behavior governed by ADR-002.
- Changed product paths are restricted to `android/core/maps/` and
  `android/feature/map/`. No dependency coordinate, provider URL, Android
  permission or provider asset is added.

## Acceptance status

| Acceptance | Status |
| --- | --- |
| `WP-312-behavior` | Partial: provider-neutral map/camera/point/line/filter, snapshot-cache and offline-region contracts implemented. Real layers, snapshots and offline regions remain blocked. |
| `WP-312-boundaries` | Partial: deterministic tests cover validation, network loss, low disk, cancellation, pause/resume/delete delegation and cache lifecycle. Real GPS, provider cache, app lifecycle and native 16 KiB evidence remain blocked. |
| `WP-312-source-test-parity` | Blocked: full 52-input family accounting requires the admitted provider adapter and real UI/backend behavior. |

## Verification

The manifest still declares WP-312 verification unconfigured. The existing
module task `:core:maps:testDebugUnitTest` was added to the established
`verifyScaffoldTests` aggregate and contains nonzero JUnit4 cases.

Local WSL execution from the Windows-linked worktree did not start the Gradle
build: Gradle failed while creating `FileHasher` with `java.io.IOException:
Input/output error`, matching the documented NTFS/worktree limitation. This is
not a test failure or a passing result; hosted exact-head results must be
recorded before this candidate can advance.
