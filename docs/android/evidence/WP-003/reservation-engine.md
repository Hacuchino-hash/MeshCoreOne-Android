# WP-003 reservation engine evidence

This document records the bounded capability/reservation implementation. Claims for
isolated worktree paths are advisory and retain deterministic overlap metadata; they
do not block another worker. External execution resources use the separate
transactional `hard_locks` table. Legacy supervised rows are upgraded in place and
retain their binding, identity, state, repair count, and authorization context.

Implemented acceptance coverage is in `tools/android-port/tests/test_capabilities.py`
and `tools/android-port/local/test_reserve.py`. It covers App3 producer transfer,
validator2 handoff, annotation plus 37-POM metadata evolution, deterministic
same-path disjoint semantic edits, stale CAS, malformed/foreign/controller hard
lock collisions, current-base and semantic-conflict integration blocks, protected
merge-only approval through the unchanged gate suite, unsupported operations and
historical receipt migration.

The validator2 regression also removes the old execution-time requirement that
the *current* manifest digest equal WP-302's historical support-approval digest.
The exact approval object and its original manifest digest remain immutable audit
evidence, while runtime admission continues to require the pinned source and
byte-exact proof. Later manifest evolution therefore no longer turns historical
authorization prose into mutable capability state.

The repository-owned runtime installer stages and hashes the complete controller,
uses the separate installed-runtime hard lock, atomically swaps the Git-common
runtime directory, verifies installed bytes, and releases the lock. The exact
configured WP-003 commands and mandatory local seven-stage cycle remain the
authoritative verification record; this file is not a completion or human-gate
attestation.

`tools/android-port/oracle/workflow_scope.py` additionally prevents unrelated
changes from scheduling auxiliary parity chains. Direct tests prove synthetic
WP-218 content/location and WP-302 navigation path sets produce `false` for both
`codec` and `backup`; codec, protocol and WP-203 inputs select only their declared
chains where independent, workflow dispatch selects all, PR/merge-group/push
endpoints are parsed explicitly, and malformed events or Git diff failures select
all rather than silently skipping.
