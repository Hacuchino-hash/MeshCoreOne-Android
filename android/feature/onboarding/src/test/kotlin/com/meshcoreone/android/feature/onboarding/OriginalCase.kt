// AndroidOnly: WP-305 Source-case identity carried by JUnit4 tests; receipts print only after assertions return.
package com.meshcoreone.android.feature.onboarding

import java.util.Base64
import org.junit.Rule
import org.junit.rules.TestName

/** [disposition] is `ported` (same behavior) or `adapted` (same behavior on a native seam). */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class OriginalCase(val id: String, val disposition: String = "ported")

abstract class SourceCaseProof {
    @get:Rule val testName = TestName()

    protected fun prove(assertions: () -> Unit) {
        val method = javaClass.getDeclaredMethod(testName.methodName)
        val source = requireNotNull(method.getAnnotation(OriginalCase::class.java)) { "Missing @OriginalCase" }
        assertions()
        println("WP305_CASE|${Base64.getEncoder().encodeToString(source.id.toByteArray(Charsets.UTF_8))}|${source.disposition}|${javaClass.name}#${method.name}")
    }
}
