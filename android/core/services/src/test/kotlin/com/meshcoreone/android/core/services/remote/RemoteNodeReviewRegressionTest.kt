// AndroidOnly: WP-210 Regression cases for review findings: Kotlin collaborators that can throw where Swift's cannot.
package com.meshcoreone.android.core.services.remote

import com.meshcoreone.android.core.model.RoomPermissionLevel
import com.meshcoreone.android.core.protocol.bytes.Bytes
import com.meshcoreone.android.core.protocol.event.LoginInfo
import com.meshcoreone.android.core.protocol.event.MeshEvent
import kotlinx.coroutines.async
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RemoteNodeReviewRegressionTest {
    private val publicKey = remoteCoreKey(0xC1)

    /** Throws from every hook; Swift's audit logger is non-throwing, so none of this may abort a flow. */
    private class ThrowingAuditLog : RemoteCommandAuditLog {
        override suspend fun logLoginRequest(target: RemoteAuditTarget, publicKey: Bytes, pathLength: UByte) = fail()
        override suspend fun logLoginSuccess(target: RemoteAuditTarget, publicKey: Bytes, isAdmin: Boolean) = fail()
        override suspend fun logLoginFailed(target: RemoteAuditTarget, publicKey: Bytes, reason: String) = fail()
        override suspend fun logCLICommand(publicKey: Bytes, command: String) = fail()
        private fun fail(): Nothing = throw IllegalStateException("audit sink unavailable")
    }

    @TestFactory
    fun cases(): List<DynamicTest> = listOf(
        remoteCoreNative("a throwing audit logger and timeout callback never abort a login or end event monitoring") {
            withRemoteCoreHarness {
                val service = RemoteNodeService(session, store, passwords, scope, clock, ThrowingAuditLog())
                try {
                    val key = addSession(remoteCoreSession(radioId, publicKey))
                    service.startEventMonitoring()
                    remoteCoreAwait("event monitor never subscribed") { session.eventSubscriptionCount == 1 }
                    session.setSendLoginResults(List(2) { Result.success(RemoteCoreFakeSession.sentInfo(1000u)) })

                    // loginFailed runs logLoginFailed inside the monitor; before the fix it ended the monitor.
                    val failed = async { runCatching { service.login(key, "bad") { throw IllegalStateException("ui gone") } } }
                    remoteCoreAwait("first login never sent") { session.sendLoginInvocations.size == 1 }
                    session.yieldEvent(MeshEvent.LoginFailed(publicKey.prefix(6)))
                    assertIs<RemoteNodeError.LoginFailed>(failed.await().exceptionOrNull())
                    assertEquals(1, session.eventSubscriptionCount, "monitoring must survive a throwing audit hook")

                    val login = async { service.login(key, "pw") }
                    remoteCoreAwait("second login never sent") { session.sendLoginInvocations.size == 2 }
                    session.yieldEvent(MeshEvent.LoginSuccess(LoginInfo(2u, true, publicKey.prefix(6))))
                    val result = login.await()
                    assertTrue(result.success)
                    assertEquals(RoomPermissionLevel.ADMIN, result.permissionLevel)
                } finally {
                    service.close()
                }
            }
        },
        remoteCoreNative("portable Unicode digit and whitespace classes equal the JVM's UNICODE_CHARACTER_CLASS \\d and \\s") {
            // The explicit spellings must match exactly what the flagged shorthand matched, for every code point.
            val flaggedDigit = java.util.regex.Pattern.compile("\\d", java.util.regex.Pattern.UNICODE_CHARACTER_CLASS)
            val flaggedSpace = java.util.regex.Pattern.compile("\\s", java.util.regex.Pattern.UNICODE_CHARACTER_CLASS)
            val digit = java.util.regex.Pattern.compile(RemoteSwiftText.UNICODE_DIGIT)
            val space = java.util.regex.Pattern.compile(RemoteSwiftText.UNICODE_WHITESPACE)
            var digits = 0
            for (codePoint in 0..Character.MAX_CODE_POINT) {
                if (codePoint in Character.MIN_SURROGATE.code..Character.MAX_SURROGATE.code) continue
                val text = String(Character.toChars(codePoint))
                val isDigit = flaggedDigit.matcher(text).matches()
                if (isDigit) digits++
                assertEquals(isDigit, digit.matcher(text).matches(), "digit U+%04X".format(codePoint))
                assertEquals(flaggedSpace.matcher(text).matches(), space.matcher(text).matches(), "space U+%04X".format(codePoint))
            }
            assertTrue(digits > 600, "Unicode decimal digits beyond ASCII must be covered")
        },
        remoteCoreNative("the non-throwing audit wrapper forwards every audit hook") {
            // A missing override would silently fall back to the interface's no-op default.
            val hooks = RemoteCommandAuditLog::class.java.declaredMethods.map { it.name }.filterNot { "$" in it }.toSet()
            val forwarded = NonThrowingAuditLog::class.java.declaredMethods.map { it.name }.filterNot { "$" in it }.toSet()
            assertTrue(hooks.isNotEmpty())
            assertEquals(emptySet(), hooks - forwarded)
        },
    )
}
