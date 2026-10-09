// AndroidOnly: WP-317 English and per-locale core:l10n strings read from the resource XML so tests assert real keys and text.
package com.meshcoreone.android.feature.settings.device.support

import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/** Reads `core/l10n/src/main/res/values[-qualifier]/l10n_strings.xml`; ids map back to names through the generated `R.string`. */
internal class XmlStrings(private val qualifier: String? = null) {
    private val byName: Map<String, String> by lazy { load() }

    fun string(id: Int): String = byName[name(id)] ?: error("No ${qualifier ?: "default"} string for id $id (${name(id)})")
    fun named(name: String): String = byName[name] ?: error("No ${qualifier ?: "default"} string named $name")
    fun format(id: Int, vararg args: Any): String = String.format(Locale.US, string(id), *args)

    private fun load(): Map<String, String> {
        val file = locate("core/l10n/src/main/res/values${qualifier?.let { "-$it" } ?: ""}/l10n_strings.xml")
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("string")
        return (0 until nodes.length).associate {
            val element = nodes.item(it) as Element
            element.getAttribute("name") to decode(element.textContent)
        }
    }

    private fun name(id: Int): String = idsToNames[id] ?: error("Unknown string resource id $id")

    private fun locate(relative: String): File {
        var directory: File? = File(System.getProperty("user.dir")).absoluteFile
        while (directory != null) {
            File(directory, relative).takeIf { it.isFile }?.let { return it }
            directory = directory.parentFile
        }
        error("Cannot locate $relative")
    }

    private fun decode(raw: String): String {
        val result = StringBuilder()
        var index = 0
        while (index < raw.length) {
            val character = raw[index]
            when {
                character == '\\' && index + 1 < raw.length -> {
                    when (val next = raw[index + 1]) {
                        'n' -> result.append('\n')
                        't' -> result.append('\t')
                        'u' -> { result.append(raw.substring(index + 2, index + 6).toInt(16).toChar()); index += 4 }
                        else -> result.append(next)
                    }
                    index += 2
                }
                character == '"' -> index++
                else -> { result.append(character); index++ }
            }
        }
        return result.toString()
    }

    companion object {
        private val idsToNames: Map<Int, String> by lazy {
            Class.forName("com.meshcoreone.android.core.l10n.R\$string").fields.associate { it.getInt(null) to it.name }
        }
    }
}
