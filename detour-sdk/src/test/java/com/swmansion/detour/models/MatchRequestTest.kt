package com.swmansion.detour.models

import com.google.gson.Gson
import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MatchRequestTest {

    private fun json(request: MatchRequest): JsonObject =
        Gson().toJsonTree(request).asJsonObject

    @Test
    fun `direct request sends device_id and user_id in snake case`() {
        val body = json(DirectMatchRequest("c_1", "3f2b8c1e-9d4a-4c55-8f0e-2a6b7c8d9e01", "u_12345"))

        assertEquals("c_1", body["clickId"].asString)
        assertEquals("3f2b8c1e-9d4a-4c55-8f0e-2a6b7c8d9e01", body["device_id"].asString)
        assertEquals("u_12345", body["user_id"].asString)
    }

    @Test
    fun `user_id is omitted when not set`() {
        assertFalse(json(DirectMatchRequest("c_1", "3f2b8c1e-9d4a-4c55-8f0e-2a6b7c8d9e01")).has("user_id"))
    }
}
