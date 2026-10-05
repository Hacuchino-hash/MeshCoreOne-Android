// AndroidOnly: WP-211 Executed JUnit display identities bind each frozen original family and parameter row.
package com.meshcoreone.android.core.services.device

import org.junit.jupiter.api.DynamicTest

internal fun original(
    suite: String, name: String, signature: String = "()", assertions: () -> Unit,
): DynamicTest = DynamicTest.dynamicTest("$suite::$name$signature", assertions)

internal fun nativeCase(name: String, assertions: () -> Unit): DynamicTest =
    DynamicTest.dynamicTest("WP-211::$name", assertions)
