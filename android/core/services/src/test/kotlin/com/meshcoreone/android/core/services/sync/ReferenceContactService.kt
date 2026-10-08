// AndroidOnly: WP-214 Test-local reference of ContactService.syncContacts/pruneOrphans (WP-209) for WP-214-owned prune ids.
package com.meshcoreone.android.core.services.sync

import com.meshcoreone.android.core.contracts.domain.ContactServiceProtocol
import com.meshcoreone.android.core.contracts.domain.ContactSyncResult
import com.meshcoreone.android.core.contracts.domain.EntityKey
import com.meshcoreone.android.core.contracts.domain.PersistenceStoreProtocol
import com.meshcoreone.android.core.model.ContactFrame
import com.meshcoreone.android.core.model.ProtocolLimits
import com.meshcoreone.android.core.model.RadioId
import com.meshcoreone.android.core.model.VContactIdentity
import com.meshcoreone.android.core.model.snapshot
import com.meshcoreone.android.core.protocol.bytes.Bytes
import com.meshcoreone.android.core.protocol.model.MeshContact
import java.time.Instant
import java.util.Collections
import kotlin.coroutines.cancellation.CancellationException

/** Swift `MockMeshCoreSession`'s contact stream: stubbed contacts, reported total, recorded `since`. */
internal class FakeContactSession {
    @Volatile var stubbedContacts: List<MeshContact> = emptyList()
    @Volatile var stubbedReportedTotal: Long? = null
    @Volatile var stubbedReportsNoTotal = false
    val getContactsInvocations: MutableList<Instant?> = Collections.synchronizedList(ArrayList())

    /** Swift `getContactsReportingTotal(since:)`: returns every stub regardless of `since`. */
    fun getContactsReportingTotal(since: Instant?): Pair<List<MeshContact>, Long?> {
        getContactsInvocations += since
        val total = if (stubbedReportsNoTotal) null else stubbedReportedTotal ?: stubbedContacts.size.toLong()
        return stubbedContacts to total
    }
}

/**
 * The source `ContactService.syncContacts` contract the sync ids observe: batch save, max-lastmod
 * watermark, and the full-sync (`since == null`) prune that runs only on a complete snapshot with a valid
 * self key and never deletes the V-contact. WP-214 cannot depend on WP-209's Kotlin `ContactService`;
 * WP-303 must rebind these ids to it (pass its factory to the prune cases).
 */
internal class ReferenceContactService(
    private val session: FakeContactSession,
    private val dataStore: PersistenceStoreProtocol,
) : ContactServiceProtocol {
    override suspend fun syncContacts(radioId: RadioId, since: Instant?): ContactSyncResult {
        val (meshContacts, reportedTotal) = session.getContactsReportingTotal(since)
        val devicePublicKeys = meshContacts.map { it.publicKey }.toSet()
        val received = dataStore.batchSaveContacts(radioId, meshContacts.map { it.toFrame() }.snapshot())
        val lastTimestamp = meshContacts.maxOfOrNull { it.lastModified.uint32Seconds() } ?: 0u
        if (since == null) pruneOrphans(radioId, devicePublicKeys, reportedTotal)
        return ContactSyncResult(received, lastTimestamp, since != null)
    }

    private suspend fun pruneOrphans(radioId: RadioId, devicePublicKeys: Set<Bytes>, reportedTotal: Long?) {
        if (reportedTotal == null) return
        if (devicePublicKeys.size < reportedTotal) return
        val selfPublicKey = try {
            dataStore.fetchDevice(radioId)?.publicKey
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        if (selfPublicKey == null || selfPublicKey.size != ProtocolLimits.PUBLIC_KEY_SIZE) return
        dataStore.fetchContacts(radioId)
            .filter { it.publicKey !in devicePublicKeys && !VContactIdentity.isVContact(it.publicKey, selfPublicKey) }
            .forEach { dataStore.deleteContact(EntityKey(radioId, it.id)) }
    }
}

internal fun MeshContact.toFrame(): ContactFrame = ContactFrame(
    publicKey, type, flags.rawValue, outPathLength, outPath, advertisedName, lastAdvertisement.uint32Seconds(),
    latitude, longitude, lastModified.uint32Seconds(), typeRawValue,
)
