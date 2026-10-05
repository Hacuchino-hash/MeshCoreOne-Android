// AndroidOnly: WP-204 Ordered immutable string-array preferences; DataStore sets cannot preserve order.
package com.meshcoreone.android.core.datastore

import com.meshcoreone.android.core.model.SnapshotList
import com.meshcoreone.android.core.model.snapshot
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException

internal object OrderedStrings {
    private const val MAGIC = 0x4D43314C
    private const val MAXIMUM_COUNT = 4096
    private const val MAXIMUM_STRING_BYTES = 16_384

    fun encode(strings: SnapshotList<String>): ByteArray {
        if (strings.size > MAXIMUM_COUNT) {
            throw StorageFailure(
                StorageProblem.InvalidPreference(AppStorageKey.recentReactionEmojis.rawValue), StorageOperation.WRITE,
            )
        }
        val buffer = BoundedOutput(BoundedPreferencesSerializer.MAXIMUM_BYTES)
        DataOutputStream(buffer).apply {
            writeInt(MAGIC)
            writeInt(strings.size)
            strings.forEach { writeCheckedString(it, MAXIMUM_STRING_BYTES) }
        }
        return buffer.toByteArray()
    }

    fun decode(raw: ByteArray): SnapshotList<String> {
        try {
            val input = DataInputStream(ByteArrayInputStream(raw))
            if (input.readInt() != MAGIC) corrupt()
            val count = input.readInt()
            if (count !in 0..MAXIMUM_COUNT) corrupt()
            val strings = mutableListOf<String>()
            repeat(count) { strings += input.readCheckedString(MAXIMUM_STRING_BYTES) }
            if (input.available() != 0) corrupt()
            return strings.snapshot()
        } catch (failure: EOFException) {
            throw StorageFailure(StorageProblem.CorruptPreferences, StorageOperation.READ, failure)
        }
    }

    private fun corrupt(): Nothing =
        throw StorageFailure(StorageProblem.CorruptPreferences, StorageOperation.READ)
}

class RecentEmojiPreferences(private val store: PreferenceStore) {
    suspend fun recentEmojis(): SnapshotList<String> =
        store.get(AppStorageKey.recentReactionEmojis).ifEmpty { DEFAULT_EMOJIS }.snapshot()

    suspend fun recordUsage(emoji: String) {
        store.update {
            val current = snapshot[AppStorageKey.recentReactionEmojis].ifEmpty { DEFAULT_EMOJIS }
            this[AppStorageKey.recentReactionEmojis] = (listOf(emoji) + current.filterNot { it == emoji }).take(6).snapshot()
        }
    }

    companion object {
        val DEFAULT_EMOJIS: SnapshotList<String> = listOf(
            "\uD83D\uDC4D", "\uD83D\uDC4E", "\u2764\uFE0F", "\uD83D\uDE02", "\uD83D\uDE2E", "\uD83D\uDE22",
        ).snapshot()
    }
}
