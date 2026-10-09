# WP-404 evidence

External contribution; no lease. No device verification claimed.

## Scope
Dynamic App Shortcuts (status, zero-hop/flood advert, per-recipient send), exported text share target,
typed request parsing, cold-start dispatcher and confirmation-gated flows in `android/platform/shortcuts/`,
behind platform-owned ports (no `core:services` dependency). See `docs/android/deviations/WP-404.md`.

## Source-test parity
Swift sources `MC1/Intents/*` and tests `MC1Tests/Intents/*` at db14559b are the spec. Test cases
are named by `@SourceCases` annotations in the JVM tests:

- Adapted: EntityIdentityTests (id round-trip, malformed ids, contact/channel identity, radio scoping,
  duplicate/zero digest resolution, picker filtering), SendMessageIntentTests (routing matrix,
  validation, confirmed queueing, state change during confirmation, node-name revalidation, error
  mapping), SendAdvertIntentTests and StatusQueryIntentTests (reach ids, honest dialogs, errors,
  cached status).
- Not applicable on Android (framework-specific): `supportedModes`, `IntentBridge` adopt/re-adopt,
  `OpenRadioStatusIntentTests`, GPS-gate tests (owned by the services layer), localization-resource
  tests for App Intents metadata (the l10n WP owns the resource conversion).
- Android-only boundary tests: invalid/ambiguous/oversized input, stale route, denied confirmation,
  cold-start hold/expiry, shortcut plan caps.

## Verification
```text
android/gradlew -p android :platform:shortcuts:testDebugUnitTest :platform:shortcuts:compileDebugKotlin \
  :app:assembleDebug validateModuleGraph --dependency-verification lenient
python3 tools/android-port/portmap.py
python3 tools/android-port/controller/validate.py
```
Results are recorded in the pull request description.
