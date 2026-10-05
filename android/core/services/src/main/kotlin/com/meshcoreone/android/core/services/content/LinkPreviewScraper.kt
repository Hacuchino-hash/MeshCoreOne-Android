// PortedFrom: MC1/Services/LinkPreviewService+Scrape.swift@db14559b39d32322b06477c6ae676112f583db50
// The Swift original's network leg (`scrapeHTMLMetadata`, `loadImageData`, `boundedData`)
// used a shared `URLSession` with `RedirectSafetyDelegate`. This pure-JVM port depends on a
// typed [BoundedHttpFetching] role instead of any concrete HTTP client, per the explicit
// instruction to port the scraper's algorithm now via an injected fetch role/fakes, ahead
// of the still-pending Android-compatible HTTP client (OkHttp) amendment. `UrlSafetyChecker`
// gates both the page URL and the resolved scraped-image URL exactly as the Swift original's
// two `URLSafetyChecker.isSafe` calls do; redirect-hop re-validation is the HTTP adapter's
// responsibility (mirroring `RedirectSafetyDelegate`, already ported as `RedirectSafetyPolicy`
// for the adapter to consult per-hop).
//
// NOT YET PORTED: the Swift original's `boundedDecode`/`fitToMaxSize` re-encodes the scraped
// image to a bounded-pixel-size JPEG via ImageIO/UIKit before returning it. That recompression
// is a native image-codec concern (forbidden in this pure-JVM module) and is intentionally
// deferred to a follow-up increment that composes this scraper's validated raw bytes with the
// already-ported native `ImageDecoding`/`BitmapImageDecoder` adapter. `loadImageData` here
// returns the capped, mime-validated raw bytes; it does not claim the recompression behavior.
package com.meshcoreone.android.core.services.content

/** A single fetch attempt's outcome, enough to apply the Swift original's bounded-GET checks. */
sealed interface HttpFetchAttempt {
    /**
     * A reachable response. [chunks] streams the body: each invocation of its callback
     * delivers one chunk and must return `true` to continue or `false` to stop (and let the
     * adapter close/cancel the underlying connection), mirroring the Swift original's
     * `for try await byte in bytes { ...; if overCap { return nil } }` early-exit streaming.
     */
    data class Started(
        val statusCode: Int,
        val mimeType: String?,
        /** Server-declared body size, or `null` when not declared (never trusted alone). */
        val expectedContentLength: Long?,
        val chunks: suspend (onChunk: suspend (ByteArray) -> Boolean) -> Unit,
    ) : HttpFetchAttempt

    /** Any network, TLS, timeout, or DNS-safety failure. [reason] is diagnostic only. */
    data class Failed(val reason: String) : HttpFetchAttempt
}

/**
 * Narrow producer-role port the scraper depends on instead of a concrete HTTP client.
 * A future native adapter implements this against a DNS-rebinding-safe Android-compatible
 * client (see docs/android/deviations/WP-218.md for the pending OkHttp proposal); this
 * module only depends on the shape of the port.
 */
interface BoundedHttpFetching {
    /**
     * [rangeHeader], when non-null, is sent as the HTTP `Range` request header exactly as the
     * Swift original's `probeImageDimensions`'s `bytes=0-65535` header does for a dimensions-only
     * probe GET; `null` means an ordinary unranged request (the existing scrape/image-fetch
     * call sites).
     */
    suspend fun fetch(url: String, timeoutMs: Long, rangeHeader: String? = null): HttpFetchAttempt
}

/** `og:image` / `og:title` HTML scrape fallback, and the scraped-image bounded fetch. */
class LinkPreviewScraper(private val httpFetching: BoundedHttpFetching) {
    /**
     * Fetches the page and scans its `<meta>` tags for an `og:image` / `twitter:image` hero
     * image and an `og:title`. Returns `null` on any safety, network, status, size, or mime
     * failure, or when the page carries neither hint.
     */
    suspend fun scrapeHtmlMetadata(url: String): ScrapedPageMetadata? {
        if (!UrlSafetyChecker.isSafe(url)) return null

        val data = boundedFetch(
            url = url,
            timeoutMs = HTML_SCRAPE_TIMEOUT_MS,
            byteCap = HTML_SCRAPE_BYTE_CAP,
            acceptsMime = { it.contains(HTML_MIME_SUBSTRING) },
        ) ?: return null

        val html = decodeUtf8OrLatin1(data) ?: return null
        return LinkPreviewHtmlMetadata.parseHtmlMetadata(html, url)
    }

    /**
     * Fetches the scraped `og:image` bytes, bounding the download and validating its mime
     * type. Re-validates the (possibly relative-resolved) image URL's safety independently
     * of the page URL's check, exactly as the Swift original does before its image GET.
     */
    suspend fun loadImageData(url: String): ByteArray? {
        if (!UrlSafetyChecker.isSafe(url)) return null

        return boundedFetch(
            url = url,
            timeoutMs = IMAGE_FETCH_TIMEOUT_MS,
            byteCap = IMAGE_BYTE_CAP,
            acceptsMime = { it.startsWith(IMAGE_MIME_PREFIX) },
        )
    }

    /**
     * Bounded GET shared by the HTML scrape and the scraped-image fetch: rejects on an
     * over-cap [HttpFetchAttempt.Started.expectedContentLength], a non-2xx status, or an
     * unaccepted mime type, then enforces [byteCap] while streaming so the cap holds even
     * when the server lies about, or omits, the content length.
     */
    private suspend fun boundedFetch(
        url: String,
        timeoutMs: Long,
        byteCap: Int,
        acceptsMime: (String) -> Boolean,
    ): ByteArray? {
        val attempt = httpFetching.fetch(url, timeoutMs)
        if (attempt !is HttpFetchAttempt.Started) return null
        if (attempt.statusCode !in 200..299) return null

        val mimeType = attempt.mimeType ?: return null
        if (!acceptsMime(mimeType)) return null

        attempt.expectedContentLength?.let { if (it > byteCap) return null }

        val buffer = java.io.ByteArrayOutputStream()
        var exceeded = false
        attempt.chunks { chunk ->
            if (exceeded) {
                false
            } else {
                buffer.write(chunk)
                if (buffer.size() > byteCap) {
                    exceeded = true
                    false
                } else {
                    true
                }
            }
        }
        if (exceeded) return null

        return buffer.toByteArray()
    }

    private fun decodeUtf8OrLatin1(data: ByteArray): String? = runCatching {
        String(data, Charsets.UTF_8)
    }.recoverCatching {
        String(data, Charsets.ISO_8859_1)
    }.getOrNull()

    companion object {
        /** Streaming bound for the HTML scrape GET; `og:image`/`og:title` live in `<head>`. */
        private const val HTML_SCRAPE_BYTE_CAP = 512 * 1024

        /** Pre-decode streaming ceiling for the scraped-image GET. */
        private const val IMAGE_BYTE_CAP = 2 * 1024 * 1024

        private const val HTML_SCRAPE_TIMEOUT_MS = 5_000L
        private const val IMAGE_FETCH_TIMEOUT_MS = 5_000L

        private const val IMAGE_MIME_PREFIX = "image/"
        private const val HTML_MIME_SUBSTRING = "html"
    }
}
