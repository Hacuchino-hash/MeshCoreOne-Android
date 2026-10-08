// AndroidOnly: WP-302 Real native SDK31/37 Compose shell evidence with state-rich, explicitly synthetic feature content.
package com.meshcoreone.android.app.navigation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.meshcoreone.android.core.contracts.AppTab
import com.meshcoreone.android.core.designsystem.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.TestName
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31, 37], qualifiers = "w1080dp-h900dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationComposeTest {
    @get:Rule val compose = createEmptyComposeRule()
    @get:Rule val executionName = TestName()
    private lateinit var host: ActivityController<ComponentActivity>
    private val width = mutableStateOf(360.dp)
    private val font = mutableFloatStateOf(1f)
    private val direction = mutableStateOf(LayoutDirection.Ltr)
    private val theme = mutableStateOf(ThemeRegistry.default)
    private val preference = mutableStateOf(AppColorSchemePreference.LIGHT)
    private val highContrast = mutableStateOf(false)
    private val navigation = NavigationCoordinator()

    @Before fun attachNativeHost() {
        emitNavigationExecutionBinding(javaClass.name, executionName.methodName, Build.VERSION.SDK_INT.toString())
        host = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        host.get().actionBar?.hide()
        host.get().setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(1f, font.floatValue),
                LocalLayoutDirection provides direction.value,
            ) {
                Box(Modifier.width(width.value).height(900.dp)) {
                    MeshCoreTheme(theme.value, preference.value, systemDark = false, highContrast = highContrast.value, motionScale = 0f) {
                        NativeNavigationShell(navigation, unreadCount = 123) { destination, _ -> FixtureContent(destination) }
                    }
                }
            }
        }
    }
    @After fun disposeNativeHost() { host.pause().stop().destroy() }

    @Composable private fun FixtureContent(destination: NavigationDestination) {
        val id = when (destination) {
            is NavigationDestination.Root -> "root-${destination.tab.name}"
            is NavigationDestination.Tool -> "tool-${destination.selection.sourceName}"
            is NavigationDestination.Setting -> "setting-${destination.selection.sourceName}"
            else -> "private-detail"
        }
        var draft by rememberSaveable { mutableStateOf("") }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("fixture:$id")) {
            Text("Synthetic shell content: $id")
            Text("\u4e2d\u6587\u6d88\u606f \u05e9\u05dc\u05d5\u05dd \u3053\u3093\u306b\u3061\u306f ".repeat(3),
                Modifier.testTag("body:$id"))
            OutlinedTextField(draft, { draft = it }, Modifier.fillMaxWidth().testTag("editor:$id"), label = { Text("Fixture draft") })
        }
    }

    @Test fun actualAvailableWidthDrivesBarRailAndMeasuredPaneThresholds() {
        for (value in listOf(360, 599, 600, 744, 779, 780, 834, 1080)) {
            compose.runOnIdle { width.value = value.dp }
            compose.onNodeWithTag(if (value < 600) "bottom-navigation" else "navigation-rail").assertIsDisplayed()
            compose.onNodeWithTag(if (value < 780) "single-pane" else "list-detail").assertIsDisplayed()
            compose.onNodeWithTag("navigation-list-pane").assertIsDisplayed()
            if (value >= 780) {
                compose.onNodeWithTag("navigation-list-pane").assertWidthIsAtLeast(380.dp)
                compose.onNodeWithTag("navigation-detail-pane").assertWidthIsAtLeast(320.dp)
                compose.onNodeWithTag("detail-empty").assertIsDisplayed()
            } else compose.onNodeWithTag("detail-empty").assertDoesNotExist()
            capture("width-$value")
        }
    }
    @Test fun fiveNativeTabsExposeSelectionHeadingUnreadAndFortyEightDpTargets() {
        for (tab in AppTab.entries) {
            compose.onNodeWithTag("tab:${tab.name}").assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp).performClick()
            compose.onNodeWithTag("tab:${tab.name}").assertIsSelected()
            compose.onNodeWithTag("navigation-heading").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
            compose.onNodeWithTag("fixture:root-${tab.name}").assertIsDisplayed()
        }
        compose.onNodeWithTag("tab:CHATS").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.StateDescription))
        compose.onNodeWithTag("open-radio-setup").assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        capture("tabs-selected-settings")
    }
    @Test fun inactiveTabDraftAndDetailFocusSurviveWindowResizeWithoutAnotherConnectionOwner() {
        compose.onNodeWithTag("editor:root-CHATS").performTextInput("retained draft")
        compose.onNodeWithTag("tab:NODES").performClick()
        compose.onNodeWithTag("tab:CHATS").performClick()
        compose.onNodeWithTag("editor:root-CHATS").assertTextContains("retained draft")
        compose.runOnIdle { navigation.navigateToSetting(SettingsDetail.LANGUAGE) }
        compose.onNodeWithTag("editor:setting-language").performClick().performTextInput("detail draft")
        compose.onNodeWithTag("editor:setting-language").assertIsFocused()
        val entry = navigation.state.value.activeStack.last()
        compose.runOnIdle { width.value = 834.dp }
        compose.onNodeWithTag("editor:setting-language").assertTextContains("detail draft").assertIsFocused()
        assertEquals(entry, navigation.state.value.activeStack.last())
        capture("resize-expanded-draft")
        compose.runOnIdle { width.value = 360.dp }
        compose.onNodeWithTag("editor:setting-language").assertTextContains("detail draft").assertIsFocused()
        assertEquals(entry, navigation.state.value.activeStack.last())
        capture("resize-compact-draft")
    }
    @Test fun collapsingToolsShowOnlyDetailAndNativeBackRestoresSectionNavigation() {
        for (value in listOf(744, 834)) {
            compose.runOnIdle { width.value = value.dp; navigation.navigateToTool(ToolSelection.CLI) }
            compose.onNodeWithTag("navigation-rail").assertIsDisplayed()
            compose.onNodeWithTag("fixture:tool-cli").assertIsDisplayed()
            if (value >= NavigationLayout.TILE_MIN_WIDTH_DP) {
                compose.onNodeWithTag("navigation-list-pane").assertIsDisplayed()
            } else {
                compose.onNodeWithTag("single-pane").assertIsDisplayed()
                compose.onNodeWithTag("navigation-list-pane").assertDoesNotExist()
            }
            for (tool in listOf(ToolSelection.TRACE_PATH, ToolSelection.LINE_OF_SIGHT)) {
                compose.runOnIdle { navigation.navigateToTool(tool) }
                compose.onNodeWithTag("navigation-hidden").assertIsDisplayed()
                compose.onNodeWithTag("single-pane").assertIsDisplayed()
                compose.onNodeWithTag("navigation-list-pane").assertDoesNotExist()
                compose.onNodeWithTag("fixture:tool-${tool.sourceName}").assertIsDisplayed()
                if (value == 834) capture("collapse-${tool.sourceName.lowercase()}")
            }
        }
        compose.onNodeWithTag("navigation-back").assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(ToolSelection.TRACE_PATH, navigation.state.value.selectedTool)
    }
    @Test fun actualActivityBackDispatcherPopsDetailThenReturnsToChats() {
        for (value in listOf(360, 834)) {
            compose.runOnIdle { width.value = value.dp; navigation.navigateToSetting(SettingsDetail.LANGUAGE) }
            compose.onNodeWithTag("fixture:setting-language").assertIsDisplayed()
            compose.onNodeWithTag("editor:setting-language").performClick().performTextInput("cancelled Back draft")
            val before = navigation.state.value
            compose.runOnUiThread {
                host.get().onBackPressedDispatcher.dispatchOnBackStarted(BackEventCompat(0f, 100f, 0f, BackEventCompat.EDGE_LEFT))
                host.get().onBackPressedDispatcher.dispatchOnBackProgressed(BackEventCompat(80f, 100f, 0.5f, BackEventCompat.EDGE_LEFT))
            }
            compose.waitForIdle()
            assertEquals("Progress must not commit the stack", before, navigation.state.value)
            compose.runOnUiThread { host.get().onBackPressedDispatcher.dispatchOnBackCancelled() }
            compose.onNodeWithTag("editor:setting-language").assertTextContains("cancelled Back draft").assertIsFocused()
            assertEquals("Cancellation must preserve selection and entry identities", before, navigation.state.value)
            compose.runOnUiThread {
                host.get().onBackPressedDispatcher.dispatchOnBackStarted(BackEventCompat(0f, 100f, 0f, BackEventCompat.EDGE_LEFT))
                host.get().onBackPressedDispatcher.dispatchOnBackProgressed(BackEventCompat(120f, 100f, 0.8f, BackEventCompat.EDGE_LEFT))
            }
            compose.runOnUiThread { host.get().onBackPressedDispatcher.onBackPressed() }
            compose.onNodeWithTag("fixture:root-SETTINGS").assertIsDisplayed()
            assertNull(navigation.state.value.selectedSetting)
            compose.runOnUiThread { host.get().onBackPressedDispatcher.onBackPressed() }
            compose.onNodeWithTag("fixture:root-CHATS").assertIsDisplayed()
            assertEquals(AppTab.CHATS, navigation.state.value.selectedTab)
        }
        capture("native-back-root")
    }
    @Test fun twoHundredPercentFontRtlCjkAndShortRailKeepActionsAndTextAccessible() {
        compose.runOnIdle { font.floatValue = 2f; direction.value = LayoutDirection.Rtl }
        for (tab in AppTab.entries) {
            compose.onNodeWithTag("tab:${tab.name}").assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
            assertTextDoesNotOverflow("tab-label:${tab.name}")
        }
        assertTextDoesNotOverflow("navigation-heading")
        assertTextDoesNotOverflow("body:root-CHATS")
        capture("font200-rtl-cjk")
        compose.runOnIdle { width.value = 834.dp }
        for (tab in AppTab.entries) compose.onNodeWithTag("tab:${tab.name}").performScrollTo().performClick().assertIsSelected()
        capture("font200-rtl-rail")
    }
    @Test fun everyUnlockedThemeAndEffectiveSchemeRendersTheActualShellWithoutResettingTab() {
        compose.runOnIdle { width.value = 834.dp; navigation.selectTab(AppTab.SETTINGS) }
        val before = navigation.state.value.activeStack
        for (value in ThemeRegistry.allThemes) {
            val schemes = ColorScheme.entries.filter { value.preferredColorScheme == null || value.preferredColorScheme == it }
            for (scheme in schemes) for (contrast in listOf(false, true)) {
                compose.runOnIdle {
                    theme.value = value; highContrast.value = contrast
                    preference.value = if (scheme == ColorScheme.DARK) AppColorSchemePreference.DARK else AppColorSchemePreference.LIGHT
                }
                compose.onNodeWithTag("tab:SETTINGS").assertIsSelected()
                assertEquals(before, navigation.state.value.activeStack)
                capture("theme-${value.id.rawValue}-${scheme.name.lowercase()}-${if (contrast) "hc" else "standard"}")
            }
        }
    }
    private fun assertTextDoesNotOverflow(tag: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(tag, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { assertTrue(it(layouts)) }
        assertFalse("$tag must honor font scaling without clipping", layouts.single().hasVisualOverflow)
    }
    private fun capture(id: String) {
        compose.waitForIdle()
        val bitmap = compose.runOnUiThread {
            val view = host.get().window.decorView
            Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        }
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        assertTrue("Blank pixels are not native evidence", pixels.toSet().size > 16)
        val bytes = ByteArrayOutputStream().use {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)); it.toByteArray()
        }
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
        val name = "sdk-${Build.VERSION.SDK_INT}-$id"
        val directory = File(requireNotNull(System.getProperty("navigationArtifactDirectory")) {
            "The native App runner must declare navigationArtifactDirectory"
        })
        assertTrue(directory.isDirectory || directory.mkdirs()); File(directory, "$name.png").writeBytes(bytes)
        println("WP302_RENDER|$name|${bitmap.width}|${bitmap.height}|$hash|${Base64.getEncoder().encodeToString(bytes)}")
    }
}
