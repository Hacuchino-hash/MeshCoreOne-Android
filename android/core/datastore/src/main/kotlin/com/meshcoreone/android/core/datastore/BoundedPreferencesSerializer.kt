// AndroidOnly: WP-204 Bounded official Preferences serialization without corruption replacement.
package com.meshcoreone.android.core.datastore

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesFileSerializer
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal class BoundedPreferencesSerializer : Serializer<Preferences> {
    override val defaultValue: Preferences get() = PreferencesFileSerializer.defaultValue

    override suspend fun readFrom(input: InputStream): Preferences {
        val bytes = readBounded(input, MAXIMUM_BYTES, StorageProblem.CorruptPreferences)
        return PreferencesFileSerializer.readFrom(ByteArrayInputStream(bytes)).also {
            validateKnownPreferences(PreferenceSnapshot.from(it))
        }
    }

    override suspend fun writeTo(t: Preferences, output: OutputStream) {
        validateKnownPreferences(PreferenceSnapshot.from(t))
        val buffer = BoundedOutput(MAXIMUM_BYTES)
        PreferencesFileSerializer.writeTo(t, buffer)
        currentCoroutineContext().ensureActive()
        buffer.writeTo(output)
    }

    companion object { const val MAXIMUM_BYTES = 1_048_576 }
}

internal suspend fun readBounded(input: InputStream, maximum: Int, corrupt: StorageProblem): ByteArray {
    val output = ByteArrayOutputStream(minOf(maximum, 8192))
    val chunk = ByteArray(8192)
    while (true) {
        currentCoroutineContext().ensureActive()
        val count = input.read(chunk)
        if (count == -1) return output.toByteArray()
        if (count == 0) throw CorruptionException(
            "Non-progressing bounded storage read",
            StorageFailure(corrupt, StorageOperation.READ),
        )
        if (count > maximum - output.size()) {
            throw StorageFailure(StorageProblem.StateTooLarge(maximum), StorageOperation.READ)
        }
        output.write(chunk, 0, count)
    }
}

internal class BoundedOutput(private val maximum: Int) : ByteArrayOutputStream(minOf(maximum, 8192)) {
    override fun write(b: Int) {
        checkSize(1)
        super.write(b)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        checkSize(len)
        super.write(b, off, len)
    }

    private fun checkSize(additional: Int) {
        if (additional < 0 || additional > maximum - count) {
            throw StorageFailure(StorageProblem.StateTooLarge(maximum), StorageOperation.WRITE)
        }
    }
}
