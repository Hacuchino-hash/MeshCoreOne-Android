// PortedFrom: MC1/Views/Chats/Components/MessageText.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.feature.chats.composer

/**
 * `MessageText.buildFormattedText`: normalize (mentions, contact shares), tokenize (URL, meshcore,
 * hashtag, coordinate) and return the styled-span model the Compose layer renders. Pure and cheap
 * enough to cache per message text; the UI turns tokens into an `AnnotatedString`.
 */
object MessageTextFormatter {
    fun format(text: String, isOutgoing: Boolean = false, currentUserName: String? = null): FormattedMessage {
        val normalized = MessageTextNormalizer.normalize(text, MessageTextNormalizer.StyleContext(isOutgoing, currentUserName))
        val tokenized = MessageLinkTokenizer.tokenize(normalized.text, normalized.spans, MessageLinkTokenizer.StyleContext(isOutgoing))
        return FormattedMessage(normalized.text, tokenized.tokens, tokenized.mapCoordinate)
    }

    /** Strips invisible/control scalars from an inbound name (shared with the mention-tap path). */
    fun displayName(name: String): String = MessageTextNormalizer.displayName(name)
}
