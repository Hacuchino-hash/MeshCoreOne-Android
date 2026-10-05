# AndroidOnly: WP-218 Auxiliary workflow/command admission is fail-closed and cannot substitute for native verification.
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

path = Path(__file__).with_name("generate_content_locks.py")
spec = importlib.util.spec_from_file_location("wp218_generation", path)
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
        for original, replacement in (("number == 25", "number == 24"), ("wp218-generation/proposal", "wp218-generation"),
            ("--self-test --workflow-check", "--run --write-locks"),
            ("--workflow-check", "--workflow-check; arbitrary-command")):
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

    def test_clean_only_admitted_lock_changes_are_allowed(self):
        paths = sorted(f"android/gradle/dependency-locks/{name.removeprefix(':').replace(':', '-')}.lockfile"
            for name in helper.locks.MODULES)
        def git(repo, *arguments):
            return ("\n".join(paths)).encode() if arguments[0] == "diff" else b""
        with patch.object(helper, "git", side_effect=git):
            self.assertEqual(paths, helper.check_changed_paths(set()))
            with self.assertRaises(helper.PortError):
                helper.check_changed_paths({"unowned"})
        for partial in ([], paths[:-1]):
            with patch.object(helper, "git", side_effect=[("\n".join(partial)).encode(), b""]):
                self.assertEqual(partial, helper.check_changed_paths(set()))
        for bad in (paths + ["android/gradle/verification-metadata.xml"], ["android/unowned.lockfile"]):
            with patch.object(helper, "git", return_value=("\n".join(bad)).encode()):
                with self.assertRaises(helper.PortError):
                    helper.check_changed_paths(set())

    def test_unchanged_generated_state_still_requires_full_delta_validation(self):
        with patch.object(helper, "git", return_value=b""):
            with patch.object(helper.locks, "check", return_value={"result": "fixture-exact-state"}) as validate:
                self.assertEqual(([], {"result": "fixture-exact-state"}), helper.verified_resolution(set()))
                validate.assert_called_once_with()
            with patch.object(helper.locks, "check", side_effect=ValueError("Actual additions are missing")):
                with self.assertRaises(ValueError):
                    helper.verified_resolution(set())

    def test_unknown_event_is_rejected_before_provisioning(self):
        with patch.dict(os.environ, {"GITHUB_EVENT_NAME": "workflow_dispatch"}):
            with self.assertRaises(helper.PortError):
                helper.identity()

    def test_unapproved_candidate_or_fork_is_rejected(self):
        with tempfile.TemporaryDirectory() as name:
            path = Path(name) / "event.json"
            for number, repository in ((24, "cbattlegear/MeshCoreOne-Android"), (25, "unapproved/fork")):
                path.write_text(json.dumps({"number": number, "pull_request": {"state": "open", "merged": False,
                    "title": "[WP-218] Safe content and location services", "base": {"ref": "main"}, "head": {
                    "ref": "cbattlegear-bookish-funicular", "repo": {"full_name": repository}}}}), encoding="utf8")
                with patch.dict(os.environ, {"GITHUB_EVENT_NAME": "pull_request", "GITHUB_EVENT_PATH": str(path)}):
                    with self.assertRaises(helper.PortError):
                        helper.identity()

    def test_closed_merged_or_wrong_base_candidate_is_rejected(self):
        with tempfile.TemporaryDirectory() as name:
            path = Path(name) / "event.json"
            for state, merged, base in (("closed", False, "main"), ("open", True, "main"), ("open", False, "other")):
                path.write_text(json.dumps({"number": 25, "pull_request": {"state": state, "merged": merged,
                    "title": "[WP-218] Safe content and location services", "base": {"ref": base}, "head": {
                    "ref": "cbattlegear-bookish-funicular",
                    "repo": {"full_name": "cbattlegear/MeshCoreOne-Android"}}}}), encoding="utf8")
                with patch.dict(os.environ, {"GITHUB_EVENT_NAME": "pull_request", "GITHUB_EVENT_PATH": str(path)}):
                    with self.assertRaises(helper.PortError):
                        helper.identity()

    def test_unknown_untracked_write_is_rejected(self):
        paths = sorted(f"android/gradle/dependency-locks/{name.removeprefix(':').replace(':', '-')}.lockfile"
            for name in helper.locks.MODULES)
        with patch.object(helper, "git", side_effect=[("\n".join(paths)).encode(), b"android/unowned.lockfile"]):
            with self.assertRaises(helper.PortError):
                helper.check_changed_paths(set())

    def test_verify_local_identity_rejects_stale_generator_or_test_helper(self):
        for stale_path in ("android/core/services/verification/generate_content_locks.py",
                "android/core/services/verification/test_generate_content_locks.py"):
            def read_bytes(self_path, stale_path=stale_path):
                posix = str(self_path).replace(os.sep, "/")
                return b"stale-local-bytes" if posix.endswith(stale_path) else b"same-bytes"
            with patch.object(helper, "git", return_value=b"same-bytes"), \
                    patch("subprocess.run"), patch.object(Path, "read_bytes", new=read_bytes):
                with self.assertRaises(helper.PortError):
                    helper.verify_local_identity({"binding": {"head_sha": "f" * 40}})

    def test_verify_local_identity_rejects_tampered_amendment_request_map(self):
        amendment_path = "docs/android/evidence/WP-218/dependency-amendment-request.json"

        def read_bytes(self_path):
            posix = str(self_path).replace(os.sep, "/")
            return b"tampered-local-bytes" if posix.endswith(amendment_path) else b"same-bytes"

        with patch.object(helper, "git", return_value=b"same-bytes"), \
                patch("subprocess.run"), patch.object(Path, "read_bytes", new=read_bytes):
            with self.assertRaises(helper.PortError):
                helper.verify_local_identity({"binding": {"head_sha": "f" * 40}})

    def test_verify_local_identity_rejects_baseline_not_ancestor_of_head(self):
        with patch.object(helper, "git", return_value=b"same-bytes"), \
                patch.object(Path, "read_bytes", return_value=b"same-bytes"), \
                patch("subprocess.run", side_effect=subprocess.CalledProcessError(1, ["git"])):
            with self.assertRaises(subprocess.CalledProcessError):
                helper.verify_local_identity({"binding": {"head_sha": "f" * 40}})

    def test_verify_local_identity_accepts_matching_committed_owned_input(self):
        with patch.object(helper, "git", return_value=b"same-bytes"), \
                patch.object(Path, "read_bytes", return_value=b"same-bytes"), patch("subprocess.run") as run:
            helper.verify_local_identity({"binding": {"head_sha": "f" * 40}})
            self.assertEqual(run.call_args.kwargs.get("check"), True)
            self.assertIn(helper.locks.BASE, run.call_args.args[0])

    def test_dependency_graph_source_is_the_single_fixed_build_output_location(self):
        self.assertEqual(helper.GRAPH_DIRECTORY,
            helper.ROOT / "android" / "core" / "services" / "build" / "wp218" / "content-dependencies")

    def test_collect_dependency_graph_rejects_missing_configuration(self):
        with tempfile.TemporaryDirectory() as name:
            proposal, source = Path(name) / "proposal", Path(name) / "graph-source"
            proposal.mkdir()
            source.mkdir()
            with patch.object(helper, "GRAPH_DIRECTORY", source):
                with self.assertRaises(helper.PortError):
                    helper.collect_dependency_graph(proposal)

    def test_collect_dependency_graph_rejects_empty_or_unresolved_graph(self):
        with tempfile.TemporaryDirectory() as name:
            source = Path(name) / "graph-source"
            source.mkdir()
            for configuration in helper.locks.CONFIGURATIONS:
                (source / f"{configuration}.tsv").write_text("component\tversion\tstatus\n")
            with patch.object(helper, "GRAPH_DIRECTORY", source):
                empty = Path(name) / "proposal-empty"
                empty.mkdir()
                with self.assertRaises(helper.PortError):
                    helper.collect_dependency_graph(empty)
            for configuration in helper.locks.CONFIGURATIONS:
                (source / f"{configuration}.tsv").write_text(
                    "component\tversion\tstatus\nexample:lib:1.0\t1.0\tunresolved: not found\n")
            with patch.object(helper, "GRAPH_DIRECTORY", source):
                unresolved = Path(name) / "proposal-unresolved"
                unresolved.mkdir()
                with self.assertRaises(helper.PortError):
                    helper.collect_dependency_graph(unresolved)

    def test_collect_dependency_graph_accepts_actual_resolved_rows_for_every_admitted_configuration(self):
        with tempfile.TemporaryDirectory() as name:
            source, proposal = Path(name) / "graph-source", Path(name) / "proposal-ok"
            source.mkdir()
            proposal.mkdir()
            for configuration in helper.locks.CONFIGURATIONS:
                (source / f"{configuration}.tsv").write_text(
                    "component\tversion\tstatus\nexample:lib:1.0\t1.0\tresolved\n")
            with patch.object(helper, "GRAPH_DIRECTORY", source):
                records = helper.collect_dependency_graph(proposal)
            self.assertEqual({record["configuration"] for record in records}, helper.locks.CONFIGURATIONS)
            self.assertTrue(all(record["rows"] == 1 for record in records))
            for record in records:
                copied = proposal / record["artifact_path"]
                self.assertEqual(copied.read_bytes(), (source / f"{record['configuration']}.tsv").read_bytes())
