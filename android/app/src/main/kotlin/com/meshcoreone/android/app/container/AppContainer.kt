// PortedFrom: MC1/MC1App.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.app.container

import com.meshcoreone.android.app.navigation.ChatSelection
import com.meshcoreone.android.app.navigation.NavigationCoordinator
import com.meshcoreone.android.app.navigation.NavigationDestination
import com.meshcoreone.android.app.state.AccessibilityAnnouncer
import com.meshcoreone.android.app.state.AppState
import com.meshcoreone.android.app.state.AppStateDependencies
import com.meshcoreone.android.app.state.AppStatePlatform
import com.meshcoreone.android.app.state.BatteryMonitor
import com.meshcoreone.android.app.state.ChatPrimerFactory
import com.meshcoreone.android.app.state.ConnectionUiState
import com.meshcoreone.android.app.state.ProcessForegroundState
import com.meshcoreone.android.app.state.RegionSelectionStore
import com.meshcoreone.android.app.state.ResyncFailureRegistry
import com.meshcoreone.android.app.state.StaleCleanupPreferences
import com.meshcoreone.android.core.connectivity.ConnectivityDiagnostics
import com.meshcoreone.android.core.connectivity.ConnectivityPlatform
import com.meshcoreone.android.core.connectivity.SystemConnectivityClock
import com.meshcoreone.android.core.connectivity.ble.BleScanCoordinator
import com.meshcoreone.android.core.connectivity.ble.SystemLinkProbe
import com.meshcoreone.android.core.connectivity.bond.BondInspector
import com.meshcoreone.android.core.connectivity.device.ConnectedDeviceEditor
import com.meshcoreone.android.core.connectivity.device.ContactRemovalPort
import com.meshcoreone.android.core.connectivity.device.RadioPresetMatcher
import com.meshcoreone.android.core.connectivity.pairing.KnownEndpointStore
import com.meshcoreone.android.core.connectivity.pairing.PairingCoordinator
import com.meshcoreone.android.core.contracts.AppTab
import com.meshcoreone.android.core.contracts.domain.EntityKey
import com.meshcoreone.android.core.contracts.domain.MessagingIssueReporter
import com.meshcoreone.android.core.contracts.domain.NotificationPreferencesPort
import com.meshcoreone.android.core.contracts.domain.NotificationStringProvider
import com.meshcoreone.android.core.contracts.domain.PersistenceStoreProtocol
import com.meshcoreone.android.core.contracts.notifications.NotificationDeliveryPort
import com.meshcoreone.android.core.model.RadioId
import com.meshcoreone.android.core.protocol.bytes.Bytes
import com.meshcoreone.android.core.runtime.ConnectionManager
import com.meshcoreone.android.core.runtime.ConnectionObserver
import com.meshcoreone.android.core.runtime.LastConnectionStore
import com.meshcoreone.android.core.runtime.ProcessConnectionPreferences
import com.meshcoreone.android.core.runtime.ProcessRuntimeMaintenance
import com.meshcoreone.android.core.runtime.RuntimeClock
import com.meshcoreone.android.core.runtime.RuntimeIssueReporter
import com.meshcoreone.android.core.runtime.RuntimeLinkFactory
import com.meshcoreone.android.core.runtime.SystemRuntimeClock
import com.meshcoreone.android.core.services.contacts.ContactPreferenceFlags
import com.meshcoreone.android.core.services.device.RadioPresets
import com.meshcoreone.android.core.services.diagnostics.DebugLogBuffer
import com.meshcoreone.android.core.services.rendering.ChatCoordinatorRegistry
import com.meshcoreone.android.core.services.rendering.DraftStore
import com.meshcoreone.android.core.services.sync.SyncClock
import com.meshcoreone.android.core.services.sync.SyncLogSink
import java.time.Instant
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Every process-lifetime collaborator [AppContainer] assembles. Production values come from
 * [AndroidAppContainerFactory]; tests supply recording doubles through the same constructor, so the container logic
 * that is exercised is the production wiring, not a parallel copy.
 */
class AppContainerDependencies(
    val store: PersistenceStoreProtocol,
    val passwords: NodePasswordVault,
    val connectionPreferences: ProcessConnectionPreferences,
    val connectivity: ConnectivityPlatform,
    val linkProbe: SystemLinkProbe,
    val scans: BleScanCoordinator,
    val linkFactory: RuntimeLinkFactory,
    val notificationDelivery: NotificationDeliveryPort,
    val notificationPreferences: NotificationPreferencesPort,
    val contactPreferences: ContactPreferenceFlags,
    val draftStore: DraftStore,
    /** The main-thread stand-in app state, UI timers and the chat coordinators run on. */
    val mainScope: CoroutineScope,
    val notificationStrings: NotificationStringProvider? = null,
    val foreground: ProcessForegroundState = ProcessForegroundState(),
    val runtimeClock: RuntimeClock = SystemRuntimeClock(),
    val runtimeContext: CoroutineContext = Dispatchers.Default,
    val knownEndpoints: KnownEndpointStore? = null,
    val bonds: BondInspector? = null,
    val ensureBonded: (suspend (java.util.UUID) -> Unit)? = null,
    val accessibility: AccessibilityAnnouncer = AccessibilityAnnouncer.NONE,
    val platform: AppStatePlatform = AppStatePlatform.NONE,
    val regionStore: RegionSelectionStore? = null,
    val stalePreferences: StaleCleanupPreferences? = null,
    val primerFactory: ChatPrimerFactory = ChatPrimerFactory { null },
    val syncClock: SyncClock = SyncClock.SYSTEM,
    val logSink: SyncLogSink = SyncLogSink.NONE,
    val messagingReporter: MessagingIssueReporter = MessagingIssueReporter {},
    val runtimeReporter: RuntimeIssueReporter = RuntimeIssueReporter {},
    val connectivityDiagnostics: ConnectivityDiagnostics = ConnectivityDiagnostics.NONE,
    val sessionLifecycle: SessionLifecycleListener = SessionLifecycleListener.NONE,
    val newBootstrapDebugLog: (CoroutineScope) -> DebugLogBuffer? = { null },
    /** Releases process-owned resources (database, storage) once the runtime has closed. */
    val onClose: suspend () -> Unit = {},
)

/**
 * The process-scoped container (Swift `MC1App`'s long-lived objects): one runtime connection manager, one app
 * state, one process store and the registry through which the per-connection [RadioSessionContainer] is reached.
 * It owns no radio-specific service; those are built by [RadioSessionContainerFactory] each time the runtime
 * connects, and the registry proves each one was torn down.
 */
class AppContainer(private val dependencies: AppContainerDependencies) {
    private val processJob = SupervisorJob()
    private val processScope = CoroutineScope(dependencies.runtimeContext + processJob)
    private val resyncFailureHub = ResyncFailureHub()
    private val observerBridge = ObserverBridge(dependencies.foreground, dependencies.mainScope.coroutineContext)

    val sessions = SessionRegistry()
    val navigation = NavigationCoordinator()
    val foreground: ProcessForegroundState = dependencies.foreground
    val bootstrapDebugLog: DebugLogBuffer? = dependencies.newBootstrapDebugLog(processScope)

    private val lastConnection = LastConnectionStore(dependencies.connectionPreferences, dependencies.runtimeClock)
    private val platformAdapter = RuntimePlatformAdapter(dependencies.connectivity)
    private val runtimeState = ManagerRuntimeSyncState({ connectionManager }, processScope)

    private val environment = SessionEnvironment(
        store = dependencies.store,
        processScope = processScope,
        notificationDelivery = dependencies.notificationDelivery,
        notificationPreferences = dependencies.notificationPreferences,
        notificationStrings = dependencies.notificationStrings,
        appState = dependencies.foreground,
        passwords = dependencies.passwords,
        contactPreferences = dependencies.contactPreferences,
        runtimeState = runtimeState,
        resyncFailure = resyncFailureHub,
        syncClock = dependencies.syncClock,
        logSink = dependencies.logSink,
        messagingReporter = dependencies.messagingReporter,
        onSessionLifecycle = dependencies.sessionLifecycle,
        bootstrapDebugLog = bootstrapDebugLog,
    )

    val connectionManager: ConnectionManager = ConnectionManager(
        devices = dependencies.store,
        rooms = dependencies.store,
        contacts = dependencies.store,
        maintenance = object : ProcessRuntimeMaintenance {
            override suspend fun warmUp() = dependencies.store.warmUp()
            // The source applies no per-device defaults at connect; per-device preferences are read lazily.
            override suspend fun initializeDevicePreferences(device: com.meshcoreone.android.core.model.DeviceDTO) = Unit
        },
        lastConnection = lastConnection,
        platform = platformAdapter,
        linkFactory = dependencies.linkFactory,
        serviceFactory = RadioSessionContainerFactory(environment, sessions),
        observer = observerBridge.observer,
        reporter = dependencies.runtimeReporter,
        clock = dependencies.runtimeClock,
        context = dependencies.runtimeContext,
    )

    val pairing: PairingCoordinator = PairingCoordinator(
        RuntimePairingPort(connectionManager, platformAdapter), dependencies.connectivity.pairing, dependencies.store,
        dependencies.linkProbe, dependencies.scans::stopBleScanning, SystemConnectivityClock(), processScope,
        dependencies.connectivityDiagnostics, dependencies.ensureBonded, dependencies.knownEndpoints, dependencies.bonds,
    )

    private val deviceEditor = ConnectedDeviceEditor(
        ManagerDeviceAccess(connectionManager), dependencies.store, dependencies.store,
        { sessions.current?.let { ContactRemoval(it.token.radioId, it) } },
        RadioPresetMatcher { frequency, bandwidth, spreadingFactor, codingRate ->
            RadioPresets.matchingPresets(frequency, bandwidth, spreadingFactor, codingRate).map { it.id }.toSet()
        },
        processScope, { Instant.now() }, dependencies.connectivityDiagnostics,
    )

    val connectionPort = ConnectionManagerAppPort(
        connectionManager, platformAdapter, pairing, lastConnection, deviceEditor, dependencies.scans::stopBleScanning,
    )

    val appState: AppState = AppState(
        AppStateDependencies(
            connection = connectionPort,
            scope = dependencies.mainScope,
            session = { sessions.current },
            processStore = { dependencies.store },
            draftStore = dependencies.draftStore,
            connectionUi = ConnectionUiState(dependencies.mainScope, dependencies.runtimeClock, dependencies.accessibility),
            batteryMonitor = BatteryMonitor(dependencies.mainScope, dependencies.runtimeClock),
            resyncFailure = ResyncFailureRegistry { handler -> resyncFailureHub.handler = handler },
            registryFactory = { store ->
                ChatCoordinatorRegistry(store, dependencies.mainScope)
            },
            primerFactory = dependencies.primerFactory,
            navigation = navigation,
            platform = ContainerAppStatePlatform(dependencies.platform),
            regionStore = dependencies.regionStore,
            stalePreferences = dependencies.stalePreferences,
            clock = dependencies.runtimeClock,
            closeChatRoute = { closeSelectedChatRoute() },
        ),
    )

    init {
        observerBridge.bind(appState, connectionPort)
    }

    /** Starts the process: loads persisted state and activates the runtime (restores the last connection). */
    fun start(): Job = dependencies.mainScope.launch { appState.initialize() }

    /**
     * `NavigationCoordinator` has no seam that clears only the chats route, so this pops the top chat of the Chats
     * stack when it is the selected one (see WP-303.md coordinator note).
     */
    private fun closeSelectedChatRoute() {
        val state = navigation.state.value
        val top = state.stacks[AppTab.CHATS]?.lastOrNull()?.destination
        if (state.selectedTab == AppTab.CHATS && top is NavigationDestination.Chat &&
            top.selection == state.chatsSelectedRoute && state.chatsSelectedRoute is ChatSelection.Channel
        ) navigation.back()
    }

    /** Process shutdown: closes the runtime (tearing down any live graph), then every process resource. */
    suspend fun close() {
        withContext(NonCancellable) {
            appState.shutdown()
            connectionManager.close()
            processJob.cancelAndJoin()
            dependencies.onClose()
        }
    }

    private class ContactRemoval(private val radioId: RadioId, private val session: com.meshcoreone.android.app.state.AppSession) :
        ContactRemovalPort {
        override suspend fun removeContact(radioId: RadioId, publicKey: Bytes) = session.contactService.removeContact(radioId, publicKey)
        override suspend fun removeLocalContact(contactId: java.util.UUID, publicKey: Bytes) =
            session.contactService.removeLocalContact(EntityKey(radioId, contactId), publicKey)
    }
}

/** Holds the resync-failure hook ConnectionUiState installs, and fires it from the session's retry loop. */
internal class ResyncFailureHub : ResyncFailureSignal {
    @Volatile var handler: (() -> Unit)? = null
    override fun resyncFailed() { handler?.invoke() }
}

/** Adds the process-global debug-log flush to the platform hooks app state calls. */
private class ContainerAppStatePlatform(private val delegate: AppStatePlatform) : AppStatePlatform by delegate {
    override suspend fun flushDebugLog() {
        DebugLogBuffer.shared?.flush()
        delegate.flushDebugLog()
    }
}

/**
 * Routes the runtime's observer callbacks to app state on the main stand-in. The bridge exists before app state
 * (the runtime needs its observer at construction), so [bind] installs the targets afterwards; no callback can
 * fire before the first connect, which only starts after the container is built.
 */
internal class ObserverBridge(private val foreground: ProcessForegroundState, private val mainContext: CoroutineContext) {
    @Volatile private var appState: AppState? = null
    @Volatile private var port: ConnectionManagerAppPort? = null
    private val scope = CoroutineScope(mainContext + SupervisorJob())

    fun bind(appState: AppState, port: ConnectionManagerAppPort) {
        this.appState = appState
        this.port = port
    }

    private suspend fun onMain(block: suspend (AppState, ConnectionManagerAppPort) -> Unit) {
        val state = appState ?: return
        val connection = port ?: return
        withContext(mainContext) { block(state, connection) }
    }

    val observer = ConnectionObserver(
        onServicesAvailable = { onMain { state, connection -> connection.refreshLastConnection(); state.onSessionStateChanged() } },
        onConnectionLost = { onMain { state, connection -> connection.refreshLastConnection(); state.onSessionStateChanged() } },
        onAutoReconnectStarted = { onMain { state, _ -> state.onAutoReconnectStarted() } },
        onDeviceSynced = { onMain { state, _ -> state.onDeviceSynced() } },
        onAuthenticationFailure = { id -> scope.launch { appState?.handleAuthenticationFailure(id, foreground.isForeground) } },
        onLastDeviceCleared = { scope.launch { port?.refreshLastConnection(); appState?.onLastConnectedDeviceCleared() } },
    )
}
