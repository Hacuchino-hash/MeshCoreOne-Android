// AndroidOnly: WP-313 Shared repeater-region fixtures (Swift RepeaterSettingsRegionTests.makeViewModel).
package com.meshcoreone.android.feature.remotenodes.settings

import com.meshcoreone.android.feature.remotenodes.support.VirtualClock
import com.meshcoreone.android.feature.remotenodes.support.session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

/** Swift `makeViewModel`: helper configured with the recorder for both send paths, firmware set. */
internal fun regionHolder(recorder: CommandRecorder, firmwareVersion: String? = "v1.15.0"): RepeaterSettingsStateHolder =
    RepeaterSettingsStateHolder(VirtualClock(), TestFaults, CoroutineScope(Job())).apply {
        helper.configure(session(name = "Test Repeater"), recorder::send, recorder::send)
        helper.setNodeInfo(firmwareVersion, "Test Repeater", null)
    }

internal fun region(name: String, parent: String?, depth: Int, floodAllowed: Boolean = true) =
    RepeaterRegionEntry(name, parent, depth, floodAllowed, isHome = false)

internal val RepeaterSettingsStateHolder.regionNames: List<String> get() = regions.state.value.regions.map { it.name }
internal val RepeaterSettingsStateHolder.errorMessage get() = helper.state.value.errorMessage
