# WP-218 HTTP client dependency proposal (OkHttp 5.5.0)

Status as of this file: **NOT ADMITTED**. No `build.gradle.kts`, lockfile, version
catalog, or `AndroidManifest.xml` has been edited for this proposal. This document
exists so the coordinator can review one complete, self-contained proposal instead
of assembling it from scattered evidence entries; it supersedes nothing already
recorded in `run-summary.json`'s `http_client_dependency_proposal_correction_2026_10_05`
entry, which remains the dated narrative of how this proposal was arrived at.

## Why a backend is needed

`core:services`' `BoundedHttpFetching` role (consumed by `LinkPreviewScraper`/
`LinkPreviewService`) is currently exercised only against fakes in pure-JVM tests.
Per the admitted native-adapter amendment, the real Android HTTP implementation
belongs in `android/app/src/main/kotlin/.../app/content/` (alongside the other three
admitted native adapters), not in `core:services` (which must stay pure-JVM with
production edges limited to `core:protocol`/`core:model`/`core:contracts`).
`java.net.http.HttpClient` (JDK 17) is not an Android API, so a real client
dependency is required.

## Exact selected coordinate and resolved variant

- **Declared coordinate:** `com.squareup.okhttp3:okhttp:5.5.0`
- **Declaration site (proposed, not yet written):** `android/app/build.gradle.kts`
  only -- the one module where the native adapter lives. Not `core:services`
  (forbidden production edge) and not any other module.
- **Actual resolved artifact on an Android target:** OkHttp 5.5.0 publishes Gradle
  Module Metadata (GMM) with Android-specific variants
  (`androidApiElements-published`/`androidRuntimeElements-published`) that are
  declared `"available-at"` a *separate* module coordinate,
  `com.squareup.okhttp3:okhttp-android:5.5.0` -- i.e. Gradle's variant-aware
  selection transparently substitutes the AAR for the plain JVM jar on
  `org.gradle.jvm.environment = "android"` consumers (which `android/app` is).
  Declaring the plain `okhttp` coordinate in `app/build.gradle.kts` therefore
  silently becomes this AAR, not `okhttp-jvm`.
- **Verification method:** fetched `okhttp-5.5.0.module` and
  `okhttp-android-5.5.0.module` directly from Maven Central
  (`https://repo.maven.apache.org/maven2/com/squareup/okhttp3/...`) and read the
  variant/`available-at`/dependency declarations; separately downloaded
  `okhttp-android-5.5.0.aar` itself (not just trusted the `.module` metadata) and
  confirmed its SHA-256 byte-exact, then extracted it (AARs are zip files) and
  read its embedded `AndroidManifest.xml` as plain text.

## Full resolved Android transitive graph (verified)

| Coordinate | Version | Source |
|---|---|---|
| `com.squareup.okhttp3:okhttp-android` | `5.5.0` | resolved AAR (see below) |
| `com.squareup.okio:okio` (+ `okio-jvm`) | `3.18.1` | `okhttp-android-5.5.0.module` dependency |
| `org.jetbrains.kotlin:kotlin-stdlib` | `2.1.21` (2.2.21 range marker also present) | `okhttp-android-5.5.0.module` dependency |
| `androidx.annotation:annotation` | `1.10.0` | `okhttp-android-5.5.0.module` dependency |
| `androidx.startup:startup-runtime` | `1.2.0` | `okhttp-android-5.5.0.module` dependency |

**AAR artifact identity (verified by direct download, not just metadata):**
`okhttp-android-5.5.0.aar`, **947165 bytes**, **sha256
`6c7fd12f092e64ca2eae0b8a8023900c0d7ffec57888cf8abc8085c6f42e1dcc`**.

**License:** Apache License 2.0 (confirmed against the published `LICENSE.txt` in
the OkHttp 5.5.0 release artifacts). Compatible with this project's GPLv3
application license and the MeshCore MIT-licensed protocol module (OkHttp would be
an `app`-module dependency, not linked into the MIT-licensed `core:protocol`).

**Sources used for this table:**
`https://repo.maven.apache.org/maven2/com/squareup/okhttp3/okhttp/5.5.0/okhttp-5.5.0.module`,
`https://repo.maven.apache.org/maven2/com/squareup/okhttp3/okhttp-android/5.5.0/okhttp-android-5.5.0.module`,
`https://repo.maven.apache.org/maven2/com/squareup/okhttp3/okhttp-android/5.5.0/okhttp-android-5.5.0.aar`,
and the upstream `https://raw.githubusercontent.com/square/okhttp/master/CHANGELOG.md`
(5.5.0, 2026-08-16 entry) for the human-readable release description.

## Manifest-merge delta (the one open concern)

Direct inspection of the downloaded AAR's embedded `AndroidManifest.xml` (not just
the `.module` dependency list, which does not show this) found it declares:

```xml
<uses-permission android:name="android.permission.INTERNET"/>
<provider
    android:name="androidx.startup.InitializationProvider"
    android:authorities="${applicationId}.androidx-startup"
    android:exported="false"
    tools:node="merge">
    <meta-data
        android:name="okhttp3.internal.platform.PlatformInitializer"
        android:value="androidx.startup"/>
</provider>
```

- The `INTERNET` permission is expected and required for any HTTP client; link
  preview fetching already implies network access, so this is not a new privacy
  surface beyond what the feature itself requires.
- The `androidx.startup.InitializationProvider` + `PlatformInitializer` entry is a
  genuine **automatic Android startup component**: it runs once at process start
  (before `Application.onCreate()`) to pick the correct Android TLS/trust-manager
  strategy. It performs **no network I/O and no tracking/analytics** -- it is
  purely a platform-capability probe -- but it is still an automatically-merged
  manifest component, which this WP's standing constraints require explicit
  review/admission for before adoption.
- `AndroidManifest.xml` is outside this session's current write surface (the
  admitted native-adapter amendment covers only
  `android/app/src/{main,test}/kotlin/.../app/content/**` and
  `android/app/build.gradle.kts`'s existing-vetted-dependency scope). Adopting
  OkHttp therefore requires either: (a) an explicit amendment admitting this one
  manifest-merge delta as acceptable incidental-to-the-client behavior, with no
  further action needed (the merge is automatic via the AAR, not a line this
  session would hand-write), or (b) a scoped `tools:node="remove"` override if the
  coordinator wants the auto-init suppressed (which *would* require a manifest
  write-lease, since an override entry must be added to `app`'s own
  `AndroidManifest.xml`).

## Cross-module Okio upgrade impact (narrowed, not "all modules")

The app-incumbent `com.squareup.okio:okio:3.9.1` (transitive via
`androidx.datastore:datastore-core-okio:1.2.1`) is currently locked in **10**
module lockfiles: `app`, `core-data`, `core-maps`, `core-ui`, `feature-chats`,
`feature-map`, `feature-nodes`, `feature-onboarding`, `feature-remotenodes`,
`feature-settings`, `platform-widgets`.

Checked which of those modules actually declare a project dependency on
`core:services` (the only path by which this adapter's new Okio requirement could
propagate into their own dependency resolution): **only `android/app`** does
(`grep 'project(":core:services")' android/*/build.gradle.kts` matches exactly
`android/app/build.gradle.kts`). Gradle resolves one dependency version per
*project*, so adding `okhttp-android:5.5.0` (and its `okio:3.18.1`) to
`app/build.gradle.kts` would force **only `app.lockfile`**'s Okio entries
(`com.squareup.okio:okio`/`okio-jvm`) from `3.9.1` to `3.18.1`. The other 9
lockfiles (`core-data`, `core-maps`, `core-ui`, `feature-*`, `platform-widgets`),
which do not depend on `core:services`, are **not** forced to change -- their
locked `3.9.1` stays exactly as-is. This is a one-module lockfile delta, not a
root-wide Okio bump.

## What is still required before admission

1. Coordinator sign-off on the `androidx.startup`/`PlatformInitializer` manifest-
   merge delta (accept as incidental, or request a scoped removal override).
2. Coordinator sign-off on the `app.lockfile`-only Okio `3.9.1` -> `3.18.1` delta.
3. An explicit write-lease amendment covering `android/app/build.gradle.kts`'s new
   dependency declaration and the corresponding `android/app.lockfile`
   (mechanically regenerated, not hand-written) entries for
   `okhttp-android`/`okio`/`kotlin-stdlib` version bump/`androidx.annotation`/
   `androidx.startup`, consistent with how the `core-services.lockfile` delta is
   being handled through the sanctioned generator + byte-exact admission pipeline.

No further action is taken on this proposal pending that sign-off. Pure-algorithm
`core:services` content work (scraper, redirect/DNS/TLS policy, caches) continues
against fakes in the interim and is not blocked by this open item.
