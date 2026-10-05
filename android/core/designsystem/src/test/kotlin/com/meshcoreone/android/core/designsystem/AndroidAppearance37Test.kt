// AndroidOnly: WP-301 Actual simulated SDK37 public contrast-listener behavior, not hardware/device evidence.
package com.meshcoreone.android.core.designsystem

import android.app.UiModeManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37])
class AndroidAppearance37Test {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>
    @Before fun createHost() { controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible() }
    @After fun closeHost() { controller.pause().stop().destroy() }

    @Test fun publicNativeContrastChangesAreObservedAndLateDisposedCallbacksAreIgnored() {
        val manager = controller.get().getSystemService(UiModeManager::class.java)
        val platform = Shadows.shadowOf(manager)
        platform.setContrast(0f)
        var high = false
        controller.get().setContent {
            val contrast = rememberHighContrast()
            SideEffect { high = contrast }
            Text("")
        }
        compose.runOnIdle { assertFalse(high) }
        compose.runOnIdle { platform.setContrast(1f) }
        compose.runOnIdle { assertTrue(high) }
        controller.get().setContent { Text("Nord") }
        compose.runOnIdle { platform.setContrast(0f) }
        compose.runOnIdle { assertTrue(high) }
    }
}
