import unittest
from unittest.mock import patch

from controller.errors import PortError
from controller.scaffold_scope import ZERO, classify, endpoints, evaluate


class ScaffoldScopeTests(unittest.TestCase):
    def test_focused_docs_and_swift_ui_skip_full_scaffold(self):
        self.assertFalse(classify(["docs/android/adr/005-fast-local-pre-push.md"]))
        self.assertFalse(classify(["MC1/Views/SettingsView.swift"]))

    def test_android_build_and_controller_changes_select_full_scaffold(self):
        for path in (
            "android/core/data/build.gradle.kts",
            "tools/android-port/controller/ci.py",
            "tools/android-port/tests/test_workflows.py",
        ):
            with self.subTest(path=path):
                self.assertTrue(classify([path]))

    def test_metadata_and_independently_owned_kotlin_suites_skip_full_scaffold(self):
        for path in (
            "docs/android/port-manifest.json",
            "docs/android/PORTING_PLAN.md",
            "docs/android/evidence/WP-211/README.md",
            "docs/android/deviations/WP-211.md",
            "android/core/protocol/src/test/kotlin/ProtocolTest.kt",
            "android/core/data/src/test/kotlin/com/meshcoreone/android/core/data/backup/BackupInteropTest.kt",
            "android/core/database/src/test/kotlin/DatabaseTest.kt",
        ):
            with self.subTest(path=path):
                self.assertFalse(classify([path]))
        self.assertTrue(classify([
            "android/core/protocol/src/test/kotlin/ProtocolTest.kt",
            "android/app/src/main/kotlin/App.kt",
        ]))

    def test_endpoints_and_fail_safe(self):
        base, head = "a" * 40, "b" * 40
        self.assertEqual(endpoints("pull_request", {"pull_request": {
            "base": {"sha": base}, "head": {"sha": head},
        }}), (base, head))
        self.assertEqual(endpoints("merge_group", {"merge_group": {
            "base_sha": base, "head_sha": head,
        }}), (base, head))
        self.assertEqual(endpoints("push", {"before": base, "after": head}), (base, head))
        self.assertIsNone(endpoints("workflow_dispatch", {}))
        with self.assertRaises(PortError):
            endpoints("push", {"before": ZERO, "after": head})
        with patch("controller.scaffold_scope.subprocess.run", side_effect=OSError("diff failed")):
            self.assertTrue(evaluate(".", "pull_request", {"pull_request": {
                "base": {"sha": base}, "head": {"sha": head},
            }})["full"])


if __name__ == "__main__":
    unittest.main()
