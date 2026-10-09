// PortedFrom: MC1Tests/Views/Chats/ContactMatchRowTests.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.feature.chats.list

import com.meshcoreone.android.core.protocol.bytes.Bytes
import com.meshcoreone.android.feature.chats.list.support.Fixtures.contact
import com.meshcoreone.android.feature.chats.list.support.OriginalCase
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import org.junit.Test

class ContactMatchRowTest {
    /**
     * `recencyTimestamp` follows `lastModified`, which a path update or a favorite toggle bumps with no
     * on-air advert; anything meaning "when did we last hear this node" reads `lastAdvertTimestamp`.
     */
    @Test @OriginalCase("ContactMatchRowTests::recencyTimestamp tracks lastModified not lastAdvertTimestamp()")
    fun `recencyTimestamp tracks lastModified not lastAdvertTimestamp`() {
        val relay = contact(
            "Relay", typeRawValue = 2u, publicKey = Bytes(ByteArray(32) { 0xAB.toByte() }),
            lastAdvertTimestamp = 1000u, lastModified = 10000u, lastHeardTimestamp = 1000u,
        )
        assertEquals(10000u, relay.recencyTimestamp)
        assertNotEquals(relay.lastAdvertTimestamp, relay.recencyTimestamp)
    }
}
