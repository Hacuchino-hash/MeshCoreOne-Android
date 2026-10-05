// AndroidOnly: WP-109 Validated, read-only operator arguments; no automatic endpoint or DNS lookup.
package com.meshcoreone.android.tools.meshcli

import java.net.InetAddress
import java.net.UnknownHostException

enum class CliCommand(val spelling: String) {
    DEVICE("device"), CAPABILITIES("capabilities"), BATTERY("battery"),
    TIME("time"), CONTACTS("contacts"), CHANNELS("channels"),
}

internal sealed interface CliInvocation {
    data object Help : CliInvocation
    data class Query(
        val address: InetAddress,
        val port: Int,
        val deadlineMillis: Long,
        val command: CliCommand,
        val indices: List<UByte>?,
    ) : CliInvocation
}

internal enum class CliExit(val value: Int) {
    OK(0), USAGE(2), TIMEOUT(3), PROTOCOL(4), TRANSPORT(5),
    PARTIAL(6), UNSUPPORTED(7), OUTPUT(8), CANCELLED(130),
}

internal data class CliIssue(val exit: CliExit, val code: String, val deviceCode: Int? = null)
internal class CliFailure(val issue: CliIssue) : Exception(issue.code)

internal object CliOptions {
    const val DEFAULT_PORT = 5000
    const val DEFAULT_DEADLINE_MILLIS = 5000L
    const val MIN_DEADLINE_MILLIS = 25L
    const val MAX_DEADLINE_MILLIS = 120_000L

    val help = """
        meshcli --host IP [--port PORT] [--deadline-ms MILLISECONDS] [COMMAND]
        Commands: device (default), capabilities, battery, time, contacts, channels
        channels accepts --indices 0,2,7; otherwise reads the device's advertised slots.
        --help or -h prints this text without opening a socket.
        IP must be an IPv4/IPv6 literal. Resolve hostnames separately; this tool never does DNS.
        Port: 1..65535 (default 5000). Overall deadline: 25..120000 ms (default 5000).
        Read-only local companion diagnostics; no sends, message drains, keys, PINs or admin commands.
        Explicit endpoint selection is manual operator authorization, not hardware certification.
        JSON goes to stdout; sanitized typed errors go to stderr. Any incomplete query exits nonzero.
        Exit codes: 0 complete, 2 usage, 3 timeout, 4 protocol, 5 transport,
        6 partial, 7 unsupported, 8 output, 130 cooperative cancellation.
    """.trimIndent()

    fun parse(arguments: Array<String>): CliInvocation {
        val args = arguments.toList()
        if (args == listOf("--help") || args == listOf("-h")) return CliInvocation.Help
        if (args.size > 20 || args.sumOf { it.toByteArray(Charsets.UTF_8).size.toLong() } > 8192) {
            usage("arguments_too_large")
        }
        var host: String? = null
        var port = DEFAULT_PORT
        var deadline = DEFAULT_DEADLINE_MILLIS
        var command: CliCommand? = null
        var indices: List<UByte>? = null
        val seen = mutableSetOf<String>()
        var position = 0
        while (position < args.size) {
            val token = args[position++]
            if (token.startsWith("-")) {
                if (token !in setOf("--host", "--port", "--deadline-ms", "--indices")) usage("unknown_option")
                if (!seen.add(token)) usage("duplicate_option")
                if (position == args.size || args[position].startsWith("-")) usage("missing_option_value")
                val value = args[position++]
                when (token) {
                    "--host" -> host = value
                    "--port" -> port = number(value, 1, 65535, "invalid_port").toInt()
                    "--deadline-ms" -> deadline = number(
                        value, MIN_DEADLINE_MILLIS, MAX_DEADLINE_MILLIS, "invalid_deadline",
                    )
                    "--indices" -> {
                        val pieces = value.split(',')
                        if (pieces.size > 256 || pieces.any(String::isEmpty)) usage("invalid_indices")
                        indices = pieces.map { number(it, 0, 255, "invalid_indices").toUByte() }
                        if (indices.distinct().size != indices.size) usage("duplicate_index")
                    }
                }
            } else {
                if (command != null) usage("multiple_commands")
                command = CliCommand.entries.firstOrNull { it.spelling == token } ?: usage("unknown_command")
            }
        }
        val selected = command ?: CliCommand.DEVICE
        if (indices != null && selected != CliCommand.CHANNELS) usage("indices_require_channels")
        return CliInvocation.Query(
            literalAddress(host ?: usage("host_required")), port, deadline, selected, indices?.toList(),
        )
    }

    private fun number(value: String, minimum: Long, maximum: Long, code: String): Long {
        if (value.isEmpty() || value.any { it !in '0'..'9' }) usage(code)
        return value.toLongOrNull()?.takeIf { it in minimum..maximum } ?: usage(code)
    }

    private fun literalAddress(value: String): InetAddress {
        val text = if (value.startsWith('[') && value.endsWith(']')) value.substring(1, value.length - 1) else value
        if (text.length > 45) usage("ip_literal_required")
        if (':' in text) {
            if (text.any { it !in "0123456789abcdefABCDEF:." }) usage("ip_literal_required")
            return try {
                // A colon and this alphabet force the JDK's numeric IPv6 parser, never hostname resolution.
                InetAddress.getByName(text)
            } catch (_: UnknownHostException) {
                usage("invalid_ip_literal")
            }
        }
        val parts = text.split('.')
        if (parts.size != 4 || parts.any { !Regex("0|[1-9][0-9]{0,2}").matches(it) }) usage("ip_literal_required")
        val bytes = parts.map { number(it, 0, 255, "invalid_ip_literal").toByte() }.toByteArray()
        return InetAddress.getByAddress(bytes)
    }

    private fun usage(code: String): Nothing = throw CliFailure(CliIssue(CliExit.USAGE, code))
}
