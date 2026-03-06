# Detour Android SDK — Deferred-Only Example

This example demonstrates `LinkProcessingMode.DEFERRED_ONLY` — the integration pattern for apps where a navigation framework (Jetpack Navigation, etc.) already handles Universal Links and custom scheme links, and Detour is only needed for deferred deep link resolution on first install.

## Scenario represented

- `MainActivity`: initializes Detour with `DEFERRED_ONLY` mode and calls `Detour.getDeferredLink()` directly on launch. No `DetourDelegate`, no intent processing.

## What this example covers

- `LinkProcessingMode.DEFERRED_ONLY` configuration.
- Manual `Detour.getDeferredLink()` call without `DetourDelegate`.
- All four `LinkResult` states: `Success`, `NotFirstLaunch`, `NoLink`, `Error`.

## Test flow

1) Start the app — it immediately calls `getDeferredLink()` on launch.
2) **Not first launch:** status shows "Not first launch — deferred link already consumed".
3) **No deferred link:** uninstall, reinstall, and launch with no prior Detour link — status shows "No deferred link matched".
4) **With a deferred link:** uninstall, copy a Detour link from the Dashboard, then install and launch — the deferred link should resolve and display on first open.

## Quick start

1. Configure this app in the [Detour Dashboard](https://godetour.dev). You'll need two values:
   - **Package name:** `swmansion.example.deferred` (from `AndroidManifest.xml`)
   - **SHA256 certificate fingerprint** — run this to get it from your debug keystore:
     ```shell
     keytool -list -v \
       -keystore ~/.android/debug.keystore \
       -alias androiddebugkey \
       -storepass android \
       -keypass android \
       | grep "SHA256"
     ```
     > The debug certificate is machine-specific — each developer must register their own fingerprint. For a release build, use your release keystore and its alias instead.

2. Add your credentials from the Dashboard — either via a secrets file:
   ```shell
   cp secrets.properties.example secrets.properties
   # then edit secrets.properties with your DETOUR_API_KEY and DETOUR_APP_ID
   ```
   or by hardcoding them directly in `MainActivity.kt`.

3. Build and run:
   ```shell
   ./gradlew :examples:example-deferred-only:installDebug
   ```

4. Test deferred links: uninstall, copy a Detour link from the Dashboard, install and launch — the deferred link resolves on first open.
