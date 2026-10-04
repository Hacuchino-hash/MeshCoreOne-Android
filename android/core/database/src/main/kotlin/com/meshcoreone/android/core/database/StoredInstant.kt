// AndroidOnly: WP-201 Exact SQLite date ordering/round-trip without millisecond or floating-point narrowing.
package com.meshcoreone.android.core.database

import java.time.Instant

data class StoredInstant(val seconds: Long, val nanos: Int) {
    init {
        if (nanos !in 0..999_999_999) throw DatabaseValueException("Instant.nanos", "Nanoseconds outside 0..999999999")
    }
    fun toInstant(): Instant = Instant.ofEpochSecond(seconds, nanos.toLong())
    companion object { fun from(instant: Instant): StoredInstant = StoredInstant(instant.epochSecond, instant.nano) }
}

class DatabaseValueException(val field: String, reason: String, cause: Throwable? = null) :
    IllegalStateException("Invalid stored $field: $reason", cause)

internal fun Long.uint(field: String): UInt {
    if (this !in 0L..0xFFFF_FFFFL) throw DatabaseValueException(field, "Does not fit UInt32")
    return toUInt()
}
internal fun Long.ushort(field: String): UShort {
    if (this !in 0L..65535L) throw DatabaseValueException(field, "Does not fit UInt16")
    return toUShort()
}
internal fun Long.ubyte(field: String): UByte {
    if (this !in 0L..255L) throw DatabaseValueException(field, "Does not fit UInt8")
    return toUByte()
}
internal fun Long.short(field: String): Short {
    if (this !in -32768L..32767L) throw DatabaseValueException(field, "Does not fit Int16")
    return toShort()
}
internal fun Long.byte(field: String): Byte {
    if (this !in -128L..127L) throw DatabaseValueException(field, "Does not fit Int8")
    return toByte()
}
