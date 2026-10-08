# WP-003 reservation engine evidence

This document records the bounded capability/reservation implementation. Claims for
isolated worktree paths are advisory and retain deterministic overlap metadata; they
do not block another worker. External execution resources use the separate
transactional `hard_locks` table. Legacy supervised rows are upgraded in place and
retain their binding, identity, state, repair count, and authorization context.

Implemented acceptance coverage is in `tools/android-port/tests/test_capabilities.py`
and the updated controller state regression. The exact configured WP-003 commands
and the mandatory local seven-stage cycle remain the authoritative verification
record; this file is not a completion or human-gate attestation.
