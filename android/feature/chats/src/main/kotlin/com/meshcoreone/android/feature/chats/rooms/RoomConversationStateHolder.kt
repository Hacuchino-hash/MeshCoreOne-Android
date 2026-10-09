// PortedFrom: MC1/Views/RemoteNodes/Rooms/RoomConversationViewModel.swift@db14559b39d32322b06477c6ae676112f583db50
// PortedFrom: MC1/Views/Chats/Room/RoomAuthenticationSheet.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.feature.chats.rooms

import com.meshcoreone.android.core.model.ContactDTO
import com.meshcoreone.android.core.model.NotificationLevel
import com.meshcoreone.android.core.model.RemoteNodeSessionDTO
import com.meshcoreone.android.core.model.RoomMessageDTO
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RoomConversationState(
    val session: RemoteNodeSessionDTO? = null,
    val messages: List<RoomMessageDTO> = emptyList(),
    val tiledRows: List<RoomTiledRow> = emptyList(),
    val isLoading: Boolean = false,
    /** Prevents an empty-state flash before the first load completes. */
    val hasLoadedOnce: Boolean = false,
    val errorMessage: String? = null,
    val composingText: String = "",
    val isSending: Boolean = false,
)

/**
 * Room conversation state: load/sync, optimistic ordered append, send/retry and the event fold with a
 * coalesced reload. A null port (no radio services) makes every operation a no-op, like the iOS nil services.
 */
class RoomConversationStateHolder(
    private val port: () -> RoomConversationPort?,
    private val scope: CoroutineScope,
    private val reloadDebounce: Duration = DEFAULT_RELOAD_DEBOUNCE,
    private val onDiagnostic: (String, Throwable?) -> Unit = { _, _ -> },
) {
    private val mutable = MutableStateFlow(RoomConversationState())
    val state: StateFlow<RoomConversationState> = mutable.asStateFlow()

    /** Non-null while a coalesced reload is scheduled but has not fired. */
    private var reloadJob: Job? = null

    private fun withMessages(messages: List<RoomMessageDTO>) =
        mutable.update { it.copy(messages = messages, tiledRows = RoomTimeline.tiledRows(messages)) }

    fun setComposingText(text: String) = mutable.update { it.copy(composingText = text) }
    fun dismissError() = mutable.update { it.copy(errorMessage = null) }

    /** Seeds the timeline, e.g. from a cached snapshot, without touching the services. */
    fun replaceMessages(messages: List<RoomMessageDTO>) = withMessages(messages)

    suspend fun loadMessages(session: RemoteNodeSessionDTO) {
        val port = port() ?: return
        mutable.update { it.copy(session = session, isLoading = true, errorMessage = null) }
        try {
            withMessages(port.fetchMessages(session.id))
            // Clear unread, drop delivered notifications for this room and refresh the badge.
            port.markAsRead(session.id)
            port.markFailedSendsSeen(session.id)
            port.removeDeliveredNotifications(session.id)
            port.updateBadgeCount()
            port.notifyConversationsChanged()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            mutable.update { it.copy(errorMessage = failure.message) }
        }
        mutable.update { it.copy(hasLoadedOnce = true, isLoading = false) }
    }

    fun appendMessageIfNew(message: RoomMessageDTO) = withMessages(RoomTimeline.insertedInOrder(mutable.value.messages, message))

    suspend fun sendMessage(text: String) {
        val session = mutable.value.session
        val port = port()
        if (session == null || port == null || text.isEmpty()) {
            mutable.update { it.copy(composingText = text) }
            return
        }
        mutable.update { it.copy(isSending = true, errorMessage = null) }
        try {
            appendMessageIfNew(port.postMessage(session.id, text))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            mutable.update { it.copy(errorMessage = failure.message) }
        }
        mutable.update { it.copy(isSending = false) }
    }

    /** Refreshes the session (permission, connection) from the store; a failed fetch keeps the old one. */
    suspend fun refreshSession() {
        val session = mutable.value.session ?: return
        val port = port() ?: return
        try {
            port.fetchSession(session.id)?.let { updated -> mutable.update { it.copy(session = updated) } }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
        }
    }

    suspend fun retryMessage(id: UUID) {
        val port = port() ?: return
        try {
            val updated = port.retryMessage(id)
            val messages = mutable.value.messages
            if (messages.any { it.id == id }) withMessages(messages.map { if (it.id == id) updated else it })
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            mutable.update { it.copy(errorMessage = failure.message) }
        }
    }

    /** Folds a room event into state; non-room events are ignored. */
    suspend fun handleEvent(event: RoomEvent) {
        val session = mutable.value.session ?: return
        when (event) {
            is RoomEvent.MessageReceived -> {
                if (event.sessionId != session.id) return
                // Optimistic append first, then coalesce the reload so a burst triggers one fetch.
                appendMessageIfNew(event.message)
                scheduleCoalescedReload()
            }
            is RoomEvent.MessageStatusUpdated ->
                if (hasMessage(event.messageId)) scheduleCoalescedReload()
            is RoomEvent.MessageFailed -> if (hasMessage(event.messageId)) {
                scheduleCoalescedReload()
                markFailedSendsSeen(session)
            }
            RoomEvent.Other -> Unit
        }
    }

    private fun hasMessage(id: UUID) = mutable.value.messages.any { it.id == id }

    private suspend fun markFailedSendsSeen(session: RemoteNodeSessionDTO) {
        val port = port() ?: return
        try {
            port.markFailedSendsSeen(session.id)
            port.notifyConversationsChanged()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            onDiagnostic("Failed to mark in-thread room failed send seen", failure)
        }
    }

    /** No-ops while a reload is already pending. */
    private fun scheduleCoalescedReload() {
        if (reloadJob != null) return
        val job = scope.launch {
            delay(reloadDebounce)
            reloadJob = null
            val session = mutable.value.session ?: return@launch
            loadMessages(session)
        }
        // A job that already finished (eager dispatcher, zero debounce) must not stay "pending".
        reloadJob = if (job.isActive) job else null
    }

    /** Cancels a pending reload (the room view disappeared). */
    fun close() {
        reloadJob?.cancel()
        reloadJob = null
    }

    companion object {
        val DEFAULT_RELOAD_DEBOUNCE: Duration = 50.milliseconds
    }
}

/** What the room login sheet shows while it resolves the room's contact. */
sealed interface RoomAuthenticationContent {
    data object Loading : RoomAuthenticationContent
    data class Ready(val contact: ContactDTO) : RoomAuthenticationContent
    data object NotFound : RoomAuthenticationContent
}

/** Resolves the contact behind a room session so the shared node login sheet can authenticate it. */
class RoomAuthenticationStateHolder(private val port: RoomAuthenticationPort) {
    private val mutable = MutableStateFlow<RoomAuthenticationContent>(RoomAuthenticationContent.Loading)
    val content: StateFlow<RoomAuthenticationContent> = mutable.asStateFlow()

    suspend fun load(session: RemoteNodeSessionDTO) {
        val contact = try {
            port.fetchContact(session.radioId, session.publicKey)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            null
        }
        mutable.value = contact?.let(RoomAuthenticationContent::Ready) ?: RoomAuthenticationContent.NotFound
    }
}

/** Notification level and favorite toggles of the room info sheet; a newer change supersedes an in-flight one. */
class RoomInfoStateHolder(
    private val session: RemoteNodeSessionDTO,
    private val port: RoomInfoPort,
    private val scope: CoroutineScope,
) {
    private val levelState = MutableStateFlow(session.notificationLevel)
    private val favoriteState = MutableStateFlow(session.isFavorite)
    val notificationLevel: StateFlow<NotificationLevel> = levelState.asStateFlow()
    val isFavorite: StateFlow<Boolean> = favoriteState.asStateFlow()
    val entries: RoomInfoEntries = roomInfoEntries(session)

    private var notificationJob: Job? = null
    private var favoriteJob: Job? = null

    fun setNotificationLevel(level: NotificationLevel) {
        levelState.value = level
        notificationJob?.cancel()
        notificationJob = scope.launch { port.setNotificationLevel(session, level) }
    }

    fun setFavorite(favorite: Boolean) {
        favoriteState.value = favorite
        favoriteJob?.cancel()
        favoriteJob = scope.launch { port.setFavorite(session, favorite) }
    }

    fun close() {
        notificationJob?.cancel()
        favoriteJob?.cancel()
    }
}
