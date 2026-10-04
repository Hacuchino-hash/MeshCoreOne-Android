// PortedFrom: MC1Services/Sources/MC1Services/Models/SavedTracePath.swift@db14559b39d32322b06477c6ae676112f583db50
// PortedFrom: MC1Services/Sources/MC1Services/Models/RxLogEntry.swift@db14559b39d32322b06477c6ae676112f583db50
// PortedFrom: MC1Services/Sources/MC1Services/Models/DebugLogEntry.swift@db14559b39d32322b06477c6ae676112f583db50
// PortedFrom: MC1Services/Sources/MC1Services/Models/LinkPreviewData.swift@db14559b39d32322b06477c6ae676112f583db50
// PortedFrom: MC1Services/Sources/MC1Services/Models/NodeStatusSnapshot.swift@db14559b39d32322b06477c6ae676112f583db50
// DTO projections preserve all columns, null/empty distinctions and binary values.
package com.meshcoreone.android.core.database

import com.meshcoreone.android.core.model.*
import com.meshcoreone.android.core.protocol.event.PayloadType
import com.meshcoreone.android.core.protocol.event.RouteType
import java.util.UUID

fun SavedTracePathDTO.toEntity(): SavedTracePathEntity =
    SavedTracePathEntity(radioId.value, id, name, pathBytes, hashSize, StoredInstant.from(createdDate))
fun SavedTracePathEntity.toDTO(runs: SnapshotList<TracePathRunDTO>): SavedTracePathDTO =
    SavedTracePathDTO(id, RadioId(radioId), name, pathBytes, hashSize, createdDate.toInstant(), runs)
fun TracePathRunDTO.toEntity(radioId: RadioId, savedPathID: UUID?): TracePathRunEntity =
    TracePathRunEntity(radioId.value, id, savedPathID, StoredInstant.from(date), success, roundTripMs, ValueConverters().doublesToBlob(hopsSNR))
fun TracePathRunEntity.toDTO(): TracePathRunDTO =
    TracePathRunDTO(id, date.toInstant(), success, roundTripMs, ValueConverters().blobToDoubles(hopsData))

fun RxLogEntryDTO.toEntity(): RxLogEntryEntity = RxLogEntryEntity(
    radioId.value, id, StoredInstant.from(receivedAt), snr, rssi, routeType.rawValue.toLong(), payloadType.rawValue.toLong(),
    payloadVersion.toLong(), transportCode, pathLength.toLong(), pathNodes, packetPayload, rawPayload, packetHash,
    channelIndex?.toLong(), channelName, decryptStatus.rawValue, fromContactName, toContactName, senderTimestamp?.toLong(),
    regionScope, regionScopeMatches, payloadTypeBits.toLong(),
)
fun RxLogEntryEntity.toDTO(): RxLogEntryDTO = RxLogEntryDTO(
    id, RadioId(radioId), receivedAt.toInstant(), snr, rssi,
    RouteType.fromRawValue(routeType.ubyte("rx.routeType")) ?: throw DatabaseValueException("rx.routeType", "Unknown raw value $routeType"),
    PayloadType.fromRawValue(payloadType.ubyte("rx.payloadType")) ?: PayloadType.UNKNOWN,
    payloadVersion.ubyte("rx.payloadVersion"), transportCode, pathLength.ubyte("rx.pathLength"), pathNodes, packetPayload, rawPayload,
    packetHash, channelIndex?.ubyte("rx.channelIndex"), channelName,
    DecryptStatus.fromRawValue(decryptStatus) ?: throw DatabaseValueException("rx.decryptStatus", "Unknown raw value $decryptStatus"),
    fromContactName, toContactName, senderTimestamp?.uint("rx.senderTimestamp"), regionScope, regionScopeMatches,
    (payloadTypeBits and 15).toUByte(), null,
)

fun DebugLogEntryDTO.toEntity(): DebugLogEntryEntity = DebugLogEntryEntity(id, StoredInstant.from(timestamp), level.rawValue, subsystem, category, message)
fun DebugLogEntryEntity.toDTO(): DebugLogEntryDTO = DebugLogEntryDTO.fromStored(
    id, timestamp.toInstant(), DebugLogLevel.fromRawValue(level) ?: throw DatabaseValueException("debug.level", "Unknown raw value $level"),
    subsystem, category, message,
)
fun LinkPreviewDataDTO.toEntity(): LinkPreviewEntity = LinkPreviewEntity(url, title, imageData, iconData, imageWidth, imageHeight, StoredInstant.from(fetchedAt))
fun LinkPreviewEntity.toDTO(): LinkPreviewDataDTO = LinkPreviewDataDTO(url, title, imageData, iconData, imageWidth, imageHeight, fetchedAt.toInstant())

fun NodeStatusSnapshotDTO.toEntity(): NodeStatusSnapshotEntity = NodeStatusSnapshotEntity(
    id, StoredInstant.from(timestamp), nodePublicKey, batteryMillivolts?.toLong(), lastSNR, lastRSSI?.toLong(), noiseFloor?.toLong(),
    uptimeSeconds?.toLong(), rxAirtimeSeconds?.toLong(), packetsSent?.toLong(), packetsReceived?.toLong(), receiveErrors?.toLong(),
    sentDirect?.toLong(), sentFlood?.toLong(), receivedDirect?.toLong(), receivedFlood?.toLong(), directDuplicates?.toLong(),
    floodDuplicates?.toLong(), postedCount?.toLong(), postPushCount?.toLong(), neighborSnapshots, telemetryEntries, latitude, longitude, altitude,
)
fun NodeStatusSnapshotEntity.toDTO(): NodeStatusSnapshotDTO = NodeStatusSnapshotDTO(
    id, timestamp.toInstant(), nodePublicKey, batteryMillivolts?.ushort("snapshot.batteryMillivolts"), lastSNR,
    lastRSSI?.short("snapshot.lastRSSI"), noiseFloor?.short("snapshot.noiseFloor"), uptimeSeconds?.uint("snapshot.uptimeSeconds"),
    rxAirtimeSeconds?.uint("snapshot.rxAirtimeSeconds"), packetsSent?.uint("snapshot.packetsSent"), packetsReceived?.uint("snapshot.packetsReceived"),
    receiveErrors?.uint("snapshot.receiveErrors"), sentDirect?.uint("snapshot.sentDirect"), sentFlood?.uint("snapshot.sentFlood"),
    receivedDirect?.uint("snapshot.receivedDirect"), receivedFlood?.uint("snapshot.receivedFlood"),
    directDuplicates?.uint("snapshot.directDuplicates"), floodDuplicates?.uint("snapshot.floodDuplicates"),
    postedCount?.ushort("snapshot.postedCount"), postPushCount?.ushort("snapshot.postPushCount"), neighborSnapshots, telemetryEntries, latitude, longitude, altitude,
)
