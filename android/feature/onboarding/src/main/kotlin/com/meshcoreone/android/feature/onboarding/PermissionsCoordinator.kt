// PortedFrom: MC1/Views/Onboarding/PermissionsCoordinator.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.feature.onboarding

import kotlinx.coroutines.flow.StateFlow

enum class PermissionCardStatus { GRANTED, DENIED, REQUESTABLE }

fun permissionCardStatus(status: PermissionStatus): PermissionCardStatus = when (status) {
    PermissionStatus.GRANTED -> PermissionCardStatus.GRANTED
    PermissionStatus.DENIED -> PermissionCardStatus.DENIED
    PermissionStatus.NOT_DETERMINED -> PermissionCardStatus.REQUESTABLE
}

/**
 * View-scoped permission state for the permissions step. Notifications and location are
 * optional: nothing here ever gates mesh messaging, so "continue" is always available.
 * Bluetooth is requested just in time by the pair step, not on this screen.
 */
class PermissionsCoordinator(private val port: OnboardingPermissionPort) {
    val snapshot: StateFlow<OnboardingPermissionSnapshot> get() = port.snapshot
    val canContinue: Boolean get() = true

    fun refresh() = port.refresh()

    fun cardStatus(kind: OnboardingPermissionKind, snapshot: OnboardingPermissionSnapshot): PermissionCardStatus =
        permissionCardStatus(snapshot.status(kind))

    companion object {
        val CARDS = listOf(OnboardingPermissionKind.NOTIFICATIONS, OnboardingPermissionKind.LOCATION)
    }
}
