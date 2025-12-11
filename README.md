# Detour Android SDK

### Android Detour is SDK for handling deferred deep links for Android applications

## Documentation

Check out our dedicated documentation page for info about this library, API reference and more: [https://docs.swmansion.com/detour/docs/](https://docs.swmansion.com/detour/docs/)

## Create account on platform

Create account and configure your links: [https://godetour.dev/auth/signup](https://godetour.dev/auth/signup)

## Installation

Add the SDK to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.detour:detour-sdk:0.1.0")
}
```

## Usage

#### Initialize SDK and use `DetourDelegate` in your Activity

```kotlin
import com.detour.sdk.Detour
import com.detour.sdk.DetourConfig
import com.detour.sdk.DetourDelegate
import com.detour.sdk.models.LinkResult
import com.detour.sdk.models.LinkType

class MainActivity : AppCompatActivity() {

    private val detourDelegate = DetourDelegate(
        activity = this,
        config = DetourConfig(
            appId = "<REPLACE_WITH_APP_ID_FROM_PLATFORM>",
            apiKey = "<REPLACE_WITH_YOUR_API_KEY>",
            shouldUseClipboard = true
        ),
        onLinkResult = { result -> handleLinkResult(result) }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize SDK
        Detour.initialize(this, detourDelegate.config)

        // Process links (Universal + Deferred)
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
                // Link successfully processed
                val link = result.link    // Full matched URL
                val route = result.route  // Extracted route for navigation
                val type = result.type    // DEFERRED or UNIVERSAL

                // Navigate based on route
                route?.let { navigateToRoute(it) }
            }

            is LinkResult.NotFirstLaunch -> {
                // Already processed on previous launch
            }

            is LinkResult.NoLink -> {
                // Normal app launch without any deep link
            }

            is LinkResult.Error -> {
                // Handle error
                Log.e(TAG, "Error processing link", result.exception)
            }
        }
    }

    private fun navigateToRoute(route: String) {
        when {
            route.startsWith("/product/") -> {
                val productId = route.removePrefix("/product/").trim('/')
                // Navigate to product screen
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

The package exposes several types to help you with type-checking in your own codebase.

**DetourConfig**

Configuration for the SDK:

```kotlin
data class DetourConfig(
    /**
     * Your application ID from the Detour dashboard.
     */
    val appId: String,

    /**
     * Your API key from the Detour dashboard.
     */
    val apiKey: String,

    /**
     * Optional: Check clipboard for deferred links on first launch.
     * Defaults to true if not provided.
     */
    val shouldUseClipboard: Boolean = true
)
```

**LinkResult**

Result types returned by the SDK:

```kotlin
sealed class LinkResult {
    /**
     * Link successfully processed.
     */
    data class Success(
        val link: String,        // Full URL that was matched
        val route: String?,      // Extracted route for navigation
        val type: LinkType       // DEFERRED or UNIVERSAL
    ) : LinkResult()

    /**
     * No deferred link available - not first launch.
     */
    object NotFirstLaunch : LinkResult()

    /**
     * No link found - normal app launch.
     */
    object NoLink : LinkResult()

    /**
     * Error occurred during processing.
     */
    data class Error(val exception: Exception) : LinkResult()
}
```

**LinkType**

Type of deep link:

```kotlin
enum class LinkType {
    /**
     * Deferred deep link - user clicked link before app was installed.
     */
    DEFERRED,

    /**
     * Universal App Link - user clicked link with app already installed.
     */
    UNIVERSAL
}
```

## API Reference

### Detour

Main SDK singleton:

```kotlin
object Detour {
    /**
     * Initialize SDK (call once in Application.onCreate or Activity.onCreate)
     */
    fun initialize(context: Context, config: DetourConfig)

    /**
     * Process intent and extract deep link.
     * Automatically detects Universal or Deferred links.
     */
    suspend fun processLink(intent: Intent): LinkResult

    /**
     * Get deferred link only (ignore Universal Links).
     */
    suspend fun getDeferredLink(): LinkResult
}
```

### DetourDelegate

Convenience class for automatic link handling:

```kotlin
class DetourDelegate(
    private val activity: AppCompatActivity,
    val config: DetourConfig,
    private val onLinkResult: (LinkResult) -> Unit
) {
    /**
     * Call from Activity.onCreate()
     */
    fun onCreate(intent: Intent)

    /**
     * Call from Activity.onNewIntent()
     */
    fun onNewIntent(intent: Intent)
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

If you need more control over link processing:

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

#### Get deferred link only

```kotlin
lifecycleScope.launch {
    val result = Detour.getDeferredLink()
    // Will only return deferred links, not universal links
}
```

## :balance_scale: License

This library is licensed under [The MIT License](../LICENSE).

## Detour Android SDK is created by Software Mansion

Since 2012, [Software Mansion](https://swmansion.com) is a software agency with experience in building web and mobile apps. We are Core React Native Contributors and experts in dealing with all kinds of React Native issues. We can help you build your next dream product – [Hire us](https://swmansion.com/contact/projects?utm_source=detour&utm_medium=readme).

[![swm](https://logo.swmansion.com/logo?color=white&variant=desktop&width=150&tag=react-native-detour-github 'Software Mansion')](https://swmansion.com)

