import copy
import hashlib
import json
import tempfile
import unittest
from collections import Counter
from pathlib import Path
from unittest.mock import patch

from fixtures import REPO, base_manifest
from controller.errors import PortError
from controller.model import (
    APPROVED_PLAN_SHA256,
    HUMAN_GATES,
    REFERENCE_SHA,
    plan_rows,
    profile,
    validate_manifest,
)
from controller.paths import conflicts, expand_selectors, git_path, overlaps, permits, validate_writes
from controller.schema import check_schema, decode_json
from bootstrap import build_inventory, check_generated
from controller.verification_config import apply_overlay, check_configuration


class ManifestTests(unittest.TestCase):
    def validate(self, data, exclusions=None):
        return validate_manifest(data, exclusions or base_manifest().exclusions, REPO, verify_checkout=False)

    def test_exact_approved_inventory_counts(self):
        manifest = base_manifest()
        counts = manifest.counts()
        self.assertEqual(counts["tracked_reference_files"], 1866)
        self.assertEqual(counts["kinds"]["production"], 1058)
        self.assertEqual(counts["kinds"]["test"] + counts["kinds"]["support"], 468)
        self.assertEqual(counts["owned_files"], 1812)
        self.assertEqual(counts["excluded_files"], 54)
        self.assertEqual(counts["work_packages"], 65)
        self.assertEqual(counts["dependency_edges"], 185)
        self.assertEqual(set(counts["human_gates"]), HUMAN_GATES)

    def test_expander_is_reproducible_not_a_runtime_glob(self):
        data, exclusions = build_inventory(REPO)
        self.assertEqual(apply_overlay(data), base_manifest().data)
        self.assertEqual(exclusions, base_manifest().exclusions)
        self.assertTrue(all("*" not in e["path"] for e in data["inventory"]))

    def test_bulk_installer_check_mode_rejects_generated_drift_without_overwriting(self):
        data, exclusions = build_inventory(REPO)
        changed = copy.deepcopy(data)
        changed["inventory"][0]["primary_owner"] = "WP-999"
        with patch("bootstrap.load_json", side_effect=[changed, exclusions]):
            with self.assertRaisesRegex(PortError, "Generated inventory drift"):
                check_generated(REPO, data, exclusions)
        with self.assertRaisesRegex(PortError, "Generated inventory drift"):
            check_generated(REPO, data, exclusions)
        self.assertEqual(check_configuration(REPO)["result"], "valid")

    def test_every_owner_and_dependency_matches_approved_plan(self):
        plan_text = (REPO / "docs" / "android" / "PORTING_PLAN.md").read_text(encoding="utf-8")
        plan = plan_rows(plan_text)
        for wp_id, wp in base_manifest().work_packages.items():
            for key in ("title", "owner", "depends_on", "human_gate"):
                self.assertEqual(wp[key], plan[wp_id][key])
        self.assertEqual(base_manifest().wp("WP-106")["depends_on"], ["WP-101", "WP-105"])
        self.assertEqual(base_manifest().wp("WP-103")["depends_on"], ["WP-101", "WP-106"])
        normalized = plan_text.replace("../../.github/agents/", "files/agents/").replace(
            "../../.github/skills/", "files/skills/"
        )
        self.assertEqual(hashlib.sha256(normalized.encode()).hexdigest(), APPROVED_PLAN_SHA256)
        with patch(
            "controller.model.APPROVED_PLAN_SHA256",
            "0afc364cd63a99f438bffe7df8542d49503bfd113c363ed89e60d284b6a3c837",
        ), self.assertRaisesRegex(PortError, "Approved plan changed"):
            self.validate(copy.deepcopy(base_manifest().data))

    def test_resources_helpers_and_licenses_are_explicit(self):
        entries = {e["path"]: e for e in base_manifest().data["inventory"]}
        samples = {
            "MC1Services/Sources/MC1Services/Errors/AppBackupError.swift": "WP-203",
            "MC1Services/Sources/MC1Services/ServiceContainer.swift": "WP-303",
            "MC1/Resources/Styles/topo-offline.json": "WP-312",
            "MC1/Resources/urlhaus-filter-hosts-online.txt": "WP-218",
            "MC1/Resources/Localization/en.lproj/Localizable.stringsdict": "WP-005",
            "MeshCore/LICENSE": "WP-318",
        }
        for path, owner in samples.items():
            self.assertEqual(entries[path]["primary_owner"], owner)
        for prefix in ("MC1/Resources/Localization/", "MC1Widgets/Resources/"):
            locales = {p.split("/")[3 if prefix.startswith("MC1/") else 2] for p in entries if p.startswith(prefix)}
            self.assertEqual(len(locales), 12)
        self.assertTrue(any("/Theme/" in p and p.endswith("Contents.json") for p in entries))
        self.assertTrue(any("Settings.bundle/Packages/" in p for p in entries))

    def test_translation_is_required_and_never_excluded(self):
        entries = [e for e in base_manifest().data["inventory"] if "Translat" in e["path"] or "LanguageDetector" in e["path"]]
        self.assertTrue(entries)
        for entry in entries:
            self.assertEqual(entry["primary_owner"], "WP-406")
            self.assertIsNone(entry["exclusion"])
        self.assertTrue(base_manifest().wp("WP-406")["human_gate"])

    def test_many_to_many_cross_references_do_not_duplicate_primary_owners(self):
        entries = base_manifest().data["inventory"]
        self.assertTrue(any(len(e["cross_references"]) > 2 for e in entries))
        self.assertEqual(len({e["path"] for e in entries}), len(entries))
        self.assertTrue(all(e["primary_owner"] not in e["cross_references"] for e in entries))

    def test_profiles_and_reviewer_are_preserved_and_model_inherited(self):
        paths = list((REPO / ".github" / "agents").glob("*.agent.md"))
        self.assertEqual(len(paths), 17)
        for path in paths:
            metadata = profile(path)
            self.assertNotIn("model", metadata)
        self.assertEqual(profile(REPO / ".github" / "agents" / "parity-reviewer.agent.md")["tools"], ["read", "search"])
        self.assertEqual(len(list((REPO / ".github" / "skills").glob("*/SKILL.md"))), 3)

    def test_empty_inventory_fails(self):
        data = copy.deepcopy(base_manifest().data)
        data["inventory"] = []
        with self.assertRaisesRegex(PortError, "empty|undersized"):
            self.validate(data)

    def test_duplicate_and_unknown_source_fail(self):
        for mode in ("duplicate", "unknown"):
            with self.subTest(mode=mode):
                data = copy.deepcopy(base_manifest().data)
                entry = copy.deepcopy(data["inventory"][0])
                if mode == "unknown":
                    entry["path"] = "MC1/NewUnownedSource.swift"
                data["inventory"].append(entry)
                with self.assertRaisesRegex(PortError, "Duplicate|Unknown"):
                    self.validate(data)

    def test_unowned_missing_source_fails(self):
        data = copy.deepcopy(base_manifest().data)
        data["inventory"].pop()
        with self.assertRaisesRegex(PortError, "Unowned"):
            self.validate(data)

    def test_unknown_owner_and_source_hash_fail(self):
        for field, value in (("primary_owner", "WP-999"), ("blob_sha", "0" * 40)):
            with self.subTest(field=field):
                data = copy.deepcopy(base_manifest().data)
                entry = next(e for e in data["inventory"] if e["primary_owner"] is not None)
                entry[field] = value
                with self.assertRaisesRegex(PortError, "Unknown|hash mismatch"):
                    self.validate(data)

    def test_reference_and_plan_hash_drift_fail(self):
        for field in ("commit", "tree_sha", "source_inventory_sha256", "installed_plan_sha256"):
            with self.subTest(field=field):
                data = copy.deepcopy(base_manifest().data)
                data["reference"][field] = "0" * len(data["reference"][field])
                with self.assertRaises(PortError):
                    self.validate(data)

    def test_plan_owner_dependency_and_gate_drift_fail(self):
        for field, value in (("owner", "services-porter"), ("depends_on", []), ("human_gate", False)):
            with self.subTest(field=field):
                data = copy.deepcopy(base_manifest().data)
                data["work_packages"][1][field] = value
                with self.assertRaisesRegex(PortError, "drift"):
                    self.validate(data)

    def test_unknown_dependency_and_cycle_fail(self):
        for mode in ("unknown", "cycle"):
            with self.subTest(mode=mode):
                data = copy.deepcopy(base_manifest().data)
                wp = next(w for w in data["work_packages"] if w["id"] == "WP-101")
                wp["depends_on"] = ["WP-999"] if mode == "unknown" else ["WP-102"]
                with self.assertRaisesRegex(PortError, "Unknown dependency|cycle"):
                    self.validate(data)

    def test_empty_or_readonly_write_lease_fails(self):
        for paths in ([], ["MC1/"], [".git/config"]):
            with self.subTest(paths=paths):
                data = copy.deepcopy(base_manifest().data)
                data["work_packages"][0]["write_paths"] = paths
                with self.assertRaises(PortError):
                    self.validate(data)

    def test_execution_state_cannot_be_faked_in_manifest(self):
        data = copy.deepcopy(base_manifest().data)
        data["work_packages"][0]["planned_state"] = "completed"
        with self.assertRaises(PortError):
            self.validate(data)

    def test_removed_billing_generated_and_apple_glue_are_reviewed(self):
        reasons = Counter(e["reason_code"] for e in base_manifest().exclusions["entries"])
        self.assertEqual(set(reasons), {"removed-billing", "generated-swift", "apple-only-glue"})
        for entry in base_manifest().exclusions["entries"]:
            self.assertTrue(entry["review_basis"])
            self.assertTrue(entry["android_adaptation"])
            self.assertIn(entry["adaptation_wp"], base_manifest().work_packages)

    def test_fake_translation_exclusion_fails(self):
        data = copy.deepcopy(base_manifest().data)
        exclusions = copy.deepcopy(base_manifest().exclusions)
        entry = next(e for e in data["inventory"] if e["path"].endswith("/TranslationSessionLauncher.swift"))
        entry["exclusion"], entry["primary_owner"] = "apple-only-glue", None
        exclusions["entries"].append({
            "path": entry["path"], "blob_sha": entry["blob_sha"], "reason_code": "apple-only-glue",
            "adaptation_wp": "WP-406", "review_basis": "fake", "android_adaptation": "fake",
        })
        with self.assertRaisesRegex(PortError, "translation"):
            self.validate(data, exclusions)

    def amendment(self, **overrides):
        entry = {
            "path": "MeshCore/Tests/MeshCoreTests/Session/MeshCoreSessionCommandCorrelationTests.swift",
            "original_blob_sha": "714887f7ab5dae5c1ef9324502c8dc44b2b736ff",
            "approved_blob_sha": "9ff64d684a9ea56cdcfebabe0288c7b8b2141e4f",
            "adr": "docs/android/adr/003-frozen-swift-pin-exception-flaky-correlation-test.md",
            "reason": "Fixes a genuine task-ordering race; test-only.",
        }
        entry.update(overrides)
        return {"schema_version": 1, "reference_sha": REFERENCE_SHA, "entries": [entry]}

    def test_real_correlation_test_amendment_passes_baseline_and_adr_checks(self):
        validate_manifest(
            base_manifest().data, base_manifest().exclusions, REPO,
            verify_checkout=False, amendments=self.amendment(),
        )

    def test_amendment_outside_tests_directory_fails(self):
        with self.assertRaisesRegex(PortError, "test-only path"):
            validate_manifest(
                base_manifest().data, base_manifest().exclusions, REPO,
                verify_checkout=False, amendments=self.amendment(path="MC1/MC1App.swift"),
            )

    def test_amendment_with_wrong_original_blob_fails(self):
        with self.assertRaisesRegex(PortError, "baseline drift"):
            validate_manifest(
                base_manifest().data, base_manifest().exclusions, REPO,
                verify_checkout=False, amendments=self.amendment(original_blob_sha="0" * 40),
            )

    def test_amendment_with_missing_adr_fails(self):
        with self.assertRaisesRegex(PortError, "missing its ADR"):
            validate_manifest(
                base_manifest().data, base_manifest().exclusions, REPO,
                verify_checkout=False, amendments=self.amendment(adr="docs/android/adr/999-does-not-exist.md"),
            )

    def test_amendment_reference_sha_drift_fails(self):
        amendments = self.amendment()
        amendments["reference_sha"] = "0" * 40
        with self.assertRaisesRegex(PortError, "Reference pin changed"):
            validate_manifest(
                base_manifest().data, base_manifest().exclusions, REPO,
                verify_checkout=False, amendments=amendments,
            )

    def test_duplicate_json_keys_nonfinite_unknown_fields_and_wrong_types_fail(self):
        for text in ('{"a":1,"a":2}', '{"a":NaN}', "{broken"):
            with self.subTest(text=text), self.assertRaises(PortError):
                decode_json(text)
        for value in ({"value": True}, {"value": 1, "extra": 2}, {}):
            with self.subTest(value=value), self.assertRaises(PortError):
                check_schema(value, {
                    "type": "object", "additionalProperties": False, "required": ["value"],
                    "properties": {"value": {"type": "integer"}},
                })


class PathTests(unittest.TestCase):
    def test_ancestor_directory_literal_file_and_case_conflicts(self):
        for left, right in (
            ("android/core/data/", "android/core/data"),
            ("android/core/data", "android/core/data/backup/"),
            ("android/core/data/**", "android/core/data/backup/Codec.kt"),
            (".github/", ".github/agents/parity-reviewer.agent.md"),
            ("docs/android/", "docs/android/port-manifest.json"),
            ("android/core/database/schemas/", "android/core/database/schemas/1.json"),
            ("android/gradle/", "android/gradle/libs.versions.toml"),
            ("android/app/AppContainer.kt", "ANDROID/APP/APPCONTAINER.KT"),
        ):
            with self.subTest(left=left, right=right):
                self.assertTrue(overlaps(left, right))
                self.assertTrue(overlaps(right, left))

    def test_adjacent_paths_do_not_conflict(self):
        self.assertFalse(overlaps("android/core/data/", "android/core/datastore/"))
        self.assertFalse(overlaps("docs/android/WP-101.md", "docs/android/WP-102.md"))

    def test_unsafe_and_git_internal_paths_fail(self):
        for path in (
            "", "/absolute", "C:/absolute", "..", "a/../b", "a//b", "a\\b", "a/\n",
            "a/\tfile", "a/\x7ffile", "a/<b", 'a/"b', "a/|b", "a/CON.txt",
            "a/name.", "a/name ", ".git/config", "nested/.GIT/hooks/pre-commit",
            "a/*.kt", "a/[x]", "a/?",
        ):
            with self.subTest(path=path), self.assertRaises(PortError):
                git_path(path, subtree=True)

    def test_all_write_paths_are_enforced_no_shared_file_exceptions(self):
        allowed = ["android/core/data/backup/", "docs/android/evidence/WP-203/"]
        validate_writes(allowed, ["android/core/data/backup/Codec.kt", "docs/android/evidence/WP-203/run.json"])
        for path in (
            "android/gradle/libs.versions.toml", "docs/android/port-manifest.json",
            "android/app/AppContainer.kt", "android/core/database/schemas/1.json",
            ".github/workflows/android-ci.yml", "android/core/l10n/strings.xml",
        ):
            with self.subTest(path=path), self.assertRaisesRegex(PortError, "outside"):
                validate_writes(allowed, [path])

    def test_selector_empty_unmatched_duplicate_unknown_and_unowned_fail(self):
        paths = ["a.swift", "b.swift"]
        scenarios = (
            [], [{"pattern": "", "owner": "WP-101"}],
            [{"pattern": "z*", "owner": "WP-101"}],
            [{"pattern": "*", "owner": "WP-101"}, {"pattern": "a*", "owner": "WP-101"}],
            [{"pattern": "*", "owner": "WP-999"}],
            [{"pattern": "a*", "owner": "WP-101"}],
        )
        for selectors in scenarios:
            with self.subTest(selectors=selectors), self.assertRaises(PortError):
                expand_selectors(selectors, paths, {"WP-101"})
        self.assertEqual(expand_selectors([{"pattern": "*", "owner": "WP-101"}], paths, {"WP-101"}),
                         {"a.swift": "WP-101", "b.swift": "WP-101"})
