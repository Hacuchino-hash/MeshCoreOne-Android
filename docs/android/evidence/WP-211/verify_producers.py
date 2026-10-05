"""AndroidOnly: WP-211 Read-only frozen catalog/fault checks, never native parity."""

import json
from decimal import Decimal
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


REGION_NAMES = {
    "northAmerica": "NORTH_AMERICA", "southAmerica": "SOUTH_AMERICA",
    "europe": "EUROPE", "oceania": "OCEANIA", "asia": "ASIA",
}


def split_arguments(text):
    depth = 0
    quoted = escaped = False
    begin = 0
    result = []
    for position, character in enumerate(text):
        if quoted:
            if escaped:
                escaped = False
            elif character == "\\":
                escaped = True
            elif character == '"':
                quoted = False
        elif character == '"':
            quoted = True
        elif character in "([{":
            depth += 1
        elif character in ")]}":
            depth -= 1
            require(depth >= 0, "Malformed catalog nesting")
        elif character == "," and depth == 0:
            result.append(text[begin:position].strip())
            begin = position + 1
    require(not quoted and depth == 0, "Malformed catalog argument declaration")
    result.append(text[begin:].strip())
    return [value for value in result if value]


def preset_calls(text, property_name, swift):
    body = collection(text, ("let " if swift else "val ") + property_name + ":", "[" if swift else "(",
                      "]" if swift else ")", initializer=True)
    calls = []
    offset = 0
    while True:
        match = re.search(r"\bRadioPreset\(", body[offset:])
        if match is None:
            break
        start = offset + match.start()
        value = collection(body[start:], "RadioPreset", "(", ")")
        calls.append(split_arguments(value))
        offset = start + len("RadioPreset(") + len(value) + 1
    require(calls, "Empty radio catalog: " + property_name)
    return calls


def literal(value):
    value = value.strip()
    require(re.fullmatch(r'"(?:\\.|[^"\\])*"', value), "Expected exact catalog string literal")
    return json.loads(value)


def members(value):
    return frozenset(re.findall(r'"([^"\\]*)"', value))


def availability(value, swift):
    if swift:
        kind, remainder = value[1:].split("(", 1)
    else:
        kind, remainder = value.split("(", 1)
        kind = {"countries": "countries", "areas": "subRegions", "continent": "continent",
                "PresetAvailability.Counties": "counties"}.get(kind)
    require(kind in ("continent", "countries", "subRegions", "counties"), "Unknown availability tier")
    arguments = split_arguments(remainder.rsplit(")", 1)[0])
    if kind == "continent":
        region = arguments[0].lstrip(".") if swift else arguments[0].removeprefix("RadioRegion.")
        return kind, REGION_NAMES[region] if swift else region
    if kind == "countries":
        return kind, members(value)
    if kind == "subRegions":
        country = literal(arguments[0].split(":", 1)[-1]) if swift else literal(arguments[0])
        keys = members(arguments[1]) if swift else members(",".join(arguments[1:]))
        return kind, country, keys
    country = literal(arguments[0].split(":", 1)[-1]) if swift else literal(arguments[0])
    state = literal(arguments[1].split(":", 1)[-1]) if swift else literal(arguments[1])
    keys = members(arguments[2]) if swift else members(",".join(arguments[2:]))
    return kind, country, state, keys


def preset_row(arguments, swift):
    if swift:
        fields = dict(argument.split(":", 1) for argument in arguments)
        fields = {key.strip(): value.strip() for key, value in fields.items()}
        require(len(fields) == len(arguments), "Duplicate source preset argument")
        region = REGION_NAMES[fields["region"].removeprefix(".")]
    else:
        require(len(arguments) >= 8, "Incomplete native preset row")
        keys = ("id", "name", "region", "frequencyMHz", "bandwidthKHz", "spreadingFactor", "codingRate", "availability")
        fields = dict(zip(keys, arguments[:8]))
        for argument in arguments[8:]:
            key, value = argument.split("=", 1)
            require(key.strip() not in fields, "Duplicate native preset argument")
            fields[key.strip()] = value.strip()
        region = fields["region"].removeprefix("RadioRegion.")
    require(set(fields) <= {
        "id", "name", "region", "frequencyMHz", "bandwidthKHz", "spreadingFactor", "codingRate",
        "availability", "pathHashSize", "recommendationPriority", "repeatSectionHeader",
    }, "Unknown catalog argument")
    return {
        "id": literal(fields["id"]), "name": literal(fields["name"]), "region": region,
        "frequency": Decimal(fields["frequencyMHz"]), "bandwidth": Decimal(fields["bandwidthKHz"]),
        "sf": int(fields["spreadingFactor"].removesuffix("u")), "cr": int(fields["codingRate"].removesuffix("u")),
        "hash_size": int(fields["pathHashSize"]) if "pathHashSize" in fields else None,
        "priority": int(fields.get("recommendationPriority", "100")),
        "repeat_header": literal(fields["repeatSectionHeader"]) if "repeatSectionHeader" in fields else None,
        "availability": availability(fields["availability"], swift),
    }


def check_radio_catalog():
    swift = reference("MC1Services/Sources/MC1Services/Services/RadioPresets.swift")
    kotlin = (NATIVE / "RadioPresets.kt").read_text(encoding="utf-8")
    counts = {}
    for property_name in ("all", "repeatPresets"):
        expected = [preset_row(arguments, True) for arguments in preset_calls(swift, property_name, True)]
        actual = [preset_row(arguments, False) for arguments in preset_calls(kotlin, property_name, False)]
        require(expected == actual, "Complete radio tuple/name/availability/hash/priority/order drift: " + property_name)
        require(len({row["id"] for row in actual}) == len(actual), "Duplicate preset catalog id")
        counts[property_name] = len(actual)
    return counts


def main():
    result = {"catalog": check_catalog(), "radio_catalog": check_radio_catalog(), "fault_cases": check_faults(),
              "source_sha": SOURCE, "native_tests_run": False,
              "scope": "Frozen input/table/declaration equality only, not executed Kotlin or WP acceptance"}
    print(json.dumps(result, sort_keys=True))


if __name__ == "__main__":
    main()
