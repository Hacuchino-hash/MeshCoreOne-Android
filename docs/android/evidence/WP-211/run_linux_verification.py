"""AndroidOnly: WP-211 Declared isolated Linux test executor retaining failures before validation."""

import argparse
import json
from pathlib import Path
import platform
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT / "tools" / "android-port"))
from controller.ci import preflight
from controller.ci_environment import candidate_environment
from controller.schema import load_json
from collect_evidence import retain, validate_retained


def execute(state_path, output, invocation_file=None):
    if platform.system() != "Linux" or platform.machine() != "x86_64":
        raise ValueError("WP-211 JVM execution requires the declared isolated Linux x64 host")
    if not state_path.is_absolute() or not output.is_absolute() or output.exists():
        raise ValueError("Exact existing state and a new absolute private evidence directory are required")
    state = load_json(state_path)
    if state["host"] != "linux":
        raise ValueError("Windows/vendor-template state is not Linux worker readiness")
    output.mkdir(parents=True)
    preflight(state, output)
    environment = candidate_environment(state)
    tasks = [":core:services:test", ":core:data:testDebugUnitTest"]
    arguments = [
        str(ROOT / "android" / "gradlew"), "-p", str(ROOT / "android"), *tasks,
        "--continue", "--no-daemon", "--console=plain", "--dependency-verification", "strict",
        "--no-build-cache", "--rerun-tasks", "--max-workers=1", "--quiet",
        "-Pkotlin.compiler.execution.strategy=in-process", "-PscaffoldTestHeap=512m",
        "--project-cache-dir", str(Path(state["private_root"]) / "project-wp211"),
    ]
    if invocation_file is not None:
        arguments.append("-PmeshCliInvocationFile=" + str(invocation_file))
    arguments.append("-Pwp211EvidenceDirectory=" + str(output / "gradle-retention"))
    outcome = {"schema_version": 1, "tasks": tasks, "arguments": arguments, "host": "linux",
               "exit_code": None, "execution_error": None}
    try:
        with (output / "gradle.log").open("w", encoding="utf-8") as log:
            result = subprocess.run(arguments, cwd=ROOT, env=environment, stdout=log,
                                    stderr=subprocess.STDOUT, timeout=1800, check=False)
        outcome["exit_code"] = result.returncode
    except (OSError, subprocess.TimeoutExpired) as failure:
        outcome["execution_error"] = type(failure).__name__
        raise
    finally:
        (output / "execution.json").write_text(json.dumps(outcome, indent=2) + "\n", encoding="utf-8")
        retain(output / "raw", invocation_file)
    if outcome["exit_code"] != 0:
        raise ValueError("Declared Gradle tests failed; complete raw inputs/JUnit/logs were retained")
    proof = validate_retained(output / "raw", invocation_file)
    print(json.dumps({"output": str(output), "counts": proof["counts"]}, sort_keys=True))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--state", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--invocation-file", type=Path)
    args = parser.parse_args()
    execute(args.state, args.output, args.invocation_file)


if __name__ == "__main__":
    main()
