"""AndroidOnly: WP-103 supplementary struct/arithmetic/hashlib oracle, never candidate Kotlin.

These inputs complement, rather than replace or regenerate, WP-004's frozen catalog.
Field layouts/scales are pinned to the original MIT MeshCore parsers.
"""

import argparse
import hashlib
import hmac
import json
from pathlib import Path
import struct
import subprocess
import sys


SOURCE = "db14559b39d32322b06477c6ae676112f583db50"
SOURCES = (
    "Parsers+Status.swift", "Parsers+Messaging.swift", "MMAParser.swift",
    "NeighboursParser.swift", "RxLogParser.swift", "RxLogTypes.swift",
    "TransportCodeRegionResolver.swift",
)
DIRECTORY = Path(__file__).resolve().parent
REPO = DIRECTORY.parents[3]
HEADER = "id\tkind\tinput_hex\texpect_1\texpect_2\texpect_3\texpect_4\texpect_5\n"


def source_blobs():
    result = {}
    for name in SOURCES:
        path = "MeshCore/Sources/MeshCore/Protocol/" + name
        blob = subprocess.run(
            ["git", "--no-pager", "-C", str(REPO), "rev-parse", SOURCE + ":" + path],
            capture_output=True, text=True, check=True, timeout=30,
        ).stdout.strip()
        current = subprocess.run(
            ["git", "--no-pager", "-C", str(REPO), "rev-parse", "HEAD:" + path],
            capture_output=True, text=True, check=True, timeout=30,
        ).stdout.strip()
        if current != blob:
            raise ValueError("Read-only parser source drift: " + path)
        result[path] = blob
    return result


def vectors():
    rows = []

    def add(identity, kind, raw, *expected):
        columns = [identity, kind, raw.hex(), *map(str, expected)]
        if len(columns) > 8 or any("\t" in c or "\n" in c for c in columns):
            raise ValueError("Invalid independent vector shape")
        rows.append(columns + ["-"] * (8 - len(columns)))

    for byte in range(256):
        rssi = 255 - byte
        add(
            f"rf-signed-{byte:03}", "raw", bytes([0x84, byte, rssi, 0xFF, 0, 0x80, 0xFF]),
            (byte if byte < 128 else byte - 256) / 4,
            rssi if rssi < 128 else rssi - 256, "0080ff",
        )

    for route in range(4):
        for payload_type in range(16):
            for version in range(4):
                for mode in range(3):
                    body = bytes.fromhex("070aaaff00")
                    tc = bytes.fromhex("01020304") if route in (0, 3) else b""
                    path = bytes(range(0x81, 0x81 + 2 * (mode + 1)))
                    encoded = mode * 64 + 2
                    raw = bytes([version * 64 + payload_type * 4 + route]) + tc + bytes([encoded]) + path + body
                    add(
                        f"rf-{route}-{payload_type}-{version}-{mode}", "rx", raw,
                        f"{route},{payload_type},{version},{encoded}", path.hex(), body.hex(),
                        tc.hex() if tc else "-", hashlib.sha256(body).hexdigest()[:16],
                    )

    # MMA's source differs from regular LPP for unsigned current and complex values.
    one_byte = {0: ("Digital Input", 1), 1: ("Digital Output", 1), 102: ("Presence", 1),
                142: ("Switch", 1), 120: ("Percentage", 1), 104: ("Humidity", 0.5)}
    signed16 = {2: ("Analog Input", 100), 3: ("Analog Output", 100),
                103: ("Temperature", 10), 121: ("Altitude", 1)}
    unsigned16 = {101: ("Illuminance", 1), 115: ("Pressure", 10), 116: ("Voltage", 100),
                  117: ("Current", 1000), 125: ("Concentration", 1), 128: ("Power", 1),
                  132: ("Direction", 1)}
    unsigned32 = {100: ("Sensor", 1), 118: ("Frequency", 1), 130: ("Distance", 1000),
                  131: ("Energy", 1000), 133: ("Time", 1)}
    complex_types = {113: ("Accelerometer", 6, 1000), 134: ("Gyrometer", 6, 100),
                     135: ("Colour", 3, 100), 136: ("GPS", 9, 100)}
    for code, (name, scale) in one_byte.items():
        values = (0, 255, 128)
        add(f"mma-{code}", "mma", bytes([255, code, *values]), code, name,
            ",".join(str(v * scale) for v in values))
    for code, (name, scale) in signed16.items():
        values = (-32768, 32767, -123)
        add(f"mma-{code}", "mma", bytes([255, code]) + struct.pack(">hhh", *values), code, name,
            ",".join(str(v / scale) for v in values))
    for code, (name, scale) in unsigned16.items():
        values = (0, 65535, 32768)
        add(f"mma-{code}", "mma", bytes([255, code]) + struct.pack(">HHH", *values), code, name,
            ",".join(str(v / scale) for v in values))
    for code, (name, scale) in unsigned32.items():
        values = (0, 0xFFFFFFFF, 0x80000000)
        add(f"mma-{code}", "mma", bytes([255, code]) + struct.pack(">III", *values), code, name,
            ",".join(str(v / scale) for v in values))
    values = (-8388608, 8388607, -1500)
    add("mma-122", "mma", bytes([255, 122]) + b"".join(v.to_bytes(3, "big", signed=True) for v in values),
        122, "Load", ",".join(str(v / 1000) for v in values))
    for code, (name, size, scale) in complex_types.items():
        values = (-32768, 32767, -123)
        raw = bytes([255, code]) + b"".join(struct.pack(">h", v) + b"\xA5" * (size - 2) for v in values)
        add(f"mma-{code}", "mma", raw, code, name, ",".join(str(v / scale) for v in values))

    prefix = bytes.fromhex("aabbccddeeff")
    counters = [0xFFFFFFFF, 0x80000000, 0, 123456, 1, 2, 3, 4]
    common = [65535, 65535, -32768, 32767, *counters, 65535, -8192.0, 65535, 65535]
    base = struct.pack("<HHhh8IHhHH", 65535, 65535, -32768, 32767, *counters, 65535, -32768, 65535, 65535)
    if len(base) != 48:
        raise ValueError("Independent binary status base must be exactly 48 bytes")
    for layout in ("repeater", "room"):
        tail = struct.pack("<II", 0xFFFFFFFF, 0x80000000) if layout == "repeater" else struct.pack("<HHI", 65535, 32768, 0x80000000)
        for format_name in ("binary", "push"):
            for trailer_size in range(0 if format_name == "binary" else 3, 13):
                payload = base + (tail + b"\xA5" * 4)[:trailer_size]
                raw = b"\x87\x7E" + prefix + payload if format_name == "push" else payload
                rx = 0xFFFFFFFF if layout == "repeater" and trailer_size >= 4 else 0
                errors = 0x80000000 if layout == "repeater" and trailer_size >= 8 else 0
                posted = 65535 if layout == "room" and trailer_size >= 4 else "null"
                pushed = 32768 if layout == "room" and trailer_size >= 4 else "null"
                add(
                    f"status-{format_name}-{layout}-{trailer_size}", "status", raw, format_name, layout,
                    ",".join(map(str, [*common, rx, errors, posted, pushed])), prefix.hex(),
                )

    for width in (0, 1, 4, 6, 32, 255):
        first = bytes((i + 128) & 255 for i in range(width))
        second = bytes((255 - i) & 255 for i in range(width))
        raw = struct.pack("<hh", -32768, 2) + first + struct.pack("<ib", -2147483648, -128)
        raw += second + struct.pack("<ib", 2147483647, 127)
        add(f"neighbours-{width}", "neighbours", raw, width, first.hex(), second.hex())

    texts = [bytes([b]) for b in range(256)] + [
        bytes.fromhex(value) for value in (
            "eda080", "edbfbf", "f4908080", "f5808080", "c080", "e080af", "f08080af",
            "e282", "f09f92", "e228a1", "f09f41", "c2c2a2", "e0a080", "ed9fbf",
            "f0908080", "f48fbfbf", "efbfbd", "e2", "f0", "ff80e28241",
        )
    ] + [text.encode("utf-8") for text in (
        "", "A\x00B", "e\u0301", "\u4e2d\u6587", "\u0645\u0631\u062d\u0628\u0627",
        "\U0001F1FA\U0001F1F8", "\U0001F469\u200D\U0001F4BB",
    )]
    for index, text in enumerate(texts):
        frame = b"\x07" + prefix + b"\xFF\x00" + struct.pack("<I", 0xFFFFFFFF) + text
        expected = ",".join(f"{ord(c):x}" for c in text.decode("utf-8", errors="replace"))
        add(f"utf8-{index}", "utf8", frame, expected)

    names = ("Germany", "#Germany", "  Germany\t\n", "$secret", "", "\t\n",
             "\u65e5\u672c", "\u00A0Europe\u2028", "Cafe\u0301", "Caf\u00E9", "\u200BGermany\u200B")
    for index, name in enumerate(names):
        trimmed = name.strip()
        key = "-" if not trimmed or trimmed.startswith("$") else hashlib.sha256(
            (trimmed if trimmed.startswith("#") else "#" + trimmed).encode()
        ).digest()[:16].hex()
        add(f"scope-{index}", "scope", name.encode(), key)

    keys = (b"", b"\x00", bytes(range(16)), hashlib.sha256(b"#Germany").digest()[:16], b"\x80" * 513)
    sample = bytes.fromhex("42deadbeef010203")
    for key_index, key in enumerate(keys):
        for bits in range(16):
            mac = hmac.new(key, bytes([bits]) + sample, hashlib.sha256).digest()
            raw_code = struct.unpack("<H", mac[:2])[0]
            code = 1 if raw_code == 0 else 0xFFFE if raw_code == 0xFFFF else raw_code
            add(f"hmac-{key_index}-{bits}", "hmac", sample, key.hex(), bits | 0xF0, code, mac.hex(), raw_code)
    boundary_key = keys[3]
    found = set()
    for counter in range(1_000_000):
        payload = struct.pack("<I", counter)
        mac = hmac.new(boundary_key, b"\x05" + payload, hashlib.sha256).digest()
        raw_code = struct.unpack("<H", mac[:2])[0]
        if raw_code in (0, 0xFFFF) and raw_code not in found:
            found.add(raw_code)
            add(f"hmac-reserved-{raw_code}", "hmac", payload, boundary_key.hex(), 5,
                1 if raw_code == 0 else 0xFFFE, mac.hex(), raw_code)
        if len(found) == 2:
            break
    if len(found) != 2:
        raise ValueError("Reserved-boundary HMAC search exhausted its fixed budget")
    if len({r[0] for r in rows}) != len(rows):
        raise ValueError("Duplicate supplementary vector identity")
    return rows


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    choice = parser.add_mutually_exclusive_group(required=True)
    choice.add_argument("--check", action="store_true")
    choice.add_argument("--write", action="store_true")
    args = parser.parse_args()
    blobs = source_blobs()
    rows = vectors()
    table = ("# Generated by WP-103/python_reference_vectors.py; source " + SOURCE + "; not Kotlin-generated.\n"
             + HEADER + "".join("\t".join(row) + "\n" for row in rows)).encode("ascii")
    metadata = {
        "schema_version": 1, "source_sha": SOURCE, "source_blobs": blobs,
        "generator": "docs/android/evidence/WP-103/python_reference_vectors.py",
        "oracle": "Python standard-library struct, integer arithmetic, UTF-8 replacement, hashlib and hmac",
        "candidate_kotlin_executed": False, "canonical_wp004_vectors_changed": False,
        "vector_count": len(rows), "families": {kind: sum(r[1] == kind for r in rows) for kind in sorted({r[1] for r in rows})},
        "tsv_sha256": hashlib.sha256(table).hexdigest(),
    }
    encoded_metadata = (json.dumps(metadata, indent=2, sort_keys=True) + "\n").encode("ascii")
    outputs = ((DIRECTORY / "python-reference-vectors.tsv", table), (DIRECTORY / "python-reference-vectors.json", encoded_metadata))
    for path, content in outputs:
        if args.write:
            path.write_bytes(content)
        elif path.read_bytes().replace(b"\r\n", b"\n") != content:
            raise ValueError("Independent supplementary vector drift: " + path.name)
    print(json.dumps({"result": "written" if args.write else "checked", **metadata}, indent=2))


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, subprocess.SubprocessError) as error:
        print("BLOCKED: " + str(error), file=sys.stderr)
        sys.exit(2)
