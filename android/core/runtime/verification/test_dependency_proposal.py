"""AndroidOnly: WP-207 Adversarial owned dependency generation DATA boundaries."""

import copy
import unittest

from dependency_proposal import (
    OWNER_LOCK, BRANCH, NEW_COORDINATES, parse_lock, validate_delta, validate_identity,
)
from controller.errors import PortError

OLD = "org.jetbrains.kotlin:kotlin-stdlib:2.3.20=compileClasspath,testCompileClasspath\nempty=annotationProcessor\n"
NEW = OLD + "org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2=testCompileClasspath,testRuntimeClasspath\n"
METADATA = b'<verification-metadata><components><component group="org.jetbrains.kotlinx" name="kotlinx-coroutines-test" version="1.10.2"><artifact name="test.pom"><sha256 value="' + b"a" * 64 + b'"/></artifact></component></components></verification-metadata>'


class DependencyProposalTests(unittest.TestCase):
    def identity(self):
        repository = {"full_name": "cbattlegear/MeshCoreOne-Android"}
        event = {"pull_request": {
            "title": "[WP-207] Connection runtime", "base": {"repo": repository, "sha": "a" * 40},
            "head": {"repo": repository, "sha": "b" * 40, "ref": BRANCH},
        }}
        environment = {"GITHUB_REPOSITORY": repository["full_name"], "GITHUB_EVENT_NAME": "pull_request",
                       "GITHUB_RUN_ID": "1", "GITHUB_RUN_ATTEMPT": "1"}
        return event, environment

    def test_real_pinned_test_only_delta(self):
        self.assertEqual([{"coordinate": "org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2",
                           "configurations": ["testCompileClasspath", "testRuntimeClasspath"]}],
                         validate_delta(OLD, NEW, METADATA))

    def test_missing_empty_zero_and_malformed_locks(self):
        for text in ("", "# empty", "x=y=z", "g:n:1=config,config", "g:n:1=testCompileClasspath\nempty=testCompileClasspath"):
            with self.subTest(text=text), self.assertRaises(PortError):
                parse_lock(text)

    def test_unknown_coordinate_version_and_config_change(self):
        for text in (
            NEW.replace(":1.10.2=", ":1.10.3="),
            NEW.replace("coroutines-test:1.10.2", "unknown:1.10.2"),
            NEW.replace("compileClasspath,testCompileClasspath\n", "compileClasspath\n"),
            NEW.replace("empty=annotationProcessor", "empty=other"),
            NEW.replace("=testCompileClasspath,testRuntimeClasspath", "=runtimeClasspath"),
        ):
            with self.subTest(text=text), self.assertRaises(PortError):
                validate_delta(OLD, text, METADATA)

    def test_removed_old_coordinate_and_missing_checksum_fail(self):
        with self.assertRaises(PortError):
            validate_delta(OLD, NEW.replace(OLD.splitlines()[0] + "\n", ""), METADATA)
        with self.assertRaises(PortError):
            validate_delta(OLD, NEW, b"<verification-metadata><components/></verification-metadata>")

    def test_exact_normal_pr_identity(self):
        event, environment = self.identity()
        self.assertEqual("b" * 40, validate_identity(event, environment, "b" * 40)["head"]["sha"])

    def test_wrong_event_repo_title_branch_sha_and_fork_fail(self):
        event, environment = self.identity()
        mutations = [
            ("env", "GITHUB_EVENT_NAME", "workflow_dispatch"),
            ("env", "GITHUB_REPOSITORY", "other/repo"),
            ("env", "GITHUB_RUN_ATTEMPT", "0"),
            ("env", "GITHUB_RUN_ID", "unknown"),
            ("title", None, "[WP-301] Foreign"),
            ("branch", None, "wrong"),
            ("sha", None, "short"),
            ("repo", None, "fork/repo"),
        ]
        for kind, key, value in mutations:
            candidate = copy.deepcopy(event); env = environment.copy()
            if kind == "env": env[key] = value
            elif kind == "title": candidate["pull_request"]["title"] = value
            elif kind == "branch": candidate["pull_request"]["head"]["ref"] = value
            elif kind == "sha": candidate["pull_request"]["head"]["sha"] = value
            else: candidate["pull_request"]["head"]["repo"]["full_name"] = value
            with self.subTest(kind=kind, key=key), self.assertRaises(PortError):
                validate_identity(candidate, env, "b" * 40)

    def test_stale_checkout_and_missing_pr_fail(self):
        event, environment = self.identity()
        with self.assertRaises(PortError):
            validate_identity(event, environment, "c" * 40)
        with self.assertRaises(PortError):
            validate_identity({}, environment, "b" * 40)

    def test_exact_owned_path_and_new_coordinate_allowlist(self):
        self.assertEqual("android/core/runtime/gradle.lockfile", OWNER_LOCK)
        self.assertEqual(2, len(NEW_COORDINATES))


if __name__ == "__main__":
    unittest.main()
