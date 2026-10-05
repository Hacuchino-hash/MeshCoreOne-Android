# AndroidOnly: WP-301 Auxiliary workflow/command admission is fail-closed and cannot substitute for native verification.
import importlib.util
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

path = Path(__file__).with_name("generate_consumer_locks.py")
spec = importlib.util.spec_from_file_location("wp301_generation", path)
helper = importlib.util.module_from_spec(spec)
spec.loader.exec_module(helper)


class GenerationTest(unittest.TestCase):
    def workflow(self):
        return helper.WORKFLOW.read_text(encoding="utf8")

    def test_exact_workflow_uses_existing_boundary_validation(self):
        self.assertFalse(helper.validate_workflow(self.workflow())["mandatory_ci_replaced"])

    def test_privileged_trigger_or_token_is_rejected(self):
        for original, replacement in (("pull_request:", "pull_request_target:"), ("contents: read", "contents: write"),
            ("persist-credentials: false", "persist-credentials: true")):
            with self.assertRaises(helper.PortError):
                helper.validate_workflow(self.workflow().replace(original, replacement))

    def test_changed_runner_budget_action_and_python_are_rejected(self):
        for original, replacement in (("ubuntu-24.04", "ubuntu-latest"), ("timeout-minutes: 20", "timeout-minutes: 60"),
            ("11bd71901bbe5b1630ceea73d27597364c9af683", "v4"), ('"3.12.4"', '"3.12"')):
            with self.assertRaises(helper.PortError):
                helper.validate_workflow(self.workflow().replace(original, replacement))

    def test_extra_stage_unbounded_upload_or_different_scope_is_rejected(self):
        for original, replacement in (("number == 24", "number == 25"), ("wp301-generation/proposal", "wp301-generation"),
            ("--self-test --workflow-check", "--run --write-locks")):
            with self.assertRaises(helper.PortError):
                helper.validate_workflow(self.workflow().replace(original, replacement))

    def test_command_runs_only_declared_targeted_resolver(self):
        command = helper.command({"host": "linux", "private_root": "/isolated/private"})
        self.assertEqual(1, command.count(helper.TASK))
        self.assertIn("--write-locks", command)
        self.assertEqual("strict", command[command.index("--dependency-verification") + 1])
        self.assertNotIn("resolveScaffoldDependencies", command)
        self.assertFalse(any(":test" in argument or ":assemble" in argument for argument in command))
        with self.assertRaises(helper.PortError):
            helper.command({"host": "windows", "private_root": "/isolated/private"})

    def test_clean_exact_ten_changes_are_required(self):
        paths = sorted(f"android/gradle/dependency-locks/{name.removeprefix(':').replace(':', '-')}.lockfile"
            for name in helper.locks.MODULES)
        with patch.object(helper, "git", return_value=("\n".join(paths)).encode()):
            self.assertEqual(paths, helper.check_changed_paths(set()))
            with self.assertRaises(helper.PortError):
                helper.check_changed_paths({"unowned"})
        for bad in (paths[:-1], paths + ["android/gradle/verification-metadata.xml"]):
            with patch.object(helper, "git", return_value=("\n".join(bad)).encode()):
                with self.assertRaises(helper.PortError):
                    helper.check_changed_paths(set())

    def test_unknown_event_is_rejected_before_provisioning(self):
        with patch.dict(os.environ, {"GITHUB_EVENT_NAME": "workflow_dispatch"}):
            with self.assertRaises(helper.PortError):
                helper.identity()

    def test_unapproved_candidate_or_fork_is_rejected(self):
        with tempfile.TemporaryDirectory() as name:
            path = Path(name) / "event.json"
            for number, repository in ((25, "cbattlegear/MeshCoreOne-Android"), (24, "unapproved/fork")):
                path.write_text(json.dumps({"number": number, "pull_request": {"head": {
                    "ref": "cbattlegear-native-themes-and-identity", "repo": {"full_name": repository}}}}), encoding="utf8")
                with patch.dict(os.environ, {"GITHUB_EVENT_NAME": "pull_request", "GITHUB_EVENT_PATH": str(path)}):
                    with self.assertRaises(helper.PortError):
                        helper.identity()
