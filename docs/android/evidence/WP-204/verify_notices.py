"""WP-204 preserve exact admitted notice bytes across Windows checkout without downloading or changing terms."""

import argparse
import hashlib
import json
from pathlib import Path
import sys

OUT = Path(__file__).resolve().parent
ROOT = OUT.parents[3]
APP_NOTICE = "android/app/src/main/assets/licenses/DataStore-Protobuf-BSD-3-Clause.txt"
EXPECTED_PATHS = {
    APP_NOTICE,
    *("android/core/datastore/src/main/assets/licenses/WP-204/" + name for name in (
        "AndroidX-DataStore-LICENSE.txt", "Okio-Apache-2.0.txt",
        "Okio-Copyrights.txt", "Protobuf-v28.2-Copyright-LICENSE.txt",
    )),
}


def verify(normalize=False):
    publication = json.loads((OUT / "publisher-inputs.json").read_text(encoding="utf8"))
    records = publication["generated_notice_assets"]
    if len(records) != 5 or {record["path"] for record in records} != EXPECTED_PATHS:
        raise ValueError("Notice receipt differs from the exact five admitted outputs")
    corrected = []
    for record in records:
        path = ROOT.joinpath(*record["path"].split("/"))
        raw = path.read_bytes()

        def matches(value):
            return len(value) == record["bytes"] and hashlib.sha256(value).hexdigest() == record["sha256"]

        if not matches(raw):
            canonical = raw.replace(b"\r\n", b"\n")
            if not normalize or record["path"] != APP_NOTICE or not matches(canonical):
                raise ValueError("Missing/changed independently verified notice bytes: " + record["path"])
            path.write_bytes(canonical)
            corrected.append(record["path"])
            if not matches(path.read_bytes()):
                raise ValueError("Exact admitted notice normalization failed")
    return {
        "result": "exact-notice-inputs-verified", "notices": 5,
        "normalized_windows_checkout_paths": corrected,
        "scope": "only exact source notice byte/hash preservation; not an APK, legal or hardware result",
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--normalize", action="store_true")
    args = parser.parse_args()
    try:
        print(json.dumps(verify(args.normalize), indent=2))
        return 0
    except (OSError, ValueError, KeyError) as failure:
        print(f"BLOCKED: {failure}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
