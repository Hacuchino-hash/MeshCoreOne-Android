// AndroidOnly: WP-209 Executed producer assertions for the neutral contact/channel/advertisement fault projection.
package com.meshcoreone.android.core.services.contacts

import com.meshcoreone.android.core.contracts.domain.errors.AdvertisementFault
import com.meshcoreone.android.core.contracts.domain.errors.ChannelServiceFault
import com.meshcoreone.android.core.contracts.domain.errors.ContactServiceFault
import com.meshcoreone.android.core.contracts.domain.errors.SourceServiceFault
import com.meshcoreone.android.core.contracts.domain.errors.SourceServiceFaultCarrier
import com.meshcoreone.android.core.protocol.config.MeshCoreException
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

/**
 * Every actual producer case is constructed, projected through [SourceServiceFaultCarrier] and checked for
 * the exact family/case, identical payload values and the identical session cause instance, while its own
 * message, cause and raw fields stay as they were before the carrier existed.
 */
class SourceServiceFaultProjectionTests {
    /** Projects through the neutral carrier, not the producer's covariant override, and checks stability. */
    private fun project(producer: Throwable): SourceServiceFault {
        val carrier = assertIs<SourceServiceFaultCarrier>(producer)
        val fault = carrier.sourceServiceFault
        assertEquals(fault, carrier.sourceServiceFault, "projection must be stable across reads")
        return fault
    }

    private fun assertPlain(producer: Throwable, expected: SourceServiceFault, message: String) {
        assertSame(expected, project(producer))
        assertEquals(message, producer.message)
        assertNull(producer.cause)
    }

    private fun assertSession(producer: Throwable, cause: MeshCoreException, message: String): SourceServiceFault {
        val fault = project(producer)
        assertEquals(message, producer.message)
        assertSame(cause, producer.cause)
        return fault
    }

    // MARK: - Contact (8)

    @TestFactory
    fun contactProjection(): List<DynamicTest> = listOf(
        contactsNative("ContactServiceError.NotConnected projects to ContactServiceFault.NotConnected with message and cause unchanged") {
            val producer = ContactServiceError.NotConnected()
            assertPlain(producer, ContactServiceFault.NotConnected, "Not connected to radio")
            assertEquals("Not connected to radio", producer.errorDescription)
            assertEquals("ContactServiceError.notConnected", producer.toString())
        },
        contactsNative("ContactServiceError.SendFailed projects to ContactServiceFault.SendFailed with message and cause unchanged") {
            val producer = ContactServiceError.SendFailed()
            assertPlain(producer, ContactServiceFault.SendFailed, "Failed to send message")
            assertEquals("ContactServiceError.sendFailed", producer.toString())
        },
        contactsNative("ContactServiceError.InvalidResponse projects to ContactServiceFault.InvalidResponse with message and cause unchanged") {
            val producer = ContactServiceError.InvalidResponse()
            assertPlain(producer, ContactServiceFault.InvalidResponse, "Invalid response from device")
            assertEquals("ContactServiceError.invalidResponse", producer.toString())
        },
        contactsNative("ContactServiceError.SyncInterrupted projects to ContactServiceFault.SyncInterrupted with message and cause unchanged") {
            val producer = ContactServiceError.SyncInterrupted()
            assertPlain(producer, ContactServiceFault.SyncInterrupted, "Sync was interrupted")
            assertEquals("ContactServiceError.syncInterrupted", producer.toString())
        },
        contactsNative("ContactServiceError.ContactNotFound projects to ContactServiceFault.ContactNotFound with message and cause unchanged") {
            val producer = ContactServiceError.ContactNotFound()
            assertPlain(producer, ContactServiceFault.ContactNotFound, "Contact not found on device")
            assertEquals("ContactServiceError.contactNotFound", producer.toString())
        },
        contactsNative("ContactServiceError.ContactTableFull projects to ContactServiceFault.ContactTableFull with message and cause unchanged") {
            val producer = ContactServiceError.ContactTableFull()
            assertPlain(producer, ContactServiceFault.ContactTableFull, "Device node list is full")
            assertEquals("ContactServiceError.contactTableFull", producer.toString())
        },
        contactsNative("ContactServiceError.ShareContactUnavailable projects to ContactServiceFault.ShareContactUnavailable with message and cause unchanged") {
            val producer = ContactServiceError.ShareContactUnavailable()
            assertPlain(
                producer,
                ContactServiceFault.ShareContactUnavailable,
                "Unable to share node. The node's advertisement may be missing or too old.",
            )
            assertEquals("ContactServiceError.shareContactUnavailable", producer.toString())
        },
        contactsNative("ContactServiceError.SessionError projects to ContactServiceFault.SessionError carrying the identical MeshCoreException") {
            val cause = MeshCoreException.DeviceError(3u)
            val producer = ContactServiceError.SessionError(cause)
            val fault = assertIs<ContactServiceFault.SessionError>(
                assertSession(producer, cause, "Device returned error code 3"),
            )
            assertSame(cause, fault.error)
            assertSame(cause, producer.error)
            assertEquals("Device returned error code 3", producer.errorDescription)
            assertEquals("ContactServiceError.sessionError(DeviceError)", producer.toString())
        },
    )

    // MARK: - Channel (9)

    @TestFactory
    fun channelProjection(): List<DynamicTest> = listOf(
        contactsNative("ChannelServiceError.NotConnected projects to ChannelServiceFault.NotConnected with message and cause unchanged") {
            assertPlain(ChannelServiceError.NotConnected(), ChannelServiceFault.NotConnected, "Not connected to device.")
        },
        contactsNative("ChannelServiceError.ChannelNotFound projects to ChannelServiceFault.ChannelNotFound with message and cause unchanged") {
            assertPlain(ChannelServiceError.ChannelNotFound(), ChannelServiceFault.ChannelNotFound, "Channel not found.")
        },
        contactsNative("ChannelServiceError.InvalidChannelIndex projects to ChannelServiceFault.InvalidChannelIndex with message and cause unchanged") {
            assertPlain(ChannelServiceError.InvalidChannelIndex(), ChannelServiceFault.InvalidChannelIndex, "Invalid channel index.")
        },
        contactsNative("ChannelServiceError.SecretHashingFailed projects to ChannelServiceFault.SecretHashingFailed with message and cause unchanged") {
            assertPlain(
                ChannelServiceError.SecretHashingFailed(),
                ChannelServiceFault.SecretHashingFailed,
                "Failed to hash channel secret.",
            )
        },
        contactsNative("ChannelServiceError.SaveFailed projects to ChannelServiceFault.SaveFailed carrying the identical raw reason") {
            val reason = "Device returned error code 6"
            val producer = ChannelServiceError.SaveFailed(reason)
            val fault = assertIs<ChannelServiceFault.SaveFailed>(project(producer))
            assertEquals(ChannelServiceFault.SaveFailed(reason), fault)
            assertSame(reason, fault.reason)
            assertSame(reason, producer.reason)
            assertEquals("Failed to save channel: Device returned error code 6", producer.message)
            assertNull(producer.cause)
        },
        contactsNative("ChannelServiceError.SendFailed projects to ChannelServiceFault.SendFailed carrying the identical raw reason") {
            val reason = ""
            val producer = ChannelServiceError.SendFailed(reason)
            val fault = assertIs<ChannelServiceFault.SendFailed>(project(producer))
            assertEquals(ChannelServiceFault.SendFailed(reason), fault)
            assertSame(reason, fault.reason)
            assertSame(reason, producer.reason)
            assertEquals("Send failed: ", producer.message)
            assertNull(producer.cause)
        },
        contactsNative("ChannelServiceError.SessionError projects to ChannelServiceFault.SessionError carrying the identical MeshCoreException") {
            val cause = MeshCoreException.Timeout()
            val producer = ChannelServiceError.SessionError(cause)
            val fault = assertIs<ChannelServiceFault.SessionError>(
                assertSession(producer, cause, "Mesh operation timed out"),
            )
            assertSame(cause, fault.error)
            assertSame(cause, producer.error)
        },
        contactsNative("ChannelServiceError.SyncAlreadyInProgress projects to ChannelServiceFault.SyncAlreadyInProgress with message and cause unchanged") {
            assertPlain(
                ChannelServiceError.SyncAlreadyInProgress(),
                ChannelServiceFault.SyncAlreadyInProgress,
                "Channel sync is already in progress.",
            )
        },
        contactsNative("ChannelServiceError.CircuitBreakerOpen projects to ChannelServiceFault.CircuitBreakerOpen carrying the exact consecutiveFailures") {
            val producer = ChannelServiceError.CircuitBreakerOpen(consecutiveFailures = 3)
            val fault = assertIs<ChannelServiceFault.CircuitBreakerOpen>(project(producer))
            assertEquals(ChannelServiceFault.CircuitBreakerOpen(3), fault)
            assertEquals(3, fault.consecutiveFailures)
            assertEquals(3, producer.consecutiveFailures)
            assertEquals("Channel sync suspended after 3 consecutive failures.", producer.message)
            assertNull(producer.cause)
        },
    )

    // MARK: - Advertisement (4)

    @TestFactory
    fun advertisementProjection(): List<DynamicTest> = listOf(
        contactsNative("AdvertisementError.NotConnected projects to AdvertisementFault.NotConnected with message and cause unchanged") {
            assertPlain(AdvertisementError.NotConnected(), AdvertisementFault.NotConnected, "Not connected to device.")
        },
        contactsNative("AdvertisementError.SendFailed projects to AdvertisementFault.SendFailed with message and cause unchanged") {
            assertPlain(AdvertisementError.SendFailed(), AdvertisementFault.SendFailed, "Failed to send advertisement.")
        },
        contactsNative("AdvertisementError.InvalidResponse projects to AdvertisementFault.InvalidResponse with message and cause unchanged") {
            assertPlain(AdvertisementError.InvalidResponse(), AdvertisementFault.InvalidResponse, "Invalid response from device.")
        },
        contactsNative("AdvertisementError.SessionError projects to AdvertisementFault.SessionError carrying the identical MeshCoreException and its own cause chain") {
            val transportFailure = IllegalStateException("link dropped")
            val cause = MeshCoreException.ConnectionLost(transportFailure)
            val producer = AdvertisementError.SessionError(cause)
            val fault = assertIs<AdvertisementFault.SessionError>(
                assertSession(producer, cause, "Connection was lost"),
            )
            assertSame(cause, fault.error)
            assertSame(cause, producer.error)
            assertSame(transportFailure, fault.error.cause)
        },
    )

    // MARK: - Exhaustiveness

    /**
     * One instance of every producer case. kotlin-reflect is not on this module's classpath, so the
     * sealed hierarchies are read through the JVM `PermittedSubclasses` attribute instead.
     */
    private val contactProducers: List<ContactServiceError> = listOf(
        ContactServiceError.NotConnected(), ContactServiceError.SendFailed(), ContactServiceError.InvalidResponse(),
        ContactServiceError.SyncInterrupted(), ContactServiceError.ContactNotFound(), ContactServiceError.ContactTableFull(),
        ContactServiceError.ShareContactUnavailable(), ContactServiceError.SessionError(MeshCoreException.NotConnected()),
    )

    private val channelProducers: List<ChannelServiceError> = listOf(
        ChannelServiceError.NotConnected(), ChannelServiceError.ChannelNotFound(), ChannelServiceError.InvalidChannelIndex(),
        ChannelServiceError.SecretHashingFailed(), ChannelServiceError.SaveFailed("save"), ChannelServiceError.SendFailed("send"),
        ChannelServiceError.SessionError(MeshCoreException.DeviceError(2u)), ChannelServiceError.SyncAlreadyInProgress(),
        ChannelServiceError.CircuitBreakerOpen(5),
    )

    private val advertisementProducers: List<AdvertisementError> = listOf(
        AdvertisementError.NotConnected(), AdvertisementError.SendFailed(), AdvertisementError.InvalidResponse(),
        AdvertisementError.SessionError(MeshCoreException.Timeout()),
    )

    private fun permitted(type: Class<*>): Set<Class<*>> = type.permittedSubclasses.orEmpty().toSet()

    private fun assertBijection(
        producers: List<Throwable>,
        producerType: Class<*>,
        faultType: Class<*>,
        sourceCaseCount: Int,
    ) {
        val producerClasses = producers.map { it.javaClass }
        val faultClasses = producers.map { project(it).javaClass }
        assertEquals(sourceCaseCount, permitted(producerType).size, "producer cases")
        assertEquals(sourceCaseCount, permitted(faultType).size, "payload cases")
        assertEquals(permitted(producerType), producerClasses.toSet(), "every producer case is exercised once")
        assertEquals(sourceCaseCount, producerClasses.size)
        assertEquals(permitted(faultType), faultClasses.toSet(), "every payload case is produced")
        assertEquals(sourceCaseCount, faultClasses.toSet().size, "no two producer cases share a payload case")
        producers.zip(faultClasses).forEach { (producer, faultClass) ->
            assertEquals(producer.javaClass.simpleName, faultClass.simpleName, "case-for-case name mapping")
        }
    }

    @TestFactory
    fun exhaustiveness(): List<DynamicTest> = listOf(
        contactsNative("every ContactServiceError case (8) maps one-for-one onto every ContactServiceFault case") {
            assertBijection(contactProducers, ContactServiceError::class.java, ContactServiceFault::class.java, 8)
        },
        contactsNative("every ChannelServiceError case (9) maps one-for-one onto every ChannelServiceFault case") {
            assertBijection(channelProducers, ChannelServiceError::class.java, ChannelServiceFault::class.java, 9)
        },
        contactsNative("every AdvertisementError case (4) maps one-for-one onto every AdvertisementFault case") {
            assertBijection(advertisementProducers, AdvertisementError::class.java, AdvertisementFault::class.java, 4)
        },
        contactsNative("the three families are the SourceServiceFault families this declaration owns") {
            val owned = setOf(ContactServiceFault::class.java, ChannelServiceFault::class.java, AdvertisementFault::class.java)
            assertEquals(owned, permitted(SourceServiceFault::class.java).intersect(owned))
            val expectedFamily = contactProducers.map { it to ContactServiceFault::class.java } +
                channelProducers.map { it to ChannelServiceFault::class.java } +
                advertisementProducers.map { it to AdvertisementFault::class.java }
            expectedFamily.forEach { (producer, family) ->
                assertEquals(family, owned.single { it.isInstance(project(producer)) }, producer.javaClass.name)
            }
        },
    )
}
