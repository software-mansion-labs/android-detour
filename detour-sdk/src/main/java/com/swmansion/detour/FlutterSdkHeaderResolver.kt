// CONTRACT: The Flutter plugin (detour-flutter-plugin) ships a marker class
// at exactly "com.swmansion.detour.DetourFlutterMarker" with a static field
// "SDK_HEADER_VALUE". If either name changes, this lookup silently fails and
// Flutter apps will be reported as native Android.
// Counterpart: detour-flutter-plugin/android/src/main/kotlin/.../DetourFlutterMarker.kt

package com.swmansion.detour

internal object FlutterSdkHeaderResolver {
    private const val MARKER_CLASS_NAME = "com.swmansion.detour.DetourFlutterMarker"
    private const val HEADER_FIELD_NAME = "SDK_HEADER_VALUE"

    val sdkHeaderValue: String by lazy {
        try {
            // Flutter wrapper ships this marker class; pure native apps do not.
            val markerClass = Class.forName(MARKER_CLASS_NAME)
            val field = markerClass.getDeclaredField(HEADER_FIELD_NAME)
            field.isAccessible = true
            field.get(null) as? String ?: BuildConfig.SDK_HEADER_VALUE
        } catch (_: Throwable) {
            // Fallback for native Android SDK consumers.
            BuildConfig.SDK_HEADER_VALUE
        }
    }
}
