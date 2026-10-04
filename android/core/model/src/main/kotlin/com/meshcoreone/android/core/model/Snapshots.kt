// AndroidOnly: WP-201 Defensive collection values for Swift Sendable/value-semantic snapshots.
package com.meshcoreone.android.core.model

import java.util.Collections

class SnapshotList<out T>(values: Iterable<T>) : AbstractList<T>(), java.util.RandomAccess {
    private val values = values.toList()
    override val size: Int get() = values.size
    override fun get(index: Int): T = values[index]

    companion object {
        fun <T> empty(): SnapshotList<T> = SnapshotList(emptyList())
        fun <T> of(vararg values: T): SnapshotList<T> = SnapshotList(values.asList())
    }
}

class SnapshotSet<out T>(values: Iterable<T>) : AbstractSet<T>() {
    private val values = Collections.unmodifiableSet(LinkedHashSet(values.toList()))
    override val size: Int get() = values.size
    override fun iterator(): Iterator<T> = values.iterator()

    companion object {
        fun <T> empty(): SnapshotSet<T> = SnapshotSet(emptyList())
    }
}

class SnapshotMap<K, out V>(values: Map<K, V>) : AbstractMap<K, V>() {
    private val contents = Collections.unmodifiableMap(LinkedHashMap(values))
    override val entries: Set<Map.Entry<K, V>> get() = contents.entries
}

fun <T> Iterable<T>.snapshot(): SnapshotList<T> = SnapshotList(this)
fun <T> Iterable<T>.snapshotSet(): SnapshotSet<T> = SnapshotSet(this)
fun <K, V> Map<K, V>.snapshotMap(): SnapshotMap<K, V> = SnapshotMap(this)
