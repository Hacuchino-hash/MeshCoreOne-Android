// AndroidOnly: WP-301 Public Android contrast/animation settings with composition-owned listener disposal.
package com.meshcoreone.android.core.designsystem

import android.app.UiModeManager
import android.content.Context
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.util.concurrent.atomic.AtomicBoolean

@RequiresApi(34)
internal class PlatformContrastObservation(context: Context, onChange: (Float) -> Unit) : AutoCloseable {
    private val manager = requireNotNull(context.getSystemService(UiModeManager::class.java))
    private val disposed = AtomicBoolean(false)
    private val listener = UiModeManager.ContrastChangeListener { value ->
        if (!disposed.get()) onChange(value)
    }
    init {
        manager.addContrastChangeListener(context.mainExecutor, listener)
    }
    override fun close() {
        if (disposed.compareAndSet(false, true)) manager.removeContrastChangeListener(listener)
    }
}

@Composable
fun rememberHighContrast(): Boolean {
    val context = LocalContext.current
    return if (Build.VERSION.SDK_INT >= 34) rememberContrastApi34(context) else rememberLegacyHighContrast(context)
}

@RequiresApi(34)
@Composable
private fun rememberContrastApi34(context: Context): Boolean {
    val manager = remember(context) { requireNotNull(context.getSystemService(UiModeManager::class.java)) }
    var contrast by remember(manager) { mutableFloatStateOf(manager.contrast) }
    DisposableEffect(manager) {
        val observation = PlatformContrastObservation(context) { contrast = it }
        onDispose { observation.close() }
    }
    return contrast > 0
}

@Composable
private fun rememberLegacyHighContrast(context: Context): Boolean {
    val resolver = context.contentResolver
    val key = "high_text_contrast_enabled"
    var contrast by remember(resolver) { mutableStateOf(Settings.Secure.getInt(resolver, key, 0) != 0) }
    DisposableEffect(resolver) {
        var disposed = false
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                if (!disposed) contrast = Settings.Secure.getInt(resolver, key, 0) != 0
            }
        }
        resolver.registerContentObserver(Settings.Secure.getUriFor(key), false, observer)
        onDispose {
            disposed = true
            resolver.unregisterContentObserver(observer)
        }
    }
    return contrast
}

@Composable
fun rememberMotionDurationScale(): Float {
    val resolver = LocalContext.current.contentResolver
    fun read(): Float = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f).also {
        require(it.isFinite() && it >= 0) { "Invalid platform animation duration scale" }
    }
    var scale by remember(resolver) { mutableFloatStateOf(read()) }
    DisposableEffect(resolver) {
        var disposed = false
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                if (!disposed) scale = read()
            }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose {
            disposed = true
            resolver.unregisterContentObserver(observer)
        }
    }
    return scale
}
