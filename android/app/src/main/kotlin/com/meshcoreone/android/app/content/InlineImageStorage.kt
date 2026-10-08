// PortedFrom: MC1Services/Sources/MC1Services/Services/InlineImageDimensionsStore.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.app.content

import android.content.Context
import com.meshcoreone.android.core.services.content.InlineImageDimensionsStore
import java.io.File
import java.time.Clock

fun inlineImageDimensionsStore(context: Context, clock: Clock = Clock.systemUTC()): InlineImageDimensionsStore =
    InlineImageDimensionsStore(File(context.filesDir, "InlineImageDimensions.json"), clock)
