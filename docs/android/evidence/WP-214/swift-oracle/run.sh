#!/bin/sh
# Recompiles the Swift oracle programs and rewrites their outputs (macOS with swiftc).
set -eu
cd "$(dirname "$0")"
out="${TMPDIR:-/tmp}/wp214-swift-oracle"
mkdir -p "$out"
for name in foundation_whitespaces split_max_splits parse_channel_message; do
  swiftc "$name.swift" -o "$out/$name"
  "$out/$name" > "$name.out"
done
swiftc --version 2>&1 | head -2 > swiftc-version.txt
