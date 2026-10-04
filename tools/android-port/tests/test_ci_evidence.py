"""AndroidOnly: WP-003 Complete evidence fixtures and failure/absence regression families."""

import copy
import struct
import tempfile
import unittest
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

from fixtures import BASE, HEAD, REPO, policy, test_manifest
from controller.apk_alignment import elf_load_alignment
from controller.ci_environment import write_json
from controller.ci_evidence import LINT_TARGETS, PYTHON_MINIMUMS, SUITES, aggregate, artifact_record, counts, lint_bundle_path, lint_evidence, suite_counts
from controller.errors import PortError
from controller.gates import Binding, policy_revision


def discovery(number):
    return {"discovered": number, "run": number, "passed": number, "failed": 0, "errors": 0, "skipped": 0}


def junit_report(path, number):
    root = ET.Element("testsuite", {"tests": str(number), "failures": "0", "errors": "0", "skipped": "0"})
    for index in range(number):
        ET.SubElement(root, "testcase", {"classname": "fixture.raw.report", "name": f"case-{index}"})
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(ET.tostring(root))


def update_artifacts(root, value):
    value["artifacts"] = [
        artifact_record(root, path) for path in sorted(root.rglob("*"))
        if path.is_file() and path.name != "ci-result.json"
    ]
    write_json(root / "ci-result.json", value)


class EvidenceTests(unittest.TestCase):
    def binding(self):
        manifest, rules = test_manifest("WP-003"), policy()
        return Binding(rules["repository"], "WP-003", BASE, HEAD, manifest.data["reference"]["commit"],
                       manifest.sha256, policy_revision(manifest, rules))

    def fixture(self, directory):
        from dataclasses import asdict

        binding = self.binding()
        for host in ("windows", "linux"):
            root = directory / host
            root.mkdir()
            suites = {name: discovery(minimum) for name, (_, minimum) in SUITES.items()}
            rows = ["suite\tdiscovered\tpassed\tfailed\terrors\tskipped"]
            for name, (_, number) in SUITES.items():
                junit_report(root / "junit" / "composite" / name / "TEST-fixture.xml", number)
                rows.append(f"{name}\t{number}\t{number}\t0\t0\t0")
            junit_report(root / "junit" / "standalone" / "build-logic" / "TEST-fixture.xml", 31)
            (root / "test-discovery.tsv").write_text("\n".join(rows) + "\n", encoding="utf-8")
            (root / "module-graph.tsv").write_text("consumer\tproducer\tconfiguration\n:app\t:core:contracts\timplementation\n")
            (root / "runtime-dependencies.tsv").write_text(
                "artifact\tdeclared_license\tlicense_url\tlicense_pom\tlicense_pom_sha256\tlegal_gate\n"
                "fixture:library:1\tApache-2.0\thttps://www.apache.org/licenses/LICENSE-2.0.txt\tfixture:library:1\t"
                + "1" * 64 + "\thuman-review-pending\n")
            lint = {}
            for target in LINT_TARGETS:
                path = root / lint_bundle_path(target)
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text('<issues format="6" by="lint fixture"/>')
                lint[target] = lint_evidence(path)
            notices = ["assets/licenses/GPL-3.0.txt", "assets/licenses/MeshCore-MIT.txt", "assets/licenses/Apache-2.0.txt"]
            with zipfile.ZipFile(root / "debug.apk", "w") as archive:
                archive.writestr("AndroidManifest.xml", "fixture-only manifest; not an actual Android binary")
                archive.writestr("classes.dex", "fixture-only dex; not production execution evidence")
                for name in notices:
                    archive.writestr(name, "fixture-only notice")
            apk = artifact_record(root, root / "debug.apk")
            inspection = {
                "scope": "fixture-only APK/report shape; not a live native execution",
                "artifact": "android/app/build/outputs/apk/debug/app-debug.apk",
                "sha256": apk["sha256"], "size_bytes": apk["size"],
                "package": "com.meshcoreone.android.debug", "min_sdk": 31, "target_sdk": 37,
                "permissions": ["com.meshcoreone.android.debug.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"],
                "pinned_notices": notices, "verification_fixture_packaged": False, "native_libraries": [],
                "native_16kb_compatibility_verified": False, "static_16kb_alignment_verified": True,
                "elf_pt_load_alignment": {}, "physical_device_or_native_runtime_verified": False,
            }
            write_json(root / "apk-inspection.json", inspection)
            value = {
                "schema_version": 1, "binding": asdict(binding), "run_id": 71, "run_attempt": 2, "host": host,
                "scope": "fixture shape only",
                "stages": {name: "success" for name in ("verify", "standalone", "assemble", "lint")},
                "cache_proofs": {name: {"user_cache_initially_absent": True, "project_cache_initially_absent": True}
                                 for name in ("composite", "standalone")},
                "python": {name: discovery(number) for name, number in PYTHON_MINIMUMS.items()},
                "suites": suites, "standalone": discovery(31), "lint": lint, "apk": inspection,
            }
            update_artifacts(root, value)
        return binding

    def aggregate(self, directory, binding, needs=None):
        return aggregate(needs or {"build": {"result": "success"}}, directory, binding, 71, 2)

    def test_both_exact_native_host_shapes_and_complete_hashes_are_required(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            binding = self.fixture(directory)
            result = self.aggregate(directory, binding)
            self.assertEqual(set(result["hosts"]), {"linux", "windows"})
            self.assertEqual(result["hosts"]["linux"]["kotlin_assertions"], 47)

    def test_failed_cancelled_skipped_and_missing_required_job_fail(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            binding = self.fixture(directory)
            for result in ("failure", "cancelled", "skipped", None):
                with self.subTest(result=result), self.assertRaises(PortError):
                    self.aggregate(directory, binding, {"build": {"result": result}})
            with self.assertRaises(PortError):
                aggregate({}, directory, binding, 71, 2)

    def test_absent_mandatory_host_artifact_is_not_green(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            binding = self.fixture(directory)
            (directory / "linux" / "ci-result.json").unlink()
            with self.assertRaisesRegex(PortError, "Both Windows and Linux"):
                self.aggregate(directory, binding)

    def test_warmed_or_missing_root_and_standalone_cache_proofs_fail(self):
        from controller.schema import load_json

        for kind in ("composite", "standalone", "missing"):
            with self.subTest(kind=kind), tempfile.TemporaryDirectory() as temporary:
                directory = Path(temporary)
                binding = self.fixture(directory)
                path = directory / "linux" / "ci-result.json"
                value = load_json(path)
                if kind == "missing":
                    value.pop("cache_proofs")
                else:
                    value["cache_proofs"][kind]["user_cache_initially_absent"] = False
                write_json(path, value)
                with self.assertRaises(PortError):
                    self.aggregate(directory, binding)
    def test_stale_base_head_source_policy_repository_run_attempt_and_host_fail(self):
        for field in ("repository", "base_sha", "head_sha", "source_sha", "manifest_sha256", "policy_revision",
                      "run_id", "run_attempt", "host"):
            with self.subTest(field=field), tempfile.TemporaryDirectory() as temporary:
                directory = Path(temporary)
                binding = self.fixture(directory)
                path = directory / "linux" / "ci-result.json"
                from controller.schema import load_json

                value = load_json(path)
                if field in value["binding"]:
                    value["binding"][field] = "elsewhere/repo" if field == "repository" else ("0" * len(value["binding"][field]))
                else:
                    value[field] = "windows" if field == "host" else 999
                write_json(path, value)
                with self.assertRaises(PortError):
                    self.aggregate(directory, binding)

    def test_missing_suite_skipped_stage_and_tampered_apk_fail(self):
        for kind in ("suite", "stage", "artifact", "fixture"):
            with self.subTest(kind=kind), tempfile.TemporaryDirectory() as temporary:
                directory = Path(temporary)
                binding = self.fixture(directory)
                from controller.schema import load_json

                path = directory / "linux" / "ci-result.json"
                value = load_json(path)
                if kind == "suite":
                    value["suites"].pop("room-verification")
                elif kind == "stage":
                    value["stages"]["lint"] = "skipped"
                elif kind == "fixture":
                    value["apk"]["verification_fixture_packaged"] = True
                else:
                    (directory / "linux" / "debug.apk").write_bytes(b"changed fixture bytes")
                write_json(path, value)
                with self.assertRaises(PortError):
                    self.aggregate(directory, binding)

    def test_zero_inconsistent_boolean_failed_and_skipped_discovery_fail(self):
        for value in (discovery(0), {**discovery(1), "run": 0}, {**discovery(1), "discovered": True},
                      {**discovery(1), "failed": 1}, {**discovery(1), "skipped": 1}):
            with self.subTest(value=value), self.assertRaises(PortError):
                counts(value)
        with self.assertRaises(PortError):
            counts(discovery(30), minimum=31)

    def test_null_untyped_and_forged_lint_evidence_fail_even_with_valid_hashes(self):
        from controller.schema import load_json

        for kind in ("null", "boolean", "count", "hash", "error", "skipped"):
            with self.subTest(kind=kind), tempfile.TemporaryDirectory() as temporary:
                directory = Path(temporary)
                binding = self.fixture(directory)
                root = directory / "linux"
                value = load_json(root / "ci-result.json")
                target = LINT_TARGETS[0]
                if kind == "null":
                    value["lint"][target] = None
                elif kind == "boolean":
                    value["lint"][target]["warnings"] = False
                elif kind == "count":
                    value["lint"][target]["warnings"] = 10
                elif kind == "hash":
                    value["lint"][target]["sha256"] = "0" * 64
                elif kind == "error":
                    (root / lint_bundle_path(target)).write_text('<issues format="6" by="lint fixture"><issue id="fixture" severity="Error" message="fixture"/></issues>')
                else:
                    (root / lint_bundle_path(target)).write_text('<issues format="6" by="lint fixture" status="skipped"/>')
                update_artifacts(root, value)
                with self.assertRaises(PortError):
                    self.aggregate(directory, binding)

    def test_fabricated_counts_raw_skipped_errors_tsv_and_missing_reports_fail(self):
        from controller.schema import load_json

        for kind in ("counts", "tsv", "invalid-tsv", "skipped", "error", "missing", "missing-lint"):
            with self.subTest(kind=kind), tempfile.TemporaryDirectory() as temporary:
                directory = Path(temporary)
                binding = self.fixture(directory)
                root = directory / "linux"
                value = load_json(root / "ci-result.json")
                raw = root / "junit" / "composite" / "app" / "TEST-fixture.xml"
                if kind == "counts":
                    value["suites"]["app"] = discovery(20)
                elif kind == "tsv":
                    path = root / "test-discovery.tsv"
                    path.write_text(path.read_text().replace("app\t10\t10", "app\t20\t20"))
                elif kind == "invalid-tsv":
                    (root / "test-discovery.tsv").write_text("not a discovery TSV")
                elif kind == "missing":
                    raw.unlink()
                elif kind == "missing-lint":
                    (root / lint_bundle_path(LINT_TARGETS[0])).unlink()
                else:
                    xml = ET.parse(raw)
                    ET.SubElement(xml.getroot().find("testcase"), kind)
                    xml.write(raw)
                update_artifacts(root, value)
                with self.assertRaises(PortError):
                    self.aggregate(directory, binding)

    def test_invalid_inspection_json_and_stale_apk_semantics_fail(self):
        from controller.schema import load_json

        for kind in ("invalid", "stale", "bytes", "fixture"):
            with self.subTest(kind=kind), tempfile.TemporaryDirectory() as temporary:
                directory = Path(temporary)
                binding = self.fixture(directory)
                root = directory / "linux"
                value = load_json(root / "ci-result.json")
                if kind == "invalid":
                    (root / "apk-inspection.json").write_text("not JSON")
                elif kind == "stale":
                    inspection = load_json(root / "apk-inspection.json")
                    inspection["target_sdk"] = 36
                    write_json(root / "apk-inspection.json", inspection)
                elif kind == "bytes":
                    (root / "debug.apk").write_bytes(b"not the inspected APK bytes")
                else:
                    value["apk"]["verification_fixture_packaged"] = True
                    write_json(root / "apk-inspection.json", value["apk"])
                update_artifacts(root, value)
                with self.assertRaises(PortError):
                    self.aggregate(directory, binding)
    def test_actual_junit_cases_not_summary_numbers_control_discovery(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            report = directory / "TEST-fixture.xml"
            report.write_text('<testsuite tests="1" failures="0" errors="0" skipped="0"><testcase classname="fixture" name="case"/></testsuite>')
            self.assertEqual(suite_counts(directory, 1)["passed"], 1)
            for xml in (
                '<testsuite tests="10" failures="0" errors="0" skipped="0"><testcase classname="fixture" name="case"/></testsuite>',
                '<testsuite tests="1" failures="0" errors="0" skipped="0"><testcase classname="fixture" name="case"><skipped/></testcase></testsuite>',
                '<!DOCTYPE fixture><testsuite/>',
            ):
                report.write_text(xml)
                with self.subTest(xml=xml), self.assertRaises(PortError):
                    suite_counts(directory, 1)

    def test_static_elf_load_alignment_is_not_runtime_device_acceptance(self):
        data = bytearray(128)
        data[:6] = b"\x7fELF\x02\x01"
        struct.pack_into("<Q", data, 32, 64)
        struct.pack_into("<HH", data, 54, 56, 1)
        struct.pack_into("<IIQQQQQQ", data, 64, 1, 5, 0, 0, 0, 128, 128, 16384)
        self.assertEqual(elf_load_alignment(bytes(data)), [16384])
        for alignment in (4096, 16385, 0):
            struct.pack_into("<Q", data, 64 + 48, alignment)
            with self.subTest(alignment=alignment), self.assertRaises(PortError):
                elf_load_alignment(bytes(data))
