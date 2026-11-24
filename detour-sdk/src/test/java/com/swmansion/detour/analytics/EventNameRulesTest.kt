package com.swmansion.detour.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EventNameRulesTest {

    @Test
    @Suppress("DEPRECATION")
    fun `accepts every standard name`() {
        DetourEventNames.entries
            .filter { it != DetourEventNames.OpenedViaUniversalLink }
            .forEach { assertNull(it.eventName, EventNameRules.problem(it.eventName)) }
    }

    @Test
    fun `accepts custom names`() {
        listOf("level_complete", "a", "x".repeat(64), "step_2", "app_open")
            .forEach { assertNull(it, EventNameRules.problem(it)) }
    }

    @Test
    fun `refuses names that break the pattern`() {
        listOf("", "Level", "level-complete", "level complete", "x".repeat(65), "zażółć").forEach {
            assertEquals(it, "event names must match ^[a-z0-9_]{1,64}$", EventNameRules.problem(it))
        }
    }

    @Test
    fun `refuses reserved prefixes`() {
        listOf("af_install", "dt_click").forEach {
            assertEquals(it, "the af_ and dt_ prefixes are reserved", EventNameRules.problem(it))
        }
    }

    @Test
    @Suppress("DEPRECATION")
    fun `refuses opened_via_universal_link`() {
        assertEquals(
            "this name is reserved for Detour",
            EventNameRules.problem(DetourEventNames.OpenedViaUniversalLink.eventName)
        )
    }
}
