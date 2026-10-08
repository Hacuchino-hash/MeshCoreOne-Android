"""AndroidOnly: WP-211 Reader regressions; synthetic XML never counts as native service evidence."""

import copy
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

import collect_evidence as reader
import verify_producers as producers
from controller.errors import PortError


class JUnitReaderTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="wp211-reader-")
        self.root = Path(self.temporary.name)

    def tearDown(self):
        self.temporary.cleanup()

    def report(self, body, counters='tests="1" failures="0" errors="0" skipped="0"'):
        raw = f'<testsuite {counters}>{body}</testsuite>'.encode()
        (self.root / "TEST-fixture.xml").write_bytes(raw)
        return raw

    def case(self, name="case", children=""):
        return f'<testcase classname="com.meshcoreone.android.core.services.device.Fixture" name="{name}">{children}</testcase>'

    def test_positive_complete_xml_is_counted_by_actual_nodes(self):
        self.report(self.case())
        self.assertEqual(1, len(reader.junit(self.root, "com.meshcoreone.android.core.services.")))

    def test_missing_directory_fails(self):
        with self.assertRaisesRegex(ValueError, "Missing complete"):
            reader.junit(self.root, "com.meshcoreone.android.core.services.")

    def test_zero_suite_fails(self):
        self.report("", 'tests="0" failures="0" errors="0" skipped="0"')
        with self.assertRaisesRegex(ValueError, "Malformed/zero"):
            reader.junit(self.root, "com.meshcoreone.android.core.services.")

    def test_counter_mismatch_fails(self):
        self.report(self.case(), 'tests="2" failures="0" errors="0" skipped="0"')
        with self.assertRaisesRegex(ValueError, "counter/testcase"):
            reader.junit(self.root, "com.meshcoreone.android.core.services.")

    def test_failed_error_and_skipped_counters_fail(self):
        for field in ("failures", "errors", "skipped"):
            with self.subTest(field=field):
                values = {"tests": 1, "failures": 0, "errors": 0, "skipped": 0, field: 1}
                counters = " ".join(f'{key}="{value}"' for key, value in values.items())
                self.report(self.case(), counters)
                with self.assertRaisesRegex(ValueError, "Failed/error/skipped"):
                    reader.junit(self.root, "com.meshcoreone.android.core.services.")

    def test_hidden_failure_error_and_skip_nodes_fail(self):
        for element in ("failure", "error", "skipped"):
            with self.subTest(element=element):
                self.report(self.case(children=f"<{element}/>"))
                with self.assertRaisesRegex(ValueError, "Hidden unsuccessful"):
                    reader.junit(self.root, "com.meshcoreone.android.core.services.")

    def test_duplicate_names_in_the_same_class_fail(self):
        self.report(self.case() + self.case(), 'tests="2" failures="0" errors="0" skipped="0"')
        with self.assertRaisesRegex(ValueError, "duplicate"):
            reader.junit(self.root, "com.meshcoreone.android.core.services.")

    def test_foreign_class_and_missing_case_name_fail(self):
        for body in (
            '<testcase classname="another.module.Test" name="case"/>',
            '<testcase classname="com.meshcoreone.android.core.services.device.Fixture"/>',
        ):
            with self.subTest(body=body):
                self.report(body)
                with self.assertRaisesRegex(ValueError, "Foreign/duplicate/missing"):
                    reader.junit(self.root, "com.meshcoreone.android.core.services.")

    def test_xml_entities_and_doctype_fail_before_parsing(self):
        raw = b'<!DOCTYPE testsuite [<!ENTITY x "injected">]><testsuite/>'
        (self.root / "TEST-fixture.xml").write_bytes(raw)
        with self.assertRaisesRegex(ValueError, "Unsafe JUnit"):
            reader.junit(self.root, "com.meshcoreone.android.core.services.")

    def test_malformed_xml_is_not_a_success(self):
        (self.root / "TEST-fixture.xml").write_bytes(b"<testsuite")
        with self.assertRaises(reader.ET.ParseError):
            reader.junit(self.root, "com.meshcoreone.android.core.services.")

    def test_oversized_reports_fail_without_lowering_the_bound(self):
        self.report(self.case())
        with patch.object(reader, "MAX_XML_BYTES", 1):
            with self.assertRaisesRegex(ValueError, "Unsafe/oversized"):
                reader.junit(self.root, "com.meshcoreone.android.core.services.")

    def test_failed_raw_bytes_and_basic_binding_survive_input_retention_failure(self):
        source = self.root / "repo"
        reports = source / "android" / "core" / "services" / "build" / "test-results" / "test"
        reports.mkdir(parents=True)
        raw = b'<testsuite tests="0" failures="1" errors="0" skipped="0"></testsuite>'
        (reports / "TEST-failed.xml").write_bytes(raw)
        output = self.root / "retained"
        with patch.object(reader, "ROOT", source), patch.object(reader, "OUT", source / "owned"), \
                patch.object(reader, "git", return_value="a" * 40), \
                patch.object(reader, "inputs", side_effect=ValueError("controlled missing compiled input")):
            with self.assertRaisesRegex(ValueError, "controlled missing"):
                reader.retain(output)
        self.assertEqual(raw, (output / "junit" / "services" / "TEST-failed.xml").read_bytes())
        metadata = json.loads((output / "retention.json").read_text())
        self.assertEqual("a" * 40, metadata["head_sha"])
        self.assertEqual(reader.LEASE, metadata["lease"])
        self.assertEqual(reader.RECOVERY_OWNER, metadata["recovery_owner_receipt"])
        self.assertEqual(1, len(metadata["raw_junit"]))
        self.assertTrue(metadata["missing_directories"])

    def test_malformed_execution_identity_cannot_prevent_raw_reports_and_input_maps_being_retained(self):
        source = self.root / "repo"
        owned = source / "owned"
        owned.mkdir(parents=True)
        (owned / "producer-freeze.json").write_text('{"scope":"synthetic reader fixture"}')
        manifest = source / "docs" / "android" / "port-manifest.json"
        manifest.parent.mkdir(parents=True)
        manifest.write_text('{"inventory":[]}')
        reports = source / "android" / "core" / "services" / "build" / "test-results" / "test"
        reports.mkdir(parents=True)
        raw = b'<testsuite tests="0" failures="1" errors="0" skipped="0"></testsuite>'
        (reports / "TEST-failed.xml").write_bytes(raw)
        invocation = self.root / "malformed-invocation.json"
        invocation.write_text("{malformed")
        input_map = {"owned.kt": {"git_blob": "a" * 40, "checkout_blob": "b" * 40, "sha256": "c" * 64}}
        output = self.root / "retained"
        with patch.object(reader, "ROOT", source), patch.object(reader, "OUT", owned), \
                patch.object(reader, "git", return_value="a" * 40), \
                patch.object(reader, "inputs", return_value=input_map):
            with self.assertRaisesRegex(PortError, "Malformed JSON"):
                reader.retain(output, invocation)
        self.assertEqual(raw, (output / "junit" / "services" / "TEST-failed.xml").read_bytes())
        self.assertEqual(invocation.read_bytes(), (output / "actual-invocation.json").read_bytes())
        metadata = json.loads((output / "retention.json").read_text())
        self.assertEqual(input_map, metadata["input_blobs"])
        self.assertEqual([], metadata["primary_inputs"])
        self.assertNotIn("execution", metadata)


class IdentityReaderTests(unittest.TestCase):
    def test_dynamic_or_unreadable_source_identity_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "Dynamic original"):
            reader.string('"${untrusted}"')
        with self.assertRaises(json.JSONDecodeError):
            reader.string('"unterminated')

    def test_actual_invocation_requires_linux_verify_and_current_immutable_bindings(self):
        with tempfile.TemporaryDirectory(prefix="wp211-invocation-") as directory:
            path = Path(directory) / "invocation.json"
            binding = {"repository": "cbattlegear/MeshCoreOne-Android", "work_package": "WP-003",
                       "head_sha": reader.git("rev-parse", "HEAD"),
                       "base_sha": "372fbc5866305e045025472ba555e7941873000f", "source_sha": reader.SOURCE,
                       "manifest_sha256": "a" * 64, "policy_revision": "b" * 64}
            value = {"schema_version": 1, "host": "linux", "stage": "verify",
                     "identity": {"binding": binding, "run_id": 1, "run_attempt": 1}}
            path.write_text(json.dumps(value))
            self.assertEqual(1, reader.invocation(path)["run_id"])
            self.assertEqual(value, reader.invocation(path)["actual_invocation"])
            for field, replacement in (("host", "windows"), ("stage", "assemble")):
                bad = {**value, field: replacement}
                path.write_text(json.dumps(bad))
                with self.assertRaises(ValueError):
                    reader.invocation(path)
            value["identity"]["binding"]["head_sha"] = "c" * 40
            path.write_text(json.dumps(value))
            with self.assertRaisesRegex(ValueError, "Stale/foreign"):
                reader.invocation(path)

    def test_current_coordinator_freeze_keeps_the_exact_reviewed_producer_and_test_blobs(self):
        freeze = reader.frozen_producers()
        self.assertEqual("98f64d2582e16e2e49c8c4fe79d5b7a239b970dd", freeze["reviewed_head_sha"])
        self.assertEqual(reader.FROZEN_PRODUCERS,
                         {entry["path"]: entry["git_blob"] for entry in freeze["files"]})

    def test_modified_frozen_producer_is_rejected_without_silently_advancing_the_freeze(self):
        with patch.object(reader, "git", return_value="0" * 40):
            with self.assertRaisesRegex(ValueError, "Reviewed producer blob drift"):
                reader.frozen_producers()

    def test_owned_partitions_derive_306_jvm_and_12_room_without_baseline_module_count_credit(self):
        _, families, native, room = reader.source_map()
        self.assertEqual(
            {
                "original_jvm_expanded": 213, "original_room_expanded": 7,
                "native_device_regressions": 93, "native_room_regressions": 5,
                "declared_device_jvm": 306, "declared_room": 12, "declared_owned_total": 318,
            },
            reader.partition_counts(families, native, room),
        )
        missing = room - {case["method"] for case in families.values() if case["runner"] == "room"}
        with self.assertRaisesRegex(ValueError, "Original Room methods missing"):
            reader.partition_counts(families, native, missing)
        dropped = {identity: case for identity, case in families.items() if case["runner"] != "room"}
        with self.assertRaisesRegex(ValueError, "Frozen original JVM/Room partition"):
            reader.partition_counts(dropped, native, room)

    def test_reviewed_close_regressions_are_required_without_changing_the_original_source_pin(self):
        _, families, native, _ = reader.source_map()
        self.assertTrue(reader.REQUIRED_CLOSE_CASES <= native.keys())
        self.assertEqual(2, len(reader.REQUIRED_CLOSE_CASES))
        self.assertEqual(93, reader.MIN_NATIVE_CASES)
        self.assertEqual(163, len(families))
        self.assertEqual("db14559b39d32322b06477c6ae676112f583db50", reader.SOURCE)
        self.assertEqual("7e2835bad2c03dfb5a088063655f9fc4dbafd00f", reader.RECEIPT_BASE)
        self.assertEqual("e3369a97bf3a1e19b801c8d69ca8abf171da432b", reader.BASE)

    def test_canonical_discovery_regressions_are_mandatory_in_addition_to_both_close_cases(self):
        _, _, native, _ = reader.source_map()
        self.assertEqual(3, len(reader.REQUIRED_DISCOVERY_CASES))
        self.assertTrue(reader.REQUIRED_DISCOVERY_CASES <= native.keys())
        self.assertTrue(reader.REQUIRED_CLOSE_CASES.isdisjoint(reader.REQUIRED_DISCOVERY_CASES))
        with patch.object(reader, "REQUIRED_DISCOVERY_CASES", reader.REQUIRED_DISCOVERY_CASES | {"WP-211::missing"}):
            with self.assertRaisesRegex(ValueError, "All canonical region discovery regressions"):
                reader.source_map()

    def test_historical_owner_cannot_be_relabelled_as_current_recovery_evidence(self):
        metadata = {
            "repository": "cbattlegear/MeshCoreOne-Android", "work_package": "WP-211",
            "source_sha": reader.SOURCE, "lease": reader.LEASE,
            "receipt_base_sha": reader.RECEIPT_BASE, "integration_base_sha": reader.BASE,
            "recovery_owner_receipt": {
                **reader.RECOVERY_OWNER, "native_session": "18dd9693-255c-4cb4-8154-86ee8040a8dc",
            },
        }
        with patch.object(reader, "load_json", return_value=metadata):
            with self.assertRaisesRegex(ValueError, "Retained recovery owner receipt drift"):
                reader.validate_retained(Path("synthetic-retention"))


class InvocationReaderTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.history = tempfile.TemporaryDirectory(prefix="wp211-invocation-history-")
        cls.repository = Path(cls.history.name)
        cls.git("init", "--quiet")
        cls.git("config", "user.name", "Reader fixture")
        cls.git("config", "user.email", "reader-fixture@example.invalid")
        for name in ("port-manifest.json", "automation-policy.json", "not-ported.json", "reference-amendments.json"):
            source = reader.ROOT / "docs" / "android" / name
            target = cls.repository / "docs" / "android" / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(source.read_bytes())
        cls.revisions = {"manifest_sha256": "a" * 64, "policy_revision": "b" * 64}
        cls.git("-c", "commit.gpgsign=false", "commit", "--quiet", "--allow-empty", "-m", "before baseline")
        cls.before = cls.git("rev-parse", "HEAD")
        cls.git("-c", "commit.gpgsign=false", "commit", "--quiet", "--allow-empty", "-m", "baseline")
        cls.baseline = cls.git("rev-parse", "HEAD")
        cls.git("-c", "commit.gpgsign=false", "commit", "--quiet", "--allow-empty", "-m", "later base")
        cls.later = cls.git("rev-parse", "HEAD")
        cls.git("-c", "commit.gpgsign=false", "commit", "--quiet", "--allow-empty", "-m", "actual head")
        cls.head = cls.git("rev-parse", "HEAD")
        cls.diverged = cls.git("-c", "commit.gpgsign=false", "commit-tree", f"{cls.later}^{{tree}}",
                               "-p", cls.later, "-m", "advanced integration base")
        cls.unrelated = cls.git("-c", "commit.gpgsign=false", "commit-tree", "HEAD^{tree}", "-m", "unrelated root")
        cls.noncommit = cls.git("rev-parse", "HEAD^{tree}")

    @classmethod
    def tearDownClass(cls):
        cls.history.cleanup()

    @classmethod
    def git(cls, *args):
        return subprocess.check_output(["git", "-C", str(cls.repository), *args]).decode().strip()

    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="wp211-invocation-record-")
        self.root = Path(self.temporary.name)
        self.path = self.root / "invocation.json"
        self.local = {"schema_version": 1, "host": "linux", "stage": "verify", "identity": None}
        self.hosted = {**self.local, "identity": {
            "binding": {"repository": "cbattlegear/MeshCoreOne-Android", "work_package": "WP-003",
                        "base_sha": self.later, "head_sha": self.head, "source_sha": reader.SOURCE,
                        **self.revisions},
            "run_id": 17, "run_attempt": 2,
        }}
        self.addCleanup(self.temporary.cleanup)
        self.enterContext(patch.object(reader, "ROOT", self.repository))
        self.enterContext(patch.object(reader, "BASE", self.baseline))

    def invoke(self, value):
        self.path.write_text(json.dumps(value), encoding="utf-8")
        return reader.invocation(self.path)

    def retention(self, value):
        execution = self.invoke(value)
        (self.root / "actual-invocation.json").write_bytes(self.path.read_bytes())
        metadata = {
            "repository": "cbattlegear/MeshCoreOne-Android", "work_package": "WP-211",
            "source_sha": reader.SOURCE, "lease": reader.LEASE,
            "receipt_base_sha": reader.RECEIPT_BASE, "integration_base_sha": reader.BASE,
            "recovery_owner_receipt": reader.RECOVERY_OWNER, "head_sha": self.head,
            "invocation_file": str(self.path), "execution": execution,
            "input_blobs": {}, "producer_freeze": {}, "missing_directories": ["synthetic"],
        }
        (self.root / "retention.json").write_text(json.dumps(metadata), encoding="utf-8")

    def test_explicit_null_is_local_only_with_current_head_and_no_hosted_ids(self):
        result = self.invoke(self.local)
        self.assertEqual(self.head, result["head_sha"])
        self.assertEqual(self.local, result["actual_invocation"])
        self.assertIsNone(result["run_id"])
        self.assertIsNone(result["run_attempt"])
        self.assertEqual(reader.invocation(None)["authority"], result["authority"])
        self.assertNotIn("actual_root_binding", result)

    def test_process_manifest_and_policy_do_not_rebind_feature_execution(self):
        value = copy.deepcopy(self.hosted)
        value["identity"]["binding"].update(
            manifest_sha256="c" * 64, policy_revision="d" * 64,
        )
        self.assertEqual(17, self.invoke(value)["run_id"])

    def test_process_policy_change_or_absence_does_not_invalidate_feature_execution(self):
        policy = self.repository / "docs/android/automation-policy.json"
        original = policy.read_bytes()
        try:
            changed = json.loads(original)
            changed["repository"] = "foreign/repository"
            policy.write_text(json.dumps(changed), encoding="utf-8")
            self.assertEqual(17, self.invoke(self.hosted)["run_id"])
            policy.unlink()
            self.assertEqual(17, self.invoke(self.hosted)["run_id"])
        finally:
            policy.write_bytes(original)

    def test_missing_identity_is_not_a_local_fallback(self):
        value = {key: item for key, item in self.local.items() if key != "identity"}
        with self.assertRaisesRegex(ValueError, "Malformed declared"):
            self.invoke(value)

    def test_only_exact_typed_linux_verify_contract_is_accepted(self):
        for field, replacement in (
            ("schema_version", True), ("schema_version", "1"), ("schema_version", 2),
            ("host", "windows"), ("host", None), ("stage", "assemble"), ("stage", None),
        ):
            with self.subTest(field=field, replacement=replacement), self.assertRaises(ValueError):
                self.invoke({**self.local, field: replacement})
        for value in (None, [], 1, {**self.local, "run_id": 17}):
            with self.subTest(value=value), self.assertRaises(ValueError):
                self.invoke(value)

    def test_nonnull_malformed_identities_never_fall_back_to_local(self):
        for identity in (False, 0, "", [], {}, {"binding": None},
                         {**self.hosted["identity"], "unexpected": True}):
            with self.subTest(identity=identity), self.assertRaises(ValueError):
                self.invoke({**self.local, "identity": identity})

    def test_hosted_ids_must_both_be_present_positive_integers(self):
        for field in ("run_id", "run_attempt"):
            for replacement in (None, False, True, 0, -1, "1", 1.0):
                value = copy.deepcopy(self.hosted)
                value["identity"][field] = replacement
                with self.subTest(field=field, replacement=replacement), self.assertRaises(ValueError):
                    self.invoke(value)
            value = copy.deepcopy(self.hosted)
            del value["identity"][field]
            with self.subTest(missing=field), self.assertRaises(ValueError):
                self.invoke(value)

    def test_existing_baseline_ancestor_and_diverged_integration_head_are_valid_hosted_bases(self):
        for base in (self.baseline, self.later, self.diverged):
            value = copy.deepcopy(self.hosted)
            value["identity"]["binding"]["base_sha"] = base
            with self.subTest(base=base):
                result = self.invoke(value)
                self.assertEqual(base, result["base_sha"])
                self.assertEqual(value, result["actual_invocation"])
                self.assertEqual(value["identity"]["binding"], result["actual_root_binding"])

    def test_base_must_be_a_full_typed_immutable_sha(self):
        for base in (None, 123, self.later[:12], self.later.upper(), "z" * 40):
            value = copy.deepcopy(self.hosted)
            value["identity"]["binding"]["base_sha"] = base
            with self.subTest(base=base), self.assertRaises(PortError):
                self.invoke(value)

    def test_missing_commit_and_existing_noncommit_base_are_rejected(self):
        value = copy.deepcopy(self.hosted)
        value["identity"]["binding"]["base_sha"] = "f" * 40
        with self.assertRaises(subprocess.CalledProcessError):
            self.invoke(value)
        value["identity"]["binding"]["base_sha"] = self.noncommit
        with self.assertRaisesRegex(ValueError, "existing commit"):
            self.invoke(value)

    def test_unrelated_and_prebaseline_commits_are_rejected(self):
        for base in (self.unrelated, self.before):
            value = copy.deepcopy(self.hosted)
            value["identity"]["binding"]["base_sha"] = base
            with self.subTest(base=base), self.assertRaises((ValueError, subprocess.CalledProcessError)):
                self.invoke(value)

    def test_actual_head_must_descend_from_the_integration_baseline(self):
        with patch.object(reader, "git", wraps=reader.git) as actual_git:
            value = copy.deepcopy(self.hosted)
            value["identity"]["binding"]["head_sha"] = self.before
            actual_git.side_effect = lambda *args: self.before if args == ("rev-parse", "HEAD") else self.git(*args)
            with self.assertRaisesRegex(ValueError, "Actual HEAD is outside"):
                self.invoke(value)

    def test_foreign_stale_and_immutable_binding_drift_are_rejected(self):
        for field, replacement in (
            ("repository", "another/repository"), ("work_package", "WP-211"),
            ("head_sha", self.before), ("source_sha", "c" * 40),
        ):
            value = copy.deepcopy(self.hosted)
            value["identity"]["binding"][field] = replacement
            with self.subTest(field=field), self.assertRaises(ValueError):
                self.invoke(value)

    def test_malformed_hosted_binding_cannot_be_reinterpreted_as_local(self):
        for binding in (None, [], {}, {**self.hosted["identity"]["binding"], "unexpected": 1}):
            value = copy.deepcopy(self.hosted)
            value["identity"]["binding"] = binding
            with self.subTest(binding=binding), self.assertRaises((ValueError, PortError)):
                self.invoke(value)

    def test_retained_local_and_hosted_invocations_reach_existing_raw_report_guards(self):
        for value in (self.local, self.hosted):
            self.retention(value)
            with self.subTest(identity=value["identity"]), \
                    patch.object(reader, "inputs", return_value={}), \
                    patch.object(reader, "frozen_producers", return_value={}):
                with self.assertRaisesRegex(ValueError, "Missing actual mandatory JUnit"):
                    reader.validate_retained(self.root, self.path)

    def test_missing_retained_invocation_cannot_be_replaced_by_external_input(self):
        self.retention(self.local)
        (self.root / "actual-invocation.json").unlink()
        with self.assertRaisesRegex(ValueError, "Missing/linked retained"):
            reader.validate_retained(self.root, self.path)

    def test_tampered_retained_invocation_is_rejected_even_when_external_original_is_supplied(self):
        self.retention(self.hosted)
        changed = copy.deepcopy(self.hosted)
        changed["identity"]["run_attempt"] += 1
        (self.root / "actual-invocation.json").write_text(json.dumps(changed), encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "Retained execution identity drift"):
            reader.validate_retained(self.root, self.path)

    def test_different_valid_external_invocation_cannot_replace_the_retained_record(self):
        self.retention(self.hosted)
        for change in ("run_attempt", "base_sha"):
            changed = copy.deepcopy(self.hosted)
            if change == "base_sha":
                changed["identity"]["binding"]["base_sha"] = self.baseline
            else:
                changed["identity"]["run_attempt"] += 1
            self.invoke(changed)
            with self.subTest(change=change), self.assertRaisesRegex(ValueError, "Provided invocation differs"):
                reader.validate_retained(self.root, self.path)

    def test_duplicate_json_keys_do_not_hide_a_local_identity(self):
        self.path.write_text(
            '{"schema_version":1,"host":"linux","stage":"verify","identity":{},"identity":null}',
            encoding="utf-8",
        )
        with self.assertRaisesRegex(PortError, "Duplicate JSON key"):
            reader.invocation(self.path)


class NativeHookTests(unittest.TestCase):
    def test_the_admitted_hook_runs_both_actual_test_tasks_and_keeps_earlier_hooks(self):
        text = (reader.ROOT / "android" / "core" / "data" / "build.gradle.kts").read_text(encoding="utf-8")
        self.assertIn('dependsOn(":core:services:test", "testDebugUnitTest", retainDeviceSettingsRoomEvidence)', text)
        for hook in ("verifyBackupTests", "verifyPersistenceRepositoryTests", "verifyDeviceSettingsTests"):
            self.assertIn(f'rootProject.tasks.named("verifyScaffoldTests") {{ dependsOn({hook}) }}', text)
            self.assertIn(f'tasks.named("check") {{ dependsOn({hook}) }}', text)
        self.assertEqual(1, text.count('testImplementation(project(":core:services"))'))
        self.assertNotIn('\n    implementation(project(":core:services"))', text)
        self.assertNotIn('project(":core:services").dependencies', text)

    def test_raw_room_completion_is_a_finalizer_and_does_not_assert_success(self):
        text = (reader.ROOT / "android" / "core" / "data" / "build.gradle.kts").read_text(encoding="utf-8")
        self.assertIn('tasks.withType<Test>().configureEach {\n'
                      '    if (name == "testDebugUnitTest") finalizedBy(retainDeviceSettingsRoomEvidence)\n}', text)
        self.assertNotIn('tasks.named("testDebugUnitTest")', text)
        self.assertIn('mustRunAfter(":core:services:test", "testDebugUnitTest")', text)
        self.assertIn('"--retain-only"', text)
        self.assertIn('.resolve("room-completion-$deviceSettingsAttempt")', text)
        self.assertIn('.resolve("full-$deviceSettingsAttempt")', text)
        self.assertIn('deviceSettingsInvocation.map { File(it).parentFile.resolve("wp211-native")', text)
        self.assertIn("import java.io.File", text)
        self.assertNotIn("java.io.File(", text)

    def test_actual_services_runner_failure_also_retains_raw_junit_without_editing_the_services_build(self):
        text = (reader.ROOT / "android" / "core" / "data" / "build.gradle.kts").read_text(encoding="utf-8")
        self.assertIn('project(":core:services").tasks.withType<Test>().configureEach {\n'
                      '    if (name == "test") finalizedBy(retainDeviceSettingsRoomEvidence)\n}', text)
        self.assertIn('mustRunAfter(":core:services:test", "testDebugUnitTest")', text)
        self.assertNotIn('project(":core:services").tasks.named("test")', text)
        self.assertNotIn('project(":core:services").dependencies', text)

    def test_standalone_linux_executor_forwards_exact_actual_invocation_and_private_retention_path(self):
        text = (reader.OUT / "run_linux_verification.py").read_text(encoding="utf-8")
        self.assertIn('tasks = [":core:services:test", ":core:data:testDebugUnitTest"]', text)
        self.assertIn('"--continue", "--no-daemon"', text)
        self.assertIn('"-PmeshCliInvocationFile=" + str(invocation_file)', text)
        self.assertIn('"-Pwp211EvidenceDirectory=" + str(output / "gradle-retention")', text)
        self.assertLess(text.index('retain(output / "raw", invocation_file)'),
                        text.index('if outcome["exit_code"] != 0'))
        self.assertLess(text.index('retain(output / "raw", invocation_file)'),
                        text.index('proof = validate_retained'))


class ProducerReaderTests(unittest.TestCase):
    def test_every_frozen_radio_row_matches_all_native_fields(self):
        self.assertEqual({"all": 28, "repeatPresets": 3}, producers.check_radio_catalog())

    def test_changed_radio_values_and_availability_memberships_fail(self):
        path = producers.NATIVE / "RadioPresets.kt"
        native = path.read_text(encoding="utf-8")
        mutations = (
            ('"eu-narrow", "EU/UK (Narrow)"', '"eu-narrow", "Changed name"'),
            ('RadioRegion.OCEANIA, 915.800, 250.0, 10u, 5u', 'RadioRegion.OCEANIA, 915.801, 250.0, 10u, 5u'),
            ('areas("US", "US-PA", "US-NJ")', 'areas("US", "US-PA", "US-DE")'),
            ('"nz-lr", "New Zealand (Gisborne)"', '"changed-id", "New Zealand (Gisborne)"'),
            ('repeatSectionHeader = "EU/Asia"', 'repeatSectionHeader = "Changed"'),
        )
        for before, after in mutations:
            self.assertIn(before, native)
            with self.subTest(field=before), patch.object(Path, "read_text", return_value=native.replace(before, after, 1)):
                with self.assertRaisesRegex(ValueError, "radio tuple/name/availability"):
                    producers.check_radio_catalog()

    def test_catalog_argument_parser_preserves_nested_and_quoted_commas(self):
        self.assertEqual(
            ['country: "US"', 'listOf("a,b", nested("x"))', "pathHashSize = 3"],
            producers.split_arguments('country: "US", listOf("a,b", nested("x")), pathHashSize = 3,'),
        )
        for malformed in ('country: "US', 'listOf("x"', "nested())"):
            with self.subTest(value=malformed), self.assertRaisesRegex(ValueError, "Malformed catalog"):
                producers.split_arguments(malformed)

    def test_catalog_unknown_tier_and_duplicate_named_fields_are_errors(self):
        with self.assertRaisesRegex(ValueError, "Unknown availability tier"):
            producers.availability('missingTier("US")', False)
        with self.assertRaisesRegex(ValueError, "Duplicate source preset"):
            producers.preset_row(['id: "x"', 'id: "y"'], True)


if __name__ == "__main__":
    unittest.main()
