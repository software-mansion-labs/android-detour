# Detour Android SDK Example

This example demonstrates a full integration of `com.detour:detour-sdk` in a native Android app.

## Scenario represented

- `MainActivity`: uses `DetourDelegate` with `LinkProcessingMode.ALL` to handle Universal Links, custom scheme links, and deferred deep links automatically. Shows custom encrypted storage via `EncryptedStorageProvider`.
- `ProductActivity`: navigated to from a `/products/{id}` route. Logs a `ViewItem` analytics event.
- `PromoActivity`: navigated to from a `/promo` route. Logs a `Purchase` event and a `subscription_renewed` retention event.
- `DeferredOnlyExampleActivity`: demonstrates manual `Detour.getDeferredLink()` usage without `DetourDelegate` — the pattern used with `LinkProcessingMode.DEFERRED_ONLY` when your navigation framework already handles intents.

## What this example covers

- SDK initialization and `DetourDelegate` lifecycle wiring (`onCreate` / `onNewIntent`).
- All three `LinkProcessingMode` options (inline comments in `MainActivity` config).
- Custom storage implementation (`EncryptedSharedPreferences`).
- `LinkResult` handling: `Success` (with `route`, `pathname`, `params`, `type`), `NotFirstLaunch`, `NoLink`, `Error`.
- Route-based navigation from link results.
- Analytics: `DetourAnalytics.logEvent()` and `DetourAnalytics.logRetention()`.
- Manual deferred link retrieval without `DetourDelegate`.

## Test flow

1) Start the app on a device or emulator.
2) You land on `MainActivity` — status shows "No deep link detected".
3) Trigger a Detour Universal Link (e.g. `https://android-sdk.godetour.link/nkeFLNfFBf/products/42`).
   - The app should display the link metadata and navigate to `ProductActivity`.
4) Return to `MainActivity` and tap **Deferred-Only Example**.
   - Shows the result of `Detour.getDeferredLink()` — will report "Not first launch" if already consumed, or "No deferred link matched" on a non-fresh install.
5) For deferred link testing: uninstall the app, copy a Detour link from the Dashboard, then install and launch — the deferred link should resolve on first open.

## Quick start

1. Configure this app in the [Detour Dashboard](https://godetour.dev). You'll need two values:
   - **Package name:** `com.detour.example` (from `AndroidManifest.xml`)
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
   cp app-example/secrets.properties.example app-example/secrets.properties
   # then edit secrets.properties with your DETOUR_API_KEY and DETOUR_APP_ID
   ```

   or by hardcoding them directly in `MainActivity.kt`.
3. Update the `intent-filter` in `AndroidManifest.xml` with your app's verified link domain and path prefix from the Dashboard.
4. Build and run:

   ```shell
   ./gradlew :app-example:installDebug
   ```

5. Trigger test links: **deferred** — copy the link from Detour Dashboard before a fresh install, then install and launch. **Universal Link** — open the link while the app is running or from cold start.
