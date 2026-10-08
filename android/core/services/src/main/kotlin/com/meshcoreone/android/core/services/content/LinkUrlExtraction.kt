// PortedFrom: MC1/Services/LinkPreviewService.swift@db14559b39d32322b06477c6ae676112f583db50
// Only the pure text-scanning portion of `LinkPreviewService` (URL extraction, the
// meshcore-open `g:{id}` Giphy short-code, and mention-range exclusion) is ported here.
// The network-fetching portion (`fetchMetadata`, LinkPresentation) has no Android
// equivalent and is a separate, still-pending native-adapter decision; the HTML
// `og:image` scrape fallback is ported in `LinkPreviewHtmlMetadata.kt`/`LinkPreviewScraper.kt`.
//
// The Swift original detects links via `NSDataDetector(types: .link)` and then filters to
// http/https schemes. There is no JVM equivalent of `NSDataDetector`'s heuristic link
// detector (which also recognizes bare domains), so this port uses an explicit
// `https?://` regex instead: every original test case exercises an explicit scheme, and a
// scheme-less "bare domain" case is not part of this port's 154-item scope. If bare-domain
// detection is later required, that is a new, explicitly scoped behavior addition, not a
// silent gap in this port.
//
// The mention-range exclusion depends only on `MentionUtilities.mentionPattern`'s literal
// regex string (`@\[([^\]]+)\]`), not the full `MentionUtilities` type (a separate,
// out-of-scope composer/contacts module not yet ported). Inlining the pattern avoids an
// undeclared cross-module dependency on code this WP does not own.
package com.meshcoreone.android.core.services.content

/** Pure text-scanning helpers extracted from `LinkPreviewService`. */
object LinkUrlExtraction {
    private val giphyShortCodePattern = Regex("^g:([A-Za-z0-9_-]+)$")
    private val mentionPattern = Regex("""@\[([^\]]+)\]""")
    private val httpUrlPattern = Regex("""https?://\S+""")

    /**
     * Extracts a Giphy GIF URL from meshcore-open `g:{id}` message format. The entire
     * (whitespace-trimmed) text must match, mirroring the Swift original's `wholeMatch`.
     */
    fun extractGiphyGifUrl(text: String): String? {
        val trimmed = text.trim()
        val match = giphyShortCodePattern.matchEntire(trimmed) ?: return null
        return "https://media.giphy.com/media/${match.groupValues[1]}/giphy.gif"
    }

    /** The first HTTP(S) URL in [text], excluding URLs inside `@[mention]` ranges, or `null`. */
    fun extractFirstUrl(text: String): String? = extractAllUrls(text).firstOrNull()

    /**
     * Every HTTP(S) URL in [text], in document order. A meshcore-open `g:{id}` short-code
     * that is the entire message is expanded and returned as the sole result, without a
     * detector pass (matching the Swift original, which checks the Giphy format first and
     * returns immediately when it matches).
     */
    fun extractAllUrls(text: String): List<String> {
        if (text.isEmpty()) return emptyList()

        extractGiphyGifUrl(text)?.let { return listOf(it) }

        val mentionRanges = mentionPattern.findAll(text).map { it.range }.toList()

        return httpUrlPattern.findAll(text)
            .filter { match -> mentionRanges.none { it.overlaps(match.range) } }
            .map { it.value }
            .toList()
    }

    private fun IntRange.overlaps(other: IntRange): Boolean =
        first <= other.last && other.first <= last
}
