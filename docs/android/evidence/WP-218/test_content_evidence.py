"""AndroidOnly: WP-218 Temporary-Git/synthetic-XML reader regressions, never native parity evidence."""

import copy
from dataclasses import asdict
import json
from pathlib import Path
import re
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import xml.etree.ElementTree as ET

import collect_evidence as collector
from controller.errors import PortError
from controller.gates import Binding, policy_revision
from controller.model import Manifest, load_manifest, tree


class ContentFixture:
    def __init__(self, directory):
        self.repo = directory / "fixture-only-repository"
        self.output = directory / "fixture-only-raw"
        self.repo.mkdir()
        self.manifest_data = copy.deepcopy(load_manifest(collector.ROOT).data)
        self.exclusions = copy.deepcopy(load_manifest(collector.ROOT).exclusions)
        actual_head = collector.git(collector.ROOT, "rev-parse", "HEAD").decode().strip()
        entries = tree(collector.ROOT, actual_head)
        manifest = Manifest(self.manifest_data, self.exclusions, collector.ROOT)
        for path in collector.input_paths(entries, manifest.data):
            destination = self.repo / path
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_bytes((collector.ROOT / path).read_bytes().replace(b"\r\n", b"\n"))
        self.aliases = dict(collector.source_bindings.ALIASES)
        self.original_blockers = {
            ("LinkPreviewServiceTests", "loadImageData rejects a redirect to a private host"):
                "Fixture-only missing original behavior; unrelated cases cannot replace it.",
        }
        fixture_path = self.repo / (
            "android/core/services/src/test/kotlin/com/meshcoreone/android/core/services/content/FixtureOriginalTest.kt"
        )
        fixture_methods = []
        for index, key in enumerate(self.original_blockers):
            method = f"fixture-only reader assertion {index}"
            fixture_methods.append(f"    @Test\n    fun `{method}`() {{ assertTrue(true) }}\n")
            self.aliases[key] = ("services", "FixtureOriginalTest", method)
        fixture_path.write_text(
            "// AndroidOnly: WP-218 synthetic reader fixture; not an implementation or execution proof.\n"
            "package com.meshcoreone.android.core.services.content\n"
            "import kotlin.test.Test\nimport kotlin.test.assertTrue\n"
            "class FixtureOriginalTest {\n" + "".join(fixture_methods) + "}\n",
            encoding="utf-8",
        )
        self.git("init", "--quiet")
        self.git("config", "user.name", "Reader fixture only")
        self.git("config", "user.email", "reader-fixture@example.invalid")
        self.git("config", "core.autocrlf", "false")
        self.git("add", "--all")
        self.commit("fixture-only immutable base")
        self.base = self.git("rev-parse", "HEAD").strip()
        (self.repo / "fixture-only-successor.txt").write_text("Synthetic lineage, not a product change.\n", encoding="utf-8")
        self.git("add", "--all")
        self.commit("fixture-only candidate")
        self.head = self.git("rev-parse", "HEAD").strip()
        self.write_reports()
        self.invocation = directory / "fixture-only-invocation.json"
        self.write_invocation()

    def git(self, *args):
        return subprocess.check_output(["git", "-C", str(self.repo), *args], stderr=subprocess.STDOUT).decode("utf-8")

    def commit(self, message):
        self.git("commit", "--quiet", "-m", message,
                 "-m", "Co-authored-by: Copilot App <223556219+Copilot@users.noreply.github.com>")

    def manifest(self):
        return Manifest(self.manifest_data, self.exclusions, self.repo)

    def write_invocation(self):
        policy = json.loads((self.repo / "docs/android/automation-policy.json").read_text())
        bound = Binding(collector.REPOSITORY, "WP-003", self.base, self.head, collector.SOURCE,
                        self.manifest().sha256, policy_revision(self.manifest(), policy))
        self.identity = {
            "schema_version": 1, "stage": "verify", "host": "linux",
            "identity": {"binding": asdict(bound), "run_id": 70001, "run_attempt": 1},
        }
        self.invocation.write_text(json.dumps(self.identity), encoding="utf-8")

    def write_reports(self):
        self.report_paths = {}
        for module, prefix in collector.PREFIXES.items():
            rows = []
            for path in sorted((self.repo / collector.TEST_INPUTS[0 if module == "services" else 1]).glob("*.kt")):
                text = path.read_text(encoding="utf-8")
                classes = list(re.finditer(r"^class\s+(\w+)", text, re.MULTILINE))
                for method in re.finditer(r"@Test\s+fun\s+(?:`([^`]+)`|(\w+))\s*\(", text):
                    owners = [match for match in classes if match.start() < method.start()]
                    if not owners:
                        raise AssertionError("Fixture has an unaccounted native declaration")
                    name = method[1] or method[2]
                    sdks = collector.configured_sdks(text)
                    for sdk in sorted(sdks) if sdks else [None]:
                        suffix = f"[{sdk}]" if sdks and sdk != max(sdks) else ""
                        rows.append((prefix + owners[-1][1], name + suffix + ("()" if module == "services" else "")))
            root = ET.Element("testsuite", {
                "tests": str(len(rows)), "failures": "0", "errors": "0", "skipped": "0",
                "name": "fixture-only schema assertions, not native execution",
            })
            for native_class, name in rows:
                ET.SubElement(root, "testcase", {"classname": native_class, "name": name})
            path = self.repo / collector.REPORTS[module] / "TEST-fixture-only.xml"
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(ET.tostring(root))
            self.report_paths[module] = path

    def capture(self, invocation=True):
        return collector.capture(self.repo, self.output, invocation=self.invocation if invocation else None)

    def verify(self, snapshot):
        return collector.verify(self.repo, self.output, snapshot)


class ContentEvidenceTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.fixture = ContentFixture(Path(self.directory.name))
        self.patches = [
            patch.object(collector, "load_manifest", return_value=self.fixture.manifest()),
            patch.object(collector, "APPROVED_BASE", self.fixture.base),
            patch.dict(collector.source_bindings.ALIASES, self.fixture.aliases, clear=True),
            patch.dict(collector.source_bindings.BLOCKED, {}, clear=True),
        ]
        for item in self.patches:
            item.start()
            self.addCleanup(item.stop)

    def test_complete_fixture_replays_all_154_original_families_and_actual_declared_nodes(self):
        snapshot = self.fixture.capture()
        value = self.fixture.verify(snapshot)
        self.assertEqual(value["result"], "PASS")
        self.assertEqual(value["original_families"], 154)
        self.assertEqual(value["executed_families"], 154)
        self.assertEqual(sum(value["native_counts"].values()), value["declared_content_tests_executed"])
        families = [row for row in value["source_cases"] if row["inputs"] is not None]
        self.assertEqual([row["inputs"]["declared_count"] for row in families], [6, 6])
        self.assertEqual(value["invocation"]["binding"]["base_sha"], self.fixture.base)
        self.assertEqual(value["invocation"]["binding"]["head_sha"], self.fixture.head)
        self.assertEqual(value["invocation"]["run_id"], 70001)
        self.assertEqual(value["invocation"]["run_attempt"], 1)

    def test_missing_real_behavior_is_blocked_not_substituted_by_unrelated_typed_or_fake_cases(self):
        with patch.dict(collector.source_bindings.BLOCKED, self.fixture.original_blockers, clear=True):
            snapshot = self.fixture.capture()
            with self.assertRaisesRegex(PortError, str(len(self.fixture.original_blockers)) + " of 154"):
                self.fixture.verify(snapshot)
        value = json.loads((self.fixture.output / "content-result.json").read_text())
        self.assertEqual(value["result"], "BLOCKED")
        self.assertEqual(value["executed_families"], 154 - len(self.fixture.original_blockers))
        self.assertEqual(len([row for row in value["source_cases"] if row["blocker"]]), len(self.fixture.original_blockers))

    def test_all_raw_report_bytes_are_retained_before_malformed_xml_is_rejected(self):
        invalid = b"<testsuite><not-closed"
        self.fixture.report_paths["services"].write_bytes(invalid)
        snapshot = self.fixture.capture()
        self.assertEqual((self.fixture.output / "junit/services/TEST-fixture-only.xml").read_bytes(), invalid)
        self.assertEqual(
            (self.fixture.output / "junit/app/TEST-fixture-only.xml").read_bytes(),
            self.fixture.report_paths["app"].read_bytes(),
        )
        with self.assertRaisesRegex(PortError, "Malformed raw JUnit"):
            self.fixture.verify(snapshot)

    def test_failed_error_and_skipped_nodes_are_retained_and_rejected_even_when_counters_lie(self):
        snapshot = self.fixture.capture()
        for kind in ("failure", "error", "skipped"):
            with self.subTest(kind=kind):
                raw = self.fixture.report_paths["services"].read_bytes()
                root = ET.fromstring(raw)
                outcome = ET.SubElement(root.find("testcase"), kind, {"message": "fixture-only actual outcome"})
                outcome.text = "Full synthetic stack/output must not be dropped by the reader."
                data = ET.tostring(root)
                path = self.fixture.output / "junit/services/TEST-fixture-only.xml"
                path.write_bytes(data)
                record = next(row for row in snapshot["reports"] if row["module"] == "services")
                record.update(sha256=collector.sha(data), size_bytes=len(data))
                with self.assertRaisesRegex(PortError, "failed/skipped"):
                    self.fixture.verify(snapshot)
                self.assertEqual(path.read_bytes(), data)

    def test_zero_inconsistent_and_missing_counters_fail(self):
        snapshot = self.fixture.capture()
        for mode in ("zero", "inconsistent", "missing"):
            with self.subTest(mode=mode):
                root = ET.fromstring(self.fixture.report_paths["services"].read_bytes())
                if mode == "zero":
                    for node in list(root):
                        root.remove(node)
                    root.set("tests", "0")
                elif mode == "inconsistent":
                    root.set("tests", str(int(root.get("tests")) + 1))
                else:
                    root.attrib.pop("errors")
                data = ET.tostring(root)
                (self.fixture.output / "junit/services/TEST-fixture-only.xml").write_bytes(data)
                record = next(row for row in snapshot["reports"] if row["module"] == "services")
                record.update(sha256=collector.sha(data), size_bytes=len(data))
                with self.assertRaises(PortError):
                    self.fixture.verify(snapshot)

    def test_missing_module_keeps_other_produced_raw_reports_and_never_succeeds(self):
        self.fixture.report_paths["services"].unlink()
        with self.assertRaisesRegex(PortError, "available bytes retained"):
            self.fixture.capture()
        self.assertEqual(
            (self.fixture.output / "junit/app/TEST-fixture-only.xml").read_bytes(),
            self.fixture.report_paths["app"].read_bytes(),
        )
        self.assertTrue(json.loads((self.fixture.output / "raw-snapshot.json").read_text())["retention_errors"])

    def test_reduced_original_case_with_consistent_junit_counters_fails(self):
        root = ET.fromstring(self.fixture.report_paths["services"].read_bytes())
        target = next(node for node in root if node.get("name") == "Allows HTTPS URLs()")
        root.remove(target)
        root.set("tests", str(len(root.findall("testcase"))))
        self.fixture.report_paths["services"].write_bytes(ET.tostring(root))
        snapshot = self.fixture.capture()
        with self.assertRaisesRegex(PortError, "Missing/ambiguous actual declared native content test"):
            self.fixture.verify(snapshot)

    def test_independent_consumer_case_cannot_disappear_just_because_it_has_no_original_family(self):
        root = ET.fromstring(self.fixture.report_paths["app"].read_bytes())
        target = next(node for node in root if node.get("name") == "garbage bytes that are not any recognized image fail explicitly")
        root.remove(target)
        root.set("tests", str(len(root.findall("testcase"))))
        self.fixture.report_paths["app"].write_bytes(ET.tostring(root))
        snapshot = self.fixture.capture()
        with self.assertRaisesRegex(PortError, "Missing/ambiguous actual declared native content test"):
            self.fixture.verify(snapshot)

    def test_duplicate_actual_native_identity_fails(self):
        root = ET.fromstring(self.fixture.report_paths["services"].read_bytes())
        root.append(copy.deepcopy(root.find("testcase")))
        root.set("tests", str(len(root.findall("testcase"))))
        self.fixture.report_paths["services"].write_bytes(ET.tostring(root))
        snapshot = self.fixture.capture()
        with self.assertRaisesRegex(PortError, "duplicate"):
            self.fixture.verify(snapshot)

    def test_utf16_xml_entities_are_retained_verbatim_but_rejected_before_parsing(self):
        raw = '<?xml version="1.0" encoding="UTF-16"?><!DOCTYPE testsuite [<!ENTITY x "unsafe">]><testsuite/>'.encode("utf-16")
        self.fixture.report_paths["services"].write_bytes(raw)
        snapshot = self.fixture.capture()
        self.assertEqual((self.fixture.output / "junit/services/TEST-fixture-only.xml").read_bytes(), raw)
        with self.assertRaisesRegex(PortError, "Unsafe raw JUnit"):
            self.fixture.verify(snapshot)

    def test_missing_invocation_still_retains_raw_reports_but_cannot_claim_a_run(self):
        snapshot = self.fixture.capture(invocation=False)
        self.assertEqual(len(snapshot["reports"]), 2)
        with self.assertRaisesRegex(PortError, "binding is mandatory"):
            self.fixture.verify(snapshot)

    def test_stale_repository_head_source_manifest_policy_and_nonancestor_base_are_rejected(self):
        snapshot = self.fixture.capture()
        original = copy.deepcopy(self.fixture.identity)
        for field in ("repository", "head_sha", "source_sha", "manifest_sha256", "policy_revision", "base_sha"):
            with self.subTest(field=field):
                value = copy.deepcopy(original)
                value["identity"]["binding"][field] = "other/repository" if field == "repository" else (
                    "0" * 64 if field in ("manifest_sha256", "policy_revision") else "0" * 40)
                raw = json.dumps(value).encode("utf-8")
                (self.fixture.output / "invocation.json").write_bytes(raw)
                snapshot["invocation"] = {"sha256": collector.sha(raw), "size_bytes": len(raw)}
                with self.assertRaises(PortError):
                    self.fixture.verify(snapshot)

    def test_zero_boolean_run_and_wrong_host_or_stage_are_not_execution_identity(self):
        snapshot = self.fixture.capture()
        original = copy.deepcopy(self.fixture.identity)
        for field, replacement in (("run_id", 0), ("run_attempt", True), ("host", "windows"), ("stage", "lint")):
            with self.subTest(field=field):
                value = copy.deepcopy(original)
                target = value["identity"] if field in ("run_id", "run_attempt") else value
                target[field] = replacement
                raw = json.dumps(value).encode()
                (self.fixture.output / "invocation.json").write_bytes(raw)
                snapshot["invocation"] = {"sha256": collector.sha(raw), "size_bytes": len(raw)}
                with self.assertRaises(PortError):
                    self.fixture.verify(snapshot)

    def test_uncommitted_compiled_input_is_copied_before_immutable_verification_rejects_it(self):
        path = "android/core/services/build.gradle.kts"
        changed = (self.fixture.repo / path).read_bytes() + b"\n// fixture-only uncommitted compiled input\n"
        (self.fixture.repo / path).write_bytes(changed)
        snapshot = self.fixture.capture()
        self.assertEqual((self.fixture.output / "inputs" / path).read_bytes(), changed)
        with self.assertRaisesRegex(PortError, "differs from its immutable Git input"):
            self.fixture.verify(snapshot)

    def test_untracked_native_file_fails_instead_of_compiling_an_unbound_success(self):
        path = self.fixture.repo / collector.TEST_INPUTS[0] / "UntrackedTest.kt"
        path.write_text("// fixture-only untracked input\n", encoding="utf-8")
        snapshot = self.fixture.capture()
        with self.assertRaisesRegex(PortError, "Uncommitted compiled"):
            self.fixture.verify(snapshot)

    def test_tampered_raw_report_or_invocation_digest_fails(self):
        snapshot = self.fixture.capture()
        snapshot["reports"][0]["sha256"] = "0" * 64
        with self.assertRaisesRegex(PortError, "Retained raw JUnit changed"):
            self.fixture.verify(snapshot)
        snapshot["reports"][0]["sha256"] = collector.sha(
            (self.fixture.output / snapshot["reports"][0]["path"]).read_bytes())
        snapshot["invocation"]["sha256"] = "0" * 64
        with self.assertRaisesRegex(PortError, "invocation bytes changed"):
            self.fixture.verify(snapshot)

    def test_report_path_escape_and_existing_snapshot_destination_fail(self):
        snapshot = self.fixture.capture()
        snapshot["reports"][0]["path"] = "../outside.xml"
        with self.assertRaisesRegex(PortError, "escaping"):
            self.fixture.verify(snapshot)
        with self.assertRaisesRegex(PortError, "newly created"):
            self.fixture.capture()

    def test_frozen_family_catalog_and_parameter_rows_cannot_be_lowered(self):
        snapshot = self.fixture.capture()
        inputs = {
            path: (self.fixture.output / "inputs" / path).read_bytes() for path in snapshot["inputs"]
        }
        catalog = json.loads(inputs["docs/android/test-cases.json"])
        entry = next(row for row in catalog["entries"] if row["path"].endswith("ImageURLDetectorTests.swift"))
        entry["cases"][0]["parameter_family"] = "single"
        changed = dict(inputs, **{"docs/android/test-cases.json": json.dumps(catalog).encode()})
        with self.assertRaisesRegex(PortError, "parameter families differ"):
            collector.original_cases(self.fixture.manifest(), changed)
        originals = collector.original_cases(self.fixture.manifest(), inputs)
        cases, _ = collector.junit(snapshot, self.fixture.output)
        path = collector.TEST_INPUTS[0] + "ImageUrlClassifierTest.kt"
        reduced = inputs[path].replace(b'"jpg", "jpeg", "png", "gif", "webp", "heic"', b'"jpg"')
        with self.assertRaisesRegex(PortError, "parameter rows"):
            collector.source_execution(originals, cases, {**inputs, path: reduced})


if __name__ == "__main__":
    raise SystemExit(collector.run_suite(unittest.defaultTestLoader.loadTestsFromModule(__import__(__name__))))
