# WP-205 human radio scenarios

**Not executed.** No authorization for real-radio scanning, connecting, bonding,
PIN/admin operations or physical-device changes was used. SDK/Robolectric,
compiled APK and static alignment results do not satisfy this checklist.
Device identifiers, PINs, keys, mesh content and addresses must be sanitized.

| Scenario | Required actual observation |
| --- | --- |
| Physical API31/32 and API37, representative OEMs | Native API branch, actual callback ordering and thread/close behavior; foreground host is the same process owner |
| nRF52 and ESP32 variants | Real NUS service/TX/RX/CCCD, write properties and independently established firmware command capacity |
| PIN/bond required, wrong PIN, cancelled pairing, peer deletes bond | Actual system pairing behavior and typed recovery; never an automatic hidden re-pair or inferred address identity |
| MTU23, 185/247 and negotiated larger MTU | Reported callback size versus actual complete command/notification capacity; exact boundary payloads, no firmware truncation or unsupported reassembly |
| Verified Write Command/pipelined firmware reads | Local acceptance versus real firmware responses/delivery, flow control and queue saturation; properties alone are not evidence |
| Adapter off/on and permission revocation during each operation | Pending operation unblocks, old stream terminates, GATT closes once or explicitly reports cleanup failure |
| Status133 and other nonzero errors | Actual original status/cause remains visible; no guessed “other app” or broken bond diagnosis |
| Cancel/timeout and rapid reconnect, delayed packets/writes | New GATT/generation cannot ingest or complete obsolete work; OEM duplicate same-attribute callback reliability remains observed rather than assumed |
| Radio service/MTU changes after connection | Explicit safe failure/recovery, no stale frame capability or hidden cache refresh |
| Receiver cancelled/restarted and prolonged RX burst | One process ingestion drain, exact packet delivery/order and observed memory use, not a screen-owned radio |
| Background/OEM restrictions, force-stop and locked storage | Later WP-206/207 execution/persistence policy; no promise of perpetual reconnect from WorkManager or this component |

A future hardware record must bind actual app/firmware/device API/OEM, base/head,
source/policy revisions, authorized operator, scenario outcome and sanitized
raw evidence. A closed issue, simulator result or idle worker is not that record.
