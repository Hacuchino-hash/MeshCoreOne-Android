// AndroidOnly: WP-405 Test-to-source case traceability.
package com.meshcoreone.android.app.deeplinks

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class SourceCases(vararg val value: String)
