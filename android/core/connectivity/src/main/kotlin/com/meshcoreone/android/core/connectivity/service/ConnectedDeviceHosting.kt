// AndroidOnly: WP-206 One connectedDevice foreground service held only while a live radio connection is wanted.
package com.meshcoreone.android.core.connectivity.service

import com.meshcoreone.android.core.contracts.domain.ConnectionIntent
import com.meshcoreone.android.core.contracts.domain.DeviceConnectionState

/** Why a background start may be legitimate; a companion association alone is never one. */
enum class StartExemption {
    /** The app has a visible activity. */
    Foreground,
    /** Delivered inside a CompanionDeviceService presence callback for an associated device. */
    CompanionPresenceCallback,
}

data class HostingInput(
    val intent: ConnectionIntent,
    val state: DeviceConnectionState,
    val reconnectInProgress: Boolean,
    val exemption: StartExemption?,
    val connectPermissionGranted: Boolean,
    val lanOnly: Boolean = false,
)

sealed interface HostingDecision {
    data object Hold : HostingDecision
    data object Release : HostingDecision
    /** A start is needed but no exemption covers it: retry on the next foreground or presence event. */
    data class Defer(val reason: DeferReason) : HostingDecision
}

enum class DeferReason { BackgroundStartRestricted, MissingTypePrerequisite }

sealed interface StartOutcome {
    data object Started : StartOutcome
    /** `ForegroundServiceStartNotAllowedException` (API 31+ background-start restriction). */
    data object BackgroundStartNotAllowed : StartOutcome
    /** API 34+ type check: missing `FOREGROUND_SERVICE_CONNECTED_DEVICE` or its runtime prerequisite. */
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
        if (input.exemption == null) return HostingDecision.Defer(DeferReason.BackgroundStartRestricted)
        return HostingDecision.Hold
    }
}

/** Applies hosting decisions idempotently; exactly one service instance is ever requested. */
class ConnectedDeviceHostingController(private val starter: ForegroundServiceStarter) {
    private val lock = Any()
    private var held = false
    var lastOutcome: StartOutcome? = null
        private set

    val isHeld: Boolean get() = synchronized(lock) { held }

    /** Serialized: the platform start/stop calls are non-suspending, so they run under the lock. */
    fun update(input: HostingInput): HostingDecision = synchronized(lock) {
        val decision = ConnectedDeviceHostingPolicy.decide(input, held)
        when (decision) {
            HostingDecision.Hold -> if (!held) {
                val outcome = starter.start()
                lastOutcome = outcome
                held = outcome == StartOutcome.Started
                if (!held) {
                    return@synchronized HostingDecision.Defer(
                        if (outcome is StartOutcome.TypeNotPermitted) DeferReason.MissingTypePrerequisite
                        else DeferReason.BackgroundStartRestricted,
                    )
                }
            }
            HostingDecision.Release -> if (held) { held = false; starter.stop() }
            is HostingDecision.Defer -> Unit
        }
        decision
    }

    /** The system stopped the service (user dismissed, OS kill): forget the hold without stopping again. */
    fun onServiceStopped() { synchronized(lock) { held = false } }
}
