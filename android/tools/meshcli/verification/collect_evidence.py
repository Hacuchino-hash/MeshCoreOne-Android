"""AndroidOnly: WP-109 Complete original-family/JUnit replay; candidate data never constitutes protected acceptance."""

import argparse
from collections import Counter, defaultdict
import hashlib
import json
from pathlib import Path
import re
import shutil
import sys
import unittest

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT / "tools" / "android-port"))

from controller.ci_evidence import counts as require_counts, read_xml, suite_counts
from controller.errors import PortError
from controller.gates import Binding, policy_revision
from controller.model import git, load_manifest, tree
from controller.module_junit import bounded_directory, linked, safe_reports
from controller.runtime_inputs import required_inputs as required_runtime_inputs
from controller.schema import decode_json, fields, load_json

SOURCE = "db14559b39d32322b06477c6ae676112f583db50"
MANIFEST = "78a22920beaa5899f9618806b5cd2b27d50399a9b29b4d8dbd79f755717ec746"
POLICY = "56bdc53548bc86d631245795dfa38b4fc86048e0e7cbe1c7d5695879b035b42a"
EVIDENCE = ROOT / "docs" / "android" / "evidence" / "WP-109"
NATIVE_PREFIX = "com.meshcoreone.android.core.protocol."
SOURCE_PREFIX = "MeshCore/Tests/"
BASELINE = EVIDENCE / "baseline-native.json"
BASELINE_SHA256 = "b090ca122bbd28fd72a6e9654bd8590e4b5db3ef497b9530135e728d8bc7bcd4"
MODULES = {
    "protocol": ("android/core/protocol/build/test-results/test", 4711),
    "meshcli": ("android/tools/meshcli/build/test-results/test", 87),
}
TEXT = {
    ".kt", ".kts", ".java", ".py", ".json", ".xml", ".md", ".txt", ".tsv",
    ".properties", ".lockfile", ".swift", ".toml", ".yml", ".yaml", ".ps1", ".bat", ".sh",
}
TEXT_NAMES = {"LICENSE", "NOTICE", "gradlew"}
EVIDENCE_SCOPE = "actual code/JUnit/source families; not independent acceptance or hardware"
LOCAL_SCOPE = "local immutable candidate execution; no hosted run authority"
LOCAL_EXECUTOR_SCOPE = "explicit local CI executor forwarding; no hosted run authority"
CLI_PREFIX = "com.meshcoreone.android.tools.meshcli."
CLI_FACTORIES = {
    "CliArgumentsTest": {"invalidArguments": 21, "forbiddenOperations": 14},
    "CliTcpTest": {"completeReadQueries": 6, "terminalFailures": 9, "contactCompleteness": 4, "bidiControlCharacters": 12},
}
CLI_SINGLES = """CliArgumentsTest|help performs no transport construction
CliArgumentsTest|unknown user text and escape sequences never enter generic errors
CliArgumentsTest|usage rejection with an unavailable stderr still returns output failure without a socket
CliArgumentsTest|IPv4 IPv6 and default options are canonical and DNS free
CliArgumentsTest|deadline and index boundary values are admitted without unsigned wrap
CliTcpTest|ACK advertisements and message content coalesced with read response are not fake success or logs
CliTcpTest|coalesced device rejection cannot lose the race to a valid singleton response
CliTcpTest|channel pipeline uses capability and real acknowledged TCP writes with gaps and ignored indexes
CliTcpTest|missing channel is partial at injected idle deadline without serial fake reconciliation
CliTcpTest|overall deadline covers handshake and closes real socket before any next query
CliTcpTest|caller cancellation reports no result and closes even after owning job cancellation
CliTcpTest|failed teardown never publishes an otherwise valid successful query
CliTcpTest|output IOException is explicit and does not leak or reopen the transport
CliTcpTest|capability rejection does not send any out of range channel command
CliTcpTest|device strings stay UTF8 and terminal control characters are escaped
CliMainProcessTest|real deployed main prints help and exits zero without a peer
CliMainProcessTest|real deployed main rejects unknown text with usage exit and sanitized stderr
CliMainProcessTest|real deployed main reads actual framed TCP and returns actual battery with zero exit
CliMainProcessTest|real deployed main preserves unsupported nonzero exit after genuine device rejection
CliMainProcessTest|real deployed main detects a closed stdout pipe after successful socket cleanup
CliMainProcessTest|real deployed main detects a closed stderr pipe without an exception stack or false success"""
TCP_CASES = (
    "unanswered arbitrary TCP matcher quarantines typed successor and fresh physical connection clears it",
    "retained TCP link blocks both logical reuse and another session until exact old owner closes",
    "cancelled owning job still awaits actual TCP close and releases a fresh session claim",
)

ALIASES = """Ed25519ToX25519Tests|Public key conversion round-trip with CryptoKit|crypto.Ed25519ToX25519Test|Public key conversion round-trip with independent RFC keys
EventDispatcherDropTests|droppedEventCount increments when a slow consumer overflows the buffer|event.EventDispatcherTest|Slow consumer preserves latest 100 with exact counted and observed oldest drops
EventFilterAnyAcknowledgementTests|matches .acknowledgement regardless of code|event.EventFilterTest|Any acknowledgement matches regardless of code
EventFilterAnyAcknowledgementTests|does not match non-acknowledgement events|event.EventFilterTest|Any acknowledgement rejects all unrelated cases
EventFilterFactoryTests|rxLogData matches .rxLogData and rejects unrelated events|event.EventFilterTest|RF log filter matches only RF logs
EventFilterFactoryTests|anyAdvertisement matches .advertisement and rejects unrelated events|event.EventFilterTest|Any advertisement matches all senders and rejects unrelated events
EventFilterFactoryTests|anyContactMessage matches .contactMessageReceived and rejects unrelated events|event.EventFilterTest|Any contact message matches only direct receipts
EventFilterFactoryTests|anyChannelMessage matches .channelMessageReceived and rejects unrelated events|event.EventFilterTest|Any channel message matches only channel text receipts
EventFilterFactoryTests|anyLoginSuccess matches .loginSuccess and rejects unrelated events|event.EventFilterTest|Any login success matches only successful login
EventFilterFactoryTests|anyLoginFailed matches .loginFailed and rejects unrelated events|event.EventFilterTest|Any login failure matches absent and present prefix only
MeshEventErrorCodeTests|All six firmware sub-codes map to the matching ErrorCode|event.EventPayloadTest|All six firmware subcodes retain original typed and raw error accessors
MeshEventErrorCodeTests|Unknown or out-of-range bytes yield a nil typed code but keep the raw value|event.EventPayloadTest|Every unknown byte has null typed error but unchanged raw value
MeshEventErrorCodeTests|A missing sub-code byte yields a nil typed code|event.EventPayloadTest|A missing subcode retains its attribute entry and null typed code
MeshEventErrorCodeTests|Non-error events have no typed error code|event.EventPayloadTest|Nonerror cases never gain a device error code
MeshEventErrorCodeTests|deviceErrorCode is nil for non-deviceError MeshCoreError cases|event.EventPayloadTest|Nonerror cases never gain a device error code
NewCommandsTests|setPathHashMode mode 0 (1-byte hashes)|command.NewCommandsTest|setPathHashMode mode 0 1 and 2 parameter family
V115CommandsTests|sendChannelData flood (default pathLength) format|command.V115CommandsTest|sendChannelData flood default pathLength format
V115CommandsTests|sendChannelData flood ignores pathBytes when pathLength == 0xFF|command.V115CommandsTest|sendChannelData flood ignores pathBytes when pathLength is FF
V115CommandsTests|sendChannelData direct-path format (1-byte hashes)|command.V115CommandsTest|sendChannelData direct-path format 1-byte hashes
V115CommandsTests|sendChannelData direct-path format (2-byte hashes)|command.V115CommandsTest|sendChannelData direct-path format 2-byte hashes
V115CommandsTests|setDefaultFloodScope with .disabled scope clears|command.V115CommandsTest|setDefaultFloodScope with disabled scope clears
WiFiTransportTests|Cancelling connect() does not leave it parked|transport.WiFiTransportIoTest|Cancelling pending connect does not leave it parked or leak its socket
LPPPythonReferenceTests|Temperature 25.5 matches Python|lpp.LPPPythonReferenceTest|Temperature 25_5 matches Python
LPPPythonReferenceTests|Analog input 3.3 matches Python|lpp.LPPPythonReferenceTest|Analog input 3_3 matches Python
RoundTripTests|setRadio rounds frequency to the nearest kHz|command.ContactAndRadioCommandsTest|setRadio rounds frequency to nearest kHz
DecodePathLenTests|mode 0 with max hops (63)|primitives.PathEncodingTest|mode 0 with max hops 63
DecodePathLenTests|mode 1 with max hops (63)|primitives.PathEncodingTest|mode 1 with max hops 63
DecodePathLenTests|mode 2 with max hops (63)|primitives.PathEncodingTest|mode 2 with max hops 63
DecodePathLenTests|0xFF flood sentinel returns nil (mode 3)|primitives.PathEncodingTest|0xFF flood sentinel returns nil mode 3
DecodePathLenTests|encode/decode round-trip|primitives.PathEncodingTest|encode decode round-trip"""


def sha(data):
    return hashlib.sha256(data).hexdigest()


def blob(data):
    return hashlib.sha1(b"blob " + str(len(data)).encode("ascii") + b"\0" + data).hexdigest()


def checkout_bytes(path, data):
    return data.replace(b"\r\n", b"\n") if path.suffix in TEXT or path.name in TEXT_NAMES else data


def baseline_digest(data):
    # The independent retained capture uses CRLF; Git/Linux may check out the same text as LF.
    return sha(data.replace(b"\r\n", b"\n").replace(b"\n", b"\r\n"))


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=True) + "\n", encoding="utf-8", newline="\n")


def junit(directory, boundary, minimum):
    reports = safe_reports(directory, boundary)
    if len(reports) > 256 or sum(p.stat().st_size for p in reports) > 32 * 1024 * 1024:
        raise PortError("Oversized complete JUnit set")
    counts = suite_counts(directory, minimum)
    cases, raw = [], {}
    for path in reports:
        data = path.read_bytes()
        declarations = data.replace(b"\0", b"").upper()
        if b"<!DOCTYPE" in declarations or b"<!ENTITY" in declarations:
            raise PortError("Unsafe declarations in mandatory raw XML")
        doc = read_xml(path)
        raw[path.name] = data
        cases.extend({"class": c.attrib["classname"], "name": c.attrib["name"], "outcome": "passed"}
                     for c in doc.findall("testcase"))
    if sum(len(read_xml(path).findall("testcase")) for path in reports) != counts["discovered"]:
        raise PortError("JUnit changed during complete collection")
    if any(path.read_bytes() != raw[path.name] for path in reports):
        raise PortError("Raw JUnit changed during collection")
    return cases, raw, counts


def aliases():
    result = {}
    for row in ALIASES.splitlines():
        scope, method, native_class, name = row.split("|")
        key = (scope, method)
        if key in result:
            raise PortError("Duplicate explicit source alias")
        result[key] = (NATIVE_PREFIX + native_class, name + "()")
    return result


def required_cli_cases():
    rows = [line.split("|") for line in CLI_SINGLES.splitlines()]
    result = {(CLI_PREFIX + scope, method + "()") for scope, method in rows}
    for scope, factories in CLI_FACTORIES.items():
        result.update((CLI_PREFIX + scope, f"{method}()[{index}]")
                      for method, total in factories.items() for index in range(1, total + 1))
    if len(result) != 87:
        raise PortError("Malformed declared CLI assertion/family inventory")
    return result


def checked_checkout(repo, head, required):
    entries = tree(repo, head)
    missing = set(required) - entries.keys()
    if missing:
        raise PortError("Required inputs are not committed: " + repr(sorted(missing)[:3]))
    raw = {}
    total_size = 0
    for path in sorted(required):
        local = repo.joinpath(*path.split("/"))
        bounded_directory(local.parent, repo)
        if linked(local) or not local.is_file() or local.stat().st_size > 16 * 1024 * 1024:
            raise PortError("Missing/unsafe/oversized immutable input: " + path)
        total_size += local.stat().st_size
        if total_size > 64 * 1024 * 1024:
            raise PortError("Oversized complete immutable input set")
        data = local.read_bytes()
        canonical = checkout_bytes(local, data)
        if blob(data) == entries[path]:
            committed = data
        elif blob(canonical) == entries[path]:
            committed = canonical
        else:
            committed = git(repo, "cat-file", "blob", entries[path])
            expected = checkout_bytes(local, committed)
            if canonical != expected:
                raise PortError("Executed checkout differs from immutable input: " + path)
        raw[path] = committed
    return {path: entries[path] for path in sorted(required)}, raw


def native_source_scopes(repo):
    result = defaultdict(set)
    for path in sorted((repo / "android" / "core" / "protocol" / "src" / "test").rglob("*.kt")):
        text = path.read_text(encoding="utf-8")
        package = re.search(r"^package ([\w.]+)", text, re.MULTILINE)
        native_class = re.search(r"^class (\w+)", text, re.MULTILINE)
        if package and native_class:
            for source in re.findall(r"PortedFrom: ([^@\s]+)@" + SOURCE, text):
                result[source].add(package[1] + "." + native_class[1])
    return result


def original_accounting(repo, catalog, details, manifest, native):
    fields(catalog, {"schema_version", "source_sha", "entries"}, label="frozen original-case catalog")
    if type(catalog["schema_version"]) is not int or catalog["schema_version"] != 1 or catalog["source_sha"] != SOURCE:
        raise PortError("Frozen case catalog pin/version mismatch")
    if details["source_sha"] != SOURCE or details["manifest_sha256"] != MANIFEST:
        raise PortError("Original assertion/parameter detail provenance mismatch")
    declarations = {(f["path"], c["id"]): (f, c) for f in details["files"] for c in f["cases"]}
    owners = {i["path"]: i for i in manifest.data["inventory"]}
    scopes = native_source_scopes(repo)
    explicit = aliases()
    identities = {(c["class"], c["name"]) for c in native}
    by_name = defaultdict(list)
    for identity in identities:
        by_name[identity[1]].append(identity)
    originals, qualified = [], set()
    for entry in catalog["entries"]:
        if not entry["path"].startswith(SOURCE_PREFIX):
            continue
        fields(entry, {"path", "blob_sha", "has_assertions", "cases"}, label="original source entry")
        source = owners[entry["path"]]
        if entry["blob_sha"] != source["blob_sha"]:
            raise PortError("Original source blob changed")
        for case in entry["cases"]:
            fields(case, {"id", "parameter_family"}, label="original declaration")
            key = (entry["path"], case["id"])
            if key in qualified or key not in declarations:
                raise PortError("Duplicate or missing original declaration")
            qualified.add(key)
            file_detail, detail = declarations[key]
            if file_detail["blob_sha"] != entry["blob_sha"] or detail["parameter_family"] != case["parameter_family"]:
                raise PortError("Original family/detail/source identity mismatch")
            if not detail["direct_assertions"] and not detail["helper_assertions"]:
                raise PortError("Original assertion-free case needs an explicit reviewed disposition")
            hits = by_name[case["id"]]
            adaptation = None
            if not hits:
                hits = [i for i in by_name[detail["method"] + "()"] if i[0] in scopes[entry["path"]]]
            if not hits and (detail["scope"], detail["method"]) in explicit:
                hits = [explicit[(detail["scope"], detail["method"])]]
                adaptation = "explicit-reviewed-native-name-or-platform-alias"
            if not hits and detail["scope"] == "WiFiTransportTests" and detail["method"].startswith("connect() is idempotent"):
                hits = [(NATIVE_PREFIX + "transport.WiFiTransportTest",
                         "connect is idempotent second call does not create new TCP connection()")]
                adaptation = "explicit-native-name-alias"
            if len(hits) != 1 or hits[0] not in identities:
                raise PortError("Missing/ambiguous actual original-family execution: " + case["id"])
            inputs = detail["inputs"]
            if case["parameter_family"] != "single" and (
                not isinstance(inputs, dict) or type(inputs["declared_count"]) is not int or inputs["declared_count"] <= 0
            ):
                raise PortError("Missing complete declared parameter rows")
            source_bytes = git(repo, "cat-file", "blob", entry["blob_sha"])
            lines = source_bytes.decode("utf-8", errors="strict").splitlines(keepends=True)
            body = "".join(lines[detail["line"] - 1:detail["end_line"]]).encode("utf-8")
            originals.append({
                "source_path": entry["path"], "source_blob_sha": entry["blob_sha"],
                "case_id": case["id"], "parameter_family": case["parameter_family"], "declared_inputs": inputs,
                "assertion_sites": detail["direct_assertions"], "helper_assertions": detail["helper_assertions"],
                "source_declaration_sha256": sha(body), "primary_owner_unchanged": source["primary_owner"],
                "native_test": {"class": hits[0][0], "name": hits[0][1], "outcome": "passed"},
                "disposition": "existing-executed-component", "native_adaptation": adaptation,
                "new_original_case": False,
            })
    expected = {i["path"] for i in manifest.data["inventory"] if i["path"].startswith(SOURCE_PREFIX)
                and i["kind"] in ("test", "support")}
    actual = {e["path"] for e in catalog["entries"] if e["path"].startswith(SOURCE_PREFIX)}
    if actual != expected or len(originals) != 486:
        raise PortError("Incomplete original MeshCore scope/declarations")
    return originals


def invocation_record(path, head, host):
    if path is None:
        return {"scope": LOCAL_SCOPE, "head_sha": head, "host": host}
    return validate_invocation(load_json(path), head, host)


def validate_invocation(record, head, host):
    fields(record, {"schema_version", "stage", "identity", "host"}, label="explicit CI invocation forwarding")
    if (type(record["schema_version"]) is not int or record["schema_version"] != 1
            or record["stage"] not in ("verify", "protocol") or record["host"] != host):
        raise PortError("Invalid invocation version/stage/host")
    identity = record["identity"]
    if identity is None:
        return {"scope": LOCAL_EXECUTOR_SCOPE, "head_sha": head, "host": host}
    fields(identity, {"binding", "run_id", "run_attempt"}, label="actual hosted invocation identity")
    bound = Binding.parse(identity["binding"])
    if bound.head_sha != head or bound.source_sha != SOURCE or bound.manifest_sha256 != MANIFEST or bound.policy_revision != POLICY:
        raise PortError("Stale/mismatched actual CI invocation")
    if bound.repository != "cbattlegear/MeshCoreOne-Android" or bound.work_package != "WP-003":
        raise PortError("Unexpected actual executor binding")
    if any(type(identity[key]) is not int or identity[key] <= 0 for key in ("run_id", "run_attempt")):
        raise PortError("Missing actual run/attempt identity")
    return record


def required_inputs(entries, manifest):
    roots = ("android/core/protocol/", "android/core/model/", "android/core/contracts/", "android/tools/meshcli/")
    required = {p for p in entries if p.startswith(roots) and (
        "/src/" in p or p.endswith((".kts", ".lockfile", ".py", ".md", ".json"))
    )} | {
        "docs/android/test-cases.json", "docs/android/port-manifest.json", "docs/android/automation-policy.json",
        "docs/android/evidence/WP-004/inventory-details.json", "docs/android/evidence/WP-109/baseline-native.json",
        "android/core/testing/fixtures/protocol-vectors.tsv", "android/core/testing/fixtures/protocol-vectors.json",
        "LICENSE",
        *("android/app/src/main/assets/licenses/" + name
          for name in ("GPL-3.0.txt", "MeshCore-MIT.txt", "BouncyCastle-MIT.txt", "Apache-2.0.txt")),
    }
    required.update(required_runtime_inputs())
    required.update(p for p in entries if p.startswith("android/build-logic/") and (
        "/src/" in p or Path(p).suffix in {".kts", ".lockfile", ".properties", ".toml"}
    ))
    required.update(p for p in entries if p.startswith("android/gradle/dependency-locks/") and p.endswith(".lockfile"))
    required.update(i["path"] for i in manifest.inputs("WP-109"))
    required.update(p for p in entries if p.startswith("docs/android/evidence/WP-102/") and p.endswith((".tsv", ".json")))
    return required


def collect(repo, output, invocation=None):
    output = output.resolve()
    if output == repo.resolve() or repo.resolve().is_relative_to(output):
        raise PortError("Evidence destination cannot overwrite repository inputs")
    if output.is_relative_to(repo.resolve()) and not output.is_relative_to(
        (repo / "android" / "tools" / "meshcli" / "build").resolve()
    ):
        raise PortError("In-tree evidence belongs only in the owned ignored build directory")
    if output.exists():
        raise PortError("Evidence destination must be new; stale raw results cannot be reused")
    head = git(repo, "rev-parse", "HEAD").decode().strip()
    manifest = load_manifest(repo)
    if manifest.sha256 != MANIFEST or policy_revision(manifest, load_json(repo / "docs/android/automation-policy.json")) != POLICY:
        raise PortError("Frozen manifest/policy changed")
    host = "windows" if sys.platform == "win32" else "linux"
    run = invocation_record(invocation, head, host)
    entries = tree(repo, head)
    roots = ("android/core/protocol/", "android/core/model/", "android/core/contracts/", "android/tools/meshcli/")
    required = required_inputs(entries, manifest)
    if len(required) > 2048:
        raise PortError("Oversized complete immutable input set")
    native_roots = [r + "src" for r in roots]
    if any(git(repo, "ls-files", "--others", "-z", "--", *native_roots).split(b"\0")):
        raise PortError("Uncommitted compiled native source inputs")
    inputs, raw_inputs = checked_checkout(repo, head, required)
    native, reports, counts = {}, {}, {}
    for module, (relative, minimum) in MODULES.items():
        native[module], reports[module], counts[module] = junit(repo.joinpath(*relative.split("/")), repo, minimum)
    validate_native_floor(repo, native)
    originals = original_accounting(repo, load_json(repo / "docs/android/test-cases.json"),
                                    load_json(repo / "docs/android/evidence/WP-004/inventory-details.json"),
                                    manifest, native["protocol"])
    output.mkdir(parents=True)
    raw_records = []
    for module, files in reports.items():
        destination = output / "junit" / module
        destination.mkdir(parents=True)
        for name, data in files.items():
            file = destination / name
            file.write_bytes(data)
            if file.read_bytes() != data:
                raise PortError("Complete raw JUnit copy changed")
            raw_records.append({"module": module, "path": file.relative_to(output).as_posix(),
                                "size": len(data), "sha256": sha(data)})
        junit(destination, output, MODULES[module][1])
    blobs = output / "input-blobs"
    blobs.mkdir()
    for path, data in raw_inputs.items():
        file = blobs / inputs[path]
        if file.exists() and file.read_bytes() != data:
            raise PortError("Immutable blob collision")
        file.write_bytes(data)
    result = {
        "schema_version": 1, "scope": EVIDENCE_SCOPE,
        "work_package": "WP-109", "head_sha": head, "source_sha": SOURCE, "manifest_sha256": MANIFEST,
        "policy_revision": POLICY, "invocation": run, "inputs": inputs, "counts": counts,
        "native_cases": native, "raw_reports": raw_records, "original_cases": originals,
        "original_declarations": len(originals), "unique_original_native_bindings": len(
            {(o["native_test"]["class"], o["native_test"]["name"]) for o in originals}),
        "new_original_cases": 0, "reviewed_exclusions": 0, "physical_radio_verified": False,
    }
    write_json(output / "evidence.json", result)
    validate_bundle(output, repo, head)
    return result


def validate_bundle(output, repo, head):
    value = load_json(output / "evidence.json")
    required = {
        "schema_version", "scope", "work_package", "head_sha", "source_sha", "manifest_sha256", "policy_revision",
        "invocation", "inputs", "counts", "native_cases", "raw_reports", "original_cases", "original_declarations",
        "unique_original_native_bindings", "new_original_cases", "reviewed_exclusions", "physical_radio_verified",
    }
    fields(value, required, label="complete WP-109 evidence")
    if (type(value["schema_version"]) is not int or value["schema_version"] != 1
            or value["scope"] != EVIDENCE_SCOPE or value["work_package"] != "WP-109" or value["head_sha"] != head):
        raise PortError("Stale/malformed CLI evidence binding")
    if (value["source_sha"], value["manifest_sha256"], value["policy_revision"]) != (SOURCE, MANIFEST, POLICY):
        raise PortError("Frozen evidence pin mismatch")
    entries = tree(repo, head)
    manifest = load_manifest(repo)
    expected_inputs = required_inputs(entries, manifest)
    if set(value["inputs"]) != expected_inputs:
        raise PortError("Missing/extra required immutable candidate/source inputs")
    invocation = value["invocation"]
    if "identity" in invocation:
        validate_invocation(invocation, head, invocation["host"])
    else:
        fields(invocation, {"scope", "head_sha", "host"}, label="local immutable execution")
        if (invocation["scope"] not in (LOCAL_SCOPE, LOCAL_EXECUTOR_SCOPE)
                or invocation["head_sha"] != head or invocation["host"] not in ("linux", "windows")):
            raise PortError("Stale local invocation")
    fields(value["counts"], set(MODULES), label="complete module-discovery counts")
    for module, (_, minimum) in MODULES.items():
        require_counts(value["counts"][module], minimum=minimum)
    for path, identity in value["inputs"].items():
        if entries.get(path) != identity:
            raise PortError("Stale/fabricated raw input identity")
        file = output / "input-blobs" / identity
        if linked(file) or not file.is_file() or file.stat().st_size > 16 * 1024 * 1024 or blob(file.read_bytes()) != identity:
            raise PortError("Missing/unsafe/fabricated raw input blob")
    records = []
    actual_cases = {}
    for module, (_, minimum) in MODULES.items():
        cases, raw, counts = junit(output / "junit" / module, output, minimum)
        actual_cases[module] = cases
        if counts != value["counts"][module] or cases != value["native_cases"][module]:
            raise PortError("Claimed CLI/protocol cases differ from complete raw execution")
        records.extend({"module": module, "path": f"junit/{module}/{name}", "size": len(data), "sha256": sha(data)}
                       for name, data in raw.items())
    if records != value["raw_reports"]:
        raise PortError("Raw report sizes/digests/list differ")
    validate_native_floor(repo, actual_cases)
    original = original_accounting(repo, load_json(repo / "docs/android/test-cases.json"),
                                   load_json(repo / "docs/android/evidence/WP-004/inventory-details.json"),
                                   manifest, actual_cases["protocol"])
    unique = len({(o["native_test"]["class"], o["native_test"]["name"]) for o in original})
    if original != value["original_cases"] or value["original_declarations"] != 486 or value["unique_original_native_bindings"] != unique:
        raise PortError("Incomplete/tampered original assertion/parameter accounting")
    if (any(type(value[key]) is not int or value[key] != 0 for key in ("new_original_cases", "reviewed_exclusions"))
            or value["physical_radio_verified"] is not False):
        raise PortError("Invented original cases/exclusions/hardware acceptance")
    return value


def validate_native_floor(repo, native):
    baseline_path = repo / "docs/android/evidence/WP-109/baseline-native.json"
    if baseline_digest(baseline_path.read_bytes()) != BASELINE_SHA256:
        raise PortError("Independently captured baseline bytes changed")
    baseline = load_json(baseline_path)
    fields(baseline, {"schema_version", "provenance", "cases", "suite_count"}, label="frozen baseline native identities")
    if baseline["schema_version"] != 1 or len(baseline["cases"]) != 4708 or baseline["suite_count"] != 57:
        raise PortError("Frozen protocol baseline identity floor changed")
    expected = {(c["class"], c["name"]) for c in baseline["cases"]}
    actual = {(c["class"], c["name"]) for c in native["protocol"]}
    if len(expected) != 4708 or not expected <= actual:
        raise PortError("Removed/replaced baseline protocol identities")
    tcp_expected = {(NATIVE_PREFIX + "parity.TcpOwnershipParityTest", method + "()") for method in TCP_CASES}
    if not tcp_expected <= actual:
        raise PortError("New real-TCP ownership/quarantine/cancellation assertions are mandatory")
    cli_expected = required_cli_cases()
    cli_actual = {(c["class"], c["name"]) for c in native["meshcli"]}
    if not cli_expected <= cli_actual:
        raise PortError("Missing actual CLI assertion/family rows: " + repr(sorted(cli_expected - cli_actual)[:4])
                        + "; actual raw XML identities=" + repr(sorted(cli_actual)))


def baseline(directory):
    cases, raw, counts = junit(directory, directory.parent, 4708)
    if counts["discovered"] != 4708 or len(raw) != 57:
        raise PortError("Baseline must be the actual complete reviewed2fe protocol run")
    result = {
        "schema_version": 1,
        "provenance": {
            "repository": "cbattlegear/MeshCoreOne-Android", "head_sha": "2fe60386dc6cfe678e26fc24974d48027080926c",
            "run_id": 37217793524, "run_attempt": 1, "artifact_id": 11308899131,
            "artifact_zip_sha256": "438a7f46975082d1d1e20324f8369b850eef4d4e2054152b417dbbd5d4892f75",
            "scope": "independently replayed full actual prerequisite; not new WP-109 assertions",
        },
        "cases": cases, "suite_count": len(raw),
    }
    encoded = (json.dumps(result, indent=2, ensure_ascii=True) + "\n").encode("utf-8")
    if baseline_digest(encoded) != BASELINE_SHA256:
        raise PortError("Given raw reports differ from independently captured actual baseline")
    write_json(BASELINE, result)
    return {"baseline_cases": len(cases), "actual_suites": len(raw)}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("collect", "validate", "baseline", "self-test"))
    parser.add_argument("--output", type=Path)
    parser.add_argument("--invocation", type=Path)
    parser.add_argument("--baseline", type=Path)
    parser.add_argument("--head")
    args = parser.parse_args(argv)
    try:
        if args.command == "self-test":
            tests = unittest.defaultTestLoader.discover(str(Path(__file__).with_name("tests")), pattern="test_*.py")
            from controller.test_runner import run_suite
            return run_suite(tests, verbosity=0)
        if args.command == "baseline":
            if args.baseline is None:
                raise PortError("An explicit verified complete baseline directory is required")
            result = baseline(args.baseline)
        elif args.command == "collect":
            if args.output is None or not args.output.is_absolute():
                raise PortError("Explicit absolute evidence output required")
            value = collect(ROOT, args.output, args.invocation)
            result = {"scope": value["scope"], "counts": value["counts"], "original_declarations": value["original_declarations"]}
        else:
            if args.output is None or not args.head:
                raise PortError("Explicit output/exact immutable head required for independent replay")
            value = validate_bundle(args.output, ROOT, args.head)
            result = {"scope": value["scope"], "counts": value["counts"], "original_declarations": value["original_declarations"]}
        print(json.dumps(result, indent=2))
        return 0
    except (PortError, OSError, ValueError, KeyError) as error:
        print("BLOCKED: " + str(error), file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
