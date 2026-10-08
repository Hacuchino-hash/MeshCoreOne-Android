// AndroidOnly: WP-303 Test harness: the production AppContainer over a Room store, a fake radio and fake platform roles.
package com.meshcoreone.android.app.container

import com.meshcoreone.android.core.contracts.domain.BluetoothAddress
import com.meshcoreone.android.core.contracts.domain.BluetoothPairingHandle
import com.meshcoreone.android.core.contracts.domain.ConnectionTarget
import com.meshcoreone.android.core.contracts.domain.DeviceConnectionState
import com.meshcoreone.android.core.data.repository.RoomPersistenceStore
import com.meshcoreone.android.core.model.RadioId
import com.meshcoreone.android.core.protocol.bytes.Bytes
import com.meshcoreone.android.core.runtime.ProcessConnectionPreferences
import com.meshcoreone.android.core.runtime.RuntimeDiagnostic
import com.meshcoreone.android.core.runtime.RuntimeIssueReporter
import com.meshcoreone.android.core.runtime.RuntimePreferenceSnapshot
import com.meshcoreone.android.core.runtime.RuntimePreferenceValue
import com.meshcoreone.android.core.services.contacts.ContactPreferenceFlags
import com.meshcoreone.android.core.services.diagnostics.DebugLogBuffer
import com.meshcoreone.android.core.services.rendering.DraftDefaults
import com.meshcoreone.android.core.services.rendering.DraftStore
import java.util.UUID
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope

internal class MemoryConnectionPreferences : ProcessConnectionPreferences {
    val values = linkedMapOf<String, RuntimePreferenceValue>()
    override suspend fun read() = RuntimePreferenceSnapshot(values)
    override suspend fun update(transform: (MutableMap<String, RuntimePreferenceValue>) -> Unit): RuntimePreferenceSnapshot {
        val next = values.toMutableMap()
        transform(next)
        values.clear(); values.putAll(next)
        return RuntimePreferenceSnapshot(values)
    }
}

internal class MemoryVault : NodePasswordVault {
    private val values = mutableMapOf<Pair<RadioId, Bytes>, String>()
    override suspend fun store(password: String, radioId: RadioId, nodePublicKey: Bytes) { values[radioId to nodePublicKey] = password }
    override suspend fun retrieve(radioId: RadioId, nodePublicKey: Bytes): String? = values[radioId to nodePublicKey]
    override suspend fun has(radioId: RadioId, nodePublicKey: Bytes): Boolean = (radioId to nodePublicKey) in values
    override suspend fun delete(radioId: RadioId, nodePublicKey: Bytes) { values.remove(radioId to nodePublicKey) }
}

internal class MemoryFlags : ContactPreferenceFlags {
    private val values = mutableMapOf<String, Boolean>()
    override fun bool(key: String): Boolean = values[key] ?: false
    override fun set(key: String, value: Boolean) { values[key] = value }
}

internal class MemoryDraftDefaults : DraftDefaults {
    private val values = mutableMapOf<String, Map<String, String>>()
    override fun stringDictionary(key: String): Map<String, String>? = values[key]
    override fun setStringDictionary(value: Map<String, String>, key: String) { values[key] = value }
}

internal class RecordingLifecycle : SessionLifecycleListener {
    val created = mutableListOf<RadioSessionContainer>()
    val tornDown = mutableListOf<RadioSessionContainer>()
    override fun created(container: RadioSessionContainer) { created += container }
    override fun tornDown(container: RadioSessionContainer) { tornDown += container }
}

/** The production [AppContainer] composed over test roles; every connection runs the real service graph. */
internal class ContainerHarness(
    test: TestScope,
    val store: RoomPersistenceStore,
    identity: (ConnectionTarget) -> Bytes = { key(7) },
    deliveryOverride: RecordingDelivery? = null,
) {
    val scheduler: TestCoroutineScheduler = test.testScheduler
    val links = HarnessLinks(identity)
    val delivery = deliveryOverride ?: RecordingDelivery()
    val pairing = HarnessPairingService()
    val preferences = MemoryConnectionPreferences()
    val lifecycle = RecordingLifecycle()
    val diagnostics = mutableListOf<RuntimeDiagnostic>()
    val draftStore = DraftStore(MemoryDraftDefaults())
    val container = AppContainer(
        AppContainerDependencies(
            store = store,
            passwords = MemoryVault(),
            connectionPreferences = preferences,
            connectivity = newConnectivity(pairing),
            linkProbe = NoSystemLinks,
            scans = newScans(),
            linkFactory = links,
            notificationDelivery = delivery,
            notificationPreferences = FixedNotificationPreferences(),
            contactPreferences = MemoryFlags(),
            draftStore = draftStore,
            mainScope = test.backgroundScope,
            runtimeClock = SchedulerClock { scheduler.currentTime },
            runtimeContext = test.backgroundScope.coroutineContext,
            accessibility = SilentAnnouncements,
            sessionLifecycle = lifecycle,
            runtimeReporter = RuntimeIssueReporter { diagnostics += it },
            newBootstrapDebugLog = { DebugLogBuffer(store, it) },
        ),
    )
    val manager get() = container.connectionManager
    val appState get() = container.appState
    val sessions get() = container.sessions

    fun target(id: UUID = UUID.randomUUID()): ConnectionTarget.Bluetooth =
        ConnectionTarget.Bluetooth(BluetoothPairingHandle(BluetoothAddress(syntheticAddress(id)), null), id)

    /** Runs scheduled work, then yields real time so Default-dispatcher services can finish. */
    fun settle(rounds: Int = 5) {
        repeat(rounds) { scheduler.runCurrent(); scheduler.advanceUntilIdle(); Thread.sleep(2) }
    }

    /** Polls (running scheduled work between checks) until [condition] holds or five real seconds pass. */
    fun eventually(message: String = "condition", condition: () -> Boolean) {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (System.nanoTime() < deadline) {
            scheduler.runCurrent()
            if (condition()) return
            Thread.sleep(5)
        }
        throw AssertionError("Timed out waiting for $message")
    }

    fun assertReady() {
        check(manager.connectionState == DeviceConnectionState.READY) { "state=${manager.connectionState}" }
    }
}
