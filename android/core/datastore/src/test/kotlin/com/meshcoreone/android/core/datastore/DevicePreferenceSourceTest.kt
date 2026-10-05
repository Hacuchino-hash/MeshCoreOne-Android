// PortedFrom: MC1Tests/Models/DevicePreferenceStoreTests.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.core.datastore

import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class DevicePreferenceSourceTest {
    @get:Rule val temporary = TemporaryFolder()

    @OriginalCase("DevicePreferenceStoreTests::Auto-update location defaults to false()")
    @Test
    fun autoUpdateDefaultsFalse() = runBlocking {
        withStorage(temporary.newFolder()) { h ->
            assertFalse(h.owner.devicePreferences.isAutoUpdateLocationEnabled(UUID.randomUUID()))
        }
    }

    @OriginalCase("DevicePreferenceStoreTests::GPS source defaults to phone()")
    @Test
    fun gpsDefaultsPhone() = runBlocking {
        withStorage(temporary.newFolder()) { h ->
            assertEquals(GPSSource.PHONE, h.owner.devicePreferences.gpsSource(UUID.randomUUID()))
        }
    }

    @OriginalCase("DevicePreferenceStoreTests::Auto-update values are scoped per device()")
    @Test
    fun autoUpdateIsDeviceScoped() = runBlocking {
        withStorage(temporary.newFolder()) { h ->
            val a = UUID.randomUUID()
            val b = UUID.randomUUID()
            val store = h.owner.devicePreferences
            store.setAutoUpdateLocationEnabled(true, a)
            assertTrue(store.isAutoUpdateLocationEnabled(a))
            assertFalse(store.isAutoUpdateLocationEnabled(b))
            store.setAutoUpdateLocationEnabled(true, b)
            assertTrue(store.isAutoUpdateLocationEnabled(a))
            assertTrue(store.isAutoUpdateLocationEnabled(b))
            store.setAutoUpdateLocationEnabled(false, a)
            assertFalse(store.isAutoUpdateLocationEnabled(a))
            assertTrue(store.isAutoUpdateLocationEnabled(b))
        }
    }

    @OriginalCase("DevicePreferenceStoreTests::GPS source values are scoped per device()")
    @Test
    fun gpsIsDeviceScoped() = runBlocking {
        withStorage(temporary.newFolder()) { h ->
            val a = UUID.randomUUID()
            val b = UUID.randomUUID()
            val store = h.owner.devicePreferences
            store.setGPSSource(GPSSource.DEVICE, a)
            assertEquals(GPSSource.DEVICE, store.gpsSource(a))
            assertEquals(GPSSource.PHONE, store.gpsSource(b))
            store.setGPSSource(GPSSource.DEVICE, b)
            assertEquals(GPSSource.DEVICE, store.gpsSource(a))
            assertEquals(GPSSource.DEVICE, store.gpsSource(b))
            store.setGPSSource(GPSSource.PHONE, a)
            assertEquals(GPSSource.PHONE, store.gpsSource(a))
            assertEquals(GPSSource.DEVICE, store.gpsSource(b))
        }
    }

    @OriginalCase("DevicePreferenceStoreTests::hasSetGPSSource returns false when no source has been set()")
    @Test
    fun gpsPresenceInitiallyFalse() = runBlocking {
        withStorage(temporary.newFolder()) { h ->
            assertFalse(h.owner.devicePreferences.hasSetGPSSource(UUID.randomUUID()))
        }
    }

    @OriginalCase("DevicePreferenceStoreTests::hasSetGPSSource returns true after setting a source()")
    @Test
    fun explicitDefaultStillHasPresence() = runBlocking {
        withStorage(temporary.newFolder()) { h ->
            val id = UUID.randomUUID()
            assertFalse(h.owner.devicePreferences.hasSetGPSSource(id))
            h.owner.devicePreferences.setGPSSource(GPSSource.PHONE, id)
            assertTrue(h.owner.devicePreferences.hasSetGPSSource(id))
        }
    }

    @OriginalCase("DevicePreferenceStoreTests::Setting and getting round-trips correctly()")
    @Test
    fun bothValuesRoundTrip() = runBlocking {
        withStorage(temporary.newFolder()) { h ->
            val id = UUID.randomUUID()
            h.owner.devicePreferences.setAutoUpdateLocationEnabled(true, id)
            h.owner.devicePreferences.setGPSSource(GPSSource.DEVICE, id)
            assertTrue(h.owner.devicePreferences.isAutoUpdateLocationEnabled(id))
            assertEquals(GPSSource.DEVICE, h.owner.devicePreferences.gpsSource(id))
            h.owner.devicePreferences.setAutoUpdateLocationEnabled(false, id)
            h.owner.devicePreferences.setGPSSource(GPSSource.PHONE, id)
            assertFalse(h.owner.devicePreferences.isAutoUpdateLocationEnabled(id))
            assertEquals(GPSSource.PHONE, h.owner.devicePreferences.gpsSource(id))
        }
    }
}
