// PortedFrom: MC1Services/Tests/MC1ServicesTests/ConnectionManagerAutoReconnectEntryTests.swift@db14559b39d32322b06477c6ae676112f583db50
// PortedFrom: MC1Services/Tests/MC1ServicesTests/ConnectionManagerDisconnectDiagnosticsTests.swift@db14559b39d32322b06477c6ae676112f583db50
// PortedFrom: MC1Services/Tests/MC1ServicesTests/ConnectionManagerReconnectAbandonmentTests.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.core.runtime

import com.meshcoreone.android.core.contracts.domain.*
import com.meshcoreone.android.core.model.TransportType
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.TestFactory
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
private suspend fun RuntimeFixture.enterAuto(details: String = "link dropped") {
    platform.state = platform.state.copy(autoReconnecting = true)
    links.last().callbacks!!.onAutoReconnecting(details)
    test.runCurrent()
}

@OptIn(ExperimentalCoroutinesApi::class)
class SourceReconnectLifecycleTest {
    @TestFactory
    fun sourceCases() = listOf(
        original("ConnectionManagerAutoReconnectEntryTests", "entry during in-flight manual connect for the same device adopts the flow") {
            withFixture {
                val sendGate = CompletableDeferred<Unit>()
                createRadio = { TestRadio().also { it.beforeSend = { frame -> if (frame[0].toInt() == 1) sendGate.await() } } }
                val connection = backgroundScope.async { runCatching { manager.connect(platform.target) } }
                runCurrent(); enterAuto()
                assertEquals(platform.target.deviceId, manager.reconnectionCoordinator.reconnectingDeviceId)
                assertNull(manager.activeConnectionAttemptDeviceId?.takeIf { manager.activeReconnectDeviceId == null })
                assertEquals(DeviceConnectionState.CONNECTING, manager.connectionState); assertEquals(0, radios.single().closes)
                sendGate.complete(Unit); runCurrent(); connection.await()
            }
        },
        original("ConnectionManagerAutoReconnectEntryTests", "entry stands down when a manual connect is in flight for a different device") {
            withFixture {
                connect(); val stale = links.first().callbacks!!
                val gate = CompletableDeferred<Unit>(); createRadio = { TestRadio().also { it.beforeConnect = { gate.await() } } }
                val other = target()
                val connection = backgroundScope.async { runCatching { manager.connect(other) } }
                runCurrent(); stale.onAutoReconnecting("stale"); runCurrent()
                assertNull(manager.reconnectionCoordinator.reconnectingDeviceId)
                assertEquals(other.deviceId, manager.activeConnectionAttemptDeviceId)
                gate.complete(Unit); runCurrent(); connection.await()
            }
        },
        original("ConnectionManagerAutoReconnectEntryTests", "entering auto-reconnect while wanting connection notifies the loss once") {
            withFixture {
                connect(); enterAuto(); runCurrent()
                assertEquals(1, autoCount); assertEquals(platform.target.deviceId, manager.activeReconnectDeviceId)
                assertTrue(order.indexOf("auto-loss") >= 0); assertEquals(1, services.single().teardowns)
            }
        },
        original("ConnectionManagerAutoReconnectEntryTests", "entering auto-reconnect after the user disconnected does not notify the loss") {
            withFixture {
                connect(); val late = links.single().callbacks!!
                manager.disconnect(); late.onAutoReconnecting("late"); runCurrent()
                assertEquals(0, autoCount); assertEquals(ConnectionIntent.UserDisconnected, manager.connectionIntent); assertEquals(1, radios.single().closes)
            }
        },
        original("ConnectionManagerAutoReconnectEntryTests", "manual-connect stand-down for a different device does not notify the loss") {
            withFixture {
                connect(); val late = links.single().callbacks!!; val other = target()
                val gate = CompletableDeferred<Unit>(); createRadio = { TestRadio().also { it.beforeConnect = { gate.await() } } }
                val work = backgroundScope.async { runCatching { manager.connect(other) } }
                runCurrent(); late.onAutoReconnecting("old radio"); runCurrent()
                assertEquals(0, autoCount); assertNull(manager.reconnectionCoordinator.reconnectingDeviceId)
                gate.complete(Unit); runCurrent(); work.await()
            }
        },
        original("ConnectionManagerAutoReconnectEntryTests", "connection loss after adoption clears the claim and arms the watchdog") {
            withFixture {
                connect(); val callbacks = links.single().callbacks!!
                enterAuto(); callbacks.onDisconnected(null); runCurrent()
                assertNull(manager.reconnectionCoordinator.reconnectingDeviceId)
                assertEquals(DeviceConnectionState.DISCONNECTED, manager.connectionState); assertTrue(manager.isReconnectionWatchdogRunning)
            }
        },
        original("ConnectionManagerDisconnectDiagnosticsTests", "auto-reconnect entry persists disconnect diagnostic with error info") {
            withFixture {
                connect(); enterAuto("domain=CBErrorDomain, code=15, desc=Failed to encrypt")
                val diagnostic = assertNotNull(last.read().diagnostic)
                assertTrue(diagnostic.contains("source=bleStateMachine.autoReconnectingHandler")); assertTrue(diagnostic.contains("code=15"))
                assertEquals(DeviceConnectionState.CONNECTING, manager.connectionState)
            }
        },
        original("ConnectionManagerDisconnectDiagnosticsTests", "auto-reconnect entry skips the claim but tears down the stale OLD session during pairing") {
            withFixture {
                connect(); manager.setPairingActivity(true, false); enterAuto()
                assertNull(manager.activeReconnectDeviceId); assertEquals(DeviceConnectionState.DISCONNECTED, manager.connectionState)
                assertNull(manager.connectedDevice); assertEquals(1, services.single().teardowns); assertEquals(0, radios.single().collectors)
                assertTrue(last.read().diagnostic!!.contains("source=handleConnectionLoss"))
            }
        },
        original("ConnectionManagerDisconnectDiagnosticsTests", "health check preserves intent and persists diagnostic when other app is connected") {
            withFixture {
                connect(); manager.disconnect(RuntimeDisconnectReason.WIFI_RECONNECT_PREP)
                platform.state = platform.state.copy(systemConnected = true); platform.adoptionSucceeds = false
                manager.checkBLEConnectionHealth()
                assertTrue(manager.connectionIntent.wantsConnection); assertTrue(manager.isReconnectionWatchdogRunning)
                assertTrue(last.read().diagnostic!!.contains("source=checkBLEConnectionHealth.otherAppConnected"))
            }
        },
        original("ConnectionManagerDisconnectDiagnosticsTests", "health check adopts system-connected last device when adoption can start") {
            withFixture {
                connect(); manager.disconnect(RuntimeDisconnectReason.WIFI_RECONNECT_PREP)
                platform.state = platform.state.copy(systemConnected = true); platform.adoptionSucceeds = true
                manager.checkBLEConnectionHealth()
                assertEquals(1, platform.adopts); assertEquals(DeviceConnectionState.CONNECTING, manager.connectionState)
                assertTrue(last.read().diagnostic!!.contains("source=checkBLEConnectionHealth.adoptSystemConnectedPeripheral"))
            }
        },
        original("ConnectionManagerDisconnectDiagnosticsTests", "manual connect adopts system-connected last device instead of throwing deviceConnectedToOtherApp") {
            withFixture {
                connect(); manager.disconnect()
                platform.state = platform.state.copy(systemConnected = true); platform.adoptionSucceeds = true
                manager.connect(platform.target, true, true)
                assertEquals(1, platform.adopts); assertTrue(manager.connectionIntent.wantsConnection)
                assertEquals(DeviceConnectionState.CONNECTING, manager.connectionState)
                assertTrue(last.read().diagnostic!!.contains("source=connect(to:).adoptSystemConnectedPeripheral"))
            }
        },
        original("ConnectionManagerReconnectAbandonmentTests", "reconnection failure preserves a live link and keeps the watchdog armed") {
            withFixture {
                connect(); platform.state = platform.state.copy(connected = true); manager.handleReconnectionFailure()
                assertEquals(0, radios.single().closes); assertEquals(DeviceConnectionState.DISCONNECTED, manager.connectionState)
                assertTrue(manager.isReconnectionWatchdogRunning); assertEquals(1, manager.consecutiveRebuildFailures)
                assertNull(manager.activeReconnectDeviceId); assertEquals(0, radios.single().collectors)
            }
        },
        original("ConnectionManagerReconnectAbandonmentTests", "reconnection failure without a live link still disconnects the transport") {
            withFixture {
                connect(); manager.handleReconnectionFailure()
                assertEquals(1, radios.single().closes); assertEquals(DeviceConnectionState.DISCONNECTED, manager.connectionState)
                assertTrue(manager.isReconnectionWatchdogRunning)
            }
        },
        original("ConnectionManagerReconnectAbandonmentTests", "reconnection failure after user disconnect severs a held link and leaves the watchdog stopped") {
            withFixture {
                connect(); platform.state = platform.state.copy(connected = true); manager.disconnect()
                manager.handleReconnectionFailure()
                assertEquals(1, radios.single().closes); assertFalse(manager.isReconnectionWatchdogRunning)
                assertEquals(0, manager.consecutiveRebuildFailures)
            }
        },
        original("ConnectionManagerReconnectAbandonmentTests", "preserve budget exhausts and severs the link") {
            withFixture {
                connect(); platform.state = platform.state.copy(connected = true); manager.startReconnectionWatchdog()
                val before = manager.reconnectionWatchdogGeneration
                repeat(3) { manager.handleReconnectionFailure(); assertEquals(0, radios.single().closes) }
                manager.handleReconnectionFailure()
                assertEquals(1, radios.single().closes); assertEquals(4, manager.consecutiveRebuildFailures)
                assertTrue(last.read().diagnostic!!.contains("preserveBudgetExhausted")); assertTrue(manager.isReconnectionWatchdogRunning)
                assertTrue(manager.reconnectionWatchdogGeneration > before)
            }
        },
        original("ConnectionManagerReconnectAbandonmentTests", "preserve with autoReconnecting alone keeps the link") {
            withFixture {
                connect(); platform.state = platform.state.copy(autoReconnecting = true); manager.handleReconnectionFailure()
                assertEquals(0, radios.single().closes); assertTrue(manager.isReconnectionWatchdogRunning)
            }
        },
        original("ConnectionManagerReconnectAbandonmentTests", "switchDevice success path refills preserve budget via named reset site") {
            withFixture {
                connect(); platform.state = platform.state.copy(connected = true)
                repeat(4) { manager.handleReconnectionFailure() }; assertEquals(4, manager.consecutiveRebuildFailures)
                platform.state = platform.state.copy(connected = false)
                manager.switchDevice(target()); assertEquals(0, manager.consecutiveRebuildFailures)
                platform.state = platform.state.copy(connected = true); manager.handleReconnectionFailure()
                assertEquals(1, manager.consecutiveRebuildFailures); assertEquals(0, radios.last().closes)
            }
        },
        original("ConnectionManagerReconnectAbandonmentTests", "resetPreserveBudgetAfterDeviceSwitch has exactly one production call site in switchDevice") {
            val root = Path.of(System.getProperty("repositoryDirectory")).resolve("android").resolve("core").resolve("runtime").resolve("src").resolve("main")
            val calls = Files.walk(root).use { paths ->
                paths.filter { it.toString().endsWith(".kt") }.flatMap { path ->
                    Files.readAllLines(path).filter { line ->
                        line.contains("resetPreserveBudgetAfterDeviceSwitch()") && !line.contains("fun resetPreserveBudgetAfterDeviceSwitch")
                    }.stream()
                }.toList()
            }
            assertEquals(1, calls.size); assertTrue(calls.single().contains("connectionState.isOperational"))
        },
        original("ConnectionManagerReconnectAbandonmentTests", "watchdog natural exit nils the task and preserve re-arms") {
            withFixture {
                connect(); manager.startReconnectionWatchdog(); runCurrent()
                val before = manager.reconnectionWatchdogGeneration
                advanceTimeBy(30_000); runCurrent()
                assertFalse(manager.isReconnectionWatchdogRunning); assertEquals(before, manager.reconnectionWatchdogGeneration)
                platform.state = platform.state.copy(connected = true); manager.handleReconnectionFailure()
                assertTrue(manager.isReconnectionWatchdogRunning); assertTrue(manager.reconnectionWatchdogGeneration > before)
                assertEquals(0, radios.single().closes)
            }
        },
        original("ConnectionManagerReconnectAbandonmentTests", "user disconnect does not advance the preserve budget") {
            withFixture { connect(); manager.disconnect(); manager.handleReconnectionFailure(); assertEquals(0, manager.consecutiveRebuildFailures) }
        },
        original("ConnectionManagerReconnectAbandonmentTests", "recordConnectionSuccess refills the preserve budget") {
            withFixture {
                connect(); platform.state = platform.state.copy(connected = true)
                repeat(3) { manager.handleReconnectionFailure() }; manager.recordConnectionSuccess()
                assertEquals(0, manager.consecutiveRebuildFailures); manager.handleReconnectionFailure(); assertEquals(0, radios.single().closes)
            }
        },
        original("ConnectionManagerReconnectAbandonmentTests", "preserve failure arms a live watchdog without cancel-restarting an existing one") {
            withFixture {
                connect(); platform.state = platform.state.copy(connected = true)
                manager.startReconnectionWatchdog(); val before = manager.reconnectionWatchdogGeneration
                manager.handleReconnectionFailure()
                assertEquals(before, manager.reconnectionWatchdogGeneration); assertTrue(manager.isReconnectionWatchdogRunning)
            }
        },
        original("ConnectionManagerReconnectAbandonmentTests", "connection-lost notification while wanting connection arms the watchdog") {
            withFixture {
                connect(); manager.disconnect(RuntimeDisconnectReason.WIFI_RECONNECT_PREP); manager.notifyConnectionLost()
                assertTrue(manager.isReconnectionWatchdogRunning)
            }
        },
        original("ConnectionManagerReconnectAbandonmentTests", "connection-lost notification after user disconnect does not arm the watchdog") {
            withFixture { connect(); manager.disconnect(); manager.notifyConnectionLost(); assertFalse(manager.isReconnectionWatchdogRunning) }
        },
        original("ConnectionManagerReconnectAbandonmentTests", "connection-lost notification on WiFi transport does not arm the BLE watchdog") {
            withFixture {
                connect(ConnectionTarget.WiFi("localhost", 5000u)); platform.state = platform.state.copy(connected = true)
                manager.handleReconnectionFailure(); manager.stopReconnectionWatchdog(); manager.notifyConnectionLost()
                assertFalse(manager.isReconnectionWatchdogRunning)
            }
        },
        original("ConnectionManagerReconnectAbandonmentTests", "forceReconnect connect for an abandoned reconnect cycle tears down the pending connect and attempts fresh") {
            withFixture {
                connect(); enterAuto(); manager.setConnectionState(DeviceConnectionState.DISCONNECTED)
                platform.registryActive = true; platform.registered = false; platform.state = platform.state.copy(autoReconnecting = false)
                assertFailsWith<ConnectionError.DeviceNotFound> { manager.connect(platform.target, false, true) }
                assertEquals(1, radios.single().closes); assertNull(manager.reconnectionCoordinator.reconnectingDeviceId)
                assertEquals(DeviceConnectionState.DISCONNECTED, manager.connectionState)
            }
        },
        original("ConnectionManagerReconnectAbandonmentTests", "forceReconnect connect while still connecting keeps deferring") {
            withFixture {
                connect(); enterAuto(); manager.connect(platform.target, false, true)
                assertEquals(0, radios.single().closes); assertEquals(platform.target.deviceId, manager.reconnectionCoordinator.reconnectingDeviceId)
                assertEquals(DeviceConnectionState.CONNECTING, manager.connectionState)
            }
        },
        original("ConnectionManagerReconnectAbandonmentTests", "non-force connect for an abandoned reconnect cycle still defers") {
            withFixture {
                connect(); enterAuto(); manager.setConnectionState(DeviceConnectionState.DISCONNECTED)
                manager.connect(platform.target)
                assertEquals(0, radios.single().closes); assertEquals(platform.target.deviceId, manager.reconnectionCoordinator.reconnectingDeviceId)
                assertEquals(DeviceConnectionState.DISCONNECTED, manager.connectionState)
            }
        },
        original("ConnectionManagerReconnectAbandonmentTests", "forceReconnect connect while a session rebuild is in flight for the device defers instead of breaking glass") {
            withFixture {
                connect(); enterAuto(); platform.state = platform.state.copy(autoReconnecting = false)
                val gate = CompletableDeferred<Unit>(); createRadio = { TestRadio().also { it.beforeSend = { frame -> if (frame[0].toInt() == 1) gate.await() } } }
                val rebuild = backgroundScope.async { manager.reconnectionCoordinator.handleReconnectionComplete(platform.target.deviceId) }
                runCurrent(); val closes = radios.sumOf { it.closes }; manager.connect(platform.target, false, true)
                assertEquals(closes, radios.sumOf { it.closes }); assertEquals(2, radios.size)
                gate.complete(Unit); runCurrent(); rebuild.await()
            }
        },
        original("ConnectionManagerReconnectAbandonmentTests", "forceReconnect connect while transport is stuck auto-reconnecting to the same device breaks glass") {
            withFixture {
                connect(); enterAuto(); manager.reconnectionCoordinator.clearReconnectingDevice()
                manager.setConnectionState(DeviceConnectionState.DISCONNECTED)
                platform.registryActive = true; platform.registered = false
                assertFailsWith<ConnectionError.DeviceNotFound> { manager.connect(platform.target, false, true) }
                assertEquals(1, radios.single().closes); assertEquals(DeviceConnectionState.DISCONNECTED, manager.connectionState)
            }
        },
        original("ConnectionManagerReconnectAbandonmentTests", "overlapping health checks on a connected missing stack rebuild once") {
            withFixture {
                connect(); platform.state = platform.state.copy(connected = true); manager.handleReconnectionFailure()
                val gate = CompletableDeferred<Unit>(); createRadio = { TestRadio().also { it.beforeSend = { frame -> if (frame[0].toInt() == 1) gate.await() } } }
                val first = backgroundScope.async { manager.checkBLEConnectionHealth() }; runCurrent()
                manager.checkBLEConnectionHealth(); assertEquals(2, radios.size)
                gate.complete(Unit); runCurrent(); first.await(); assertEquals(DeviceConnectionState.READY, manager.connectionState)
            }
        },
        original("ConnectionManagerReconnectAbandonmentTests", "coordinator cycle claim blocks health rebuild during retry gap") {
            withFixture {
                connect(); enterAuto(); platform.state = platform.state.copy(connected = true)
                manager.setConnectionState(DeviceConnectionState.DISCONNECTED)
                manager.checkBLEConnectionHealth()
                assertEquals(platform.target.deviceId, manager.activeReconnectDeviceId); assertEquals(1, radios.size)
            }
        },
    )
}
