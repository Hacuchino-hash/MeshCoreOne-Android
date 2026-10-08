"""AndroidOnly: WP-003 Deterministic isolation/archive tests, not live-host evidence."""

import copy
import hashlib
import io
import os
import tempfile
import tarfile
import unittest
import xml.etree.ElementTree as ET
import zipfile
from dataclasses import asdict
from pathlib import Path
from unittest.mock import patch

from fixtures import REPO
from controller.ci_environment import candidate_environment, host_name, toolchain_lock, verify_wrapper
from controller.errors import PortError
from controller.gates import Binding, policy_revision
from controller.model import git, load_manifest
from controller.provision import download_archive, extract_archive, local_package_xml, provision, safe_member, sdk_metadata
from controller.schema import load_json


class EnvironmentTests(unittest.TestCase):
    def state(self, directory):
        return {
            "schema_version": 1, "host": host_name(), "java_home": str(directory / "jdk"),
            "android_home": str(directory / "sdk"), "private_root": str(directory / "private"),
            "provenance": {"scope": "fixture only"},
        }

    def forwarding_identity(self):
        manifest = load_manifest(REPO)
        rules = load_json(REPO / "docs" / "android" / "automation-policy.json")
        head = git(REPO, "rev-parse", "HEAD").decode().strip()
        return {
            "binding": asdict(Binding(
                "cbattlegear/MeshCoreOne-Android", "WP-003", "1" * 40, head,
                manifest.data["reference"]["commit"], manifest.sha256, policy_revision(manifest, rules),
            )),
            "run_id": 123, "run_attempt": 2,
        }

    def captured_stage(self, directory, host, stage, identity, *, local=False):
        from controller.ci import run_stage

        state = {**self.state(directory), "host": host}
        output = directory / "evidence"
        calls = []

        def execute(command, environment, log, **kwargs):
            calls.append((command, environment, log, kwargs))
            if log.name == f"gradle-{stage}.log":
                raise PortError("Declared command fixture failure; never a real JVM run")

        with (
            patch("controller.ci_environment.host_name", return_value=host),
            patch("controller.ci.execution_identity", return_value=identity) as observe_identity,
            patch("controller.ci.verify_wrapper"),
            patch("controller.ci.shutil.which", return_value="fixture-pwsh"),
            patch("controller.ci.execute", side_effect=execute),
            patch.dict(os.environ, {"GH_TOKEN": "fixture-only", "GITHUB_TOKEN": "fixture-only",
                                    "ANDROID_CI_OUTPUT": str(output)}),
            self.assertRaises(PortError),
        ):
            run_stage(stage, state, output, local=local)
        self.assertEqual(2, len(calls))
        self.assertFalse((output / f"stage-{stage}.json").exists(), "A failed command cannot publish success")
        if host == "windows":
            wrapper_record = load_json(output / f"gradle-{stage}-invocation.json")
            arguments = wrapper_record["arguments"]
            self.assertIn("-InvocationFile", calls[1][0])
            self.assertEqual(str(output / f"gradle-{stage}-invocation.json"), calls[1][0][-1])
        else:
            arguments = calls[1][0][3:]
        for key in ("GH_TOKEN", "GITHUB_TOKEN", "ANDROID_CI_OUTPUT", "GITHUB_RUN_ID", "GITHUB_EVENT_PATH"):
            self.assertNotIn(key, calls[1][1], "No credential or disallowed parent environment forwarding")
        self.assertIn("--dependency-verification", arguments)
        self.assertEqual("strict", arguments[arguments.index("--dependency-verification") + 1])
        self.assertNotIn("--rerun-tasks", arguments)
        self.assertNotIn("--no-build-cache", arguments)
        self.assertNotIn("--max-workers=1", arguments)
        return output, state, arguments, observe_identity.call_count

    def test_meshcli_forwarding_uses_actual_arguments_and_bound_json_on_both_hosts(self):
        identity = self.forwarding_identity()
        for host in ("linux", "windows"):
            for stage in ("scaffold", "protocol"):
                with self.subTest(host=host, stage=stage), tempfile.TemporaryDirectory() as temporary:
                    output, state, arguments, calls = self.captured_stage(Path(temporary), host, stage, identity)
                    self.assertEqual(1, calls)
                    self.assertEqual({
                        "schema_version": 1, "stage": stage, "identity": identity, "host": state["host"],
                    }, load_json(output / "wp109-invocation.json"))
                    self.assertIn("-PmeshCliEvidenceDirectory=" + str(output / "wp109"), arguments)
                    self.assertIn("-PmeshCliInvocationFile=" + str(output / "wp109-invocation.json"), arguments)
                    self.assertEqual(2, len([arg for arg in arguments if arg.startswith("-PmeshCli")]))

    def test_meshcli_local_none_identity_is_explicit_and_keeps_existing_local_budgets(self):
        for host in ("linux", "windows"):
            with self.subTest(host=host), tempfile.TemporaryDirectory() as temporary:
                output, _, arguments, _ = self.captured_stage(Path(temporary), host, "protocol", None, local=True)
                value = load_json(output / "wp109-invocation.json")
                self.assertIsNone(value["identity"])
                self.assertEqual(host, value["host"])
                self.assertIn("-PscaffoldTestHeap=256m", arguments)
                self.assertTrue(any(arg.startswith("-PscaffoldTestJvmArgs=") for arg in arguments))

    def test_runtime_failure_evidence_forwarding_is_exact_verify_only_on_each_executor(self):
        identity = self.forwarding_identity()
        for host in ("linux", "windows"):
            for stage in ("scaffold", "protocol", "prepare"):
                with self.subTest(host=host, stage=stage), tempfile.TemporaryDirectory() as temporary:
                    output, _, arguments, _ = self.captured_stage(Path(temporary), host, stage, identity)
                    forwarded = [arg for arg in arguments if arg.startswith("-Pwp207")]
                    expected = ["-Pwp207EvidenceDirectory=" + str(output / "wp207-native")] if stage == "scaffold" else []
                    self.assertEqual(expected, forwarded)

    def test_meshcli_real_executor_identity_preserves_event_head_base_run_attempt_and_host(self):
        from controller.ci import meshcli_evidence_options
        from controller.ci_environment import write_json

        identity = self.forwarding_identity()
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            event = directory / "event.json"
            write_json(event, {"pull_request": {
                "base": {"repo": {"full_name": "cbattlegear/MeshCoreOne-Android"},
                         "sha": identity["binding"]["base_sha"]},
                "head": {"sha": identity["binding"]["head_sha"]},
            }})
            with patch.dict(os.environ, {
                "GITHUB_EVENT_PATH": str(event), "GITHUB_EVENT_NAME": "pull_request",
                "GITHUB_REPOSITORY": "cbattlegear/MeshCoreOne-Android",
                "GITHUB_RUN_ID": "123", "GITHUB_RUN_ATTEMPT": "2",
            }):
                options = meshcli_evidence_options("scaffold", self.state(directory), directory / "evidence")
            value = load_json(directory / "evidence" / "wp109-invocation.json")
            self.assertEqual(identity, value["identity"])
            self.assertEqual(host_name(), value["host"])
            self.assertEqual("scaffold", value["stage"])
            self.assertEqual(2, len(options))

    def test_meshcli_forwarding_does_not_touch_other_declared_stages(self):
        for host in ("linux", "windows"):
            for stage in ("prepare",):
                with self.subTest(host=host, stage=stage), tempfile.TemporaryDirectory() as temporary:
                    output, _, arguments, calls = self.captured_stage(Path(temporary), host, stage, None)
                    self.assertEqual(0, calls)
                    self.assertFalse((output / "wp109-invocation.json").exists())
                    self.assertFalse(any(arg.startswith("-PmeshCli") for arg in arguments))

    def test_wp301_native_evidence_property_uses_actual_verify_arguments_only_on_both_hosts(self):
        for host in ("linux", "windows"):
            for stage in ("scaffold", "protocol", "prepare"):
                with self.subTest(host=host, stage=stage), tempfile.TemporaryDirectory() as temporary:
                    output, _, arguments, _ = self.captured_stage(Path(temporary), host, stage, None)
                    actual = [arg for arg in arguments if arg.startswith("-Pwp301EvidenceDirectory=")]
                    expected = ["-Pwp301EvidenceDirectory=" + str(output / "wp301-native")] if stage == "scaffold" else []
                    self.assertEqual(expected, actual)

    def test_meshcli_unknown_malformed_or_stale_identity_blocks_before_any_jvm_command(self):
        from controller.ci import run_stage

        valid = self.forwarding_identity()
        variants = [
            {}, {**valid, "binding": {}}, {**valid, "run_id": True}, {**valid, "run_attempt": 0},
            {**valid, "binding": {**valid["binding"], "head_sha": "2" * 40}},
            {**valid, "binding": {**valid["binding"], "base_sha": "malformed"}},
            {**valid, "binding": {**valid["binding"], "repository": "other/repository"}},
        ]
        for identity in variants:
            with self.subTest(identity=identity), tempfile.TemporaryDirectory() as temporary:
                directory = Path(temporary)
                with (
                    patch("controller.ci.execution_identity", return_value=identity),
                    patch("controller.ci.verify_wrapper"),
                    patch("controller.ci.execute") as execute,
                    self.assertRaises(PortError),
                ):
                    run_stage("protocol", self.state(directory), directory / "evidence")
                execute.assert_not_called()
                self.assertFalse((directory / "evidence" / "wp109-invocation.json").exists())
                self.assertFalse((directory / "evidence" / "stage-protocol.json").exists())

    def test_meshcli_missing_malformed_or_zero_reports_never_publish_a_successful_stage(self):
        from controller.ci import run_stage

        for data in (None, b"<testsuite", b'<testsuite tests="0" failures="0" errors="0" skipped="0"/>'):
            with self.subTest(data=data), tempfile.TemporaryDirectory() as temporary:
                directory = Path(temporary)
                reports = directory / "android" / "core" / "protocol" / "build" / "test-results" / "test"
                reports.mkdir(parents=True)
                if data is not None:
                    (reports / "TEST-fixture.xml").write_bytes(data)
                with (
                    patch("controller.ci.REPO", directory),
                    patch("controller.ci.execution_identity", return_value=None),
                    patch("controller.ci.verify_wrapper"),
                    patch("controller.ci.execute"),
                    patch("controller.ci.shutil.which", return_value="fixture-pwsh"),
                    self.assertRaises(PortError),
                ):
                    run_stage("protocol", self.state(directory), directory / "evidence")
                self.assertFalse((directory / "evidence" / "stage-protocol.json").exists())

    def test_parent_allowlist_is_preserved_and_credentials_are_not_inherited(self):
        with tempfile.TemporaryDirectory() as temporary:
            state = self.state(Path(temporary))
            inherited = {
                "PATH": "fixture-path", "GH_TOKEN": "fixture-not-a-secret",
                "GITHUB_TOKEN": "fixture-not-a-secret", "COPILOT_GITHUB_TOKEN": "fixture-not-a-secret",
                "ACTIONS_RUNTIME_TOKEN": "fixture-not-a-secret", "SIGNING_PASSWORD": "fixture-not-a-secret",
                "ANDROID_PORT_GITHUB_TOKEN": "fixture-not-a-secret", "NODE_OPTIONS": "fixture-options",
                "JAVA_OPTS": "-Dcredential=fixture", "GRADLE_OPTS": "-Dcredential=fixture",
                "GRADLE_USER_HOME": str(Path(temporary) / "restored-gradle-cache"),
            }
            value = candidate_environment(state, inherited)
            for key in inherited.keys() - {"PATH", "JAVA_OPTS", "GRADLE_OPTS", "GRADLE_USER_HOME"}:
                self.assertNotIn(key, value)
            self.assertNotIn("credential", value["JAVA_OPTS"] + value["GRADLE_OPTS"])
            self.assertEqual(value["ANDROID_HOME"], state["android_home"])
            self.assertEqual(value["GRADLE_USER_HOME"], inherited["GRADLE_USER_HOME"])
            standalone = candidate_environment(state, inherited, standalone=True)
            self.assertEqual(value["GRADLE_USER_HOME"], standalone["GRADLE_USER_HOME"])

    def test_private_absolute_inputs_and_exact_host_are_required(self):
        with tempfile.TemporaryDirectory() as temporary:
            state = self.state(Path(temporary))
            for key, value in (("java_home", "relative"), ("private_root", ""), ("host", "macos"), ("schema_version", 2)):
                with self.subTest(key=key), self.assertRaises(PortError):
                    candidate_environment({**state, key: value}, {})

    def test_hosted_budgets_do_not_copy_local_shared_host_jit_flags(self):
        with tempfile.TemporaryDirectory() as temporary:
            state = self.state(Path(temporary))
            hosted = candidate_environment(state, {})
            local = candidate_environment(state, {}, local=True)
            self.assertNotIn("TieredStopAtLevel", hosted["GRADLE_OPTS"])
            self.assertIn("TieredStopAtLevel=1", local["GRADLE_OPTS"])
            self.assertIn("-Xmx2048m", hosted["GRADLE_OPTS"])
            self.assertIn("-Xmx768m", local["GRADLE_OPTS"])

    def test_exact_host_archive_sets_and_unchanged_wrapper_pins(self):
        lock = toolchain_lock()
        for host in ("linux", "windows"):
            selected = [a for a in lock["archives"] if a["host"] in ("all", host)]
            self.assertEqual(len(selected), 4)
            self.assertEqual({a["id"] for a in selected}, {
                "jdk", "platforms;android-37.2", "build-tools;37.0.0", "cmdline-tools;23.0",
            })
        verify_wrapper()

    def test_archive_digest_and_size_are_verified_before_extracting(self):
        content = b"publisher-fixture-bytes"
        record = {
            "url": "https://dl.google.com/android/repository/fixture.zip", "size": len(content),
            "sha256": hashlib.sha256(content).hexdigest(), "sha1": hashlib.sha1(content).hexdigest(),
        }
        with tempfile.TemporaryDirectory() as temporary:
            for index, changes in enumerate(({}, {"sha256": "0" * 64}, {"size": 1}, {"sha1": "0" * 40})):
                with self.subTest(changes=changes), patch("urllib.request.urlopen", return_value=io.BytesIO(content)):
                    target = Path(temporary) / f"{index}.zip"
                    if changes:
                        with self.assertRaises(PortError):
                            download_archive({**record, **changes}, target)
                    else:
                        download_archive(record, target)
                        self.assertEqual(target.read_bytes(), content)

    def test_archive_paths_cannot_escape_private_installation(self):
        for name in ("../outside", "/outside", r"C:\outside", "root/../../outside", r"root\outside", "C:outside"):
            with self.subTest(name=name), self.assertRaises(PortError):
                safe_member(name)
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            archive = root / "fixture.zip"
            with zipfile.ZipFile(archive, "w") as output:
                output.writestr("../outside", "fixture")
            with self.assertRaises(PortError):
                extract_archive(archive, root / "private")
            self.assertFalse((root / "outside").exists())

    def test_sdk_xml_preserves_real_type_namespaces_without_duplicate_attributes(self):
        data = b"""<sdk:sdk-repository xmlns:sdk="http://schemas.android.com/sdk/android/repo/repository2/03"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:generic="http://schemas.android.com/repository/android/generic/03">
          <license id="android-sdk-license">fixture terms</license>
          <remotePackage path="build-tools;37.0.0"><type-details xsi:type="generic:genericDetailsType"/>
            <revision><major>37</major></revision><archives/><channelRef ref="channel-0"/>
          </remotePackage></sdk:sdk-repository>"""
        result = local_package_xml(ET.fromstring(data), data, "build-tools;37.0.0")
        parsed = ET.fromstring(result)
        self.assertEqual(parsed.find("localPackage").get("path"), "build-tools;37.0.0")
        self.assertIsNone(parsed.find("localPackage/archives"))
        self.assertIn(b"xmlns:generic=", result)

    def test_changed_sdk_terms_and_metadata_are_not_silently_accepted(self):
        lock = toolchain_lock()
        data = b'<repository><license id="android-sdk-license">changed fixture terms</license></repository>'
        with self.assertRaisesRegex(PortError, "terms changed"):
            sdk_metadata(lock, [], data)
        for data in (b"<!DOCTYPE repository><repository/>", b"<!ENTITY fixture>"):
            with self.subTest(data=data), self.assertRaises(PortError):
                sdk_metadata(lock, [], data)

    def test_both_host_installers_extract_verified_archives_without_executing_or_downloading_cli_helpers(self):
        for host in ("windows", "linux"):
            with self.subTest(host=host), tempfile.TemporaryDirectory() as temporary:
                lock = copy.deepcopy(toolchain_lock())
                terms = b"fixture SDK terms"
                lock["sdk_license_sha256"] = hashlib.sha256(terms).hexdigest()
                lock["sdk_license_sha1"] = hashlib.sha1(terms).hexdigest()
                selected = [a for a in lock["archives"] if a["host"] in ("all", host)]
                downloads = {}
                repository = ET.Element("repository")
                ET.SubElement(repository, "license", {"id": "android-sdk-license"}).text = terms.decode()
                for item in selected:
                    content = io.BytesIO()
                    if item["id"] == "jdk" and host == "linux":
                        with tarfile.open(fileobj=content, mode="w:gz") as archive:
                            file = tarfile.TarInfo("publisher-root/bin/java")
                            file.size = 7
                            archive.addfile(file, io.BytesIO(b"fixture"))
                    else:
                        with zipfile.ZipFile(content, "w") as archive:
                            archive.writestr("publisher-root/source.properties", "fixture only")
                            archive.writestr("publisher-root/bin/android", "must never execute")
                    data = content.getvalue()
                    item.update({"size": len(data), "sha256": hashlib.sha256(data).hexdigest(),
                                 "sha1": None if item["id"] == "jdk" else hashlib.sha1(data).hexdigest()})
                    downloads[item["url"]] = data
                    if item["id"] == "jdk":
                        continue
                    package = ET.SubElement(repository, "remotePackage", {"path": item["id"]})
                    revision = ET.SubElement(package, "revision")
                    for part, value in zip(("major", "minor", "micro"), item["revision"].split("."), strict=True):
                        ET.SubElement(revision, part).text = value
                    archive = ET.SubElement(ET.SubElement(package, "archives"), "archive")
                    if item["host"] != "all":
                        ET.SubElement(archive, "host-os").text = host
                    complete = ET.SubElement(archive, "complete")
                    for key, value in (("size", item["size"]), ("checksum", item["sha1"]), ("url", item["url"].rsplit("/", 1)[1])):
                        ET.SubElement(complete, key).text = str(value)
                downloads[lock["sdk_metadata_url"]] = ET.tostring(repository)
                requested = []

                def fetch(request, **kwargs):
                    url = request if isinstance(request, str) else request.full_url
                    requested.append(url)
                    if url not in downloads:
                        raise AssertionError("Undeclared CLI/helper download")
                    return io.BytesIO(downloads[url])

                with (
                    patch("controller.provision.toolchain_lock", return_value=lock),
                    patch("controller.provision.host_name", return_value=host),
                    patch("urllib.request.urlopen", side_effect=fetch),
                    patch("subprocess.run") as run,
                    patch("subprocess.Popen") as popen,
                ):
                    state = provision(Path(temporary) / "isolated", accept_sdk_license=True)
                run.assert_not_called()
                popen.assert_not_called()
                self.assertEqual(set(requested), set(downloads))
                self.assertEqual(len(requested), 5)
                self.assertFalse(state["provenance"]["cli_bootstrap_executed"])
                self.assertFalse(state["provenance"]["separate_cli_helper_downloaded_or_executed"])
                self.assertEqual(state["provenance"]["sdk_installation"], "verified-archive-extraction-only")

    def test_preflight_reads_installed_metadata_without_executing_the_cli_stub(self):
        from controller.ci import preflight
        from controller.schema import load_json

        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            state = self.state(directory)
            for path, package_id, revision in (
                ("platforms/android-37.2", "platforms;android-37.2", (1, 0, 0)),
                ("build-tools/37.0.0", "build-tools;37.0.0", (37, 0, 0)),
                ("cmdline-tools/23.0", "cmdline-tools;23.0", (23, 0, 0)),
            ):
                target = directory / "sdk" / path / "package.xml"
                target.parent.mkdir(parents=True)
                root = ET.Element("repository")
                package = ET.SubElement(root, "localPackage", {"path": package_id})
                version = ET.SubElement(package, "revision")
                for part, value in zip(("major", "minor", "micro"), revision, strict=True):
                    ET.SubElement(version, part).text = str(value)
                target.write_bytes(ET.tostring(root))
            with patch("controller.ci.execute") as execute:
                preflight(state, directory / "evidence")
            execute.assert_called_once()
            self.assertTrue(execute.call_args.args[0][1].endswith("check_environment.py"))
            self.assertFalse(load_json(directory / "evidence" / "sdk-inventory.json")["cli_bootstrap_executed"])
