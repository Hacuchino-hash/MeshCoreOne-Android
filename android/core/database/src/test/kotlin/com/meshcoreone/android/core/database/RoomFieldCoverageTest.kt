// AndroidOnly: WP-201 Real DAO round-trips cover every native persistent model/DTO field and converter failure surface.
package com.meshcoreone.android.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.meshcoreone.android.core.model.*
import com.meshcoreone.android.core.protocol.bytes.Bytes
import com.meshcoreone.android.core.protocol.event.PayloadType
import com.meshcoreone.android.core.protocol.event.RouteType
import java.util.UUID
import kotlin.test.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class RoomFieldCoverageTest {
    private lateinit var db: MeshCoreDatabase
    @Before fun open() { db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), MeshCoreDatabase::class.java).build() }
    @After fun close() { db.close() }

    @Test fun everyDeviceColumnIncludingRawSettingsOptionalRepeatFieldsAndCollectionsRoundTrips() = runTest {
        val d = device().copy(
            firmwareVersion = 255u, firmwareVersionString = "fork", manufacturerName = "manufacturer", buildDate = "date",
            maxContacts = 65535u, maxChannels = 255u, frequency = UInt.MAX_VALUE, bandwidth = UInt.MAX_VALUE,
            spreadingFactor = 255u, codingRate = 255u, txPower = Byte.MIN_VALUE, maxTxPower = Byte.MAX_VALUE,
            latitude = -90.0, longitude = 180.0, blePin = UInt.MAX_VALUE, clientRepeat = true, pathHashMode = 2u,
            defaultFloodScopeName = "scope", preRepeatFrequency = 0u, preRepeatBandwidth = UInt.MAX_VALUE,
            preRepeatSpreadingFactor = 255u, preRepeatCodingRate = 0u, manualAddContacts = true, autoAddConfig = 255u,
            autoAddMaxHops = 255u, multiAcks = 255u, telemetryModeBase = 255u, telemetryModeLoc = 255u,
            telemetryModeEnv = 255u, advertLocationPolicy = 255u, lastContactSync = UInt.MAX_VALUE, isActive = true,
            ocvPreset = "custom", appliedRadioPresetID = "applied", customOCVArrayString = "1,2,3",
            connectionMethods = SnapshotList.of(ConnectionMethod.Bluetooth(UUID.randomUUID()), ConnectionMethod.WiFi("radio.local", 65535u, "WiFi")),
            knownRegions = SnapshotList.of("US", "\u4F60"),
        )
        db.devices().insert(d.toEntity()); assertEquals(d, assertNotNull(db.devices().byId(d.id)).toDTO())
        assertEquals("AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE", ValueConverters().uuidToText(RADIO_A))
    }

    @Test fun everyContactChannelAndDiscoveredColumnPreservesRawTypesPathsCountsAndOptionalData() = runTest {
        val c = contact().copy(
            typeRawValue = 254u, flags = 255u, outPathLength = 0x82u, outPath = Bytes.of(128, 255, 0, 1, 2, 3),
            lastAdvertTimestamp = UInt.MAX_VALUE, latitude = -45.0, longitude = 179.0, lastModified = UInt.MAX_VALUE,
            lastHeardTimestamp = UInt.MAX_VALUE, nickname = "", isBlocked = true, isMuted = true, isFavorite = true,
            lastMessageDate = AT, unreadCount = Long.MAX_VALUE, unreadMentionCount = 3_000_000_000L,
            ocvPreset = "custom", customOCVArrayString = "custom", avatarImageData = Bytes.of(0, 128, 255),
        )
        db.contacts().insert(c.toEntity()); assertEquals(c, assertNotNull(db.contacts().byId(RADIO_A, c.id)).toDTO())
        val ch = channel(index = 255u).copy(secret = Bytes.of(128, 255), isEnabled = false, lastMessageDate = AT,
            unreadCount = Long.MAX_VALUE, unreadMentionCount = 3_000_000_000L, notificationLevel = NotificationLevel.MENTIONS_ONLY,
            isFavorite = true, floodScopeModeRawValue = "future", regionScope = "raw region")
        db.channels().insert(ch.toEntity()); assertEquals(ch, assertNotNull(db.channels().byId(RADIO_A, ch.id)).toDTO())
        val n = DiscoveredNodeDTO(UUID.randomUUID(), RadioId(RADIO_A), KEY, "node", 254u, AT, UInt.MAX_VALUE,
            999.0, -999.0, 255u, Bytes.of(128, 255), Long.MAX_VALUE, UInt.MAX_VALUE)
        db.discoveredNodes().insert(n.toEntity()); assertEquals(n, assertNotNull(db.discoveredNodes().byId(RADIO_A, n.id)).toDTO())
        assertTrue(n.hasLocation)
        val block = BlockedChannelSenderDTO(name = "Exact NAME", radioId = RadioId(RADIO_A), dateBlocked = AT)
        db.blockedSenders().insert(block.toEntity()); assertEquals(block, db.blockedSenders().forName(RADIO_A, block.name).single().toDTO())
    }

    @Test fun allMessageAndPendingSendColumnsPreserveNullsRawWidthMetadataAndExcludedTransientQueuePolicy() = runTest {
        val m = message(status = MessageStatus.RETRYING).copy(
            channelIndex = 255u, textType = TextType.SIGNED_PLAIN, ackCode = UInt.MAX_VALUE, pathLength = 0x82u,
            snr = -12.5, pathNodes = Bytes.of(128, 255), senderKeyPrefix = Bytes.of(128, 255), senderNodeName = "",
            isRead = true, replyToID = UUID.randomUUID(), roundTripTime = UInt.MAX_VALUE, heardRepeats = Long.MAX_VALUE,
            sendCount = 3_000_000_000L, retryAttempt = 3_000_000_000L, maxRetryAttempts = Long.MAX_VALUE,
            deduplicationKey = "dedup", linkPreviewURL = "https://example.invalid/", linkPreviewTitle = "title",
            linkPreviewImageData = Bytes.of(128), linkPreviewIconData = Bytes.of(255), linkPreviewFetched = true,
            containsSelfMention = true, mentionSeen = true, failureSeen = true, timestampCorrected = true,
            senderTimestamp = UInt.MAX_VALUE, reactionSummary = "summary", routeType = RouteType.TC_DIRECT,
            regionScope = "scope", regionScopeMatches = SnapshotList.of("scope", "other"),
        )
        val inserted = m.toEntity()
        assertNull(inserted.linkPreviewImageData); assertNull(inserted.linkPreviewIconData); assertFalse(inserted.linkPreviewFetched)
        db.messages().insert(inserted.copy(linkPreviewImageData = m.linkPreviewImageData, linkPreviewIconData = m.linkPreviewIconData, linkPreviewFetched = true))
        assertEquals(m, assertNotNull(db.messages().byId(RADIO_A, m.id)).toDTO())
        val queue = PendingSendDTO(UUID.randomUUID(), RadioId(RADIO_A), m.id, PendingSendKind.CHANNEL, null, 255u,
            true, "captured", UInt.MAX_VALUE, "", Long.MAX_VALUE, AT, null)
        db.pendingSends().insert(queue.toEntity()); assertEquals(queue, db.pendingSends().forMessage(RADIO_A, m.id).single().toDTO())
        assertEquals(Long.MAX_VALUE, db.pendingSends().maximumSequence(RADIO_A)); assertNull(db.pendingSends().maximumSequence(RADIO_B))
        db.pendingSends().upsert(queue.copy(attemptCount = 0).toEntity())
        assertEquals(0L, db.pendingSends().forMessage(RADIO_A, m.id).single().attemptCount)
        assertEquals(1, db.pendingSends().setAttemptCount(RADIO_A, queue.id, 3_000_000_000L))
        assertEquals(3_000_000_000L, db.pendingSends().forMessage(RADIO_A, m.id).single().attemptCount)
    }

    @Test fun repeatReactionSessionRoomMessageAndTraceRowsCoverEveryFieldAndRelationshipIdentifier() = runTest {
        val m = message(); db.messages().insert(m.toEntity())
        val repeat = MessageRepeatDTO(UUID.randomUUID(), m.id, AT, Bytes.of(128, 255), 0x41u, -3.5, Long.MIN_VALUE, UUID.randomUUID())
        db.repeats().insert(repeat.toEntity(RadioId(RADIO_A), m.id)); assertEquals(repeat, db.repeats().forMessage(RADIO_A, m.id).single().toDTO())
        val reaction = ReactionDTO(messageID = m.id, emoji = "\uD83D\uDE00", senderName = "sender", messageHash = "hash",
            rawText = "raw", receivedAt = AT, channelIndex = 255u, contactID = m.contactID, radioId = RadioId(RADIO_A))
        db.reactions().insert(reaction.toEntity()); assertEquals(reaction, db.reactions().forMessage(RADIO_A, m.id, 100).single().toDTO())
        val s = session().copy(latitude = -90.0, longitude = 180.0, isConnected = true, permissionLevel = RoomPermissionLevel.ADMIN,
            lastConnectedDate = AT, lastBatteryMillivolts = 65535u, lastUptimeSeconds = UInt.MAX_VALUE, lastNoiseFloor = Short.MIN_VALUE,
            unreadCount = Long.MAX_VALUE, notificationLevel = NotificationLevel.MUTED, isFavorite = true,
            lastRxAirtimeSeconds = UInt.MAX_VALUE, neighborCount = Long.MAX_VALUE, lastSyncTimestamp = UInt.MAX_VALUE, lastMessageDate = AT)
        db.sessions().insert(s.toEntity()); assertEquals(s, assertNotNull(db.sessions().byId(RADIO_A, s.id)).toDTO())
        db.sessions().markDisconnected(RADIO_A, s.id)
        assertEquals(RoomPermissionLevel.ADMIN, assertNotNull(db.sessions().byId(RADIO_A, s.id)).toDTO().permissionLevel)
        val r = roomMessage(s.id).copy(authorName = "", statusRawValue = 99, ackCode = UInt.MAX_VALUE,
            roundTripTime = UInt.MAX_VALUE, retryAttempt = Long.MAX_VALUE, maxRetryAttempts = Long.MAX_VALUE, failureSeen = true)
        db.roomMessages().insert(r.toEntity(RadioId(RADIO_A))); assertEquals(r, assertNotNull(db.roomMessages().byId(RADIO_A, r.id)).toDTO())
        val run = TracePathRunDTO(UUID.randomUUID(), AT, false, Long.MAX_VALUE, SnapshotList.of(-1.5, 0.0, 7.0))
        val path = SavedTracePathDTO(UUID.randomUUID(), RadioId(RADIO_A), "trace", Bytes.of(128, 255), 4, AT, SnapshotList.of(run))
        db.tracePaths().insert(path.toEntity()); db.traceRuns().insert(run.toEntity(RadioId(RADIO_A), path.id))
        assertEquals(path, assertNotNull(db.tracePaths().byId(RADIO_A, path.id)).toDTO(db.traceRuns().forPath(RADIO_A, path.id).map { it.toDTO() }.snapshot()))
    }

    @Test fun everyRxDebugLinkPreviewAndNodeSnapshotColumnRoundTripsWhileDecodedTextNeverPersists() = runTest {
        val rx = RxLogEntryDTO(UUID.randomUUID(), RadioId(RADIO_A), AT, -12.5, Long.MIN_VALUE, RouteType.TC_FLOOD,
            PayloadType.UNKNOWN, 255u, Bytes.of(0, 128, 255, 1), 0x82u, Bytes.of(128, 255), Bytes.of(128), Bytes.of(255),
            "hash", 255u, "", DecryptStatus.SUCCESS, "from", "to", UInt.MAX_VALUE, "scope", SnapshotList.of("a", "b"), 12u, "private plaintext")
        db.rxLogs().insert(rx.toEntity()); assertEquals(rx.copy(decodedText = null), assertNotNull(db.rxLogs().byId(RADIO_A, rx.id)).toDTO())
        val log = DebugLogEntryDTO.create(DebugLogLevel.FAULT, "subsystem", "category", "message", timestamp = AT)
        db.debugLogs().insert(log.toEntity()); assertEquals(log, db.debugLogs().since(AT.epochSecond, AT.nano, 10).single().toDTO())
        val link = LinkPreviewDataDTO("https://example.invalid/", "", Bytes.of(128), Bytes.of(255), Long.MAX_VALUE, 0, AT)
        db.linkPreviews().insert(link.toEntity()); assertEquals(link, assertNotNull(db.linkPreviews().byURL(link.url)).toDTO())
        val snap = NodeStatusSnapshotDTO(timestamp = AT, nodePublicKey = KEY, batteryMillivolts = 65535u, lastSNR = -12.5,
            lastRSSI = Short.MIN_VALUE, noiseFloor = Short.MAX_VALUE, uptimeSeconds = UInt.MAX_VALUE, rxAirtimeSeconds = UInt.MAX_VALUE,
            packetsSent = UInt.MAX_VALUE, packetsReceived = UInt.MAX_VALUE, receiveErrors = UInt.MAX_VALUE,
            sentDirect = UInt.MAX_VALUE, sentFlood = UInt.MAX_VALUE, receivedDirect = UInt.MAX_VALUE, receivedFlood = UInt.MAX_VALUE,
            directDuplicates = UInt.MAX_VALUE, floodDuplicates = UInt.MAX_VALUE, postedCount = 65535u, postPushCount = 65535u,
            neighborSnapshots = SnapshotList.of(NeighborSnapshotEntry(Bytes.of(128, 255), -1.5, Long.MAX_VALUE)),
            telemetryEntries = SnapshotList.of(TelemetrySnapshotEntry(Long.MAX_VALUE, "temperature", -12.5)), latitude = -90.0, longitude = 180.0, altitude = 0.0)
        db.nodeSnapshots().insert(snap.toEntity()); assertEquals(snap, assertNotNull(db.nodeSnapshots().byId(snap.id)).toDTO())
        val empty = snap.copy(id = UUID.randomUUID(), timestamp = AT.plusNanos(1), neighborSnapshots = SnapshotList.empty(), telemetryEntries = null)
        db.nodeSnapshots().insert(empty.toEntity()); val actual = assertNotNull(db.nodeSnapshots().latest(KEY)).toDTO()
        assertTrue(assertNotNull(actual.neighborSnapshots).isEmpty()); assertNull(actual.telemetryEntries)
    }

    @Test fun malformedStoredJsonByteUuidEnumsAndWidthsDoNotBecomeEmptySuccessfulRows() {
        val converters = ValueConverters()
        listOf("""[1]""", """["unterminated]""").forEach { assertFailsWith<DatabaseValueException> { converters.textToStrings(it) } }
        assertFailsWith<DatabaseValueException> { converters.textToMethods("""[{"wifi":{"host":"radio","port":"5000"}}]""") }
        assertFailsWith<DatabaseValueException> { converters.textToMethods("""[{"wifi":{"host":"radio","port":65536}}]""") }
        assertFailsWith<DatabaseValueException> { converters.textToMethods("""[{"unknown":{}}]""") }
        assertFailsWith<DatabaseValueException> { converters.textToUUID("0-0-0-0-0") }
        assertFailsWith<DatabaseValueException> { converters.blobToDoubles(Bytes.utf8("not an array")) }
        assertFailsWith<DatabaseValueException> { converters.blobToDoubles(Bytes.of(91, 128, 93)) }
        val bytes = byteArrayOf(128.toByte(), 255.toByte()); val immutable = converters.blobToBytes(bytes)
        bytes.fill(0); val accessor = converters.bytesToBlob(immutable); accessor.fill(0)
        assertEquals(Bytes.of(128, 255), immutable)
        val queue = PendingSendDTO.fromEnvelope(DirectMessageEnvelope(UUID.randomUUID(), UUID.randomUUID()), RadioId(RADIO_A), enqueuedAt = AT).toEntity()
        assertFailsWith<DatabaseValueException> { queue.copy(kindRawValue = 99).toDTO() }
    }
}
