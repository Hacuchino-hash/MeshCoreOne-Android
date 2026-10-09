// AndroidOnly: WP-313 CLI recorder and fault doubles shared by the remote-node settings tests.
package com.meshcoreone.android.feature.remotenodes.settings

import com.meshcoreone.android.core.contracts.domain.EntityKey
import com.meshcoreone.android.feature.remotenodes.dependencies.RemoteNodeFaultClassifier
import kotlin.time.Duration

/** Stands in for `RemoteNodeError.timeout`. */
internal class FakeTimeout : Exception("Request timed out")

/** Classifies [FakeTimeout] as the service timeout, as app wiring does for `RemoteNodeError.timeout`. */
internal object TestFaults : RemoteNodeFaultClassifier {
    override fun isTimeout(error: Throwable): Boolean = error is FakeTimeout
    override fun isRemoteNoResponseYet(error: Throwable): Boolean = false
    override fun isBinarySessionTimeout(error: Throwable): Boolean = false
}

/** Swift `CommandRecorder`: records every CLI command and replies per command or with [reply]. */
internal class CommandRecorder(var reply: String = "OK") {
    val commands = mutableListOf<String>()
    val timeouts = mutableListOf<Duration>()
    val sessions = mutableListOf<EntityKey>()
    var repliesByCommand: MutableMap<String, String> = mutableMapOf()
    var errorsByCommand: MutableMap<String, Exception> = mutableMapOf()
    var onSend: (suspend (String) -> Unit)? = null

    suspend fun send(session: EntityKey, command: String, timeout: Duration): String {
        commands += command
        timeouts += timeout
        sessions += session
        onSend?.invoke(command)
        errorsByCommand[command]?.let { throw it }
        return repliesByCommand[command] ?: reply
    }

    fun resetCommands() = commands.clear()
}
