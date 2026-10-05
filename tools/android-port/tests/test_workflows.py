"""AndroidOnly: WP-003 Negative workflow/schema and protected-overlay assertions."""

import copy
import json
import tempfile
import unittest
from contextlib import ExitStack
from pathlib import Path
from unittest.mock import patch

from fixtures import REPO, base_manifest
from bootstrap import build_inventory
from controller.errors import PortError
from controller.verification_config import AMENDMENTS, apply_overlay, check_configuration
from controller.workflows import parse_yaml, validate_candidate, validate_setup, validate_trusted, validate_workflows


class WorkflowTests(unittest.TestCase):
    def read(self, name):
        text = (REPO / ".github" / "workflows" / name).read_text(encoding="utf-8")
        return parse_yaml(text), text

    def test_actual_workflows_parse_and_have_the_real_trust_boundaries(self):
        self.assertEqual(validate_workflows(REPO)["result"], "valid")

    def test_legacy_bootstrap_installs_the_hash_pinned_yaml_runtime_before_tests(self):
        workflow, text = self.read("android-bootstrap.yml")
        steps = workflow["jobs"]["controller-tests"]["steps"]
        installer = next(index for index, step in enumerate(steps)
                         if "requirements-ci.txt" in step.get("run", ""))
        runner = next(index for index, step in enumerate(steps)
                      if "controller/test_runner.py" in step.get("run", ""))
        self.assertLess(installer, runner)
        self.assertIn("--require-hashes", steps[installer]["run"])
        self.assertIn("--only-binary=:all:", steps[installer]["run"])
        self.assertEqual(workflow["permissions"], {"contents": "read"})
        self.assertEqual(steps[0]["with"]["persist-credentials"], "false")
        self.assertIn("github.event.pull_request.head.sha", steps[0]["with"]["ref"])
        self.assertNotIn("secrets.", text)

    def test_protocol_workflow_executes_the_real_nonzero_jvm_suite_on_linux(self):
        from controller.ci import TASKS

        workflow, text = self.read("android-protocol.yml")
        job = workflow["jobs"]["protocol"]
        self.assertEqual(TASKS["protocol"], [":core:protocol:test"])
        self.assertEqual(job["runs-on"], "ubuntu-24.04")
        self.assertNotIn("strategy", job)
        self.assertIn("merge_group", workflow["on"])
        self.assertNotIn("paths", workflow["on"]["pull_request"])
        self.assertEqual(workflow["permissions"], {"contents": "read"})
        command = next(step for step in job["steps"]
                       if "--stage protocol" in step.get("run", ""))
        self.assertNotIn("if", command)
        self.assertNotIn("continue-on-error", command)
        self.assertEqual(job["steps"][0]["with"]["persist-credentials"], "false")
        self.assertNotIn("secrets.", text)

    def test_duplicate_yaml_keys_and_invalid_yaml_are_rejected(self):
        for text in ("jobs: {}\njobs: {}\n", "jobs: ["):
            with self.subTest(text=text), self.assertRaises(PortError):
                parse_yaml(text)

    def test_required_ci_no_path_filters_and_merge_group_are_enforced(self):
        original, text = self.read("android-ci.yml")
        for kind in ("path", "branch", "merge", "gate", "needs", "host", "strategy", "skip-stage"):
            value = copy.deepcopy(original)
            if kind in ("path", "branch"):
                value["on"]["pull_request"]["paths" if kind == "path" else "branches"] = ["android/**"]
            elif kind == "merge":
                value["on"].pop("merge_group")
            elif kind == "gate":
                value["jobs"]["android-ci"]["if"] = "success()"
            elif kind == "needs":
                value["jobs"]["android-ci"]["needs"] = []
            elif kind == "host":
                value["jobs"]["build"]["runs-on"] = "windows-2025"
            elif kind == "strategy":
                value["jobs"]["build"]["strategy"] = {"matrix": {"host": ["linux", "windows"]}}
            else:
                value["jobs"]["build"]["steps"][7]["if"] = "false"
            with self.subTest(kind=kind), self.assertRaises(PortError):
                validate_candidate(value, text)

    def test_read_only_checkout_ephemeral_runners_pins_and_secret_absence(self):
        original, text = self.read("android-ci.yml")
        for kind in ("permissions", "credentials", "action", "cache", "secret", "runner", "ignored-failure"):
            value, changed = copy.deepcopy(original), text
            if kind == "permissions":
                value["permissions"]["checks"] = "write"
            elif kind == "credentials":
                value["jobs"]["build"]["steps"][0]["with"]["persist-credentials"] = "true"
            elif kind == "action":
                value["jobs"]["build"]["steps"][0]["uses"] = "actions/checkout@v4"
            elif kind == "cache":
                value["jobs"]["build"]["steps"].append({"uses": "actions/cache@fixture"})
            elif kind == "runner":
                changed += "\n# self-hosted\n"
            elif kind == "ignored-failure":
                value["jobs"]["build"]["steps"][0]["continue-on-error"] = "true"
            else:
                changed += "\n# ${{ secrets.DISPATCH_TOKEN }}\n"
            with self.subTest(kind=kind), self.assertRaises(PortError):
                validate_candidate(value, changed)

    def test_cloud_setup_supported_single_job_and_only_supported_properties(self):
        original, text = self.read("copilot-setup-steps.yml")
        for kind in ("job", "timeout", "host", "env", "defaults", "condition", "strategy"):
            value = copy.deepcopy(original)
            job = value["jobs"]["copilot-setup-steps"]
            if kind == "job":
                value["jobs"]["extra"] = copy.deepcopy(job)
            elif kind == "timeout":
                job["timeout-minutes"] = "60"
            elif kind == "host":
                job["runs-on"] = "macos-latest"
            else:
                job[{"condition": "if"}.get(kind, kind)] = {}
            with self.subTest(kind=kind), self.assertRaises(PortError):
                validate_setup(value, text)

    def test_trusted_duties_never_run_candidate_workflows_or_publish_preview_pass(self):
        original, text = self.read("android-parity-review.yml")
        for kind in ("event", "name", "checkout", "ref", "schedule"):
            value = copy.deepcopy(original)
            job = value["jobs"]["staging"]
            if kind in ("event", "schedule"):
                value["on"]["workflow_run" if kind == "event" else "schedule"] = {}
            elif kind == "name":
                job["name"] = "parity-review"
            elif kind == "checkout":
                job["steps"][0]["with"]["ref"] = "${{ inputs.candidate_sha }}"
            else:
                job["if"] = "github.ref != github.event.repository.default_branch"
            with self.subTest(kind=kind), self.assertRaises(PortError):
                validate_trusted(value, text)

    def test_verification_overlay_is_reproducible_and_only_changes_two_real_configs(self):
        original, _ = build_inventory(REPO)
        amended = apply_overlay(original)
        self.assertEqual(amended, base_manifest().data)
        for before, after in zip(original["work_packages"], amended["work_packages"], strict=True):
            if before["id"] in AMENDMENTS:
                self.assertEqual({k: v for k, v in before.items() if k != "verification"},
                                 {k: v for k, v in after.items() if k != "verification"})
            else:
                self.assertEqual(before, after)
        self.assertEqual(check_configuration(REPO)["verification_amendments"], ["WP-002", "WP-003"])

    def test_generator_or_future_feature_configuration_drift_is_rejected(self):
        original, _ = build_inventory(REPO)
        changed = copy.deepcopy(original)
        changed["work_packages"][4]["verification"]["configured"] = True
        with self.assertRaises(PortError):
            apply_overlay(changed)
        changed = copy.deepcopy(base_manifest().data)
        changed["inventory"][0]["primary_owner"] = "WP-003"
        from controller.schema import load_json

        real = load_json
        with patch("controller.verification_config.load_json", side_effect=lambda path: changed if path.name == "port-manifest.json" else real(path)):
            with self.assertRaises(PortError):
                check_configuration(REPO)


class StageReportTests(unittest.TestCase):
    def execute_stage(self, repo, output, stage):
        from controller.ci import run_stage

        with ExitStack() as mocks:
            mocks.enter_context(patch("controller.ci.REPO", repo))
            mocks.enter_context(patch("controller.ci.execution_identity", return_value=None))
            mocks.enter_context(patch("controller.ci.platform.python_version", return_value="3.12.4"))
            mocks.enter_context(patch("controller.ci.toolchain_lock", return_value={"python": "3.12.4"}))
            mocks.enter_context(patch("controller.ci.verify_wrapper"))
            mocks.enter_context(patch("controller.ci.candidate_environment",
                                     return_value={"GRADLE_USER_HOME": str(repo / "private-gradle")}))
            mocks.enter_context(patch("controller.ci.execute"))
            if stage == "lint":
                mocks.enter_context(patch("controller.ci.collect_lint",
                                         return_value={"app": {"warnings": 0, "sha256": "1" * 64}}))
            elif stage == "verify":
                mocks.enter_context(patch("controller.ci.collect_suites", return_value={"fixture": {"passed": 1}}))
                mocks.enter_context(patch("controller.ci.collect_module_tests", return_value={"core/model": {"fixture": True}}))
            run_stage(stage, {"host": "linux", "private_root": str(repo / "private")}, output)
        return json.loads((output / f"stage-{stage}.json").read_text(encoding="utf-8"))

    def test_protocol_report_copies_actual_cases_without_accessing_lint_fields(self):
        from test_ci_evidence import junit_report

        with tempfile.TemporaryDirectory() as temporary:
            repo = Path(temporary)
            output = repo / "evidence"
            source = repo / "android" / "core" / "protocol" / "build" / "test-results" / "test" / "TEST-protocol.xml"
            junit_report(source, 84)
            result = self.execute_stage(repo, output, "protocol")
            self.assertEqual(result["suite"]["passed"], 84)
            self.assertNotIn("reports", result)
            self.assertEqual(source.read_bytes(), (output / "junit" / "protocol" / source.name).read_bytes())

    def test_lint_report_preserves_raw_artifacts_without_entering_protocol_staging(self):
        from controller.ci_evidence import lint_bundle_path

        with tempfile.TemporaryDirectory() as temporary:
            repo = Path(temporary)
            output = repo / "evidence"
            source = repo / "android" / "app" / "build" / "reports" / "lint-results-debug.xml"
            source.parent.mkdir(parents=True)
            source.write_text('<issues format="6" by="lint fixture"/>', encoding="utf-8")
            result = self.execute_stage(repo, output, "lint")
            self.assertIn("app", result["reports"])
            self.assertNotIn("suite", result)
            self.assertEqual(source.read_bytes(), (output / lint_bundle_path("app")).read_bytes())

    def test_composite_verify_retains_active_module_reports_without_changing_other_stages(self):
        with tempfile.TemporaryDirectory() as temporary:
            repo = Path(temporary)
            result = self.execute_stage(repo, repo / "evidence", "verify")
            self.assertEqual(result["module_unit_tests"], {"core/model": {"fixture": True}})
            self.assertEqual(result["suites"], {"fixture": {"passed": 1}})
            self.assertNotIn("reports", result)
