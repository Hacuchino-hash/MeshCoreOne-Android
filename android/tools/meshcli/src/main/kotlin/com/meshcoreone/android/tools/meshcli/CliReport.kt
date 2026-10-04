// AndroidOnly: WP-109 Explicit diagnostic JSON; keys, PINs and message bodies never enter output.
package com.meshcoreone.android.tools.meshcli

import com.meshcoreone.android.core.protocol.model.BatteryInfo
import com.meshcoreone.android.core.protocol.model.DeviceCapabilities
import com.meshcoreone.android.core.protocol.session.ChannelFetchResult
import com.meshcoreone.android.core.protocol.session.ContactFetchResult
import com.meshcoreone.android.core.protocol.session.ContactStreamProgress
import java.time.Instant

internal object CliJson {
    fun text(value: String): String = buildString {
        append('"')
        for (character in value) {
            when {
                character == '"' -> append("\\\"")
                character == '\\' -> append("\\\\")
                character.code < 32 || character.code in 127..159 ||
                    character.code in 0x202a..0x202e || character.code in 0x2066..0x2069 ->
                    append("\\u").append(character.code.toString(16).padStart(4, '0'))
                else -> append(character)
            }
        }
        append('"')
    }

    fun fields(vararg values: Pair<String, String>): String =
        values.joinToString(",", "{", "}") { (key, value) -> "${text(key)}:$value" }
    fun array(values: Iterable<String>): String = values.joinToString(",", "[", "]")
}

internal data class CliReport(val json: String, val issue: CliIssue? = null) {
    companion object {
        fun device(command: CliCommand, value: DeviceCapabilities) = CliReport(CliJson.fields(
            "status" to CliJson.text("complete"),
            "command" to CliJson.text(command.spelling),
            "firmware_protocol" to value.firmwareVersion.toString(),
            "capacity_known" to (value.firmwareVersion >= 3u).toString(),
            "max_contacts" to value.maxContacts.toString(),
            "max_channels" to value.maxChannels.toString(),
            "firmware_build" to CliJson.text(value.firmwareBuild),
            "model" to CliJson.text(value.model),
            "version" to CliJson.text(value.version),
            "client_repeat" to value.clientRepeat.toString(),
            "path_hash_mode" to value.pathHashMode.toString(),
        ))

        fun battery(value: BatteryInfo) = CliReport(CliJson.fields(
            "status" to CliJson.text("complete"), "command" to CliJson.text("battery"),
            "millivolts" to value.level.toString(),
            "used_storage_kb" to (value.usedStorageKB?.toString() ?: "null"),
            "total_storage_kb" to (value.totalStorageKB?.toString() ?: "null"),
        ))

        fun time(value: Instant) = CliReport(CliJson.fields(
            "status" to CliJson.text("complete"), "command" to CliJson.text("time"),
            "unix_seconds" to value.epochSecond.toString(),
        ))

        fun contacts(value: ContactFetchResult, completed: Boolean): CliReport {
            val canonical = value.contacts.all { it.publicKey.size == 32 && it.id == it.publicKey.hexString }
            val unique = value.contacts.map { it.id }.distinct().size == value.contacts.size
            val complete = completed && canonical && unique && value.reportedTotal == value.contacts.size.toLong()
            return CliReport(CliJson.fields(
                "status" to CliJson.text(if (complete) "complete" else "partial"),
                "command" to CliJson.text("contacts"),
                "received" to value.contacts.size.toString(),
                "reported_total" to (value.reportedTotal?.toString() ?: "null"),
                "contacts" to CliJson.array(value.contacts.map { contact ->
                    CliJson.fields(
                        "id" to CliJson.text(contact.publicKey.hexString),
                        "name" to CliJson.text(contact.advertisedName),
                        "type_raw" to contact.typeRawValue.toString(),
                        "flags_raw" to contact.flags.rawValue.toString(),
                        "path_length_raw" to contact.outPathLength.toString(),
                        "last_modified_unix_seconds" to contact.lastModified.epochSecond.toString(),
                    )
                }),
            ), if (complete) null else CliIssue(CliExit.PARTIAL, "incomplete_contact_snapshot"))
        }

        fun contactProgress(value: ContactStreamProgress) = CliReport(CliJson.fields(
            "status" to CliJson.text("partial"), "command" to CliJson.text("contacts"),
            "accepted_count" to value.receivedCount.toString(),
            "reported_total" to (value.reportedTotal?.toString() ?: "null"),
            "complete" to "false",
        ), CliIssue(CliExit.PARTIAL, "interrupted_contact_stream"))

        fun channels(value: ChannelFetchResult): CliReport {
            val complete = value.missing.isEmpty()
            return CliReport(CliJson.fields(
                "status" to CliJson.text(if (complete) "complete" else "partial"),
                "command" to CliJson.text("channels"),
                "received" to value.received.size.toString(),
                "missing" to CliJson.array(value.missing.map { it.toString() }),
                "channels" to CliJson.array(value.received.map { channel ->
                    CliJson.fields("index" to channel.index.toString(), "name" to CliJson.text(channel.name))
                }),
            ), if (complete) null else CliIssue(CliExit.PARTIAL, "missing_channel_slots"))
        }
    }
}

internal fun CliIssue.json(): String = CliJson.fields(
    "status" to CliJson.text("error"), "kind" to CliJson.text(exit.name.lowercase(java.util.Locale.ROOT)),
    "code" to CliJson.text(code), "exit" to exit.value.toString(),
    "device_code" to (deviceCode?.toString() ?: "null"),
)
