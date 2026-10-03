# ADR 002: dependency, model and asset admission

**Status:** Proposed; WP-001 supervised dependent draft, pending human review.
No SDK/model/provider is newly approved for linking by this draft.

## License policy

The pinned application [LICENSE](https://github.com/Avi0n/MeshCoreOne/blob/db14559b39d32322b06477c6ae676112f583db50/LICENSE)
is **GPLv3**; [MeshCore/LICENSE](https://github.com/Avi0n/MeshCoreOne/blob/db14559b39d32322b06477c6ae676112f583db50/MeshCore/LICENSE)
is **MIT**. Those notices and the reference acknowledgement inputs remain
read-only and must survive the port/distribution. Preserve copyright and MIT
permission text with the pure JVM protocol. WP-506 provides corresponding
application source, provenance/SBOM, notices and user instructions with release APKs.
AboutLibraries or an SPDX label alone is not a legal compatibility determination.

For every linked dependency/transitive artifact, model, dataset or bundled asset,
record exact version/revision/hash, origin, applicable license/terms, notices,
modifications, redistribution/source obligations and the human decision where
required. A build candidate is distinct from an approved dependency and from an
actually linked artifact. Unknown/changed terms block admission; never relabel
proprietary terms as MIT or assume optionality eliminates GPL obligations.

| Candidate / input | Admission condition / owner |
| --- | --- |
| AGP/Kotlin/Compose/Room/KSP | Pin after build proof and dependency/transitive license inventory; WP-002/003 |
| Nordic BLE facade candidate | Verify exact artifact's BSD-3-Clause notices/terms and platform spike; WP-205, not approval by name |
| Crypto/network/media/chart libraries | Exact linked artifact/transitive/native obligations and wire/platform proof; owning WPs, WP-503 audit |
| MapLibre Native | Engine license is separate from styles/fonts/sprites/tiles/provider terms; WP-312, actual 16 KB proof later |
| Translation engine/model/provider, including ML Kit | Human license/privacy/runtime gate WP-406; no linked adapter/model before permission |
| Material/custom replacement icons | Permission and notices for exact artwork; WP-301; do not extract/repackage restricted SF Symbol artwork |
| Bundled filter/terrain/localization/other data | Provenance, update/distribution terms and attribution for actual input; owning WP and WP-503 |

Map-provider review must cover base/satellite/topo layers, attribution visibility,
tile caching, offline region download/retention/deletion, quotas, keys and snapshot
redistribution. Engine permission does not grant tile/offline rights. A provider
may be network-only or unavailable; expose that capability instead of scraping a
different provider or presenting a blank map as success.

## Optional-engine SPI, required feature

Translation is **required for the feature-complete release**. Its implementation
is optional/selectable behind the [TranslationPort](../architecture/contracts.md),
not translation itself. Google services are allowed only for optional capabilities;
BLE/WiFi messaging, local data and navigation must work without them or internet.
Being on-device does not establish SDK/model GPL compatibility, privacy or GMS-less
support. ML Kit remains a candidate, neither automatically linked nor categorically
waived/rejected without examining its actual terms.

WP-406 must decide engine/provider and model rights, language coverage/detection,
device/runtime prerequisites, retention/deletion, payload transmission, model
download consent and offline behavior after download where supported. Do not add
accounts/backends, analytics or undeclared network fallback to obtain translation.
If no candidate passes, try another permitted implementation or return the
unresolved decision to the human gate; do not silently remove required scope.

Model availability has distinct `Unavailable`, `UnsupportedLanguage`,
`DownloadRequired`, `Downloading(progress)`, `Ready` and `Failed(typedIssue)` states.
Download/delete/translate cancellation propagates and clears in-progress UI.
No-engine scaffold behavior is explicitly unavailable and blocks feature-complete
acceptance; returning original text is not translation success. Follow the source
[MessageTranslating](https://github.com/Avi0n/MeshCoreOne/blob/db14559b39d32322b06477c6ae676112f583db50/MC1/Services/MessageTranslating.swift)
and [installed-first launcher](https://github.com/Avi0n/MeshCoreOne/blob/db14559b39d32322b06477c6ae676112f583db50/MC1/Services/TranslationSessionLauncher.swift)
for the installed/download distinction, with a native consent/error adaptation.

## Product exclusions and stable system integration

All ten themes are unlocked; no billing, entitlement, account, analytics, updater
or automatic APK installation is introduced. Distribution remains GitHub Releases
sideload APK only at WP-506. Ambient radio/chat status does not automatically
qualify for promoted Live Updates; use the ordinary ongoing notification,
widget/tile unless a specific user-initiated time-sensitive activity proves eligibility.
Stable shortcuts/share targets supply release operations; alpha AppFunctions is
not a release prerequisite. Hardware, signing and legal/provider acceptance
remain actual future human/protected gates.
