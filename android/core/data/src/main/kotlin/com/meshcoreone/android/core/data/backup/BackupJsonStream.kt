// AndroidOnly: WP-203 Strict record streaming and allocation-free validation of ignored Codable fields.
package com.meshcoreone.android.core.data.backup

import java.io.Reader
import kotlinx.serialization.json.*

internal sealed interface JsonReadSelection {
    data object Scalar : JsonReadSelection
    data class Object(val fields: Map<String, JsonReadSelection>, val rejectUnknown: Boolean = false) : JsonReadSelection
    data class Array(val item: JsonReadSelection) : JsonReadSelection
}

internal fun interface JsonMaterializationObserver { fun created() }

internal class BackupJsonStream(
    private val reader: Reader,
    private val checkCancellation: () -> Unit,
    private val observer: JsonMaterializationObserver? = null,
) {
    private var lookahead = -2
    private var readCount = 0

    private fun peek(): Int {
        if (lookahead == -2) lookahead = reader.read()
        return lookahead
    }
    private fun take(): Int {
        val result = peek()
        lookahead = -2
        if (++readCount % 8192 == 0) checkCancellation()
        return result
    }
    private fun whitespace() { while (isWhitespace(peek())) take() }
    private fun expect(character: Char) {
        whitespace()
        if (take() != character.code) invalidValue("json", BackupValueProblem.JSON)
    }
    private fun depth(value: Int) {
        if (value > 128) invalidValue("json.depth", BackupValueProblem.JSON)
    }

    fun objectMembers(selection: JsonReadSelection.Object, atDepth: Int = 0, consume: (String, JsonReadSelection) -> Unit) {
        depth(atDepth)
        expect('{')
        whitespace()
        if (peek() == '}'.code) { take(); return }
        val seen = hashSetOf<String>()
        val maximumKeyLength = selection.fields.keys.maxOfOrNull { it.length } ?: 0
        while (true) {
            val captured = StringBuilder(minOf(maximumKeyLength, 64))
            var tooLong = false
            decodedString { character ->
                if (captured.length < maximumKeyLength) captured.append(character) else tooLong = true
            }
            val key = if (tooLong) null else captured.toString().takeIf { it in selection.fields }
            expect(':')
            if (key == null) {
                skipValue(atDepth + 1)
                if (selection.rejectUnknown) invalidValue("json.enum", BackupValueProblem.ENUM)
            } else {
                if (!seen.add(key)) invalidValue("json.duplicateKey", BackupValueProblem.JSON)
                consume(key, selection.fields.getValue(key))
            }
            whitespace()
            when (take()) {
                '}'.code -> return
                ','.code -> Unit
                else -> invalidValue("json", BackupValueProblem.JSON)
            }
        }
    }

    fun <T> arrayValues(selection: JsonReadSelection, decode: (JsonElement) -> T): List<T> {
        expect('[')
        whitespace()
        val values = mutableListOf<T>()
        if (peek() == ']'.code) { take(); return values }
        while (true) {
            checkCancellation()
            values += decode(element(selection, 1))
            whitespace()
            when (take()) {
                ']'.code -> return values
                ','.code -> Unit
                else -> invalidValue("json", BackupValueProblem.JSON)
            }
        }
    }

    fun nextIsNull(): Boolean { whitespace(); return peek() == 'n'.code }

    fun element(selection: JsonReadSelection = JsonReadSelection.Scalar, atDepth: Int = 0): JsonElement {
        depth(atDepth)
        whitespace()
        val result = when (peek()) {
            '{'.code -> {
                val objectSelection = selection as? JsonReadSelection.Object ?: invalidValue("json.object", BackupValueProblem.TYPE)
                val values = linkedMapOf<String, JsonElement>()
                objectMembers(objectSelection, atDepth) { key, nested -> values[key] = element(nested, atDepth + 1) }
                JsonObject(values)
            }
            '['.code -> {
                val arraySelection = selection as? JsonReadSelection.Array ?: invalidValue("json.array", BackupValueProblem.TYPE)
                expect('[')
                val values = mutableListOf<JsonElement>()
                whitespace()
                if (peek() == ']'.code) take() else while (true) {
                    values += element(arraySelection.item, atDepth + 1)
                    whitespace()
                    when (take()) {
                        ']'.code -> break
                        ','.code -> Unit
                        else -> invalidValue("json", BackupValueProblem.JSON)
                    }
                }
                JsonArray(values)
            }
            '"'.code -> {
                if (selection != JsonReadSelection.Scalar) invalidValue("json.string", BackupValueProblem.TYPE)
                val text = StringBuilder()
                decodedString { text.append(it) }
                JsonPrimitive(text.toString())
            }
            -1 -> invalidValue("json.truncated", BackupValueProblem.JSON)
            else -> {
                val primitive = requireNotNull(primitive(capture = true))
                if (selection != JsonReadSelection.Scalar && primitive !is JsonNull) invalidValue("json.value", BackupValueProblem.TYPE)
                primitive
            }
        }
        observer?.created()
        return result
    }

    fun skipValue(atDepth: Int = 0) {
        depth(atDepth)
        whitespace()
        when (peek()) {
            '{'.code -> {
                expect('{')
                whitespace()
                if (peek() == '}'.code) take() else while (true) {
                    decodedString {}
                    expect(':')
                    skipValue(atDepth + 1)
                    whitespace()
                    when (take()) {
                        '}'.code -> break
                        ','.code -> Unit
                        else -> invalidValue("json", BackupValueProblem.JSON)
                    }
                }
            }
            '['.code -> {
                expect('[')
                whitespace()
                if (peek() == ']'.code) take() else while (true) {
                    skipValue(atDepth + 1)
                    whitespace()
                    when (take()) {
                        ']'.code -> break
                        ','.code -> Unit
                        else -> invalidValue("json", BackupValueProblem.JSON)
                    }
                }
            }
            '"'.code -> decodedString {}
            -1 -> invalidValue("json.truncated", BackupValueProblem.JSON)
            else -> primitive(capture = false)
        }
    }

    private inline fun decodedString(consume: (Char) -> Unit) {
        expect('"')
        while (true) {
            val next = take()
            if (next < 32) invalidValue("json.string", BackupValueProblem.JSON)
            when (next) {
                '"'.code -> return
                '\\'.code -> consume(when (val escaped = take()) {
                    '"'.code, '\\'.code, '/'.code -> escaped.toChar()
                    'b'.code -> '\b'
                    'f'.code -> '\u000C'
                    'n'.code -> '\n'
                    'r'.code -> '\r'
                    't'.code -> '\t'
                    'u'.code -> {
                        var code = 0
                        repeat(4) {
                            val digit = take()
                            val value = when (digit) {
                                in '0'.code..'9'.code -> digit - '0'.code
                                in 'A'.code..'F'.code -> digit - 'A'.code + 10
                                in 'a'.code..'f'.code -> digit - 'a'.code + 10
                                else -> invalidValue("json.escape", BackupValueProblem.JSON)
                            }
                            code = code * 16 + value
                        }
                        code.toChar()
                    }
                    else -> invalidValue("json.escape", BackupValueProblem.JSON)
                })
                else -> consume(next.toChar())
            }
        }
    }

    private fun primitive(capture: Boolean): JsonElement? {
        val text = if (capture) StringBuilder() else null
        fun consume() { val character = take(); text?.append(character.toChar()) }
        fun literal(expected: String) {
            for (character in expected) {
                if (peek() != character.code) invalidValue("json.literal", BackupValueProblem.JSON)
                consume()
            }
        }
        fun digits() {
            if (peek() !in '0'.code..'9'.code) invalidValue("json.number", BackupValueProblem.JSON)
            while (peek() in '0'.code..'9'.code) consume()
        }
        when (peek()) {
            't'.code -> literal("true")
            'f'.code -> literal("false")
            'n'.code -> literal("null")
            else -> {
                if (peek() == '-'.code) consume()
                if (peek() == '0'.code) {
                    consume()
                    if (peek() in '0'.code..'9'.code) invalidValue("json.number", BackupValueProblem.JSON)
                } else {
                    if (peek() !in '1'.code..'9'.code) invalidValue("json.number", BackupValueProblem.JSON)
                    digits()
                }
                if (peek() == '.'.code) { consume(); digits() }
                if (peek() == 'e'.code || peek() == 'E'.code) {
                    consume()
                    if (peek() == '+'.code || peek() == '-'.code) consume()
                    digits()
                }
            }
        }
        if (!isValueEnd(peek())) invalidValue("json.value", BackupValueProblem.JSON)
        return text?.let { backupJson.parseToJsonElement(it.toString()) }
    }

    fun requireEnd() {
        whitespace()
        if (peek() != -1) invalidValue("json.trailing", BackupValueProblem.JSON)
        checkCancellation()
    }

    private fun isWhitespace(value: Int): Boolean = value == 32 || value == 9 || value == 10 || value == 13
    private fun isValueEnd(value: Int): Boolean =
        value == -1 || isWhitespace(value) || value == ','.code || value == ']'.code || value == '}'.code
}
