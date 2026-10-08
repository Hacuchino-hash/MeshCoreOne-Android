// PortedFrom: MC1Services/Sources/MC1Services/Models/Rendering/PreviewLoadState.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.core.services.rendering

/** State of link preview loading for a message. */
enum class PreviewLoadState { IDLE, LOADING, LOADED, NO_PREVIEW, DISABLED, MALWARE_WARNING }
