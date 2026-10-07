"""AndroidOnly: WP-218 Explicit original-family/native-test bindings, not parity approval."""

CLASSES = {
    "LinkPreviewPreferencesTests": "LinkPreviewPreferencesTest",
    "DecodedPreviewCacheTests": "DecodedPreviewCacheTest",
    "ElevationServiceTests.OptimalSampleCountTests": "ElevationServiceTest",
    "ElevationServiceTests.SampleCoordinatesTests": "ElevationServiceTest",
    "ElevationServiceTests.ErrorTests": "ElevationServiceTest",
    "ElevationServiceTests.DistanceIntegrationTests": "ElevationServiceTest",
    "ImageHeaderDecoderTests": "ImageHeaderDecoderTest",
    "ImageURLDetectorTests": "ImageUrlClassifierTest",
    "InlineImageCacheTests": "InlineImageCacheTest",
    "InlineImagePrefetcherTests": "InlineImagePrefetcherTest",
    "LinkPreviewCacheTests": "LinkPreviewCacheTest",
    "LinkPreviewServiceTests": "LinkUrlExtractionTest",
    "RegionResolverTests": "RegionResolverTest",
    "MalwareDomainFilterTests": "MalwareDomainFilterTest",
    "URLSafetyCheckerTests": "UrlSafetyCheckerTest",
}

# Missing behavior remains a blocking original case; unrelated typed/fake tests are not substitutes.
BLOCKED = {
    ("ElevationServiceTests.ErrorTests", "networkError has descriptive message"):
        "The localized Network error presentation/underlying message is not implemented.",
    ("ElevationServiceTests.ErrorTests", "invalidResponse has descriptive message"):
        "The localized Invalid response from elevation API presentation is not implemented.",
    ("ElevationServiceTests.ErrorTests", "apiError includes message"):
        "The localized API error presentation/associated message is not implemented.",
    ("ElevationServiceTests.ErrorTests", "noData has descriptive message"):
        "The localized No elevation data returned presentation is not implemented.",
    ("LinkPreviewServiceTests", "loadImageData rejects a redirect to a private host"):
        "Requires the real DNS-safe native HTTP redirect consumer, not an initial-URL rejection fake.",
}

ALIASES = {}


def aliases(scope, native_class, rows, module="services"):
    for line in rows.strip().splitlines():
        original, native = line.split("|")
        key = (scope, original)
        if key in ALIASES:
            raise ValueError("Duplicate original content binding: " + repr(key))
        ALIASES[key] = (module, native_class, native)


aliases("LinkPreviewPreferencesTests", "LinkPreviewPreferencesTest", """
Has expected defaults: previews off, auto-resolve on|Has expected defaults, previews off, auto-resolve on
""")
aliases("DecodedPreviewCacheTests", "DecodedPreviewCacheTest", """
Returns nil for an unseen URL|Returns null for a URL never stored
Strips raw image and icon bytes from the cached DTO|Stores raw source bytes but strips them from the retained dto
clear() empties the cache|clear empties the cache
""")
aliases("DecodedPreviewCacheTests", "BitmapImageDecoderTest", """
Cost reflects decoded pixel bytes|decoded preview cost reflects real Bitmap byte cost
""", module="app")
aliases("ElevationServiceTests.OptimalSampleCountTests", "ElevationServiceTest", """
Returns 20 samples for distances under 1km|optimalSampleCount returns 20 samples for distances under 1km
Returns 50 samples for distances 1-5km|optimalSampleCount returns 50 samples for distances 1-5km
Returns 80 samples for distances 5-20km|optimalSampleCount returns 80 samples for distances 5-20km
Returns 100 samples for distances over 20km|optimalSampleCount returns 100 samples for distances over 20km
Sample count never exceeds 100|optimalSampleCount never exceeds 100
""")
aliases("ElevationServiceTests.SampleCoordinatesTests", "ElevationServiceTest", """
First coordinate equals pointA|sampleCoordinates first coordinate equals pointA
Last coordinate equals pointB|sampleCoordinates last coordinate equals pointB
Returns correct number of samples|sampleCoordinates returns correct number of samples
Sample count clamped to minimum of 2|sampleCoordinates count clamped to minimum of 2
Sample count clamped to maximum of 100|sampleCoordinates count clamped to maximum of 100
Coordinates are evenly distributed|sampleCoordinates are evenly distributed
Identical points return same coordinate repeated|sampleCoordinates identical points return same coordinate repeated
""")
aliases("ImageHeaderDecoderTests", "ImageHeaderDecoderTest", """
decodeDimensions returns nil for non-image bytes|decodeDimensions returns null for non-image bytes
decodeDimensions returns nil for empty data|decodeDimensions returns null for empty data
""")
aliases("ImageURLDetectorTests", "ImageUrlClassifierTest", """
Detects common image extensions|detects common image extensions
Rejects non-image extensions|rejects non-image extensions
Case insensitive extension detection|case insensitive extension detection
Handles URLs with query parameters|handles URLs with query parameters
Rejects URL with no extension|rejects URL with no extension
Rejects empty path|rejects empty path
Resolves giphy.com/gifs/slug-text-ID|resolves giphy-com-gifs-slug-text-ID
Resolves giphy.com/gifs/ID (no slug)|resolves giphy-com-gifs-ID no slug
Resolves giphy.com/embed/ID|resolves giphy-com-embed-ID
Recognizes media.giphy.com as direct image URL|recognizes media-giphy-com as direct image URL
Recognizes i.giphy.com as direct image URL|recognizes i-giphy-com as direct image URL
Returns nil for non-Giphy URLs|returns null for non-Giphy URLs
Returns nil for Giphy URLs without valid path|returns null for Giphy URLs without valid path
Resolves www.giphy.com URLs|resolves www-giphy-com URLs
isImageURL returns true for direct image URLs|isImageUrl returns true for direct image URLs
isImageURL returns true for resolvable Giphy URLs|isImageUrl returns true for resolvable Giphy URLs
isImageURL returns false for non-image URLs|isImageUrl returns false for non-image URLs
directImageURL returns self for direct images|directImageUrl returns self for direct images
directImageURL resolves Giphy URLs|directImageUrl resolves Giphy URLs
""")
aliases("ImageURLDetectorTests", "ImageHeaderDecoderTest", """
Detects GIF87a magic bytes|isGifData detects GIF87a magic bytes
Detects GIF89a magic bytes|isGifData detects GIF89a magic bytes
Rejects non-GIF data|isGifData rejects non-GIF data
Rejects data shorter than 4 bytes|isGifData rejects data shorter than 4 bytes
Rejects empty data|isGifData rejects empty data
""")
aliases("ImageURLDetectorTests", "BitmapImageDecoderTest", """
downsampledImage honors maxPixelSize|downsampledImage honors maxPixelSize
""", module="app")
aliases("InlineImageCacheTests", "InlineImageCacheTest", """
Probe returns nil for a private-IP host|probeImageDimensions returns null for a private-IP host
Probe returns nil for a non-HTTP scheme|probeImageDimensions returns null for a non-HTTP scheme
An image URL that serves HTML returns notImage and stays retryable|fetchImageData reroutes to notImage for an html-serving image-extension url, repeatably
An oversized HTML page still returns notImage instead of tripping the size guard|fetchImageData reroutes an oversized html page to notImage before the size guard trips
Decoded cache returns nil for an unseen URL|decoded returns null for an unseen url
Decoded cache round-trips a stored entry with raw bytes|decoded round-trips a stored entry with raw bytes
Decoded cache preserves the GIF flag and omits bytes|decoded preserves the gif flag and omits bytes
Re-storing the same key replaces the entry without growing the cache|re-storing the same key replaces the entry without growing the cache
Decoded cost reflects pixel size plus raw bytes|cost reflects decoded handle cost plus raw bytes
""")
aliases("InlineImagePrefetcherTests", "InlineImagePrefetcherTest", """
Text with no URLs returns immediately without probes or previews|text with no URLs returns immediately without probes or previews
Direct image suffix invokes the dimension probe path|direct image suffix invokes the dimension probe path
Image probes are skipped when disallowed but card URLs still resolve|image probes are skipped when disallowed but card URLs still resolve
Multiple URLs fan out across both classifier paths|multiple URLs fan out across both classifier paths
Direct image probe is skipped when dimensions are already cached|direct image probe is skipped when dimensions are already cached
Mixed direct image and link preview URLs both invoke their paths|mixed direct image and link preview URLs both invoke their paths
""")
aliases("LinkPreviewCacheTests", "LinkPreviewCacheTest", """
isFetching returns true while fetch is in progress|isFetching returns false when no fetch is in progress
Concurrent fetches for the same URL coalesce; every caller receives the loaded result|Concurrent fetches for the same URL coalesce, every caller receives the loaded result
""")
aliases("LinkPreviewServiceTests", "LinkUrlExtractionTest", """
Extracts HTTPS URL from text|extracts https url from text
Extracts HTTP URL from text|extracts http url from text
Returns nil for text without URLs|returns null for text without urls
Extracts first URL when multiple URLs present|extracts first url when multiple urls present
Ignores non-HTTP schemes like tel: and mailto:|ignores non-http schemes like tel and mailto
Extracts URL with path and query string|extracts url with path and query string
Extracts URL at beginning of text|extracts url at beginning of text
Extracts URL at end of text|extracts url at end of text
Returns nil for empty text|returns null for empty text
Handles URL with fragment|handles url with fragment
Ignores URL-like text within mention brackets|ignores url-like text within mention brackets
Ignores domain-like text within mention brackets|ignores domain-like text within mention brackets
Extracts real URL when mention also contains URL-like text|extracts real url when mention also contains url-like text
Extracts URL when no mentions present|extracts url when no mentions present
Returns nil when only URL-like text in mention|returns null when only url-like text in mention
Extracts Giphy URL from g: prefix message|extracts giphy url from g prefix message
Extracts Giphy URL from g: with whitespace|extracts giphy url from g with whitespace
Handles g: with hyphens and underscores in ID|handles g with hyphens and underscores in id
Returns nil for g: with no ID|returns null for g with no id
Does not match g: embedded in longer text|does not match g embedded in longer text
Does not match g: with invalid characters in ID|does not match g with invalid characters in id
extractGiphyGIFURL returns nil for plain text|extractGiphyGifUrl returns null for plain text
extractGiphyGIFURL returns nil for regular URL|extractGiphyGifUrl returns null for regular url
""")
aliases("LinkPreviewServiceTests", "LinkPreviewHtmlMetadataTest", """
Parses a pasteboard-style head for its og image|parses a pasteboard-style head for its og image
Prefers og image over twitter image when both are present|prefers og image over twitter image when both are present
Handles content attribute before property attribute|handles content attribute before property attribute
Falls back to twitter image when no og image is present|falls back to twitter image when no og image is present
Resolves a relative og image URL against the page URL|resolves a relative og image url against the page url
Unescapes HTML entities in a multi param og image URL|unescapes html entities in a multi param og image url
Returns nil when the page has no Open Graph or Twitter Card tags|returns null when the page has no open graph or twitter card tags
Extracts the og title alongside the og image|extracts the og title alongside the og image
Returns a title-only result when the page has og title but no image|returns a title-only result when the page has og title but no image
Drops a non-HTTP og image URL|drops a non-http og image url
""")
aliases("LinkPreviewServiceTests", "LinkPreviewScraperTest", """
scrapeHTMLMetadata parses og tags from a stubbed HTML page|scrapeHtmlMetadata parses og tags from a stubbed html page
scrapeHTMLMetadata rejects a non-HTML mime type|scrapeHtmlMetadata rejects a non-html mime type
loadImageData rejects an oversized expected content length|loadImageData rejects an oversized expected content length
loadImageData rejects a non image mime type|loadImageData rejects a non-image mime type
""")
aliases("LinkPreviewServiceTests", "BitmapImageDecoderTest", """
loadImageData returns decoded data for a valid image|loadImageData returns decoded data for a valid image
""", module="app")
aliases("RegionResolverTests", "RegionResolverTest", """
nil isoCountryCode \u2192 nil|nil country code resolves to null
Unauthorized location \u2192 nil|unauthorized location resolves to null
""")
aliases("URLSafetyCheckerTests", "UrlSafetyCheckerTest", """
Allows media.giphy.com|Allows media giphy com
Allows i.giphy.com|Allows i giphy com
Detects loopback 127.0.0.1|Detects loopback 127 0 0 1
Detects loopback 127.255.255.255|Detects loopback 127 255 255 255
Detects 10.0.0.0/8|Detects 10_0_0_0 slash8
Detects 172.16.0.0/12|Detects 172_16_0_0 slash12
Detects 192.168.0.0/16|Detects 192_168_0_0 slash16
Detects link-local 169.254.0.0/16|Detects link-local 169_254_0_0 slash16
Detects 0.0.0.0|Detects 0_0_0_0
Detects multicast 224.0.0.0/4|Detects multicast 224_0_0_0 slash4
Detects reserved 240.0.0.0/4|Detects reserved 240_0_0_0 slash4
Detects IPv6 loopback ::1|Detects IPv6 loopback colon colon1
Detects IPv6 unspecified ::|Detects IPv6 unspecified colon colon
Detects IPv6 link-local fe80::|Detects IPv6 link-local fe80 colon colon
Detects IPv6 unique local fc00::/7|Detects IPv6 unique local fc00 colon colon slash7
Detects IPv6 multicast ff00::/8|Detects IPv6 multicast ff00 colon colon slash8
""")


def target(case):
    key = (case["scope"], case["method"])
    if key in BLOCKED:
        return None, BLOCKED[key]
    if key in ALIASES:
        return ALIASES[key], None
    if case["scope"] not in CLASSES:
        return None, "No explicit native class binding for the original scope."
    return ("services", CLASSES[case["scope"]], case["method"]), None
