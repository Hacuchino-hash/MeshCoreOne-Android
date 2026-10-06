// AndroidOnly: WP-213 Sendable URL value standing in for Foundation URL inside rendering models.
package com.meshcoreone.android.core.services.rendering

import java.net.URI
import java.net.URISyntaxException

/**
 * Immutable absolute-or-relative URL string with Foundation `URL` equality (the string itself; no
 * case folding of scheme/host as `java.net.URI.equals` would do).
 *
 * [parse] stands in for `URL(string:)`: the empty string and strings `java.net.URI` rejects return
 * null; accepted strings are kept verbatim. Since iOS 17, `URL(string:)` instead repairs some invalid
 * input (percent-encoding spaces, IDNA hosts); that difference is recorded in the WP-213 deviations.
 */
@JvmInline
value class WebURL private constructor(val absoluteString: String) {
    override fun toString(): String = absoluteString

    companion object {
        /** RFC-defined sentinel used for failed inline images without a real source URL. */
        val BLANK: WebURL = WebURL("about:blank")

        fun parse(string: String): WebURL? {
            if (string.isEmpty()) return null
            return try {
                URI(string)
                WebURL(string)
            } catch (_: URISyntaxException) {
                null
            }
        }
    }
}
