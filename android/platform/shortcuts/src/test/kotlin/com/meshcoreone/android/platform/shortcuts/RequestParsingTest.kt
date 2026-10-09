// AndroidOnly: WP-404 Hostile/invalid/ambiguous/cold-start/stale-route/denied boundary tests.
package com.meshcoreone.android.platform.shortcuts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RequestParsingTest {
    private val id = TargetIdentity.contactId(contact())
    private fun raw(action: String?, mime: String? = null, text: CharSequence? = null, target: String? = null, reach: String? = null, shortcut: String? = null) =
        RawShortcutIntent(action, mime, text, target, reach, shortcut)

    @Test
    fun launcherActionsParse() {
        assertEquals(ShortcutRequest.Status, ShortcutRequestParser.parse(raw(ShortcutContract.ACTION_STATUS)))
        assertEquals(ShortcutRequest.Advert(AdvertReach.FLOOD), ShortcutRequestParser.parse(raw(ShortcutContract.ACTION_ADVERT, reach = "flood")))
        assertEquals(ShortcutRequest.Send(id, null), ShortcutRequestParser.parse(raw(ShortcutContract.ACTION_SEND_TARGET, target = id)))
    }

    @Test
    fun shareParsesTextAndDirectShareTarget() {
        assertEquals(ShortcutRequest.Send(null, "hi"), ShortcutRequestParser.parse(raw(ShortcutContract.ACTION_SEND, "text/plain", "hi")))
        assertEquals(ShortcutRequest.Send(id, "hi"), ShortcutRequestParser.parse(raw(ShortcutContract.ACTION_SEND, "TEXT/PLAIN", "hi", shortcut = "send:$id")))
    }

    @Test
    fun invalidAmbiguousAndOversizedInputIsDropped() {
        val dropped = listOf(
            raw(null), raw("android.intent.action.VIEW", "text/plain", "x"), raw("other.action"),
            raw(ShortcutContract.ACTION_ADVERT, reach = "everywhere"), raw(ShortcutContract.ACTION_ADVERT),
            raw(ShortcutContract.ACTION_SEND_TARGET), raw(ShortcutContract.ACTION_SEND_TARGET, target = "garbage"),
            raw(ShortcutContract.ACTION_SEND_TARGET, target = "a".repeat(ShortcutContract.MAX_ID_CHARS + 1)),
            raw(ShortcutContract.ACTION_SEND, "image/png", "x"), raw(ShortcutContract.ACTION_SEND, null, "x"),
            raw(ShortcutContract.ACTION_SEND, "text/plain", null), raw(ShortcutContract.ACTION_SEND, "text/plain", "   "),
            raw(ShortcutContract.ACTION_SEND, "text/plain", "x".repeat(ShortcutContract.MAX_SHARED_TEXT_CHARS + 1)),
            raw(ShortcutContract.ACTION_SEND, "text/plain", "x", shortcut = "other:$id"),
            raw(ShortcutContract.ACTION_SEND, "text/plain", "x", shortcut = "send:garbage"),
        )
        dropped.forEach { assertNull(ShortcutRequestParser.parse(it), it.toString()) }
    }

    @Test
    fun coldStartHoldsOnceAndExpires() {
        var now = 0L
        val dispatcher = ShortcutDispatcher({ now }, 1000)
        val seen = mutableListOf<ShortcutRequest>()
        assertEquals(ShortcutDispatcher.Outcome.Held, dispatcher.submit(ShortcutRequest.Status))
        assertTrue(dispatcher.hasPending())
        dispatcher.install { seen += it }
        dispatcher.install { seen += it }
        assertEquals(listOf<ShortcutRequest>(ShortcutRequest.Status), seen)
        assertEquals(ShortcutDispatcher.Outcome.Delivered, dispatcher.submit(ShortcutRequest.Advert(AdvertReach.ZERO_HOP)))
        dispatcher.uninstall()
        dispatcher.submit(ShortcutRequest.Status)
        now = 5000
        dispatcher.install { seen += it }
        assertEquals(2, seen.size)
        assertTrue(!dispatcher.hasPending())
    }

    @Test
    fun planKeepsStableIdsRespectsCapAndRadioScope() {
        val text = FakeText()
        val targets = (1..30).map { TargetIdentity.target(contact(key = it, name = "C$it")) }
        val plan = ShortcutPlan.build(targets, text, 10)
        assertEquals(10, plan.size)
        assertEquals(listOf("status", "advert.zerohop", "advert.flood"), plan.take(3).map { it.id })
        assertTrue(plan.drop(3).all { it.sharesToTarget && it.id == "send:" + it.targetId })
        assertEquals(plan, ShortcutPlan.build(targets, text, 10))
        assertEquals(2, ShortcutPlan.build(targets, text, 2).size)
        assertTrue(ShortcutPlan.build(targets, text, 0).isEmpty())
    }
}
