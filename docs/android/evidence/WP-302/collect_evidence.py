"""WP-302 fail-closed original-family/native-state/render evidence; no device or gate authority."""
from __future__ import annotations

import argparse
import base64
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import struct
import subprocess
import sys
import uuid
import unittest

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT / "tools" / "android-port"))
from controller.ci_evidence import read_xml, suite_counts
from controller.errors import PortError
from controller.model import load_manifest

PIN = "db14559b39d32322b06477c6ae676112f583db50"
APP = ROOT / "android" / "app"
OUT = APP / "build" / "reports" / "wp302"
NAV = Path("kotlin/com/meshcoreone/android/app/navigation")
TEST_METHOD = re.compile(r"@Test\s+fun\s+(?:`([^`]+)`|(\w+))")
SPECIAL_NAMES = {
    "ChatViewModel.navigateToMap forwards the coordinate to the navigation sink": "chatConsumerForwardsMapCoordinateToNavigationSink",
    "Line of Sight and Trace Path collapse the sidebar; other tools keep it": "lineOfSightAndTracePathCollapseSidebarOtherToolsKeepIt",
}


def fail(message):
    raise PortError("WP-302: " + message)


def git(*args):
    return subprocess.check_output(["git", "-C", str(ROOT), *args])


def digest(data):
    return hashlib.sha256(data).hexdigest()


def canonical(value):
    return json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=True).encode()


def method_name(value):
    return re.sub(r"\[(?:31|37)\]$", "", value.removesuffix("()")).removesuffix("()")


def inventory():
    manifest = load_manifest(ROOT)
    owned = [entry for entry in manifest.data["inventory"] if entry["primary_owner"] == "WP-302"]
    inputs = {entry["path"]: entry["blob_sha"] for entry in owned}
    retained = json.loads(Path(__file__).with_name("source-inventory.json").read_text(encoding="utf8"))
    if len(inputs) != 10 or inputs != {entry["path"]: entry["blob_sha"] for entry in retained["inputs"]}:
        fail("primary source inventory changed")
    for path, expected in inputs.items():
        for revision in (PIN, "HEAD"):
            if git("rev-parse", revision + ":" + path).decode().strip() != expected:
                fail("source drift at " + revision + ":" + path)
    catalog = json.loads((ROOT / "docs/android/test-cases.json").read_text(encoding="utf8"))
    originals = {}
    for entry in catalog["entries"]:
        if entry["path"] in inputs and entry["path"].endswith("Tests.swift"):
            if entry["blob_sha"] != inputs[entry["path"]] or not entry["has_assertions"]:
                fail("invalid original assertion inventory")
            for case in entry["cases"]:
                if case["id"] in originals:
                    fail("duplicate original family")
                originals[case["id"]] = {"source_path": entry["path"], **case}
    if len(originals) != 50:
        fail("all fifty original families are mandatory")
    shared = APP / "src/androidTest" / NAV / "cases/OriginalNavigationCases.kt"
    names = [a or b for a, b in TEST_METHOD.findall(shared.read_text(encoding="utf8"))]
    mapped = {}
    for case in originals:
        original_name = case.split("::", 1)[1].removesuffix("()")
        native = SPECIAL_NAMES.get(original_name, original_name)
        if names.count(native) != 1:
            fail("missing/duplicate original assertion body: " + case)
        mapped[case] = {
            "class": "com.meshcoreone.android.app.navigation.cases.OriginalNavigationTest", "method": native,
            "classification": "extracted-map-consumer-equivalent-pending-review" if original_name.startswith("ChatViewModel.")
                else "native-layout-adaptation" if case.startswith("SidebarNavigationLayoutTests::") else "ported",
        }
    if set(names) != {value["method"] for value in mapped.values()}:
        fail("original bodies do not match the full catalog")
    expected = {(value["class"], value["method"], sdk) for value in mapped.values() for sdk in ("31", "37")}
    for path in (APP / "src/test" / NAV).glob("*Test.kt"):
        content = path.read_text(encoding="utf8")
        cls = re.search(r"^class (\w+)", content, re.M)
        package = re.search(r"^package ([\w.]+)", content, re.M)
        if cls is None or package is None:
            fail("malformed native class: " + str(path))
        sdks = ("31", "37") if "@Config(sdk = [31, 37]" in content else ("jvm",)
        for a, b in TEST_METHOD.findall(content):
            for sdk in sdks:
                identity = (package[1] + "." + cls[1], a or b, sdk)
                if identity in expected:
                    fail("duplicate declared native assertion")
                expected.add(identity)
    if len(expected) <= 100:
        fail("missing native adaptation/assertion suites")
    return manifest.sha256, inputs, originals, mapped, expected


def current_inputs():
    paths = set(git("ls-files", "-z", "--", "android").decode().split("\0")) - {""}
    paths = {path for path in paths if "/src/" in path or path.endswith((".gradle.kts", ".toml", ".lockfile"))
             or path.endswith("verification-metadata.xml")}
    paths |= {"docs/android/evidence/WP-302/collect_evidence.py", "docs/android/evidence/WP-302/source-inventory.json"}
    result = {}
    for relative in sorted(paths):
        raw = (ROOT / relative).read_bytes()
        committed = git("show", "HEAD:" + relative)
        if raw != committed:
            fail("compiled checkout bytes differ from candidate Git input: " + relative)
        result[relative] = {"sha256": digest(raw), "size": len(raw)}
    return result


def bind_inputs(path):
    manifest, source, _, _, _ = inventory()
    inputs = current_inputs()
    value = {
        "schema_version": 1, "wp": "WP-302", "nonce": str(uuid.uuid4()),
        "head": git("rev-parse", "HEAD").decode().strip(), "tree": git("rev-parse", "HEAD^{tree}").decode().strip(),
        "source_sha": PIN, "manifest_sha256": manifest, "source_inputs": source,
        "inputs": inputs, "inputs_sha256": digest(canonical(inputs)),
    }
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf8")
    path.with_suffix(".properties").write_text(
        "".join(key + "=" + value[key] + "\n" for key in ("nonce", "head", "tree", "inputs_sha256")), encoding="ascii",
    )
    return value


def verify_binding(binding):
    if binding["schema_version"] != 1 or binding["wp"] != "WP-302" or binding["source_sha"] != PIN:
        fail("wrong pre-test binding")
    if binding["head"] != git("rev-parse", "HEAD").decode().strip() or binding["tree"] != git("rev-parse", "HEAD^{tree}").decode().strip():
        fail("stale candidate binding")
    inputs = current_inputs()
    if binding["inputs"] != inputs or binding["inputs_sha256"] != digest(canonical(inputs)):
        fail("stale/altered compiled-input binding")


def native_reports(directory, binding, expected):
    counts = suite_counts(directory, 1)
    reports, nodes, executed, renders = [], {}, {}, {}
    prefix = "com.meshcoreone.android.app.navigation."
    for path in sorted(directory.glob("TEST-*.xml")):
        root = read_xml(path)
        reports.append({"name": path.name, "bytes": path.stat().st_size, "sha256": digest(path.read_bytes())})
        for case in root.iter("testcase"):
            cls, name = case.get("classname", ""), method_name(case.get("name", ""))
            if cls.startswith(prefix):
                identity = (cls, name)
                nodes[identity] = nodes.get(identity, 0) + 1
        for output in root.iter("system-out"):
            for line in (output.text or "").splitlines():
                if line.startswith("WP302_EXECUTION|"):
                    parts = line.split("|")
                    if len(parts) != 8 or parts[1:5] != [binding[key] for key in ("nonce", "head", "tree", "inputs_sha256")]:
                        fail("malformed/stale per-test execution binding")
                    identity = (parts[5], method_name(parts[6]), parts[7])
                    if identity not in expected or identity in executed:
                        fail("unknown/duplicate actual native assertion: " + repr(identity))
                    executed[identity] = True
                elif line.startswith("WP302_RENDER|"):
                    parts = line.split("|")
                    if len(parts) != 6 or parts[1] in renders or not re.fullmatch(r"[a-z0-9-]+", parts[1]):
                        fail("malformed/duplicate render")
                    name, width, height, checksum = parts[1], int(parts[2]), int(parts[3]), parts[4]
                    data = base64.b64decode(parts[5], validate=True)
                    if not 0 < len(data) < 16 * 1024 * 1024 or not 0 < width <= 4096 or not 0 < height <= 4096:
                        fail("invalid native image bounds")
                    if data[:8] != b"\x89PNG\r\n\x1a\n" or struct.unpack(">II", data[16:24]) != (width, height) or digest(data) != checksum:
                        fail("native image bytes/dimensions/hash disagree")
                    renders[name] = {"width": width, "height": height, "sha256": checksum, "bytes": len(data), "data": data}
    if set(executed) != expected:
        fail("missing current assertions: " + repr(sorted(expected - executed.keys())))
    expected_nodes = {}
    for cls, name, _ in expected:
        identity = (cls, name)
        expected_nodes[identity] = expected_nodes.get(identity, 0) + 1
    if nodes != expected_nodes:
        fail("XML testcase nodes and bound executions disagree")
    states = {"width-" + str(width) for width in (360, 599, 600, 744, 779, 780, 834, 1080)} | {
        "tabs-selected-settings", "resize-expanded-draft", "resize-compact-draft", "collapse-tracepath",
        "collapse-lineofsight", "native-back-root", "font200-rtl-cjk", "font200-rtl-rail",
    }
    states |= {
        "theme-" + theme + "-" + scheme + "-" + contrast
        for theme in ("default", "ember", "fern", "marine", "olive", "lavender", "sakura", "solarized", "nord", "catppuccin")
        for scheme in (("dark",) if theme == "ember" else ("light", "dark")) for contrast in ("standard", "hc")
    }
    required = {"sdk-" + sdk + "-" + state for sdk in ("31", "37") for state in states}
    if renders.keys() != required:
        fail("missing/unexpected SDK31/37 native screen states")
    return counts, reports, renders


def collect(junit, binding_path):
    raw = OUT / "raw-junit"
    raw.mkdir(parents=True, exist_ok=True)
    for path in sorted(junit.glob("TEST-*.xml")):
        (raw / path.name).write_bytes(path.read_bytes())
    manifest, source, originals, mapped, expected = inventory()
    binding = json.loads(binding_path.read_text(encoding="utf8"))
    verify_binding(binding)
    if binding["manifest_sha256"] != manifest or binding["source_inputs"] != source:
        fail("source/manifest binding changed")
    counts, reports, renders = native_reports(junit, binding, expected)
    OUT.mkdir(parents=True, exist_ok=True)
    raw = OUT / "raw-junit"
    raw.mkdir(exist_ok=True)
    for report in reports:
        data = (junit / report["name"]).read_bytes()
        if len(data) != report["bytes"] or digest(data) != report["sha256"]:
            fail("raw report changed during retention")
        (raw / report["name"]).write_bytes(data)
    for name, value in renders.items():
        target = OUT / "screens" / (name + ".png")
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(value["data"])
    value = {
        "schema_version": 1, "wp": "WP-302", "binding": binding, "original_families": 50,
        "actual_native_nodes": len(expected), "counts": counts, "raw_junit": reports,
        "families": [{"id": case, **originals[case], **mapped[case], "actual_sdks": [31, 37]} for case in originals],
        "renders": {name: {k: v for k, v in item.items() if k != "data"} for name, item in renders.items()},
        "limits": "Local native Robolectric/SQLite/Compose only; not instrumentation, OS IME/predictive gestures, iOS, physical/OEM, license/signing/release or formal parity approval.",
    }
    (OUT / "source-case-execution.json").write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf8")
    return {"result": "passed-local-native-only", "original_families": 50, "actual_native_nodes": len(expected), "renders": len(renders)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bind-inputs", action="store_true")
    parser.add_argument("--inventory", action="store_true")
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--self-test", action="store_true")
    parser.add_argument("--junit", type=Path, default=APP / "build/test-results/testDebugUnitTest")
    parser.add_argument("--binding", type=Path, default=OUT / "input-binding.json")
    args = parser.parse_args()
    try:
        if args.self_test:
            spec = importlib.util.spec_from_file_location("wp302_reader_tests", Path(__file__).with_name("test_evidence_reader.py"))
            module = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(module)
            suite = unittest.defaultTestLoader.loadTestsFromModule(module)
            if suite.countTestCases() == 0:
                fail("zero reader regressions")
            result = unittest.TextTestRunner(verbosity=1).run(suite)
            if not result.wasSuccessful() or result.skipped:
                fail("failed/skipped reader regressions")
        if args.bind_inputs:
            value = bind_inputs(args.binding)
            result = {"result": "bound-not-executed", "head": value["head"], "nonce": value["nonce"]}
        elif args.inventory:
            _, _, _, _, expected = inventory()
            result = {"result": "static-only-not-execution", "original_families": 50, "declared_native_nodes": len(expected)}
        else:
            result = collect(args.junit, args.binding)
        print(json.dumps(result, sort_keys=True))
        return 0
    except (PortError, OSError, ValueError, KeyError, subprocess.CalledProcessError) as error:
        print("BLOCKED: " + str(error), file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
