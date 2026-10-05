// PortedFrom: MC1/Services/DecodedPreviewCache.swift@db14559b39d32322b06477c6ae676112f583db50
//             MC1/Services/InlineImageCache.swift (decoded-entry eviction policy)@db14559b39d32322b06477c6ae676112f583db50
//             MC1/Services/LinkPreviewCache.swift (NSCache countLimit/totalCostLimit shape)@db14559b39d32322b06477c6ae676112f583db50
// Generic extraction of the FIFO, count-and-cost-bounded eviction policy shared by every image
// cache in the original source. The originals differ only in their payload type (UIImage pair,
// Data, LinkPreviewDataDTO) and in concurrency primitive (OSAllocatedUnfairLock, actor + NSCache);
// this module owns the pure, synchronous eviction algorithm only. Thread-safety and the concrete
// payload (android.graphics.Bitmap, network bytes, etc.) are narrow native-adapter
// responsibilities layered on top (see docs/android/deviations/WP-218.md).
package com.meshcoreone.android.core.services.content

/**
 * A FIFO cache bounded by both entry count and total cost, mirroring the eviction policy shared
 * by `DecodedPreviewCache`, `InlineImageCache`'s decoded mirror, and `LinkPreviewCache`'s
 * `NSCache` (`countLimit` + `totalCostLimit`).
 *
 * Eviction is insertion-order FIFO (not true LRU - the originals never promote on read either):
 * entries are evicted oldest-first while there is more than one entry and either bound is
 * exceeded. The just-inserted entry is always kept even if it alone exceeds [maxTotalCostBytes],
 * matching the Swift comment "so a large hero is still served once."
 *
 * Not thread-safe by itself - the original types each wrap an equivalent single-threaded
 * structure in their own lock/actor. Callers needing concurrent access should guard calls with
 * their own synchronization primitive, exactly as the Swift originals do around their raw state.
 */
class FifoCostBoundedCache<K, V>(
    private val maxEntryCount: Int,
    private val maxTotalCostBytes: Long,
    private val costOf: (V) -> Long,
) {
    private val entries = LinkedHashMap<K, V>()
    private val insertionOrder = ArrayDeque<K>()
    private var totalCostBytes = 0L

    /** Current total cost of all retained entries. */
    val totalCost: Long
        get() = totalCostBytes

    /** Current entry count. */
    val size: Int
        get() = entries.size

    /** Returns the cached value for [key], or `null` if absent. */
    fun get(key: K): V? = entries[key]

    /**
     * Stores [value] under [key], replacing any existing entry for that key (and moving it to the
     * back of the insertion order, matching the Swift `store(_:for:)` replace-and-reorder
     * behavior), then runs the FIFO eviction sweep.
     */
    fun put(key: K, value: V) {
        val existing = entries[key]
        if (existing != null) {
            totalCostBytes -= costOf(existing)
            insertionOrder.remove(key)
        }
        entries[key] = value
        insertionOrder.addLast(key)
        totalCostBytes += costOf(value)
        evictIfNeeded()
    }

    /** Empties the cache, e.g. in response to a memory-pressure signal from a native adapter. */
    fun clear() {
        entries.clear()
        insertionOrder.clear()
        totalCostBytes = 0L
    }

    private fun evictIfNeeded() {
        while (insertionOrder.size > 1 &&
            (insertionOrder.size > maxEntryCount || totalCostBytes > maxTotalCostBytes)
        ) {
            val oldestKey = insertionOrder.removeFirst()
            val evicted = entries.remove(oldestKey)
            if (evicted != null) {
                totalCostBytes -= costOf(evicted)
            }
        }
    }
}
