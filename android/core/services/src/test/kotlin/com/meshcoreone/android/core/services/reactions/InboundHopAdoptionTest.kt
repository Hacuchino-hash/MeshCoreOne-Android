// AndroidOnly: WP-216 rule checks for adoptInboundHop and equivalence with core:data's internal RepositoryPolicies copy.
package com.meshcoreone.android.core.services.reactions

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.fail
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

/**
 * Swift has no dedicated test for `adoptInboundHop` (its store and mock call it). core:data keeps an internal copy
 * (`RepositoryPolicies.adoptInboundHop`) that this module cannot call, so [coreDataAdoptInboundHop] transcribes
 * that function verbatim (wp213-rendering @ 052993b9) and the grid below requires both to agree.
 */
class InboundHopAdoptionTest {
    /** Verbatim transcription of core:data `adoptInboundHop` (returning `InboundHop(count, timestamp)`). */
    private data class CoreDataInboundHop(val count: Long, val timestamp: UInt?)

    private fun coreDataAdoptInboundHop(
        storedHops: Long?,
        storedTimestamp: UInt?,
        incomingHops: Long,
        incomingTimestamp: UInt?,
    ): CoreDataInboundHop? = when {
        storedTimestamp == null -> CoreDataInboundHop(incomingHops, incomingTimestamp)
        incomingTimestamp == null -> null
        incomingTimestamp > storedTimestamp -> CoreDataInboundHop(incomingHops, incomingTimestamp)
        incomingTimestamp == storedTimestamp && incomingHops < (storedHops ?: Long.MAX_VALUE) ->
            CoreDataInboundHop(incomingHops, incomingTimestamp)
        else -> null
    }

    private val hops = listOf<Long?>(null, 0, 1, 2, 5, 63, Long.MAX_VALUE)
    private val stamps = listOf<UInt?>(null, 0u, 1u, 99u, 100u, 101u, Int.MAX_VALUE.toUInt(), UInt.MAX_VALUE)

    @TestFactory
    fun adoption(): List<DynamicTest> = listOf(
        DynamicTest.dynamicTest("WP-216::adoptInboundHop agrees with core:data RepositoryPolicies over the full input grid") {
            val mismatches = ArrayList<String>()
            for (storedHops in hops) for (storedStamp in stamps) for (incomingHops in hops.filterNotNull()) for (incomingStamp in stamps) {
                val ours = adoptInboundHop(storedHops, storedStamp, incomingHops, incomingStamp)?.let { CoreDataInboundHop(it.hopCount, it.advertTimestamp) }
                val theirs = coreDataAdoptInboundHop(storedHops, storedStamp, incomingHops, incomingStamp)
                if (ours != theirs) mismatches.add("($storedHops,$storedStamp,$incomingHops,$incomingStamp): $ours vs $theirs")
            }
            if (mismatches.isNotEmpty()) fail(mismatches.joinToString("\n"))
        },
        DynamicTest.dynamicTest("WP-216::adoptInboundHop adopts anything while no timestamp is stored, including ungrouped reads") {
            assertEquals(InboundHopAdoption(3, 100u), adoptInboundHop(null, null, 3, 100u))
            assertEquals(InboundHopAdoption(7, null), adoptInboundHop(2, null, 7, null))
        },
        DynamicTest.dynamicTest("WP-216::adoptInboundHop ignores an ungrouped read once a timestamp is stored") {
            assertNull(adoptInboundHop(5, 100u, 0, null))
        },
        DynamicTest.dynamicTest("WP-216::adoptInboundHop takes a newer advert whether hops rise or fall") {
            assertEquals(InboundHopAdoption(9, 101u), adoptInboundHop(2, 100u, 9, 101u))
            assertEquals(InboundHopAdoption(0, 101u), adoptInboundHop(2, 100u, 0, 101u))
        },
        DynamicTest.dynamicTest("WP-216::adoptInboundHop takes a closer copy of the same broadcast only") {
            assertEquals(InboundHopAdoption(1, 100u), adoptInboundHop(2, 100u, 1, 100u))
            assertNull(adoptInboundHop(2, 100u, 2, 100u))
            assertNull(adoptInboundHop(2, 100u, 3, 100u))
            assertEquals(InboundHopAdoption(Long.MAX_VALUE - 1, 100u), adoptInboundHop(null, 100u, Long.MAX_VALUE - 1, 100u))
            assertNull(adoptInboundHop(null, 100u, Long.MAX_VALUE, 100u))
        },
        DynamicTest.dynamicTest("WP-216::adoptInboundHop never goes back to an older advert") {
            assertNull(adoptInboundHop(9, 100u, 0, 99u))
            assertNull(adoptInboundHop(9, UInt.MAX_VALUE, 0, 0u))
        },
    )
}
