// AndroidOnly: WP-109 Executable dev-only TCP diagnostics using the real session, socket and scoped teardown.
package com.meshcoreone.android.tools.meshcli

import com.meshcoreone.android.core.protocol.config.MeshCoreException
import com.meshcoreone.android.core.protocol.config.SessionConfiguration
import com.meshcoreone.android.core.protocol.event.EventFilter
import com.meshcoreone.android.core.protocol.event.MeshEvent
import com.meshcoreone.android.core.protocol.model.ErrorCode
import com.meshcoreone.android.core.protocol.session.MeshCoreSession
import com.meshcoreone.android.core.protocol.session.SessionClock
import com.meshcoreone.android.core.protocol.session.SessionCorrelationException
import com.meshcoreone.android.core.protocol.session.SessionDiagnostic
import com.meshcoreone.android.core.protocol.session.SystemSessionClock
import com.meshcoreone.android.core.protocol.transport.tcp.WiFiFrameException
import com.meshcoreone.android.core.protocol.transport.tcp.WiFiTransport
import com.meshcoreone.android.core.protocol.transport.tcp.WiFiTransportError
import com.meshcoreone.android.core.protocol.transport.tcp.WiFiTransportException
import java.io.BufferedWriter
import java.io.IOException
import java.io.OutputStreamWriter
import java.io.Writer
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.selects.select

class CliConsole(val stdout: Writer, val stderr: Writer) {
    internal fun output(text: String) { stdout.write(text); stdout.write("\n"); stdout.flush() }
    internal fun error(issue: CliIssue) { stderr.write(issue.json()); stderr.write("\n"); stderr.flush() }
}

class CliRuntime(
    val clock: SessionClock = SystemSessionClock(),
    val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    val socketFactory: () -> Socket = { Socket() },
)

object MeshCli {
    @JvmStatic
    fun main(arguments: Array<String>) {
        val console = CliConsole(
            BufferedWriter(OutputStreamWriter(System.out, Charsets.UTF_8)),
            BufferedWriter(OutputStreamWriter(System.err, Charsets.UTF_8)),
        )
        val code = try {
            runBlocking { execute(arguments, console) }
        } catch (_: CancellationException) {
            CliExit.CANCELLED.value
        }
        exitProcess(code)
    }

    suspend fun execute(
        arguments: Array<String>, console: CliConsole, runtime: CliRuntime = CliRuntime(),
    ): Int {
        val invocation = try {
            CliOptions.parse(arguments)
        } catch (failure: CliFailure) {
            console.error(failure.issue)
            return failure.issue.exit.value
        }
        if (invocation is CliInvocation.Help) {
            return output(console) { console.output(CliOptions.help); CliExit.OK.value }
        }
        val query = invocation as CliInvocation.Query
        return supervisorScope {
            val critical = CompletableDeferred<CliIssue>()
            val transport = WiFiTransport(
                ioDispatcher = runtime.ioDispatcher, socketFactory = runtime.socketFactory,
                connectionTimeoutMillis = query.deadlineMillis, writeTimeoutMillis = query.deadlineMillis,
                addressResolver = { _, port -> InetSocketAddress(query.address, port) },
            )
            transport.setConnectionInfo(query.address.hostAddress, query.port)
            val seconds = query.deadlineMillis / 1000.0
            val session = MeshCoreSession(
                transport, SessionConfiguration(
                    defaultTimeout = seconds, clientIdentifier = "MCore",
                    contactStreamInactivityTimeout = minOf(seconds, 15.0),
                    contactStreamHardTimeout = seconds,
                    channelPipelineIdleTimeout = minOf(seconds / 4, 1.5),
                    channelPipelineHardTimeout = seconds, channelPipelinePostDrainGrace = 0.0,
                ), runtime.clock, coroutineContext,
                onDiagnostic = { diagnostic ->
                    if (diagnostic is SessionDiagnostic.ParseFailure) {
                        critical.complete(CliIssue(CliExit.PROTOCOL, "malformed_packet"))
                    }
                },
            )
            val stream = session.events(EventFilter {
                it is MeshEvent.Error || it is MeshEvent.ParseFailure || it is MeshEvent.Disabled
            })
            val monitor = launch(start = CoroutineStart.UNDISPATCHED) {
                stream.collect { event ->
                    critical.complete(when (event) {
                        is MeshEvent.Error -> deviceIssue(event.code?.toInt())
                        is MeshEvent.Disabled -> CliIssue(CliExit.UNSUPPORTED, "device_disabled")
                        else -> CliIssue(CliExit.PROTOCOL, "malformed_packet")
                    })
                }
            }
            var report: CliReport? = null
            var issue: CliIssue? = null
            var cleanupIssue: CliIssue? = null
            var cancelled: CancellationException? = null
            try {
                val completed = deadline(runtime.clock, query.deadlineMillis) {
                    val operation = async {
                        session.start()
                        read(session, query)
                    }
                    try {
                        select {
                            critical.onAwait { throw CliFailure(it) }
                            operation.onAwait { it }
                        }
                    } finally {
                        withContext(NonCancellable) { operation.cancelAndJoin() }
                    }
                }
                report = completed
                issue = completed.issue
            } catch (failure: CliFailure) {
                issue = failure.issue
            } catch (failure: MeshCoreException) {
                issue = protocolIssue(failure)
            } catch (failure: IOException) {
                issue = ioIssue(failure)
            } catch (failure: CancellationException) {
                cancelled = failure
                issue = CliIssue(CliExit.CANCELLED, "cancelled")
            } finally {
                if (query.command == CliCommand.CONTACTS && report == null) {
                    session.lastContactFetchProgress?.let { report = CliReport.contactProgress(it) }
                }
                withContext(NonCancellable) {
                    try {
                        session.stop()
                    } catch (failure: IOException) {
                        cleanupIssue = CliIssue(CliExit.TRANSPORT, "teardown_failed")
                        if (issue == null) issue = ioIssue(failure)
                    } catch (failure: MeshCoreException) {
                        cleanupIssue = CliIssue(CliExit.PROTOCOL, "teardown_failed")
                        if (issue == null) issue = protocolIssue(failure)
                    } finally {
                        // Finish drains the filtered queue, including an error coalesced with a valid reply.
                        monitor.join()
                        if (critical.isCompleted && !critical.isCancelled) {
                            val observed = critical.await()
                            if (issue == null || issue?.exit == CliExit.PARTIAL) issue = observed
                        }
                        critical.cancel()
                    }
                }
            }
            val result = output(console) {
                if (issue == null) {
                    console.output(checkNotNull(report).json)
                } else {
                    if (report?.issue?.exit == CliExit.PARTIAL) report?.let { console.output(it.json) }
                    console.error(checkNotNull(issue))
                    cleanupIssue?.let { console.error(it) }
                }
                issue?.exit?.value ?: CliExit.OK.value
            }
            cancelled?.let { throw it }
            result
        }
    }

    private suspend fun read(session: MeshCoreSession, query: CliInvocation.Query): CliReport = when (query.command) {
        CliCommand.DEVICE, CliCommand.CAPABILITIES -> CliReport.device(query.command, session.queryDevice())
        CliCommand.BATTERY -> CliReport.battery(session.getBattery())
        CliCommand.TIME -> CliReport.time(session.getTime())
        CliCommand.CONTACTS -> {
            val result = session.getContactsReportingTotal()
            CliReport.contacts(result, session.lastContactFetchProgress?.completed == true && !session.isContactsDirty)
        }
        CliCommand.CHANNELS -> {
            val capabilities = session.queryDevice()
            if (capabilities.maxChannels !in 1..256) {
                throw CliFailure(CliIssue(CliExit.UNSUPPORTED, "channel_capacity_unavailable"))
            }
            val indices = query.indices ?: (0 until capabilities.maxChannels.toInt()).map(Int::toUByte)
            if (indices.any { it.toLong() >= capabilities.maxChannels }) {
                throw CliFailure(CliIssue(CliExit.UNSUPPORTED, "channel_index_unavailable"))
            }
            CliReport.channels(session.getChannels(indices))
        }
    }

    private suspend fun <T> deadline(clock: SessionClock, millis: Long, action: suspend CoroutineScope.() -> T): T =
        supervisorScope {
            val timer = async(start = CoroutineStart.UNDISPATCHED) { clock.sleepFor(millis.milliseconds) }
            val result = async { action() }
            try {
                select {
                    result.onAwait { it }
                    timer.onAwait { throw CliFailure(CliIssue(CliExit.TIMEOUT, "overall_deadline")) }
                }
            } finally {
                withContext(NonCancellable) { result.cancelAndJoin(); timer.cancelAndJoin() }
            }
        }

    private inline fun output(console: CliConsole, action: () -> Int): Int = try {
        action()
    } catch (_: IOException) {
        console.error(CliIssue(CliExit.OUTPUT, "output_failed"))
        CliExit.OUTPUT.value
    }

    private fun deviceIssue(code: Int?): CliIssue = when (code) {
        ErrorCode.UNSUPPORTED_COMMAND.rawValue.toInt() -> CliIssue(CliExit.UNSUPPORTED, "unsupported_command", code)
        null -> CliIssue(CliExit.PROTOCOL, "missing_device_error_code")
        else -> CliIssue(CliExit.PROTOCOL, "device_error", code)
    }

    private fun protocolIssue(failure: MeshCoreException): CliIssue = when (failure) {
        is MeshCoreException.Timeout -> CliIssue(CliExit.TIMEOUT, "response_timeout")
        is MeshCoreException.DeviceError -> deviceIssue(failure.code.toInt())
        is MeshCoreException.FeatureDisabled -> CliIssue(CliExit.UNSUPPORTED, "device_disabled")
        is MeshCoreException.ConnectionLost -> {
            val causes = generateSequence<Throwable>(failure) { it.cause }.take(16).toList()
            when {
                causes.any { it is WiFiFrameException } -> CliIssue(CliExit.PROTOCOL, "truncated_frame")
                causes.any { it is SessionCorrelationException } -> CliIssue(CliExit.PROTOCOL, "unsafe_correlation")
                else -> CliIssue(CliExit.TRANSPORT, "connection_lost")
            }
        }
        else -> CliIssue(CliExit.PROTOCOL, "invalid_protocol_result")
    }

    private fun ioIssue(failure: IOException): CliIssue = when {
        failure is WiFiFrameException -> CliIssue(CliExit.PROTOCOL, "invalid_frame")
        failure is WiFiTransportException && failure.error in setOf(
            WiFiTransportError.ConnectionTimeout, WiFiTransportError.SendTimeout,
        ) -> CliIssue(CliExit.TIMEOUT, "transport_timeout")
        else -> CliIssue(CliExit.TRANSPORT, "socket_failure")
    }
}
