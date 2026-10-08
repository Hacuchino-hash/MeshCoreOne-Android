// PortedFrom: MC1/Services/RegionResolver.swift@db14559b39d32322b06477c6ae676112f583db50
// PortedFrom: MC1Services/Sources/MC1Services/Services/RegionalAreas.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.core.services.content

import com.meshcoreone.android.core.services.device.RegionalAreas

object RegionalAreaMatcher : RegionAreaMatching {
    override fun matchSubdivision(country: String, normalized: String?): String? =
        RegionalAreas.matchSubdivision(country, normalized)

    override fun matchCounty(country: String, state: String?, normalized: String?): String? =
        RegionalAreas.matchCounty(country, state, normalized)
}
