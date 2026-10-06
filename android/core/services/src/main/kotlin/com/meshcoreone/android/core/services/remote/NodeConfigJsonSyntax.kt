// AndroidOnly: WP-210 Strict RFC 8259 reader and Foundation-JSONEncoder-compatible writer; core:services has no JSON library (no kotlinx-serialization/org.json on the JVM classpath).
package com.meshcoreone.android.core.services.remote

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/** A parsed JSON value. Numbers keep their source text so integer range checks stay exact. */
internal sealed interface NodeConfigJsonValue {
    /**
     * Members in source order; lookups resolve a duplicated key to its FIRST occurrence, as Foundation's
     * JSONDecoder does (verified against swiftc/Foundation on macOS 26 at every nesting level, null included).
     */
    data class Object(val members: List<Pair<String, NodeConfigJsonValue>>) : NodeConfigJsonValue {
        operator fun get(key: String): NodeConfigJsonValue? = members.firstOrNull { it.first == key }?.second
        fun containsKey(key: String): Boolean = members.any { it.first == key }
    }
    data class Array(val items: List<NodeConfigJsonValue>) : NodeConfigJsonValue
    data class Text(val value: String) : NodeConfigJsonValue

    /**
     * A parsed string value whose escapes and control characters are validated only when it is decoded,
     * like Foundation's JSONDecoder: an invalid string under an unknown or shadowed duplicate key is
     * never an error, but one the decoder actually reads is reported as corrupted data.
     */
    data class RawText(val raw: String) : NodeConfigJsonValue {
        /** @throws NodeConfigJsonSyntaxException for an invalid escape or an unescaped control character. */
        fun decode(): String = NodeConfigJsonReader.decodeStringContents(raw)
    }

    /** The literal as scanned; its JSON number grammar is validated only when it is decoded (Foundation). */
    data class Number(val literal: String) : NodeConfigJsonValue
    data class Bool(val value: Boolean) : NodeConfigJsonValue
    data object Null : NodeConfigJsonValue
}

/** Raised for malformed JSON text (Swift `DecodingError.dataCorrupted` at the root). */
class NodeConfigJsonSyntaxException(message: String) : IllegalArgumentException(message)

internal object NodeConfigJsonReader {
    private const val MAX_DEPTH = 512

    /** Decodes UTF-8 (an optional BOM is skipped) and rejects malformed byte sequences. */
    fun parse(bytes: ByteArray): NodeConfigJsonValue {
        val start = if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) 3 else 0
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val text = try {
            decoder.decode(ByteBuffer.wrap(bytes, start, bytes.size - start)).toString()
        } catch (error: CharacterCodingException) {
            throw NodeConfigJsonSyntaxException("The given data was not valid UTF-8 JSON.")
        }
        return parse(text)
    }

    fun parse(text: String): NodeConfigJsonValue = Cursor(text).parseDocument()

    /** Strict RFC 8259 number grammar, checked when a scanned number is decoded. */
    private val NUMBER_GRAMMAR = Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")

    fun isValidNumber(literal: String): Boolean = NUMBER_GRAMMAR.matches(literal)

    /** Decodes the contents between a string's quotes, validating escapes and control characters. */
    fun decodeStringContents(raw: String): String = Cursor("\"$raw\"").parseQuotedString()

    private class Cursor(private val text: String) {
        private var index = 0

        fun parseDocument(): NodeConfigJsonValue {
            skipWhitespace()
            if (index >= text.length) fail("The given data was not valid JSON (empty input).")
            val value = parseValue(0)
            skipWhitespace()
            if (index != text.length) fail("Unexpected character after the top-level value at offset $index.")
            return value
        }

        private fun parseValue(depth: Int): NodeConfigJsonValue {
            if (depth > MAX_DEPTH) fail("JSON nesting is too deep.")
            skipWhitespace()
            if (index >= text.length) fail("Unexpected end of JSON.")
            return when (val character = text[index]) {
                '{' -> parseObject(depth)
                '[' -> parseArray(depth)
                '"' -> NodeConfigJsonValue.RawText(scanString())
                't' -> literal("true", NodeConfigJsonValue.Bool(true))
                'f' -> literal("false", NodeConfigJsonValue.Bool(false))
                'n' -> literal("null", NodeConfigJsonValue.Null)
                else -> if (character == '-' || character in '0'..'9') scanNumber() else fail("Invalid value at offset $index.")
            }
        }

        private fun parseObject(depth: Int): NodeConfigJsonValue {
            index++
            val members = mutableListOf<Pair<String, NodeConfigJsonValue>>()
            skipWhitespace()
            if (peek() == '}') { index++; return NodeConfigJsonValue.Object(members) }
            while (true) {
                skipWhitespace()
                if (peek() != '"') fail("Expected an object key at offset $index.")
                val key = parseString()
                skipWhitespace()
                expect(':')
                members += key to parseValue(depth + 1)
                skipWhitespace()
                when (peek()) {
                    ',' -> {
                        index++
                        skipWhitespace()
                        // Foundation accepts one trailing comma before the closer ({"a":1,}), not {,}.
                        if (peek() == '}') { index++; return NodeConfigJsonValue.Object(members) }
                    }
                    '}' -> { index++; return NodeConfigJsonValue.Object(members) }
                    else -> fail("Expected ',' or '}' at offset $index.")
                }
            }
        }

        private fun parseArray(depth: Int): NodeConfigJsonValue {
            index++
            val items = mutableListOf<NodeConfigJsonValue>()
            skipWhitespace()
            if (peek() == ']') { index++; return NodeConfigJsonValue.Array(items) }
            while (true) {
                items += parseValue(depth + 1)
                skipWhitespace()
                when (peek()) {
                    ',' -> {
                        index++
                        skipWhitespace()
                        // Foundation accepts one trailing comma before the closer ([1,]), not [,].
                        if (peek() == ']') { index++; return NodeConfigJsonValue.Array(items) }
                    }
                    ']' -> { index++; return NodeConfigJsonValue.Array(items) }
                    else -> fail("Expected ',' or ']' at offset $index.")
                }
            }
        }

        /** Decodes a whole quoted string (used for object keys, which Foundation validates eagerly). */
        fun parseQuotedString(): String {
            val value = parseString()
            if (index != text.length) fail("Unexpected character after string at offset $index.")
            return value
        }

        /** Finds a value string's closing quote, skipping escaped characters, without validating it. */
        private fun scanString(): String {
            expect('"')
            val start = index
            while (true) {
                if (index >= text.length) fail("Unterminated string.")
                when (text[index++]) {
                    '"' -> return text.substring(start, index - 1)
                    '\\' -> {
                        if (index >= text.length) fail("Unterminated string.")
                        index++
                    }
                }
            }
        }

        private fun parseString(): String {
            expect('"')
            val builder = StringBuilder()
            while (true) {
                if (index >= text.length) fail("Unterminated string.")
                val character = text[index++]
                when {
                    character == '"' -> return builder.toString()
                    character == '\\' -> builder.append(parseEscape())
                    character < ' ' -> fail("Unescaped control character in string at offset ${index - 1}.")
                    else -> builder.append(character)
                }
            }
        }

        private fun parseEscape(): String {
            if (index >= text.length) fail("Unterminated escape sequence.")
            return when (val escape = text[index++]) {
                '"' -> "\""
                '\\' -> "\\"
                '/' -> "/"
                'b' -> "\b"
                'f' -> "\u000C"
                'n' -> "\n"
                'r' -> "\r"
                't' -> "\t"
                'u' -> parseUnicodeEscape()
                else -> fail("Invalid escape '\\$escape'.")
            }
        }

        private fun parseUnicodeEscape(): String {
            val first = hex4()
            if (first.isLowSurrogate()) fail("Unpaired low surrogate in \\u escape.")
            if (!first.isHighSurrogate()) return first.toString()
            if (!text.startsWith("\\u", index)) fail("Unpaired high surrogate in \\u escape.")
            index += 2
            val second = hex4()
            if (!second.isLowSurrogate()) fail("Invalid surrogate pair in \\u escape.")
            return String(charArrayOf(first, second))
        }

        private fun hex4(): Char {
            if (index + 4 > text.length) fail("Truncated \\u escape.")
            val digits = text.substring(index, index + 4)
            if (!digits.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) fail("Invalid \\u escape.")
            index += 4
            return digits.toInt(16).toChar()
        }

        /**
         * Scans a number the way Foundation's structural pass does: a leading '-' or digit, then any run
         * of digits, '.', 'e', 'E', '+' or '-'. Grammar errors such as `01` or `1.` surface only on decode.
         */
        private fun scanNumber(): NodeConfigJsonValue {
            val start = index
            index++
            while (peek().let { it in '0'..'9' || it == '.' || it == 'e' || it == 'E' || it == '+' || it == '-' }) index++
            return NodeConfigJsonValue.Number(text.substring(start, index))
        }

        private fun literal(word: String, value: NodeConfigJsonValue): NodeConfigJsonValue {
            if (!text.startsWith(word, index)) fail("Invalid literal at offset $index.")
            index += word.length
            return value
        }

        private fun expect(character: Char) {
            if (peek() != character) fail("Expected '$character' at offset $index.")
            index++
        }

        private fun peek(): Char = if (index < text.length) text[index] else '\u0000'

        private fun skipWhitespace() {
            while (index < text.length && text[index].let { it == ' ' || it == '\t' || it == '\n' || it == '\r' }) index++
        }

        private fun fail(message: String): Nothing = throw NodeConfigJsonSyntaxException(message)
    }
}

/**
 * Serializes like Foundation's `JSONEncoder`: compact, or `.prettyPrinted` (two-space indent,
 * `"key" : value`, empty containers as an opening bracket, a blank line, then the indented closer),
 * with `.sortedKeys` ordering and the default `/` -> `\/` escaping. Non-ASCII text is written raw.
 * Sorting is ordinal, which equals Foundation's ordering for the fixed lowercase snake_case keys the
 * node config format uses.
 */
internal object NodeConfigJsonWriter {
    private const val INDENT = "  "

    fun write(value: NodeConfigJsonValue, prettyPrinted: Boolean, sortedKeys: Boolean): String =
        StringBuilder().also { append(it, value, prettyPrinted, sortedKeys, 0) }.toString()

    private fun append(out: StringBuilder, value: NodeConfigJsonValue, pretty: Boolean, sorted: Boolean, depth: Int) {
        when (value) {
            is NodeConfigJsonValue.Object -> {
                val members = if (sorted) value.members.sortedBy { it.first } else value.members
                container(out, '{', '}', members, pretty, depth) { (key, member) ->
                    appendString(out, key)
                    out.append(if (pretty) " : " else ":")
                    append(out, member, pretty, sorted, depth + 1)
                }
            }
            is NodeConfigJsonValue.Array -> container(out, '[', ']', value.items, pretty, depth) {
                append(out, it, pretty, sorted, depth + 1)
            }
            is NodeConfigJsonValue.Text -> appendString(out, value.value)
            is NodeConfigJsonValue.RawText -> appendString(out, value.decode())
            is NodeConfigJsonValue.Number -> out.append(value.literal)
            is NodeConfigJsonValue.Bool -> out.append(value.value)
            NodeConfigJsonValue.Null -> out.append("null")
        }
    }

    private fun <T> container(
        out: StringBuilder, open: Char, close: Char, elements: List<T>, pretty: Boolean, depth: Int,
        element: (T) -> Unit,
    ) {
        out.append(open)
        if (pretty) out.append('\n')
        elements.forEachIndexed { position, item ->
            if (position > 0) out.append(if (pretty) ",\n" else ",")
            if (pretty) out.append(INDENT.repeat(depth + 1))
            element(item)
        }
        if (pretty) out.append('\n').append(INDENT.repeat(depth))
        out.append(close)
    }

    private fun appendString(out: StringBuilder, value: String) {
        out.append('"')
        for (character in value) {
            when (character) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '/' -> out.append("\\/")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                '\b' -> out.append("\\b")
                '\u000C' -> out.append("\\f")
                else -> if (character < ' ') {
                    out.append("\\u00").append(Character.forDigit(character.code shr 4, 16)).append(Character.forDigit(character.code and 15, 16))
                } else {
                    out.append(character)
                }
            }
        }
        out.append('"')
    }
}
