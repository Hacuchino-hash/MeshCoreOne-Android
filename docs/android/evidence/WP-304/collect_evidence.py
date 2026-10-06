# AndroidOnly: WP-304 Require actual complete raw JUnit/source-family/native PNG assertions; never grant macro parity.
from __future__ import annotations

import argparse
import base64
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import struct
import sys
import unittest
import xml.etree.ElementTree as ET
import zlib

from source_inventory import EvidenceError, ROOT, PIN, require, inventory, native_inputs, git


def read_xml(path):
    raw = path.read_bytes()
    require(0 < len(raw) <= 64 * 1024 * 1024, "Empty or excessive raw JUnit")
    require(b"<!DOCTYPE" not in raw.upper() and b"<!ENTITY" not in raw.upper(), "Unsafe raw JUnit XML")
    return ET.fromstring(raw)


def png_shape(raw):
    require(raw.startswith(b"\x89PNG\r\n\x1a\n"), "Not a native PNG")
    offset, dimensions, seen_data, ended = 8, None, False, False
    payload = bytearray()
    pixel_bytes = None
    while offset < len(raw):
        require(offset + 12 <= len(raw), "Truncated PNG")
        length = struct.unpack(">I", raw[offset:offset + 4])[0]
        kind = raw[offset + 4:offset + 8]
        end = offset + 12 + length
        require(end <= len(raw), "Truncated PNG chunk")
        data = raw[offset + 8:offset + 8 + length]
        crc = struct.unpack(">I", raw[offset + 8 + length:end])[0]
        require(zlib.crc32(kind + data) & 0xffffffff == crc, "PNG CRC mismatch")
        if kind == b"IHDR":
            require(dimensions is None and offset == 8 and length == 13, "Invalid PNG IHDR")
            dimensions = struct.unpack(">II", data[:8])
            require(all(0 < value <= 4096 for value in dimensions), "Invalid native PNG dimensions")
            require(data[8] == 8 and data[9] in (2, 6) and data[10:] == b"\0\0\0",
                "Unsupported native PNG pixel encoding")
            pixel_bytes = 3 if data[9] == 2 else 4
        elif kind == b"IDAT":
            seen_data = True
            payload.extend(data)
        elif kind == b"IEND":
            require(length == 0 and end == len(raw), "Invalid PNG ending")
            ended = True
        offset = end
    require(dimensions and seen_data and ended, "Incomplete native PNG")
    expected = (dimensions[0] * pixel_bytes + 1) * dimensions[1]
    inflater = zlib.decompressobj()
    expanded = inflater.decompress(payload, expected + 1)
    require(len(expanded) == expected and inflater.eof and not inflater.unused_data
        and not inflater.unconsumed_tail, "Malformed or excessive native PNG pixels")
    stride = dimensions[0] * pixel_bytes + 1
    require(all(expanded[index] in range(5) for index in range(0, expected, stride)), "Invalid PNG row filter")
    return dimensions


def declarations():
    _, cases = inventory()
    paths = sorted((ROOT / "android" / "core" / "ui" / "src" / "test" / "kotlin").rglob("*.kt"))
    require(paths, "Missing shared UI native test sources")
    identities, methods = {}, set()
    for path in paths:
        text = path.read_text(encoding="utf8")
        for identifier, parameters in re.findall(r'@OriginalCase\("([^"]+)"(?:,\s*(\d+))?\)', text):
            require(identifier in cases and identifier not in identities, "Unknown/duplicate original declaration")
            require(int(parameters or 1) == cases[identifier]["scenarios"], "Wrong source parameter expansion")
            identities[identifier] = path.relative_to(ROOT).as_posix()
        package = re.search(r"(?m)^package\s+([\w.]+)", text)
        classes = re.findall(r"(?m)^class\s+(\w+)", text)
        tests = re.findall(r"@Test\s+(?:fun\s+)?(\w+)\s*\(", text)
        if tests:
            require(package and len(classes) == 1, "Ambiguous native test class")
            for method in tests:
                identity = package[1] + "." + classes[0] + "#" + method
                require(identity not in methods, "Duplicate native test declaration")
                methods.add(identity)
    require(set(identities) == set(cases), "Missing owned original family declarations")
    require(methods, "Zero declared native tests")
    return cases, methods


def source_bindings():
    bindings = {}
    directory = ROOT / "android" / "core" / "ui" / "src" / "test" / "kotlin"
    for path in sorted(directory.rglob("*.kt")):
        text = path.read_text(encoding="utf8")
        package = re.search(r"(?m)^package\s+([\w.]+)", text)
        classes = re.findall(r"(?m)^class\s+(\w+)", text)
        if not package or len(classes) != 1:
            continue
        pattern = r'((?:\s*@(OriginalCase|ProducerBindingPending|NativeAdaptation)\([^\n]*\)\s*)+)@Test\s+fun\s+(\w+)\s*\('
        for match in re.finditer(pattern, text):
            annotations, method = match[1], match[3]
            source = re.search(r'@OriginalCase\("([^"]+)"', annotations)
            if source is None:
                continue
            identifier = source[1]
            pending = re.search(r'@ProducerBindingPending\("([^"]+)"\)', annotations)
            adaptation = re.search(r'@NativeAdaptation\("([^"]+)"\)', annotations)
            disposition = "pending-" + pending[1] if pending else "native-adaptation-" + adaptation[1] if adaptation else "native"
            require(identifier not in bindings, "Duplicate method-bound source identity")
            bindings[identifier] = {"method": package[1] + "." + classes[0] + "#" + method, "disposition": disposition}
    cases, _ = declarations()
    require(set(bindings) == set(cases), "Missing method-bound source declarations")
    return bindings


def verify_source_receipt(identifier, disposition, method, kind, expected, observed):
    require(identifier in expected and expected[identifier] == {"method": method, "disposition": disposition},
        "Source receipt is not bound to its actual current method/disposition")
    require(method in observed, "Source receipt method was not executed")
    require((kind == "POLICY_CASE") == disposition.startswith("pending-"), "Pending copy policy was credited as a ported equivalent")


def collect(junit, output=None):
    cases, expected_methods = declarations()
    expected_bindings = source_bindings()
    reports = sorted(junit.glob("TEST-*.xml"))
    require(reports, "No produced native JUnit")
    observed, families, captures, bindings = set(), {}, {}, {}
    counters = {"discovered": 0, "passed": 0, "failed": 0, "errors": 0, "skipped": 0}
    records = []
    for path in reports:
        tree = read_xml(path)
        nodes = list(tree.iter("testcase"))
        require(nodes and tree.tag == "testsuite", "Missing/ambiguous native suite")
        for name, expected in (("tests", len(nodes)), ("failures", sum(node.find("failure") is not None for node in nodes)),
                ("errors", sum(node.find("error") is not None for node in nodes)),
                ("skipped", sum(node.find("skipped") is not None for node in nodes))):
            require(tree.get(name) is not None and int(tree.get(name)) == expected, "JUnit counter/outcome mismatch")
        for node in nodes:
            name = re.sub(r"\[\d+\]$", "", node.get("name", "")).removesuffix("()")
            identity = node.get("classname", "") + "#" + name
            require(identity not in observed and identity in expected_methods, "Duplicate/unexpected executed native test")
            observed.add(identity)
            counters["discovered"] += 1
            status = next((kind for kind in ("failure", "error", "skipped") if node.find(kind) is not None), None)
            counters[{"failure": "failed", "error": "errors", "skipped": "skipped"}.get(status, "passed")] += 1
        text = "\n".join(node.text or "" for node in tree.iter("system-out"))
        for kind, encoded, rows, binding, method in re.findall(
                r"WP304_(CASE|POLICY_CASE)\|([A-Za-z0-9+/=]+)\|(\d+)\|([a-zA-Z0-9-]+)\|([\w.]+#\w+)", text):
            identifier = base64.b64decode(encoded, validate=True).decode("utf8")
            require(identifier in cases and identifier not in families, "Unknown/duplicate executed source family")
            require(int(rows) == cases[identifier]["scenarios"], "Missing source parameter assertions")
            families[identifier] = int(rows)
            require(binding == "native" or binding.startswith(("pending-WP-", "native-adaptation-")), "Unknown source binding disposition")
            verify_source_receipt(identifier, binding, method, kind, expected_bindings, observed)
            bindings[identifier] = binding
        for identifier, width, height, sha, encoded in re.findall(
                r"WP304_PNG\|([a-z0-9-]+)\|(\d+)\|(\d+)\|([0-9a-f]{64})\|([A-Za-z0-9+/=]+)", text):
            raw = base64.b64decode(encoded, validate=True)
            require(identifier not in captures, "Duplicate native PNG identity")
            require(hashlib.sha256(raw).hexdigest() == sha and png_shape(raw) == (int(width), int(height)), "Native PNG binding mismatch")
            captures[identifier] = {"bytes": len(raw), "sha256": sha, "width": int(width), "height": int(height)}
            if output is not None:
                directory = output / "ui"
                directory.mkdir(parents=True, exist_ok=True)
                (directory / (identifier + ".png")).write_bytes(raw)
        raw = path.read_bytes()
        records.append({"name": path.name, "bytes": len(raw), "sha256": hashlib.sha256(raw).hexdigest()})
    require(observed == expected_methods, "Missing executed native tests")
    require(counters["discovered"] > 0 and counters["passed"] == counters["discovered"]
        and not any(counters[key] for key in ("failed", "errors", "skipped")), "Failed/error/skipped/zero native tests")
    require(set(families) == set(cases) and sum(families.values()) == 158, "Missing executed original UI/parameter assertion families")
    required = {"compact-light", "expanded-dark-hc", "resize", "font200-cjk-rtl", "failure-retry", "dialog", "tips", "crop",
        "storage-recovery-font200-rtl", "committed-preference-only"}
    require(required <= captures.keys(), "Missing required meaningful native PNG states")
    record = {
        "schema_version": 1, "work_package": "WP-304",
        "scope": "Native UI/source-presentation assertions only; missing producer bindings still block macro parity.",
        "observed_head_sha": git("rev-parse", "HEAD").decode().strip(), "source_sha": PIN,
        "counts": counters, "accounted_source_families": len(families), "accounted_source_scenarios": sum(families.values()),
        "ported_source_families": sum(not value.startswith("pending-") for value in bindings.values()),
        "ported_source_scenarios": sum(families[key] for key, value in bindings.items() if not value.startswith("pending-")),
        "reports": records, "captures": captures, "inputs": native_inputs(),
        "source_binding_dispositions": bindings,
        "producer_binding_blockers": {key: value for key, value in bindings.items() if value.startswith("pending-")},
        "source_parity_accepted": False, "gate_or_hardware_accepted": False,
    }
    if output is not None:
        output.mkdir(parents=True, exist_ok=True)
        (output / "native-evidence.json").write_text(json.dumps(record, indent=2, sort_keys=True) + "\n", encoding="utf8")
    require(not record["producer_binding_blockers"], "Native tests retained, but original producer bindings remain BLOCKED; copy policy is not parity")
    return record


def self_tests():
    path = Path(__file__).with_name("test_reader.py")
    require(path.is_file(), "Missing owned reader regressions")
    spec = importlib.util.spec_from_file_location("wp304_reader_tests", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    suite = unittest.defaultTestLoader.loadTestsFromModule(module)
    require(suite.countTestCases() > 0, "Zero owned reader regressions")
    result = unittest.TextTestRunner(verbosity=1).run(suite)
    require(result.wasSuccessful() and not result.skipped, "Failed/error/skipped reader regressions")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--static", action="store_true")
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--self-test", action="store_true")
    parser.add_argument("--junit", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    try:
        require(args.static or args.check or args.self_test, "No declared verification mode")
        if args.self_test:
            self_tests()
        if args.static:
            cases, methods = declarations()
            scopes = source_bindings()
            print(json.dumps({"result": "static-accounted-not-executed-not-parity", "accounted_source_families": len(cases),
                "accounted_source_scenarios": 158, "declared_native_methods": len(methods),
                "pending_producer_families": sum(value["disposition"].startswith("pending-") for value in scopes.values())}))
        if args.check:
            require(args.junit is not None, "Missing actual JUnit directory")
            print(json.dumps(collect(args.junit, args.output), sort_keys=True))
        return 0
    except (EvidenceError, OSError, ValueError, KeyError, ET.ParseError) as error:
        print("BLOCKED: " + str(error), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
