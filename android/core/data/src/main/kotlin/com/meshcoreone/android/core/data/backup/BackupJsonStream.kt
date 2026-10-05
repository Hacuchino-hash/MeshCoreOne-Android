// AndroidOnly: WP-203 Record-at-a-time strict JSON traversal behind the checked inflation stream.
package com.meshcoreone.android.core.data.backup

import java.io.Reader
import kotlinx.serialization.json.*

internal class BackupJsonStream(private val reader: Reader, private val checkCancellation: () -> Unit) {
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
    private fun whitespace() { while (peek() in listOf(32, 9, 10, 13)) take() }
    private fun expect(character: Char) {
        whitespace()
        if (take() != character.code) invalidValue("json", BackupValueProblem.JSON)
    }

    fun objectMembers(consume: (String) -> Unit) {
        expect('{')
        whitespace()
        if (peek() == '}'.code) { take(); return }
        val seen = hashSetOf<String>()
        while (true) {
            val key = string()
            if (!seen.add(key)) invalidValue("json.duplicateKey", BackupValueProblem.JSON)
            expect(':')
            consume(key)
            whitespace()
            when (take()) {
                '}'.code -> return
                ','.code -> Unit
                else -> invalidValue("json", BackupValueProblem.JSON)
            }
        }
    }

    fun <T> arrayValues(decode: (JsonElement) -> T): List<T> {
        expect('[')
        whitespace()
        val values = mutableListOf<T>()
        if (peek() == ']'.code) { take(); return values }
        while (true) {
            checkCancellation()
            values += decode(element())
            whitespace()
            when (take()) {
                ']'.code -> return values
                ','.code -> Unit
                else -> invalidValue("json", BackupValueProblem.JSON)
            }
        }
    }

    fun nextIsNull(): Boolean { whitespace(); return peek() == 'n'.code }

    fun element(depth: Int = 0): JsonElement {
        if (depth > 128) invalidValue("json.depth", BackupValueProblem.JSON)
        whitespace()
        return when (peek()) {
            '{'.code -> {
                val values = linkedMapOf<String, JsonElement>()
                objectMembers { key -> values[key] = element(depth + 1) }
                JsonObject(values)
            }
            '['.code -> {
                expect('[')
                val values = mutableListOf<JsonElement>()
                whitespace()
                if (peek() == ']'.code) take() else while (true) {
                    values += element(depth + 1)
                    whitespace()
                    when (take()) {
                        ']'.code -> break
                        ','.code -> Unit
                        else -> invalidValue("json", BackupValueProblem.JSON)
                    }
                }
                JsonArray(values)
            }
            '"'.code -> JsonPrimitive(string())
            -1 -> invalidValue("json.truncated", BackupValueProblem.JSON)
            else -> {
                val token = StringBuilder()
                while (peek() != -1 && peek() !in listOf(32, 9, 10, 13, ','.code, ']'.code, '}'.code)) token.append(take().toChar())
                val text = token.toString()
                if (text !in setOf("true", "false", "null") && !NUMBER.matches(text)) {
                    invalidValue("json.number", BackupValueProblem.JSON)
                }
                val result = backupJson.parseToJsonElement(text)
                if (result !is JsonPrimitive) invalidValue("json", BackupValueProblem.JSON)
                result
            }
        }
    }

    private fun string(): String {
        expect('"')
        val text = StringBuilder("\"")
        var escaped = false
        while (true) {
            val next = take()
            if (next < 32) invalidValue("json.string", BackupValueProblem.JSON)
            val character = next.toChar()
            text.append(character)
            if (!escaped && character == '"') break
            escaped = !escaped && character == '\\'
        }
        return backupJson.parseToJsonElement(text.toString()).jsonPrimitive.content
    }

    fun requireEnd() {
        whitespace()
        if (peek() != -1) invalidValue("json.trailing", BackupValueProblem.JSON)
        checkCancellation()
    }

    companion object {
        private val NUMBER = Regex("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?")
    }
}
