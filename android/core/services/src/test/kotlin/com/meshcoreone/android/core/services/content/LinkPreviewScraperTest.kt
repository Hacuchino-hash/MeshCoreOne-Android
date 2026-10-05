// PortedFrom: MC1Tests/Services/LinkPreviewServiceTests.swift@db14559b39d32322b06477c6ae676112f583db50
// (scrapeHTMLMetadata/loadImageData network-leg cases only; see
// `LinkPreviewHtmlMetadataTest.kt` for the pure-parse cases.)
//
// The Swift original stubs these cases with real `URLProtocol` subclasses so the actual
// `URLSession`/`RedirectSafetyDelegate` machinery runs end-to-end. This port's `httpFetching`
// dependency is a typed role (see `LinkPreviewScraper.kt`), so these tests exercise
// `LinkPreviewScraper`'s own bounded-fetch decision logic against a fake implementation of
// that role; redirect-hop re-validation is the future HTTP adapter's own responsibility (it
// consults the already-ported `RedirectSafetyPolicy`) and is out of scope for a fake that
// never performs a real HTTP redirect.
package com.meshcoreone.android.core.services.content

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LinkPreviewScraperTest {
    private fun fetcherReturning(attempt: HttpFetchAttempt): BoundedHttpFetching =
        object : BoundedHttpFetching {
            override suspend fun fetch(url: String, timeoutMs: Long, rangeHeader: String?): HttpFetchAttempt = attempt
        }

    private fun startedWith(
        statusCode: Int = 200,
        mimeType: String? = "text/html",
        expectedContentLength: Long? = null,
        body: ByteArray,
    ): HttpFetchAttempt.Started = HttpFetchAttempt.Started(
        statusCode = statusCode,
        mimeType = mimeType,
        expectedContentLength = expectedContentLength,
        chunks = { onChunk -> if (body.isNotEmpty()) onChunk(body) },
    )

    @Test
    fun `scrapeHtmlMetadata parses og tags from a stubbed html page`() = runTest {
        val html = """<meta property="og:title" content="Stubbed page"><meta property="og:image" content="https://example.com/hero.jpg">"""
        val scraper = LinkPreviewScraper(fetcherReturning(startedWith(body = html.toByteArray())))

        val result = scraper.scrapeHtmlMetadata("https://example.com/page")

        assertEquals("Stubbed page", result?.title)
        assertEquals("https://example.com/hero.jpg", result?.imageUrl)
    }

    @Test
    fun `scrapeHtmlMetadata rejects a non-html mime type`() = runTest {
        val scraper = LinkPreviewScraper(
            fetcherReturning(startedWith(mimeType = "application/json", body = "{}".toByteArray())),
        )

        assertNull(scraper.scrapeHtmlMetadata("https://example.com/page"))
    }

    @Test
    fun `loadImageData rejects an unsafe url before any fetch`() = runTest {
        var fetchCalled = false
        val fetching = object : BoundedHttpFetching {
            override suspend fun fetch(url: String, timeoutMs: Long, rangeHeader: String?): HttpFetchAttempt {
                fetchCalled = true
                return startedWith(mimeType = "image/jpeg", body = byteArrayOf(1, 2, 3))
            }
        }
        val scraper = LinkPreviewScraper(fetching)

        val data = scraper.loadImageData("http://127.0.0.1/secret.jpg")

        assertNull(data)
        kotlin.test.assertFalse(fetchCalled, "a private-host target must be rejected before any fetch attempt")
    }

    @Test
    fun `loadImageData rejects an oversized expected content length`() = runTest {
        val scraper = LinkPreviewScraper(
            fetcherReturning(
                startedWith(mimeType = "image/jpeg", expectedContentLength = 10 * 1024 * 1024, body = byteArrayOf(1)),
            ),
        )

        assertNull(scraper.loadImageData("https://example.com/huge.jpg"))
    }

    @Test
    fun `loadImageData rejects a non-image mime type`() = runTest {
        val scraper = LinkPreviewScraper(
            fetcherReturning(startedWith(mimeType = "text/plain", body = byteArrayOf(1, 2, 3))),
        )

        assertNull(scraper.loadImageData("https://example.com/not-an-image.jpg"))
    }

    @Test
    fun `loadImageData rejects a stream that exceeds the byte cap while streaming`() = runTest {
        val overCap = ByteArray(3 * 1024 * 1024) // over the 2MB image cap, with no expectedContentLength declared
        val scraper = LinkPreviewScraper(
            fetcherReturning(startedWith(mimeType = "image/jpeg", body = overCap)),
        )

        assertNull(scraper.loadImageData("https://example.com/huge-undeclared.jpg"))
    }

    @Test
    fun `loadImageData returns the capped bytes for a valid image fetch`() = runTest {
        val bytes = byteArrayOf(0x42, 0x4d, 1, 2, 3) // arbitrary small payload; decode is a separate native adapter concern
        val scraper = LinkPreviewScraper(
            fetcherReturning(startedWith(mimeType = "image/jpeg", body = bytes)),
        )

        val data = scraper.loadImageData("https://example.com/photo.jpg")

        assertEquals(bytes.toList(), data?.toList())
    }

    @Test
    fun `boundedFetch rejects a non-2xx status`() = runTest {
        val scraper = LinkPreviewScraper(
            fetcherReturning(startedWith(statusCode = 404, body = byteArrayOf(1))),
        )

        assertNull(scraper.scrapeHtmlMetadata("https://example.com/missing"))
    }

    @Test
    fun `boundedFetch rejects a failed fetch attempt`() = runTest {
        val scraper = LinkPreviewScraper(fetcherReturning(HttpFetchAttempt.Failed("connection refused")))

        assertNull(scraper.scrapeHtmlMetadata("https://example.com/page"))
    }
}
