"""AndroidOnly: WP-211 Read-only frozen catalog/fault checks, never native parity."""

import json
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[4]
SOURCE = "db14559b39d32322b06477c6ae676112f583db50"
NATIVE = ROOT / "android" / "core" / "services" / "src" / "main" / "kotlin"
NATIVE = NATIVE / "com" / "meshcoreone" / "android" / "core" / "services" / "device"


def require(condition, message):
    if not condition:
        raise ValueError(message)


def reference(path):
    return subprocess.check_output(["git", "-C", str(ROOT), "show", SOURCE + ":" + path]).decode("utf-8")


def collection(text, marker, opening, closing, *, initializer=False):
    declaration = text.index(marker)
    if initializer:
        declaration = text.index("=", declaration)
    start = text.index(opening, declaration)
    depth = 0
    quoted = escaped = False
    for position in range(start, len(text)):
        character = text[position]
        if quoted:
            if escaped:
                escaped = False
            elif character == "\\":
                escaped = True
            elif character == '"':
                quoted = False
            continue
        if character == '"':
            quoted = True
        elif character == opening:
            depth += 1
        elif character == closing:
            depth -= 1
            if depth == 0:
                return text[start + 1:position]
    raise ValueError("Unterminated catalog: " + marker)


def check_catalog():
    swift = reference("MC1Services/Sources/MC1Services/Services/RegionalAreas.swift")
    kotlin = (NATIVE / "RegionalAreas.kt").read_text(encoding="utf-8")
    counts = {}
    for region, source_call, property_name in (("US", "us", "usSubdivisions"), ("AU", "au", "auSubdivisions")):
        expected = re.findall(
            rf'{source_call}\("([^"]+)",\s*"([^"]+)"',
            collection(swift, "let " + property_name, "[", "]", initializer=True),
        )
        actual = re.findall(
            rf'subdivision\("{region}",\s*"([^"]+)",\s*"([^"]+)"',
            collection(kotlin, "val " + property_name, "(", ")", initializer=True),
        )
        require(expected and expected == actual, "Missing/reordered/changed subdivision rows: " + region)
        counts[region] = len(expected)
    source_continents = re.findall(
        r'"([A-Z]{2})":\s*\.([A-Za-z]+)',
        collection(swift, "let continents:", "[", "]", initializer=True),
    )
    region_names = {
        "northAmerica": "NORTH_AMERICA", "southAmerica": "SOUTH_AMERICA",
        "europe": "EUROPE", "oceania": "OCEANIA", "asia": "ASIA",
    }
    expected_continents = [(code, region_names[region]) for code, region in source_continents]
    actual_continents = re.findall(
        r'"([A-Z]{2})"\s+to\s+RadioRegion\.([A-Z_]+)',
        collection(kotlin, "val continents:", "(", ")", initializer=True),
    )
    require(expected_continents and expected_continents == actual_continents, "Country/continent catalog drift")
    source_countries = re.findall(
        r'Country\(id:\s*"([^"]+)",\s*subdivisions:\s*(\w+)\)',
        collection(swift, "let countries:", "[", "]", initializer=True),
    )
    actual_countries = re.findall(
        r'Country\("([^"]+)",\s*(\w+)\)', collection(kotlin, "val countries:", "(", ")", initializer=True),
    )
    expected_countries = [(code, "null" if areas == "nil" else areas) for code, areas in source_countries]
    require(expected_countries and expected_countries == actual_countries, "Country picker row/order drift")
    require({code for code, _ in source_countries} == {code for code, _ in source_continents},
            "Country and continent keys disagree")
    expected_counties = re.findall(
        r'"([^"]+)"', collection(swift, "let usCounties:", "[", "]", initializer=True),
    )
    actual_counties = re.findall(
        r'"([^"]+)"', collection(kotlin, "val usCounties:", "(", ")", initializer=True),
    )
    require(expected_counties == actual_counties, "County matcher catalog drift")
    require('"washington dc", "washington d.c."' in kotlin and '["washington dc", "washington d.c."]' in swift,
            "DC alias matcher drift")
    require("lowercase(Locale.ROOT)" in kotlin, "Matcher lowercasing is locale-dependent")
    return {**counts, "countries": len(source_countries), "county_keys": len(expected_counties) - 1}


def check_faults():
    path = ROOT / "android" / "core" / "contracts" / "src" / "main" / "kotlin"
    path = path / "com" / "meshcoreone" / "android" / "core" / "contracts" / "domain" / "errors"
    kotlin = (path / "DeviceSettingsFaults.kt").read_text(encoding="utf-8")
    inputs = (
        ("DeviceServiceError", "MC1Services/Sources/MC1Services/Services/DeviceService.swift",
         ["DeviceNotFound", "PersistenceFailed"]),
        ("SettingsServiceError", "MC1Services/Sources/MC1Services/Errors/SettingsServiceError.swift",
         ["NotConnected", "SendFailed", "InvalidResponse", "SessionError", "VerificationFailed", "DeviceGPSVerificationFailed"]),
    )
    counts = {}
    for fault, source_path, expected in inputs:
        source = reference(source_path)
        source = source[source.index("public enum " + fault):source.index("public var errorDescription")]
        source_cases = re.findall(r"^\s*case\s+(\w+)", source, re.MULTILINE)
        require([name[0].upper() + name[1:] for name in source_cases] == expected, "Frozen fault input drift")
        declaration = collection(kotlin, "sealed interface " + fault, "{", "}")
        native_cases = re.findall(r"data\s+(?:object|class)\s+(\w+)", declaration)
        require(native_cases == expected, "Native fault case drift")
        require("PortedFrom: " + source_path + "@" + SOURCE in kotlin, "Missing exact fault provenance")
        counts[fault] = len(source_cases)
    for field in (
        "val reason: String", "val error: MeshCoreException", "val expected: String", "val actual: String",
        "val expectedEnabled: Boolean", "val actualEnabled: Boolean", "cause: Throwable?",
    ):
        require(field in kotlin, "Missing typed fault field: " + field)
    require("NotConnected, SendFailed -> true" in kotlin and "error is MeshCoreException.Timeout" in kotlin,
            "Retry predicate drift")
    require("import android." not in kotlin and ".core.ui" not in kotlin and ".core.l10n" not in kotlin,
            "Fault producer depends on Android/UI/localization")
    return counts


def main():
    result = {"catalog": check_catalog(), "fault_cases": check_faults(),
              "source_sha": SOURCE, "native_tests_run": False,
              "scope": "Frozen input/table/declaration equality only, not executed Kotlin or WP acceptance"}
    print(json.dumps(result, sort_keys=True))


if __name__ == "__main__":
    main()
