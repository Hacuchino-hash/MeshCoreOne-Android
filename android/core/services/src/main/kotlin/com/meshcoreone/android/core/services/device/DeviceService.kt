// PortedFrom: MC1Services/Sources/MC1Services/Services/DeviceService.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.core.services.device

import com.meshcoreone.android.core.contracts.domain.DevicePersisting
import com.meshcoreone.android.core.contracts.domain.PersistenceStoreError
import com.meshcoreone.android.core.contracts.domain.PersistenceStoreException
import com.meshcoreone.android.core.contracts.domain.SessionEvent
import com.meshcoreone.android.core.contracts.domain.errors.DeviceServiceError
import com.meshcoreone.android.core.contracts.domain.errors.DeviceServiceException
import com.meshcoreone.android.core.model.DeviceDTO
import java.util.UUID
import java.util.logging.Logger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DeviceService(
    private val dataStore: DevicePersisting,
    private val context: DeviceSettingsContext,
) {
    private val operationLock = Mutex()
    private val callbackLock = Any()
    private var onDeviceUpdated: (suspend (SessionEvent<DeviceDTO>) -> Unit)? = null

    init {
        context.onClose { synchronized(callbackLock) { onDeviceUpdated = null } }
    }

    fun setDeviceUpdateCallback(callback: suspend (SessionEvent<DeviceDTO>) -> Unit) {
        context.requireCurrent()
        synchronized(callbackLock) { onDeviceUpdated = callback }
    }

    fun clearDeviceUpdateCallback() {
        synchronized(callbackLock) { onDeviceUpdated = null }
    }

    suspend fun updateOCVSettings(deviceID: UUID, preset: String, customArray: String?) = context.operation {
        val (updated, callback) = operationLock.withLock {
            context.requireCurrent()
            val current = dataStore.fetchDevice(deviceID)
                ?: throw DeviceServiceException(DeviceServiceError.DeviceNotFound)
            context.requireCurrent()
            val updated = current.copy(ocvPreset = preset, customOCVArrayString = customArray)
            try {
                dataStore.saveDevice(updated)
            } catch (failure: PersistenceStoreException) {
                val reason = when (val error = failure.error) {
                    is PersistenceStoreError.SaveFailed -> error.reason
                    is PersistenceStoreError.FetchFailed -> error.reason
                    else -> requireNotNull(failure.message)
                }
                Logger.getLogger("MeshCore.DeviceService").warning("Device settings persistence save failed")
                throw DeviceServiceException(DeviceServiceError.PersistenceFailed(reason), failure)
            }
            context.requireCurrent()
            updated to synchronized(callbackLock) { onDeviceUpdated }
        }
        context.requireCurrent()
        callback?.invoke(SessionEvent(context.token, updated))
    }
}
