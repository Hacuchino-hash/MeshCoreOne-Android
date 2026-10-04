// AndroidOnly: WP-106 Immutable collection snapshots preserve Swift event value semantics.
package com.meshcoreone.android.core.protocol.event

import java.util.Collections

class EventList<out T>(elements: Collection<T>) : AbstractList<T>() {
    private val elements = elements.toList()
    override val size: Int get() = elements.size
    override fun get(index: Int): T = elements[index]
}

class EventMap<K, out V>(values: Map<K, V>) : AbstractMap<K, V>() {
    private val snapshot: Map<K, V> = Collections.unmodifiableMap(LinkedHashMap(values))
    override val entries: Set<Map.Entry<K, V>> get() = snapshot.entries
}
