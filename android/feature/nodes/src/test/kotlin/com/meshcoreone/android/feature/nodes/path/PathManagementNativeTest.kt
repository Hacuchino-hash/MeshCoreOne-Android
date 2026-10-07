// AndroidOnly: WP-311 Native coverage of path discovery timing, retransmits and the save/reset/direct commands.
package com.meshcoreone.android.feature.nodes.path

import com.meshcoreone.android.core.l10n.R
import com.meshcoreone.android.core.protocol.bytes.Bytes
import com.meshcoreone.android.feature.nodes.deps.NodesMessage
import com.meshcoreone.android.feature.nodes.support.FakeContactService
import com.meshcoreone.android.feature.nodes.support.Fixtures
import com.meshcoreone.android.feature.nodes.support.Fixtures.pathContact
import com.meshcoreone.android.feature.nodes.support.Harness
import com.meshcoreone.android.feature.nodes.support.scenario
import com.meshcoreone.android.feature.nodes.support.settle
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import org.junit.Test

class PathManagementNativeTest {
    @Test
    fun `discovery without a response times out to no-path-found and counts down`() = scenario {
        val harness = Harness(clock).connect()
        harness.timeouts.discoverySeconds = 20.0
        val vm = PathManagementStateHolder(harness.dependencies, scope)
        vm.discoverPath(pathContact())
        settle()
        assertTrue(vm.state.value.isDiscovering)
        assertEquals(20, vm.state.value.discoverySecondsRemaining)
        clock.advanceBy(5.seconds)
        assertEquals(15, vm.state.value.discoverySecondsRemaining)
        clock.advanceBy(15.seconds)
        val state = vm.state.value
        assertFalse(state.isDiscovering)
        assertEquals(PathDiscoveryResult.NoPathFound, state.discoveryResult)
        assertTrue(state.showDiscoveryResult)
        assertNull(state.discoverySecondsRemaining)
        assertEquals(listOf("discover"), harness.contactService.calls)
    }

    @Test
    fun `retransmits at the firmware interval until the budget ends`() = scenario {
        val harness = Harness(clock).connect()
        harness.timeouts.discoverySeconds = 20.0
        harness.timeouts.retransmit = 6.seconds
        harness.contactService.sendPathDiscoveryBehavior = { FakeContactService.sentInfo(3000u) }
        val vm = PathManagementStateHolder(harness.dependencies, scope)
        vm.discoverPath(pathContact())
        settle()
        clock.advanceBy(20.seconds)
        // Initial send plus resends at 6, 12 and 18 seconds.
        assertEquals(4, harness.contactService.calls.count { it == "discover" })
        assertEquals(PathDiscoveryResult.NoPathFound, vm.state.value.discoveryResult)
    }

    @Test
    fun `a failed resend keeps waiting`() = scenario {
        val harness = Harness(clock).connect()
        harness.timeouts.retransmit = 5.seconds
        var sends = 0
        harness.contactService.sendPathDiscoveryBehavior = {
            sends += 1
            if (sends > 1) error("radio busy") else FakeContactService.sentInfo(2000u)
        }
        val vm = PathManagementStateHolder(harness.dependencies, scope)
        vm.discoverPath(pathContact())
        settle()
        clock.advanceBy(12.seconds)
        assertTrue(vm.state.value.isDiscovering)
        vm.handleDiscoveryResponse(1)
        assertEquals(PathDiscoveryResult.Success(1), vm.state.value.discoveryResult)
    }

    @Test
    fun `a response mid-wait wins and the expiry never overwrites it`() = scenario {
        val harness = Harness(clock).connect()
        val vm = PathManagementStateHolder(harness.dependencies, scope)
        vm.discoverPath(pathContact())
        settle()
        clock.advanceBy(3.seconds)
        vm.handleDiscoveryResponse(2)
        clock.advanceBy(30.seconds)
        assertEquals(PathDiscoveryResult.Success(2), vm.state.value.discoveryResult)
        assertFalse(vm.state.value.isDiscovering)
    }

    @Test
    fun `a failing initial send reports the failure`() = scenario {
        val harness = Harness(clock).connect()
        harness.contactService.sendPathDiscoveryBehavior = { error("no ack") }
        val vm = PathManagementStateHolder(harness.dependencies, scope)
        vm.discoverPath(pathContact())
        settle()
        val result = assertIs<PathDiscoveryResult.Failed>(vm.state.value.discoveryResult)
        assertEquals("failed: no ack", result.message)
        assertFalse(vm.state.value.isDiscovering)
    }

    @Test
    fun `discovery without a contact service is a no-op`() = scenario {
        val vm = PathManagementStateHolder(Harness(clock).dependencies, scope)
        vm.discoverPath(pathContact())
        settle()
        assertFalse(vm.state.value.isDiscovering)
    }

    @Test
    fun `save encodes at the device hash size and refreshes the contact`() = scenario {
        val harness = Harness(clock).connect(device = Fixtures.device(pathHashMode = 1u))
        val vm = PathManagementStateHolder(harness.dependencies, scope)
        var refreshed = 0
        vm.onContactNeedsRefresh = { refreshed += 1 }
        vm.insert(pathContact(publicKey = Fixtures.key(0xA1, 0xB2)), AddHopIntent.APPEND)
        assertTrue(vm.saveFromEditor(pathContact()))
        assertEquals(listOf(Bytes.of(0xA1, 0xB2) to 0x41u.toUByte()), harness.contactService.setPaths)
        assertEquals(1, refreshed)
        assertFalse(vm.state.value.isSettingPath)
    }

    @Test
    fun `save refuses an unresolvable narrow hop before any radio command`() = scenario {
        val harness = Harness(clock).connect(device = Fixtures.device(pathHashMode = 2u))
        val vm = PathManagementStateHolder(harness.dependencies, scope)
        vm.seed { it.copy(editablePath = listOf(PathHop(Bytes.of(0xA1), null, null))) }
        assertFalse(vm.saveFromEditor(pathContact()))
        assertEquals(NodesMessage.res(R.string.l10n_app_contacts_contacts_pathmanagement_error_hopresizerequired), vm.state.value.errorMessage)
        assertTrue(harness.contactService.setPaths.isEmpty())
    }

    @Test
    fun `reset failure is reported with the reset message`() = scenario {
        val harness = Harness(clock).connect()
        harness.contactService.resetPathBehavior = { error("timeout") }
        val vm = PathManagementStateHolder(harness.dependencies, scope)
        assertFalse(vm.confirmFloodRouting(pathContact()))
        assertEquals(NodesMessage.res(R.string.l10n_app_contacts_contacts_pathmanagement_error_resetfailed, "failed: timeout"), vm.state.value.errorMessage)
        assertFalse(vm.state.value.isSettingPath)
    }

    @Test
    fun `direct routing sets an empty zero-hop path`() = scenario {
        val harness = Harness(clock).connect()
        val vm = PathManagementStateHolder(harness.dependencies, scope)
        assertTrue(vm.confirmDirectRouting(pathContact()))
        assertEquals(listOf(Bytes.EMPTY to 0x00u.toUByte()), harness.contactService.setPaths)
    }

    @Test
    fun `reserved hash mode is clamped to three bytes`() = scenario {
        val harness = Harness(clock).connect(device = Fixtures.device(pathHashMode = 3u))
        val vm = PathManagementStateHolder(harness.dependencies, scope)
        assertEquals(3, vm.hashSize)
        assertEquals(21, vm.maxHopCount)
    }

    @Test
    fun `load contacts splits repeaters and rooms and skips a cached reload`() = scenario {
        val harness = Harness(clock).connect()
        val radio = checkNotNull(harness.session.radioId)
        harness.store.contacts += pathContact(name = "R", radioId = radio)
        harness.store.contacts += pathContact(name = "Room", radioId = radio, type = com.meshcoreone.android.core.protocol.model.ContactType.ROOM)
        harness.store.discovered += Fixtures.discoveredRepeater(radioId = radio)
        val vm = PathManagementStateHolder(harness.dependencies, scope)
        vm.loadContacts(radio)
        assertEquals(listOf("R"), vm.availableRepeaters.map { it.name })
        assertEquals(listOf("Room"), vm.availableRooms.map { it.name })
        assertEquals(1, vm.discoveredRepeaters.size)
        harness.store.contacts.clear()
        vm.loadContacts(radio)
        assertEquals(2, vm.state.value.allContacts.size)
        vm.loadContacts(radio, forceReload = true)
        assertTrue(vm.state.value.allContacts.isEmpty())
    }

    @Test
    fun `picker sections de-duplicate only in the all filter`() = scenario {
        val harness = Harness(clock)
        val vm = PathManagementStateHolder(harness.dependencies, scope)
        val favorite = pathContact(name = "Fav", publicKey = Fixtures.key(0x01), isFavorite = true)
        val plain = pathContact(name = "plain", publicKey = Fixtures.key(0x02))
        val shadowed = Fixtures.discoveredRepeater(name = "Shadow", publicKey = Fixtures.key(0x02))
        val discovered = Fixtures.discoveredRepeater(name = "Disc", publicKey = Fixtures.key(0x03))
        vm.seed { it.copy(availableRepeaters = listOf(plain, favorite), discoveredNodes = listOf(shadowed, discovered)) }
        val all = AddHopPicker.buildResults(vm, AddHopFilter.ALL, listOf(Fixtures.key(0x01)), "", java.util.Locale.US)
        assertEquals(listOf("Fav"), all.recent.map { it.displayName })
        assertTrue(all.favorites.isEmpty())
        assertEquals(listOf("plain"), all.contacts.map { it.displayName })
        assertEquals(listOf("Disc"), all.discovered.map { it.displayName })
        val favorites = AddHopPicker.buildResults(vm, AddHopFilter.FAVORITES, listOf(Fixtures.key(0x01)), "", java.util.Locale.US)
        assertEquals(listOf("Fav"), favorites.favorites.map { it.displayName })
        assertTrue(favorites.recent.isEmpty())
        val discoveredOnly = AddHopPicker.buildResults(vm, AddHopFilter.DISCOVERED, emptyList(), "", java.util.Locale.US)
        assertEquals(listOf("Disc", "Shadow"), discoveredOnly.discovered.map { it.displayName })
        assertTrue(AddHopPicker.isBulkMode("A1,B2"))
        assertEquals(NodesMessage.res(R.string.l10n_app_contacts_contacts_pathedit_positionappend, 1), AddHopPicker.bannerText(vm, AddHopIntent.APPEND))
    }
}
