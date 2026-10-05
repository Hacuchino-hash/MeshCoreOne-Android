"""WP-204 immutable existing app/data lock configuration records, never generated replacement locks."""

import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys

OUT = Path(__file__).resolve().parent
ROOT = OUT.parents[3]
INITIAL_BASE = "dc15f1ba445acf3230383ea68d4827c592f3fafa"
INPUTS = {
    ":app": (
        "android/gradle/dependency-locks/app.lockfile",
        "android/app/build.gradle.kts",
    ),
    ":core:data": (
        "android/gradle/dependency-locks/core-data.lockfile",
        "android/core/data/build.gradle.kts",
    ),
}
TRACKED_COMPONENTS = (
    "org.jetbrains.kotlin:kotlin-stdlib:",
    "org.jetbrains.kotlinx:kotlinx-coroutines-",
    "org.jetbrains.kotlinx:kotlinx-serialization-",
    "androidx.annotation:annotation",
)


def git(*arguments):
    return subprocess.check_output(["git", "-C", str(ROOT), *arguments])


def observed_report(path, configuration):
    if path is None:
        return None
    raw = path.read_bytes()
    text = raw.decode("utf8")
    matches = list(re.finditer(r"^Project '(:app|:core:data)'\s*$", text, re.MULTILINE))
    if len(matches) != 2:
        raise ValueError("Missing actual app/data dependency report sections")
    sections = []
    for index, match in enumerate(matches):
        end = matches[index + 1].start() if index + 1 < len(matches) else len(text)
        section = text[match.end():end]
        if not re.search(r"^" + re.escape(configuration) + r" - ", section, re.MULTILINE):
            raise ValueError("Actual report configuration mismatch")
        failures = sorted(set(re.findall(r"^[+\\]---\s+(\S+)\s+FAILED\s*$", section, re.MULTILINE)))
        sections.append({
            "module": match.group(1), "configuration": configuration,
            "actually_reported_failed_components": failures,
            "failed_component_count": len(failures),
            "graph_result": "BLOCKED" if failures else "no-reported-component-failures",
            "new_datastore_or_okio_component_in_compile_graph": bool(
                re.search(r"androidx\.datastore:|com\.squareup\.okio:", section),
            ) if configuration.endswith("CompileClasspath") else None,
            "scope": "actual dependency report nodes only; not artifact resolution or a successful build",
        })
    return {
        "configuration": configuration, "raw_sha256": hashlib.sha256(raw).hexdigest(),
        "raw_bytes": len(raw), "sections": sections,
    }


def record(runtime_report=None, compile_report=None, resolved_graphs=None):
    modules = []
    for module, (path, build_path) in INPUTS.items():
        raw = git("show", f"{INITIAL_BASE}:{path}")
        local = ROOT.joinpath(*path.split("/")).read_bytes()
        if resolved_graphs is None and raw.replace(b"\r\n", b"\n") != local.replace(b"\r\n", b"\n"):
            raise ValueError("Unadmitted shared lock changed: " + path)
        build = ROOT.joinpath(*build_path.split("/")).read_text(encoding="utf8")
        if 'implementation(project(":core:datastore"))' not in build:
            raise ValueError("Actual module does not consume datastore: " + module)
        configurations = {}
        for line in raw.decode("utf8").splitlines():
            if not line or line.startswith("#"):
                continue
            component, names = line.split("=", 1)
            for name in names.split(","):
                configurations.setdefault(name, []).append(component)
        rows = [
            {
                "configuration": name,
                "existing_component_count": sum(component != "empty" for component in components),
                "existing_relevant_locked_components": [
                    component for component in components if component.startswith(TRACKED_COMPONENTS)
                ],
                "existing_empty_lock_record": "empty" in components,
            }
            for name, components in sorted(configurations.items())
            if name.startswith(("debug", "release")) and name.endswith(
                ("CompileClasspath", "RuntimeClasspath", "LintChecksClasspath"),
            )
        ]
        modules.append({
            "module": module, "lock_path": path,
            "immutable_base_blob": git("rev-parse", f"{INITIAL_BASE}:{path}").decode().strip(),
            "immutable_base_sha256": hashlib.sha256(raw).hexdigest(),
            "immutable_base_bytes": len(raw),
            "build_path": build_path, "actual_datastore_edge": "implementation",
            "candidate_affected_configurations": rows,
            "narrow_runtime_lint_configuration_proposal": [
                row["configuration"] for row in rows
                if row["configuration"].endswith(("RuntimeClasspath", "LintChecksClasspath"))
            ],
            "compile_configuration_exclusion": "Actual debugCompileClasspath has no new DataStore/Okio transitive "
                                                "component; do not rewrite compile lock records without a separately observed delta",
        })
    generated = []
    if resolved_graphs is not None:
        graph_raw = resolved_graphs.read_bytes()
        lines = graph_raw.decode("utf8").splitlines()
        if not lines or lines[0] != "module\tconfiguration\tcomponent":
            raise ValueError("Malformed actual admitted dependency graph output")
        rows = [line.split("\t") for line in lines[1:]]
        if any(len(row) != 3 for row in rows):
            raise ValueError("Malformed actual dependency graph row")
        for module in modules:
            path = module["lock_path"]
            raw = ROOT.joinpath(*path.split("/")).read_bytes()
            original = git("show", f"{INITIAL_BASE}:{path}").decode("utf8")

            def locked(text):
                return {
                    line.split("=", 1)[0]: set(line.split("=", 1)[1].split(","))
                    for line in text.splitlines() if line and not line.startswith("#")
                }

            before, after = locked(original), locked(raw.decode("utf8"))
            allowed = set(module["narrow_runtime_lint_configuration_proposal"])
            deltas = []
            for component in sorted(set(before) | set(after)):
                added = after.get(component, set()) - before.get(component, set())
                removed = before.get(component, set()) - after.get(component, set())
                if not (added | removed) <= allowed:
                    raise ValueError("Unadmitted shared lock configuration changed: " + component)
                if component not in before and component != "empty" and not component.startswith(
                    ("androidx.datastore:", "com.squareup.okio:", "org.jetbrains.kotlinx:kotlinx-serialization-"),
                ):
                    raise ValueError("New unreviewed shared lock coordinate: " + component)
                if added or removed:
                    deltas.append({"component": component, "added_configurations": sorted(added),
                                   "removed_configurations": sorted(removed)})
            observed = {row[1] for row in rows if row[0] == module["module"]}
            if observed != allowed:
                raise ValueError("Actual resolver did not visit exactly the admitted consumer configurations")
            generated.append({
                "module": module["module"], "lock_path": path, "bytes": len(raw),
                "canonical_lf_sha256": hashlib.sha256(raw.replace(b"\r\n", b"\n")).hexdigest(),
                "actual_resolved_configurations": sorted(observed), "actual_lock_deltas": deltas,
                "new_component_records": len(set(after) - set(before) - {"empty"}),
                "unrelated_configuration_delta": False,
            })
    return {
        "schema_version": 1,
        "work_package": "WP-204", "lease": "autonomous-WP-204-dc15f1ba",
        "cli_app_session": "00cbcd6f-1b3b-4e40-8d44-2ce4216eb72e",
        "initial_base_sha": INITIAL_BASE,
        "observed_head_sha": git("rev-parse", "HEAD").decode().strip(),
        "scope": "actual immutable existing lock configuration records and actual implementation edges; "
                 "not newly resolved coordinates, replacement lock state or successful strict graph verification",
        "proposed_shared_lock_paths": [path for path, _ in INPUTS.values()],
        "unchanged_versions": {
            "datastore_candidate": "1.2.1", "okio_candidate": "3.9.1",
            "catalog_kotlin": "2.3.20", "catalog_coroutines": "1.10.2",
        },
        "modules": modules,
        "actual_generated_consumer_locks": generated,
        "actual_resolved_graphs": {
            "bytes": len(graph_raw), "sha256": hashlib.sha256(graph_raw).hexdigest(),
            "rows": len(rows), "result": "all-visited-dependencies-resolved-no-unresolved-node",
            "scope": "actual selected Gradle component graphs; separate APK/POM/runtime artifact and final CI proof still required",
        } if resolved_graphs is not None else None,
        "actual_read_only_reports": [
            item for item in (
                observed_report(runtime_report, "debugRuntimeClasspath"),
                observed_report(compile_report, "debugCompileClasspath"),
            ) if item is not None
        ],
        "strict_report_attempts": [
            {
                "tasks": ":app:dependencies --configuration debugRuntimeClasspath "
                         ":core:data:dependencies --configuration debugRuntimeClasspath",
                "result": "failed-before-dependency-report",
                "actual_failure": "included-build convention compile failed: "
                                  "org/jetbrains/kotlin/com/google/common/collect/AbstractMapBasedMultimap$KeySet",
            },
            {
                "tasks": "same read-only tasks with --stacktrace",
                "result": "launcher-failed-and-stopped",
                "actual_failure": "PowerShell native error rendering stack overflow under shared-host pressure; "
                                  "only this command tree was stopped",
            },
            {
                "tasks": "same read-only tasks via trusted credential-stripped Gradle Windows launcher/raw Python capture",
                "result": "failed-before-Gradle-at-exact-JDK-preflight",
                "actual_failure": "java.exe -version exit1; subsequent fresh PowerShell "
                                  "startup failed HRESULT0x800705AF/0x80008088 on the shared host",
            },
        ],
        "remaining_action": "After serialized shared admission, generate only the actually affected admitted app/data "
                            "runtime/lint configuration lock state with Gradle and prove strict artifact resolution. "
                            "Compile reports alone do not certify a runtime graph. No --write-locks was used on shared paths.",
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--write", action="store_true")
    parser.add_argument("--runtime-report", type=Path)
    parser.add_argument("--compile-report", type=Path)
    parser.add_argument("--resolved-graphs", type=Path)
    args = parser.parse_args()
    try:
        result = record(args.runtime_report, args.compile_report, args.resolved_graphs)
        if args.write:
            (OUT / "lock-admission.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf8")
            if args.runtime_report is not None:
                (OUT / "runtime-dependency-report.txt").write_bytes(args.runtime_report.read_bytes())
            if args.compile_report is not None:
                (OUT / "compile-dependency-report.txt").write_bytes(args.compile_report.read_bytes())
            if args.resolved_graphs is not None:
                (OUT / "resolved-dependency-graphs.tsv").write_bytes(args.resolved_graphs.read_bytes())
        print(json.dumps({"result": "existing-lock-inputs-recorded", "modules": [
            {"module": row["module"], "configurations": len(row["candidate_affected_configurations"])}
            for row in result["modules"]
        ], "scope": result["scope"]}, indent=2))
        return 0
    except (OSError, ValueError, subprocess.SubprocessError) as failure:
        print(f"BLOCKED: {failure}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
