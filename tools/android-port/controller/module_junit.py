"""AndroidOnly: WP-003 Retain every active root-module unit suite without claiming source parity."""

import os
import re
import shutil
import stat
from pathlib import Path

from .errors import PortError
from .model import SHA, git
from .paths import git_path
from .schema import fields

MODULE_SOURCE = re.compile(r"android/((?:core|feature|platform)/[a-z][a-z0-9-]*)/(.+)")
UNIT_SOURCE = re.compile(r"src/(?:test|testDebug)/(?:java|kotlin)/.+\.(?:java|kt)")
SEPARATE_SUITES = {"core/contracts", "core/protocol", "core/testing"}
REPORT_LOCATIONS = ("build/test-results/test", "build/test-results/testDebugUnitTest")


def module_sources(repo: Path, revision=None):
    revision = revision or git(repo, "rev-parse", "HEAD").decode().strip()
    if not isinstance(revision, str) or SHA.fullmatch(revision) is None:
        raise PortError("Module unit evidence requires an immutable candidate commit")
    entries, active = {}, set()
    for entry in git(repo, "ls-tree", "-rz", "--full-tree", revision, "--", "android").split(b"\0"):
        if not entry:
            continue
        metadata, raw_path = entry.split(b"\t", 1)
        path = git_path(raw_path.decode("utf-8"))
        match = MODULE_SOURCE.fullmatch(path)
        if match is None or match[1] in SEPARATE_SUITES:
            continue
        module, relative = match.groups()
        if relative != "build.gradle.kts" and not relative.startswith(("src/main/", "src/test/", "src/testDebug/")):
            continue
        mode, kind, blob = metadata.decode("ascii").split()
        if mode not in ("100644", "100755") or kind != "blob" or SHA.fullmatch(blob) is None:
            raise PortError("Unsafe/non-file committed module test input: " + path)
        entries.setdefault(module, {})[path] = blob
        if UNIT_SOURCE.fullmatch(relative):
            active.add(module)
    return {module: dict(sorted(entries[module].items())) for module in sorted(active)}


def bundle_directory(root: Path, module: str):
    if re.fullmatch(r"(?:core|feature|platform)/[a-z][a-z0-9-]*", module) is None:
        raise PortError("Invalid root unit-test module")
    return root / "junit" / "modules" / module.replace("/", "--")


def linked(path: Path):
    if path.is_symlink():
        return True
    return os.name == "nt" and path.exists() and bool(path.lstat().st_file_attributes & stat.FILE_ATTRIBUTE_REPARSE_POINT)


def bounded_directory(directory: Path, root: Path):
    if not directory.resolve().is_relative_to(root.resolve()):
        raise PortError("Unit report directory escapes its bounded root")
    for parent in (directory, *directory.parents):
        if linked(parent):
            raise PortError("Linked unit-report directory is not evidence")
        if parent == root:
            break


def safe_reports(directory: Path, root: Path):
    bounded_directory(directory, root)
    files = sorted(directory.glob("TEST-*.xml"), key=lambda path: path.name)
    if not files:
        raise PortError("Missing mandatory active-module JUnit reports: " + str(directory))
    if any(linked(path) or not path.is_file() for path in files):
        raise PortError("Unsafe active-module JUnit report")
    return files


def report_record(root: Path, directory: Path):
    from .ci_evidence import artifact_record, suite_counts

    files = safe_reports(directory, root)
    result = {
        "counts": suite_counts(directory, 1),
        "reports": [artifact_record(root, path) for path in files],
    }
    return result


def collect_module_tests(repo: Path, output: Path):
    expected = module_sources(repo)
    if output.resolve().is_relative_to(repo.resolve()) or repo.resolve().is_relative_to(output.resolve()):
        raise PortError("Unit evidence must not overwrite project inputs or build reports")
    paths = sorted({path for inputs in expected.values() for path in inputs})
    if paths and git(repo, "diff", "--name-only", "HEAD", "--", *paths).strip():
        raise PortError("Compiled module inputs differ from the immutable candidate")
    destination_root = output / "junit" / "modules"
    bounded_directory(destination_root, output)
    if destination_root.exists():
        raise PortError("Unit evidence destination must be newly created")
    plans = {}
    for module, inputs in expected.items():
        module_root = repo / "android" / module
        candidates = [
            module_root / relative for relative in REPORT_LOCATIONS
            if any((module_root / relative).glob("TEST-*.xml"))
        ]
        if len(candidates) != 1:
            raise PortError("Missing/ambiguous actual unit runner for active module: " + module)
        source = candidates[0]
        original = report_record(repo / "android", source)
        plans[module] = (source, original)
    results = {}
    for module, inputs in expected.items():
        source, original = plans[module]
        destination = bundle_directory(output, module)
        destination.mkdir(parents=True)
        for path in safe_reports(source, repo / "android"):
            shutil.copyfile(path, destination / path.name)
            if path.read_bytes() != (destination / path.name).read_bytes():
                raise PortError("Copied active-module JUnit bytes changed")
        copied = report_record(output, destination)
        original_files = [(Path(item["path"]).name, item["size"], item["sha256"]) for item in original["reports"]]
        copied_files = [(Path(item["path"]).name, item["size"], item["sha256"]) for item in copied["reports"]]
        if copied["counts"] != original["counts"] or copied_files != original_files:
            raise PortError("Copied active-module discovery changed")
        results[module] = {"inputs": inputs, **copied}
    return results


def validate_module_tests(value, root: Path, repo: Path, revision: str):
    from .ci_evidence import counts

    expected = module_sources(repo, revision)
    if not isinstance(value, dict) or set(value) != set(expected):
        raise PortError("Missing/extra active root-module unit evidence")
    actual_paths = set()
    for module, inputs in expected.items():
        record = value[module]
        fields(record, {"inputs", "counts", "reports"}, label="active module unit evidence")
        if record["inputs"] != inputs:
            raise PortError("Active-module evidence has stale/fabricated candidate input blobs")
        counts(record["counts"], minimum=1)
        actual = report_record(root, bundle_directory(root, module))
        if record["counts"] != actual["counts"] or record["reports"] != actual["reports"]:
            raise PortError("Active-module raw JUnit differs from typed discovery/report hashes")
        actual_paths.update(report["path"] for report in actual["reports"])
    present = {path.relative_to(root).as_posix() for path in (root / "junit" / "modules").rglob("TEST-*.xml")}
    if present != actual_paths:
        raise PortError("Unexpected or unaccounted active-module JUnit reports")
    return value
