// PortedFrom: MC1/Services/LinkPreviewService.swift@db14559b39d32322b06477c6ae676112f583db50
// PortedFrom: MC1/Services/LinkPreviewService+Scrape.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.core.services.content

fun interface PreviewImageProcessing {
    suspend fun boundedPreviewImage(data: ByteArray): ByteArray?
}
