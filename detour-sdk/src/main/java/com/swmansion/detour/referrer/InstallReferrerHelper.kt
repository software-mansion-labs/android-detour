package com.swmansion.detour.referrer

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerClient.InstallReferrerResponse
import com.android.installreferrer.api.InstallReferrerStateListener
import com.android.installreferrer.api.ReferrerDetails
import com.swmansion.detour.models.InstallReferrerDetails
import com.swmansion.detour.models.ReferrerUtm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.net.URLDecoder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * Helper for retrieving install referrer information.
 */
internal class InstallReferrerHelper(private val context: Context) {

    private data class ReferrerResponse(val code: Int, val details: ReferrerDetails?)

    /**
     * Get the install referrer with its parsed parameters and timestamps.
     * Returns null if not available or on error.
     */
    suspend fun getReferrerDetails(): InstallReferrerDetails? = withContext(Dispatchers.IO) {
        try {
            val playReferrer = readReferrer() ?: return@withContext null
            val raw = playReferrer.installReferrer?.takeIf { it.isNotBlank() } ?: return@withContext null
            buildDetails(
                raw = raw,
                clickTs = playReferrer.referrerClickTimestampSeconds,
                installBeginTs = playReferrer.installBeginTimestampSeconds,
                clickTsServer = playReferrer.referrerClickTimestampServerSeconds,
                installBeginTsServer = playReferrer.installBeginTimestampServerSeconds,
                googlePlayInstant = playReferrer.googlePlayInstantParam,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting install referrer", e)
            null
        }
    }

    // SERVICE_UNAVAILABLE is a failed connection to the Play Store app and can pass, so it gets one retry.
    // Other codes (DEVELOPER_ERROR, FEATURE_NOT_SUPPORTED) will not change on a retry.
    private suspend fun readReferrer(): ReferrerDetails? {
        val first = connect()
        if (first.code != InstallReferrerResponse.SERVICE_UNAVAILABLE) return first.details

        Log.w(TAG, "Install referrer service unavailable, retrying once")
        delay(RETRY_DELAY_MS)
        return connect().details
    }

    private suspend fun connect(): ReferrerResponse = suspendCancellableCoroutine { continuation ->
        val referrerClient = InstallReferrerClient.newBuilder(context).build()
        val completed = AtomicBoolean(false)

        fun resumeOnce(value: ReferrerResponse) {
            if (!completed.compareAndSet(false, true)) return

            try {
                referrerClient.endConnection()
            } catch (e: Exception) {
                Log.e(TAG, "Error ending connection", e)
            }

            if (continuation.isActive) {
                continuation.resume(value)
            }
        }

        referrerClient.startConnection(object : InstallReferrerStateListener {
            override fun onInstallReferrerSetupFinished(responseCode: Int) {
                when (responseCode) {
                    InstallReferrerResponse.OK -> {
                        try {
                            resumeOnce(ReferrerResponse(responseCode, referrerClient.installReferrer))
                        } catch (e: Exception) {
                            Log.e(TAG, "Error reading install referrer", e)
                            resumeOnce(ReferrerResponse(responseCode, null))
                        }
                    }
                    else -> {
                        Log.w(TAG, "Install referrer response code: $responseCode")
                        resumeOnce(ReferrerResponse(responseCode, null))
                    }
                }
            }

            override fun onInstallReferrerServiceDisconnected() {
                Log.w(TAG, "Install referrer service disconnected")
                resumeOnce(ReferrerResponse(InstallReferrerResponse.SERVICE_DISCONNECTED, null))
            }
        })

        continuation.invokeOnCancellation {
            resumeOnce(ReferrerResponse(InstallReferrerResponse.SERVICE_DISCONNECTED, null))
        }
    }

    @VisibleForTesting
    internal fun buildDetails(
        raw: String,
        clickTs: Long,
        installBeginTs: Long,
        clickTsServer: Long,
        installBeginTsServer: Long,
        googlePlayInstant: Boolean,
    ): InstallReferrerDetails {
        val params = parseReferrerParams(raw)
        val utm = ReferrerUtm(
            source = params["utm_source"],
            medium = params["utm_medium"],
            campaign = params["utm_campaign"],
            content = params["utm_content"],
            term = params["utm_term"],
        )
        return InstallReferrerDetails(
            raw = raw,
            utm = utm.takeIf { it != EMPTY_UTM },
            gclid = params["gclid"],
            clickTs = clickTs,
            installBeginTs = installBeginTs,
            clickTsServer = clickTsServer,
            installBeginTsServer = installBeginTsServer,
            googlePlayInstant = googlePlayInstant,
            clickId = params["click_id"],
        )
    }

    @VisibleForTesting
    internal fun extractClickId(referrerUrl: String): String? = parseReferrerParams(referrerUrl)["click_id"]

    /**
     * Splits the referrer into parameters, then URL-decodes each key and value, so an encoded
     * `&` or `=` stays inside its value. A referrer with no `=` but with `%3D` is encoded twice
     * and gets decoded once before splitting. One leading `?` is ignored. The first value of a
     * repeated key wins; empty keys and values are left out. Values keep their case (gclid).
     */
    @VisibleForTesting
    internal fun parseReferrerParams(raw: String): Map<String, String> {
        val query = unwrapDoubleEncoding(raw).removePrefix("?")
        return query.split("&")
            .mapNotNull { param ->
                val separator = param.indexOf('=')
                if (separator <= 0 || separator == param.lastIndex) return@mapNotNull null
                decode(param.substring(0, separator)) to decode(param.substring(separator + 1))
            }
            .distinctBy { it.first }
            .toMap()
    }

    private fun unwrapDoubleEncoding(raw: String): String {
        if (raw.contains('=') || !raw.contains("%3D", ignoreCase = true)) return raw
        return decode(raw)
    }

    private fun decode(part: String): String = try {
        URLDecoder.decode(part, "UTF-8")
    } catch (e: IllegalArgumentException) {
        Log.w(TAG, "Install referrer part is not URL-encoded correctly; using it as is", e)
        part
    }

    companion object {
        private const val TAG = "InstallReferrerHelper"
        private const val RETRY_DELAY_MS = 1_000L
        private val EMPTY_UTM = ReferrerUtm(null, null, null, null, null)
        private val UUID_REGEX =
            Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", RegexOption.IGNORE_CASE)

        /**
         * The backend matches only UUID click IDs. Other values, like `null` from older
         * Detour Play Store links or an ID from another tool, need signal matching instead.
         */
        fun isDirectMatchClickId(clickId: String): Boolean = UUID_REGEX.matches(clickId)
    }
}
