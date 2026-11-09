package com.swmansion.detour.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnalyticsApiErrorTest {

    @Test
    fun `reads error and code from the body`() {
        assertEquals("Invalid currency (invalid_body)", AnalyticsApiClient.apiError("""{"error":"Invalid currency","code":"invalid_body"}"""))
        assertEquals("Invalid currency", AnalyticsApiClient.apiError("""{"error":"Invalid currency"}"""))
    }

    @Test
    fun `ignores bodies without an API error`() {
        assertNull(AnalyticsApiClient.apiError(null))
        assertNull(AnalyticsApiClient.apiError(""))
        assertNull(AnalyticsApiClient.apiError("<html>Bad Gateway</html>"))
        assertNull(AnalyticsApiClient.apiError("""{"ok":false}"""))
    }
}
