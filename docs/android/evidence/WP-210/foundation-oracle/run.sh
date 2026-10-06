#!/bin/sh
# Builds and runs the Foundation oracle (macOS with Swift; Command Line Tools are enough).
# Usage, from the repository root: sh docs/android/evidence/WP-210/foundation-oracle/run.sh
set -eu
dir=$(dirname "$0")
work=$(mktemp -d)
sed -n 1,239p MC1Services/Sources/MC1Services/Models/NodeConfig.swift > "$work/main.swift"
cat "$dir/oracle-main.swift" >> "$work/main.swift"
swiftc -O "$work/main.swift" -o "$work/oracle"
"$work/oracle"
