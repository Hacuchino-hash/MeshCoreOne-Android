import unittest
from unittest.mock import patch

from controller.errors import PortError
from controller.scaffold_scope import ZERO, classify, endpoints, evaluate


class ScaffoldScopeTests(unittest.TestCase):
    def test_focused_docs_and_swift_ui_skip_full_scaffold(self):
        self.assertFalse(classify(["docs/android/adr/005-fast-local-pre-push.md"]))
        self.assertFalse(classify(["MC1/Views/SettingsView.swift"]))

    def test_android_build_controller_and_evidence_select_full_scaffold(self):
        for path in (
            "android/core/data/build.gradle.kts",
            "tools/android-port/controller/ci.py",
            "tools/android-port/tests/test_workflows.py",
            "docs/android/evidence/WP-211/collect_evidence.py",
        ):
            with self.subTest(path=path):
                self.assertTrue(classify([path]))

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
