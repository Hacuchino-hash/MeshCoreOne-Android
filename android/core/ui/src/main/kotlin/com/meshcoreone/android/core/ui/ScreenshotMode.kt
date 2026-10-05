// PortedFrom: MC1/Extensions/ProcessInfo+ScreenshotMode.swift@db14559b39d32322b06477c6ae676112f583db50
// Native adaptation: explicit debug-only capture input; no release behavior or global launch argument reader.
package com.meshcoreone.android.core.ui

import com.meshcoreone.android.core.model.SnapshotSet
import com.meshcoreone.android.core.model.snapshotSet

class ScreenshotMode(val debugBuild: Boolean, arguments: Set<String>) {
    val arguments: SnapshotSet<String> = arguments.snapshotSet()
    val enabled: Boolean get() = debugBuild && "-screenshotMode" in arguments
}
