# AndroidOnly: WP-304 Require actual complete raw JUnit/source-family/native PNG assertions; never grant macro parity.
from __future__ import annotations

import argparse
import base64
import hashlib
import json
from pathlib import Path
import re
import struct
import sys
import xml.etree.ElementTree as ET
import zlib

from source_inventory import EvidenceError, ROOT, PIN, PROJECTION_SUITES, require, inventory, native_inputs, git


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


def kotlin_code(text):
    require(len(text) <= 2 * 1024 * 1024, "Excessive native test source")
    masked = list(text)

    def comment_end(position):
        if text.startswith("//", position):
            end = text.find("\n", position)
            return len(text) if end < 0 else end
        depth, cursor = 1, position + 2
        while cursor < len(text) and depth:
            if text.startswith("/*", cursor):
                depth += 1
                cursor += 2
            elif text.startswith("*/", cursor):
                depth -= 1
                cursor += 2
            else:
                cursor += 1
        require(depth == 0, "Unterminated native test comment")
        return cursor

    def interpolation_end(position, nesting):
        require(nesting <= 64, "Excessive native test interpolation")
        depth, cursor = 1, position
        while cursor < len(text):
            if text.startswith(("//", "/*"), cursor):
                cursor = comment_end(cursor)
            elif text[cursor] in "\"'`":
                cursor = literal_end(cursor, nesting + 1)
            elif text[cursor] == "{":
                depth += 1
                cursor += 1
            elif text[cursor] == "}":
                depth -= 1
                cursor += 1
                if depth == 0:
                    return cursor
            else:
                cursor += 1
        require(False, "Unterminated native test interpolation")

    def literal_end(position, nesting=0):
        delimiter = '"""' if text.startswith('"""', position) else text[position]
        cursor = position + len(delimiter)
        while cursor < len(text):
            if text.startswith(delimiter, cursor):
                return cursor + len(delimiter)
            if delimiter in {'"', "'"} and text[cursor] == "\\":
                cursor += 2
            elif delimiter in {'"', '"""'} and text.startswith("${", cursor):
                cursor = interpolation_end(cursor + 2, nesting + 1)
            else:
                require(delimiter == '"""' or text[cursor] != "\n", "Unterminated native test literal")
                cursor += 1
        require(False, "Unterminated native test literal")

    position = 0
    while position < len(text):
        if text.startswith(("//", "/*"), position):
            end = comment_end(position)
        elif text[position] in "\"'`":
            end = literal_end(position)
        else:
            position += 1
            continue
        for index in range(position, end):
            if masked[index] != "\n":
                masked[index] = " "
        position = end
    return "".join(masked)


def native_test_methods(text):
    code = kotlin_code(text)
    tests = list(re.finditer(r"@Test\s+fun\s+(\w+)\s*\(\s*\)", code))
    require(len(re.findall(r"@(?:[\w.]+\.)?Test\b", code)) == len(tests),
        "Unsupported/orphan native JUnit annotation")
    package = re.search(r"(?m)^package\s+([\w.]+)", code)
    classes = list(re.finditer(r"(?m)^class\s+(\w+)\b", code))
    if tests:
        require(package and len(classes) == 1, "Ambiguous native test class")
        opening = code.find("{", classes[0].end())
        require(opening >= 0, "Missing native test class body")
    positions = {match.start(): match for match in tests}
    stack, methods = [], set()
    closing = {")": "(", "]": "[", "}": "{"}
    for index, character in enumerate(code):
        if index in positions:
            require(stack == [("{", opening)], "Native @Test must be a direct class-level JUnit method: "
                + positions[index][1] + " at line " + str(text.count("\n", 0, index) + 1))
            method = package[1] + "." + classes[0][1] + "#" + positions[index][1]
            require(method not in methods, "Duplicate native test declaration")
            methods.add(method)
        if character in "([{":
            stack.append((character, index))
        elif character in ")]}":
            require(stack and stack[-1][0] == closing[character], "Unbalanced native test delimiter")
            stack.pop()
    require(not stack, "Unclosed native test delimiter")
    return methods


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
        declared = native_test_methods(text)
        require(not methods.intersection(declared), "Duplicate native test declaration")
        methods.update(declared)
    require(set(identities) == set(cases), "Missing owned original family declarations")
    require(methods, "Zero declared native tests")
    for owner, suite in PROJECTION_SUITES.items():
        projection_declaration_methods(suite)
    return cases, methods


def projection_declaration_methods(suite):
    relative = "android/core/" + suite["module"] + "/src/test/kotlin/" + suite["classname"].replace(".", "/") + ".kt"
    text = ROOT.joinpath(*relative.split("/")).read_text(encoding="utf8")
    kind = suite["declaration_kind"]
    if kind == "runtime-native-case":
        names = ["WP-207::" + name for name in re.findall(r'nativeCase\("([^"]+)"', text)]
    elif kind == "junit-method":
        names = [method.split("#", 1)[1] for method in native_test_methods(text)]
    elif kind == "contacts-native-case":
        names = ["WP-209::" + name for name in re.findall(r'contactsNative\("([^"]+)"', text)]
    elif kind == "remote-native-case":
        names = ["WP-210::" + name for name in re.findall(r'remoteCoreNative\("([^"]+)"', text) if "$" not in name]
        for case, retryable in re.findall(r'remoteNative\("(\w+)",[^\n]+,\s*(true|false)\)', text):
            names.append(f"WP-210::RemoteNodeError.{case} projects to RemoteNodeFault.{case} "
                f"with message, cause and retryability ({retryable}) unchanged")
        for helper, producer, payload in (("roomNative", "RoomServerError", "RoomServerFault"),
                ("binaryNative", "BinaryProtocolError", "BinaryProtocolFault")):
            for case in re.findall(helper + r'\("(\w+)",', text):
                names.append(f"WP-210::{producer}.{case} projects to {payload}.{case} with message and cause unchanged")
    else:
        require(False, "Unknown producer projection declaration kind")
    require(len(names) == len(set(names)), "Duplicate producer projection declaration")
    methods = set(names)
    if "methods" in suite:
        require(methods == suite["methods"], "Actual producer projection source methods drifted")
    else:
        require(len(methods) == suite["case_count"], "Missing landed producer per-case projection assertions")
    return methods


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


def verify_projection_suite(path, classname, methods):
    require(path.is_file() and not path.is_symlink()
        and not any(parent.is_symlink() for parent in path.parents), "Missing/linked actual producer projection report")
    tree = read_xml(path)
    nodes = list(tree.iter("testcase"))
    require(tree.tag == "testsuite" and tree.get("name") == classname and nodes,
        "Missing/ambiguous producer projection suite")
    for name, expected in (("tests", len(nodes)), ("failures", sum(node.find("failure") is not None for node in nodes)),
            ("errors", sum(node.find("error") is not None for node in nodes)),
            ("skipped", sum(node.find("skipped") is not None for node in nodes))):
        require(tree.get(name) is not None and int(tree.get(name)) == expected, "Producer JUnit counter/outcome mismatch")
    observed = set()
    for node in nodes:
        method = node.get("name", "").removesuffix("()")
        require(node.get("classname") == classname and method in methods and method not in observed,
            "Duplicate/unknown producer projection method")
        observed.add(method)
        require(all(node.find(status) is None for status in ("failure", "error", "skipped")),
            "Failed/error/skipped producer projection assertion")
    require(observed == methods and len(observed) > 0, "Missing/zero producer projection assertions")
    raw = path.read_bytes()
    return {"name": path.name, "bytes": len(raw), "sha256": hashlib.sha256(raw).hexdigest(),
        "classname": classname, "methods": sorted(observed), "passed": len(observed)}


def producer_projection_evidence():
    return {owner: verify_projection_suite(
        ROOT.joinpath(*suite["directory"].split("/")) / ("TEST-" + suite["classname"] + ".xml"),
        suite["classname"], projection_declaration_methods(suite),
    ) for owner, suite in PROJECTION_SUITES.items()}


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
    projections = producer_projection_evidence()
    record = {
        "schema_version": 1, "work_package": "WP-304",
        "scope": "Native UI/source-presentation assertions only; missing producer bindings still block macro parity.",
        "observed_head_sha": git("rev-parse", "HEAD").decode().strip(), "source_sha": PIN,
        "counts": counters, "accounted_source_families": len(families), "accounted_source_scenarios": sum(families.values()),
        "ported_source_families": sum(not value.startswith("pending-") for value in bindings.values()),
        "ported_source_scenarios": sum(families[key] for key, value in bindings.items() if not value.startswith("pending-")),
        "reports": records, "captures": captures, "inputs": native_inputs(),
        "source_binding_dispositions": bindings,
        "actual_producer_projection_suites": projections,
        "producer_binding_blockers": {key: value for key, value in bindings.items() if value.startswith("pending-")},
        "source_parity_accepted": False, "gate_or_hardware_accepted": False,
    }
    if output is not None:
        output.mkdir(parents=True, exist_ok=True)
        (output / "native-evidence.json").write_text(json.dumps(record, indent=2, sort_keys=True) + "\n", encoding="utf8")
    require(not record["producer_binding_blockers"], "Native tests retained, but original producer bindings remain BLOCKED; copy policy is not parity")
    return record


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--static", action="store_true")
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--junit", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    try:
        require(args.static or args.check, "No declared verification mode")
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
