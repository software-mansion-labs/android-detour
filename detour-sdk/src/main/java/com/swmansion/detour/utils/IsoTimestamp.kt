package com.swmansion.detour.utils

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** UTC with milliseconds: the format of every new timestamp field in the API contract. */
internal object IsoTimestamp {
    private const val PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"

    // SimpleDateFormat is not thread-safe, so each call gets its own.
    private fun format() = SimpleDateFormat(PATTERN, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

    fun format(atMs: Long): String = format().format(Date(atMs))

    fun parse(value: String): Long? = try {
        format().parse(value)?.time
    } catch (e: ParseException) {
        null
    }
}
