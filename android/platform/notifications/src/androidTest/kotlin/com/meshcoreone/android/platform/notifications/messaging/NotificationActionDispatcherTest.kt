// AndroidOnly: WP-401 Deterministic cold-start and stale-radio action dispatch evidence.
package com.meshcoreone.android.platform.notifications.messaging

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.meshcoreone.android.core.contracts.domain.EntityKey
import com.meshcoreone.android.core.contracts.notifications.NotificationAction
import com.meshcoreone.android.core.contracts.notifications.NotificationPayload
import com.meshcoreone.android.core.contracts.notifications.NotificationResponse
import com.meshcoreone.android.core.model.RadioId
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationActionDispatcherTest {
    @After
    fun reset() {
        NotificationActionDispatcher.resetForTesting()
    }

    @Test
    fun coldStartKeepsActionUntilTheCurrentGraphIsReady() = runTest {
        var ready = false
        val delivered = mutableListOf<NotificationResponse>()
        NotificationActionDispatcher.install(this) { response ->
            if (!ready) false else true.also { delivered += response }
        }
        val response = response("10000000-0000-0000-0000-000000000001")

        assertEquals(EnqueueResult.DELIVERED_OR_QUEUED, NotificationActionDispatcher.enqueue(response))
        assertFalse(NotificationActionDispatcher.drainOnce())
        assertTrue(delivered.isEmpty())

        ready = true
        assertTrue(NotificationActionDispatcher.drainOnce())
        assertEquals(listOf(response), delivered)
    }

    @Test
    fun staleRadioIdentityRemainsAttachedForTheSessionHandlerGuard() = runTest {
        val response = response("90000000-0000-0000-0000-000000000009")
        var routedRadio: RadioId? = null
        NotificationActionDispatcher.install(this) {
            routedRadio = NotificationPayloadCodec.radioId(it.payload)
            true
        }

        NotificationActionDispatcher.enqueue(response)
        assertTrue(NotificationActionDispatcher.drainOnce())
        assertEquals(RadioId(UUID.fromString("90000000-0000-0000-0000-000000000009")), routedRadio)
    }

    private fun response(radio: String): NotificationResponse {
        val radioId = RadioId(UUID.fromString(radio))
        return NotificationResponse(
            NotificationPayload.DirectMessage(
                EntityKey(radioId, UUID.fromString("20000000-0000-0000-0000-000000000002")),
                UUID.fromString("30000000-0000-0000-0000-000000000003"),
            ),
            NotificationAction.REPLY,
            "reply",
        )
    }
}
