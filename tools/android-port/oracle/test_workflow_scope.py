import subprocess
import unittest
from pathlib import Path
from unittest.mock import patch

from oracle.workflow_scope import ALL_SCOPE_INPUTS, SCOPES, ZERO, classify, endpoints, evaluate

BASE = "a" * 40
HEAD = "b" * 40


class WorkflowScopeTests(unittest.TestCase):
    def test_unrelated_wp218_and_wp302_changes_schedule_no_auxiliary_chain(self):
        for paths in (
            ["android/feature/content/src/main/kotlin/ContentScreen.kt",
             "android/feature/location/src/test/kotlin/LocationTest.kt"],
            ["android/feature/navigation/src/main/kotlin/NavigationGraph.kt"],
        ):
            with self.subTest(paths=paths):
                result = classify(paths)
                self.assertFalse(result["codec"])
                self.assertFalse(result["backup"])
                self.assertEqual(result, {scope: False for scope in SCOPES})

    def test_codec_source_and_harness_schedule_codec(self):
        for path in (
            "MC1Services/Sources/MC1Services/Services/AppBackupEnvelope.swift",
            "tools/android-port/oracle/codec_harness.py",
        ):
            with self.subTest(path=path):
                self.assertTrue(classify([path])["codec"])

    def test_wp203_room_backup_and_harness_schedule_complete_backup_chain(self):
        for path in (
            "android/core/data/src/main/kotlin/com/meshcoreone/android/core/data/backup/AppBackupCodec.kt",
            "android/core/database/src/main/kotlin/com/meshcoreone/android/core/database/MeshDatabase.kt",
            "tools/android-port/oracle/wp203_interop.py",
            "docs/android/evidence/WP-203/WP203InteropTests.swift",
        ):
            with self.subTest(path=path):
                self.assertTrue(classify([path])["backup"])

    def test_protocol_only_change_schedules_protocol_only(self):
        result = classify([
            "android/core/protocol/src/main/kotlin/com/meshcoreone/android/core/protocol/bytes/Bytes.kt"
        ])
        self.assertEqual(result, {
            "helpers": False, "codec": False, "protocol": True, "backup": False,
        })

    def test_workflow_and_provenance_changes_schedule_all(self):
        for path in ALL_SCOPE_INPUTS:
            with self.subTest(path=path):
                self.assertEqual(classify([path]), {scope: True for scope in SCOPES})

    def test_event_endpoints_are_exact(self):
        self.assertEqual(
            endpoints("pull_request", {"pull_request": {
                "base": {"sha": BASE}, "head": {"sha": HEAD}
            }}),
            (BASE, HEAD),
        )
        self.assertEqual(
            endpoints("merge_group", {"merge_group": {"base_sha": BASE, "head_sha": HEAD}}),
            (BASE, HEAD),
        )
        self.assertEqual(endpoints("push", {"before": BASE, "after": HEAD}), (BASE, HEAD))
        self.assertIsNone(endpoints("workflow_dispatch", {}))

    def test_dispatch_runs_all_without_being_a_failure_fallback(self):
        result = evaluate(Path.cwd(), "workflow_dispatch", {})
        self.assertFalse(result["fail_safe"])
        self.assertEqual(result["scopes"], {scope: True for scope in SCOPES})

    def test_malformed_unknown_zero_push_and_diff_failure_run_all(self):
        events = (
            ("pull_request", {}),
            ("unknown", {}),
            ("push", {"before": ZERO, "after": HEAD}),
        )
        for name, event in events:
            with self.subTest(name=name):
                result = evaluate(Path.cwd(), name, event)
                self.assertTrue(result["fail_safe"])
                self.assertEqual(result["scopes"], {scope: True for scope in SCOPES})
        with patch("oracle.workflow_scope.changed_paths",
                   side_effect=subprocess.CalledProcessError(1, ["git", "diff"])):
            result = evaluate(Path.cwd(), "push", {"before": BASE, "after": HEAD})
        self.assertTrue(result["fail_safe"])
        self.assertEqual(result["scopes"], {scope: True for scope in SCOPES})
