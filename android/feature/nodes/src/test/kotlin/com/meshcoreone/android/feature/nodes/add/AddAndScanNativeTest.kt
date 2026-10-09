// AndroidOnly: WP-311 Native coverage of manual add validation and the QR scan claim/lookup/import flow.
package com.meshcoreone.android.feature.nodes.add

import com.meshcoreone.android.core.l10n.R
import com.meshcoreone.android.core.protocol.model.ContactType
import com.meshcoreone.android.feature.nodes.deps.NodesContactFailure
import com.meshcoreone.android.feature.nodes.deps.NodesMessage
import com.meshcoreone.android.feature.nodes.support.Fixtures
import com.meshcoreone.android.feature.nodes.support.Harness
import com.meshcoreone.android.feature.nodes.support.scenario
import com.meshcoreone.android.feature.nodes.support.settle
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class AddAndScanNativeTest {
    private val hex64 = "ab".repeat(32)

    @Test
    fun `key field keeps lowercase hex and validates the 64-character length`() = scenario {
        val form = AddContactStateHolder(Harness(clock).dependencies)
        form.setPublicKeyHex("AB cd-EF")
        assertEquals("abcdef", form.state.value.publicKeyHex)
        assertEquals(PublicKeyStatus.Count(6, 64), form.state.value.publicKeyStatus)
        form.setPublicKeyHex(hex64.uppercase())
        assertEquals(PublicKeyStatus.Valid, form.state.value.publicKeyStatus)
        assertFalse(form.state.value.canAdd)
        form.setContactName("Alice")
        assertTrue(form.state.value.canAdd)
    }

    @Test
    fun `names are capped to the usable firmware byte length on whole characters`() = scenario {
        val form = AddContactStateHolder(Harness(clock).dependencies)
        form.setContactName("\u00E9".repeat(20))
        assertEquals("\u00E9".repeat(15), form.state.value.contactName)
    }

    @Test
    fun `fullwidth hex counts toward the length but fails conversion`() = scenario {
        val harness = Harness(clock).connect()
        val form = AddContactStateHolder(harness.dependencies)
        form.setContactName("Alice")
        form.setPublicKeyHex("\uFF21" + hex64.drop(1))
        assertTrue(form.state.value.isValidPublicKey)
        assertFalse(form.add())
        assertEquals(NodesMessage.res(R.string.l10n_app_contacts_contacts_add_error_invalidformat), form.state.value.errorMessage)
        assertTrue(harness.contactService.calls.isEmpty())
    }

    @Test
    fun `add sends a never-advertised flood frame and reports a full table`() = scenario {
        val harness = Harness(clock).connect()
        val form = AddContactStateHolder(harness.dependencies)
        form.setContactName("Relay")
        form.setPublicKeyHex(hex64)
        form.selectType(ContactType.REPEATER)
        assertTrue(form.add())
        val stored = harness.store.contacts.single()
        assertEquals(ContactType.REPEATER, stored.type)
        assertTrue(stored.isFloodRouted)
        assertEquals(0u, stored.lastAdvertTimestamp)
        assertEquals(clock.wallNow.epochSecond.toUInt(), stored.lastModified)
        harness.contactService.addBehavior = { _, _ -> throw NodesContactFailure.ContactTableFull() }
        assertFalse(form.add())
        assertEquals(NodesMessage.res(R.string.l10n_app_contacts_contacts_add_error_nodelistfull, 350), form.state.value.errorMessage)
        assertFalse(form.state.value.isSubmitting)
    }

    @Test
    fun `add without a radio reports not connected`() = scenario {
        val form = AddContactStateHolder(Harness(clock).dependencies)
        assertFalse(form.add())
        assertEquals(NodesMessage.res(R.string.l10n_app_contacts_contacts_add_error_notconnected), form.state.value.errorMessage)
    }

    @Test
    fun `pasting a contact link fills the form and a bad link shows the row error`() = scenario {
        val harness = Harness(clock)
        val form = AddContactStateHolder(harness.dependencies)
        form.pasteContactUrl("https://example.com")
        assertTrue(form.state.value.showPasteError)
        form.pasteContactUrl(harness.codec.exportContactUri("Room", Fixtures.repeated(0xCD), ContactType.ROOM))
        val state = form.state.value
        assertFalse(state.showPasteError)
        assertEquals("Room", state.contactName)
        assertEquals("cd".repeat(32), state.publicKeyHex)
        assertEquals(ContactType.ROOM, state.selectedType)
    }

    @Test
    fun `an invalid scan reports the format error and keeps scanning`() = scenario {
        val scan = ScanContactStateHolder(Harness(clock).connect().dependencies, scope)
        scan.handleScanResult("not a contact")
        assertEquals(NodesMessage.res(R.string.l10n_app_contacts_contacts_scan_error_invalidformat), scan.state.value.errorMessage)
        assertEquals(1, scan.state.value.errorFeedback)
        assertNull(scan.state.value.scannedContact)
    }

    @Test
    fun `a saved contact opens as View and later scans are ignored`() = scenario {
        val harness = Harness(clock).connect()
        val radio = checkNotNull(harness.session.radioId)
        val saved = Fixtures.contact(radioId = radio, publicKey = Fixtures.repeated(0xAA), name = "Saved")
        harness.store.contacts += saved
        val scan = ScanContactStateHolder(harness.dependencies, scope)
        scan.handleScanResult(harness.codec.exportContactUri("Claimed", saved.publicKey, ContactType.CHAT))
        scan.handleScanResult(harness.codec.exportContactUri("Other", Fixtures.repeated(0xBB), ContactType.CHAT))
        settle()
        val state = scan.state.value
        assertEquals("Claimed", state.scannedContact?.name)
        assertEquals(saved, state.existingContact)
        assertEquals(NodesMessage.Text("Saved"), state.title)
        assertEquals(1, state.selectionFeedback)
        assertEquals(ScanConfirmOutcome.Completed(saved), scan.confirm())
        assertTrue(harness.contactService.calls.isEmpty())
    }

    @Test
    fun `importing a new scanned contact adds it after confirmation and opens the stored row`() = scenario {
        val harness = Harness(clock).connect()
        val scan = ScanContactStateHolder(harness.dependencies, scope)
        scan.handleScanResult(harness.codec.exportContactUri("Relay", Fixtures.repeated(0xEE), ContactType.REPEATER))
        settle()
        assertTrue(harness.contactService.calls.isEmpty())
        val outcome = assertIs<ScanConfirmOutcome.Completed>(scan.confirm())
        assertEquals("Relay", outcome.contact.name)
        assertEquals(listOf("add:Relay"), harness.contactService.calls)
        assertEquals(1, scan.state.value.successFeedback)
        assertEquals(NodesMessage.res(R.string.l10n_app_contacts_contacts_scan_accessibility_added, "Relay"), harness.announcer.messages.last())
    }

    @Test
    fun `import failure is announced and scan again resets the review`() = scenario {
        val harness = Harness(clock).connect()
        harness.contactService.addBehavior = { _, _ -> error("radio") }
        val scan = ScanContactStateHolder(harness.dependencies, scope)
        scan.handleScanResult(harness.codec.exportContactUri("Relay", Fixtures.repeated(0xEE), ContactType.REPEATER))
        settle()
        assertEquals(ScanConfirmOutcome.Stay, scan.confirm())
        val message = NodesMessage.res(R.string.l10n_app_contacts_contacts_scan_error_importfailed, "failed: radio")
        assertEquals(message, scan.state.value.errorMessage)
        assertEquals(message, harness.announcer.messages.last())
        assertFalse(scan.state.value.isImporting)
        scan.resetToScanner()
        assertNull(scan.state.value.scannedContact)
        assertNull(scan.state.value.errorMessage)
    }
}
