"""AndroidOnly: WP-102 Independent Python/RFC vectors; never imports candidate Kotlin."""

import argparse
import hashlib
import hmac
import json
from pathlib import Path
import subprocess

import cryptography
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import ed25519, x25519
from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes


ROOT = Path(__file__).resolve().parents[4]
OUTPUT = Path(__file__).resolve().parent
SOURCE = "db14559b39d32322b06477c6ae676112f583db50"
SECRET = bytes.fromhex("8b3387e9c5cdea6ac9e5edbaa115cd72")
ALICE = bytes.fromhex("77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a")
BOB = bytes.fromhex("5dab087e624a8a4b79e17f8b83800ee66f3bb1292618b6fd1c2f8b27ff88e0eb")
ALICE_PUBLIC = bytes.fromhex("8520f0098930a754748b7ddcb43ef75a0dbf3a0d26381af4eba4a98eaa9b4e6a")
BOB_PUBLIC = bytes.fromhex("de9edb7d7b7dc1b4d35b61c2ece435373f8343c85b78674dadfc7e146f882b4f")
SHARED = bytes.fromhex("4a5d9d5ba4ce2de1728e3bf480350f25e07e21c947d19e3376f09b3c1e161742")
RFC8032 = [
    (
        "rfc8032-1",
        "9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60",
        "d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a",
        "",
        "e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e065224901555f"
        "b8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b",
    ),
    (
        "rfc8032-2",
        "4ccd089b28ff96da9db6c346ec114e0f5b8a319f35aba624da8cf6ed4fb8a6fb",
        "3d4017c3e843895a92b70aa74d1b7ebc9c982ccf2ec4968cc0cd55f12af4660c",
        "72",
        "92a009a9f0d4cab8720e820b5f642540a2b27b5416503f8fb3762223ebdb69da"
        "085ac1e43e15996e458f3613d0f11d8c387b2eaeb4302aeeb00d291612bb0c00",
    ),
    (
        "rfc8032-3",
        "c5aa8df43f9f837bedb7442f31dcb7b166d38535076f094b85ce3a2e0b4458f7",
        "fc51cd8e6218a1a38da47ed00230f0580816ed13ba3303ac5deb911548908025",
        "af82",
        "6291d657deec24024827e69c3abe01a30ce548a284743a445e3680d7db5ac3ac"
        "18ff9b538d16f290ae67f760984dc6594a7c15e9716ed28dc027beceea1ec40a",
    ),
]
PINNED_INPUTS = {
    "MeshCore/Sources/MeshCore/Protocol/ChannelCrypto.swift": "0e53e6958a516369d073a43c0ce8b245282009ce",
    "MeshCore/Sources/MeshCore/Protocol/DirectMessageCrypto.swift": "049386cb73147a7715fe0ea96358445e253eb0dd",
    "MeshCore/Sources/MeshCore/Protocol/Ed25519ToX25519.swift": "3730ebd17b7b691fad742d5ff6f57ba27a155644",
    "MeshCore/Tests/MeshCoreTests/ChannelCryptoTests.swift": "9e04884f23aca6685acd5f07a9f4d8ee0da8d11e",
    "MeshCore/Tests/MeshCoreTests/DirectMessageCryptoTests.swift": "324033b90084788747edde64071cf7fe70260da2",
    "MeshCore/Tests/MeshCoreTests/Ed25519ToX25519Tests.swift": "95e82357a2fbbe6cade2845b1384b4980af45adf",
}


def raw_public(key):
    return key.public_key().public_bytes(serialization.Encoding.Raw, serialization.PublicFormat.Raw)


def expand_seed(seed):
    expanded = bytearray(hashlib.sha512(seed).digest())
    expanded[0] &= 248
    expanded[31] &= 63
    expanded[31] |= 64
    return bytes(expanded)


def aes_encrypt(plaintext, secret):
    padded = plaintext + bytes((-len(plaintext)) % 16)
    encryptor = Cipher(algorithms.AES(secret[:16]), modes.ECB()).encryptor()
    return encryptor.update(padded) + encryptor.finalize()


def packet(secret, plaintext, header=b""):
    ciphertext = aes_encrypt(plaintext, secret)
    return header + hmac.digest(secret, ciphertext, "sha256")[:2] + ciphertext


def tsv(header, rows):
    ids = [row[0] for row in rows]
    if not rows or len(ids) != len(set(ids)):
        raise ValueError("Missing/duplicate independent vector discovery")
    return ("\t".join(header) + "\n" + "".join("\t".join(map(str, row)) + "\n" for row in rows)).encode("ascii")


def outputs():
    for path, expected in PINNED_INPUTS.items():
        actual = subprocess.check_output(
            ["git", "--no-pager", "-C", str(ROOT), "rev-parse", f"{SOURCE}:{path}"], text=True
        ).strip()
        if actual != expected:
            raise ValueError(f"Pinned crypto input is unavailable/drifted: {path}")
    if raw_public(x25519.X25519PrivateKey.from_private_bytes(ALICE)) != ALICE_PUBLIC:
        raise ValueError("Independent library disagrees with RFC7748 Alice public key")
    if raw_public(x25519.X25519PrivateKey.from_private_bytes(BOB)) != BOB_PUBLIC:
        raise ValueError("Independent library disagrees with RFC7748 Bob public key")
    if x25519.X25519PrivateKey.from_private_bytes(BOB).exchange(
        x25519.X25519PublicKey.from_public_bytes(ALICE_PUBLIC)
    ) != SHARED:
        raise ValueError("Independent library disagrees with RFC7748 shared secret")

    messages = []

    def message(identity, kind, text, timestamp=1_703_123_456, type_byte=0, secret=None, private=None, public=None):
        secret = secret if secret is not None else SECRET if kind == "channel" else SHARED
        text_bytes = text.encode("utf-8") if isinstance(text, str) else text
        plaintext = timestamp.to_bytes(4, "little") + bytes([type_byte]) + text_bytes
        try:
            expected_text = text_bytes.split(b"\x00", 1)[0].decode("utf-8").encode("utf-8").hex()
            outcome = "success"
        except UnicodeDecodeError:
            expected_text = "-"
            outcome = "decryptFailed" if kind == "channel" else "success"
        messages.append([
            identity, kind, (private or BOB).hex() if kind != "channel" else "-",
            (public or ALICE_PUBLIC).hex() if kind != "channel" else "-",
            secret.hex(), plaintext.hex(), packet(secret, plaintext, b"\xaa\xbb" if kind != "channel" else b"").hex(),
            timestamp, type_byte, expected_text, outcome,
        ])

    message("channel-normal", "channel", "Alice: Hello mesh!")
    message("channel-wrong-key", "channel", "Bob: Secret message")
    message("channel-corrupted-mac", "channel", "Test message")
    message("channel-empty", "channel", "", timestamp=0)
    message("channel-long", "channel", "This is a longer message that will definitely span multiple AES blocks for encryption testing")
    message("channel-unicode", "channel", "Hello! \u4f60\u597d! \U0001f30d")
    for type_byte in range(3):
        message(f"channel-type-{type_byte}", "channel", "Test message", type_byte=type_byte)
    message(
        "channel-secret32", "channel", "Carol: 256-bit key channel",
        secret=SECRET + bytes.fromhex("102030405060708090a0b0c0d0e0f000"),
    )
    message("channel-swift-high-bit", "channel", "Hi\u4f60\U0001f600", timestamp=0x80000000, type_byte=2)
    for name, text, timestamp in [
        ("normal", "Hello from sender!", 1_703_123_456),
        ("wrong-key", "Secret message", 1_703_123_456),
        ("corrupted-mac", "Test", 1_703_123_456),
        ("empty", "", 0),
        ("unicode", "Hello! \u4f60\u597d! \U0001f30d", 1_703_123_456),
        ("extract", "Test", 1_703_123_456),
    ]:
        message(f"direct-{name}", "direct", text, timestamp=timestamp)
    for kind in ("channel", "direct"):
        for length in (0, 1, 10, 11, 12, 26, 27, 28, 255, 256, 1023, 1024):
            message(f"{kind}-length-{length}", kind, "x" * length, timestamp=0xFFFFFFFF, type_byte=255)
        for identity, text in [
            ("nul", b"ok\x00\xfftail"),
            ("leading-continuation", b"\x80"),
            ("overlong", b"\xc0\xaf"),
            ("truncated", b"\xe2\x82"),
            ("surrogate", b"\xed\xa0\x80"),
            ("out-of-range", b"\xf4\x90\x80\x80"),
            ("invalid-after-valid", b"ok\xff"),
        ]:
            message(f"{kind}-utf8-{identity}", kind, text, timestamp=0xFFFFFFFF, type_byte=255)
        secret = SECRET if kind == "channel" else SHARED
        for length in (17, 31, 33):
            ciphertext = bytes(range(length))
            body = hmac.digest(secret, ciphertext, "sha256")[:2] + ciphertext
            messages.append([
                f"{kind}-nonblock-{length}", kind, BOB.hex() if kind == "direct" else "-",
                ALICE_PUBLIC.hex() if kind == "direct" else "-", secret.hex(), "-",
                (b"\xaa\xbb" + body if kind == "direct" else body).hex(), "-", "-", "-",
                "decryptFailed" if kind == "channel" else "decryptionFailed",
            ])
    for length in (16, 17, 24, 31, 32, 33, 64, 65, 128):
        message(f"channel-key-length-{length}", "channel", "full HMAC key", secret=bytes(range(length)))
    for length in (0, 1, 15):
        secret = bytes(range(length))
        ciphertext = bytes(range(16))
        messages.append([
            f"channel-short-key-{length}", "channel", "-", "-", secret.hex(), "-",
            (hmac.digest(secret, ciphertext, "sha256")[:2] + ciphertext).hex(), "-", "-", "-", "decryptFailed",
        ])

    keys = []
    signatures = []
    seeds = [(name, bytes.fromhex(seed)) for name, seed, _, _, _ in RFC8032]
    seeds += [(f"deterministic-{index}", hashlib.sha256(f"WP-102 independent seed {index}".encode("ascii")).digest())
              for index in range(16)]
    for identity, seed in seeds:
        signing = ed25519.Ed25519PrivateKey.from_private_bytes(seed)
        expanded = expand_seed(seed)
        x_private = expanded[:32]
        x_public = raw_public(x25519.X25519PrivateKey.from_private_bytes(x_private))
        keys.append([identity, seed.hex(), expanded.hex(), raw_public(signing).hex(), x_private.hex(), x_public.hex()])
    for identity, seed, public, text, expected in RFC8032:
        signing = ed25519.Ed25519PrivateKey.from_private_bytes(bytes.fromhex(seed))
        if raw_public(signing).hex() != public or signing.sign(bytes.fromhex(text)).hex() != expected:
            raise ValueError(f"Independent library disagrees with {identity}")
        signatures.append([identity, seed, public, text, expected])
    signing = ed25519.Ed25519PrivateKey.from_private_bytes(seeds[0][1])
    for length in (16, 31, 32, 33, 120, 121, 255, 256, 1024, 65536):
        text = bytes(index % 256 for index in range(length))
        signatures.append([f"message-length-{length}", seeds[0][1].hex(), raw_public(signing).hex(), text.hex(), signing.sign(text).hex()])

    sender = keys[0]
    recipient = keys[1]
    secret = x25519.X25519PrivateKey.from_private_bytes(bytes.fromhex(sender[4])).exchange(
        x25519.X25519PublicKey.from_public_bytes(bytes.fromhex(recipient[5]))
    )
    message("ed-converted-direct", "direct", "Hello", secret=secret,
            private=bytes.fromhex(recipient[4]), public=bytes.fromhex(sender[5]))
    source_oracle_path = ROOT / "android" / "core" / "testing" / "fixtures" / "reference-codec" / "channel-crypto-oracle.json"
    source_oracle = json.loads(source_oracle_path.read_text(encoding="utf-8"))
    if source_oracle["source_sha"] != SOURCE:
        raise ValueError("Stale actual Swift source-oracle revision")
    expected_oracle = {item["id"]: item["packet_hex"] for item in source_oracle["vectors"]}
    if set(expected_oracle) != {"normal", "high-bit-utf8"}:
        raise ValueError("Missing/extra actual Swift source-oracle case")
    by_id = {row[0]: row for row in messages}
    if by_id["channel-normal"][6] != expected_oracle["normal"] or by_id["channel-swift-high-bit"][6] != expected_oracle["high-bit-utf8"]:
        raise ValueError("Independent Python bytes disagree with actual pinned Swift oracle")

    result = {
        "message-vectors.tsv": tsv(
            ["id", "kind", "private_key_hex", "public_key_hex", "secret_hex", "plaintext_hex",
             "packet_hex", "timestamp", "type", "text_hex", "outcome"], messages
        ),
        "key-vectors.tsv": tsv(["id", "seed_hex", "expanded_hex", "ed_public_hex", "x_private_hex", "x_public_hex"], keys),
        "signature-vectors.tsv": tsv(["id", "seed_hex", "public_hex", "message_hex", "signature_hex"], signatures),
    }
    metadata = {
        "schema_version": 1,
        "generator": "docs/android/evidence/WP-102/generate_vectors.py",
        "source_sha": SOURCE,
        "source_blobs": PINNED_INPUTS,
        "independent_implementation": {"name": "Python cryptography", "version": cryptography.__version__,
                                     "license": "Apache-2.0 OR BSD-3-Clause; test/evidence tool only"},
        "standards": ["RFC7748 section 6.1", "RFC8032 section 7.1", "RFC2104"],
        "swift_oracle": {"path": str(source_oracle_path.relative_to(ROOT)).replace("\\", "/"),
                         "sha256": hashlib.sha256(source_oracle_path.read_bytes()).hexdigest(),
                         "compared_cases": ["normal", "high-bit-utf8"],
                         "executed_here": False},
        "counts": {"message_vectors": len(messages), "key_vectors": len(keys), "signature_vectors": len(signatures)},
        "fixture_hash_encoding": "ASCII text with CRLF normalized to LF; hexadecimal wire bytes are unchanged",
        "sha256": {name: hashlib.sha256(data).hexdigest() for name, data in result.items()},
        "candidate_expected_bytes": False,
    }
    result["vector-generation.json"] = (json.dumps(metadata, indent=2, sort_keys=True) + "\n").encode("ascii")
    return result, metadata["counts"]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--check", action="store_true")
    mode.add_argument("--regenerate", action="store_true")
    args = parser.parse_args()
    expected, counts = outputs()
    for name, data in expected.items():
        path = OUTPUT / name
        if args.check:
            if not path.is_file() or path.read_bytes().replace(b"\r\n", b"\n") != data:
                raise ValueError(f"Independent vector bytes/provenance differ: {name}")
        else:
            path.write_bytes(data)
    print(json.dumps({"result": "checked" if args.check else "generated", **counts, "candidate_code_used": False}))


if __name__ == "__main__":
    main()
