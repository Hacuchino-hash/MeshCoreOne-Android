// PortedFrom: MeshCore/Sources/MeshCore/Session/ContactManager.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.core.protocol.session

import com.meshcoreone.android.core.protocol.bytes.Bytes
import com.meshcoreone.android.core.protocol.event.EventList
import com.meshcoreone.android.core.protocol.event.MeshEvent
import com.meshcoreone.android.core.protocol.model.MeshContact
import java.text.Collator
import java.time.Instant
import java.util.Locale

internal class ContactManager {
    private val lock = Any()
    private val contacts = linkedMapOf<String, MeshContact>()
    private val pending = linkedMapOf<String, MeshContact>()
    private var lastModified: Instant? = null
    private var dirty = true
    private var autoUpdate = false

    val cachedContacts: List<MeshContact> get() = synchronized(lock) { EventList(contacts.values) }
    val cachedPendingContacts: List<MeshContact> get() = synchronized(lock) { EventList(pending.values) }
    val needsRefresh: Boolean get() = synchronized(lock) { dirty }
    val contactsLastModified: Instant? get() = synchronized(lock) { lastModified }
    val isEmpty: Boolean get() = synchronized(lock) { contacts.isEmpty() }
    val isAutoUpdateEnabled: Boolean get() = synchronized(lock) { autoUpdate }

    fun getByName(name: String, exactMatch: Boolean = false): MeshContact? = synchronized(lock) {
        if (exactMatch) contacts.values.firstOrNull {
            it.advertisedName.lowercase(Locale.ROOT) == name.lowercase(Locale.ROOT)
        } else {
            val collator = Collator.getInstance().apply {
                strength = Collator.PRIMARY
                decomposition = Collator.CANONICAL_DECOMPOSITION
            }
            contacts.values.firstOrNull { contact ->
                val text = contact.advertisedName
                val boundaries = mutableListOf(0)
                var offset = 0
                while (offset < text.length) {
                    offset += Character.charCount(text.codePointAt(offset))
                    boundaries += offset
                }
                boundaries.indices.any { start ->
                    (start until boundaries.size).any { end ->
                        collator.compare(text.substring(boundaries[start], boundaries[end]), name) == 0
                    }
                }
            }
        }
    }

    fun getByKeyPrefix(prefix: String): MeshContact? = synchronized(lock) {
        contacts.values.firstOrNull { it.publicKey.hexString.startsWith(prefix.lowercase(Locale.ROOT)) }
    }
    fun getByKeyPrefix(prefix: Bytes): MeshContact? = synchronized(lock) {
        contacts.values.firstOrNull { it.publicKey.prefix(prefix.size) == prefix }
    }
    fun getByPublicKey(key: Bytes): MeshContact? = synchronized(lock) { contacts[key.hexString] }
    fun store(contact: MeshContact) = synchronized(lock) { contacts[contact.id] = contact; Unit }
    fun updateCache(newContacts: List<MeshContact>, lastModified: Instant) = synchronized(lock) {
        newContacts.forEach { contacts[it.id] = it }
        this.lastModified = lastModified
        dirty = false
    }
    fun markClean(lastModified: Instant) = synchronized(lock) { this.lastModified = lastModified; dirty = false }
    fun markDirty() = synchronized(lock) { dirty = true }
    fun addPending(contact: MeshContact) = synchronized(lock) { pending[contact.id] = contact; Unit }
    fun popPending(publicKey: String): MeshContact? = synchronized(lock) { pending.remove(publicKey) }
    fun flushPending() = synchronized(lock) { pending.clear() }
    fun remove(contactId: String) = synchronized(lock) {
        contacts.remove(contactId)
        pending.remove(contactId)
        dirty = true
    }
    fun clear() = synchronized(lock) { contacts.clear(); pending.clear(); lastModified = null; dirty = true }
    fun setAutoUpdate(enabled: Boolean) = synchronized(lock) { autoUpdate = enabled }

    fun trackChanges(event: MeshEvent) = synchronized(lock) {
        when (event) {
            is MeshEvent.Contact -> contacts[event.contact.id] = event.contact
            is MeshEvent.NewContact -> { pending[event.contact.id] = event.contact; dirty = true }
            is MeshEvent.ContactsEnd -> { lastModified = event.lastModified; dirty = false }
            is MeshEvent.Advertisement, is MeshEvent.PathUpdate, MeshEvent.ContactsFull -> dirty = true
            is MeshEvent.ContactDeleted -> {
                contacts.remove(event.publicKey.hexString)
                pending.remove(event.publicKey.hexString)
                dirty = true
            }
            else -> Unit
        }
    }
}
