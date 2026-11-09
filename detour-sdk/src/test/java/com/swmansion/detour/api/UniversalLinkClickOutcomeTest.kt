package com.swmansion.detour.api

import com.swmansion.detour.api.DetourApiClient.Companion.universalLinkClickOutcome
import com.swmansion.detour.api.DetourApiClient.UniversalLinkClickOutcome
import com.swmansion.detour.api.DetourApiClient.UniversalLinkClickResult
import org.junit.Assert.assertEquals
import org.junit.Test

class UniversalLinkClickOutcomeTest {

    @Test
    fun `saves an allowed click`() {
        val outcome = universalLinkClickOutcome(200, """{"allowed":true,"clickId":"click-1"}""")

        assertEquals(saved(UniversalLinkClickResult(allowed = true, clickId = "click-1")), outcome)
    }

    @Test
    fun `denies the link when the click limit is exceeded`() {
        val body = """{"allowed":false,"error":"Limit","code":"CLICK_LIMIT_EXCEEDED","clicksInPeriod":100,"effectiveLimit":100}"""

        val outcome = universalLinkClickOutcome(402, body)

        val denied = UniversalLinkClickResult(
            allowed = false,
            error = "Limit",
            code = "CLICK_LIMIT_EXCEEDED",
            clicksInPeriod = 100,
            effectiveLimit = 100
        )
        assertEquals(saved(denied), outcome)
    }

    @Test
    fun `denies the link on 402 without a readable body`() {
        val outcome = universalLinkClickOutcome(402, null)

        assertEquals(saved(UniversalLinkClickResult(allowed = false, error = "Click limit exceeded")), outcome)
    }

    @Test
    fun `denies the link when a success response says it is not allowed`() {
        val outcome = universalLinkClickOutcome(200, """{"allowed":false}""")

        assertEquals(false, (outcome as UniversalLinkClickOutcome.Saved).result.allowed)
    }

    @Test
    fun `does not save the click on an error response`() {
        assertEquals(UniversalLinkClickOutcome.NotSaved, universalLinkClickOutcome(500, """{"error":"boom"}"""))
        assertEquals(UniversalLinkClickOutcome.NotSaved, universalLinkClickOutcome(429, null))
    }

    @Test
    fun `allows the link when a success response cannot be read`() {
        val outcome = universalLinkClickOutcome(200, "not json")

        assertEquals(saved(UniversalLinkClickResult(allowed = true)), outcome)
    }

    private fun saved(result: UniversalLinkClickResult) = UniversalLinkClickOutcome.Saved(result)
}
