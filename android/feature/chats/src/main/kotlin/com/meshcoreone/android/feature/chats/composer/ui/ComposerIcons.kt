// AndroidOnly: WP-308 Three original stroke glyphs the locked icon set lacks (no material-icons dependency), drawn like core:designsystem MeshIcons.
package com.meshcoreone.android.feature.chats.composer.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

internal object ComposerIcons {
    val Add: ImageVector by lazy {
        glyph("Add") {
            moveTo(12f, 5f); lineTo(12f, 19f)
            moveTo(5f, 12f); lineTo(19f, 12f)
        }
    }
    val ArrowUp: ImageVector by lazy {
        glyph("ArrowUp") {
            moveTo(12f, 19f); lineTo(12f, 5f)
            moveTo(6f, 11f); lineTo(12f, 5f); lineTo(18f, 11f)
        }
    }
    val Emoji: ImageVector by lazy {
        glyph("Emoji") {
            moveTo(3f, 12f)
            arcToRelative(9f, 9f, 0f, true, true, 18f, 0f)
            arcToRelative(9f, 9f, 0f, true, true, -18f, 0f)
            moveTo(9f, 10f); lineTo(9f, 10.5f)
            moveTo(15f, 10f); lineTo(15f, 10.5f)
            moveTo(8.5f, 14.5f); quadTo(12f, 18f, 15.5f, 14.5f)
        }
    }

    private fun glyph(name: String, build: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = build,
            )
        }.build()
}
