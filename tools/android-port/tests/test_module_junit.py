"""AndroidOnly: WP-003 Real temporary Git trees and adversarial active-module report assertions."""

import copy
import subprocess
import tempfile
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path, PureWindowsPath
from unittest.mock import patch

from controller.errors import PortError
from controller.module_junit import collect_module_tests, module_sources, safe_reports, validate_module_tests
from test_ci_evidence import junit_report


class ModuleJUnitTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.repo = self.root / "repo"
        self.repo.mkdir()
        self.output = self.root / "evidence"
        self.command("init", "--quiet")
        (self.repo / ".gitignore").write_text("**/build/\n", encoding="utf-8")
        self.source("core/model", "src/main/kotlin/Model.kt", "class Model\n")
        self.source("core/model", "src/test/kotlin/ModelTest.kt", "class ModelTest\n")
        self.source("core/model", "build.gradle.kts", 'plugins { id("mesh.jvm.library") }\n')
        self.source("core/database", "src/main/kotlin/Database.kt", "class Database\n")
        self.source("core/database", "src/test/java/DatabaseTest.java", "class DatabaseTest {}\n")
        self.source("core/ble", "src/main/kotlin/Boundary.kt", "class Boundary\n")
        self.commit()
        self.report("core/model")
        self.report("core/database", runner="testDebugUnitTest")

    def command(self, *arguments, input=None):
        return subprocess.run(
            ["git", "-c", "core.autocrlf=false", "-c", "core.hooksPath=" + str(self.root / "no-hooks"),
             "-c", "commit.gpgsign=false", "-c", "user.name=Module evidence fixture",
             "-c", "user.email=fixture@example.invalid", "-C", str(self.repo), *arguments],
            input=input, capture_output=True, text=True, check=True, timeout=30,
        ).stdout.strip()

    def source(self, module, relative, content):
        path = self.repo / "android" / module / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")
        return path

    def commit(self):
        self.command("add", "--all")
        self.command("commit", "--quiet", "-m",
                     "Temporary evidence fixture\n\nCo-authored-by: Copilot App <223556219+Copilot@users.noreply.github.com>")
        self.head = self.command("rev-parse", "HEAD")

    def report(self, module, count=3, runner="test"):
        path = self.repo / "android" / module / "build" / "test-results" / runner / "TEST-fixture.xml"
        junit_report(path, count)
        return path

    def positive(self):
        self.report("core/model")
        self.report("core/database", runner="testDebugUnitTest")
        return collect_module_tests(self.repo, self.output)

    def test_actual_committed_sources_select_both_jvm_and_android_modules(self):
        result = module_sources(self.repo)
        self.assertEqual(set(result), {"core/model", "core/database"})
        self.assertIn("android/core/model/build.gradle.kts", result["core/model"])
        self.assertIn("android/core/model/src/main/kotlin/Model.kt", result["core/model"])
        for inputs in result.values():
            for path, blob in inputs.items():
                self.assertEqual(blob, self.command("rev-parse", "HEAD:" + path))

    def test_separate_and_original_suites_are_not_double_counted(self):
        for module in ("core/contracts", "core/protocol", "core/testing"):
            self.source(module, "src/test/kotlin/SeparateTest.kt", "class SeparateTest\n")
        self.commit()
        self.assertEqual(set(module_sources(self.repo)), {"core/model", "core/database"})

    def test_android_test_only_and_uncommitted_sources_are_not_unit_suite_declarations(self):
        self.source("core/ble", "src/androidTest/kotlin/DeviceTest.kt", "class DeviceTest\n")
        self.commit()
        self.source("core/ble", "src/test/kotlin/UncommittedTest.kt", "class UncommittedTest\n")
        self.assertNotIn("core/ble", module_sources(self.repo))

    def test_test_debug_sources_activate_the_actual_module(self):
        self.source("core/ble", "src/testDebug/kotlin/BleTest.kt", "class BleTest\n")
        self.commit()
        self.assertIn("core/ble", module_sources(self.repo))

    def test_explicit_immutable_old_revision_does_not_read_new_head_inputs(self):
        original = self.head
        self.source("core/ble", "src/test/kotlin/BleTest.kt", "class BleTest\n")
        self.commit()
        self.assertNotIn("core/ble", module_sources(self.repo, original))
        self.assertIn("core/ble", module_sources(self.repo, self.head))

    def test_invalid_nonimmutable_revision_is_rejected(self):
        for revision in ("HEAD", "main", "../elsewhere", "0" * 39, True):
            with self.subTest(revision=revision), self.assertRaises(PortError):
                module_sources(self.repo, revision)

    def test_committed_symlink_input_is_rejected_without_os_symlink_privilege(self):
        blob = self.command("hash-object", "-w", "--stdin", input="../outside")
        self.command("update-index", "--add", "--cacheinfo",
                     "120000," + blob + ",android/core/model/src/test/kotlin/Link.kt")
        self.command("commit", "--quiet", "-m",
                     "Temporary linked-input fixture\n\nCo-authored-by: Copilot App <223556219+Copilot@users.noreply.github.com>")
        with self.assertRaisesRegex(PortError, "Unsafe"):
            module_sources(self.repo)

    def test_real_xml_copies_and_candidate_blobs_replay_exactly(self):
        result = self.positive()
        self.assertEqual(validate_module_tests(result, self.output, self.repo, self.head), result)
        for module, record in result.items():
            self.assertEqual(record["counts"]["passed"], 3)
            for report in record["reports"]:
                raw = self.output / report["path"]
                source = self.repo / "android" / module / "build" / "test-results"
                self.assertEqual(raw.read_bytes(), next(source.rglob(raw.name)).read_bytes())

    def test_report_order_is_case_sensitive_even_for_windows_path_enumeration(self):
        names = [
            "TEST-SourceReconnectLifecycleTest.xml",
            "TEST-SourceReconnectPolicyTest.xml",
            "TEST-SourceReconnectionCoordinatorTest.xml",
        ]
        windows_paths = [PureWindowsPath(r"C:\evidence") / name for name in reversed(names)]
        self.assertNotEqual(names, [path.name for path in sorted(windows_paths)])
        directory = self.root / "reports"
        directory.mkdir()
        with (
            patch.object(Path, "glob", return_value=iter(windows_paths)),
            patch("controller.module_junit.linked", return_value=False),
            patch.object(PureWindowsPath, "is_file", return_value=True, create=True) as file_guard,
        ):
            reports = safe_reports(directory, self.root)
        self.assertEqual(names, [path.name for path in reports])
        self.assertEqual(len(windows_paths), file_guard.call_count)
        self.assertEqual(set(windows_paths), set(reports))

    def test_mixed_case_raw_report_order_keeps_exact_bytes_and_strict_replay(self):
        names = [
            "TEST-SourceReconnectLifecycleTest.xml",
            "TEST-SourceReconnectPolicyTest.xml",
            "TEST-SourceReconnectionCoordinatorTest.xml",
        ]
        directory = self.repo / "android" / "core" / "model" / "build" / "test-results" / "test"
        for index, name in enumerate(reversed(names)):
            path = directory / name
            junit_report(path, 1)
            node = ET.fromstring(path.read_bytes())
            node.find("testcase").set("classname", f"fixture.ordered{index}")
            path.write_bytes(ET.tostring(node))
        value = self.positive()
        records = value["core/model"]["reports"]
        self.assertEqual(sorted(names + ["TEST-fixture.xml"]), [Path(item["path"]).name for item in records])
        self.assertEqual(6, value["core/model"]["counts"]["passed"])
        self.assertEqual(value, validate_module_tests(value, self.output, self.repo, self.head))
        for record in records:
            self.assertEqual((self.output / record["path"]).read_bytes(), (directory / Path(record["path"]).name).read_bytes())
        value["core/model"]["reports"].reverse()
        with self.assertRaisesRegex(PortError, "differs"):
            validate_module_tests(value, self.output, self.repo, self.head)

    def test_missing_active_runner_does_not_return_empty_success(self):
        (self.repo / "android" / "core" / "database" / "build" / "test-results" / "testDebugUnitTest" / "TEST-fixture.xml").unlink()
        with self.assertRaisesRegex(PortError, "Missing/ambiguous"):
            collect_module_tests(self.repo, self.output)

    def test_ambiguous_old_and_new_runner_directories_are_rejected(self):
        self.report("core/model")
        self.report("core/model", runner="testDebugUnitTest")
        with self.assertRaisesRegex(PortError, "ambiguous"):
            collect_module_tests(self.repo, self.output)

    def test_zero_failed_skipped_and_false_declared_outcomes_are_rejected(self):
        for outcome in ("zero", "failure", "error", "skipped", "missing-count"):
            with self.subTest(outcome=outcome):
                path = self.report("core/model", 0 if outcome == "zero" else 3)
                self.report("core/database", runner="testDebugUnitTest")
                if outcome != "zero":
                    root = ET.fromstring(path.read_bytes())
                    if outcome == "missing-count":
                        root.attrib.pop("tests")
                    else:
                        ET.SubElement(root.find("testcase"), outcome)
                    path.write_bytes(ET.tostring(root))
                with self.assertRaises(PortError):
                    collect_module_tests(self.repo, self.output)

    def test_duplicate_case_identity_is_not_inflated_discovery(self):
        path = self.report("core/model")
        root = ET.fromstring(path.read_bytes())
        root.findall("testcase")[1].set("name", root.findall("testcase")[0].get("name"))
        path.write_bytes(ET.tostring(root))
        with self.assertRaisesRegex(PortError, "duplicate"):
            collect_module_tests(self.repo, self.output)

    def test_unsafe_entities_and_malformed_xml_are_rejected(self):
        for data in (b"<!DOCTYPE testsuite><testsuite/>", b"<testsuite"):
            with self.subTest(data=data):
                path = self.report("core/model")
                path.write_bytes(data)
                with self.assertRaises(PortError):
                    collect_module_tests(self.repo, self.output)

    def test_dirty_compiled_inputs_cannot_claim_the_committed_snapshot(self):
        self.source("core/model", "src/main/kotlin/Model.kt", "class ChangedModel\n")
        with self.assertRaisesRegex(PortError, "differ"):
            collect_module_tests(self.repo, self.output)

    def test_destination_reuse_and_project_overlap_fail_before_copy(self):
        self.positive()
        with self.assertRaisesRegex(PortError, "newly created"):
            collect_module_tests(self.repo, self.output)
        for output in (self.repo / "evidence", self.root):
            with self.subTest(output=output), self.assertRaisesRegex(PortError, "overwrite"):
                collect_module_tests(self.repo, output)

    def test_missing_extra_stale_fabricated_or_altered_modules_fail_replay(self):
        original = self.positive()
        for kind in ("missing", "extra", "inputs", "count", "hash", "null"):
            with self.subTest(kind=kind):
                value = copy.deepcopy(original)
                record = value["core/model"]
                if kind == "missing":
                    value.pop("core/model")
                elif kind == "extra":
                    value["core/ble"] = copy.deepcopy(record)
                elif kind == "inputs":
                    record["inputs"][next(iter(record["inputs"]))] = "0" * 40
                elif kind == "count":
                    record["counts"]["passed"] = 4
                elif kind == "hash":
                    record["reports"][0]["sha256"] = "0" * 64
                else:
                    value["core/model"] = None
                with self.assertRaises(PortError):
                    validate_module_tests(value, self.output, self.repo, self.head)

    def test_actual_new_suite_cannot_disappear_from_claimed_report_list(self):
        value = self.positive()
        report = self.output / "junit" / "modules" / "core--model" / "TEST-extra.xml"
        junit_report(report, 1)
        node = ET.fromstring(report.read_bytes())
        node.find("testcase").set("classname", "fixture.extra")
        report.write_bytes(ET.tostring(node))
        with self.assertRaises(PortError):
            validate_module_tests(value, self.output, self.repo, self.head)

    def test_unregistered_raw_module_report_is_not_ignored(self):
        value = self.positive()
        junit_report(self.output / "junit" / "modules" / "core--ble" / "TEST-extra.xml", 1)
        with self.assertRaisesRegex(PortError, "unaccounted"):
            validate_module_tests(value, self.output, self.repo, self.head)

    def test_changed_or_absent_raw_xml_cannot_match_old_digests(self):
        value = self.positive()
        path = self.output / value["core/model"]["reports"][0]["path"]
        path.write_bytes(path.read_bytes().replace(b"case-0", b"case-9"))
        with self.assertRaisesRegex(PortError, "differs"):
            validate_module_tests(value, self.output, self.repo, self.head)
        path.unlink()
        with self.assertRaisesRegex(PortError, "Missing"):
            validate_module_tests(value, self.output, self.repo, self.head)

    def test_outside_or_linked_report_directories_fail_closed(self):
        path = self.root / "outside"
        junit_report(path / "TEST-outside.xml", 1)
        with self.assertRaisesRegex(PortError, "escapes"):
            safe_reports(path, self.repo)
        self.report("core/model")
        with patch("controller.module_junit.linked", return_value=True), self.assertRaisesRegex(PortError, "Linked"):
            collect_module_tests(self.repo, self.output)


if __name__ == "__main__":
    unittest.main()
