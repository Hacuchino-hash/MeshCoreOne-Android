// AndroidOnly: WP-311 Native coverage of the bulk hop-code parser and the path editor's code/move actions.
package com.meshcoreone.android.feature.nodes.path

import com.meshcoreone.android.core.protocol.bytes.Bytes
import com.meshcoreone.android.feature.nodes.support.Fixtures
import com.meshcoreone.android.feature.nodes.support.Fixtures.pathContact
import com.meshcoreone.android.feature.nodes.support.Harness
import com.meshcoreone.android.feature.nodes.support.scenario
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class HopCodeParserTest {
    private val known = mapOf(Bytes.of(0xA1) to Fixtures.key(0xA1, 0x01), Bytes.of(0xB2) to Fixtures.key(0xB2, 0x02))
    private fun resolve(hash: Bytes) = known[hash]?.let { ResolvedHop(it, "N${hash.hexString}") }

    private fun classify(input: String, existing: Set<Bytes> = emptySet(), remaining: Int? = null, hashSize: Int = 1) =
        HopCodeParser.classify(input, hashSize, existing, remaining, ::resolve)

    @Test
    fun `codes are trimmed, uppercased, de-duplicated and classified in order`() {
        val result = classify(" a1 ,b2,A1,zz,c3,,abc")
        assertEquals(listOf("A1", "B2", "ZZ", "C3", "ABC"), result.map { it.code })
        assertIs<HopCodeStatus.WillAdd>(result[0].status)
        assertIs<HopCodeStatus.WillAdd>(result[1].status)
        assertEquals(HopCodeStatus.InvalidFormat, result[2].status)
        assertEquals(HopCodeStatus.NotFound, result[3].status)
        assertEquals(HopCodeStatus.InvalidFormat, result[4].status)
    }

    @Test
    fun `tabs and space separators trim but a newline makes the code invalid`() {
        assertIs<HopCodeStatus.WillAdd>(classify("\tA1\u00A0").single().status)
        assertEquals(HopCodeStatus.InvalidFormat, classify("A1\n").single().status)
    }

    @Test
    fun `fullwidth digits pass the digit check but fail byte parsing`() {
        assertEquals(HopCodeStatus.InvalidFormat, classify("\uFF21\uFF11").single().status)
    }

    @Test
    fun `existing hashes and earlier codes report already in path`() {
        val result = classify("A1,B2", existing = setOf(Bytes.of(0xA1)))
        assertEquals(HopCodeStatus.AlreadyInPath, result[0].status)
        assertIs<HopCodeStatus.WillAdd>(result[1].status)
    }

    @Test
    fun `codes past the remaining capacity are path full`() {
        val result = classify("A1,B2", remaining = 1)
        assertTrue(result[0].willBeAdded)
        assertEquals(HopCodeStatus.PathFull, result[1].status)
    }

    @Test
    fun `wider hash sizes need twice as many digits`() {
        assertEquals(HopCodeStatus.InvalidFormat, classify("A1", hashSize = 2).single().status)
        assertEquals(HopCodeStatus.NotFound, classify("A1B2", hashSize = 2).single().status)
    }

    @Test
    fun `code input errors join in invalid, not found, already order`() {
        val result = CodeInputResult(added = listOf("A1"), notFound = listOf("C3"), alreadyInPath = listOf("B2"), invalidFormat = listOf("ZZ", "Q"))
        assertTrue(result.hasErrors)
        val message = assertNotNull(result.errorMessage)
        assertIs<com.meshcoreone.android.feature.nodes.deps.NodesMessage.Joined>(message)
        assertEquals(3, message.parts.size)
        assertNull(CodeInputResult(added = listOf("A1")).errorMessage)
    }

    @Test
    fun `addCodes appends resolvable hops, records recents and stops at the cap`() = scenario {
        val harness = Harness(clock)
        val vm = PathManagementStateHolder(harness.dependencies, scope)
        vm.loadRecentKeys(Fixtures.radio())
        vm.seed { state -> state.copy(allContacts = listOf(pathContact(publicKey = Fixtures.key(0xA1)), pathContact(publicKey = Fixtures.key(0xB2)))) }
        val result = vm.addCodes("A1, B2, C3, A1")
        assertEquals(listOf("A1", "B2"), result.added)
        assertEquals(listOf("C3"), result.notFound)
        assertEquals(listOf(Bytes.of(0xA1), Bytes.of(0xB2)), vm.state.value.editablePath.map { it.hashBytes })
        assertEquals(listOf(Fixtures.key(0xB2), Fixtures.key(0xA1)), vm.state.value.recentPublicKeys)
        assertEquals(listOf("A1"), vm.addCodes("A1").alreadyInPath)
    }

    @Test
    fun `move follows Swift move(fromOffsets toOffset) semantics`() {
        val list = listOf("a", "b", "c", "d")
        assertEquals(listOf("b", "c", "a", "d"), PathEditing.move(list, setOf(0), 3))
        assertEquals(listOf("d", "a", "b", "c"), PathEditing.move(list, setOf(3), 0))
        assertEquals(listOf("b", "d", "a", "c"), PathEditing.move(list, setOf(0, 2), 4))
        assertEquals(list, PathEditing.move(list, setOf(1), 1))
    }

    @Test
    fun `max hop count is bounded by the hop field and the byte budget`() {
        assertEquals(63, PathEditing.maxHopCount(1))
        assertEquals(32, PathEditing.maxHopCount(2))
        assertEquals(21, PathEditing.maxHopCount(3))
    }
}
