// AndroidOnly: WP-304 Source identities/parameter receipts are emitted only after real assertions return.
package com.meshcoreone.android.core.ui

import java.util.Base64
import org.junit.Rule
import org.junit.rules.TestName

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class OriginalCase(val id: String, val scenarios: Int = 1)

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class ProducerBindingPending(val owner: String)

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class NativeAdaptation(val reason: String)

abstract class SourceCaseProof {
    @get:Rule val testName = TestName()

    protected fun prove(scenarios: Int = 1, assertions: () -> Unit) {
        val methodName = testName.methodName.replace(Regex("\\[\\d+\\]$"), "")
        val method = javaClass.getDeclaredMethod(methodName)
        val source = requireNotNull(method.getAnnotation(OriginalCase::class.java))
        check(source.scenarios == scenarios) { "Source parameter family was not fully exercised" }
        assertions()
        val binding = method.getAnnotation(ProducerBindingPending::class.java)?.let { "pending-" + it.owner }
            ?: method.getAnnotation(NativeAdaptation::class.java)?.let { "native-adaptation-" + it.reason }
            ?: "native"
        val prefix = if (binding.startsWith("pending-")) "WP304_POLICY_CASE" else "WP304_CASE"
        println("$prefix|${Base64.getEncoder().encodeToString(source.id.toByteArray(Charsets.UTF_8))}|$scenarios|$binding|${javaClass.name}#$methodName")
    }
}
