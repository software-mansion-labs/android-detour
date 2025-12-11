package com.detour.sdk.fingerprint

import android.content.ClipboardManager
import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.util.DisplayMetrics
import com.detour.sdk.models.LocaleInfo
import com.detour.sdk.models.ProbabilisticFingerprint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.TimeZone

/**
 * Collects device fingerprint data for link matching.
 */
internal class FingerprintCollector(private val context: Context) {

    /**
     * Collect probabilistic device fingerprint.
     */
    suspend fun collectFingerprint(shouldUseClipboard: Boolean): ProbabilisticFingerprint =
        withContext(Dispatchers.IO) {
            val displayMetrics = getDisplayMetrics()
            val locale = Locale.getDefault()
            val timezone = TimeZone.getDefault()

            ProbabilisticFingerprint(
                platform = "android",
                model = Build.MODEL,
                manufacturer = Build.MANUFACTURER,
                systemVersion = Build.VERSION.RELEASE,
                screenWidth = displayMetrics.widthPixels,
                screenHeight = displayMetrics.heightPixels,
                scale = displayMetrics.density,
                locale = listOf(LocaleInfo(languageTag = locale.toLanguageTag())),
                timezone = timezone.id,
                userAgent = getUserAgent(),
                timestamp = System.currentTimeMillis(),
                pastedLink = if (shouldUseClipboard) getClipboardContent() else null
            )
        }

    private fun getDisplayMetrics(): DisplayMetrics {
        return Resources.getSystem().displayMetrics
    }

    private fun getUserAgent(): String? {
        return try {
            System.getProperty("http.agent")
        } catch (_: Exception) {
            null
        }
    }

    private fun getClipboardContent(): String? {
        return try {
            val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clipData = clipboardManager?.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                clipData.getItemAt(0)?.text?.toString()
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}
