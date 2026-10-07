// AndroidOnly: WP-212 Native DM decrypt/reprocess and store-adapter cases; the source suites have no DM key fixtures.
package com.meshcoreone.android.core.services.diagnostics

import com.meshcoreone.android.core.contracts.domain.PersistenceStoreProtocol
import com.meshcoreone.android.core.model.DecryptStatus
import com.meshcoreone.android.core.model.RxLogEntryDTO
import com.meshcoreone.android.core.model.SnapshotList
import com.meshcoreone.android.core.model.SnapshotMap
import com.meshcoreone.android.core.protocol.bytes.Bytes
import com.meshcoreone.android.core.protocol.crypto.Ed25519Crypto
import com.meshcoreone.android.core.protocol.crypto.WireCrypto
import com.meshcoreone.android.core.protocol.crypto.X25519Crypto
import com.meshcoreone.android.core.protocol.event.PayloadType
import com.meshcoreone.android.core.protocol.event.RouteType
import java.lang.reflect.Proxy
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

class RxLogServiceDirectMessageTest {
    /** Firmware-style 64-byte export whose first half is the X25519 scalar the service keeps. */
    private val myExpandedKey = Ed25519Crypto.expandSeed(Bytes(ByteArray(32) { 0x01 }))
    private val myX25519Public = X25519Crypto.publicKey(myExpandedKey.prefix(32))
    private val senderSeed = Bytes(ByteArray(32) { 0x02 })
    private val senderEdPublic = Ed25519Crypto.publicKeyFromSeed(senderSeed)
    private val senderPrefix: UByte = senderEdPublic[0]

    /** `[destHash:1][srcHash:1][MAC:2][ciphertext:N]` encrypted by the sender to us. */
    private fun dmPayload(timestamp: UInt, text: String): Bytes {
        val shared = X25519Crypto.sharedSecret(Ed25519Crypto.x25519PrivateKeyFromSeed(senderSeed), myX25519Public)
        val plaintext = rxLogSvcUInt32LE(timestamp) + Bytes.of(0) + Bytes.utf8(text)
        val ciphertext = WireCrypto.encryptAes128EcbZeroPadded(plaintext, shared)
        return Bytes.of(myX25519Public[0].toInt(), senderPrefix.toInt()) + WireCrypto.truncatedHmacSha256(ciphertext, shared) + ciphertext
    }

    private fun case(name: String, body: suspend RxLogSvcHarness.() -> Unit): DynamicTest =
        DynamicTest.dynamicTest("WP-212::$name") { RxLogSvcHarness().use { runBlocking { it.body() } } }

    @TestFactory
    fun cases(): List<DynamicTest> = listOf(
        case("direct messages are marked dmNoMatchingKey until keys exist, then decrypt live") {
            val radioId = rxLogSvcRadio()
            service.startEventMonitoring(radioId)
            rxLogSvcWaitUntil("monitor finished its database secret load") { session.subscriberCount == 1 }
            val payload = dmPayload(1_700_000_123u, "direct hello")
            service.process(rxLogSvcParsed(PayloadType.TEXT_MESSAGE, payload, routeType = RouteType.DIRECT))
            val before = store.allEntries().single()
            assertEquals(DecryptStatus.DM_NO_MATCHING_KEY, before.decryptStatus)
            assertNull(before.senderTimestamp)
            service.updatePrivateKey(myExpandedKey)
            service.updateContactPublicKeys(mapOf(senderPrefix to listOf(senderEdPublic)))
            assertEquals(DecryptStatus.SUCCESS, store.entry(before.id)?.decryptStatus, "the key update re-processed it")
            service.process(rxLogSvcParsed(PayloadType.TEXT_MESSAGE, payload, routeType = RouteType.TC_DIRECT))
            val after = store.allEntries().last()
            assertEquals(DecryptStatus.SUCCESS, after.decryptStatus)
            assertEquals(1_700_000_123u, after.senderTimestamp)
            assertEquals("direct hello", after.decodedText)
            assertEquals("direct hello", service.decryptEntry(before).decodedText, "decryptEntry re-decrypts stored DMs")
        },
        case("recent dmNoMatchingKey entries gain their sender timestamp once both keys arrive") {
            val radioId = rxLogSvcRadio()
            service.startEventMonitoring(radioId)
            val entry = RxLogEntryDTO.fromParsed(
                radioId, rxLogSvcParsed(PayloadType.TEXT_MESSAGE, dmPayload(77u, "x"), routeType = RouteType.DIRECT),
                decryptStatus = DecryptStatus.DM_NO_MATCHING_KEY,
            )
            store.seedEntry(entry)
            service.updateContactPublicKeys(mapOf(senderPrefix to listOf(senderEdPublic)))
            assertEquals(0, store.decryptStatusFetchCount, "no private key yet: reprocess returns before fetching")
            service.updatePrivateKey(myExpandedKey)
            val updated = store.entry(entry.id)
            assertEquals(DecryptStatus.SUCCESS, updated?.decryptStatus)
            assertEquals(77u, updated?.senderTimestamp)
            assertNull(updated?.channelIndex)
        },
        case("short private keys and invalid contact keys never trigger DM reprocessing") {
            service.startEventMonitoring(rxLogSvcRadio())
            service.updatePrivateKey(Bytes(ByteArray(31)))
            service.updateContactPublicKeys(mapOf(1.toUByte() to listOf(Bytes(ByteArray(31)), Bytes(ByteArray(32) { -1 }))))
            assertEquals(0, store.decryptStatusFetchCount)
            assertTrue(RxLogEntryDecryptor.convertPublicKeysToX25519(mapOf(1.toUByte() to listOf(Bytes(ByteArray(5))))).isEmpty())
        },
        case("flood-routed text messages are not DM-decrypted") {
            service.startEventMonitoring(rxLogSvcRadio())
            service.process(rxLogSvcParsed(PayloadType.TEXT_MESSAGE, dmPayload(1u, "f"), routeType = RouteType.FLOOD))
            assertEquals(DecryptStatus.NOT_APPLICABLE, store.allEntries().single().decryptStatus)
        },
        case("RxLogServiceStore.from delegates every operation to the contract store") {
            val calls = mutableListOf<String>()
            val contract = Proxy.newProxyInstance(
                PersistenceStoreProtocol::class.java.classLoader, arrayOf(PersistenceStoreProtocol::class.java),
            ) { _, method, _ ->
                // Suspend functions return through Object; answer by source operation name (value-class mangling stripped).
                val name = method.name.substringBefore('-')
                calls += name
                when (name) {
                    "fetchContactPublicKeysByPrefix" -> SnapshotMap(emptyMap<Any, Any>())
                    "fetchDevice" -> null
                    "setInboundHopCount", "clearRxLogEntries" -> Unit
                    else -> SnapshotList.empty<Any>()
                }
            } as PersistenceStoreProtocol
            val adapted = RxLogServiceStore.from(contract)
            val radioId = rxLogSvcRadio()
            adapted.fetchChannels(radioId)
            adapted.fetchContactPublicKeysByPrefix(radioId)
            adapted.fetchDevice(radioId)
            adapted.setInboundHopCount(radioId, Bytes.of(1), 1, null)
            adapted.fetchRxLogEntries(radioId, 5)
            adapted.fetchRecentEntriesByDecryptStatus(radioId, DecryptStatus.SUCCESS, Instant.EPOCH)
            adapted.fetchEntriesWithTransportCode(radioId, 5)
            adapted.batchUpdateChannelMessageRegion(radioId, SnapshotList.empty())
            adapted.batchUpdateDMMessageRegion(radioId, SnapshotList.empty())
            adapted.clearRxLogEntries(radioId)
            assertEquals(
                listOf(
                    "fetchChannels", "fetchContactPublicKeysByPrefix", "fetchDevice", "setInboundHopCount",
                    "fetchRxLogEntries", "fetchRecentEntriesByDecryptStatus", "fetchEntriesWithTransportCode",
                    "batchUpdateChannelMessageRegion", "batchUpdateDMMessageRegion", "clearRxLogEntries",
                ),
                calls,
            )
        },
    )
}
