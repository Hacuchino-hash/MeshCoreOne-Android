// AndroidOnly: WP-301 Actual simulated SDK37 public contrast-listener behavior, not hardware/device evidence.
package com.meshcoreone.android.core.designsystem

import android.app.UiModeManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowLooper
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37])
class AndroidAppearance37Test {
    @Test fun publicNativeContrastChangesAreObservedAndLateDisposedCallbacksAreIgnored() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = context.getSystemService(UiModeManager::class.java)
        val platform = Shadows.shadowOf(manager)
        platform.setContrast(0f)
        var high = false
        val observation = PlatformContrastObservation(context) { high = it > 0 }
        try {
            assertFalse(high)
            platform.setContrast(1f)
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
            assertTrue(high)
            observation.close()
            platform.setContrast(0f)
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
            assertTrue(high)
        } finally {
            observation.close()
        }
    }
}
