"""AndroidOnly: WP-003 Negative workflow/schema and protected-overlay assertions."""

import copy
import unittest
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

    def test_duplicate_yaml_keys_and_invalid_yaml_are_rejected(self):
        for text in ("jobs: {}\njobs: {}\n", "jobs: ["):
            with self.subTest(text=text), self.assertRaises(PortError):
                parse_yaml(text)

    def test_required_ci_no_path_filters_and_merge_group_are_enforced(self):
        original, text = self.read("android-ci.yml")
        for kind in ("path", "branch", "merge", "gate", "needs", "host", "skip-stage"):
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
                value["jobs"]["build"]["strategy"]["matrix"]["include"].pop()
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
