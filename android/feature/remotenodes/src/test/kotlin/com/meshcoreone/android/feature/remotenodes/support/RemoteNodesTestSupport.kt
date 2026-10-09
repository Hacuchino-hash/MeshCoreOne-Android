// AndroidOnly: WP-313 Original-case binding, virtual clock and blocking runner for the remote-node JVM suite.
package com.meshcoreone.android.feature.remotenodes.support

import com.meshcoreone.android.core.contracts.domain.EntityKey
import com.meshcoreone.android.core.model.RadioId
import com.meshcoreone.android.core.model.RemoteNodeRole
import com.meshcoreone.android.core.model.RemoteNodeSessionDTO
import com.meshcoreone.android.core.model.RoomPermissionLevel
import com.meshcoreone.android.core.protocol.bytes.Bytes
import com.meshcoreone.android.feature.remotenodes.common.RemoteNodesClock
import java.time.Instant
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield

/**
 * Original-case binding carried in source (same mechanism as core:data / core:connectivity): the
 * JUnit XML names the method; this annotation binds it to the frozen Swift case id and disposition
 * (`source-behavior`, `native-equivalent` or `platform-adaptation`).
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
@Repeatable
internal annotation class OriginalCase(val value: String, val disposition: String = "source-behavior")

internal val EPOCH: Instant = Instant.ofEpochSecond(1_704_067_200)

/**
 * Virtual wall + monotonic clock. [sleep] records the request and advances time immediately (then
 * yields), so retry budgets, success flashes and the 60 s discovery loop run instantly and
 * deterministically.
 */
internal class VirtualClock(start: Instant = EPOCH) : RemoteNodesClock {
    private val lock = Any()
    private var offsetNanos = 0L
    private val origin = start
    private val recorded = mutableListOf<Duration>()

    override val now: Instant get() = synchronized(lock) { origin.plusNanos(offsetNanos) }
    override val elapsed: Duration get() = synchronized(lock) { offsetNanos }.nanoseconds
    val sleeps: List<Duration> get() = synchronized(lock) { recorded.toList() }

    override suspend fun sleep(duration: Duration) {
        synchronized(lock) { recorded += duration }
        advance(duration)
        yield()
    }

    fun advance(duration: Duration) {
        synchronized(lock) { offsetNanos += duration.inWholeNanoseconds }
    }

    fun set(instant: Instant) {
        synchronized(lock) { offsetNanos = java.time.Duration.between(origin, instant).toNanos() }
    }

    fun advanceWall(duration: java.time.Duration) = advance(duration.toNanos().nanoseconds)
}

/** Runs a suspending test body on a blocking event loop with a real-time safety cap. */
internal fun runSuspend(block: suspend CoroutineScope.() -> Unit) {
    runBlocking { withTimeout(30.seconds) { block() } }
}

internal val TEST_RADIO: RadioId = RadioId(UUID.fromString("00000000-0000-0000-0000-0000000000aa"))

internal fun bytes(count: Int, value: Int): Bytes = Bytes(ByteArray(count) { value.toByte() })

internal fun session(
    publicKey: Bytes = bytes(32, 0x42),
    name: String = "Test Node",
    role: RemoteNodeRole = RemoteNodeRole.REPEATER,
    isConnected: Boolean = true,
    permissionLevel: RoomPermissionLevel = RoomPermissionLevel.ADMIN,
    latitude: Double = 0.0,
    longitude: Double = 0.0,
): RemoteNodeSessionDTO = RemoteNodeSessionDTO(
    radioId = TEST_RADIO, publicKey = publicKey, name = name, role = role, isConnected = isConnected,
    permissionLevel = permissionLevel, latitude = latitude, longitude = longitude,
)

internal val RemoteNodeSessionDTO.key: EntityKey get() = EntityKey(radioId, id)

internal fun java.time.Duration.toKotlin(): Duration = toNanos().nanoseconds

internal fun Duration.toJava(): java.time.Duration = toJavaDuration()
