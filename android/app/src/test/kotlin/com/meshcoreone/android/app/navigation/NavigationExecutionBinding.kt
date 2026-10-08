// AndroidOnly: WP-302 Fail closed unless each native assertion carries the actual pre-test candidate/input binding.
package com.meshcoreone.android.app.navigation

import java.io.File
import java.util.Properties

internal fun emitNavigationExecutionBinding(className: String, methodName: String, sdk: String) {
    val file = File(requireNotNull(System.getProperty("navigationInputBinding")) {
        "App verification must declare the actual pre-test navigationInputBinding"
    })
    val binding = Properties().apply { file.inputStream().use { load(it) } }
    val fields = listOf("nonce", "head", "tree", "inputs_sha256").map {
        requireNotNull(binding.getProperty(it)) { "Missing pre-test binding field: $it" }
    }
    check((fields + listOf(className, methodName, sdk)).none { '|' in it || '\n' in it || '\r' in it })
    println("WP302_EXECUTION|${fields.joinToString("|")}|$className|$methodName|$sdk")
}
