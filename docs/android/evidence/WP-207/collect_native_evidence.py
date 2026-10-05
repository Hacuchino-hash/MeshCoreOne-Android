"""AndroidOnly: WP-207 Real native repository/preference consumer proof, never prerequisite source credit."""

import argparse
import hashlib
import json
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT / "tools" / "android-port"))
from controller.ci_evidence import suite_counts, artifact_record
from controller.errors import PortError
from controller.model import git
from controller.module_junit import safe_reports

EXPECTED = {
    "data": (
        "com.meshcoreone.android.core.data.ConnectionRuntimeRoomIntegrationTest",
        {
            "changedEndpointPreservesRestoredPartitionAndRealContactMessagePendingTriple",
            "processStartupGloballyResetsStaleSessionsIncludingOrphanRadioWithoutTouchingPermissionsOrNativeSortDates",
            "actualProcessStoreSurvivesTwoPhysicalGenerationsAndRuntimeClose",
            "ghostReconciliationExecutesTheActualRepositoryAlgorithmWithoutRekeyingUnrelatedPartitions",
        },
        158,
    ),
    "datastore": (
        "com.meshcoreone.android.core.datastore.ConnectionRuntimePreferenceIntegrationTest",
        {
            "actualCanonicalLastRadioAndIntentSurviveProcessStoreReopen",
            "connectionUpdatesPreserveRawOrderedListsUnknownSelectionsAndPerDevicePresence",
            "distinctLastConnectionAndBondHoldersClearIndependentlyInTheActualStore",
            "missingPerDeviceDefaultsAreWrittenOnceAndExplicitFalseIsNeverOverwritten",
            "closedActualStoreErrorsNeverBecomeMissingConnectionSuccess",
            "queuedBondRefreshCannotRecreateForgottenSlotAfterRealDataStoreClear",
        },
        137,
    ),
}


def collect():
    result = {}
    for module, (class_name, expected, minimum) in EXPECTED.items():
        directory = ROOT / "android" / "core" / module / "build" / "test-results" / "testDebugUnitTest"
        files = safe_reports(directory, ROOT)
        counts = suite_counts(directory, minimum)
        selected = {}
        for path in files:
            raw = path.read_bytes()
            for case in ET.fromstring(raw).findall("testcase"):
                if case.get("classname") == class_name:
                    name = case.get("name")
                    if name in selected:
                        raise PortError("Duplicate native runtime consumer assertion")
                    selected[name] = {
                        "junit": path.relative_to(ROOT).as_posix(), "sha256": hashlib.sha256(raw).hexdigest(),
                    }
        if set(selected) != expected:
            raise PortError("Missing/extra real runtime consumer assertions: " + module)
        result[module] = {
            "full_module_counts": counts, "runtime_consumer_cases": selected,
            "raw_junit": [artifact_record(ROOT, path) for path in files],
        }
    return {
        "schema_version": 1, "work_package": "WP-207",
        "head_sha": git(ROOT, "rev-parse", "HEAD").decode().strip(),
        "scope": "Actual simulated SDK31 Room/DataStore consumers; no earlier WP source credit or hardware claims",
        "modules": result,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    result = collect()
    if args.output is not None:
        if args.output.resolve().is_relative_to(ROOT) and not args.output.resolve().is_relative_to(Path(__file__).parent):
            raise PortError("Native evidence output is outside the owning lease")
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({module: value["full_module_counts"] for module, value in result["modules"].items()}))


if __name__ == "__main__":
    main()
