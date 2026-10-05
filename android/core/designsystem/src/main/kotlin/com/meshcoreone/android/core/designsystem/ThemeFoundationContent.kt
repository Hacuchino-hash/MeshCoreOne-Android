// AndroidOnly: WP-301 Immutable, executable token/selection/status host, not the WP-303 graph or appearance feature.
package com.meshcoreone.android.core.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.unit.dp
import com.meshcoreone.android.core.l10n.generated.AppLocalizableStrings
import com.meshcoreone.android.core.l10n.generated.AppSettingsStrings

data class ThemePreviewContent(
    val identityName: String,
    val identityGlyph: String,
    val incomingText: String,
    val outgoingText: String,
    val signal: SignalColorRole,
    val radio: RadioColorRole,
)

val ThemeFailureCode = SemanticsPropertyKey<String>("ThemeFailureCode")

@Composable
fun MeshBrandMark(modifier: Modifier = Modifier) {
    val label = stringResource(com.meshcoreone.android.core.l10n.R.string.app_name)
    Box(modifier.clip(RoundedCornerShape(12.dp)).semantics { contentDescription = label }) {
        Image(painterResource(R.drawable.meshcore_one_background), contentDescription = null, modifier = Modifier.matchParentSize())
        Image(painterResource(R.drawable.meshcore_one_mark), contentDescription = null, modifier = Modifier.matchParentSize())
    }
}

@Composable
fun ThemeSelectionSwatch(theme: Theme, isSelected: Boolean, onSelect: () -> Unit, modifier: Modifier = Modifier) {
    val name = themeName(theme)
    val resources = LocalContext.current.resources
    val label = if (isSelected) AppSettingsStrings.appearanceAccessibilityThemeCardSelectedLabel(resources, name)
        else AppSettingsStrings.appearanceAccessibilityThemeCardOwnedLabel(resources, name)
    Card(
        onClick = onSelect, enabled = !isSelected,
        shape = RoundedCornerShape(ThemeCardMetrics.CORNER_RADIUS.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier.then(if (isSelected) Modifier.border(
            BorderStroke(ThemeCardMetrics.SELECTION_STROKE_WIDTH.dp, MaterialTheme.colorScheme.primary),
            RoundedCornerShape(ThemeCardMetrics.CORNER_RADIUS.dp),
        ) else Modifier).widthIn(min = ThemeCardMetrics.GRID_ITEM_MINIMUM.dp)
            .sizeIn(minWidth = NativeThemeMetrics.MINIMUM_TOUCH_TARGET.dp, minHeight = NativeThemeMetrics.MINIMUM_TOUCH_TARGET.dp)
            .testTag("theme:${theme.id.rawValue}")
            .semantics {
                selected = isSelected
                role = Role.RadioButton
                contentDescription = label
            },
    ) {
        Column(Modifier.padding(ThemeCardMetrics.CONTENT_PADDING.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemePaletteSwatch(theme, Modifier.fillMaxWidth().height(64.dp), PaletteSwatchSize.THUMBNAIL)
            Text(name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.clearAndSetSemantics {})
            if (isSelected) {
                Row(horizontalArrangement = Arrangement.spacedBy(ThemeCardMetrics.BADGE_ICON_SPACING.dp)) {
                    Icon(MeshSymbol.CHECK.vector, null, Modifier.size(16.dp))
                    Text(stringResource(AppSettingsStrings.appearanceThemesSelected), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
fun AvatarColorSwatch(
    input: AvatarColorInput,
    glyph: String,
    modifier: Modifier = Modifier,
) {
    val frame = LocalMeshTheme.current.frame
    val fill = input.category?.let(frame::categoryAvatarColor) ?: frame.identityColor(input.name)
    val sourceGlyph = frame.avatarGlyphColor(fill, input.category != null && frame.theme.usesCategoryAvatarOverride)
    val foreground = readableGlyph(sourceGlyph, fill)
    Box(
        modifier.size(49.dp).background(fill.toComposeColor(), CircleShape).semantics { contentDescription = input.name },
        contentAlignment = Alignment.Center,
    ) {
        if (input.category != null) {
            val symbol = when (input.category) {
                AvatarCategory.CHANNEL -> MeshSymbol.HASHTAG
                AvatarCategory.REPEATER -> MeshSymbol.RADIO
                AvatarCategory.ROOM -> MeshSymbol.ROOM
            }
            Icon(symbol.vector, null, Modifier.size(24.dp), tint = foreground.toComposeColor())
        } else Text(glyph, color = foreground.toComposeColor(), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun SignalQualityBadge(quality: SignalColorRole, modifier: Modifier = Modifier) {
    val label = stringResource(quality.labelResource)
    StatusToken(label, MeshSymbol.SIGNAL, LocalMeshTheme.current.roles.signalColor(quality), modifier)
}

@Composable
fun RadioStatusBadge(status: RadioColorRole, modifier: Modifier = Modifier) {
    val label = stringResource(status.labelResource)
    val symbol = when (status) {
        RadioColorRole.READY -> MeshSymbol.READY
        RadioColorRole.FAILED -> MeshSymbol.ERROR
        RadioColorRole.REPEAT -> MeshSymbol.REPEAT
        RadioColorRole.CONNECTING, RadioColorRole.CONNECTED, RadioColorRole.SYNCING -> MeshSymbol.SYNC
        RadioColorRole.DISCONNECTED -> MeshSymbol.WARNING
    }
    StatusToken(label, symbol, LocalMeshTheme.current.roles.radioColor(status), modifier)
}

@Composable
private fun StatusToken(label: String, symbol: MeshSymbol, color: ThemeColor, modifier: Modifier) {
    Row(
        modifier.semantics(mergeDescendants = true) { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(symbol.vector, null, Modifier.size(24.dp), tint = color.toComposeColor())
        Text(label, color = color.toComposeColor(), style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.clearAndSetSemantics {})
    }
}

@Composable
fun ThemeFoundationContent(
    content: ThemePreviewContent,
    onSelect: (ThemeId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalAppTheme.current
    val tokens = LocalMeshTheme.current
    Surface(color = MaterialTheme.colorScheme.background, modifier = modifier.testTag("theme-foundation-preview")) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MeshBrandMark(Modifier.size(49.dp))
                Text(stringResource(AppSettingsStrings.appearanceTitle), style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() })
            }
            Text(themeName(theme), style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("active-theme"))
            RadioStatusBadge(content.radio, Modifier.testTag("radio-status"))
            SignalQualityBadge(content.signal, Modifier.testTag("signal-quality"))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AvatarColorSwatch(AvatarColorInput(content.identityName), content.identityGlyph)
                Text(content.identityName, color = tokens.frame.identityColor(content.identityName).toComposeColor(),
                    style = MaterialTheme.typography.bodyLarge)
            }
            Surface(color = tokens.roles.incomingBubble.toComposeColor(), shape = MaterialTheme.shapes.medium) {
                Text(content.incomingText, color = tokens.roles.onIncomingBubble.toComposeColor(),
                    style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(12.dp).testTag("incoming-sample"))
            }
            Surface(color = tokens.roles.outgoingBubble.toComposeColor(), shape = MaterialTheme.shapes.medium) {
                Text(content.outgoingText, color = tokens.roles.onOutgoingBubble.toComposeColor(),
                    style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(12.dp).testTag("outgoing-sample"))
            }
            Text(stringResource(AppSettingsStrings.appearanceThemesHeader), style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() })
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ThemeRegistry.allThemes.forEach { choice ->
                    ThemeSelectionSwatch(choice, choice.id == theme.id, { onSelect(choice.id) })
                }
            }
        }
    }
}

@Composable
fun ThemeFailureContent(state: ThemeServiceState.Failed, onRetry: () -> Unit) {
    val message = stringResource(AppLocalizableStrings.commonErrorFailedToLoad)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("theme-error").semantics {
            heading()
            this[ThemeFailureCode] = state.failure.problem.javaClass.simpleName
        })
        androidx.compose.material3.Button(onClick = onRetry, modifier = Modifier.sizeIn(
            minWidth = NativeThemeMetrics.MINIMUM_TOUCH_TARGET.dp, minHeight = NativeThemeMetrics.MINIMUM_TOUCH_TARGET.dp,
        )) { Text(stringResource(AppLocalizableStrings.commonTryAgain)) }
    }
}
