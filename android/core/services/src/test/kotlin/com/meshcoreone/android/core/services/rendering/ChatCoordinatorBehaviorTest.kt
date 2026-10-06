// AndroidOnly: WP-213 coordinator reload/hard-reset/window-lane/rebuild semantics beyond the ported Swift suite.
package com.meshcoreone.android.core.services.rendering

import com.meshcoreone.android.core.contracts.domain.EntityKey
import com.meshcoreone.android.core.model.ChatConversationID
import com.meshcoreone.android.core.model.MessageDTO
import com.meshcoreone.android.core.model.MessageDirection
import com.meshcoreone.android.core.model.MessageStatus
import com.meshcoreone.android.core.model.RadioId
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

class ChatCoordinatorBehaviorTest {
    private val radioId = RadioId(UUID.randomUUID())
    private val contactID = UUID.randomUUID()
    private val dmID = ChatConversationID.dm(radioId, contactID)

    private fun dmMessage(text: String, second: Long, status: MessageStatus = MessageStatus.SENT, outgoing: Boolean = true) =
        RenderingFixtures.testDirectMessage(
            radioId = radioId, contactID = contactID, text = text, timestamp = second.toUInt(),
            createdAt = Instant.ofEpochSecond(second), status = status,
            direction = if (outgoing) MessageDirection.OUTGOING else MessageDirection.INCOMING,
        )

    private fun channelMessage(text: String, second: Long, status: MessageStatus = MessageStatus.SENT) = MessageDTO(
        radioId = radioId, channelIndex = 4u, text = text, timestamp = second.toUInt(), createdAt = Instant.ofEpochSecond(second),
        direction = MessageDirection.OUTGOING, status = status,
    )

    @TestFactory
    fun hardResetCases(): List<DynamicTest> = listOf(
        native("hardReset refetches the DM window, hides sent outgoing reactions and reports the unfiltered count") {
            runRendering {
                val store = RenderingInMemoryMessageStore()
                val kept = listOf(dmMessage("a", 10), dmMessage("👍\nabcdefgh", 11, MessageStatus.FAILED), dmMessage("b", 12, outgoing = false))
                val hidden = dmMessage("👍\nabcdefgh", 13)
                (kept + hidden).forEach { store.saveMessage(it) }
                val coordinator = renderingCoordinator(dmID, store)
                coordinator.append(dmMessage("stale", 11))
                val invalidated = AtomicInteger()
                coordinator.renderStateInvalidated = { invalidated.incrementAndGet() }
                coordinator.hardReset("test")
                assertTrue(coordinator.hardResetInFlight)
                coordinator.hardResetTask?.join()
                assertEquals(kept.map { it.id }, coordinator.messages.map { it.id })
                assertEquals(4L, coordinator.renderState.totalFetchedCount)
                assertFalse(coordinator.renderState.hasMoreMessages)
                assertEquals(ChatRenderState.LoadPhase.LOADED, coordinator.renderState.phase)
                assertEquals(1, invalidated.get())
                assertFalse(coordinator.hardResetInFlight)
            }
        },
        native("hardReset on a channel uses the channel window and channel reaction format") {
            runRendering {
                val store = RenderingInMemoryMessageStore()
                val channelReaction = channelMessage("@[Alice]👍\nabcdefgh", 20)
                val dmFormatText = channelMessage("👍\nabcdefgh", 21)
                listOf(channelReaction, dmFormatText, dmMessage("other conversation", 22)).forEach { store.saveMessage(it) }
                val coordinator = renderingCoordinator(ChatConversationID.channel(radioId, 4u), store)
                coordinator.hardReset("test")
                coordinator.hardResetTask?.join()
                assertEquals(listOf(dmFormatText.id), coordinator.messages.map { it.id })
                assertEquals(2L, coordinator.renderState.totalFetchedCount)
            }
        },
        native("a nil fetch for an id still held in memory triggers a hard reset") {
            runRendering {
                val store = RenderingInMemoryMessageStore()
                val persisted = dmMessage("persisted", 30)
                store.saveMessage(persisted)
                val coordinator = renderingCoordinator(dmID, store)
                val ghost = dmMessage("ghost", 31)
                coordinator.append(ghost)
                coordinator.enqueueReload(ghost.id)
                coordinator.coalescedReloadTask?.join()
                assertNotNull(coordinator.hardResetTask).join()
                assertEquals(listOf(persisted.id), coordinator.messages.map { it.id })
                assertEquals(1, store.windowQueries)
            }
        },
        native("reloads enqueued during a hard reset are buffered and drained after it completes") {
            runRendering {
                val store = RenderingInMemoryMessageStore()
                val message = dmMessage("m", 40)
                store.saveMessage(message)
                val coordinator = renderingCoordinator(dmID, store)
                val gate = CompletableDeferred<Unit>()
                val reachedHook = CompletableDeferred<Unit>()
                coordinator.hardResetAfterFetchHook = {
                    reachedHook.complete(Unit)
                    gate.await()
                }
                coordinator.hardReset("test")
                reachedHook.await()
                coordinator.enqueueReload(message.id)
                assertTrue(message.id in coordinator.pendingReloadIDs)
                assertFalse(coordinator.reloadInFlight, "the scheduler guard holds new drains while a reset is in flight")
                assertNull(coordinator.coalescedReloadTask)
                gate.complete(Unit)
                coordinator.hardResetTask?.join()
                assertNotNull(coordinator.coalescedReloadTask).join()
                assertTrue(coordinator.pendingReloadIDs.isEmpty())
                assertEquals(listOf(EntityKey(radioId, message.id)), store.fetchedKeys)
            }
        },
        native("a running drain stops at the loop top once a hard reset begins, leaving later ids for the reschedule") {
            runRendering {
                val store = RenderingInMemoryMessageStore()
                val first = dmMessage("first", 50)
                val second = dmMessage("second", 51)
                listOf(first, second).forEach { store.saveMessage(it) }
                val coordinator = renderingCoordinator(dmID, store)
                coordinator.replaceAll(listOf(first, second))
                val fetchGate = CompletableDeferred<Unit>()
                val resetGate = CompletableDeferred<Unit>()
                store.fetchGate = fetchGate
                coordinator.hardResetAfterFetchHook = { resetGate.await() }
                coordinator.enqueueReload(first.id)
                store.fetchReachedGate.await() // the drain is mid-fetch for `first`
                coordinator.enqueueReload(second.id) // buffered behind the in-flight drain
                coordinator.hardReset("test") // held in flight by its after-fetch hook
                store.fetchGate = null
                fetchGate.complete(Unit)
                coordinator.coalescedReloadTask?.join()
                assertEquals(listOf(first.id), store.fetchedKeys.map { it.id }, "drain must not fetch after the reset began")
                assertEquals(setOf(second.id), coordinator.pendingReloadIDs)
                assertFalse(coordinator.reloadInFlight)
                resetGate.complete(Unit)
                coordinator.hardResetTask?.join()
                assertNotNull(coordinator.coalescedReloadTask).join()
                assertEquals(listOf(first.id, second.id), store.fetchedKeys.map { it.id })
                assertTrue(coordinator.pendingReloadIDs.isEmpty())
            }
        },
        native("store errors are logged and skipped without rebuild or hard reset") {
            runRendering {
                val store = RenderingInMemoryMessageStore()
                val message = dmMessage("m", 60)
                store.saveMessage(message)
                store.failingFetchIDs = setOf(message.id)
                val coordinator = renderingCoordinator(dmID, store)
                coordinator.append(message)
                val rebuilt = CopyOnWriteArrayList<UUID>()
                coordinator.renderItemRebuilder = { rebuilt.add(it) }
                coordinator.enqueueReload(message.id)
                coordinator.coalescedReloadTask?.join()
                assertTrue(rebuilt.isEmpty())
                assertNull(coordinator.hardResetTask)
                assertFalse(coordinator.reloadInFlight)
            }
        },
        native("a throwing renderItemRebuilder is contained: every refreshed id is offered and later reloads still run") {
            runRendering {
                val store = RenderingInMemoryMessageStore()
                val first = dmMessage("first", 70)
                val second = dmMessage("second", 71)
                listOf(first, second).forEach { store.saveMessage(it) }
                val coordinator = renderingCoordinator(dmID, store)
                coordinator.append(first)
                coordinator.append(second)
                val offered = CopyOnWriteArrayList<UUID>()
                coordinator.renderItemRebuilder = { id ->
                    offered.add(id)
                    if (id == first.id) throw IllegalStateException("view model gone")
                }
                coordinator.enqueueReload(setOf(first.id, second.id))
                coordinator.coalescedReloadTask?.join()
                assertEquals(setOf(first.id, second.id), offered.toSet(), "one throwing call must not skip the rest")
                assertFalse(coordinator.reloadInFlight)
                offered.clear()
                coordinator.enqueueReload(second.id)
                coordinator.coalescedReloadTask?.join()
                assertEquals(listOf(second.id), offered.toList(), "the shared scope must still run later reloads")
            }
        },
        native("cancelling a not-yet-started drain clears the in-flight flag so later events reschedule") {
            runRendering {
                val coordinator = renderingCoordinator(dmID)
                coordinator.enqueueReload(UUID.randomUUID())
                coordinator.cancelInFlight()
                coordinator.coalescedReloadTask?.join()
                assertFalse(coordinator.reloadInFlight)
                coordinator.enqueueReload(UUID.randomUUID())
                assertTrue(coordinator.reloadInFlight)
            }
        },
    )

    @TestFactory
    fun windowLaneCases(): List<DynamicTest> = listOf(
        native("window operations run one at a time in FIFO order; a cancelled waiter does not reorder the lane") {
            runRendering {
                val coordinator = renderingCoordinator(dmID)
                val events = CopyOnWriteArrayList<String>()
                val gate = CompletableDeferred<Unit>()
                val first = launch { coordinator.performWindowOperation { events.add("first-start"); gate.await(); events.add("first-end") } }
                yield()
                val second = launch { coordinator.performWindowOperation { events.add("second") } }
                val third = launch { coordinator.performWindowOperation { events.add("third") } }
                yield()
                second.cancel()
                yield()
                assertEquals(listOf("first-start"), events.toList(), "later operations wait for the first")
                gate.complete(Unit)
                listOf(first, second, third).forEach { it.join() }
                assertEquals(listOf("first-start", "first-end", "third"), events.toList())
            }
        },
        native("the window lane returns the operation result and propagates its failure") {
            runRendering {
                val coordinator = renderingCoordinator(dmID)
                assertEquals(42, coordinator.performWindowOperation { 42 })
                val failure = try {
                    coordinator.performWindowOperation { error("boom") }
                } catch (expected: IllegalStateException) {
                    expected
                }
                assertEquals("boom", failure.message)
                assertEquals("after", coordinator.performWindowOperation { "after" }, "a failed turn still releases the lane")
            }
        },
    )

    @TestFactory
    fun mutationCases(): List<DynamicTest> = listOf(
        native("applyStatusUpdate keeps delivered rows, preserves RTT when nil and refreshes the rendered row") {
            runRendering {
                val coordinator = renderingCoordinator(dmID)
                val message = dmMessage("m", 70, MessageStatus.SENDING)
                coordinator.append(message)
                coordinator.rebuildItems(renderingBuildPairs(coordinator.messages), EnvInputs.DEFAULT)
                coordinator.buildItemsTask?.join()
                coordinator.applyStatusUpdate(message.id, MessageStatus.DELIVERED, roundTripTime = 900u)
                coordinator.applyStatusUpdate(message.id, MessageStatus.SENT)
                assertEquals(MessageStatus.DELIVERED, coordinator.messagesByID[message.id]?.status)
                assertEquals(900u, coordinator.messagesByID[message.id]?.roundTripTime)
                coordinator.applyStatusUpdate(message.id, MessageStatus.PENDING, userInitiated = true)
                assertEquals(MessageStatus.PENDING, coordinator.messagesByID[message.id]?.status)
                assertEquals(900u, coordinator.messagesByID[message.id]?.roundTripTime)
                coordinator.applyStatusUpdate(message.id, MessageStatus.FAILED)
                val row = coordinator.renderState.items.single()
                assertTrue(row.envelope.hasFailed)
                assertEquals(MessageStatus.FAILED, row.footer.status)
                coordinator.applyStatusUpdate(UUID.randomUUID(), MessageStatus.SENT) // unknown id: no-op
                assertEquals(1, coordinator.messages.size)
            }
        },
        native("prepend skips known ids, keeps page order, and list mutations bump the id once each") {
            runRendering {
                val coordinator = renderingCoordinator(dmID)
                val a = dmMessage("a", 1)
                val b = dmMessage("b", 2)
                val c = dmMessage("c", 3)
                coordinator.append(c)
                val before = coordinator.renderStateID
                coordinator.prepend(listOf(a, b, c))
                assertEquals(listOf(a.id, b.id, c.id), coordinator.messages.map { it.id })
                assertEquals(before + 1UL, coordinator.renderStateID)
                coordinator.prepend(listOf(c))
                coordinator.prepend(emptyList())
                assertEquals(before + 1UL, coordinator.renderStateID)
                coordinator.replaceMessagesPreservingByID(listOf(c, a, b))
                assertEquals(listOf(c.id, a.id, b.id), coordinator.messages.map { it.id })
                assertEquals(setOf(a.id, b.id, c.id), coordinator.messagesByID.keys)
                coordinator.remove(UUID.randomUUID())
                assertEquals(before + 2UL, coordinator.renderStateID)
                coordinator.remove(a.id)
                assertEquals(listOf(c.id, b.id), coordinator.messagesFlow.value.map { it.id })
                assertEquals(before + 3UL, coordinator.renderStateID)
            }
        },
        native("render-item mutations bump the id only when the state changes") {
            runRendering {
                val coordinator = renderingCoordinator(dmID)
                val message = dmMessage("m", 80)
                val item = MessageFragmentBuilder.makeItem(message, RenderingFixtures.makeInputs(message.id), EnvInputs.DEFAULT)
                val start = coordinator.renderStateID
                coordinator.appendRenderItem(item)
                assertEquals(start + 1UL, coordinator.renderStateID)
                assertEquals(0L, coordinator.renderState.itemIndexByID[message.id])
                assertEquals(1L, coordinator.renderState.totalFetchedCount)
                coordinator.updateRenderItem(message.id) { it }
                coordinator.updateRenderItem(UUID.randomUUID()) { it }
                coordinator.removeRenderItem(UUID.randomUUID())
                assertEquals(start + 1UL, coordinator.renderStateID)
                coordinator.updateRenderItem(message.id) { it.with(grouping = it.grouping.copy(showTimestamp = true)) }
                assertEquals(start + 2UL, coordinator.renderStateID)
                coordinator.removeRenderItem(message.id)
                assertTrue(coordinator.renderState.items.isEmpty())
                assertEquals(start + 3UL, coordinator.renderStateID)
            }
        },
    )

    @TestFactory
    fun rebuildCases(): List<DynamicTest> = listOf(
        native("rebuildItems builds on the build dispatcher, applies items and index, keeps phase, runs postApply once") {
            runRendering {
                val dispatches = AtomicInteger()
                val counting = object : CoroutineDispatcher() {
                    override fun dispatch(context: CoroutineContext, block: Runnable) {
                        dispatches.incrementAndGet()
                        Dispatchers.Default.dispatch(context, block)
                    }
                }
                val coordinator = ChatCoordinator(dmID, RenderingInMemoryMessageStore(), this, counting)
                val messages = List(3) { dmMessage("m$it", 90L + it) }
                coordinator.replaceAll(messages)
                val applied = AtomicInteger()
                coordinator.rebuildItems(renderingBuildPairs(messages), EnvInputs.DEFAULT) { applied.incrementAndGet() }
                coordinator.buildItemsTask?.join()
                assertTrue(dispatches.get() > 0, "the pure build must run on the build dispatcher")
                assertEquals(messages.map { it.id }, coordinator.renderState.items.map { it.id })
                assertEquals(2L, coordinator.renderState.itemIndexByID[messages[2].id])
                assertEquals(ChatRenderState.LoadPhase.LOADED, coordinator.renderState.phase)
                assertEquals(1, applied.get())
            }
        },
        native("back-to-back rebuilds: the last scheduled wins and the superseded build never applies") {
            runRendering {
                val coordinator = renderingCoordinator(dmID)
                val first = dmMessage("first", 100)
                val second = dmMessage("second", 101)
                val postApplies = CopyOnWriteArrayList<String>()
                val invalidated = AtomicInteger()
                coordinator.renderStateInvalidated = { invalidated.incrementAndGet() }
                coordinator.rebuildItems(renderingBuildPairs(listOf(first)), EnvInputs.DEFAULT) { postApplies.add("first") }
                val firstJob = coordinator.buildItemsTask
                coordinator.rebuildItems(renderingBuildPairs(listOf(second)), EnvInputs.DEFAULT) { postApplies.add("second") }
                coordinator.buildItemsTask?.join()
                firstJob?.join()
                assertTrue(firstJob?.isCancelled == true)
                assertEquals(listOf(second.id), coordinator.renderState.items.map { it.id })
                assertEquals(listOf("second"), postApplies.toList())
                assertEquals(0, invalidated.get())
            }
        },
    )

    private fun native(name: String, body: () -> Unit) = DynamicTest.dynamicTest("WP-213::$name", body)
}
