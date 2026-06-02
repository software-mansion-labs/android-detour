<img src="https://github.com/user-attachments/assets/c965b51b-7307-477a-8d22-9c9cd6da6231" alt="Detour Android SDK by Software Mansion" width="100%"/>

[![Ad](https://revive-adserver.swmansion.com/www/images/zone-gh-android-detour-1?n=1)](https://revive-adserver.swmansion.com/www/delivery/ck.php?zoneid=zone-gh-android-detour-1&n=1)
[![Ad](https://revive-adserver.swmansion.com/www/images/zone-gh-android-detour-2?n=1)](https://revive-adserver.swmansion.com/www/delivery/ck.php?zoneid=zone-gh-android-detour-2&n=1)
[![Ad](https://revive-adserver.swmansion.com/www/images/zone-gh-android-detour-3?n=1)](https://revive-adserver.swmansion.com/www/delivery/ck.php?zoneid=zone-gh-android-detour-3&n=1)

# Detour Android SDK

Detour is an Android SDK for handling deferred deep links. A deferred link works like a regular deep link, but survives the Play Store install — a user who clicks a link before having the app installed is redirected to the right screen on first launch. Detour also handles App Links and custom scheme links in a single unified API.

## Quick links

- Documentation: [https://detour.swmansion.com/docs/](https://detour.swmansion.com/docs/)
- Installation guide: [https://detour.swmansion.com/docs/sdk/android/sdk-installation](https://detour.swmansion.com/docs/sdk/android/sdk-installation)

## Create an account

You need a Detour account to generate app credentials and configure your links.  
Sign up here: [https://godetour.dev/auth/signup](https://godetour.dev/auth/signup)

## Installation

Add the SDK to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.swmansion.detour:detour-sdk:1.1.0")
}
```

### Requirements

- Minimum SDK: Android 5.0 (API 21)
- Target SDK: Android 14 (API 34)
- Kotlin 2.0+

### Permissions

The following permissions are included automatically:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

## Usage

Initialize the SDK and use `DetourDelegate` in your Activity:

<details>
<summary>Usage example</summary>

```kotlin
import android.util.Log
import com.swmansion.detour.Detour
import com.swmansion.detour.DetourConfig
import com.swmansion.detour.DetourDelegate
import com.swmansion.detour.models.LinkResult
import com.swmansion.detour.models.LinkType

class MainActivity : AppCompatActivity() {

    private val detourDelegate = DetourDelegate(
        lifecycleOwner = this,
        config = DetourConfig(
            appId = "<REPLACE_WITH_APP_ID_FROM_PLATFORM>",
            apiKey = "<REPLACE_WITH_YOUR_API_KEY>"
        ),
        onLinkResult = { result -> handleLinkResult(result) }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Detour.initialize(this, detourDelegate.config)
        detourDelegate.onCreate(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        detourDelegate.onNewIntent(intent)
    }

    private fun handleLinkResult(result: LinkResult) {
        when (result) {
            is LinkResult.Success -> {
                val url = result.url            // Full matched URL
                val route = result.route        // Extracted route for navigation
                val pathname = result.pathname  // Route path without query string
                val params = result.params      // Parsed query parameters
                val type = result.type          // DEFERRED, VERIFIED, or SCHEME

                // navigate to route
            }
            is LinkResult.NotFirstLaunch -> { /* Already processed on a previous launch */ }
            is LinkResult.NoLink -> { /* Normal app launch without any deep link */ }
            is LinkResult.Error -> {
                Log.e("MainActivity", "Error processing link", result.exception)
            }
        }
    }
}
```

</details>

### Manual control without DetourDelegate

<details>
<summary>Manual control example</summary>

```kotlin
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val config = DetourConfig(
            appId = "<REPLACE_WITH_APP_ID_FROM_PLATFORM>",
            apiKey = "<REPLACE_WITH_YOUR_API_KEY>"
        )
        Detour.initialize(this, config)

        lifecycleScope.launch {
            val result = Detour.processLink(intent)
            // handle result
        }
    }
}
```

</details>

### Controlling which links Detour processes

Use `linkProcessingMode` to control which link sources the SDK handles:

| Value           | App Links | Deferred links | Custom scheme links |
| --------------- | --------- | -------------- | ------------------- |
| `ALL` (default) | ✅        | ✅             | ✅                  |
| `WEB_ONLY`      | ✅        | ✅             | ❌                  |
| `DEFERRED_ONLY` | ❌        | ✅             | ❌                  |

<details>
<summary>linkProcessingMode config example</summary>

```kotlin
val config = DetourConfig(
    appId = "<REPLACE_WITH_APP_ID_FROM_PLATFORM>",
    apiKey = "<REPLACE_WITH_YOUR_API_KEY>",
    linkProcessingMode = LinkProcessingMode.WEB_ONLY
)
```

</details>

Use `DEFERRED_ONLY` when your navigation framework already handles App Links and scheme links — this prevents double-processing.

### Custom storage

The SDK uses `SharedPreferences` by default. You can provide a custom storage implementation via `DetourConfig`:

<details>
<summary>Custom storage example</summary>

```kotlin
import com.swmansion.detour.storage.DetourStorage

class EncryptedStorageProvider(context: Context) : DetourStorage {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "DetourSecure",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    override suspend fun getItem(key: String): String? =
        withContext(Dispatchers.IO) { prefs.getString(key, null) }

    override suspend fun setItem(key: String, value: String) {
        withContext(Dispatchers.IO) { prefs.edit().putString(key, value).apply() }
    }
}

val config = DetourConfig(
    appId = "<REPLACE_WITH_APP_ID_FROM_PLATFORM>",
    apiKey = "<REPLACE_WITH_YOUR_API_KEY>",
    storage = EncryptedStorageProvider(this)
)
```

</details>

## Analytics

The SDK includes a built-in analytics module. `Detour.initialize()` automatically tracks app opens for retention. You can also log custom events using the predefined `DetourEventNames` enum:

<details>
<summary>Analytics example</summary>

```kotlin
import com.swmansion.detour.analytics.DetourAnalytics
import com.swmansion.detour.analytics.DetourEventNames

DetourAnalytics.logEvent(DetourEventNames.Purchase)
DetourAnalytics.logRetention("week_1")
```

</details>

See the [analytics docs](https://detour.swmansion.com/docs/) for the full event list and retention tracking setup.

## Types

### DetourConfig

<details>
<summary>DetourConfig</summary>

```kotlin
data class DetourConfig(
    /** Your API key from the Detour dashboard. */
    val apiKey: String,

    /** Your application ID from the Detour dashboard. */
    val appId: String,

    /**
     * Controls which link sources are handled by the SDK (default: ALL).
     * - ALL: deferred + App Links + custom scheme links
     * - WEB_ONLY: deferred + App Links, but NOT custom scheme links
     * - DEFERRED_ONLY: only deferred links (no intent processing)
     */
    val linkProcessingMode: LinkProcessingMode = LinkProcessingMode.ALL,

    /** Custom storage implementation (defaults to SharedPreferences). */
    val storage: DetourStorage? = null
)
```

</details>

### LinkResult

<details>
<summary>LinkResult</summary>

```kotlin
sealed class LinkResult {
    data class Success(
        val url: String,                    // Full URL that was matched
        val route: String,                  // Extracted route for navigation
        val pathname: String,               // Route path without query string
        val type: LinkType,                 // DEFERRED, VERIFIED, or SCHEME
        val params: Map<String, String>     // Parsed query parameters
    ) : LinkResult()

    data object NotFirstLaunch : LinkResult()               // Not first launch
    data object NoLink : LinkResult()                       // Normal app launch
    data class Error(val exception: Exception) : LinkResult()
}
```

</details>

### LinkType

<details>
<summary>LinkType</summary>

```kotlin
enum class LinkType {
    DEFERRED,   // User clicked link before app was installed
    VERIFIED,   // App Link — http/https link with verified domain ownership
    SCHEME      // Custom scheme deep link (e.g. myapp://...)
}
```

</details>

### LinkProcessingMode

<details>
<summary>LinkProcessingMode</summary>

```kotlin
enum class LinkProcessingMode {
    ALL,            // Deferred links + App Links + custom scheme links (default)
    WEB_ONLY,       // Deferred links + App Links, no custom scheme links
    DEFERRED_ONLY   // Deferred links only — no intent processing
}
```

</details>

### DetourStorage

<details>
<summary>DetourStorage</summary>

```kotlin
interface DetourStorage {
    suspend fun getItem(key: String): String?
    suspend fun setItem(key: String, value: String)
    suspend fun removeItem(key: String) { /* optional */ }
}
```

</details>

## API Reference

### Detour

<details>
<summary>Detour singleton</summary>

```kotlin
object Detour {
    /** Initialize SDK — call once in Application.onCreate() or Activity.onCreate(). */
    fun initialize(context: Context, config: DetourConfig)

    /** Process intent and extract deep link (App Link, scheme, or deferred). */
    suspend fun processLink(intent: Intent): LinkResult

    /** Get deferred link only — ignores App Links and scheme links. */
    suspend fun getDeferredLink(): LinkResult

    /** Resolve a short link URL to its full data. */
    suspend fun resolveShortLink(url: String): ShortLinkResponse?
}
```

</details>

### DetourDelegate

<details>
<summary>DetourDelegate</summary>

```kotlin
class DetourDelegate(
    private val lifecycleOwner: LifecycleOwner,
    val config: DetourConfig,
    private val onLinkResult: (LinkResult) -> Unit
) {
    fun onCreate(intent: Intent)
    fun onNewIntent(intent: Intent)
}
```

</details>

### DetourAnalytics

<details>
<summary>DetourAnalytics</summary>

```kotlin
object DetourAnalytics {
    /** Log a predefined analytics event with optional data. */
    fun logEvent(eventName: DetourEventNames, data: Any? = null)

    /** Log a retention event. */
    fun logRetention(eventName: String)
}
```

</details>

## Other Detour SDKs

Detour is also available for other app stacks:

- iOS SDK: [https://github.com/software-mansion-labs/ios-detour](https://github.com/software-mansion-labs/ios-detour)
- Flutter SDK: [https://github.com/software-mansion-labs/detour-flutter-plugin](https://github.com/software-mansion-labs/detour-flutter-plugin)
- React Native SDK: [https://github.com/software-mansion-labs/react-native-detour](https://github.com/software-mansion-labs/react-native-detour)

---

## License

This library is licensed under [The MIT License](./LICENSE).

## Detour Android SDK is created by Software Mansion

Since 2012, [Software Mansion](https://swmansion.com) is a software agency with experience in building web and mobile apps. We are Core React Native Contributors and experts in dealing with all kinds of React Native issues. We can help you build your next dream product – [Hire us](https://swmansion.com/contact/projects?utm_source=detour&utm_medium=readme).

[![swm](https://logo.swmansion.com/logo?color=white&variant=desktop&width=150&tag=android-detour-github "Software Mansion")](https://swmansion.com)
