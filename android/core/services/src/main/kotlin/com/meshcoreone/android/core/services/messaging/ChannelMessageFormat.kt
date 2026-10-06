// PortedFrom: MC1Services/Sources/MC1Services/Utilities/ChannelMessageFormat.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.core.services.messaging

object ChannelMessageFormat {
    data class Parsed(val senderName: String, val messageText: String)

    fun parse(text: String): Parsed? {
        val colon = text.indexOf(':')
        if (colon <= 0) return null
        return Parsed(trimSpaces(text.substring(0, colon)), trimSpaces(text.substring(colon + 1)))
    }

    private fun trimSpaces(value: String): String =
        value.trim { it == '\t' || Character.getType(it) == Character.SPACE_SEPARATOR.toInt() }
}
