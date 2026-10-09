// PortedFrom: MC1Tests/Views/Chats/ConversationListScrollPerfHarnessTests.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.feature.chats.list

import com.meshcoreone.android.feature.chats.list.support.OriginalCase
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class ConversationListScrollPerfHarnessTest {
    private val uiSources = File("src/main/kotlin/com/meshcoreone/android/feature/chats/list/ui")

    /** List rows must not attach per-cell warm tasks; LazyColumn recycles items during scroll. */
    @Test @OriginalCase("ConversationListScrollPerfHarnessTests::conversation list source has no per-row dwell prewarm task()", "platform-adaptation")
    fun `conversation list source has no per-row dwell prewarm task`() {
        assertTrue(uiSources.isDirectory, "expected ${uiSources.absolutePath}")
        val rowSources = listOf("ChatsListScreen.kt", "ConversationListRow.kt", "ConversationRowContent.kt")
            .joinToString("\n") { File(uiSources, it).readText() }
        assertFalse(rowSources.contains("prewarm", ignoreCase = true))
        assertFalse(Regex("LaunchedEffect\\(\\s*conversation").containsMatchIn(rowSources))
        assertFalse(rowSources.contains("DisposableEffect"))
    }

    // `hosted list programmatic scroll stays under hitch budget` is deferred: it measures a hosted UIKit
    // scroll view; the Android equivalent needs a device/macrobenchmark (see docs/android/deviations/WP-306.md).
}
