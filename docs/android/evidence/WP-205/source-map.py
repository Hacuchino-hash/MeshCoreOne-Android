"""AndroidOnly: WP-205 Deterministic accounting of frozen original cases, not execution or acceptance."""

import argparse
import json
import re
from pathlib import Path

SOURCE = "db14559b39d32322b06477c6ae676112f583db50"
ROOT = Path(__file__).resolve().parents[4]
TEST_ROOT = ROOT / "android" / "core" / "ble" / "src" / "test" / "kotlin"


def symbol(suite, method):
    return {"suite": suite, "method": method}


VALUES = "BleValuesTest"
LIFE = "BleLifecycleTest"
OPS = "BleOperationFailureTest"
DISCOVERY = "BleDiscoveryTest"
WRITES = "BleWriteTest"
STREAM = "BleStreamTest"
ADAPTER = "AndroidGattAdapterTest"
BONDS = "BleBondRefreshTest"

PHASE = [
    ("ported", VALUES, "idle phase has correct name", None),
    ("ported", VALUES, "idle is not part of discovery chain", None),
    ("ported", VALUES, "idle phase is not active", None),
    ("native-equivalent", VALUES, "idle phase has no peripheral", "A-01"),
    ("native-equivalent", VALUES, "idle phase has no deviceID", "A-01"),
]

BASE = [
    ("ported", VALUES, "initializes in idle phase", None),
    ("ported", VALUES, "isConnected returns false when idle", None),
    ("native-equivalent", VALUES, "connectedDeviceID returns nil when idle", "A-01"),
    ("native-equivalent", VALUES, "isAutoReconnecting returns false when idle", "A-06"),
    ("ported", VALUES, "linkDiagnostics reports the idle phase when idle", None),
    ("ported", VALUES, "disconnect returns immediately when idle", None),
    ("platform-adaptation", VALUES, "Bluetooth addresses are redacted connection handles not UUID identities", "A-01"),
    ("ported", VALUES, "send throws notConnected when idle", None),
    ("ported", VALUES, "disconnect is idempotent", None),
    ("platform-adaptation", LIFE, "construction is idempotent activation without a physical connection", "A-01"),
    ("ported", VALUES, "connection generation starts at zero", None),
    ("platform-adaptation", STREAM, "old disconnect after rapid reconnect is rejected despite matching device handle", "A-04"),
    ("platform-adaptation", OPS, "wrong generation operation and reply kind cannot complete the current waiter", "A-04"),
    ("platform-adaptation", OPS, "wrong generation operation and reply kind cannot complete the current waiter", "A-04"),
    ("platform-adaptation", LIFE, "explicit reconnect uses fifteen second discovery without an implicit loop", "A-03"),
    ("platform-adaptation", LIFE, "connect discovers MTU and subscribes in exact whole operation order", "A-04"),
    ("platform-adaptation", LIFE, "discovery timeout is forty seconds for the whole chain not each request", "A-03"),
    ("platform-adaptation", DISCOVERY, "MTU request without a callback is bounded by the discovery deadline", "A-03"),
    ("platform-adaptation", LIFE, "discovery timeout is forty seconds for the whole chain not each request", "A-03"),
    ("native-equivalent", OPS, "disconnect and duplicate terminal callbacks cannot close twice", "A-03"),
]

RESTORATION = [
    ("platform-adaptation", LIFE, "disconnect invalidates a connect queued behind the old attempt", "A-06"),
    ("platform-adaptation", VALUES, "disconnect is idempotent", "A-06"),
    ("native-equivalent", VALUES, "isAutoReconnecting returns false when idle", "A-06"),
    ("platform-adaptation", OPS, "disconnect and duplicate terminal callbacks cannot close twice", "A-06"),
    ("native-equivalent", DISCOVERY, "disconnect after subscription reply before adoption preserves auth classification", "A-04"),
    ("native-equivalent", LIFE, "close completion is awaited before a new generation opens", "A-04"),
    ("platform-adaptation", OPS, "cancellation closes the active operation once and joins its timer", "A-07"),
    ("ported", WRITES, "stale write callback is dropped instead of resuming the newer write", None),
    ("native-equivalent", ADAPTER, "real nonzero authentication callback status preserves typed recovery", "A-02"),
    ("native-equivalent", ADAPTER, "status133 is a retained GATT failure not guessed pairing or competing app", "A-02"),
]

WITHOUT_RESPONSE = [
    ("native-equivalent", OPS, "matching immediate callbacks register before triggering and leave no timer", "A-05"),
    ("native-equivalent", WRITES, "concurrent writes are serialized through completion not just initial calls", "A-05"),
    ("native-equivalent", LIFE, "receiver and readiness operations end on explicit idempotent disconnect", "A-05"),
    ("native-equivalent", WRITES, "write readiness timeout is exactly five seconds and invalidates its GATT", "A-05"),
    ("native-equivalent", WRITES, "stale write callback is dropped instead of resuming the newer write", "A-05"),
    ("platform-adaptation", WRITES, "without response capability requires characteristic and verified firmware evidence", "A-05"),
    ("ported", WRITES, "write only ESP32 characteristic stays on acknowledged writes", None),
]

MAPPINGS = {
    "BLEPhaseTests.swift": PHASE,
    "BLEStateMachineTests.swift": BASE,
    "BLEStateMachineRestorationAndTeardownTests.swift": RESTORATION,
    "BLEStateMachineWriteWithoutResponseTests.swift": WITHOUT_RESPONSE,
}

BOND_METHODS = [
    "successful RSSI with live session refreshes an existing verification",
    "failed RSSI does not refresh verification",
    "pre handshake connected without session live does not refresh",
    "stale session live after phase teardown does not refresh on reconnect",
    "dead stack with preserved connected phase does not refresh",
    "refresh never creates a verification that was cleared",
    "clear then tick and tick then clear both leave nil",
    "stale bond and live RSSI never shield a definitive authentication failure",
    "cross launch seed uses the refreshed date without manufacturing a new verification",
    "joint zombie shield cannot gain new freshness after session live is cleared",
    "onBondRefreshed fires only when refresh mutates",
    "forget after RSSI hop invalidates the refresh rather than resurrecting keys",
    "epoch gate blocks an old refresh after clear even when a new verification was seeded",
]


def mappings_for(name, count):
    if name in MAPPINGS:
        rows = MAPPINGS[name]
    elif name == "BondShieldRefreshTests.swift":
        rows = [
            ("platform-adaptation" if i in (7, 9)
             else "component-equivalent-consumer-proof-deferred" if i in (8, 11, 12)
             else "ported",
             BONDS, method, "A-06" if i in (7, 9) else "A-07" if i in (8, 11, 12) else None)
            for i, method in enumerate(BOND_METHODS)
        ]
    elif name in ("BLEStateMachineAutoReconnectRetryTests.swift", "BLEStateMachineFringeEncryptionGraceTests.swift"):
        rows = [
            ("platform-adaptation", LIFE,
             "explicit reconnect uses fifteen second discovery without an implicit loop", "A-06")
            for _ in range(count)
        ]
    elif name == "BLEStateMachineBondSuspectRecoveryTests.swift":
        rows = [
            ("platform-adaptation", DISCOVERY, "MTU request without a callback is bounded by the discovery deadline", "A-03"),
            ("native-equivalent", DISCOVERY, "disconnect after subscription reply before adoption preserves auth classification", "A-04"),
        ]
    elif name == "BLEStateMachineDisconnectionMappingTests.swift":
        rows = [
            ("native-equivalent", ADAPTER, "real nonzero authentication callback status preserves typed recovery", "A-02"),
            ("native-equivalent", LIFE, "bond loss is definitive even after a connected verified firmware session", "A-02"),
            ("ported", LIFE, "clean disconnect is not mislabeled authentication or other app ownership", None),
            ("native-equivalent", LIFE, "bonding failure during setup cannot leak a continuation", "A-02"),
            ("native-equivalent", "BleGattStatusTest", "ATT status maps to source typed errors and retains operation metadata", "A-02"),
            ("native-equivalent", "BleGattStatusTest", "ATT status maps to source typed errors and retains operation metadata", "A-02"),
            ("native-equivalent", "BleGattStatusTest", "ATT status maps to source typed errors and retains operation metadata", "A-02"),
        ]
    elif name == "BLEStateMachineEmptyGATTHoldTests.swift":
        rows = [
            ("platform-adaptation", DISCOVERY, "empty successful service discovery fails closed without an OS reconnect hold", "A-06"),
            ("platform-adaptation", STREAM, "old disconnect after rapid reconnect is rejected despite matching device handle", "A-04"),
            ("platform-adaptation", DISCOVERY, "missing TX or RX characteristics never create a partial connected transport", "A-06"),
            ("platform-adaptation", DISCOVERY, "notification subscription without completion cannot claim connected", "A-06"),
            ("platform-adaptation", DISCOVERY, "missing or duplicated notification CCCD fails before negotiation or writes", "A-06"),
            ("native-equivalent", "BleGattStatusTest", "ATT status maps to source typed errors and retains operation metadata", "A-02"),
            ("native-equivalent", "BleGattStatusTest", "ATT status maps to source typed errors and retains operation metadata", "A-02"),
            ("native-equivalent", LIFE, "receiver and readiness operations end on explicit idempotent disconnect", "A-06"),
        ]
    elif count == 0:
        rows = []
    else:
        raise ValueError(f"Unaccounted original source: {name}")
    if len(rows) != count:
        raise ValueError(f"Original case inventory changed: {name}")
    return rows


def build():
    manifest = json.loads((ROOT / "docs" / "android" / "port-manifest.json").read_text(encoding="utf-8"))
    catalog = json.loads((ROOT / "docs" / "android" / "test-cases.json").read_text(encoding="utf-8"))
    if manifest["reference"]["commit"] != SOURCE or catalog["source_sha"] != SOURCE:
        raise ValueError("Original source pin changed")
    owned = [entry for entry in manifest["inventory"] if entry["primary_owner"] == "WP-205"]
    originals = {entry["path"]: entry for entry in catalog["entries"]}
    native = set()
    for path in sorted(TEST_ROOT.rglob("*.kt")):
        data = path.read_text(encoding="utf-8")
        for method in re.findall(r"@Test\s+fun\s+`([^`]+)`", data):
            native.add((path.stem, method))
    files = []
    cases = []
    for entry in owned:
        original = originals.get(entry["path"])
        source_cases = [] if original is None else original["cases"]
        rows = mappings_for(Path(entry["path"]).name, len(source_cases)) if original is not None else []
        files.append({
            "path": entry["path"], "blob_sha": entry["blob_sha"], "kind": entry["kind"],
            "original_declarations": len(source_cases),
        })
        for case, (disposition, suite, method, adaptation) in zip(source_cases, rows, strict=True):
            if (suite, method) not in native:
                raise ValueError(f"Missing actual native assertion method: {suite}::{method}")
            cases.append({
                "source_path": entry["path"], "source_blob_sha": entry["blob_sha"],
                "original_id": case["id"], "original_parameter_family": case["parameter_family"],
                "disposition": disposition, "native_assertion": symbol(suite, method),
                "adaptation": adaptation, "execution": "requires actual raw JUnit binding",
                "review": "proposed; no independent acceptance claimed",
            })
    if len(files) != 27 or len(cases) != 86:
        raise ValueError("Incomplete WP-205 ownership/case accounting")
    return {
        "generated_by": "docs/android/evidence/WP-205/source-map.py",
        "source_sha": SOURCE, "work_package": "WP-205",
        "scope": "Original-to-native assertion accounting; not execution, parity acceptance or hardware evidence",
        "owned_inputs": files, "original_declarations": len(cases), "cases": cases,
        "native_assertion_methods": [symbol(suite, method) for suite, method in sorted(native)],
        "inherited_adapter_runtime_suites": ["AndroidGattApi31Test", "AndroidGattApi32Test", "AndroidGattApi33Test", "AndroidGattApi37Test"],
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    path = Path(__file__).with_name("source-cases.json")
    expected = json.dumps(build(), indent=2, ensure_ascii=True) + "\n"
    if args.check:
        if path.read_text(encoding="utf-8") != expected:
            raise ValueError("Original/native accounting is stale or malformed")
    else:
        path.write_text(expected, encoding="utf-8")
    print("Accounted for 27 owned inputs and all 86 frozen declarations; execution/review remain separate.")


if __name__ == "__main__":
    main()
