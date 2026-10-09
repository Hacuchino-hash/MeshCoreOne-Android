// AndroidOnly: WP-308 Wires a ComposerStateHolder to the stateless bar: TextFieldValue sync, focus requests, emoji picker.
package com.meshcoreone.android.feature.chats.composer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshcoreone.android.feature.chats.composer.ComposerStateHolder
import com.meshcoreone.android.feature.chats.composer.EmojiPickerStateHolder

/**
 * Collects [holder] (lifecycle-aware) and renders the bar. The field keeps its own [TextFieldValue] so
 * the selection and IME composition survive; the holder's draft is the source of truth for everything
 * else, so an external insert (mention pick, share, emoji) replaces the field text and moves the caret.
 *
 * [onPickContact] opens the contact picker for "Share Contact" (the picker lives with the contacts
 * feature; its result comes back through [ComposerStateHolder.insertShared]).
 */
@Composable
fun ComposerRoute(
    holder: ComposerStateHolder,
    placeholder: String,
    onPickContact: () -> Unit,
    modifier: Modifier = Modifier,
    emojiPicker: EmojiPickerStateHolder? = null,
) {
    val state by holder.state.collectAsStateWithLifecycle()
    var fieldValue by remember { mutableStateOf(TextFieldValue(state.draft, TextRange(state.draft.length))) }
    var showEmoji by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    DisposableEffect(holder) { onDispose(holder::close) }
    // An insert, restore or clear that did not come from typing replaces the field text.
    LaunchedEffect(state.draft) {
        if (fieldValue.text != state.draft) fieldValue = TextFieldValue(state.draft, TextRange(state.draft.length))
    }
    LaunchedEffect(state.focusRequest) {
        if (state.focusRequest > 0) runCatching { focusRequester.requestFocus() }
    }

    Box(modifier) {
        ComposerBar(
            state = state,
            fieldValue = fieldValue,
            placeholder = placeholder,
            share = ShareAvailability(holder.canShareLocation(), holder.canShareMyInfo()),
            actions = ComposerBarActions(
                onFieldValueChange = { value ->
                    val textChanged = value.text != fieldValue.text
                    fieldValue = value
                    if (textChanged) holder.onDraftChanged(value.text)
                },
                onSend = { holder.send() },
                onShareLocation = { holder.shareLocation() },
                onShareContact = onPickContact,
                onShareMyInfo = holder::shareMyInfo,
                onOpenEmoji = emojiPicker?.let { { showEmoji = true } },
                onDismissFailure = holder::dismissFailure,
            ),
            focusRequester = focusRequester,
        )
    }
    if (showEmoji && emojiPicker != null) {
        EmojiPickerSheet(
            holder = emojiPicker,
            onDismiss = { showEmoji = false },
            onPick = { emoji ->
                emojiPicker.markAsFrequentlyUsed(emoji)
                val caret = holder.insertAtSelection(emoji, fieldValue.selection.start, fieldValue.selection.end)
                fieldValue = TextFieldValue(holder.state.value.draft, TextRange(caret))
                showEmoji = false
                holder.requestFocus()
            },
        )
    }
}
