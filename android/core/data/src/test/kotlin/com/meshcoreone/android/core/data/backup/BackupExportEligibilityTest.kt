// AndroidOnly: WP-203 Export success is limited to snapshots the same envelope importer can represent.
package com.meshcoreone.android.core.data.backup

import com.meshcoreone.android.core.database.*
import com.meshcoreone.android.core.model.*
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class BackupExportEligibilityTest : BackupRoomTest() {
    @Test fun contactUuidCollisionAcrossRadiosFailsBeforeExportingAndPreservesBothRows() = runBlocking {
        val first = contact()
        val second = contact(OTHER_RADIO, publicKey = key(0xBB))
        seed(envelope(contacts = listOf(first, second)))
        val failure = exportFailure()
        assertEquals(BackupValueProblem.IDENTITY, (failure.cause as BackupValueException).problem)
        assertEquals(first, db.contacts().byId(RADIO.value, first.id)?.toDTO())
        assertEquals(second, db.contacts().byId(OTHER_RADIO.value, second.id)?.toDTO())
        assertTrue(db.sessions().backupAll().isEmpty())
        assertTrue(db.messages().backupRadioIds().isEmpty())
    }

    @Test fun otherImporterEligibilityMismatchesAlsoCannotProduceSuccessShapedExport() = runBlocking {
        seed(envelope(contacts = listOf(contact(OTHER_RADIO)), messages = listOf(message())))
        val failure = exportFailure()
        assertEquals(BackupValueProblem.RELATIONSHIP, (failure.cause as BackupValueException).problem)
        assertNotNull(db.messages().byId(RADIO.value, id(4)))
        assertNotNull(db.contacts().byId(OTHER_RADIO.value, id(2)))
        db.messages().clearRadio(RADIO.value)
        db.contacts().clearRadio(OTHER_RADIO.value)
        seed(envelope(devices = listOf(device(), device(publicKey = key(0xBB), id = id(90)))))
        assertEquals(BackupValueProblem.IDENTITY, (exportFailure().cause as BackupValueException).problem)
        assertEquals(2, db.devices().all().size)
    }

    @Test fun failedEligibilityClosesOutputWithoutWritingAnyBytesOrMutatingChildren() = runBlocking {
        seed(envelope(contacts = listOf(contact(), contact(OTHER_RADIO)), messages = listOf(message())))
        var closed = false
        val output = object : ByteArrayOutputStream() {
            override fun close() { closed = true; super.close() }
        }
        try {
            service.export(store, output)
            fail("Unrepresentable snapshot must fail")
        } catch (failure: AppBackupException) { assertTrue(failure.error is AppBackupError.ExportFailed) }
        assertTrue(closed)
        assertEquals(0, output.size())
        assertNotNull(db.messages().byId(RADIO.value, id(4)))
        assertEquals(2, db.contacts().backupAll().size)
    }

    private suspend fun exportFailure(): AppBackupException =
        try { service.export(store); throw AssertionError("Expected typed export failure") }
        catch (failure: AppBackupException) {
            assertTrue(failure.error is AppBackupError.ExportFailed)
            failure
        }
}
