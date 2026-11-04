package com.swmansion.detour

/**
 * Turns on MMP mode when set as [DetourConfig.attribution]: requests report `X-SDK-Package: mmp`,
 * so the Detour backend may attribute installs and conversions to ad networks and send them
 * postbacks. Whether it does is decided on the backend (the app's attribution plan, consent and
 * the network's settings). It collects nothing beyond the core SDK; options of the attribution
 * features are added here.
 */
class DetourAttributionConfig
