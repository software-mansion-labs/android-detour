package com.swmansion.detour

/**
 * Turns on attribution mode when set as [DetourConfig.attribution]: once consent for
 * [com.swmansion.detour.consent.DetourPurpose.ATTRIBUTION] is passed on this launch, requests
 * report `X-SDK-Package: mmp`, so the Detour backend may attribute installs and conversions to ad
 * networks and send them postbacks. Whether it does is decided on the backend (the app's
 * attribution plan and the network's settings).
 *
 * @property facebookAppId Your Meta (Facebook) app ID. With it, the first deferred-matching request
 * carries the Meta Install Referrer read from the Facebook, Instagram or Facebook Lite app, so
 * installs from Meta ads can be attributed. Your manifest also needs the `<queries>` entries from
 * the README. `null` reads nothing.
 * @property isChildDirected `true` for an app for children (COPPA, Google Play Families): the SDK
 * then reads no device identifier.
 * @property readsAppSetId `false` keeps the App Set ID on the device, also when the Detour server
 * turns it on.
 */
class DetourAttributionConfig @JvmOverloads constructor(
    val facebookAppId: String? = null,
    val isChildDirected: Boolean = false,
    val readsAppSetId: Boolean = true,
)
