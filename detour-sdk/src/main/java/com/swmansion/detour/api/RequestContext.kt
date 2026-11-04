package com.swmansion.detour.api

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import java.util.Locale

/**
 * App and device context sent with every request next to the request's own fields.
 */
internal data class RequestContext(
    val appVersion: String? = null,
    val buildNumber: String? = null,
    val osVersion: String? = null,
    val installSource: String? = null,
    val networkType: String? = null,
    val locale: String? = null,
    val sessionId: String? = null,
    val consentSnapshot: Map<String, Any>? = null,
) {
    /** match-link keeps `locale` as its v1 array of device signals. */
    fun forMatchLink(): RequestContext = copy(locale = null)

    fun toMap(): Map<String, Any> = listOfNotNull<Pair<String, Any>>(
        appVersion?.let { "app_version" to it },
        buildNumber?.let { "build_number" to it },
        osVersion?.let { "os_version" to it },
        installSource?.let { "install_source" to it },
        networkType?.let { "network_type" to it },
        locale?.let { "locale" to it },
        sessionId?.let { "session_id" to it },
        consentSnapshot?.let { "consent_snapshot" to it },
    ).toMap()
}

internal class RequestContextProvider(
    private val context: Context,
    private val sessionId: () -> String? = { null },
    private val consentSnapshot: () -> Map<String, Any>? = { null },
) {

    private val packageInfo: PackageInfo? by lazy {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
        }.getOrNull()
    }

    private val installSource: String? by lazy {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getInstallerPackageName(context.packageName)
            }
        }.getOrNull()
    }

    fun current(): RequestContext = RequestContext(
        appVersion = packageInfo?.versionName,
        buildNumber = packageInfo?.let(::versionCode),
        osVersion = Build.VERSION.RELEASE,
        installSource = installSource,
        networkType = networkType(),
        locale = Locale.getDefault().toLanguageTag(),
        sessionId = sessionId(),
        consentSnapshot = consentSnapshot(),
    )

    private fun versionCode(info: PackageInfo): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode.toString()
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toString()
        }

    private fun networkType(): String? = runCatching {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return@runCatching null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return@runCatching null
            when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
                else -> "other"
            }
        } else {
            @Suppress("DEPRECATION")
            when (manager.activeNetworkInfo?.takeIf { it.isConnected }?.type) {
                null -> null
                ConnectivityManager.TYPE_WIFI -> "wifi"
                ConnectivityManager.TYPE_MOBILE -> "cellular"
                else -> "other"
            }
        }
    }.getOrNull()
}
