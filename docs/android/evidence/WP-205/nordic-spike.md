# WP-205 bounded facade compatibility spike

The candidate was inspected before selecting the production adapter. This is a
source/API compatibility spike, not Nordic binary execution, library admission
or physical-radio certification.

## Immutable candidate and platform inputs

| Input | Exact identity / SHA-256 |
| --- | --- |
| Nordic candidate | `no.nordicsemi.android:ble:2.11.0`, release 2025-09-11 |
| Candidate source revision | `ee4b01a311c370804bba3ac17957b4922b0499dd` |
| Maven sources JAR, 197871 bytes | `aab33d77e6771f1178d0ef2df7a0daf1717d21e47582fb01d9fdc4e02d839a10` |
| Maven POM, 2012 bytes | `e1ee63ac8bb8e80745d9582922fb50d5f1d3f1ffcbc00a0a9b4c4d2a53b3908f` |
| Revision's BSD-3-Clause LICENSE | `5dc1a11e97803d5de38ebf3f44f9ef5120e1854ae8830ace6bbeaa2c5bd876bd` |
| `BleManagerHandler.java` | `387ad3048ebf7d903e90f6386e6952f09be4c6b8a9eac3ec7e1b231e541fa4ca` |
| `WriteRequest.java` | `628a9821b691c0f942501ccb9e1d9220a5fc6a9258268351ee8d101c1dd072a8` |
| `BleManager.java` | `a01887275419f634841e568be16e7bb433a5483bcac984cdc2397aa78a9ade2f` |
| Official BluetoothGatt reference HTML | `7168ba4b74410dbf521c2b3f2e5a570e0d164db5efcb277d9ccea8d6e8fc268b` |
| Official BluetoothGattCallback reference HTML | `ef005fb4e3df33bbfebb5f8abc41382bb9fac0d8dd158d0a34614a838457db19` |
| Actual compilation platform | checksum-pinned `platforms;android-37.2`, revision 1 |

Origins: [release](https://github.com/NordicSemiconductor/Android-BLE-Library/releases/tag/2.11.0),
[sources](https://repo.maven.apache.org/maven2/no/nordicsemi/android/ble/2.11.0/ble-2.11.0-sources.jar),
[POM](https://repo.maven.apache.org/maven2/no/nordicsemi/android/ble/2.11.0/ble-2.11.0.pom),
[license](https://github.com/NordicSemiconductor/Android-BLE-Library/blob/ee4b01a311c370804bba3ac17957b4922b0499dd/LICENSE),
[GATT](https://developer.android.com/reference/android/bluetooth/BluetoothGatt),
[callbacks](https://developer.android.com/reference/android/bluetooth/BluetoothGattCallback).

The POM declares AndroidX annotation 1.9.1 and core 1.12.0. None of these
candidate artifacts was added, linked, installed as a dependency or copied into
the application. No new dependency/license admission is claimed.

## Compatibility observations and decision

| Concern | Observed candidate behavior | Native facade decision |
| --- | --- | --- |
| API31/32 versus 33+ writes | Handler lines 1225/1235 and 1272/1280 select immutable-value modern overloads versus mutable legacy fields | Use the same public API split, copying legacy fields immediately before the serialized operation |
| Callback values | Handler lines 2761-2767 supplies both notification signatures | Copy at callback entry; use only the API-appropriate signature, avoiding duplicate delivery |
| Actual MTU | Handler lines 2848-2854 updates on successful callback and caps its stored value at 515 | Retain actual callback MTU, cap attribute payload at 512, and keep firmware capabilities separate |
| Hidden cache refresh | Handler lines 1613-1623 invokes `getMethod("refresh")`; BleManager exposes optional refresh APIs | No reflection, hidden API, cache-refresh request or service-cache workaround |
| Completion correlation | Characteristic write callback has no originating request ID in Android's API | Register a per-operation identity before triggering; never reuse an ambiguous/timed-out/cancelled GATT handle |
| Frame splitting | Candidate exposes write-request splitting; ATT capacity is not firmware reassembly proof | Exactly one whole command per write; reject oversized or unverified larger frames before issuing |
| Runtime ownership | Candidate includes reconnect/bonding policy and lifecycle machinery | WP-205 owns one explicit connection; WP-206/207 decide process intent and retries |
| New dependency | Candidate would add another linked runtime and admission surface | The small dependency-free platform adapter provides the required control directly |

The native facade is selected as a documented platform adaptation, not a claim
that Nordic is incompatible or that its optional refresh API always executes.
Executable facade/adapter regressions supplement this source/API spike and
must be recorded with their actual raw reports before publication.

Independent review additionally checked the same revision's
`no/nordicsemi/android/ble/error/GattError.java`: `parseConnectionError` treats
connection-state8 as `GATT CONN TIMEOUT`, while `parse` treats ATT8 as
insufficient authorization. The native status-domain repair and actual pending
write/no-pending regressions preserve this independent distinction rather than
using an operation kind to reinterpret an HCI callback as ATT.

## Official API37 checks

`javap` against the provisioned, pinned API37.2 `android.jar` confirms:

- Modern characteristic write is `(characteristic, byte[], int) -> int`;
  modern descriptor write is `(descriptor, byte[]) -> int`.
- Modern notification callback adds `byte[]`; descriptor-read callback ordering
  is `(gatt, descriptor, int status, byte[] value)`, not the characteristic-read
  ordering. Descriptor reads are not needed for NUS notification enablement.
- API37 offers `BluetoothDevice.connectGatt(BluetoothGattConnectionSettings,
  Executor, BluetoothGattCallback)`. Its builder can explicitly disable
  auto-connect and automatic MTU negotiation.
- The official MTU reference states that API34+ requests 517 for the first GATT
  client's request and ignores subsequent requests. The requested number is
  never treated as successful negotiation; absent/nonzero callbacks fail.
- The official reference warns that write-without-response data can be
  truncated to the MTU. Pre-issue whole-frame guards are therefore mandatory.

Android has no portable CoreBluetooth-style peripheral-ready callback or
per-write sequence argument. JVM/shadow evidence cannot prove firmware delivery,
OEM callback reliability, a bond/PIN exchange or an actual API31/37 radio.
