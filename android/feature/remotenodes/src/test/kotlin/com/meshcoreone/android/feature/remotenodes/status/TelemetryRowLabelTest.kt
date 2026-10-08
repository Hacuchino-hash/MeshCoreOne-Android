// PortedFrom: MC1Tests/Views/RemoteNodes/TelemetryRowLabelTests.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.feature.remotenodes.status

import com.meshcoreone.android.core.l10n.generated.AppRemoteNodesStrings
import com.meshcoreone.android.core.protocol.lpp.LPPDataPoint
import com.meshcoreone.android.core.protocol.lpp.LPPSensorType
import com.meshcoreone.android.core.protocol.lpp.LPPValue
import com.meshcoreone.android.feature.remotenodes.support.OriginalCase
import kotlin.test.assertEquals
import org.junit.Test

class TelemetryRowLabelTest {
    @Test
    @OriginalCase("TelemetryRowLabelTests::equal temperatures on one channel still label the second as MCU()")
    fun `equal temperatures on one channel still label the second as MCU`() {
        val ambient = LPPDataPoint(1u, LPPSensorType.TEMPERATURE, LPPValue.Float(21.0))
        val mcu = LPPDataPoint(1u, LPPSensorType.TEMPERATURE, LPPValue.Float(21.0))
        val points = listOf(ambient, mcu)
        assertEquals(AppRemoteNodesStrings.remoteNodesStatusSensorTemperature, telemetryLabel(ambient, 0, points))
        assertEquals(AppRemoteNodesStrings.remoteNodesStatusSensorMcuTemperature, telemetryLabel(mcu, 1, points))
    }
}
