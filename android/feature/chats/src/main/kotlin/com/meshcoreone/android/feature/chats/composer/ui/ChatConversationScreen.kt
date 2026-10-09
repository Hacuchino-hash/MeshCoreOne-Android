// AndroidOnly: WP-308 Conversation screen: timeline (WP-307) + composer, mention overlay and tap handling in one edge-to-edge column.
package com.meshcoreone.android.feature.chats.composer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshcoreone.android.core.model.ContactDTO
import com.meshcoreone.android.feature.chats.composer.ComposerStateHolder
import com.meshcoreone.android.feature.chats.composer.EmojiPickerStateHolder
import com.meshcoreone.android.feature.chats.composer.MentionPickerContext
import com.meshcoreone.android.feature.chats.composer.MentionTapEvaluator
import com.meshcoreone.android.feature.chats.composer.MessageLinkDispatcher
import com.meshcoreone.android.feature.chats.timeline.ChatViewModel
import com.meshcoreone.android.feature.chats.timeline.ui.ChatTimelineRoute

/** What the shell binds for tap handling inside one conversation. */
data class ConversationLinkActions(
    val contacts: () -> List<ContactDTO>,
    val connectedDeviceName: () -> String?,
    val radioId: () -> com.meshcoreone.android.core.model.RadioId,
    /** The shared `ChatLinkRouter.route`. */
    val route: (String) -> Boolean,
    val openExternal: (String) -> Unit,
    val navigateToContact: (ContactDTO) -> Unit,
)

/**
 * Plug-in for `ChatsEntry`'s `detailPane`: timeline with linkified bodies, the `@` suggestion overlay
 * above the composer, and the composer itself. Insets are edge-to-edge: the bar sits above the
 * navigation bar and the IME (`navigationBarsPadding().imePadding()` consumes in that order, so the
 * IME adds only what exceeds the nav bar).
 */
@Composable
fun ChatConversationScreen(
    timeline: ChatViewModel,
    composer: ComposerStateHolder,
    placeholder: String,
    links: ConversationLinkActions,
    currentUserName: String?,
    onPickContact: () -> Unit,
    modifier: Modifier = Modifier,
    emojiPicker: EmojiPickerStateHolder? = null,
) {
    val composerState by composer.state.collectAsStateWithLifecycle()
    var pickerContext by remember { mutableStateOf<MentionPickerContext?>(null) }
    val dispatcher = remember(links) {
        MessageLinkDispatcher(
            links.contacts, links.radioId, links.connectedDeviceName,
            onMention = { outcome ->
                when (outcome) {
                    is MentionTapEvaluator.Outcome.Navigate -> links.navigateToContact(outcome.contact)
                    is MentionTapEvaluator.Outcome.Picker -> pickerContext = outcome.context
                }
            },
            route = links.route,
            openExternal = links.openExternal,
        )
    }
    Column(modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
        Box(Modifier.weight(1f)) {
            ChatTimelineRoute(
                viewModel = timeline,
                messageBody = { message, outgoing ->
                    MessageBody(message.text, outgoing, currentUserName, onLink = { dispatcher.dispatch(it) })
                },
            )
            MentionSuggestionList(
                composerState.mentionSuggestions,
                onSelect = composer::selectMention,
                modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        ComposerRoute(composer, placeholder, onPickContact, emojiPicker = emojiPicker)
    }
    pickerContext?.let { context ->
        MentionPickerSheet(
            context,
            onSelect = { contact -> pickerContext = null; links.navigateToContact(contact) },
            onDismiss = { pickerContext = null },
        )
    }
}
