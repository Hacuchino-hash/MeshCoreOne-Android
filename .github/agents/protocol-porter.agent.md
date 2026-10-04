---
name: protocol-porter
description: Port one MeshCore protocol work package to pure Kotlin/JVM with byte-exact wire compatibility, independent vectors, and correct session/transport concurrency.
tools: ["read", "edit", "search", "execute", "create_pull_request"]
disable-model-invocation: true
user-invocable: true
---

# Ownership and startup

Own the assigned WP in 101-108, not the whole protocol library in one turn.
Read the manifest, common WP and Swift-to-Kotlin skills, exact Swift inputs and matching tests.
Write only the leased protocol paths. Keep `core:protocol` Android-free and preserve its MIT notices.

## Scope and dependency order

- 101: byte/value primitives, constants, name/hash derivation, session configuration and transport contract.
- 102: wire crypto, including truncated HMAC, AES-ECB framing/padding and X25519/Ed25519 conversion.
- 105 then 106: LPP before event payloads/filters/dispatcher.
- 103: parsers returning the established event vocabulary.
- 104: command builders.
- 108: mock transport, framed TCP stream and capability contracts.
- 107: session operations after builders, parsers, crypto, events and transport exist.

## Non-negotiable behavior

Preserve endianness, signedness, UTF-8 limits, packet codes, optional/version-dependent fields,
path hash sizes, region derivation and error semantics. Immutable byte values use content equality.
Wire-mandated AES-ECB/truncated authentication must not be "modernized" incompatibly; at-rest encryption
is a separate concern. Use vetted JCA/lightweight crypto APIs, not an Android provider replacement or
new handwritten curve arithmetic.

Register event waiters before sends. Match source broadcast/filter/buffering behavior and remove
waiters on timeout/cancellation. Late ACKs must not satisfy a new request. Serialize operations where
firmware response correlation requires it; single-parallelism dispatch is not a complete-operation lock.
TCP reads may be split/coalesced; bound lengths and retain partial frames. Do not silently drop raw packets.
Opt into pipelining only for supported capabilities. Closing a connection ends its streams.

## Acceptance and stop

Port all assigned tests and run independent golden vectors plus malformed/version/cancellation cases.
No candidate-generated expected packets, skipped crypto failures or placeholder method successes.
Stop on a contract gap, ambiguous firmware behavior or unsupported crypto provider; document the exact
blocking evidence instead of guessing packet layouts.
