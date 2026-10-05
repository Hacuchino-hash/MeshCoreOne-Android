// AndroidOnly: WP-203 Non-materializing ignored JSON traversal, syntax, cancellation and allocation regressions.
package com.meshcoreone.android.core.data.backup

import com.meshcoreone.android.core.model.*
import com.meshcoreone.android.core.protocol.bytes.Bytes
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class BackupIgnoredValueTest {
    @Test fun fiftyMillionIgnoredNumbersStayStreamingUnderSourceSizeLimits() {
        val expected = envelope()
        val json = expected.jsonObject().toString()
        val compressed = ignoredArray(json.dropLast(1) + ",\"futureIgnored\":", "}", 50_000_000)
        assertTrue(compressed.size < BackupContract.MAX_COMPRESSED_BYTES)
        var materialized = 0
        val codec = AppBackupCodec(JsonMaterializationObserver {
            if (++materialized > 100) throw AssertionError("Ignored array materialized a JSON tree")
        })
        assertEquals(expected, codec.parseBackup(Bytes(compressed)))
        assertTrue(materialized in 1..100)
    }

    @Test fun ignoredObjectMembersAndLongKeysDoNotBuildTreesOrKeySets() {
        val expected = envelope()
        val json = expected.jsonObject().toString()
        val output = ByteArrayOutputStream()
        BackupDeflater(output, checkCancellation = {}).use { deflater ->
            deflater.write((json.dropLast(1) + ",\"futureIgnored\":{").toByteArray(Charsets.UTF_8))
            val chunk = "\"discard\":[0,1,{\"nested\":true}],".repeat(1000).toByteArray(Charsets.UTF_8)
            repeat(1000) { deflater.write(chunk) }
            deflater.write("\"last\":null},\"".toByteArray(Charsets.UTF_8))
            val keyChunk = ByteArray(64 * 1024) { 'A'.code.toByte() }
            repeat(32) { deflater.write(keyChunk) }
            deflater.write("\":{\"ignored\":[]}}".toByteArray(Charsets.UTF_8))
            deflater.finish()
        }
        var materialized = 0
        val codec = AppBackupCodec(JsonMaterializationObserver {
            if (++materialized > 100) throw AssertionError("Ignored object/keys materialized")
        })
        assertEquals(expected, codec.parseBackup(Bytes(output.toByteArray())))
        assertTrue(materialized in 1..100)
    }

    @Test fun largeIgnoredDtoAndPreferenceValuesLeaveEveryKnownFieldUntouched() {
        val expected = fullEnvelope()
        val json = expected.jsonObject().toString()
        val markers = listOf("\"messages\":[{", "\"userDefaults\":{")
        for (marker in markers) {
            val start = json.indexOf(marker) + marker.length
            assertTrue(start >= marker.length)
            val compressed = ignoredArray(json.substring(0, start) + "\"futureIgnored\":", "," + json.substring(start), 10_000_000)
            var materialized = 0
            val codec = AppBackupCodec(JsonMaterializationObserver {
                if (++materialized > 1000) throw AssertionError("Ignored nested value materialized")
            })
            assertEquals(expected, codec.parseBackup(Bytes(compressed)))
            assertTrue(materialized in 1..1000)
        }
    }

    @Test fun allDtoNestedCodableObjectsDiscardOnlyUnknownFields() {
        var value = fullEnvelope().jsonObject()
        val unknown = buildJsonObject { put("nested", JsonArray(List(100) { JsonPrimitive(it) })) }
        for (kind in BackupModelKind.entries) {
            value = value.modifyingRow(kind.arrayKey) { it.replacing("futureIgnored", unknown) }
        }
        value = value.modifyingRow("savedTracePaths") { row ->
            row.replacing("runs", JsonArray(row.getValue("runs").jsonArray.map {
                it.jsonObject.replacing("futureIgnored", unknown)
            }))
        }.replacing("userDefaults", fullEnvelope().userDefaults!!.encode().replacing("futureIgnored", unknown)
            .replacing("regionSelection", encodeRegion(RegionSelection("US", RegionSelection.Source.MANUAL)).replacing("futureIgnored", unknown)))
        val expected = fullEnvelope().copy(userDefaults = BackupUserDefaults(hasCompletedOnboarding = true,
            regionSelection = RegionSelection("US", RegionSelection.Source.MANUAL)))
        var materialized = 0
        assertEquals(expected, AppBackupCodec(JsonMaterializationObserver {
            if (++materialized > 1000) throw AssertionError("Ignored DTO descendants materialized")
        }).parseBackup(value.compressed()))
        assertTrue(materialized in 1..1000)
    }

    @Test fun ignoredMalformedTruncatedAndDeepValuesStillFailTypedValidation() {
        val prefix = envelope().jsonObject().toString().dropLast(1) + ",\"futureIgnored\":"
        for (value in listOf("[0,]", "{\"missingColon\" 0}", "\"\\q\"", "\"\\uGGGG\"", "tru", "01", "[0,1",
            "[".repeat(130) + "0" + "]".repeat(130))) {
            assertEquals(AppBackupError.InvalidFile,
                assertThrows(AppBackupException::class.java) { AppBackupCodec().parseBackup(Bytes.utf8(prefix + value + "}").zlibCompressed()) }.error)
        }
    }

    @Test fun ignoredTraversalCancellationPropagatesAndClosesInput() {
        val prefix = envelope().jsonObject().toString().dropLast(1) + ",\"futureIgnored\":"
        val compressed = ignoredArray(prefix, "}", 1_000_000)
        var closed = false
        var checks = 0
        val input = object : ByteArrayInputStream(compressed) {
            override fun close() { closed = true; super.close() }
        }
        assertThrows(CancellationException::class.java) {
            AppBackupCodec().parseBackup(input, checkCancellation = {
                if (++checks == 5) throw CancellationException("Controlled ignored-value cancellation")
            })
        }
        assertTrue(closed)
        assertEquals(5, checks)
    }

    @Test fun escapedKnownKeysAndStrictKnownValuesRetainOriginalValidation() {
        val json = fullEnvelope().jsonObject().toString().replace("\"devices\":", "\"\\u0064evices\":")
        assertEquals(fullEnvelope(), AppBackupCodec().parseBackup(Bytes.utf8(json).zlibCompressed()))
        val malformed = fullEnvelope().jsonObject().modifyingRow("messages") {
            it.replacing("timestamp", JsonArray(List(1000) { JsonPrimitive(0) }))
        }
        assertEquals(AppBackupError.InvalidFile,
            assertThrows(AppBackupException::class.java) { AppBackupCodec().parseBackup(malformed.compressed()) }.error)
    }

    private fun ignoredArray(prefix: String, suffix: String, zeros: Long): ByteArray {
        val output = ByteArrayOutputStream()
        BackupDeflater(output, checkCancellation = {}).use { deflater ->
            deflater.write((prefix + "[").toByteArray(Charsets.UTF_8))
            val pairs = ByteArray(64 * 1024) { if (it % 2 == 0) '0'.code.toByte() else ','.code.toByte() }
            var remaining = zeros - 1
            while (remaining > 0) {
                val count = minOf(remaining, pairs.size / 2L).toInt()
                deflater.write(pairs, 0, count * 2)
                remaining -= count
            }
            deflater.write(("0]" + suffix).toByteArray(Charsets.UTF_8))
            deflater.finish()
        }
        return output.toByteArray()
    }
}
