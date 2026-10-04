// AndroidOnly: WP-103 Execute all independent struct/hashlib/hmac/UTF-8 supplementary vectors.
// Generator: docs/android/evidence/WP-103/python_reference_vectors.py, pinned Swift inputs in its source map.
package com.meshcoreone.android.core.protocol.parser

import com.meshcoreone.android.core.protocol.command.PinnedCommandSource
import com.meshcoreone.android.core.protocol.event.MeshEvent
import com.meshcoreone.android.core.protocol.event.StatusResponse
import org.junit.jupiter.api.TestFactory
import java.security.MessageDigest
import kotlin.io.path.readText
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PythonReferenceVectorsTest {
    @TestFactory
    fun allIndependentVectors(): List<org.junit.jupiter.api.DynamicTest> {
        val text = PinnedCommandSource.root.resolve("docs").resolve("android").resolve("evidence").resolve("WP-103")
            .resolve("python-reference-vectors.tsv").readText(Charsets.UTF_8).replace("\r\n", "\n")
        val hash = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.US_ASCII))
        assertEquals("76e6d74b1aadf7c3a8cf2f8bedf81a0d42e1aeaf9c76c324309a9d2d78118923", com.meshcoreone.android.core.protocol.bytes.Bytes(hash).hexString)
        val rows = text.lines().filterNot { it.startsWith("#") }.drop(1).filter { it.isNotEmpty() }.map { it.split('\t') }
        assertEquals(1479, rows.size)
        assertEquals(1479, rows.map { it[0] }.toSet().size)
        assertEquals(mapOf("raw" to 256, "rx" to 768, "mma" to 27, "status" to 46, "neighbours" to 6, "utf8" to 283, "scope" to 11, "hmac" to 82), rows.groupingBy { it[1] }.eachCount())
        return rows.map { row ->
            assertEquals(8, row.size, row[0])
            nativeCase("independent:${row[0]}") { check(row) }
        }
    }

    private fun check(row: List<String>) {
        val input = hex(row[2])
        when (row[1]) {
            "raw" -> {
                val info = parsed<MeshEvent.RawData>(input).info
                assertEquals(row[3].toDouble(), info.snr); assertEquals(row[4].toLong(), info.rssi); assertEquals(hex(row[5]), info.payload)
            }
            "rx" -> {
                val data = assertNotNull(RxLogParser.parse(null, null, input))
                val fields = row[3].split(',').map(String::toInt)
                assertEquals(fields[0], data.routeType.rawValue.toInt())
                assertEquals(fields[1], data.payloadTypeBits.toInt())
                assertEquals(if (fields[1] in 12..14) 255 else fields[1], data.payloadType.rawValue.toInt())
                assertEquals(fields[2], data.payloadVersion.toInt()); assertEquals(fields[3], data.pathLength.toInt())
                assertEquals(hex(row[4]).toList(), data.pathNodes); assertEquals(hex(row[5]), data.packetPayload)
                assertEquals(if (row[6] == "-") null else hex(row[6]), data.transportCode)
                assertEquals(row[7], data.packetHash); assertEquals(input, data.rawPayload); assertNull(data.snr); assertNull(data.rssi)
                assertEquals(if (fields[1] == 2) hex("0a") else null, data.senderPubkeyPrefix)
                assertEquals(if (fields[1] == 2) hex("07") else null, data.recipientPubkeyPrefix)
            }
            "mma" -> {
                val result = MMAParser.decode(input)
                assertIs<BinaryListDecodeResult.Complete<*>>(result)
                assertEquals(input.size, result.bytesConsumed)
                val entry = result.entries.single()
                assertEquals(255u.toUByte(), entry.channel); assertEquals(row[4], entry.type)
                val expected = row[5].split(',').map(String::toDouble)
                assertEquals(expected[0], entry.min, 1e-12); assertEquals(expected[1], entry.max, 1e-12); assertEquals(expected[2], entry.avg, 1e-12)
            }
            "status" -> {
                val layout = if (row[4] == "room") StatusResponse.Layout.ROOM_SERVER else StatusResponse.Layout.REPEATER
                val prefix = hex(row[6])
                val response = if (row[3] == "binary") {
                    assertNotNull(Parsers.StatusResponse.parseFromBinaryResponse(input, prefix, layout))
                } else {
                    assertEquals(0x87u.toUByte(), input[0])
                    assertIs<MeshEvent.StatusResponse>(Parsers.StatusResponse.parse(input.slice(1, input.size), layout)).response
                }
                assertEquals(prefix, response.publicKeyPrefix); assertEquals(layout, response.layout)
                val expected = row[5].split(',').map { if (it == "null") null else it.toDouble() }
                assertEquals(expected, listOf(
                    response.battery.toDouble(), response.txQueueLength.toDouble(), response.noiseFloor.toDouble(), response.lastRSSI.toDouble(),
                    response.packetsReceived.toDouble(), response.packetsSent.toDouble(), response.airtime.toDouble(), response.uptime.toDouble(),
                    response.sentFlood.toDouble(), response.sentDirect.toDouble(), response.receivedFlood.toDouble(), response.receivedDirect.toDouble(),
                    response.fullEvents.toDouble(), response.lastSNR, response.directDuplicates.toDouble(), response.floodDuplicates.toDouble(),
                    response.rxAirtime.toDouble(), response.receiveErrors.toDouble(), response.roomServerPostedCount?.toDouble(), response.roomServerPostPushCount?.toDouble(),
                ))
            }
            "neighbours" -> {
                val result = NeighboursParser.decode(input, nodePrefix, requestTag, row[3].toInt())
                assertNull(result.diagnostic); assertEquals(input.size, result.bytesConsumed); assertEquals(0, result.remainingData.size)
                val response = result.requireComplete()
                assertEquals(nodePrefix, response.publicKeyPrefix); assertEquals(requestTag, response.tag)
                assertEquals(-32768L, response.totalCount); assertEquals(2, response.neighbours.size)
                assertEquals(hex(row[4]), response.neighbours[0].publicKeyPrefix); assertEquals(hex(row[5]), response.neighbours[1].publicKeyPrefix)
                assertEquals(-2147483648L, response.neighbours[0].secondsAgo); assertEquals(2147483647L, response.neighbours[1].secondsAgo)
                assertEquals(-32.0, response.neighbours[0].snr); assertEquals(31.75, response.neighbours[1].snr)
            }
            "utf8" -> {
                val message = parsed<MeshEvent.ContactMessageReceived>(input).message
                assertEquals(row[3], message.text.codePoints().toArray().joinToString(",") { it.toString(16) })
                assertEquals(nodePrefix, message.senderPublicKeyPrefix); assertEquals(0xffu.toUByte(), message.pathLength)
                assertEquals(java.time.Instant.ofEpochSecond(0xffffffffL), message.senderTimestamp); assertNull(message.snr); assertNull(message.signature)
            }
            "scope" -> {
                val name = String(input.toByteArray(), Charsets.UTF_8)
                assertEquals(if (row[3] == "-") null else hex(row[3]), TransportCodeRegionResolver.deriveScopeKey(name))
            }
            "hmac" -> {
                assertEquals(row[5].toUShort(), TransportCodeRegionResolver.calcTransportCode(hex(row[3]), row[4].toUByte(), input))
                val mac = hex(row[6]); assertEquals(32, mac.size)
                assertEquals(row[7].toInt(), mac[0].toInt() + 256 * mac[1].toInt())
            }
            else -> error("Unknown independent vector family: ${row[1]}")
        }
    }
}
