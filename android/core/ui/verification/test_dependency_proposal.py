# AndroidOnly: WP-304 Adversarial exact actor/PR/command/cap/config/version/write/proposal boundaries.
from __future__ import annotations

import copy
import hashlib
from pathlib import Path
import tempfile
import unittest
import subprocess

from dependency_proposal import (
    BRANCH, OWNER_LOCK, ROOT_LOCK, SETTINGS_BOOKKEEPING, CONFIGURATION_NAME, VM, command, parse_lock, parse_graph, validate_budget,
    validate_command, validate_delta, validate_identity, validate_workflow, validate_writes, ROOT, WORKFLOW,
    validate_settings_bookkeeping, retain_settings_bookkeeping,
    seed_initial_owned_lock,
)
from controller.errors import PortError

SEED = "g:old:1=debugRuntimeClasspath\nempty=androidApis\n"
GENERATED = "g:old:1=debugRuntimeClasspath\ng:new:2=debugUnitTestRuntimeClasspath\nempty=androidApis\n"
CONFIGS = "androidApis\ndebugRuntimeClasspath\ndebugUnitTestRuntimeClasspath\n"
GRAPH = (
    "module\tconfiguration\tkind\tcomponent\n"
    ":core:ui\tandroidApis\tselected\tproject :core:ui\n"
    ":core:ui\tdebugRuntimeClasspath\tselected\tproject :core:ui\n"
    ":core:ui\tdebugRuntimeClasspath\tselected\tg:old:1\n"
    ":core:ui\tdebugUnitTestRuntimeClasspath\tselected\tproject :core:ui\n"
    ":core:ui\tdebugUnitTestRuntimeClasspath\tselected\tg:new:2\n"
)
BOOKKEEPING = b"# Gradle local-file catalog bookkeeping\nempty=incomingCatalogForLibs0\n"


def settings_entry(raw):
    return {"bytes": len(raw), "sha256": hashlib.sha256(raw).hexdigest(), "git_blob": None}


def metadata(*coordinates):
    entries = ""
    for coordinate in coordinates:
        group, name, version = coordinate.split(":")
        entries += f'<component group="{group}" name="{name}" version="{version}"><artifact name="{name}.pom"><sha256 value="{"a" * 64}"/></artifact></component>'
    return ('<verification-metadata xmlns="https://schema.gradle.org/dependency-verification">'
        '<configuration><verify-metadata>true</verify-metadata></configuration><components>' +
        entries + '</components></verification-metadata>').encode()


class DependencyProposalTests(unittest.TestCase):
    def identity(self):
        repository = {"full_name": "cbattlegear/MeshCoreOne-Android"}
        event = {"number": 33, "pull_request": {
            "number": 33, "state": "open", "merged": False, "title": "[WP-304] Shared UI",
            "base": {"ref": "main", "repo": repository, "sha": "a" * 40},
            "head": {"ref": BRANCH, "repo": repository, "sha": "b" * 40},
        }}
        environment = {"GITHUB_EVENT_NAME": "pull_request", "GITHUB_REPOSITORY": repository["full_name"],
            "GITHUB_ACTOR": "cbattlegear", "GITHUB_TRIGGERING_ACTOR": "cbattlegear",
            "GITHUB_RUN_ID": "1", "GITHUB_RUN_ATTEMPT": "1"}
        return event, environment

    def test_exact_normal_pr_actor_and_run(self):
        event, environment = self.identity()
        self.assertEqual("b" * 40, validate_identity(event, environment, "b" * 40)["head"]["sha"])

    def test_wrong_event_repository_actor_triggering_actor_or_run_fails(self):
        event, environment = self.identity()
        for key, value in (
            ("GITHUB_EVENT_NAME", "workflow_dispatch"), ("GITHUB_REPOSITORY", "foreign/repository"),
            ("GITHUB_ACTOR", "unknown"), ("GITHUB_TRIGGERING_ACTOR", "unknown"),
            ("GITHUB_RUN_ID", "0"), ("GITHUB_RUN_ATTEMPT", "missing"),
        ):
            env = environment | {key: value}
            with self.subTest(key=key), self.assertRaises(PortError):
                validate_identity(event, env, "b" * 40)

    def test_wrong_pr_branch_title_base_state_fork_and_stale_sha_fail(self):
        event, environment = self.identity()
        for mutation in ("number", "branch", "title", "base", "closed", "merged", "fork", "sha"):
            value = copy.deepcopy(event)
            if mutation == "number": value["number"] = 34
            elif mutation == "branch": value["pull_request"]["head"]["ref"] = "other"
            elif mutation == "title": value["pull_request"]["title"] = "[WP-207] Foreign"
            elif mutation == "base": value["pull_request"]["base"]["ref"] = "other"
            elif mutation == "closed": value["pull_request"]["state"] = "closed"
            elif mutation == "merged": value["pull_request"]["merged"] = True
            elif mutation == "fork": value["pull_request"]["head"]["repo"]["full_name"] = "fork/repo"
            else: value["pull_request"]["head"]["sha"] = "short"
            with self.subTest(mutation=mutation), self.assertRaises(PortError):
                validate_identity(value, environment, "b" * 40)
        with self.assertRaises(PortError):
            validate_identity(event, environment, "c" * 40)

    def test_exact_owned_resolver_flags_and_cache(self):
        state = {"host": "linux", "private_root": "/private"}
        values = command(state)
        self.assertIn(":core:ui:resolveSharedUiDependencies", values)
        self.assertIn("--write-locks", values)
        self.assertIn("--no-build-cache", values)
        self.assertEqual(values, validate_command(values, state))

    def test_wrong_task_worker_verification_strategy_or_cache_fails(self):
        state = {"host": "linux", "private_root": "/private"}
        original = command(state)
        for old, new in (
            (":core:ui:resolveSharedUiDependencies", "resolveScaffoldDependencies"),
            ("--max-workers=1", "--max-workers=2"), ("strict", "lenient"),
            ("-Pkotlin.compiler.execution.strategy=in-process", "-Pkotlin.compiler.execution.strategy=daemon"),
            (str(Path(state["private_root"]) / "project-ui-proposal"), "/shared"),
        ):
            values = [new if value == old else value for value in original]
            with self.subTest(old=old), self.assertRaises(PortError):
                validate_command(values, state)
        with self.assertRaises(PortError):
            command({"host": "windows", "private_root": "/private"})

    def test_exact_512_heap_metaspace_cap(self):
        environment = {"JAVA_OPTS": "-Xms32m -Xmx128m -XX:+UseSerialGC -XX:ActiveProcessorCount=2 -Dfile.encoding=UTF-8",
            "GRADLE_OPTS": '-Dorg.gradle.jvmargs="' + VM + '"'}
        validate_budget(environment)
        for key in environment:
            with self.assertRaises(PortError):
                validate_budget(environment | {key: environment[key].replace("512m", "2048m") if key == "GRADLE_OPTS" else "unbounded"})

    def test_actual_selected_owned_graph_matches_mechanically_generated_lock(self):
        value = validate_delta(SEED, None, GENERATED, GRAPH, CONFIGS, metadata("g:old:1", "g:new:2"))
        self.assertEqual(3, value["configuration_count"])
        self.assertEqual(2, value["selected_component_rows"])

    def test_actual_gradle_hyphenated_names_are_retained_in_lock_roster_and_graph(self):
        names = "kotlin-extension,unified-test-platform-gradle-work-action"
        generated = GENERATED + "g:framework:3=" + names + "\n"
        configs = CONFIGS + names.replace(",", "\n") + "\n"
        graph = GRAPH + "".join(":core:ui\t" + name + "\tselected\tg:framework:3\n" for name in names.split(","))
        self.assertEqual(set(names.split(",")), set(parse_lock(generated)) - set(parse_lock(GENERATED)))
        result = validate_delta(SEED, None, generated, graph, configs, metadata("g:old:1", "g:new:2", "g:framework:3"))
        self.assertEqual(5, result["configuration_count"])
        self.assertEqual(4, result["selected_component_rows"])

    def test_standard_underscore_name_uses_the_same_bounded_grammar(self):
        generated = GENERATED + "g:framework:3=test_fixture\n"
        graph = GRAPH + ":core:ui\ttest_fixture\tselected\tg:framework:3\n"
        result = validate_delta(SEED, None, generated, graph, CONFIGS + "test_fixture\n",
            metadata("g:old:1", "g:new:2", "g:framework:3"))
        self.assertEqual(4, result["configuration_count"])

    def test_unsafe_blank_excessive_and_duplicate_configuration_names_fail(self):
        for name in ("bad:name", "bad/name", "bad\\name", "bad,name", "bad=name", "",
                "bad name", "bad\tname", "a" * 129, "bad\nname", "bad\rname"):
            with self.subTest(name=name):
                self.assertIsNone(CONFIGURATION_NAME.fullmatch(name))
                if "," not in name:
                    with self.assertRaises(PortError):
                        parse_lock(GENERATED + "g:framework:3=" + name + "\n")
            with self.subTest(roster=name), self.assertRaises(PortError):
                validate_delta(SEED, None, GENERATED, GRAPH, CONFIGS + name + "\n",
                    metadata("g:old:1", "g:new:2"))
        with self.assertRaises(PortError):
            validate_delta(SEED, None, GENERATED, GRAPH, CONFIGS + "debugRuntimeClasspath\n", metadata("g:old:1", "g:new:2"))

    def test_unknown_version_missing_metadata_and_changed_incumbent_fail(self):
        for generated, graph, verified in (
            (GENERATED.replace("g:old:1", "g:old:9"), GRAPH.replace("g:old:1", "g:old:9"), metadata("g:old:9", "g:new:2")),
            (GENERATED, GRAPH, metadata("g:old:1")),
            (GENERATED.replace("g:new:2", "g:unknown:2"), GRAPH.replace("g:new:2", "g:unknown:2"), metadata("g:old:1", "g:new:2")),
        ):
            with self.assertRaises(PortError):
                validate_delta(SEED, None, generated, graph, CONFIGS, verified)

    def test_foreign_config_module_unresolved_zero_and_malformed_graph_fail(self):
        for graph in ("", "module\tconfiguration\tkind\tcomponent\n", GRAPH.replace(":core:ui", ":core:ble"),
            GRAPH.replace("debugRuntimeClasspath", "foreignConfig"), GRAPH.replace("\tselected\t", "\tunresolved\t", 1),
            GRAPH + GRAPH.splitlines()[-1] + "\n"):
            with self.assertRaises(PortError):
                validate_delta(SEED, None, GENERATED, graph, CONFIGS, metadata("g:old:1", "g:new:2"))

    def test_missing_zero_malformed_duplicate_or_empty_populated_lock_fails(self):
        for value in ("", "# comment", "x=y=z", "g:n:1=config,config", "g:n:1=config\nempty=config"):
            with self.subTest(value=value), self.assertRaises(PortError):
                parse_lock(value)

    def test_other_config_and_generated_graph_mismatch_fail(self):
        for generated in (GENERATED + "g:old:1=foreignConfig\n", GENERATED.replace("g:new:2", "g:old:1"),
                GENERATED.replace("debugRuntimeClasspath", "foreignConfig")):
            with self.assertRaises(PortError):
                validate_delta(SEED, None, generated, GRAPH, CONFIGS, metadata("g:old:1", "g:new:2"))

    def test_removed_prior_owned_configuration_or_coordinate_fails(self):
        with self.assertRaises(PortError):
            validate_delta(SEED, GENERATED, GENERATED.replace("g:new:2=debugUnitTestRuntimeClasspath\n", ""),
                GRAPH, CONFIGS, metadata("g:old:1", "g:new:2"))

    def test_late_sdk_seed_configuration_cannot_disappear_from_executed_roster_and_lock(self):
        with self.assertRaises(PortError):
            validate_delta(SEED, None, GENERATED.replace("empty=androidApis\n", ""),
                "\n".join(line for line in GRAPH.splitlines() if "\tandroidApis\t" not in line) + "\n",
                CONFIGS.replace("androidApis\n", ""), metadata("g:old:1", "g:new:2"))

    def test_initial_migration_retains_unexecuted_genuine_seed_sdk_state_exactly(self):
        generated = GENERATED
        roster = CONFIGS.replace("androidApis\n", "")
        graph = "\n".join(line for line in GRAPH.splitlines() if "\tandroidApis\t" not in line) + "\n"
        value = validate_delta(SEED, None, generated, graph, roster, metadata("g:old:1", "g:new:2"))
        self.assertEqual(3, value["configuration_count"])
        row = next(item for item in value["configurations"] if item["configuration"] == "androidApis")
        self.assertEqual([], row["before"])
        self.assertEqual([], row["after"])

    def test_unexecuted_prior_sdk_state_cannot_gain_coordinates_or_change_membership(self):
        roster = CONFIGS.replace("androidApis\n", "")
        graph = "\n".join(line for line in GRAPH.splitlines() if "\tandroidApis\t" not in line) + "\n"
        with self.assertRaises(PortError):
            validate_delta(SEED, GENERATED, GENERATED.replace("empty=androidApis", "g:sdk:2=androidApis"),
                graph, roster, metadata("g:old:1", "g:new:2", "g:sdk:2"))

    def seed_fixture(self, root):
        (root / "android" / "gradle" / "dependency-locks").mkdir(parents=True)
        (root / "android" / "core" / "ui").mkdir(parents=True)
        raw = subprocess.check_output(["git", "-C", str(ROOT), "show", "HEAD:" + ROOT_LOCK])
        (root / ROOT_LOCK).write_bytes(raw)
        output = root / "proposal"
        output.mkdir()
        return raw, output

    def test_absent_owned_lock_is_seeded_only_from_exact_prior_generated_root_bytes(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            raw, output = self.seed_fixture(root)
            prior, record = seed_initial_owned_lock(root, output)
            self.assertIsNone(prior)
            self.assertTrue(record["seeded_from_root"])
            self.assertIsNone(record["preexisting_prior"])
            self.assertEqual(raw, (root / OWNER_LOCK).read_bytes())
            self.assertEqual(raw, (output / "seeded-owned-gradle.lockfile").read_bytes())
            self.assertEqual(raw, (root / ROOT_LOCK).read_bytes())

    def test_existing_owned_state_is_preserved_not_replaced_by_old_root(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            _, output = self.seed_fixture(root)
            existing = GENERATED.encode()
            (root / OWNER_LOCK).write_bytes(existing)
            prior, record = seed_initial_owned_lock(root, output)
            self.assertEqual(existing, prior)
            self.assertFalse(record["seeded_from_root"])
            self.assertEqual(existing, (root / OWNER_LOCK).read_bytes())
            self.assertFalse((output / "seeded-owned-gradle.lockfile").exists())

    def test_corrupt_root_or_partial_existing_owned_state_is_not_silently_replaced(self):
        for partial in (False, True):
            with tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                raw, output = self.seed_fixture(root)
                if partial:
                    (root / OWNER_LOCK).write_bytes(b"partial=unknown=state\n")
                else:
                    (root / ROOT_LOCK).write_bytes(raw + b"# changed source\n")
                with self.assertRaises(PortError):
                    seed_initial_owned_lock(root, output)
                if partial:
                    self.assertEqual(b"partial=unknown=state\n", (root / OWNER_LOCK).read_bytes())
                else:
                    self.assertFalse((root / OWNER_LOCK).exists())

    def test_only_local_ui_lock_change_is_admitted(self):
        before = {ROOT_LOCK: {"sha256": "a"}}
        validate_writes(before, before.copy(), ["?? " + OWNER_LOCK])
        self.assertEqual("android/core/ui/gradle.lockfile", OWNER_LOCK)

    def test_new_validated_ignored_settings_bookkeeping_and_only_owned_untracked_lock_are_admitted(self):
        before = {ROOT_LOCK: {"sha256": "unchanged"}}
        after = before | {SETTINGS_BOOKKEEPING: settings_entry(BOOKKEEPING)}
        validate_writes(before, after, ["?? " + OWNER_LOCK], None, BOOKKEEPING)
        self.assertEqual({ROOT_LOCK: {"sha256": "unchanged"}}, before)

    def test_existing_unchanged_bookkeeping_and_owned_tracked_lock_change_are_admitted(self):
        before = {ROOT_LOCK: {"sha256": "unchanged"}, SETTINGS_BOOKKEEPING: settings_entry(BOOKKEEPING)}
        validate_writes(before, before.copy(), [" M " + OWNER_LOCK], BOOKKEEPING, BOOKKEEPING)

    def test_bookkeeping_unknown_remote_duplicate_empty_non_utf8_or_excessive_content_fails(self):
        for raw in (b"", b"# no record\n", b"empty=other\n", b"g:remote:1=incomingCatalogForLibs0\n",
                BOOKKEEPING + b"empty=incomingCatalogForLibs0\n", BOOKKEEPING + b"g:new:1=other\n",
                BOOKKEEPING + b"\0", b"\xff", b"#" * 4097):
            with self.subTest(raw=raw[:80]), self.assertRaises((PortError, UnicodeError)):
                validate_settings_bookkeeping(raw)

    def test_bookkeeping_removal_changed_root_source_and_untracked_addition_fail(self):
        before = {ROOT_LOCK: {"sha256": "a"}, "MC1/source.swift": {"sha256": "source"},
            SETTINGS_BOOKKEEPING: settings_entry(BOOKKEEPING)}
        changes = ["?? " + OWNER_LOCK]
        for after in (
            {key: value for key, value in before.items() if key != SETTINGS_BOOKKEEPING},
            before | {ROOT_LOCK: {"sha256": "b"}},
            before | {"MC1/source.swift": {"sha256": "changed"}},
            before | {"android/settings-extra.lockfile": {"sha256": "unknown"}},
            {key: value for key, value in before.items() if key != ROOT_LOCK},
        ):
            raw = BOOKKEEPING if SETTINGS_BOOKKEEPING in after else None
            with self.assertRaises(PortError):
                validate_writes(before, after, changes, BOOKKEEPING, raw)

    def test_bookkeeping_requires_raw_matching_size_hash_and_untracked_identity(self):
        base = {ROOT_LOCK: {"sha256": "a"}}
        for entry in (
            settings_entry(BOOKKEEPING) | {"sha256": "bad"},
            settings_entry(BOOKKEEPING) | {"bytes": 1},
            settings_entry(BOOKKEEPING) | {"git_blob": "a" * 40},
        ):
            with self.assertRaises(PortError):
                validate_writes(base, base | {SETTINGS_BOOKKEEPING: entry}, ["?? " + OWNER_LOCK], None, BOOKKEEPING)
        with self.assertRaises(PortError):
            validate_writes(base, base | {SETTINGS_BOOKKEEPING: settings_entry(BOOKKEEPING)}, ["?? " + OWNER_LOCK])

    def test_bookkeeping_bytes_are_retained_before_rejecting_invalid_entries(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "android").mkdir()
            output = root / "proposal"
            output.mkdir()
            raw = b"g:remote:1=incomingCatalogForLibs0\n"
            (root / SETTINGS_BOOKKEEPING).write_bytes(raw)
            preserved = retain_settings_bookkeeping(root, output, "after")
            self.assertEqual(raw, (output / "settings-bookkeeping-after.lockfile").read_bytes())
            with self.assertRaises(PortError):
                validate_settings_bookkeeping(preserved)

    def test_renamed_quoted_unsafe_or_space_paths_are_not_silently_normalized(self):
        base = {ROOT_LOCK: {"sha256": "a"}}
        for line in ("R  " + ROOT_LOCK + " -> " + OWNER_LOCK, '?? "' + OWNER_LOCK + '"',
            "?? " + OWNER_LOCK + " other", "?? ../" + OWNER_LOCK, "?? " + SETTINGS_BOOKKEEPING):
            with self.subTest(line=line), self.assertRaises(PortError):
                validate_writes(base, base, [line])

    def test_root_shared_unknown_tracked_and_untracked_writes_fail_without_restore(self):
        before = {ROOT_LOCK: {"sha256": "a"}}
        with self.assertRaises(PortError):
            validate_writes(before, {ROOT_LOCK: {"sha256": "b"}}, [" M " + ROOT_LOCK])
        for path in (ROOT_LOCK, "android/gradle/verification-metadata.xml", "unknown.txt", "android/core/data/gradle.lockfile"):
            with self.subTest(path=path), self.assertRaises(PortError):
                validate_writes(before, before, ["?? " + path])

    def test_exact_single_readonly_ordinary_workflow(self):
        self.assertFalse(validate_workflow((ROOT / WORKFLOW).read_text(encoding="utf8"))["mandatory_ci_or_gate"])

    def test_wrong_workflow_pr_actor_budget_pin_permissions_or_extra_job_fails(self):
        text = (ROOT / WORKFLOW).read_text(encoding="utf8")
        for changed in (
            text.replace("== 33", "== 34"), text.replace("timeout-minutes: 20", "timeout-minutes: 21"),
            text.replace("contents: read", "contents: write"), text.replace("persist-credentials: false", "persist-credentials: true"),
            text.replace("ubuntu-24.04", "windows-2025"), text.replace("--workflow-check", "--not-a-check"),
        ):
            with self.assertRaises((PortError, ValueError)):
                validate_workflow(changed)


if __name__ == "__main__":
    unittest.main()
