// AndroidOnly: WP-303 Onboarding flag persistence and the first-run gate flow.
package com.meshcoreone.android.app.onboarding

import com.meshcoreone.android.feature.onboarding.OnboardingState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.CoroutineScope

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingFlagsTest {
    @Test fun `completion persists in order and publishes the gate immediately`() = runTest {
        val writes = mutableListOf<Pair<String, Boolean>>()
        val flags = WriteBehindOnboardingFlags(emptyMap(), CoroutineScope(UnconfinedTestDispatcher(testScheduler)), { k, v -> writes += k to v })
        val state = OnboardingState(flags)
        assertFalse(state.hasCompletedOnboarding)
        assertFalse(flags.hasCompleted.value)
        state.completeOnboarding()
        assertTrue(flags.hasCompleted.value)
        assertTrue(flags.getBoolean(OnboardingState.KEY_HAS_COMPLETED))
        state.resetOnboarding()
        assertFalse(flags.hasCompleted.value)
        assertEquals(listOf(OnboardingState.KEY_HAS_COMPLETED to true, OnboardingState.KEY_HAS_COMPLETED to false), writes)
    }

    @Test fun `a persisted completed flag is already true and a failing write is reported not thrown`() = runTest {
        val failures = mutableListOf<Throwable>()
        val flags = WriteBehindOnboardingFlags(
            mapOf(OnboardingState.KEY_HAS_COMPLETED to true), CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
            { _, _ -> error("disk full") }, failures::add,
        )
        assertTrue(flags.hasCompleted.value)
        flags.setBoolean("other", true)
        assertTrue(flags.getBoolean("other"))
        assertEquals(listOf("disk full"), failures.map { it.message })
    }
}
