import contextlib
import copy
import io
import json
import subprocess
import sqlite3
import sys
import tempfile
import unittest
from dataclasses import replace
from pathlib import Path
from unittest.mock import patch

from fixtures import BASE, HEAD, NOW, REPO, base_manifest, policy, settings, test_manifest
from controller.dispatch import ledger_snapshot, main
from controller.errors import PortError
from controller.ledger import Ledger
from controller.model import REFERENCE_SHA, validate_manifest
from controller.paths import git_path
from controller.render import cloud_payload, input_page, issue_payload, local_payload, render
from controller.test_runner import run_suite
from controller.validate import main as validate_main
from portmap import port_map


class CliTests(unittest.TestCase):
    def command(self, arguments, environment=None):
        output, errors = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(output), contextlib.redirect_stderr(errors):
            result = main(arguments, environment={} if environment is None else environment)
        return result, output.getvalue(), errors.getvalue()

    def test_all_dispatch_commands_default_dry_run_and_never_create_ledger_or_api(self):
        with tempfile.TemporaryDirectory() as directory:
            ledger = Path(directory) / "not-created.sqlite"
            environment = {"ANDROID_PORT_LEDGER": str(ledger)}
            with patch("controller.dispatch.GitHubApi", side_effect=AssertionError("no network in dry-run")):
                for command in ("sync-issues", "claim", "release", "cloud", "local"):
                    with self.subTest(command=command):
                        result, output, _ = self.command([command, "--wp", "WP-101"], environment)
                        self.assertEqual(result, 0)
                        value = json.loads(output)
                        self.assertTrue(value["dry_run"])
                        self.assertFalse(value["side_effects"])
                        self.assertTrue(value["blockers"])
                self.assertFalse(ledger.exists())

    def test_live_off_paused_missing_configuration_blocks_before_any_network(self):
        with patch("controller.dispatch.GitHubApi", side_effect=AssertionError("blocked before API")):
            for command in ("cloud", "local", "claim", "sync-issues"):
                with self.subTest(command=command):
                    result, _, errors = self.command(["--live", command, "--wp", "WP-101"])
                    self.assertEqual(result, 2)
                    self.assertIn("BLOCKED", errors)
                    self.assertIn("usage/budget", errors)

    def test_native_live_dispatch_is_blocked_without_host_not_faked_success(self):
        rules = policy()
        with tempfile.TemporaryDirectory() as directory:
            value = settings(Path(directory) / "ledger.sqlite")
            with patch("controller.dispatch.load_json", return_value=rules), patch(
                "controller.dispatch.Settings.from_env", return_value=value
            ):
                result, _, errors = self.command(["--live", "local", "--wp", "WP-101"])
                self.assertEqual(result, 2)
                self.assertIn("cannot call app-native create_session", errors)

    def test_status_and_report_do_not_claim_ported_features(self):
        for command in ("status", "report"):
            result, output, _ = self.command([command])
            self.assertEqual(result, 0)
            value = json.loads(output)
            if command == "status":
                self.assertTrue(value["paused"])
                self.assertEqual(value["dispatch_mode"], "off")
                self.assertEqual(len(value["work_packages"]), 65)
                self.assertTrue(all(not w["completion_reconciled"] for w in value["work_packages"]))
            else:
                self.assertTrue(value["traceability_only"])
                self.assertFalse(value["authoritative"])
                self.assertEqual(value["verified_feature_completions"], 0)

    def test_files_returns_exact_primary_ownership_not_globs(self):
        result, output, _ = self.command(["files", "--wp", "WP-203", "--primary-only"])
        self.assertEqual(result, 0)
        entries = json.loads(output)
        self.assertTrue(entries)
        self.assertTrue(all(e["primary_owner"] == "WP-203" for e in entries))
        self.assertTrue(all("*" not in e["path"] for e in entries))

    def test_cloud_local_and_issue_rendering_share_trusted_prompt_and_no_models(self):
        for format_name in ("cloud", "local", "issue", "prompt"):
            result, output, _ = self.command(["render", "--wp", "WP-101", "--format", format_name])
            self.assertEqual(result, 0)
            if format_name == "cloud":
                value = json.loads(output)["agent_assignment"]
                prompt = value["custom_instructions"]
                self.assertNotIn("model", value)
            elif format_name == "local":
                value = json.loads(output)["kickoff"]
                prompt = value["prompt"]
                self.assertNotIn("model", value)
            elif format_name == "issue":
                value = json.loads(output)
                prompt = value["body"]
                self.assertNotIn("assignees", value)
            else:
                prompt = output
            self.assertIn(REFERENCE_SHA, prompt)
            self.assertIn("protocol-porter", prompt)
            self.assertIn("Exact allowed writes", prompt)
            self.assertIn("original case/parameter family", prompt)
            self.assertIn("not a stub", prompt)

    def test_repair_preview_targets_existing_only_not_another_assignment(self):
        result, output, _ = self.command(["cloud", "--wp", "WP-101", "--repair-feedback", "bounded fix"])
        self.assertEqual(result, 0)
        value = json.loads(output)
        self.assertFalse(value["payload"]["new_worker"])
        self.assertNotIn("assignees", value["payload"])
        self.assertTrue(any("existing reconciled" in reason for reason in value["blockers"]))

    def test_unknown_wp_partial_or_malformed_evidence_is_nonzero_blocked(self):
        scenarios = (
            ["files", "--wp", "WP-999"],
            ["report", "--wp", "WP-101", "--evidence", "missing.json"],
            ["--live", "report", "--wp", "WP-000", "--pr-number", "1", "--import-supervised"],
        )
        for arguments in scenarios:
            with self.subTest(arguments=arguments):
                result, _, errors = self.command(arguments)
                self.assertEqual(result, 2)
                self.assertIn("BLOCKED", errors)

    def test_snapshot_connection_closes_on_normal_and_malformed_state_windows(self):
        with tempfile.TemporaryDirectory(prefix="meshcore-snapshot-tests-") as directory:
            path = Path(directory) / "ledger.sqlite"
            rules = policy()
            value = settings(path)
            ledger = Ledger(path, rules["repository"])
            self.assertEqual(ledger_snapshot(value, rules["repository"]), {})
            manifest = test_manifest()
            ledger.claim(manifest.wp("WP-101"), {}, "local", "attempt", NOW, 1800, 5, 0, 100, 5)
            self.assertEqual(len(ledger_snapshot(value, rules["repository"])), 1)
            with contextlib.closing(sqlite3.connect(path)) as connection:
                with connection:
                    connection.execute("UPDATE leases SET state='invalid'")
            with self.assertRaises(PortError):
                ledger_snapshot(value, rules["repository"])
            # Windows refuses this if either readonly or exception-path connection leaked.
            path.rename(path.with_name("closed.sqlite"))

    def test_reference_advancement_and_unknown_new_source_fail_without_moving_pin(self):
        original = base_manifest()
        expected = {e["path"]: e["blob_sha"] for e in original.data["inventory"]}
        for kind in ("advance", "new-source"):
            current = copy.deepcopy(expected)
            if kind == "advance":
                current["MC1/MC1App.swift"] = "0" * 40
            else:
                current["MC1/NewUpstreamFile.swift"] = "0" * 40
            with patch("controller.model.tree", side_effect=[expected, current]):
                with self.subTest(kind=kind), self.assertRaisesRegex(PortError, "reference advanced/changed"):
                    validate_manifest(original.data, original.exclusions, REPO)
        self.assertEqual(base_manifest().data["reference"]["commit"], REFERENCE_SHA)

    def test_all_65_handoffs_fit_with_inherited_models_and_full_pinned_acceptance(self):
        manifest, rules = base_manifest(), policy()
        for wp_id in manifest.work_packages:
            with self.subTest(wp=wp_id):
                text = render(manifest, rules, wp_id)
                self.assertLessEqual(len(text), 65536)
                issue = issue_payload(manifest, rules, wp_id)
                cloud = cloud_payload(manifest, rules, wp_id, "unclaimed-dry-run")
                local = local_payload(manifest, rules, wp_id, "unclaimed-dry-run")
                self.assertEqual(issue["body"], text)
                self.assertEqual(cloud["agent_assignment"]["custom_instructions"], text)
                self.assertEqual(local["kickoff"]["prompt"], text)
                self.assertNotIn("model", cloud["agent_assignment"])
                self.assertNotIn("model", local["kickoff"])
                for acceptance in manifest.wp(wp_id)["acceptance"]:
                    self.assertIn(acceptance["id"], text)
        self.assertIn("ENTIRE frozen manifest inventory", render(manifest, rules, "WP-501"))

    def test_wide_audit_pagination_is_complete_unique_and_pinned(self):
        manifest = base_manifest()
        expected = sorted(manifest.inputs("WP-501"), key=lambda e: e["path"])
        accumulated, page, scope = [], 1, None
        while page:
            result = input_page(manifest, "WP-501", page, 100, manifest.sha256, REFERENCE_SHA)
            self.assertEqual(result["manifest_sha256"], manifest.sha256)
            self.assertEqual(result["source_sha"], REFERENCE_SHA)
            self.assertEqual(result["total_entries"], len(expected))
            if scope is None:
                scope = result["scope_sha256"]
            self.assertEqual(result["scope_sha256"], scope)
            accumulated.extend(result["entries"])
            page = result["next_page"]
        self.assertEqual(accumulated, expected)
        self.assertEqual(len({e["path"] for e in accumulated}), len(expected))

    def test_pagination_filter_pin_drift_missing_guards_and_invalid_pages_fail_closed(self):
        manifest = base_manifest()
        result = input_page(manifest, "WP-501", 1, 100, manifest.sha256, REFERENCE_SHA, kind="test")
        self.assertTrue(all(e["kind"] == "test" for e in result["entries"]))
        for page, size, sha, source in (
            (0, 100, manifest.sha256, REFERENCE_SHA), (1, 0, manifest.sha256, REFERENCE_SHA),
            (1, 201, manifest.sha256, REFERENCE_SHA), (999, 100, manifest.sha256, REFERENCE_SHA),
            (1, 100, "0" * 64, REFERENCE_SHA), (1, 100, manifest.sha256, "0" * 40),
        ):
            with self.subTest(page=page, size=size, sha=sha), self.assertRaises(PortError):
                input_page(manifest, "WP-501", page, size, sha, source)
        result, _, errors = self.command(["files", "--wp", "WP-501", "--page", "1"])
        self.assertEqual(result, 2)
        self.assertIn("pin guards", errors)

    def test_bootstrap_wrappers_are_readonly_trusted_default_branch_and_not_required_verdicts(self):
        directory = REPO / ".github" / "workflows"
        for filename in ("android-port-dispatch.yml", "android-parity-review.yml",
                         "android-pr-shepherd.yml", "android-gate-integrity.yml"):
            content = (directory / filename).read_text(encoding="utf-8")
            with self.subTest(filename=filename):
                self.assertIn("workflow_dispatch:", content)
                self.assertNotIn("schedule:", content)
                self.assertNotIn("pull_request_target:", content)
                self.assertNotIn("workflow_run:", content)
                self.assertNotIn("secrets.", content)
                self.assertNotIn("--live", content)
                self.assertIn("contents: read", content)
                self.assertIn("persist-credentials: false", content)
                self.assertIn("github.event.repository.default_branch", content)
                self.assertIn("ref: ${{ github.sha }}", content)
                self.assertIn("android-port-controller", content)
                if filename != "android-gate-integrity.yml":
                    self.assertIn("--dry-run", content)
        bootstrap = (directory / "android-bootstrap.yml").read_text(encoding="utf-8")
        self.assertIn("pull_request:", bootstrap)
        self.assertIn("merge_group:", bootstrap)
        self.assertIn("test_runner.py", bootstrap)
        self.assertNotIn("gradlew", bootstrap)
        self.assertNotIn("secrets.", bootstrap)
        self.assertFalse((directory / "copilot-setup-steps.yml").exists())

    def test_zero_and_skipped_test_runner_results_fail(self):
        with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(run_suite(unittest.TestSuite(), verbosity=0), 2)

            class Skipped(unittest.TestCase):
                @unittest.skip("fixture skip must fail strict discovery policy")
                def test_skipped(self):
                    pass

            self.assertEqual(run_suite(unittest.defaultTestLoader.loadTestsFromTestCase(Skipped), verbosity=0), 2)

    def test_empty_missing_runner_directory_is_nonzero_in_a_real_subprocess(self):
        runner = REPO / "tools" / "android-port" / "controller" / "test_runner.py"
        with tempfile.TemporaryDirectory() as directory:
            for path in (Path(directory), Path(directory) / "missing"):
                result = subprocess.run(
                    [sys.executable, str(runner), "--tests", str(path), "--quiet"],
                    capture_output=True, text=True,
                )
                self.assertEqual(result.returncode, 2)
                self.assertIn("BLOCKED", result.stderr)


class ProvenanceTests(unittest.TestCase):
    def temporary_manifest(self, directory):
        return replace(base_manifest(), repo=Path(directory))

    def test_absent_android_tree_is_unported_not_feature_success(self):
        with tempfile.TemporaryDirectory() as directory:
            self.assertEqual(port_map(self.temporary_manifest(directory)), [])

    def test_many_to_many_source_headers_are_derived_not_feature_acceptance(self):
        manifest = base_manifest()
        sources = [e["path"] for e in manifest.inputs("WP-101", False) if e["kind"] == "production"][:2]
        with tempfile.TemporaryDirectory() as directory:
            file = Path(directory) / "android" / "core" / "protocol" / "Combined.kt"
            file.parent.mkdir(parents=True)
            file.write_text("\n".join(f"// PortedFrom: {source}@{REFERENCE_SHA}" for source in sources), encoding="utf-8")
            results = port_map(self.temporary_manifest(directory))
            self.assertEqual(results[0]["sources"], sources)
            self.assertEqual(results[0]["feature_acceptance"], "not established by headers")

    def test_missing_unknown_or_stale_headers_fail(self):
        with tempfile.TemporaryDirectory() as directory:
            file = Path(directory) / "android" / "Invalid.kt"
            file.parent.mkdir()
            for content in (
                "class MissingHeader",
                f"// PortedFrom: unknown.swift@{REFERENCE_SHA}",
                "// PortedFrom: MC1/MC1App.swift@" + "0" * 40,
                "// AndroidOnly: WP-999 unknown owner",
            ):
                file.write_text(content, encoding="utf-8")
                with self.subTest(content=content), self.assertRaises(PortError):
                    port_map(self.temporary_manifest(directory))

    def test_generated_provenance_requires_existing_generator_and_known_pinned_inputs(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            file = root / "android" / "Generated.kt"
            file.parent.mkdir()
            generator = root / "tools" / "android-port" / "l10n_convert.py"
            generator.parent.mkdir(parents=True)
            generator.write_text("# fixture-only generator\n", encoding="utf-8")
            source = "MC1/Resources/Localization/en.lproj/Localizable.strings"
            content = f"// GeneratedFrom: tools/android-port/l10n_convert.py; inputs: {source}@{REFERENCE_SHA}"
            file.write_text(content, encoding="utf-8")
            self.assertTrue(port_map(self.temporary_manifest(directory))[0]["generated_inputs"])
            for invalid in (
                "// GeneratedFrom: unspecified",
                f"// GeneratedFrom: tools/android-port/missing.py; inputs: {source}@{REFERENCE_SHA}",
                f"// GeneratedFrom: tools/android-port/l10n_convert.py; inputs: unknown@{REFERENCE_SHA}",
                f"// GeneratedFrom: tools/android-port/l10n_convert.py; inputs: {source}@{'0' * 40}",
            ):
                file.write_text(invalid, encoding="utf-8")
                with self.subTest(invalid=invalid), self.assertRaises(PortError):
                    port_map(self.temporary_manifest(directory))
