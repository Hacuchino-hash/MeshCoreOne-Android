// PortedFrom: MC1Services/Tests/MC1ServicesTests/MC1ServicesTests.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.core.ui

import com.meshcoreone.android.core.model.MC1ServicesVersion
import com.meshcoreone.android.core.protocol.command.PacketBuilder
import com.meshcoreone.android.core.protocol.event.MeshEvent
import com.meshcoreone.android.core.protocol.parser.PacketParser
import kotlin.test.*
import org.junit.Test

class ServicesBasicTest : SourceCaseProof() {
    @OriginalCase("MC1ServicesTests::Version is accessible()")
    @Test fun version() = prove { assertEquals("0.1.0", MC1ServicesVersion.VERSION) }
    @OriginalCase("MC1ServicesTests::MeshCore types are re-exported()")
    @NativeAdaptation("gradle-api-visibility")
    @Test fun protocolTypesAreAvailableThroughTheRealContractApi() = prove {
        assertEquals("MeshEvent", MeshEvent::class.simpleName)
        assertEquals("PacketBuilder", PacketBuilder::class.simpleName)
        assertEquals("PacketParser", PacketParser::class.simpleName)
    }
}
