// PortedFrom: MeshCore/Tests/MeshCoreTestSupport/CallTracker.swift@db14559b39d32322b06477c6ae676112f583db50
// PortedFrom: MC1Tests/Helpers/TestHelpers.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.core.testing

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Test

class TestSupportTest {
    @Test
    fun trackerStartsEmptyAndRecordsEveryInvocation() {
        val tracker = CallTracker()
        assertFalse(tracker.wasCalled)
        assertEquals(0L, tracker.callCount)
        tracker.markCalled()
        tracker.markCalled()
        assertTrue(tracker.wasCalled)
        assertEquals(2L, tracker.callCount)
    }

    @Test
    fun trackerDoesNotLoseCallsFromConcurrentCallbacks() = runTest {
        val tracker = CallTracker()
        coroutineScope {
            repeat(8) {
                launch(Dispatchers.Default) { repeat(1000) { tracker.markCalled() } }
            }
        }
        assertEquals(8000L, tracker.callCount)
    }

    @Test
    fun mutableBoxCapturesNullableValuesWithoutAnUnsafeCast() = runTest {
        val box = MutableBox<String?>(null)
        launch { box.value = "observed" }.join()
        assertEquals("observed", box.value)
        box.value = null
        assertEquals(null, box.value)
    }
}
