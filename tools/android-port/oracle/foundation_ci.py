"""AndroidOnly: WP-004 Execute/verify the actual new helper suite with the WP-003 launcher."""

import argparse
import os
import shutil
import sys
from pathlib import Path
from xml.etree import ElementTree

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from controller.ci import execute, preflight
from controller.ci_environment import candidate_environment, verify_wrapper
from controller.errors import PortError
from controller.schema import load_json
from oracle.identity import evidence_identity
from oracle.reference import OracleError, REPO, SOURCE_SHA, json_bytes, sha256, write_or_check

TASKS = (":core:testing:testDebugUnitTest", "validateModuleGraph")
EXPECTED_TESTS = {
    "TestClockTest": {
        "alreadyCancelledSleepThrowsAndDoesNotPark", "cancellingAfterParkThrowsAndClearsSleeperCount",
        "advancingWakesAnUncancelledSleeper", "pastAndCurrentDeadlinesReturnWithoutParking",
        "onlyDueSleepersWakeAndCancelledSleeperDoesNotAffectAnother", "wakingConsumerCanRegisterAnotherSleep",
        "negativeAndZeroAdvancesKeepPinnedSourceBehavior", "cancellationRacingAdvanceNeverDoubleResumesOrLeaks",
        "infiniteTimeIsAnExplicitInvalidInput",
    },
    "TestPollingTest": {
        "initiallyTrueConditionDoesNotAdvanceTime", "conditionCanBecomeTrueOnTheFinalDeadlineCheck",
        "intervalOvershootStillPerformsThePinnedFinalCheck", "zeroAndNegativeTimeoutsCheckOnceWithoutSleeping",
        "defaultTimeoutAndIntervalUseOnlyInjectedVirtualTime", "cancellationPropagatesAndRemovesTheClockSleeper",
        "conditionFailureIsNotConvertedToTimeoutOrSuccess", "invalidPollingIntervalsFailRatherThanSpinForever",
    },
    "TestSupportTest": {
        "trackerStartsEmptyAndRecordsEveryInvocation", "trackerDoesNotLoseCallsFromConcurrentCallbacks",
        "mutableBoxCapturesNullableValuesWithoutAnUnsafeCast",
    },
    "ScriptedOperationTest": {
        "recordedSnapshotsDoNotChangeWhenLaterCallsArrive",
        "scriptedOperationsPreserveOrderingAndSurfaceFailures", "missingConsumerIsNotSuccessShapedFakeCoverage",
        "scriptedSuspensionRetainsCancellationAndNoFabricatedResult",
        "testContainerUsesTheCallingTestSchedulerAndOwnedBackgroundScope",
    },
    "ReferenceVectorFixtureTest": {
        "actualIndependentFixturesAreDiscoveredAndDecoded", "signedJvmBytesPreserveHighBitAndSourceEndianBytes",
        "immutableFixtureBytesHaveContentEqualityAndDefensiveCopies", "duplicateMissingZeroAndStaleFixtureEvidenceFail",
        "invalidHexByteCountAndSourceFieldsFail", "malformedUtf8IsNotSilentlyReplaced",
        "oversizedFixtureInputFailsBeforeDecoding",
    },
}
PACKAGE = "com.meshcoreone.android.core.testing."


def verify_junit(directory: Path):
    files = sorted(directory.glob("TEST-*.xml"))
    if not files:
        raise OracleError("Missing actual core-testing JUnit evidence")
    found, report = {}, []
    for path in files:
        try:
            root = ElementTree.parse(path).getroot()
        except ElementTree.ParseError as error:
            raise OracleError("Malformed actual helper JUnit evidence") from error
        if root.tag != "testsuite" or not root.get("name", "").startswith(PACKAGE):
            raise OracleError("Unknown helper test suite")
        name = root.get("name").removeprefix(PACKAGE)
        if name not in EXPECTED_TESTS or name in found:
            raise OracleError("Duplicate/unknown helper suite")
        cases = root.findall("testcase")
        identities = [case.get("name") for case in cases]
        if len(identities) != len(set(identities)) or set(identities) != EXPECTED_TESTS[name]:
            raise OracleError(f"Missing/unknown/zero actual assertions in {name}")
        if any(case.find(tag) is not None for case in cases for tag in ("failure", "error", "skipped")):
            raise OracleError("Failed/error/skipped mandatory helper case")
        for attribute, count in (("tests", len(cases)), ("failures", 0), ("errors", 0), ("skipped", 0)):
            if root.get(attribute) != str(count):
                raise OracleError("JUnit claimed counts disagree with actual case nodes")
        found[name] = identities
        report.append({"suite": root.get("name"), "cases": sorted(identities), "discovered": len(cases),
                       "passed": len(cases), "failed": 0, "errors": 0, "skipped": 0,
                       "xml_sha256": sha256(path.read_bytes())})
    if set(found) != set(EXPECTED_TESTS):
        raise OracleError("Missing mandatory new core-testing suite")
    return {"discovered": sum(len(names) for names in found.values()),
            "passed": sum(len(names) for names in found.values()),
            "failed": 0, "errors": 0, "skipped": 0, "suites": report}


def run_kotlin(state, output, *, local=False):
    output.mkdir(parents=True, exist_ok=True)
    preflight(state, output, local=local)
    verify_wrapper()
    environment = candidate_environment(state, local=local)
    options = [
        "--no-daemon", "--console=plain", "--dependency-verification", "strict",
        "--no-build-cache", "--rerun-tasks", "--max-workers=1",
        "-Pkotlin.compiler.execution.strategy=in-process",
        "-PscaffoldTestHeap=" + ("256m" if local else "512m"), "--quiet",
        "--project-cache-dir", str(Path(state["private_root"]) / "project-root"),
    ]
    if local:
        options.append("-PscaffoldTestJvmArgs=-Xms32m -XX:+UseSerialGC -XX:ActiveProcessorCount=2 -XX:TieredStopAtLevel=1 -XX:ReservedCodeCacheSize=32m -XX:MaxMetaspaceSize=256m")
    wrapper = REPO / "android" / ("gradlew.bat" if state["host"] == "windows" else "gradlew")
    command = [str(wrapper), "-p", str(REPO / "android"), *TASKS, *options]
    if state["host"] == "windows":
        shell = shutil.which("pwsh", path=environment.get("PATH"))
        if shell is None:
            raise OracleError("Missing PowerShell 7 for the unchanged credential-stripped Windows launcher")
        invocation = output / "invocation.json"
        write_or_check(invocation, json_bytes({"wrapper": str(wrapper), "project": str(REPO / "android"),
                                             "arguments": [*TASKS, *options]}), check=False)
        command = [shell, "-NoLogo", "-NoProfile", "-NonInteractive", "-File",
                   str(REPO / "tools" / "android-port" / "controller" / "gradle_windows.ps1"),
                   "-InvocationFile", str(invocation)]
    execute(command, environment, output / "core-testing.log")
    directory = REPO / "android" / "core" / "testing" / "build" / "test-results" / "testDebugUnitTest"
    report = verify_junit(directory)
    destination = output / "junit"
    destination.mkdir(exist_ok=True)
    for path in sorted(directory.glob("TEST-*.xml")):
        shutil.copyfile(path, destination / path.name)
    write_or_check(output / "kotlin-evidence.json", json_bytes({
        "schema_version": 1, "work_package": "WP-004", "source_sha": SOURCE_SHA,
        "tasks": list(TASKS), "strict_dependency_verification": True,
        "scope": "actual deterministic helper/fixture assertions; not protocol/app/device parity",
        "identity": evidence_identity(),
        "tests": report,
    }), check=False)
    return report


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("kotlin", "junit"))
    parser.add_argument("--state", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--junit", type=Path)
    parser.add_argument("--local", action="store_true")
    args = parser.parse_args(argv)
    try:
        if args.command == "junit":
            if args.junit is None:
                raise OracleError("Actual JUnit directory is required")
            result = verify_junit(args.junit)
        else:
            state = args.state or os.environ.get("ANDROID_CI_STATE")
            output = args.output or os.environ.get("ANDROID_CI_OUTPUT")
            if state is None or output is None or not Path(output).is_absolute():
                raise OracleError("Explicit private toolchain state and absolute evidence output are required")
            result = run_kotlin(load_json(Path(state)), Path(output), local=args.local)
        print(json_bytes(result).decode(), end="")
        return 0
    except (PortError, OSError, ValueError, KeyError) as error:
        print(f"BLOCKED: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
