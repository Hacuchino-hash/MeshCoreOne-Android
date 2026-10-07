# ADR 003: one-time frozen Swift pin exception for a flaky test ordering race

**Status:** Accepted (human-authorized exception, not a precedent).

## Context

The Swift reference tree (`MC1/**`, `MC1Services/**`, `MC1Tests/**`, `MC1Widgets/**`,
`MeshCore/**`, `Shared/**`, `AppIcon.icon/**`, `LICENSE`, `project.yml`, `swiftgen.yml`)
is pinned read-only at `db14559b39d32322b06477c6ae676112f583db50` for the Android
port. CI's `validate.py` fails closed on any diff under that tree with
`BLOCKED: Swift reference advanced/changed; do not move the pin`, and an earlier
external contribution (PR #30) proposing a fix here was closed for exactly that
reason.

`MeshCoreSessionCommandCorrelationTests.swift` contains a genuine ordering race,
independently diagnosed and reproduced: `binary request errors release the
serializer for following requests` launches two unstructured `Task`s
(`requestStatus`, `requestTelemetry`) back to back and assumes `requestStatus`
wins the serializer first. Nothing guarantees that ordering. When telemetry wins
instead, `simulateError(code: 12)` fails telemetry, and the still-pending status
request times out at the 2.0 s `binaryRequestOverallTimeout`. This is a bug in
the test's scheduling assumption, not in `MeshCoreSession`, `PacketBuilder`, or
any production code. It has caused every observed `SPM Package Tests` flake in
this repo's CI history.

## Decision

The maintainer explicitly authorized a one-time, test-only edit to this single
test to make the second request start only after the first has visibly claimed
the serializer (asserted via the exact `PacketBuilder` frame sent), removing the
race instead of relying on scheduling luck. No production Swift source changed.
No test assertions were weakened; two assertions were added (exact frame sent at
each step) so a future ordering regression fails clearly instead of timing out.

This is **not** a change to the pin's general read-only policy. Future Swift-side
fixes, including other flaky tests, still require the same explicit maintainer
authorization and an ADR entry before any edit lands; CI's fail-closed check on
the Swift tree remains otherwise fully in force.

## Evidence

- Local, macOS 26 / Apple Swift 6.3.2 (Swift Testing): reproduced the original
  failure by injecting a delay that lets telemetry win (same timeout at the same
  line); the fixed test passes with either task's original scheduling lucky or
  unlucky; the fixed suite ran 50/50 under background QoS with all cores
  saturated; the full `MeshCore` package (`swift test`) passed 486 tests in 54
  suites.
- Upstream (`Avi0n/MeshCoreOne`) carries the same latent race at the pinned
  commit; this fix is local-only and does not move the pin forward or claim the
  upstream test was changed.
