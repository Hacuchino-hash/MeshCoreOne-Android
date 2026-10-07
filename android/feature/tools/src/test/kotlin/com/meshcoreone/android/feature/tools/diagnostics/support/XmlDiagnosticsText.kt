// AndroidOnly: WP-316 English core:l10n strings read from the resource XML so tests assert the real keys and text.
package com.meshcoreone.android.feature.tools.diagnostics.support

import com.meshcoreone.android.feature.tools.diagnostics.DiagnosticsText
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * A [DiagnosticsText] over core:l10n `values/l10n_strings.xml` (English). Resource ids are mapped
 * back to names through the generated `R.string` class, so a wrong id in production code shows
 * up as wrong text in a test rather than passing against a hand-written fake.
 */
internal class XmlDiagnosticsText(override val locale: Locale = Locale.US) : DiagnosticsText {
    override fun string(id: Int): String = templates[id] ?: error("Unknown string resource id $id")

    override fun format(id: Int, vararg args: Any): String = String.format(locale, string(id), *args)

    /** CLDR English list pattern (`ListFormatter` en_US, oracle-checked). */
    override fun joinList(items: List<String>): String = when (items.size) {
        0 -> ""
        1 -> items[0]
        2 -> "${items[0]} and ${items[1]}"
        else -> items.dropLast(1).joinToString(", ") + ", and " + items.last()
    }

    companion object {
        private val templates: Map<Int, String> by lazy { load() }

        fun name(id: Int): String? = idsToNames[id]

        private val idsToNames: Map<Int, String> by lazy {
            Class.forName("com.meshcoreone.android.core.l10n.R\$string").fields
                .associate { it.getInt(null) to it.name }
        }

        private fun load(): Map<Int, String> {
            val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(stringsFile())
            val nodes = document.getElementsByTagName("string")
            val byName = (0 until nodes.length).associate { index ->
                val element = nodes.item(index) as Element
                element.getAttribute("name") to decode(element.textContent)
            }
            return idsToNames.mapNotNull { (id, name) -> byName[name]?.let { id to it } }.toMap()
        }

        private fun stringsFile(): File {
            val relative = "core/l10n/src/main/res/values/l10n_strings.xml"
            var directory: File? = File(System.getProperty("user.dir")).absoluteFile
            while (directory != null) {
                val candidate = File(directory, relative)
                if (candidate.isFile) return candidate
                directory = directory.parentFile
            }
            error("Cannot locate $relative from ${System.getProperty("user.dir")}")
        }

        /** Android string-resource decoding: drops unescaped quotes and resolves backslash escapes. */
        private fun decode(raw: String): String {
            val result = StringBuilder()
            var index = 0
            while (index < raw.length) {
                val character = raw[index]
                when {
                    character == '\\' && index + 1 < raw.length -> {
                        val next = raw[index + 1]
                        when (next) {
                            'n' -> result.append('\n')
                            't' -> result.append('\t')
                            'u' -> {
                                result.append(raw.substring(index + 2, index + 6).toInt(16).toChar())
                                index += 4
                            }
                            else -> result.append(next)
                        }
                        index += 2
                    }
                    character == '"' -> index++
                    else -> {
                        result.append(character)
                        index++
                    }
                }
            }
            return result.toString()
        }
    }
}
