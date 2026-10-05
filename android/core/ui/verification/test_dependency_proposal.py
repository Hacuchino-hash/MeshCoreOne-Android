# AndroidOnly: WP-304 Adversarial exact actor/PR/command/cap/config/version/write/proposal boundaries.
from __future__ import annotations

import copy
from pathlib import Path
import unittest

from dependency_proposal import (
    BRANCH, OWNER_LOCK, ROOT_LOCK, VM, command, parse_lock, parse_graph, validate_budget,
    validate_command, validate_delta, validate_identity, validate_workflow, validate_writes, ROOT, WORKFLOW,
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

    def test_only_local_ui_lock_change_is_admitted(self):
        before = {ROOT_LOCK: {"sha256": "a"}}
        validate_writes(before, before.copy(), ["?? " + OWNER_LOCK])
        self.assertEqual("android/core/ui/gradle.lockfile", OWNER_LOCK)

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
