// AndroidOnly: WP-304 Immutable localized display values, not wire text or domain failures.
package com.meshcoreone.android.core.ui

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.meshcoreone.android.core.model.SnapshotList
import com.meshcoreone.android.core.model.snapshot
import java.util.Locale

@Immutable
sealed interface UiText {
    data class Resource(val resourceId: Int) : UiText
    data class Verbatim(val value: String) : UiText
    data class Format(val resourceId: Int, val arguments: SnapshotList<UiFormatArgument>) : UiText
    class Generated internal constructor(
        val sourceKey: String,
        private val formatter: (Resources) -> String,
    ) : UiText {
        internal fun render(resources: Resources): String = formatter(resources)
    }

    fun resolve(resources: Resources): String = when (this) {
        is Resource -> resources.getString(resourceId)
        is Verbatim -> value
        is Format -> String.format(
            resources.configuration.locales[0], resources.getString(resourceId),
            *arguments.map { it.value }.toTypedArray(),
        )
        is Generated -> render(resources)
    }
}

@Immutable
sealed interface UiFormatArgument {
    val value: Any
    data class Text(override val value: String) : UiFormatArgument
    data class Integer(override val value: Long) : UiFormatArgument
    data class Decimal(override val value: Double) : UiFormatArgument {
        init { require(value.isFinite()) { "Display decimals must be finite" } }
    }
}

internal fun generatedText(sourceKey: String, formatter: (Resources) -> String): UiText =
    UiText.Generated(sourceKey, formatter)

internal fun formattedText(resourceId: Int, vararg arguments: UiFormatArgument): UiText =
    UiText.Format(resourceId, arguments.asList().snapshot())

@Composable
fun uiString(text: UiText): String {
    LocalConfiguration.current
    return text.resolve(LocalContext.current.resources)
}

internal fun resourceLocale(resources: Resources): Locale = resources.configuration.locales[0]
