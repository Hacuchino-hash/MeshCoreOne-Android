// AndroidOnly: WP-206 One connectedDevice foreground service held only while a live radio connection is wanted.
package com.meshcoreone.android.core.connectivity.service

import com.meshcoreone.android.core.contracts.domain.ConnectionIntent
import com.meshcoreone.android.core.contracts.domain.DeviceConnectionState

data class HostingInput(
    val intent: ConnectionIntent,
    val state: DeviceConnectionState,
    val reconnectInProgress: Boolean,
    /**
     * Only a visible app may start the service. A companion association or presence callback is not a
     * start exemption without `REQUEST_COMPANION_START_FOREGROUND_SERVICES_FROM_BACKGROUND`, which this
     * module deliberately does not declare (WP-206 A-05): background starts are deferred.
     */
    val appInForeground: Boolean,
    val connectPermissionGranted: Boolean,
    val lanOnly: Boolean = false,
)

sealed interface HostingDecision {
    data object Hold : HostingDecision
    data object Release : HostingDecision
    /** A start is needed but not allowed now: retry on the next foreground. */
    data class Defer(val reason: DeferReason) : HostingDecision
}

enum class DeferReason { BackgroundStartRestricted, MissingTypePrerequisite }

sealed interface StartOutcome {
    /** The start request was accepted; the service reports `startForeground` success or failure later. */
    data object Started : StartOutcome
    /** `ForegroundServiceStartNotAllowedException` (API 31+ background-start restriction). */
    data object BackgroundStartNotAllowed : StartOutcome
    /**
     * API 34+ type check: `startForeground` inside the service threw (missing
     * `FOREGROUND_SERVICE_CONNECTED_DEVICE` or its runtime prerequisite). Reported asynchronously.
     */
    data class TypeNotPermitted(val cause: Throwable) : StartOutcome
    data class Failed(val cause: Throwable) : StartOutcome
}

/** Platform start/stop of the single connected-device service. */
interface ForegroundServiceStarter {
    fun start(): StartOutcome
    fun stop()
}

object ConnectedDeviceHostingPolicy {
    /**
     * Hold the service while the user wants a connection and a link is live or being re-established.
     * A disconnected, idle intent releases it: background reconnect is driven by presence callbacks,
     * not by keeping an empty foreground service alive.
     */
    fun decide(input: HostingInput, currentlyHeld: Boolean): HostingDecision {
        val active = input.intent.wantsConnection &&
            (input.state != DeviceConnectionState.DISCONNECTED || input.reconnectInProgress)
        if (!active) return HostingDecision.Release
        if (currentlyHeld) return HostingDecision.Hold
        // connectedDevice type prerequisite: BLUETOOTH_CONNECT for BLE; CHANGE_NETWORK_STATE (install-time) for LAN.
        if (!input.lanOnly && !input.connectPermissionGranted) return HostingDecision.Defer(DeferReason.MissingTypePrerequisite)
        if (!input.appInForeground) return HostingDecision.Defer(DeferReason.BackgroundStartRestricted)
        return HostingDecision.Hold
    }
}

/**
 * Applies hosting decisions; exactly one service instance is ever requested. A service is never
 * stopped before it has called `startForeground` (stopping a `startForegroundService` service
 * earlier crashes the app): a release that arrives while starting is applied when the service
 * reports it started.
 */
class ConnectedDeviceHostingController(private val starter: ForegroundServiceStarter) {
    private enum class Phase { Idle, Starting, Running, StopPending }

    private val lock = Any()
    private var phase = Phase.Idle
    var lastOutcome: StartOutcome? = null
        private set

    val isHeld: Boolean get() = synchronized(lock) { phase == Phase.Starting || phase == Phase.Running }
    val isStopPending: Boolean get() = synchronized(lock) { phase == Phase.StopPending }

    /** Serialized: the platform start/stop calls are non-suspending, so they run under the lock. */
    fun update(input: HostingInput): HostingDecision = synchronized(lock) {
        val held = phase == Phase.Starting || phase == Phase.Running
        val decision = ConnectedDeviceHostingPolicy.decide(input, held || phase == Phase.StopPending)
        when (decision) {
            HostingDecision.Hold -> when (phase) {
                Phase.Idle -> {
                    val outcome = starter.start()
                    lastOutcome = outcome
                    if (outcome != StartOutcome.Started) {
                        return@synchronized HostingDecision.Defer(DeferReason.BackgroundStartRestricted)
                    }
                    phase = Phase.Starting
                }
                Phase.StopPending -> phase = Phase.Starting
                Phase.Starting, Phase.Running -> Unit
            }
            HostingDecision.Release -> when (phase) {
                Phase.Running -> { phase = Phase.Idle; starter.stop() }
                Phase.Starting -> phase = Phase.StopPending
                Phase.Idle, Phase.StopPending -> Unit
            }
            is HostingDecision.Defer -> Unit
        }
        decision
    }

    /** The service called `startForeground`; apply a release that arrived meanwhile. */
    fun onServiceStarted() = synchronized(lock) {
        when (phase) {
            Phase.Starting -> phase = Phase.Running
            Phase.StopPending -> { phase = Phase.Idle; starter.stop() }
            Phase.Idle, Phase.Running -> Unit
        }
    }

    /** `startForeground` threw inside the service, which stopped itself: the hold is gone. */
    fun onServiceStartFailed(cause: Throwable) = synchronized(lock) {
        phase = Phase.Idle
        lastOutcome = if (cause is SecurityException) StartOutcome.TypeNotPermitted(cause) else StartOutcome.Failed(cause)
    }

    /** The system stopped the service (user dismissed, OS kill): forget the hold without stopping again. */
    fun onServiceStopped() { synchronized(lock) { phase = Phase.Idle } }
}
