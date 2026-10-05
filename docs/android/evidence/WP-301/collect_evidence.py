"""WP-301 real original-family/parameter/input/raw-JUnit/render evidence, never gate authority."""

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
import unittest

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT / "tools" / "android-port"))
from controller.ci_evidence import read_xml, suite_counts
from controller.errors import PortError
from controller.model import load_manifest

PIN = "db14559b39d32322b06477c6ae676112f583db50"
MODULE = ROOT / "android" / "core" / "designsystem"
OUT = MODULE / "build" / "reports" / "wp301"
ANNOTATION = re.compile(r'@OriginalCase\("([^"]+)"(?:,\s*(\d+))?\)\s*@Test\s+fun\s+([A-Za-z0-9_]+)', re.MULTILINE)
EXCLUSIONS = {
    "ThemeServiceErrorTests::notOwned carries the productID and has a non-empty localized description()":
        "Removed entitlement error: all Android themes are unlocked; invalid IDs/storage/resource failures are typed native failures instead.",
    "ThemeServiceOwnershipTests::refunding the bundle reverts the selected theme to default()":
        "Removed StoreKit refund behavior; no billing, bundle ownership, entitlement callback or refund-triggered downgrade.",
    "AppStateThemeWiringTests::AppState exposes a storeState wrapping an idle StoreService()":
        "Removed StoreService/StoreState entitlement graph. Actual process preference and Compose theme consumer seams are tested; complete AppContainer belongs to WP-303.",
}
UNLOCKED_ADAPTATIONS = {
    "ThemeServicePureTests::with no purchases, only the default theme is available and paid themes throw on setCurrent()",
    "ThemeServiceOwnershipTests::a theme owned via the bundle is accessible and selectable()",
    "ThemeServiceOwnershipTests::owning the bundle makes every theme available()",
    "ThemeServiceOwnershipTests::the load() walk does not wipe a persisted theme on an empty ownership read()",
    "ThemeServiceOwnershipTests::refreshFromUserDefaults reverts an unowned restored theme once the store is loaded()",
}


def fail(message):
    raise PortError("WP-301: " + message)


def git(*args):
    return subprocess.check_output(["git", "-C", str(ROOT), *args])


def digest(data):
    return hashlib.sha256(data).hexdigest()


def source_parameter_counts():
    identity = git("show", PIN + ":MC1Tests/Theme/IdentityGamutTests.swift").decode()
    contrast = git("show", PIN + ":MC1Tests/Theme/ThemeContrastTests.swift").decode()
    def names(text):
        match = re.search(r'\(0\.\.<(\d+)\)\.map.*?\}\s*\+\s*\[([^]]+)\]', text, re.S)
        if match is None:
            fail("original identity parameter declaration changed")
        return int(match[1]) + len(re.findall(r'"(?:[^"\\]|\\.)*"', match[2]))
    gamut_count, theme_count = names(identity), names(contrast)
    if (gamut_count, theme_count) != (606, 407):
        fail("original name family changed")
    return {
        "IdentityGamutTests::every identity color clears AA against its surfaces in both appearances and contrasts()": gamut_count * 4,
        "IdentityGamutTests::avatar glyph clears AA against the identity fill in both appearances()": gamut_count * 2,
        "IdentityGamutTests::hue is stable across appearance and contrast for a given name()": 50,
        "ThemeContrastTests::outgoing text clears WCAG AA 4.5:1 against the accent in every appearance()": 34,
        "ThemeContrastTests::incoming hashtag links clear WCAG AA 4.5:1 against the incoming bubble in every appearance()": 34,
        "ThemeContrastTests::identity colors clear AA against their surfaces for every theme and appearance()": 38 * theme_count,
        "ThemeContrastTests::gamut-derived category colors clear AA against the list canvas()": 34 * 3,
        "ThemeContrastTests::channel, repeater, and room avatars resolve to distinct on-anchor hues for every gamut theme()": 9,
        "ThemeContrastTests::avatar glyph clears AA against the identity fill for every theme and appearance()": 38 * 100,
    }


def static_inventory():
    manifest = load_manifest(ROOT)
    if manifest.sha256 != "78a22920beaa5899f9618806b5cd2b27d50399a9b29b4d8dbd79f755717ec746":
        fail("canonical manifest changed")
    owned = [entry for entry in manifest.data["inventory"] if entry["primary_owner"] == "WP-301"]
    inputs = {entry["path"]: entry["blob_sha"] for entry in owned}
    if len(inputs) != 89:
        fail("owned input set changed")
    for path, expected in inputs.items():
        if git("rev-parse", PIN + ":" + path).decode().strip() != expected:
            fail("source blob changed: " + path)
    test_paths = {entry["path"] for entry in owned if entry["kind"] == "test"}
    catalog = json.loads((ROOT / "docs" / "android" / "test-cases.json").read_text(encoding="utf8"))
    originals = {}
    for entry in catalog["entries"]:
        if entry["path"] in test_paths:
            if entry["blob_sha"] != inputs[entry["path"]] or not entry["has_assertions"]:
                fail("invalid original source test inventory")
            for case in entry["cases"]:
                if case["id"] in originals:
                    fail("duplicate original family")
                originals[case["id"]] = {"source_path": entry["path"], "source_blob": entry["blob_sha"], **case}
    if len(originals) != 58:
        fail("original family inventory must contain all 58 cases")
    mapped, production, methods, current_inputs = {}, {}, set(), {}
    for path in sorted((MODULE / "src").rglob("*")):
        if not path.is_file():
            continue
        relative = path.relative_to(ROOT).as_posix()
        raw = path.read_bytes()
        current_inputs[relative] = {
            "working_blob": subprocess.check_output(
                ["git", "-C", str(ROOT), "hash-object", "--path", relative, "--stdin"], input=raw,
            ).decode().strip(),
            "sha256": digest(raw), "size": len(raw),
        }
        if path.suffix != ".kt":
            continue
        content = raw.decode("utf8").replace("\r\n", "\n")
        package = re.search(r"^package ([A-Za-z0-9_.]+)$", content, re.M)
        cls = re.search(r"^class ([A-Za-z0-9_]+)", content, re.M)
        if "src/test/kotlin/" in relative and cls is not None:
            if package is None:
                fail("missing native test package: " + relative)
            prefix = package[1] + "." + cls[1]
            for method in re.findall(r"@Test\s+fun\s+([A-Za-z0-9_]+)", content):
                identity = prefix + "#" + method
                if identity in methods:
                    fail("duplicate native test method")
                methods.add(identity)
            for case, declared, method in ANNOTATION.findall(content):
                if case not in originals or case in mapped:
                    fail("unknown/duplicate original-case annotation: " + case)
                mapped[case] = {
                    "native_test": prefix + "#" + method, "native_path": relative,
                    "parameters": int(declared or "1"),
                    "classification": "approved-all-unlocked-adaptation" if case in UNLOCKED_ADAPTATIONS else
                        "native-process-consumer-equivalent" if case.startswith("AppStateThemeWiringTests::") else "ported",
                }
        if "src/main/kotlin/" in relative:
            for source in re.findall(r"^// PortedFrom: (.+)@" + PIN + "$", content, re.M):
                production.setdefault(source, []).append(relative)
    if set(mapped) | set(EXCLUSIONS) != set(originals) or set(mapped) & set(EXCLUSIONS):
        fail("missing or overlapping original families: " + repr(sorted(set(originals) - set(mapped) - set(EXCLUSIONS))))
    required_production = {entry["path"] for entry in owned if entry["kind"] == "production"}
    if required_production - production.keys():
        fail("missing primary production mappings: " + repr(sorted(required_production - production.keys())))
    expected_parameters = source_parameter_counts()
    for case, mapping in mapped.items():
        if mapping["parameters"] != expected_parameters.get(case, 1):
            fail("narrowed/changed original parameter family: " + case)
    provenance = json.loads((MODULE / "src" / "main" / "assets" / "theme-source-map.json").read_text(encoding="utf8"))
    if provenance["primary_owned_inputs"] != inputs or len(provenance["resource_dispositions"]) != 59:
        fail("incomplete resource/input provenance")
    return originals, mapped, production, methods, current_inputs, expected_parameters


def junit_evidence(directory, expected_methods, mapped, expected_parameters):
    result = suite_counts(directory, 1)
    files = sorted(directory.glob("TEST-*.xml"))
    if not files:
        fail("missing raw JUnit")
    observed, family_counts, renders, reports = set(), {}, {}, []
    for path in files:
        root = read_xml(path)
        reports.append({"name": path.name, "bytes": path.stat().st_size, "sha256": digest(path.read_bytes())})
        for case in root.iter("testcase"):
            name = re.split(r"\[", case.get("name", ""), maxsplit=1)[0].removesuffix("()")
            identity = case.get("classname", "") + "#" + name
            if identity in observed:
                fail("duplicate actual native case")
            observed.add(identity)
        for output in root.iter("system-out"):
            for line in (output.text or "").splitlines():
                if line.startswith("WP301_FAMILY|"):
                    parts = line.split("|")
                    if len(parts) != 4 or parts[1] in family_counts:
                        fail("malformed/duplicate executed family accounting")
                    case, parameters, assertions = parts[1], int(parts[2]), int(parts[3])
                    if case not in expected_parameters or parameters != expected_parameters[case] or assertions < parameters:
                        fail("invalid executed original parameter/assertion count")
                    family_counts[case] = {"parameters": parameters, "assertions": assertions}
                if line.startswith("WP301_RENDER|"):
                    parts = line.split("|")
                    if len(parts) != 6 or not re.fullmatch(r"[a-z0-9-]+", parts[1]) or parts[1] in renders:
                        fail("malformed/duplicate native-render record")
                    name, width, height, checksum = parts[1], int(parts[2]), int(parts[3]), parts[4]
                    data = base64.b64decode(parts[5], validate=True)
                    if not 0 < len(data) < 16 * 1024 * 1024 or not 0 < width <= 4096 or not 0 < height <= 4096:
                        fail("native-render bounds invalid")
                    if data[:8] != b"\x89PNG\r\n\x1a\n" or struct.unpack(">II", data[16:24]) != (width, height) or digest(data) != checksum:
                        fail("actual render bytes/dimensions/hash disagree")
                    renders[name] = {"width": width, "height": height, "sha256": checksum, "bytes": len(data), "data": data}
    if observed != expected_methods:
        fail("missing/extra current native methods: " + repr(sorted(expected_methods - observed)))
    if family_counts.keys() != expected_parameters.keys():
        fail("original executed parameter counters are missing")
    for mapping in mapped.values():
        if mapping["native_test"] not in observed:
            fail("mapped original family did not actually execute")
    expected_renders = {
        "theme-" + theme + "-" + contrast for theme in
        ("default", "ember", "fern", "marine", "olive", "lavender", "sakura", "solarized", "nord", "catppuccin")
        for contrast in ("standard", "hc")
    } | {"font200-rtl-cjk-expanded"}
    if set(renders) != expected_renders:
        fail("required real native preview/render states are missing or unexpected")
    return result, reports, family_counts, renders


def collect(junit):
    originals, mapped, production, methods, current_inputs, expected_parameters = static_inventory()
    counts, reports, counters, renders = junit_evidence(junit, methods, mapped, expected_parameters)
    records = []
    for case, source in originals.items():
        records.append({
            "id": case, "source_path": source["source_path"], "source_blob": source["source_blob"],
            **(mapped[case] if case in mapped else {"classification": "approved-removed-billing", "reason": EXCLUSIONS[case]}),
            "actual_parameter_assertions": counters.get(case),
        })
    OUT.mkdir(parents=True, exist_ok=True)
    for name, render in renders.items():
        target = OUT / "ui" / (name + ".png")
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(render["data"])
    value = {
        "schema_version": 1, "work_package": "WP-301", "source_sha": PIN,
        "observed_head_sha": git("rev-parse", "HEAD").decode().strip(),
        "scope": "Real native unit/Compose/source-family/render execution, not iOS/UIKit/device/hardware/formal gate or whole-app graph acceptance.",
        "original_families": 58, "implemented_families": len(mapped), "approved_removed_families": len(EXCLUSIONS),
        "cases": records, "production_mappings": production, "current_inputs": current_inputs,
        "unit_counts": counts, "raw_junit": reports,
        "renders": {name: {key: value for key, value in render.items() if key != "data"} for name, render in renders.items()},
    }
    (OUT / "source-case-execution.json").write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf8")
    return {"result": "passed", "original_families": 58, "mapped_families": len(mapped),
            "approved_removed_families": len(EXCLUSIONS), "counts": counts, "native_renders": len(renders)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--inventory", action="store_true")
    parser.add_argument("--self-test", action="store_true")
    parser.add_argument("--junit", type=Path, default=MODULE / "build" / "test-results" / "testDebugUnitTest")
    args = parser.parse_args()
    try:
        if args.self_test:
            path = Path(__file__).with_name("test_evidence_reader.py")
            if not path.is_file():
                fail("missing evidence-reader regression suite")
            spec = importlib.util.spec_from_file_location("wp301_evidence_tests", path)
            module = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(module)
            suite = unittest.defaultTestLoader.loadTestsFromModule(module)
            if suite.countTestCases() == 0:
                fail("zero evidence-reader tests")
            tested = unittest.TextTestRunner(verbosity=1).run(suite)
            if not tested.wasSuccessful() or tested.skipped:
                fail("evidence-reader regressions failed/skipped")
        if args.inventory:
            originals, mapped, production, methods, inputs, parameters = static_inventory()
            result = {"result": "valid-static-inventory-not-execution", "original_families": len(originals),
                      "mapped_families": len(mapped), "removed_families": len(EXCLUSIONS), "native_methods": len(methods),
                      "source_parameter_families": parameters, "primary_production_inputs": len(production)}
        else:
            result = collect(args.junit)
        print(json.dumps(result, sort_keys=True))
        return 0
    except (PortError, OSError, ValueError, KeyError, subprocess.CalledProcessError) as error:
        print("BLOCKED: " + str(error), file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
