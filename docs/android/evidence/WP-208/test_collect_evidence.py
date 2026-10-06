"""AndroidOnly: WP-208 Positive/adversarial reader assertions; synthetic XML is not product evidence."""

import copy
import importlib.util
import json
from pathlib import Path
import tempfile
import subprocess
import unittest
import xml.etree.ElementTree as ET

SPEC = importlib.util.spec_from_file_location("wp208_reader", Path(__file__).with_name("collect_evidence.py"))
READER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(READER)


class EvidenceReaderTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="wp208-reader-")
        self.output = Path(self.temporary.name)
        self.accounting = {
            "implementation_inputs": ["synthetic/input.kt"],
            "expanded_originals": {"Example::original()": {}},
            "original_families": {"Example::original()": {}},
            "native_regressions": {"WP-208::native": {}},
            "room_consumers": ["room"],
        }
        self.write_suite("services", [("Example::original()", READER.PACKAGE + "ExampleTest"),
                                      ("WP-208::native", READER.PACKAGE + "NativeTest")])
        self.write_suite("data", [("room", READER.ROOM_CLASS)] +
                         [(f"existing-{index}", "com.meshcoreone.android.core.data.ExistingTest") for index in range(368)])
        self.invocation = {
            "stage": "verify", "host": "linux", "identity": {
                "run_id": 123, "run_attempt": 1,
                "binding": {"repository": "cbattlegear/MeshCoreOne-Android", "head_sha": "a" * 40,
                            "base_sha": READER.BASE, "source_sha": READER.SOURCE, "manifest_sha256": READER.MANIFEST,
                            "policy_revision": READER.POLICY, "work_package": "WP-003"},
            },
        }
        self.save_invocation()
        self.snapshot = {
            "schema_version": 1, "repository": "cbattlegear/MeshCoreOne-Android", "work_package": "WP-208", "head_sha": "a" * 40,
            "source_sha": READER.SOURCE, "manifest_sha256": READER.MANIFEST, "policy_revision": READER.POLICY,
            "capture_errors": [], "invocation": READER.record(self.output / "invocation.json", self.output),
            "execution_expected": {"run_id": 123, "run_attempt": 1, "base_sha": READER.BASE, "head_sha": "a" * 40},
            "input_blobs": [{"path": "synthetic/input.kt", "git_blob": "b" * 40, "expected_blob": "b" * 40, "matches_head": True}],
            "raw_junit": {},
        }
        self.save_snapshot(refresh_reports=True)

    def tearDown(self):
        self.temporary.cleanup()

    def write_suite(self, module, cases, **counters):
        directory = self.output / "junit" / module
        directory.mkdir(parents=True, exist_ok=True)
        root = ET.Element("testsuite", tests=str(len(cases)), failures="0", errors="0", skipped="0")
        root.attrib.update(counters)
        for name, classname in cases:
            ET.SubElement(root, "testcase", name=name, classname=classname)
        path = directory / "TEST-fixture.xml"
        path.write_bytes(ET.tostring(root))
        return path

    def save_invocation(self):
        (self.output / "invocation.json").write_text(json.dumps(self.invocation), encoding="utf-8")
        if hasattr(self, "snapshot"):
            self.snapshot["invocation"] = READER.record(self.output / "invocation.json", self.output)

    def save_snapshot(self, refresh_reports=False):
        if refresh_reports:
            for module in ("services", "data"):
                self.snapshot["raw_junit"][module] = [READER.record(path, self.output)
                    for path in sorted((self.output / "junit" / module).glob("TEST-*.xml"), key=lambda value: value.name)]
        (self.output / "raw-capture.json").write_text(json.dumps(self.snapshot), encoding="utf-8")

    def validate(self):
        return READER.validate_capture(self.output, self.accounting)

    def rejected(self):
        with self.assertRaises((ValueError, ET.ParseError)):
            self.validate()

    def test_positive_complete_fixture_has_nonzero_exact_owned_and_full_native_counts(self):
        result = self.validate()
        self.assertEqual(2, result["counts"]["messaging"]["discovered"])
        self.assertEqual(369, result["counts"]["full_data"]["discovered"])
        self.assertEqual(1, result["counts"]["actual_room_consumers"])
        self.assertEqual(123, result["execution_identity"]["run_id"])

    def test_actual_authored_source_accounts_every_frozen_family_and_argument_row(self):
        source = READER.source_accounting()
        self.assertEqual(128, len(source["original_families"]))
        self.assertEqual(131, len(source["expanded_originals"]))
        self.assertEqual(34, len(source["primary_inputs"]))
        self.assertGreaterEqual(len(source["room_consumers"]), 8)
        self.assertGreater(len(source["native_regressions"]), 0)

    def test_actual_data_hook_uses_correct_reader_flags_and_forwarded_invocation_only(self):
        text = (READER.ROOT / "android" / "core" / "data" / "build.gradle.kts").read_text()
        self.assertIn('import java.io.File', text)
        self.assertIn('providers.gradleProperty("meshCliInvocationFile")', text)
        self.assertIn('File(invocation.parentFile, "wp208-native")', text)
        self.assertIn('add("--capture-only")', text)
        self.assertIn('"--output"', text)
        self.assertIn('"--invocation"', text)
        self.assertNotIn('--invocation-file', text)
        self.assertNotIn('GITHUB_RUN_ID', text)
        self.assertNotIn('System.getenv', text)

    def test_actual_runners_have_distinct_lazy_raw_first_finalizers(self):
        text = (READER.ROOT / "android" / "core" / "data" / "build.gradle.kts").read_text()
        self.assertIn('if (name == "testDebugUnitTest") {', text)
        self.assertIn('finalizedBy(retainMessagingRoomRawEvidence)', text)
        self.assertIn('if (name == "test") {', text)
        self.assertIn('finalizedBy(retainMessagingServicesRawEvidence)', text)
        self.assertIn('rootProject.project(":core:services").tasks.withType<Test>().configureEach', text)
        self.assertIn('"raw/services-completion"', text)
        self.assertIn('"raw/room-completion"', text)
        self.assertIn('"validated"', text)
        self.assertNotIn('tasks.named("testDebugUnitTest")', text)

    def test_raw_scope_preserves_all_required_capture_contexts_and_actual_gradle_assertions(self):
        text = (READER.ROOT / "android" / "core" / "data" / "build.gradle.kts").read_text()
        self.assertIn('forwardedInvocation || verifierSelected || servicesRunnerSelected || directlyRequested', text)
        self.assertIn('gradle.taskGraph.hasTask(":core:data:verifyMessagingTests")', text)
        self.assertIn('gradle.taskGraph.hasTask(":core:services:test")', text)
        self.assertIn("it.substringAfterLast(':') == rawTaskName", text)
        self.assertEqual(2, text.count('messagingRawCaptureSelected(name)'))
        self.assertIn('check(!messagingRawCaptureRequired(false, false, false, false))', text)
        for context in ((True, False, False, False), (False, True, False, False),
                        (False, False, True, False), (False, False, False, True),
                        (True, True, True, True)):
            flags = ', '.join(str(value).lower() for value in context)
            self.assertIn(f'check(messagingRawCaptureRequired({flags}))', text)
        self.assertIn('WP208_HOOK_SCOPE_ASSERTIONS|6', text)
        self.assertIn('throw GradleException("WP-208 requires the actual forwarded meshCliInvocationFile', text)
        self.assertNotIn('wp203_ci', text)

    def test_runner_progress_is_explicit_without_modifying_frozen_services_build(self):
        text = (READER.ROOT / "android" / "core" / "data" / "build.gradle.kts").read_text()
        self.assertIn('WP208_RUNNER_START|', text)
        self.assertIn('WP208_ROOM_CASE_START|', text)
        self.assertIn('WP208_ROOM_CASE_END|', text)
        self.assertIn('WP208_SERVICES_CASE_START|', text)
        self.assertIn('WP208_SERVICES_CASE_END|', text)

    def test_original_and_native_test_wrappers_always_close_owned_fixtures_on_failure(self):
        text = (READER.ROOT / READER.SERVICES / READER.MESSAGING / "MessagingTestSupport.kt").read_text()
        self.assertEqual(2, text.count('{ runMessagingCase(assertion) }'))
        self.assertIn('fixtureJob = SupervisorJob(test.coroutineContext[Job])', text)
        self.assertIn('finally { fixtureJob.cancelAndJoin() }', text)
        self.assertIn('finally {', text)
        self.assertIn('prior.addSuppressed(failure)', text)

    def test_pollers_and_room_generations_have_explicit_failure_path_release(self):
        text = (READER.ROOT / READER.SERVICES / READER.MESSAGING / "MessagingTestSupport.kt").read_text()
        self.assertIn('pollers.asReversed().forEach { release { it.close() } }', text)
        self.assertIn('queues.asReversed().forEach { release { it.shutdown() } }', text)
        room = (READER.ROOT / READER.ROOM).read_text()
        self.assertIn('generations.asReversed().forEach { release { it.stop() } }', room)
        self.assertIn('pollers.asReversed().forEach { release { it.close() } }', room)
        self.assertIn('first.addSuppressed(cause)', room)

    def test_verifier_depends_on_both_actual_tasks_and_preserves_incumbent_hooks(self):
        text = (READER.ROOT / "android" / "core" / "data" / "build.gradle.kts").read_text()
        self.assertIn('dependsOn(":core:services:test", "testDebugUnitTest")', text)
        self.assertIn('dependsOn(verifyMessagingTests, verifyMessagingEvidenceReaders)', text)
        self.assertIn('dependsOn(verifyPersistenceRepositoryTests)', text)
        self.assertIn('dependsOn(verifyBackupTests)', text)
        self.assertEqual(1, text.count('testImplementation(project(":core:services"))'))
        self.assertNotIn('implementation(project(":core:services"))\n', text.replace('testImplementation', 'TEST_EDGE'))
        self.assertNotIn('verifyDeviceSettingsTests', text)

    def test_two_file_bootstrap_and_local_data_lock_are_exact_frozen_producers(self):
        receipt = READER.load_json(Path(__file__).with_name("services-bootstrap-carry.json"))
        self.assertEqual(2, len(receipt["files"]))
        for row in receipt["files"]:
            path = READER.ROOT.joinpath(*row["path"].split("/"))
            raw = path.read_bytes().replace(b"\r\n", b"\n")
            self.assertEqual(row["carried_blob"], READER.git(READER.ROOT, "hash-object", "--path=" + row["path"], str(path)))
            self.assertEqual(row["lf_bytes"], len(raw))
            self.assertEqual(row["lf_sha256"], READER.hashlib.sha256(raw).hexdigest())
        lock = receipt["data_local_lock"]
        self.assertEqual(lock["blob"], READER.git(READER.ROOT, "hash-object", str(READER.ROOT.joinpath(*lock["path"].split("/")))))

    def test_missing_full_module_xml_fails(self):
        (self.output / "junit" / "data" / "TEST-fixture.xml").unlink()
        self.rejected()

    def test_zero_test_success_xml_is_not_evidence(self):
        self.write_suite("services", [])
        self.save_snapshot(refresh_reports=True)
        self.rejected()

    def test_failure_counter_fails_the_entire_module(self):
        self.write_suite("services", [("Example::original()", READER.PACKAGE + "ExampleTest")], failures="1")
        self.save_snapshot(refresh_reports=True)
        self.rejected()

    def test_hidden_failure_node_cannot_hide_behind_zero_aggregate(self):
        path = self.output / "junit" / "services" / "TEST-fixture.xml"
        root = ET.fromstring(path.read_bytes())
        ET.SubElement(root.find("testcase"), "failure")
        path.write_bytes(ET.tostring(root))
        self.save_snapshot(refresh_reports=True)
        self.rejected()

    def test_skipped_mandatory_case_fails(self):
        path = self.output / "junit" / "services" / "TEST-fixture.xml"
        root = ET.fromstring(path.read_bytes())
        ET.SubElement(root.find("testcase"), "skipped")
        path.write_bytes(ET.tostring(root))
        self.save_snapshot(refresh_reports=True)
        self.rejected()

    def test_error_counter_and_bad_discovery_counter_fail(self):
        path = self.output / "junit" / "services" / "TEST-fixture.xml"
        root = ET.fromstring(path.read_bytes())
        root.set("errors", "1")
        root.set("tests", "3")
        path.write_bytes(ET.tostring(root))
        self.save_snapshot(refresh_reports=True)
        self.rejected()

    def test_duplicate_assertion_identity_fails(self):
        self.write_suite("services", [("Example::original()", READER.PACKAGE + "ExampleTest"),
                                      ("Example::original()", READER.PACKAGE + "ExampleTest")])
        self.save_snapshot(refresh_reports=True)
        self.rejected()

    def test_duplicate_display_identity_across_classes_fails(self):
        self.write_suite("services", [("Example::original()", READER.PACKAGE + "ExampleTest"),
                                      ("WP-208::native", READER.PACKAGE + "NativeTest"),
                                      ("WP-208::native", READER.PACKAGE + "OtherTest")])
        self.save_snapshot(refresh_reports=True)
        self.rejected()

    def test_absent_or_extra_owned_case_fails(self):
        changed = copy.deepcopy(self.accounting)
        changed["native_regressions"]["WP-208::missing"] = {}
        with self.assertRaises(ValueError):
            READER.validate_capture(self.output, changed)

    def test_other_services_owners_are_retained_but_not_source_credit(self):
        self.write_suite("services", [("Example::original()", READER.PACKAGE + "ExampleTest"),
                                      ("WP-208::native", READER.PACKAGE + "NativeTest"),
                                      ("WP-218::other", "com.meshcoreone.android.core.services.content.OtherTest")])
        self.save_snapshot(refresh_reports=True)
        result = self.validate()
        self.assertEqual(2, result["counts"]["messaging"]["discovered"])
        self.assertEqual(3, result["counts"]["full_services"]["discovered"])

    def test_lower_native_floor_is_rejected(self):
        self.write_suite("data", [("room", READER.ROOM_CLASS)])
        self.save_snapshot(refresh_reports=True)
        self.rejected()

    def test_changed_raw_bytes_are_rejected_before_case_validation(self):
        path = self.output / "junit" / "services" / "TEST-fixture.xml"
        path.write_bytes(path.read_bytes() + b"\n")
        self.rejected()

    def test_unrecorded_report_is_rejected(self):
        path = self.output / "junit" / "services" / "TEST-extra.xml"
        path.write_text('<testsuite tests="1" failures="0" errors="0" skipped="0"><testcase name="extra" classname="extra"/></testsuite>')
        self.rejected()

    def test_missing_committed_input_and_mismatched_blob_are_rejected(self):
        self.snapshot["input_blobs"][0]["matches_head"] = False
        self.save_snapshot()
        self.rejected()

    def test_unknown_capture_identity_cannot_be_relabelled(self):
        self.snapshot["work_package"] = "WP-218"
        self.save_snapshot()
        self.rejected()

    def test_stale_head_wrong_host_and_missing_invocation_fail_closed(self):
        for key, value in (("host", "windows"), ("stage", "protocol")):
            old = self.invocation[key]
            self.invocation[key] = value
            self.save_invocation(); self.save_snapshot(); self.rejected()
            self.invocation[key] = old
        self.invocation["identity"]["binding"]["head_sha"] = "c" * 40
        self.save_invocation(); self.save_snapshot(); self.rejected()

    def test_missing_run_id_is_not_historical_or_local_acceptance(self):
        self.invocation["identity"]["run_id"] = 0
        self.save_invocation(); self.save_snapshot(); self.rejected()

    def test_absent_invocation_metadata_never_becomes_messaging_success(self):
        self.snapshot["invocation"] = None
        self.save_snapshot()
        self.rejected()

    def test_different_valid_base_sha_is_not_the_authorized_base(self):
        self.invocation["identity"]["binding"]["base_sha"] = "d" * 40
        self.save_invocation(); self.save_snapshot(); self.rejected()

    def test_different_positive_run_or_attempt_cannot_replace_the_captured_identity(self):
        for field, value in (("run_id", 124), ("run_attempt", 2)):
            old = self.invocation["identity"][field]
            self.invocation["identity"][field] = value
            self.save_invocation(); self.save_snapshot(); self.rejected()
            self.invocation["identity"][field] = old

    def test_unsafe_xml_declarations_and_malformed_xml_fail(self):
        path = self.output / "junit" / "services" / "TEST-fixture.xml"
        for raw in (b'<!DOCTYPE testsuite [<!ENTITY unsafe "x">]><testsuite/>', b"<testsuite"):
            path.write_bytes(raw)
            self.save_snapshot(refresh_reports=True)
            self.rejected()

    def test_disabled_source_assertion_and_duplicate_native_declarations_fail(self):
        directory = self.output / "declarations"
        directory.mkdir()
        path = directory / "Example.kt"
        for text in (
            '// AndroidOnly: WP-208 fixture\n@Disabled\nnative("case")',
            '// AndroidOnly: WP-208 fixture\nnative("case")\nnative("case")',
        ):
            path.write_text(text)
            with self.assertRaises(ValueError):
                READER.declarations(directory)

    def test_duplicate_json_fields_cannot_replace_a_capture_binding(self):
        path = self.output / "raw-capture.json"
        text = path.read_text()
        path.write_text(text[:-1] + ', "work_package": "WP-208"}')
        self.rejected()

    def test_record_path_traversal_cannot_read_outside_the_declared_artifact_root(self):
        directory = self.output / "isolated"
        directory.mkdir()
        path = directory / ".." / "invocation.json"
        with self.assertRaises(ValueError):
            READER.record(path, directory)

    def test_raw_failed_xml_and_changed_input_binding_are_retained_before_rejection(self):
        repo = self.output / "repo"
        repo.mkdir()
        source = repo / "android" / "core" / "services" / "src" / "main" / "Example.kt"
        source.parent.mkdir(parents=True)
        source.write_text("// synthetic capture fixture\n")
        lock = repo / "android" / "gradle" / "dependency-locks" / "core-services.lockfile"
        lock.parent.mkdir(parents=True)
        lock.write_text("# synthetic actual-path lock fixture\n")
        for args in (
            ["init", "--quiet"],
            ["add", "."],
            ["-c", "user.name=WP208 Reader Test", "-c", "user.email=wp208@example.invalid", "commit", "--quiet", "-m", "synthetic reader fixture"],
        ):
            subprocess.run(["git", "-C", str(repo), *args], check=True, capture_output=True)
        source.write_text("// changed synthetic capture fixture\n")
        report = repo / "android" / "core" / "services" / "build" / "test-results" / "test" / "TEST-failed.xml"
        report.parent.mkdir(parents=True)
        raw = b'<testsuite tests="1" failures="1" errors="0" skipped="0"><testcase name="failed" classname="fixture"><failure/></testcase></testsuite>'
        report.write_bytes(raw)
        output = self.output / "capture"
        metadata = READER.capture(output, self.output / "invocation.json", repo)
        self.assertEqual(raw, (output / "junit" / "services" / "TEST-failed.xml").read_bytes())
        self.assertTrue((output / "raw-capture.json").is_file())
        self.assertFalse(metadata["input_blobs"][0]["matches_head"])
        captured_lock = next(row for row in metadata["input_blobs"] if row["path"] == "android/gradle/dependency-locks/core-services.lockfile")
        self.assertTrue(captured_lock["matches_head"])
        self.assertEqual(READER.git(repo, "rev-parse", "HEAD:android/gradle/dependency-locks/core-services.lockfile"), captured_lock["git_blob"])
        self.assertEqual(READER.SOURCE, metadata["source_sha"])
        with self.assertRaises(ValueError):
            READER.validate_capture(output, self.accounting)


if __name__ == "__main__":
    unittest.main()
