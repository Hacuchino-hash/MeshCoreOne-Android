// PortedFrom: MC1Tests/Views/Chats/ChannelInfoRegionQueryTargetsTests.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.feature.chats.channels

import com.meshcoreone.android.core.model.ContactDTO
import com.meshcoreone.android.core.model.DiscoveredNodeDTO
import com.meshcoreone.android.core.protocol.bytes.Bytes
import com.meshcoreone.android.core.protocol.model.ContactType
import com.meshcoreone.android.feature.chats.list.support.Fixtures
import com.meshcoreone.android.feature.chats.list.support.OriginalCase
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class RegionQueryTargetsTest {
    private fun key(byte: Int) = Bytes(ByteArray(32) { byte.toByte() })
    private val a = key(0x0A)
    private val b = key(0x0B)
    private val c = key(0x0C)
    private val d = key(0x0D)
    private val e = key(0x0E)

    private fun contact(publicKey: Bytes, type: ContactType, name: String = "contact", outPathLength: UByte = 0xFFu, outPath: Bytes = Bytes.EMPTY) =
        Fixtures.contact(name, publicKey = publicKey, typeRawValue = type.rawValue, outPathLength = outPathLength).copy(outPath = outPath)

    private fun node(publicKey: Bytes, type: ContactType, name: String = "discovered", outPathLength: UByte = 0xFFu, outPath: Bytes = Bytes.EMPTY) =
        DiscoveredNodeDTO(
            id = UUID.randomUUID(), radioId = Fixtures.radio, publicKey = publicKey, name = name, typeRawValue = type.rawValue,
            lastHeard = Instant.EPOCH, lastAdvertTimestamp = 0u, latitude = 0.0, longitude = 0.0,
            outPathLength = outPathLength, outPath = outPath, inboundHopCount = null, inboundHopAdvertTimestamp = null,
        )

    private fun build(responders: Set<Bytes>, contacts: List<ContactDTO> = emptyList(), nodes: List<DiscoveredNodeDTO> = emptyList()) =
        RegionQueryTargets.build(responders, contacts, nodes, supportsAdHocRequest = true)

    @Test @OriginalCase("ChannelInfoRegionQueryTargetsTests::queries responders present only in the discovered-nodes table()")
    fun `queries responders present only in the discovered-nodes table`() {
        val targets = build(setOf(a), nodes = listOf(node(a, ContactType.REPEATER)))
        assertEquals(1, targets.size)
        assertEquals(a.hexString, targets.first().publicKey.hexString)
    }

    @Test @OriginalCase("ChannelInfoRegionQueryTargetsTests::prefers contact record when both sources have the responder()")
    fun `prefers contact record when both sources have the responder`() {
        val targets = build(setOf(a), listOf(contact(a, ContactType.REPEATER, "from-contact")), listOf(node(a, ContactType.REPEATER, "from-discovery")))
        assertEquals(1, targets.size)
        assertEquals("from-contact", targets.first().advertisedName)
    }

    @Test @OriginalCase("ChannelInfoRegionQueryTargetsTests::unions contacts and discovered nodes without duplication()")
    fun `unions contacts and discovered nodes without duplication`() {
        val targets = build(
            setOf(a, b, c), listOf(contact(a, ContactType.REPEATER)),
            listOf(node(a, ContactType.REPEATER), node(b, ContactType.REPEATER), node(c, ContactType.REPEATER)),
        )
        assertEquals(setOf(a.hexString, b.hexString, c.hexString), targets.map { it.publicKey.hexString }.toSet())
        assertEquals(3, targets.size)
    }

    @Test @OriginalCase("ChannelInfoRegionQueryTargetsTests::excludes non-repeater types from both pools()")
    fun `excludes non-repeater types from both pools`() {
        val targets = build(
            setOf(a, b, c, d),
            listOf(contact(a, ContactType.CHAT), contact(b, ContactType.REPEATER)),
            listOf(node(c, ContactType.ROOM), node(d, ContactType.REPEATER)),
        )
        assertEquals(setOf(b.hexString, d.hexString), targets.map { it.publicKey.hexString }.toSet())
    }

    @Test @OriginalCase("ChannelInfoRegionQueryTargetsTests::drops responders that are in neither pool()")
    fun `drops responders that are in neither pool`() {
        val targets = build(setOf(a, e), listOf(contact(a, ContactType.REPEATER)))
        assertEquals(listOf(a.hexString), targets.map { it.publicKey.hexString })
    }

    @Test @OriginalCase("ChannelInfoRegionQueryTargetsTests::drops non-responders even if they are repeater contacts()")
    fun `drops non-responders even if they are repeater contacts`() {
        val targets = build(setOf(a), listOf(contact(a, ContactType.REPEATER), contact(b, ContactType.REPEATER)))
        assertEquals(listOf(a.hexString), targets.map { it.publicKey.hexString })
    }

    @Test @OriginalCase("ChannelInfoRegionQueryTargetsTests::forwards outPath bytes from contact source()")
    fun `forwards outPath bytes from contact source`() {
        val path = Bytes(byteArrayOf(0x11, 0x22, 0x33))
        val target = build(setOf(a), listOf(contact(a, ContactType.REPEATER, outPathLength = 3u, outPath = path))).first()
        assertEquals(3u.toUByte(), target.outPathLength)
        assertEquals(path.hexString, target.outPath.hexString)
    }

    @Test @OriginalCase("ChannelInfoRegionQueryTargetsTests::forwards outPath bytes from discovered-node source()")
    fun `forwards outPath bytes from discovered-node source`() {
        val path = Bytes(byteArrayOf(0xAA.toByte(), 0xBB.toByte()))
        val target = build(setOf(a), nodes = listOf(node(a, ContactType.REPEATER, outPathLength = 2u, outPath = path))).first()
        assertEquals(2u.toUByte(), target.outPathLength)
        assertEquals(path.hexString, target.outPath.hexString)
    }

    @Test @OriginalCase("ChannelInfoRegionQueryTargetsTests::uses contact outPath when both sources have routing data()")
    fun `uses contact outPath when both sources have routing data`() {
        val contactPath = Bytes(byteArrayOf(0x11, 0x22, 0x33))
        val nodePath = Bytes(byteArrayOf(0xAA.toByte(), 0xBB.toByte()))
        val target = build(
            setOf(a), listOf(contact(a, ContactType.REPEATER, outPathLength = 3u, outPath = contactPath)),
            listOf(node(a, ContactType.REPEATER, outPathLength = 2u, outPath = nodePath)),
        ).first()
        assertEquals(3u.toUByte(), target.outPathLength)
        assertEquals(contactPath.hexString, target.outPath.hexString)
    }

    @Test
    fun `without ad-hoc support only contact repeaters are queried`() {
        val targets = RegionQueryTargets.build(
            setOf(a, b), listOf(contact(a, ContactType.REPEATER)), listOf(node(b, ContactType.REPEATER)), supportsAdHocRequest = false,
        )
        assertEquals(listOf(a.hexString), targets.map { it.publicKey.hexString })
        assertTrue(RegionQueryTargets.build(emptySet(), emptyList(), emptyList(), true).isEmpty())
    }
}
