# AndroidOnly: WP-304 Frozen source/case accounting and exact current owned input identities.
from __future__ import annotations

import hashlib
import json
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[4]
PIN = "db14559b39d32322b06477c6ae676112f583db50"
MANIFEST = "78a22920beaa5899f9618806b5cd2b27d50399a9b29b4d8dbd79f755717ec746"
PRIMARY = "e76f2a7a42fc8a27173133750f071cc03ecde35083bab59ff4fdf8e0ac396802"
ROOT_LOCK_SHA = "95055e812451d9906683f36ee3e46373dc5fe5424fa833f02163bb13f78f1c96"
FROZEN_FAULT_PATH = "android/core/contracts/src/main/kotlin/com/meshcoreone/android/core/contracts/domain/errors/DeviceSettingsFaults.kt"
FROZEN_FAULT_BLOB = "b2a6b84a3846c016184e06772da4800700e3e8af"
FROZEN_MESSAGING_PATH = "android/core/contracts/src/main/kotlin/com/meshcoreone/android/core/contracts/domain/errors/MessagingFaults.kt"
FROZEN_MESSAGING_BLOB = "d6f96917b135f12d3fa62c0e5c456aac709257bc"
FROZEN_SERVICE_CARRIES = {
    "android/core/contracts/src/main/kotlin/com/meshcoreone/android/core/contracts/domain/errors/ContactsChannelAdvertisementFaults.kt":
        "3f1f2077a0846242b1bbec669ce6e0ac913d1bae",
    "android/core/contracts/src/main/kotlin/com/meshcoreone/android/core/contracts/domain/errors/RemoteRoomNodeConfigFaults.kt":
        "bc2d9b387d6183062ba90fa6f23be1197bacf1c1",
}
PROJECTION_INPUTS = (
    "android/core/contracts/src/main/kotlin/com/meshcoreone/android/core/contracts/domain/errors/RuntimeFaults.kt",
    "android/core/contracts/src/main/kotlin/com/meshcoreone/android/core/contracts/domain/errors/BleFaults.kt",
    "android/core/runtime/src/main/kotlin/com/meshcoreone/android/core/runtime/ConnectionError.kt",
    "android/core/runtime/src/main/kotlin/com/meshcoreone/android/core/runtime/TimeoutUtility.kt",
    "android/core/ble/src/main/kotlin/com/meshcoreone/android/core/ble/BleError.kt",
    "android/core/runtime/src/test/kotlin/com/meshcoreone/android/core/runtime/RuntimeFaultProjectionTest.kt",
    "android/core/ble/src/test/kotlin/com/meshcoreone/android/core/ble/BleFaultProjectionTest.kt",
    "android/core/services/src/main/kotlin/com/meshcoreone/android/core/services/contacts/ContactServiceError.kt",
    "android/core/services/src/main/kotlin/com/meshcoreone/android/core/services/contacts/ChannelServiceSupport.kt",
    "android/core/services/src/main/kotlin/com/meshcoreone/android/core/services/contacts/AdvertisementService.kt",
    "android/core/services/src/main/kotlin/com/meshcoreone/android/core/services/remote/RemoteNodeError.kt",
    "android/core/services/src/main/kotlin/com/meshcoreone/android/core/services/remote/RoomServerError.kt",
    "android/core/services/src/main/kotlin/com/meshcoreone/android/core/services/remote/BinaryProtocolError.kt",
    "android/core/services/src/main/kotlin/com/meshcoreone/android/core/services/remote/NodeConfigServiceTypes.kt",
    "android/core/services/src/test/kotlin/com/meshcoreone/android/core/services/contacts/SourceServiceFaultProjectionTests.kt",
    "android/core/services/src/test/kotlin/com/meshcoreone/android/core/services/remote/RemoteRoomNodeConfigFaultProjectionTests.kt",
)
FROZEN_SERVICE_PROJECTION_TESTS = {
    "android/core/services/src/test/kotlin/com/meshcoreone/android/core/services/contacts/SourceServiceFaultProjectionTests.kt":
        "c0d4770bd746a910bd275f1360bb6bd65a6162e6",
    "android/core/services/src/test/kotlin/com/meshcoreone/android/core/services/remote/RemoteRoomNodeConfigFaultProjectionTests.kt":
        "a844d0565d78d65ad10191f886c62e9b09f574a0",
}
PROJECTION_SUITES = {
    "runtime": {
        "directory": "android/core/runtime/build/test-results/test",
        "classname": "com.meshcoreone.android.core.runtime.RuntimeFaultProjectionTest",
        "module": "runtime",
        "declaration_kind": "runtime-native-case",
        "methods": {
            "WP-207::runtime faults preserve every connection payload cause and diagnostic",
            "WP-207::runtime timeout projection preserves operation Duration and constructor behavior",
            "WP-207::actual runtime deadline projects its fault and joins the cancelled child",
            "WP-207::cooperative runtime deadline remains cancellation without a fault carrier",
        },
    },
    "ble": {
        "directory": "android/core/ble/build/test-results/testDebugUnitTest",
        "classname": "com.meshcoreone.android.core.ble.BleFaultProjectionTest",
        "module": "ble",
        "declaration_kind": "junit-method",
        "methods": {
            "allSourceAndNativeCasesKeepTheirPayloadCauseRecoveryAndDiagnostics",
            "everyMetadataEnumAndNullableOrExtremeStatusProjectsWithoutOrdinalCoercion",
            "actualGattThrowSitesStillDistinguishAttAndConnectionStateStatusEight",
            "sourceAndNeutralCasesAreExactlyOneToOneAndAllRecoveryValuesAreRepresented",
        },
    },
    "contacts": {
        "directory": "android/core/services/build/test-results/test",
        "classname": "com.meshcoreone.android.core.services.contacts.SourceServiceFaultProjectionTests",
        "module": "services",
        "declaration_kind": "contacts-native-case",
        "case_count": 25,
    },
    "remote": {
        "directory": "android/core/services/build/test-results/test",
        "classname": "com.meshcoreone.android.core.services.remote.RemoteRoomNodeConfigFaultProjectionTests",
        "module": "services",
        "declaration_kind": "remote-native-case",
        "case_count": 41,
    },
}
COMMITTED_PRODUCER_INPUTS = (
    "android/core/model/src/main/kotlin/com/meshcoreone/android/core/model/CommittedBackupPreferenceFailure.kt",
    "android/core/data/src/main/kotlin/com/meshcoreone/android/core/data/backup/AppBackupService.kt",
    "android/core/data/src/test/kotlin/com/meshcoreone/android/core/data/backup/BackupTransactionTest.kt",
)
FAMILY_SCENARIOS = {
    "MC1ServicesTests": 2, "ErrorLocalizationTests": 30,
    "BatteryInfoDisplayTests": 18, "BatteryPercentageCalculationTests": 6,
    "ErrorDispatchCoverageTests": 1, "ErrorUserFacingMessageTests": 34,
    "ThemedSurfaceRowFillTests": 4, "AvatarCropGeometryTests": 9,
    "RSSIScanTrackerTests": 8, "RSSITuningTests": 18,
    "WiFiHostValidationTests": 9, "RelativeTimestampTextTests": 11,
    "SyncingPillViewTests": 8,
}
PARAMETERS = {
    "ErrorLocalizationTests::MeshCoreError.deviceError maps known firmware codes(code : UInt8 , expected : String)": 6,
    "ErrorLocalizationTests::ProtocolError cases produce non-empty, readable descriptions(protocolError : ProtocolError)": 6,
    "RSSIScanTrackerTests::an unusable reading is skipped and creates no entry(rssi : Int)": 3,
    "RSSITuningTests::isUsable accepts negative dBm readings and rejects the unavailable sentinels(rssi : Int , expected : Bool)": 5,
    "RSSITuningTests::first reading maps directly at the tier thresholds(rssi : Int , expected : RSSITuning . SignalTier)": 3,
    "RSSITuningTests::fillLevel maps each tier to its glyph fill(tier : RSSITuning . SignalTier , expected : Double)": 3,
    "WiFiHostValidationTests::classifies host(_ host : String , _ expected : Bool)": 9,
}


class EvidenceError(ValueError):
    pass


def require(condition, message):
    if not condition:
        raise EvidenceError(message)


def unique_json(path):
    def unique(pairs):
        result = {}
        for key, value in pairs:
            require(key not in result, "Duplicate JSON key")
            result[key] = value
        return result
    return json.loads(path.read_text(encoding="utf8"), object_pairs_hook=unique)


def digest(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=True).encode()).hexdigest()


def git(*arguments, data=None):
    return subprocess.check_output(["git", "-C", str(ROOT), *arguments], input=data)


def inventory():
    manifest = unique_json(ROOT / "docs" / "android" / "port-manifest.json")
    require(digest(manifest) == MANIFEST and manifest["reference"]["commit"] == PIN, "Manifest/source drift")
    inputs = [row for row in manifest["inventory"] if row["primary_owner"] == "WP-304"]
    require(len(inputs) == 88 and sum(row["kind"] == "production" for row in inputs) == 75
        and sum(row["kind"] == "test" for row in inputs) == 13, "Changed primary macro")
    require(digest({row["path"]: row["blob_sha"] for row in inputs}) == PRIMARY, "Primary ownership/blob map drift")
    for row in inputs:
        require(git("rev-parse", PIN + ":" + row["path"]).decode().strip() == row["blob_sha"], "Frozen blob drift")
        current = ROOT.joinpath(*row["path"].split("/"))
        require(current.is_file(), "Missing assigned input")
        require(git("hash-object", "--", str(current)).decode().strip() == row["blob_sha"], "Assigned checkout drift")
    lock = ROOT / "android" / "gradle" / "dependency-locks" / "core-ui.lockfile"
    require(hashlib.sha256(lock.read_bytes().replace(b"\r\n", b"\n")).hexdigest() == ROOT_LOCK_SHA, "Unleased ROOT UI lock changed")
    fault = ROOT.joinpath(*FROZEN_FAULT_PATH.split("/"))
    require(fault.is_file() and git("hash-object", "--", str(fault)).decode().strip() == FROZEN_FAULT_BLOB,
        "Frozen producer Device/Settings fault carry missing or changed")
    messaging = ROOT.joinpath(*FROZEN_MESSAGING_PATH.split("/"))
    require(messaging.is_file() and git("hash-object", "--", str(messaging)).decode().strip() == FROZEN_MESSAGING_BLOB,
        "Frozen producer Message/Polling/Queue fault carry missing or changed")
    for relative, expected in FROZEN_SERVICE_CARRIES.items():
        path = ROOT.joinpath(*relative.split("/"))
        require(path.is_file() and git("hash-object", "--", str(path)).decode().strip() == expected,
            "Frozen external service fault declaration missing or changed")
    for relative in PROJECTION_INPUTS:
        require(ROOT.joinpath(*relative.split("/")).is_file(), "Missing granted actual Runtime/BLE projection input")
    for relative, expected in FROZEN_SERVICE_PROJECTION_TESTS.items():
        require(git("hash-object", "--", str(ROOT.joinpath(*relative.split("/")))).decode().strip() == expected,
            "Landed service projection assertions changed without their source-owner receipt")
    catalog = unique_json(ROOT / "docs" / "android" / "test-cases.json")
    require(catalog["source_sha"] == PIN, "Original case pin drift")
    owned_paths = {row["path"] for row in inputs}
    cases = {}
    suites = {}
    for entry in catalog["entries"]:
        if entry["path"] not in owned_paths:
            continue
        require(git("rev-parse", PIN + ":" + entry["path"]).decode().strip() == entry["blob_sha"], "Original case blob drift")
        count = 0
        for case in entry["cases"]:
            identifier = case["id"]
            require(identifier not in cases, "Duplicate frozen original case")
            parameters = PARAMETERS.get(identifier, 1)
            require((case["parameter_family"] != "single") == (identifier in PARAMETERS), "Changed parameter family")
            cases[identifier] = {"path": entry["path"], "parameter_family": case["parameter_family"], "scenarios": parameters}
            count += parameters
        suites[Path(entry["path"]).stem] = count
    require(len(cases) == 130 and sum(case["scenarios"] for case in cases.values()) == 158, "Changed original source floor")
    require(suites == FAMILY_SCENARIOS, "Changed original suite/parameter accounting")
    validate_native_resources()
    return inputs, cases


def validate_native_resources():
    directory = ROOT / "android" / "core" / "ui" / "src" / "main" / "res"
    expected = {"values", "values-de", "values-es", "values-fr", "values-it", "values-ko", "values-nl",
        "values-pl", "values-pt", "values-ru", "values-uk", "values-b+zh+Hans"}
    files = sorted(directory.glob("values*/native_errors.xml"))
    require({path.parent.name for path in files} == expected and len(files) == 12, "Missing/extra native error locale")
    names = None
    for path in files:
        raw = path.read_bytes()
        require(b"<!DOCTYPE" not in raw.upper() and b"<!ENTITY" not in raw.upper(), "Unsafe native resource XML")
        rows = list(ET.fromstring(raw))
        keys = [row.get("name") for row in rows]
        require(all(keys) and len(keys) == len(set(keys)) and all(row.tag == "string" and row.text for row in rows),
            "Missing/duplicate/empty native copy")
        if names is None:
            names = set(keys)
        require(set(keys) == names, "Native locale fallback would hide a missing error distinction")
        for row in rows:
            placeholders = re.findall(r"%\d+\$[a-z]", row.text)
            require(placeholders == (["%1$d"] if row.get("name") in {"ui_storage_unsupported_version", "ui_storage_too_large"} else []),
                "Native resource parameter mismatch")
    return {"locales": len(files), "keys_per_locale": len(names)}


def native_inputs():
    roots = [ROOT / "android" / "core" / "ui", Path(__file__).parent]
    files = []
    for root in roots:
        for path in root.rglob("*"):
            if path.is_file() and not any(part in {"build", "__pycache__"} for part in path.relative_to(root).parts):
                files.append(path)
    result = {}
    for path in sorted(set(files)):
        relative = path.relative_to(ROOT).as_posix()
        raw = path.read_bytes()
        blob = git("hash-object", "--path", relative, "--stdin", data=raw).decode().strip()
        result[relative] = {"working_blob": blob, "bytes": len(raw), "sha256": hashlib.sha256(raw).hexdigest()}
    for relative in (FROZEN_FAULT_PATH, FROZEN_MESSAGING_PATH, *COMMITTED_PRODUCER_INPUTS,
            *FROZEN_SERVICE_CARRIES, *PROJECTION_INPUTS):
        path = ROOT.joinpath(*relative.split("/"))
        require(path.is_file(), "Missing actual frozen/projection producer input")
        raw = path.read_bytes()
        result[relative] = {
            "working_blob": git("hash-object", "--", str(path)).decode().strip(),
            "bytes": len(raw), "sha256": hashlib.sha256(raw).hexdigest(),
        }
    return result
