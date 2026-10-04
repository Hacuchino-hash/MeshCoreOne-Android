// AndroidOnly: WP-103 Raw-frame construction and original-case labels, without an Android testing dependency.
package com.meshcoreone.android.core.protocol.parser

import com.meshcoreone.android.core.protocol.bytes.ByteWriter
import com.meshcoreone.android.core.protocol.bytes.Bytes
import com.meshcoreone.android.core.protocol.event.MeshEvent
import org.junit.jupiter.api.DynamicTest
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

internal fun original(suite: String, name: String, assertions: () -> Unit): DynamicTest =
    DynamicTest.dynamicTest("$suite::$name()", assertions)

internal fun nativeCase(name: String, assertions: () -> Unit): DynamicTest =
    DynamicTest.dynamicTest("WP-103::$name", assertions)

internal fun hex(value: String): Bytes = Bytes.fromHex(value.replace(" ", ""))
internal fun filled(value: Int = 0, size: Int): Bytes = Bytes(ByteArray(size) { value.toByte() })
internal fun le(value: UInt): Bytes = ByteWriter().appendUInt32LE(value).toBytes()
internal fun rawFrame(code: Int, payload: Bytes = Bytes.EMPTY): Bytes = Bytes.of(code) + payload
internal val epoch2024 = java.time.Instant.ofEpochSecond(1_704_067_200)
internal val nodePrefix = hex("aabbccddeeff")
internal val requestTag = hex("deadbeef")

internal inline fun <reified T : MeshEvent> parsed(data: Bytes): T = assertIs<T>(PacketParser.parse(data))

internal fun expectFailure(event: MeshEvent, data: Bytes? = null, reason: String? = null): MeshEvent.ParseFailure {
    val failure = assertIs<MeshEvent.ParseFailure>(event)
    if (data != null) assertEquals(data, failure.data)
    if (reason != null) assertTrue(failure.reason.contains(reason), failure.reason)
    return failure
}

internal fun contactBody(
    name: Bytes = Bytes.utf8("TestContact"),
    key: Bytes = filled(0xaa, 32),
    type: Int = 1,
    flags: Int = 2,
    pathLength: Int = 3,
    path: Bytes = hex("112233"),
    lastAdvert: UInt = 1_704_067_200u,
    latitude: Int = 37_774_900,
    longitude: Int = -122_419_400,
    lastModified: UInt = 1_704_067_200u,
): Bytes = ByteWriter().append(key).appendUInt8(type.toUByte()).appendUInt8(flags.toUByte())
    .appendUInt8(pathLength.toUByte()).append(path.paddedOrTruncated(64)).append(name.paddedOrTruncated(32))
    .appendUInt32LE(lastAdvert).appendInt32LE(latitude).appendInt32LE(longitude).appendUInt32LE(lastModified).toBytes()

internal fun selfBody(
    txPower: Byte = 20,
    key: Bytes = filled(0xbb, 32),
    latitude: Int = 37_774_900,
    longitude: Int = -122_419_400,
    multiAcks: UByte = 1u,
    policy: UByte = 2u,
    telemetry: UByte = 6u,
    manual: UByte = 1u,
    frequency: UInt = 906_875u,
    bandwidth: UInt = 250_000u,
    sf: UByte = 11u,
    cr: UByte = 8u,
    name: Bytes = Bytes.utf8("MyNode"),
): Bytes = ByteWriter().appendUInt8(1u).appendInt8(txPower).appendInt8(30)
    .append(key).appendInt32LE(latitude).appendInt32LE(longitude).appendUInt8(multiAcks)
    .appendUInt8(policy).appendUInt8(telemetry).appendUInt8(manual).appendUInt32LE(frequency)
    .appendUInt32LE(bandwidth).appendUInt8(sf).appendUInt8(cr).append(name).toBytes()

internal fun deviceBody(
    version: UByte = 10u,
    contactsHalf: UByte = 50u,
    channels: UByte = 8u,
    pin: UInt = 123_456u,
    build: String = "2025-01-01",
    model: String = "T-Deck",
    hardwareVersion: String = "1.12.0",
    extensions: Bytes = hex("0100"),
): Bytes = ByteWriter().appendUInt8(version).appendUInt8(contactsHalf).appendUInt8(channels)
    .appendUInt32LE(pin).append(Bytes.utf8(build).paddedOrTruncated(12))
    .append(Bytes.utf8(model).paddedOrTruncated(40)).append(Bytes.utf8(hardwareVersion).paddedOrTruncated(20))
    .append(extensions).toBytes()

internal fun loginBody(
    admin: UByte,
    acl: UByte,
    time: UInt = 0u,
    prefix: Bytes = nodePrefix,
): Bytes = ByteWriter().appendUInt8(admin).append(prefix).appendUInt32LE(time).appendUInt8(acl)
    .appendUInt8(2u).toBytes()

internal fun statusBody(
    battery: UShort = 1000u,
    queue: UShort = 5u,
    noise: Short = -110,
    rssi: Short = -85,
    counters: List<UInt> = listOf(100u, 200u, 10_000u, 600u, 10u, 20u, 30u, 40u),
    fullEvents: UShort = 3u,
    snr: Short = 40,
    directDups: UShort = 2u,
    floodDups: UShort = 1u,
    tail: Bytes = le(20_000u),
): Bytes {
    val writer = ByteWriter().appendUInt16LE(battery).appendUInt16LE(queue).appendInt16LE(noise).appendInt16LE(rssi)
    counters.forEach(writer::appendUInt32LE)
    return writer.appendUInt16LE(fullEvents).appendInt16LE(snr).appendUInt16LE(directDups)
        .appendUInt16LE(floodDups).append(tail).toBytes()
}

internal fun traceBody(
    hashes: Bytes,
    snrs: Bytes,
    finalSnr: Int = 12,
    flags: UByte = 0u,
    tag: UInt = 12_345u,
    auth: UInt = 67_890u,
): Bytes = ByteWriter().appendUInt8(0u).appendUInt8(hashes.size.toUByte()).appendUInt8(flags)
    .appendUInt32LE(tag).appendUInt32LE(auth).append(hashes).append(snrs).appendUInt8(finalSnr.toUByte()).toBytes()
