// PortedFrom: MC1Services/Sources/MC1Services/Extensions/StatusResponse+Compatibility.swift@db14559b39d32322b06477c6ae676112f583db50
// PortedFrom: MC1Services/Sources/MC1Services/Extensions/TelemetryResponse+DataPoints.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.core.services.remote

import com.meshcoreone.android.core.protocol.event.StatusResponse

// TelemetryResponse+DataPoints: the protocol module's `TelemetryResponse.dataPoints` member already
// decodes `rawData` with `LPPDecoder.decode`, exactly as the Swift extension does, so no extension is
// declared here (a same-named extension would be shadowed by that member).

/** Uptime in seconds (compatibility alias). */
val StatusResponse.uptimeSeconds: UInt get() = uptime

/** Battery level in millivolts (Swift `UInt16(clamping:)`). */
val StatusResponse.batteryMillivolts: UShort get() = battery.coerceIn(0L, UShort.MAX_VALUE.toLong()).toUShort()

/** Last RSSI value (Swift `Int16(clamping:)`). */
val StatusResponse.lastRssi: Short get() = lastRSSI.coerceIn(Short.MIN_VALUE.toLong(), Short.MAX_VALUE.toLong()).toShort()

/** Last SNR value (compatibility conversion to `Float`). */
val StatusResponse.lastSnr: Float get() = lastSNR.toFloat()

/** Repeater RX airtime in seconds (compatibility alias). */
val StatusResponse.repeaterRxAirtimeSeconds: UInt get() = rxAirtime
