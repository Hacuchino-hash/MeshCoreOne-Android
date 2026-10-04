"""AndroidOnly: WP-006 Real Git and complete owner/provenance proposal assertions."""

import copy
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[3] / "tools" / "android-port"))

from controller.errors import PortError
from controller.model import Manifest, REFERENCE_SHA
from resync import changes, compare, export_report, proposal_id, require_revision


def fixture_manifest():
    rows = [
        {"path": "Source/A.swift", "blob_sha": "1" * 40, "kind": "production",
         "primary_owner": "WP-101", "cross_references": ["WP-103", "WP-103"], "exclusion": None},
        {"path": "Tests/A.swift", "blob_sha": "2" * 40, "kind": "test",
         "primary_owner": "WP-101", "cross_references": ["WP-103"], "exclusion": None},
        {"path": "Resources/en.strings", "blob_sha": "3" * 40, "kind": "resource",
         "primary_owner": "WP-103", "cross_references": [], "exclusion": None},
        {"path": "Apple/Glue.swift", "blob_sha": "4" * 40, "kind": "apple-glue",
         "primary_owner": None, "cross_references": [], "exclusion": "apple-only-glue"},
    ]
    packages = [
        {"id": value, "owner": "protocol-porter", "acceptance": [{"id": value + "-behavior"}]}
        for value in ("WP-101", "WP-103")
    ]
    return Manifest({"reference": {"commit": REFERENCE_SHA}, "inventory": rows, "work_packages": packages}, {}, Path.cwd())


def delta(path="Source/A.swift", blob="1" * 40, status="M", incoming=None):
    return {
        "status": status,
        "old_path": None if status == "A" else path,
        "new_path": None if status == "D" else (incoming or path),
        "old_blob": None if status == "A" else blob,
        "new_blob": None if status == "D" else "9" * 40,
        "rename_similarity": 85 if status == "R" else None,
        "old_mode": None if status == "A" else "100644",
        "new_mode": None if status == "D" else "100644",
    }


class ProposalTests(unittest.TestCase):
    def setUp(self):
        self.manifest = fixture_manifest()
        self.policy = {"repository": "owner/repo", "dispatch_mode": "off", "paused": True}
        self.mapping = [{"implementation": "android/A.kt", "sources": ["Source/A.swift"], "generated_inputs": []}]

    def report(self, inputs=None, mappings=None, candidate="a" * 40, existing=()):
        return compare(
            self.manifest, self.policy, candidate, inputs if inputs is not None else [delta()],
            self.mapping if mappings is None else mappings, existing,
        )

    def test_primary_and_each_consumer_get_one_complete_proposal(self):
        report = self.report([delta(), delta("Tests/A.swift", "2" * 40)])
        self.assertEqual(["WP-101", "WP-103"], [p["work_package"] for p in report["proposals"]])
        for proposal in report["proposals"]:
            self.assertEqual(2, len(proposal["changes"]))
            self.assertEqual(["android/A.kt"], proposal["android_implementations"])
            self.assertIn("Tests/A.swift", proposal["follow_up_prompt"])
            self.assertIn("android/A.kt", proposal["follow_up_prompt"])
            self.assertIn(proposal["acceptance_ids"][0], proposal["follow_up_prompt"])
        self.assertEqual("review_required", report["status"])

    def test_generated_multi_input_provenance_routes_renamed_original(self):
        mapping = [{"implementation": "android/Generated.kt", "sources": [], "generated_inputs": [
            f"tools/android-port/generator.py; inputs: Source/A.swift@{REFERENCE_SHA}, Tests/A.swift@{REFERENCE_SHA}",
        ]}]
        report = self.report(mappings=mapping)
        self.assertEqual(["android/Generated.kt"], report["proposals"][0]["android_implementations"])

    def test_stale_generated_provenance_fails(self):
        mapping = [{"implementation": "android/Generated.kt", "sources": [], "generated_inputs": [
            "tools/android-port/generator.py; inputs: Source/A.swift@" + "a" * 40,
        ]}]
        with self.assertRaises(PortError):
            self.report(mappings=mapping)

    def test_deletion_retains_old_owner_consumer_and_native_inputs(self):
        report = self.report([delta(status="D")])
        self.assertEqual("review_required", report["status"])
        self.assertEqual(["WP-101", "WP-103"], [p["work_package"] for p in report["proposals"]])
        self.assertIsNone(report["proposals"][0]["changes"][0]["new_blob"])
        self.assertEqual(["android/A.kt"], report["proposals"][0]["android_implementations"])

    def test_rename_is_routed_but_new_mapping_is_not_silently_approved(self):
        report = self.report([delta(status="R", incoming="Source/Renamed.swift")])
        self.assertEqual("blocked", report["status"])
        self.assertEqual("renamed_destination_requires_reviewed_mapping", report["blockers"][0]["reason"])
        self.assertEqual("WP-101", report["blockers"][0]["suggested_primary_owner"])
        self.assertEqual(2, len(report["proposals"]))

    def test_new_unowned_input_is_explicit_blocker(self):
        report = self.report([delta("New.swift", status="A")])
        self.assertEqual("blocked", report["status"])
        self.assertEqual("new_unowned_source", report["blockers"][0]["reason"])
        self.assertEqual([], report["proposals"])

    def test_changed_exclusion_is_retained_for_review_not_ignored(self):
        report = self.report([delta("Apple/Glue.swift", "4" * 40)])
        self.assertEqual(1, len(report["changed_exclusions"]))
        self.assertIn("never automatically discard", report["changed_exclusions"][0]["disposition"])

    def test_resource_change_routes_real_owner(self):
        report = self.report([delta("Resources/en.strings", "3" * 40)])
        self.assertEqual(["WP-103"], [p["work_package"] for p in report["proposals"]])
        self.assertEqual("resource", report["proposals"][0]["changes"][0]["kind"])

    def test_old_blob_disagreement_fails(self):
        with self.assertRaises(PortError):
            self.report([delta(blob="0" * 40)])

    def test_duplicate_old_cross_references_do_not_create_duplicate_work(self):
        report = self.report()
        self.assertEqual(2, len(report["proposals"]))
        self.assertEqual(2, len({p["id"] for p in report["proposals"]}))

    def test_id_does_not_change_for_different_candidate_with_identical_delta(self):
        first = self.report()
        second = self.report(candidate="b" * 40, existing=[first])
        self.assertEqual([p["id"] for p in first["proposals"]], [p["id"] for p in second["proposals"]])
        self.assertEqual(0, second["summary"]["new_owner_proposals"])
        self.assertEqual(2, len(second["proposals"]), "Already-proposed evidence must not disappear")

    def test_new_android_implementation_does_not_duplicate_same_source_proposal(self):
        first = self.report()
        mappings = self.mapping + [{"implementation": "android/B.kt", "sources": ["Source/A.swift"], "generated_inputs": []}]
        second = self.report(mappings=mappings, existing=[first])
        self.assertEqual(0, second["summary"]["new_owner_proposals"])
        self.assertEqual(["android/A.kt", "android/B.kt"], second["proposals"][0]["android_implementations"])

    def test_existing_id_must_match_complete_delta(self):
        prior = self.report()
        prior["proposals"][0]["changes"][0]["new_blob"] = "8" * 40
        with self.assertRaises(PortError):
            self.report(existing=[prior])

    def test_stale_existing_manifest_policy_source_or_repository_fails(self):
        for key in ("repository", "source_sha", "manifest_sha256", "policy_revision"):
            prior = self.report()
            prior["binding"][key] = "stale"
            with self.subTest(key=key), self.assertRaises(PortError):
                self.report(existing=[prior])

    def test_malformed_repository_policy_and_null_proposal_identity_fail_explicitly(self):
        prior = self.report()
        prior["proposals"][0]["id"] = None
        with self.assertRaises(PortError):
            self.report(existing=[prior])
        self.policy = {}
        with self.assertRaises(PortError):
            self.report()

    def test_stop_switches_do_not_relabel_semantic_policy_revision(self):
        first = self.report()
        self.policy.update({"dispatch_mode": "draft_only", "paused": False, "activation_approved": False})
        second = self.report(existing=[first])
        self.assertEqual(first["binding"]["policy_revision"], second["binding"]["policy_revision"])
        self.assertEqual(0, second["summary"]["new_owner_proposals"])

    def test_comparison_does_not_modify_inputs_or_activate_actions(self):
        original = copy.deepcopy(self.manifest.data)
        report = self.report()
        self.assertEqual(original, self.manifest.data)
        self.assertTrue(report["operation"]["read_only_comparison"])
        self.assertTrue(all(not v for k, v in report["operation"].items() if k != "read_only_comparison"))

    def test_no_change_has_no_fake_proposals(self):
        report = self.report([])
        self.assertEqual("no_changes", report["status"])
        self.assertEqual([], report["proposals"])

    def test_more_than_inline_limit_keeps_complete_changed_inputs(self):
        rows = []
        for index in range(1_000):
            path = f"Source/VeryLongSourceFileName{index:05}.swift"
            self.manifest.data["inventory"].append({
                "path": path, "blob_sha": "1" * 40, "kind": "production", "primary_owner": "WP-101",
                "cross_references": [], "exclusion": None,
            })
            rows.append(delta(path))
        report = self.report(rows)
        self.assertEqual(1_000, len(report["source_changes"]))
        self.assertEqual(1_000, len(report["proposals"][0]["changes"]))

    def test_invalid_sha_paths_modes_and_duplicate_delta_fail(self):
        for invalid in ("main", "--help", "a" * 39, "A" * 40):
            with self.subTest(invalid=invalid), self.assertRaises(PortError):
                require_revision(invalid)
        for field, invalid in (("old_path", "../Source"), ("new_blob", "bad"), ("new_mode", "120000")):
            row = delta()
            row[field] = invalid
            with self.subTest(field=field), self.assertRaises(PortError):
                proposal_id("WP-101", [row])
        with self.assertRaises(PortError):
            proposal_id("WP-101", [delta(), delta()])

    def test_export_is_private_complete_exclusive_and_no_checkout_write(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            checkout = root / "repo"
            checkout.mkdir()
            report = self.report()
            output = root / "evidence" / "resync.json"
            export_report(output, report, checkout)
            from controller.schema import load_json

            self.assertEqual(report, load_json(output))
            with self.assertRaises(PortError):
                export_report(output, report, checkout)
            with self.assertRaises(PortError):
                export_report(checkout / "bad.json", report, checkout)
            with self.assertRaises(PortError):
                export_report(Path("relative-report.json"), report, checkout)


class GitChangeTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.repo = Path(self.temporary.name)
        self.command("init", "--quiet")
        self.command("config", "user.name", "Fixture")
        self.command("config", "user.email", "fixture@example.invalid")
        self.command("config", "core.autocrlf", "false")
        (self.repo / "Source.swift").write_text("alpha\n" * 10, encoding="utf-8")
        (self.repo / "Remove.strings").write_text('"key" = "value";\n', encoding="utf-8")
        self.original = self.commit()

    def tearDown(self):
        self.temporary.cleanup()

    def command(self, *args):
        return subprocess.run(["git", "-C", str(self.repo), *args], check=True,
                              capture_output=True).stdout.decode().strip()

    def commit(self):
        self.command("add", "--all")
        self.command("commit", "--quiet", "-m", "Fixture\n\nCo-authored-by: Copilot App <223556219+Copilot@users.noreply.github.com>")
        return self.command("rev-parse", "HEAD")

    def test_real_modified_deleted_and_added_files(self):
        (self.repo / "Source.swift").write_text("changed\n", encoding="utf-8")
        (self.repo / "Remove.strings").unlink()
        (self.repo / "New.swift").write_text("new source\n", encoding="utf-8")
        candidate = self.commit()
        rows = changes(self.repo, self.original, candidate)
        self.assertEqual({"A", "M", "D"}, {row["status"] for row in rows})
        self.assertTrue(all(row["old_blob"] or row["new_blob"] for row in rows))
        self.assertEqual(candidate, self.command("rev-parse", "HEAD"))

    def test_real_exact_rename_preserves_old_and_new_identity(self):
        (self.repo / "Source.swift").rename(self.repo / "Renamed.swift")
        rows = changes(self.repo, self.original, self.commit())
        self.assertEqual(1, len(rows))
        self.assertEqual("R", rows[0]["status"])
        self.assertEqual("Source.swift", rows[0]["old_path"])
        self.assertEqual("Renamed.swift", rows[0]["new_path"])
        self.assertEqual(rows[0]["old_blob"], rows[0]["new_blob"])
        self.assertEqual(100, rows[0]["rename_similarity"])

    def test_real_modified_rename_accepts_git_three_digit_similarity(self):
        (self.repo / "Source.swift").rename(self.repo / "Renamed.swift")
        with (self.repo / "Renamed.swift").open("a", encoding="utf-8") as output:
            output.write("beta\n")
        rows = changes(self.repo, self.original, self.commit())
        self.assertEqual("R", rows[0]["status"])
        self.assertLess(rows[0]["rename_similarity"], 100)
        self.assertNotEqual(rows[0]["old_blob"], rows[0]["new_blob"])

    def test_real_mode_only_change_is_not_lost_as_same_blob(self):
        self.command("update-index", "--chmod=+x", "Source.swift")
        self.command("commit", "--quiet", "-m", "Mode fixture\n\nCo-authored-by: Copilot App <223556219+Copilot@users.noreply.github.com>")
        rows = changes(self.repo, self.original, self.command("rev-parse", "HEAD"))
        self.assertEqual(1, len(rows))
        self.assertEqual(rows[0]["old_blob"], rows[0]["new_blob"])
        self.assertEqual("100644", rows[0]["old_mode"])
        self.assertEqual("100755", rows[0]["new_mode"])

    def test_missing_commit_fails_not_empty_success(self):
        with self.assertRaises(PortError):
            changes(self.repo, self.original, "0" * 40)

    def test_noop_git_comparison_is_truly_empty(self):
        self.assertEqual([], changes(self.repo, self.original, self.original))


if __name__ == "__main__":
    unittest.main()
