# AndroidOnly: WP-304 Generate owned provenance/case accounting from exact frozen inputs, never the manifest or catalog.
from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
from source_inventory import ROOT, PIN, inventory, require


def generate():
    inputs, cases = inventory()
    production = {}
    tests = {}
    directory = ROOT / "android" / "core" / "ui" / "src"
    for path in sorted(directory.rglob("*.kt")):
        relative = path.relative_to(ROOT).as_posix()
        text = path.read_text(encoding="utf8")
        for source in re.findall(r"// PortedFrom: ([^\s@]+)@" + PIN, text):
            target = production if "/main/" in relative else tests
            target.setdefault(source, []).append(relative)
    rows = []
    for entry in inputs:
        targets = (production if entry["kind"] == "production" else tests).get(entry["path"], [])
        require(targets, "Unaccounted primary source: " + entry["path"])
        original = {key: value for key, value in cases.items() if value["path"] == entry["path"]}
        rows.append({
            "source_path": entry["path"], "source_blob": entry["blob_sha"], "kind": entry["kind"],
            "implementation_paths": targets,
            "original_cases": original,
            "scope": "Provenance/case accounting only; concrete producer binding, native execution and parity are separate evidence.",
        })
    return {"schema_version": 1, "work_package": "WP-304", "source_sha": PIN,
        "primary_sources": 88, "source_families": 130, "source_scenarios": 158, "entries": rows}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--write", action="store_true")
    args = parser.parse_args()
    value = generate()
    path = Path(__file__).with_name("source-map.json")
    if args.write:
        path.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf8")
    else:
        require(path.is_file() and json.loads(path.read_text(encoding="utf8")) == value, "Owned source map missing/stale")
    print(json.dumps({"result": "source-accounting-valid-not-parity", "sources": 88, "families": 130, "scenarios": 158}))


if __name__ == "__main__":
    main()
