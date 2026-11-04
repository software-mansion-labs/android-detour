package com.swmansion.detour.consent

import com.swmansion.detour.utils.IsoTimestamp

/**
 * The user's choice from your consent platform, passed with [com.swmansion.detour.Detour.setConsent].
 *
 * @property purposes Purpose ID ([DetourPurpose]) → granted. A purpose missing from the map is not granted.
 * @property timestampMs When the user made the choice.
 * @property consentTextRef The version of the consent text the user saw, for example the consent platform's version.
 * @property schemaVersion Version of the purpose list. `null` uses the version from the Detour server.
 * @property region The user's legal regime, for example `eea`.
 * @property source Where the choice came from, for example `cmp`.
 */
data class DetourConsent @JvmOverloads constructor(
    val purposes: Map<String, Boolean>,
    val timestampMs: Long = System.currentTimeMillis(),
    val consentTextRef: String? = null,
    val schemaVersion: Int? = null,
    val region: String? = null,
    val source: String? = null,
) {
    /** The wire format of the API contract, also used to store the consent on the device. */
    internal fun toWire(): Map<String, Any> = buildMap {
        put("purposes", purposes)
        put("timestamp", IsoTimestamp.format(timestampMs))
        consentTextRef?.let { put("consent_text_ref", it) }
        schemaVersion?.let { put("schema_version", it) }
        region?.let { put("region", it) }
        source?.let { put("source", it) }
    }

    internal companion object {
        fun fromWire(wire: Map<*, *>): DetourConsent? {
            val purposes = (wire["purposes"] as? Map<*, *>)
                ?.mapNotNull { (key, value) -> if (key is String && value is Boolean) key to value else null }
                ?.toMap()
                ?: return null
            val timestampMs = (wire["timestamp"] as? String)?.let(IsoTimestamp::parse) ?: return null
            return DetourConsent(
                purposes = purposes,
                timestampMs = timestampMs,
                consentTextRef = wire["consent_text_ref"] as? String,
                // Gson reads every JSON number as a Double.
                schemaVersion = (wire["schema_version"] as? Number)?.toInt(),
                region = wire["region"] as? String,
                source = wire["source"] as? String,
            )
        }
    }
}
