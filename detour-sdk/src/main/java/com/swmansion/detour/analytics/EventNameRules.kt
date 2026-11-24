package com.swmansion.detour.analytics

// Mirrors `lib/services/eventsService/eventNames.ts` in detour-website. Keep in sync.
internal object EventNameRules {
    private val PATTERN = Regex("^[a-z0-9_]{1,64}$")

    /** `af_` marks events imported from AppsFlyer; `dt_` is kept for Detour's own names. */
    private val RESERVED_PREFIXES = listOf("af_", "dt_")

    /** The server turns these into link clicks, so an app may not log them itself. */
    private val RESERVED_NAMES = setOf("opened_via_universal_link")

    /** The server's limit for `data`, as UTF-8 bytes of its JSON. */
    const val MAX_DATA_BYTES = 2048

    /** Why the server would refuse [name], or `null` when it is a valid event name. */
    fun problem(name: String): String? = when {
        !PATTERN.matches(name) -> "event names must match ${PATTERN.pattern}"
        RESERVED_PREFIXES.any { name.startsWith(it) } ->
            "the ${RESERVED_PREFIXES.joinToString(" and ")} prefixes are reserved"
        name in RESERVED_NAMES -> "this name is reserved for Detour"
        else -> null
    }
}
