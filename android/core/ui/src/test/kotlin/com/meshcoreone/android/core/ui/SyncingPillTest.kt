// PortedFrom: MC1Tests/Views/SyncingPillViewTests.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.core.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.meshcoreone.android.core.designsystem.MeshSymbol
import com.meshcoreone.android.core.l10n.generated.AppLocalizableStrings as L
import kotlin.test.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], qualifiers = "en-rUS")
class SyncingPillTest : SourceCaseProof() {
    private val resources get() = ApplicationProvider.getApplicationContext<Context>().resources
    @OriginalCase("SyncingPillViewTests::Connecting state shows correct text and icon()")
    @Test fun connecting() = prove {
        assertEquals(resources.getString(L.commonStatusConnecting), StatusPillState.Connecting.text.resolve(resources))
        assertEquals(MeshSymbol.SYNC, StatusPillState.Connecting.symbol); assertFalse(StatusPillState.Connecting.isFailure)
    }
    @OriginalCase("SyncingPillViewTests::Syncing state shows correct text and icon()")
    @Test fun syncing() = prove {
        assertEquals(resources.getString(L.commonStatusSyncing), StatusPillState.Syncing.text.resolve(resources))
        assertEquals(MeshSymbol.SYNC, StatusPillState.Syncing.symbol); assertFalse(StatusPillState.Syncing.isFailure)
    }
    @OriginalCase("SyncingPillViewTests::Ready state shows correct text and icon()")
    @Test fun ready() = prove {
        assertEquals(resources.getString(L.commonStatusReady), StatusPillState.Ready.text.resolve(resources))
        assertEquals(MeshSymbol.READY, StatusPillState.Ready.symbol); assertFalse(StatusPillState.Ready.isFailure)
    }
    @OriginalCase("SyncingPillViewTests::Disconnected state shows orange warning icon and text()")
    @NativeAdaptation("native-warning-foreground-and-readable-surface")
    @Test fun disconnected() = prove {
        assertEquals(resources.getString(L.commonStatusDisconnected), StatusPillState.Disconnected.text.resolve(resources))
        assertEquals(MeshSymbol.WARNING, StatusPillState.Disconnected.symbol); assertFalse(StatusPillState.Disconnected.isFailure)
    }
    @OriginalCase("SyncingPillViewTests::Disconnected with tap handler stores closure()")
    @Test fun retainsTap() = prove {
        var tapped = false
        val actions = SyncingPillActions { tapped = true }
        assertFalse(tapped); assertNotNull(actions.onDisconnectedTap).invoke(); assertTrue(tapped)
    }
    @OriginalCase("SyncingPillViewTests::Failed state shows red text and failure icon with custom message()")
    @NativeAdaptation("material-error-container-with-readable-foreground")
    @Test fun failed() = prove {
        val state = StatusPillState.Failed(UiText.Verbatim("Sync Failed"))
        assertEquals("Sync Failed", state.text.resolve(resources)); assertTrue(state.isFailure)
        assertEquals(MeshSymbol.ERROR, state.symbol)
    }
    @OriginalCase("SyncingPillViewTests::Failed state preserves custom error message()")
    @Test fun preservesFailure() = prove {
        assertEquals("Custom Error", StatusPillState.Failed(UiText.Verbatim("Custom Error")).text.resolve(resources))
    }
    @OriginalCase("SyncingPillViewTests::Hidden state shows empty text and no icon()")
    @Test fun hidden() = prove {
        assertEquals("", StatusPillState.Hidden.text.resolve(resources)); assertNull(StatusPillState.Hidden.symbol)
        assertFalse(StatusPillState.Hidden.isFailure)
    }
}
