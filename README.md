<img src="https://github.com/user-attachments/assets/c965b51b-7307-477a-8d22-9c9cd6da6231" alt="React Native Detour by Software Mansion" width="100%"/>

# Detour Android SDK

Android SDK for handling deferred deep links

## Documentation

Check out our dedicated documentation page for info about this library, API reference and more: [https://docs.swmansion.com/detour/docs/](https://docs.swmansion.com/detour/docs/)

## Create account on platform

Create account and configure your links: [https://godetour.dev/auth/signup](https://godetour.dev/auth/signup)

## Installation

Add the SDK to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.swmansion:detour-sdk:0.1.0")
}
```

## Usage

### Initialize SDK and use `DetourDelegate` in your Activity

```kotlin
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

        // Initialize SDK
        Detour.initialize(this, detourDelegate.config)

        // Process links (Universal + Scheme + Deferred)
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

                navigateToRoute(route)
            }

            is LinkResult.NotFirstLaunch -> {
                // Already processed on previous launch
            }

            is LinkResult.NoLink -> {
                // Normal app launch without any deep link
            }

            is LinkResult.Error -> {
                Log.e(TAG, "Error processing link", result.exception)
            }
        }
    }

    private fun navigateToRoute(route: String) {
        when {
            route.startsWith("/product/") -> {
                val productId = route.removePrefix("/product/").split("?").first()
                // Navigate to product screen with productId
            }
            route.startsWith("/promo") -> {
                // Navigate to promo screen
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
```

## Types

**DetourConfig**

Configuration for the SDK:

```kotlin
data class DetourConfig(
    /** Your API key from the Detour dashboard. */
    val apiKey: String,

    /** Your application ID from the Detour dashboard. */
    val appId: String,

    /**
     * Controls which link sources are handled by the SDK (default: ALL).
     * - ALL: deferred + Universal/App Links + custom scheme links
     * - WEB_ONLY: deferred + Universal/App Links, but NOT custom scheme links
     * - DEFERRED_ONLY: only deferred links (no intent processing)
     */
    val linkProcessingMode: LinkProcessingMode = LinkProcessingMode.ALL,

    /** Custom storage implementation (defaults to SharedPreferences). */
    val storage: DetourStorage? = null
)
```

**LinkResult**

Result types returned by the SDK:

```kotlin
sealed class LinkResult {
    data class Success(
        val url: String,                       // Full URL that was matched
        val route: String,                     // Extracted route for navigation
        val pathname: String,                  // Route path without query string
        val type: LinkType,                    // DEFERRED, VERIFIED, or SCHEME
        val params: Map<String, String>        // Parsed query parameters
    ) : LinkResult()

    data object NotFirstLaunch : LinkResult()  // Not first launch
    data object NoLink : LinkResult()          // Normal app launch
    data class Error(val exception: Exception) : LinkResult()
}
```

**LinkType**

Type of deep link:

```kotlin
enum class LinkType {
    DEFERRED,   // User clicked link before app was installed
    VERIFIED,   // Verified App Link — http/https link, domain ownership verified via Digital Asset Links
    SCHEME      // Custom scheme deep link (e.g. myapp://...)
}
```

**LinkProcessingMode**

Controls which link sources are handled:

```kotlin
enum class LinkProcessingMode {
    ALL,            // Handle all link types (default)
    WEB_ONLY,       // Handle Universal Links + deferred, skip custom schemes
    DEFERRED_ONLY   // Only handle deferred links
}
```

**DetourStorage**

Interface for custom storage implementations:

```kotlin
interface DetourStorage {
    suspend fun getItem(key: String): String?
    suspend fun setItem(key: String, value: String)
    suspend fun removeItem(key: String) { /* optional */ }
}
```

## API Reference

### Detour

Main SDK singleton:

```kotlin
object Detour {
    /** Initialize SDK (call once in Application.onCreate or Activity.onCreate) */
    fun initialize(context: Context, config: DetourConfig)

    /** Process intent and extract deep link (Universal, Scheme, or Deferred) */
    suspend fun processLink(intent: Intent): LinkResult

    /** Get deferred link only (ignore Universal/Scheme Links) */
    suspend fun getDeferredLink(): LinkResult

    /** Resolve a short link URL to its full data */
    suspend fun resolveShortLink(url: String): ShortLinkResponse?
}
```

### DetourDelegate

Convenience class for automatic link handling:

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

### DetourAnalytics

Analytics API for tracking events:

```kotlin
object DetourAnalytics {
    /** Log a predefined analytics event with optional data */
    fun logEvent(eventName: DetourEventNames, data: Any? = null)

    /** Log a retention event */
    fun logRetention(eventName: String)
}
```

## Requirements

- **Minimum SDK**: Android 5.0 (API 21)
- **Target SDK**: Android 14 (API 34)
- **Language**: Kotlin 2.0+

## Permissions

The SDK requires the following permissions (automatically included):

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

## Advanced Usage

#### Manual control without DetourDelegate

```kotlin
class MainActivity : AppCompatActivity() {

    private val config = DetourConfig(
        appId = "your-app-id",
        apiKey = "your-api-key"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Detour.initialize(this, config)

        lifecycleScope.launch {
            val result = Detour.processLink(intent)
            handleResult(result)
        }
    }
}
```

#### Deferred-only mode

Use when your navigation framework already handles Universal/Scheme Links:

```kotlin
val config = DetourConfig(
    appId = "your-app-id",
    apiKey = "your-api-key",
    linkProcessingMode = LinkProcessingMode.DEFERRED_ONLY
)
```

#### Get deferred link only (without DetourDelegate)

```kotlin
lifecycleScope.launch {
    val result = Detour.getDeferredLink()
    // Will only return deferred links, not universal or scheme links
}
```

#### Custom Storage Provider

The SDK uses storage to persist the "first entrance" flag and device ID. By default, it uses `SharedPreferences`, but you can provide a custom implementation:

```kotlin
import com.swmansion.detour.storage.DetourStorage

// Example: Using EncryptedSharedPreferences
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
    appId = "your-app-id",
    apiKey = "your-api-key",
    storage = EncryptedStorageProvider(this)
)
```

## License

This library is licensed under [The MIT License](../LICENSE).

## Detour Android SDK is created by Software Mansion

Since 2012, [Software Mansion](https://swmansion.com) is a software agency with experience in building web and mobile apps. We are Core React Native Contributors and experts in dealing with all kinds of React Native issues. We can help you build your next dream product – [Hire us](https://swmansion.com/contact/projects?utm_source=detour&utm_medium=readme).

[![swm](https://logo.swmansion.com/logo?color=white&variant=desktop&width=150&tag=react-native-detour-github 'Software Mansion')](https://swmansion.com)
