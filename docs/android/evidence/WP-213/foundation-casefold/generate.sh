#!/bin/sh
# Prints the Foundation case-fold table embedded in FoundationCaseFold.kt (macOS with Swift).
set -eu
dir=$(dirname "$0"); work=$(mktemp -d)
cp "$dir/gen-table.swift.txt" "$work/main.swift"
swiftc -O "$work/main.swift" -o "$work/gen"
"$work/gen"
