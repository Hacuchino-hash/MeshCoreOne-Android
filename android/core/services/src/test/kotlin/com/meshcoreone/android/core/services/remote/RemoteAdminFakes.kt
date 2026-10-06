// AndroidOnly: WP-210 Store, audit-log and binary-session fakes plus a harness for the repeater/room admin, room server and binary protocol service tests.
package com.meshcoreone.android.core.services.remote

import com.meshcoreone.android.core.contracts.domain.EntityKey
import com.meshcoreone.android.core.model.ContactDTO
import com.meshcoreone.android.core.model.MessageStatus
import com.meshcoreone.android.core.model.RadioId
import com.meshcoreone.android.core.model.RemoteNodeSessionDTO
import com.meshcoreone.android.core.model.RoomMessageDTO
import com.meshcoreone.android.core.model.SnapshotList
import com.meshcoreone.android.core.protocol.bytes.Bytes
import com.meshcoreone.android.core.protocol.event.ACLResponse
import com.meshcoreone.android.core.protocol.event.ConnectionState
import com.meshcoreone.android.core.protocol.event.EventFilter
import com.meshcoreone.android.core.protocol.event.LoginInfo
import com.meshcoreone.android.core.protocol.event.MMAResponse
import com.meshcoreone.android.core.protocol.event.MeshEvent
import com.meshcoreone.android.core.protocol.event.MessageSentInfo
import com.meshcoreone.android.core.protocol.event.NeighboursResponse
import com.meshcoreone.android.core.protocol.event.StatusResponse
import com.meshcoreone.android.core.protocol.event.TelemetryResponse
import com.meshcoreone.android.core.protocol.model.ContactFlags
import com.meshcoreone.android.core.protocol.model.ContactType
import com.meshcoreone.android.core.protocol.model.MeshContact
import com.meshcoreone.android.core.protocol.session.ContactFetchResult
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow

/**
 * In-memory [RemoteAdminStore] layered on a [RemoteCoreFakeStore] so RemoteNodeService (which writes
 * through [core]) and the admin services read the same session and contact rows. Room-message rows follow
 * the Room DAO semantics (duplicate-skipping insert, COALESCE on ack/RTT, conditional `markConnected`).
 * [operations] records every room write in call order.
 */
class RemoteAdminFakeStore(val core: RemoteCoreFakeStore, private val clock: RemoteCoreTestClock) :
    RemoteAdminStore, RemoteNodeStore by core {
    private val lock = Any()
    private val messages = LinkedHashMap<EntityKey, RoomMessageDTO>()
    private val log = mutableListOf<String>()

    /** Sessions the admin-service read path reports as missing (a row deleted between two reads). */
    val vanishedSessions: MutableSet<EntityKey> = java.util.Collections.synchronizedSet(HashSet())
    @Volatile var markConnectedError: Exception? = null
    @Volatile var updateActivityError: Exception? = null
    @Volatile var updateStatusError: Exception? = null
    @Volatile var findContactError: Exception? = null
    /** When set, `updateRoomMessageStatus` waits on it before writing. */
    @Volatile var updateStatusGate: CompletableDeferred<Unit>? = null

    val operations: List<String> get() = synchronized(lock) { log.toList() }
    fun message(key: EntityKey): RoomMessageDTO? = synchronized(lock) { messages[key] }
    val messageCount: Int get() = synchronized(lock) { messages.size }
    fun removeMessage(key: EntityKey) = synchronized(lock) { messages.remove(key); Unit }

    private fun record(entry: String) = synchronized(lock) { log += entry }

    private suspend fun mutateSession(key: EntityKey, change: (RemoteNodeSessionDTO) -> RemoteNodeSessionDTO) {
        core.session(key)?.let { core.saveRemoteNodeSessionDTO(change(it)) }
    }

    override suspend fun fetchRemoteNodeSession(key: EntityKey): RemoteNodeSessionDTO? =
        if (key in vanishedSessions) null else core.fetchRemoteNodeSession(key)

    override suspend fun fetchRemoteNodeSessionByPrefix(radioId: RadioId, prefix: Bytes): RemoteNodeSessionDTO? =
        core.fetchRemoteNodeSessions(radioId).firstOrNull { it.publicKey.prefix(6) == prefix }

    override suspend fun markRoomSessionConnected(key: EntityKey): Boolean {
        record("markConnected")
        markConnectedError?.let { throw it }
        val existing = core.session(key) ?: return false
        if (existing.isConnected) return false
        core.saveRemoteNodeSessionDTO(existing.copy(isConnected = true))
        return true
    }

    override suspend fun updateRoomActivity(key: EntityKey, syncTimestamp: UInt?) {
        record("activity:${syncTimestamp ?: "nil"}")
        updateActivityError?.let { throw it }
        mutateSession(key) {
            it.copy(
                lastSyncTimestamp = maxOf(it.lastSyncTimestamp, syncTimestamp ?: it.lastSyncTimestamp),
                lastMessageDate = clock.wallClock.instant(),
            )
        }
    }

    override suspend fun saveRoomMessage(radioId: RadioId, dto: RoomMessageDTO) {
        record("save:${dto.statusRawValue}")
        synchronized(lock) {
            val duplicate = messages.entries.any {
                it.key.radioId == radioId && it.value.sessionID == dto.sessionID && it.value.deduplicationKey == dto.deduplicationKey
            }
            if (!duplicate) messages[EntityKey(radioId, dto.id)] = dto
        }
    }

    override suspend fun fetchRoomMessage(key: EntityKey): RoomMessageDTO? = message(key)

    override suspend fun fetchRoomMessages(session: EntityKey, limit: Long?, offset: Long?): SnapshotList<RoomMessageDTO> {
        record("fetchMessages:${limit ?: "nil"}:${offset ?: "nil"}")
        val rows = synchronized(lock) {
            messages.filter { it.key.radioId == session.radioId && it.value.sessionID == session.id }.values
                .sortedBy { it.timestamp }
        }
        val skipped = rows.drop((offset ?: 0).toInt())
        return SnapshotList(if (limit == null) skipped else skipped.take(limit.toInt()))
    }

    override suspend fun isDuplicateRoomMessage(session: EntityKey, deduplicationKey: String): Boolean = synchronized(lock) {
        messages.entries.any {
            it.key.radioId == session.radioId && it.value.sessionID == session.id && it.value.deduplicationKey == deduplicationKey
        }
    }

    override suspend fun updateRoomMessageStatus(key: EntityKey, status: MessageStatus, ackCode: UInt?, roundTripTime: UInt?) {
        updateStatusGate?.await()
        record("status:$status")
        updateStatusError?.let { throw it }
        synchronized(lock) {
            messages[key]?.let {
                messages[key] = it.copy(
                    statusRawValue = status.rawValue, ackCode = ackCode ?: it.ackCode, roundTripTime = roundTripTime ?: it.roundTripTime,
                )
            }
        }
    }

    override suspend fun updateRoomMessageRetryStatus(key: EntityKey, status: MessageStatus, retryAttempt: Long, maxRetryAttempts: Long) {
        record("retryStatus:$status:$retryAttempt:$maxRetryAttempts")
        synchronized(lock) {
            messages[key]?.let {
                messages[key] = it.copy(statusRawValue = status.rawValue, retryAttempt = retryAttempt, maxRetryAttempts = maxRetryAttempts)
            }
        }
    }

    override suspend fun incrementRoomUnreadCount(key: EntityKey) {
        record("unread+1")
        mutateSession(key) { it.copy(unreadCount = it.unreadCount + 1) }
    }

    override suspend fun resetRoomUnreadCount(key: EntityKey) {
        record("unread=0")
        mutateSession(key) { it.copy(unreadCount = 0) }
    }

    override suspend fun findContactNameByKeyPrefix(prefix: Bytes): String? =
        core.allContacts.firstOrNull { it.publicKey.prefix(prefix.size) == prefix }?.displayName

    override suspend fun findContactByPublicKey(publicKey: Bytes): ContactDTO? {
        findContactError?.let { throw it }
        return core.allContacts.firstOrNull { it.publicKey == publicKey }
    }
}

/** Records every audit call as a readable line, in order. */
class RemoteAdminRecordingAuditLog : RemoteCommandAuditLog {
    private val lock = Any()
    private val lines = mutableListOf<String>()
    val entries: List<String> get() = synchronized(lock) { lines.toList() }
    private fun add(line: String) = synchronized(lock) { lines += line }

    override suspend fun logStatusResponse(target: RemoteAuditTarget, publicKey: Bytes, batteryMv: UShort?, uptimeSec: UInt?) =
        add("statusResponse ${target.rawValue} ${publicKey.hexString} $batteryMv $uptimeSec")
    override suspend fun logTelemetryResponse(target: RemoteAuditTarget, publicKey: Bytes, pointCount: Int) =
        add("telemetryResponse ${target.rawValue} ${publicKey.hexString} $pointCount")
    override suspend fun logCLIResponse(publicKey: Bytes, response: String) = add("cliResponse ${publicKey.hexString} $response")
    override suspend fun logNeighborsRequest(publicKey: Bytes, count: UByte, offset: UShort) =
        add("neighborsRequest ${publicKey.prefix(6).hexString} $count $offset")
    override suspend fun logNeighborsResponse(publicKey: Bytes, totalCount: Int, returnedCount: Int) =
        add("neighborsResponse ${publicKey.hexString} $totalCount $returnedCount")
    override suspend fun logRoomMessagePosted(publicKey: Bytes, messageLength: Int) =
        add("roomPosted ${publicKey.prefix(6).hexString} $messageLength")
    override suspend fun logRoomMessageReceived(roomPublicKey: Bytes, authorPrefix: Bytes, messageLength: Int) =
        add("roomReceived ${roomPublicKey.hexString} ${authorPrefix.hexString} $messageLength")
}

/** Admin services wired over a [RemoteCoreHarness] that share its session, clock, scope and rows. */
class RemoteAdminHarness(val core: RemoteCoreHarness) {
    val audit = RemoteAdminRecordingAuditLog()
    val store = RemoteAdminFakeStore(core.store, core.clock)
    val repeater = RepeaterAdminService(core.session, core.service, store, auditLogger = audit)
    val roomAdmin = RoomAdminService(core.service, store, audit)
    val rooms = RoomServerService(core.session, core.service, store, core.radioId, core.scope, auditLogger = audit)
}

/** Waits for the login send for [publicKey], then pushes the matching `loginSuccess`. */
suspend fun RemoteAdminHarness.remoteAdminAnswerLogin(publicKey: Bytes, permissions: UByte, admin: Boolean) {
    remoteCoreAwait("login never sent") { core.session.sendLoginInvocations.any { it.destination == publicKey } }
    core.session.yieldEvent(MeshEvent.LoginSuccess(LoginInfo(permissions, admin, publicKey.prefix(6))))
}

/** Runs [block] with a fresh [RemoteAdminHarness] whose core harness is always closed. */
suspend fun <T> withRemoteAdminHarness(block: suspend RemoteAdminHarness.() -> T): T =
    withRemoteCoreHarness { RemoteAdminHarness(this).block() }

/**
 * Hand-written [BinaryProtocolSessionPort] for BinaryProtocolService: each operation pops the next
 * scripted result for its name (`status`, `statusTyped`, `telemetry`, `neighbours`, `allNeighbours`,
 * `mma`, `acl`, `selfTelemetry`, `pathDiscovery`, `trace`), records its call, and fails with
 * [RemoteCoreNotStubbed] when nothing is scripted. Event subscriptions register synchronously.
 */
class RemoteAdminFakeBinarySession : BinaryProtocolSessionPort {
    private val lock = Any()
    private val scripted = HashMap<String, ArrayDeque<suspend () -> Any?>>()
    private val callLog = mutableListOf<String>()
    private val resetKeys = mutableListOf<Bytes>()
    private val subscriptions = LinkedHashMap<Long, Channel<MeshEvent>>()
    private var nextSubscription = 0L
    @Volatile var resetPathError: Exception? = null
    @Volatile var resetPathGate: CompletableDeferred<Unit>? = null

    val calls: List<String> get() = synchronized(lock) { callLog.toList() }
    val resetPathPublicKeys: List<Bytes> get() = synchronized(lock) { resetKeys.toList() }
    val eventSubscriptionCount: Int get() = synchronized(lock) { subscriptions.size }

    /** Queue [answers] for operation [name]; each is returned or thrown in turn. */
    fun script(name: String, vararg answers: suspend () -> Any?) = synchronized(lock) {
        scripted.getOrPut(name) { ArrayDeque() }.addAll(answers)
    }

    fun yieldEvent(event: MeshEvent) = synchronized(lock) { subscriptions.values.forEach { it.trySend(event) } }

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T> answer(name: String, call: String): T {
        val next = synchronized(lock) {
            callLog += call
            scripted[name]?.removeFirstOrNull()
        } ?: throw RemoteCoreNotStubbed(name)
        return next() as T
    }

    override val connectionState: Flow<ConnectionState> = emptyFlow()
    override fun events(): Flow<MeshEvent> {
        val channel = Channel<MeshEvent>(Channel.UNLIMITED)
        val id = synchronized(lock) { nextSubscription++.also { subscriptions[it] = channel } }
        return flow {
            try {
                for (event in channel) emit(event)
            } finally {
                synchronized(lock) { subscriptions.remove(id) }
            }
        }
    }
    override fun events(filter: EventFilter): Flow<MeshEvent> = throw RemoteCoreNotStubbed("events(filter)")
    override suspend fun waitForEvent(filter: EventFilter, timeout: Double?): MeshEvent? = null

    override suspend fun requestStatus(publicKey: Bytes): StatusResponse = answer("status", "status ${publicKey.hexString}")
    override suspend fun requestStatus(publicKey: Bytes, type: ContactType): StatusResponse =
        answer("statusTyped", "statusTyped ${publicKey.hexString} $type")
    override suspend fun requestTelemetry(publicKey: Bytes): TelemetryResponse = answer("telemetry", "telemetry ${publicKey.hexString}")
    override suspend fun requestNeighbours(
        publicKey: Bytes, count: UByte, offset: UShort, orderBy: UByte, pubkeyPrefixLength: UByte,
    ): NeighboursResponse = answer("neighbours", "neighbours ${publicKey.hexString} $count $offset $orderBy $pubkeyPrefixLength")
    override suspend fun fetchAllNeighbours(publicKey: Bytes, orderBy: UByte, pubkeyPrefixLength: UByte): NeighboursResponse =
        answer("allNeighbours", "allNeighbours ${publicKey.hexString} $orderBy $pubkeyPrefixLength")
    override suspend fun requestMMA(publicKey: Bytes, start: Instant, end: Instant): MMAResponse =
        answer("mma", "mma ${publicKey.hexString} ${start.epochSecond} ${end.epochSecond}")
    override suspend fun requestACL(publicKey: Bytes): ACLResponse = answer("acl", "acl ${publicKey.hexString}")
    override suspend fun getSelfTelemetry(): TelemetryResponse = answer("selfTelemetry", "selfTelemetry")
    override suspend fun sendPathDiscovery(destination: Bytes): MessageSentInfo =
        answer("pathDiscovery", "pathDiscovery ${destination.hexString}")
    override suspend fun sendTrace(tag: UInt?, authCode: UInt?, flags: UByte, path: Bytes?): MessageSentInfo =
        answer("trace", "trace $tag $authCode $flags ${path?.hexString}")

    override suspend fun resetPath(publicKey: Bytes) {
        synchronized(lock) { resetKeys += publicKey; callLog += "resetPath ${publicKey.hexString}" }
        resetPathGate?.await()
        resetPathError?.let { throw it }
    }

    override suspend fun getContacts(since: Instant?): List<MeshContact> = throw RemoteCoreNotStubbed("getContacts")
    override suspend fun getContactsReportingTotal(since: Instant?): ContactFetchResult =
        throw RemoteCoreNotStubbed("getContactsReportingTotal")
    override suspend fun getContact(publicKey: Bytes): MeshContact? = throw RemoteCoreNotStubbed("getContact")
    override suspend fun addContact(contact: MeshContact) = throw RemoteCoreNotStubbed("addContact")
    override suspend fun removeContact(publicKey: Bytes) = throw RemoteCoreNotStubbed("removeContact")
    override suspend fun shareContact(publicKey: Bytes) = throw RemoteCoreNotStubbed("shareContact")
    override suspend fun exportContact(publicKey: Bytes?): String = throw RemoteCoreNotStubbed("exportContact")
    override suspend fun importContact(cardData: Bytes) = throw RemoteCoreNotStubbed("importContact")
    override suspend fun changeContactFlags(contact: MeshContact, flags: ContactFlags) =
        throw RemoteCoreNotStubbed("changeContactFlags")
}

/** A status payload with recognizable battery/uptime values. */
fun remoteAdminStatus(prefix: Bytes, battery: Long = 4100, uptime: UInt = 3600u): StatusResponse = StatusResponse(
    publicKeyPrefix = prefix, battery = battery, txQueueLength = 0, noiseFloor = -110, lastRSSI = -60,
    packetsReceived = 10u, packetsSent = 5u, airtime = 1u, uptime = uptime, sentFlood = 0u, sentDirect = 0u,
    receivedFlood = 0u, receivedDirect = 0u, fullEvents = 0, lastSNR = 7.5, directDuplicates = 0,
    floodDuplicates = 0, rxAirtime = 0u,
)
