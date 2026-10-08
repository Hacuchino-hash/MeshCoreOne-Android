#!/bin/sh
# WP-217 Swift oracle. Compiles the frozen MockDataProvider*.swift and MockMessageFactory.swift with
# `Date()` rewritten to a pinned `OracleClock.now`, against the DTO stubs in OracleStubs.swift.txt, and
# prints the seed dump (integral instant) and the dump's SHA-256 at a fractional instant.
# Usage: run-oracle.sh <scratch-dir>
set -eu
here=$(cd "$(dirname "$0")" && pwd)
repo=$(git -C "$here" rev-parse --show-toplevel)
work=${1:?scratch directory required}
mkdir -p "$work/src"
for source in "$repo"/MC1Services/Sources/MC1Services/Simulator/MockDataProvider*.swift \
  "$repo"/MC1Services/Sources/MC1Services/Simulator/MockMessageFactory.swift; do
  sed 's/Date()/OracleClock.now/g' "$source" > "$work/src/$(basename "$source")"
done
cp "$here/OracleStubs.swift.txt" "$work/src/OracleStubs.swift"
cp "$here/main.swift.txt" "$work/src/main.swift"
swiftc -o "$work/oracle" "$work"/src/*.swift
"$work/oracle" 1767225600
"$work/oracle" 1767225600 --sha256
"$work/oracle" 1767225600.75 --sha256
