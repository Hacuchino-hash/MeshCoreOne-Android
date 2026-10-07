// AndroidOnly: WP-211 Review regressions: a context closed under a live caller is a typed NotConnected, not a silent cancellation.
package com.meshcoreone.android.core.services.device

import com.meshcoreone.android.core.contracts.domain.errors.*
import com.meshcoreone.android.core.protocol.command.PacketBuilder
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.TestFactory
import kotlin.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SettingsCloseTest {
    @TestFactory
    fun closeCases() = listOf(
        nativeAsync("closing the context during an in-flight verified write reports NotConnected to the live caller") {
            val fixture = settingsFixture()
            try {
                val write = request { fixture.settings.setNodeNameVerified("New") }
                runCurrent()
                assertEquals(PacketBuilder.setName("New"), fixture.radio.sent.last(), "The write must be on the wire")
                fixture.context.close()
                runCurrent()
                assertEquals(SettingsServiceError.NotConnected,
                    assertFailsWith<SettingsServiceException> { write.await() }.error)
            } finally { fixture.close() }
        },
        nativeAsync("a call after the context closed reports NotConnected without a transport side effect") {
            val fixture = settingsFixture()
            try {
                val sent = fixture.radio.sent.size
                fixture.context.close()
                assertEquals(SettingsServiceError.NotConnected,
                    assertFailsWith<SettingsServiceException> { fixture.settings.getBattery() }.error)
                assertEquals(sent, fixture.radio.sent.size)
            } finally { fixture.close() }
        },
    )
}
