// AndroidOnly: WP-102 Read complete independently generated crypto evidence without a production testing dependency.
package com.meshcoreone.android.core.protocol.crypto

import com.meshcoreone.android.core.protocol.bytes.Bytes
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.readLines
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

internal data class MessageVector(
    val id: String,
    val kind: String,
    val privateKey: Bytes?,
    val publicKey: Bytes?,
    val secret: Bytes,
    val plaintext: Bytes?,
    val packet: Bytes,
    val timestamp: UInt?,
    val type: UByte?,
    val text: String?,
    val outcome: String,
)

internal data class KeyVector(
    val id: String,
    val seed: Bytes,
    val expanded: Bytes,
    val edPublic: Bytes,
    val xPrivate: Bytes,
    val xPublic: Bytes,
)

internal data class SignatureVector(
    val id: String,
    val seed: Bytes,
    val publicKey: Bytes,
    val message: Bytes,
    val signature: Bytes,
)

internal object CryptoFixtures {
    val root: Path = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
        .first { it.resolve("MeshCore").isDirectory() && it.resolve("android").isDirectory() }
    private val directory = root.resolve("docs").resolve("android").resolve("evidence").resolve("WP-102")

    val messages: List<MessageVector> = rows(
        "message-vectors.tsv",
        listOf("id", "kind", "private_key_hex", "public_key_hex", "secret_hex", "plaintext_hex",
            "packet_hex", "timestamp", "type", "text_hex", "outcome"),
        74,
    ).map { row ->
        check(row[1] == "channel" || row[1] == "direct") { "Unknown message fixture kind" }
        val vector = MessageVector(
            row[0], row[1], optionalBytes(row[2]), optionalBytes(row[3]), bytes(row[4]), optionalBytes(row[5]),
            bytes(row[6]), row[7].takeUnless { it == "-" }?.toUInt(), row[8].takeUnless { it == "-" }?.toUByte(),
            optionalBytes(row[9])?.let(::strictUtf8), row[10],
        )
        check(vector.outcome in setOf("success", "decryptFailed", "decryptionFailed")) { "Unknown message outcome" }
        if (vector.kind == "direct") {
            check(vector.privateKey?.size == 32 && vector.publicKey?.size == 32) { "Incomplete direct-message key fixture" }
        }
        if (vector.outcome == "success") {
            check(vector.timestamp != null && vector.type != null) { "Incomplete plaintext header expectation" }
            if (vector.kind == "channel") check(vector.text != null) { "Missing channel text expectation" }
        }
        vector
    }

    val keys: List<KeyVector> = rows(
        "key-vectors.tsv", listOf("id", "seed_hex", "expanded_hex", "ed_public_hex", "x_private_hex", "x_public_hex"), 19,
    ).map { row ->
        KeyVector(row[0], bytes(row[1]), bytes(row[2]), bytes(row[3]), bytes(row[4]), bytes(row[5])).also {
            check(it.seed.size == 32 && it.expanded.size == 64 && it.edPublic.size == 32 &&
                it.xPrivate.size == 32 && it.xPublic.size == 32) { "Malformed independent key vector" }
        }
    }

    val signatures: List<SignatureVector> = rows(
        "signature-vectors.tsv", listOf("id", "seed_hex", "public_hex", "message_hex", "signature_hex"), 13,
    ).map { row ->
        SignatureVector(row[0], bytes(row[1]), bytes(row[2]), bytes(row[3]), bytes(row[4])).also {
            check(it.seed.size == 32 && it.publicKey.size == 32 && it.signature.size == 64) { "Malformed signature vector" }
        }
    }

    fun message(id: String): MessageVector = messages.single { it.id == id }

    fun assertChannel(vector: MessageVector) {
        when (vector.outcome) {
            "success" -> {
                val result = assertIs<ChannelCrypto.DecryptResult.Success>(ChannelCrypto.decrypt(vector.packet, vector.secret))
                assertEquals(vector.timestamp, result.timestamp, vector.id)
                assertEquals(vector.type, result.txtType, vector.id)
                assertTrue(vector.text == result.text, "Channel text differs from independent vector ${vector.id}")
            }
            "decryptFailed" -> assertEquals(ChannelCrypto.DecryptResult.DecryptFailed, ChannelCrypto.decrypt(vector.packet, vector.secret))
            else -> error("Unexpected channel fixture outcome")
        }
    }

    fun assertDirect(vector: MessageVector) {
        val result = DirectMessageCrypto.decrypt(vector.packet, requireNotNull(vector.privateKey), requireNotNull(vector.publicKey))
        when (vector.outcome) {
            "success" -> {
                val success = assertIs<DirectMessageCrypto.DecryptResult.Success>(result)
                assertEquals(vector.timestamp, success.timestamp, vector.id)
                assertEquals(vector.type, success.typeAttempt, vector.id)
                assertTrue(vector.text == success.text, "Direct text differs from independent vector ${vector.id}")
            }
            "decryptionFailed" -> assertEquals(DirectMessageCrypto.DecryptResult.DecryptionFailed, result)
            else -> error("Unexpected direct-message fixture outcome")
        }
    }

    private fun rows(name: String, header: List<String>, count: Int): List<List<String>> {
        val lines = directory.resolve(name).readLines(Charsets.US_ASCII)
        check(lines.firstOrNull()?.split('\t') == header) { "Missing/malformed independent crypto fixture header: $name" }
        val rows = lines.drop(1).map { it.split('\t') }
        check(rows.size == count && rows.all { it.size == header.size }) { "Missing/malformed crypto fixture cases: $name" }
        check(rows.map { it.first() }.toSet().size == rows.size && rows.all { it.first().isNotBlank() }) {
            "Duplicate/empty independent crypto case identity: $name"
        }
        return rows
    }

    private fun bytes(hex: String): Bytes {
        check(hex.matches(Regex("(?:[0-9a-f]{2})*"))) { "Malformed independent crypto hexadecimal input" }
        return Bytes.fromHex(hex)
    }

    private fun optionalBytes(hex: String): Bytes? = if (hex == "-") null else bytes(hex)

    private fun strictUtf8(bytes: Bytes): String = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes.toByteArray())).toString()
}
