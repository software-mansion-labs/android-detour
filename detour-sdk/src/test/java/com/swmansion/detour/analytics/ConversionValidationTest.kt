package com.swmansion.detour.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class ConversionValidationTest {

    private val valid = DetourConversion(
        name = "purchase",
        revenue = BigDecimal("4.99"),
        currency = "USD",
        revenueType = RevenueType.IAP,
        productId = "coins_500",
        quantity = 1,
        transactionId = "GPA.1234",
    )

    private fun ruleFor(conversion: DetourConversion) = conversion.validationError()?.substringBefore(" ")

    @Test
    fun `accepts a valid conversion and the edges of each rule`() {
        assertNull(valid.validationError())
        assertNull(valid.copy(productId = null, quantity = null, transactionId = null).validationError())
        assertNull(valid.copy(name = "a".repeat(255), transactionId = "t".repeat(255), productId = "").validationError())
        assertNull(valid.copy(revenue = BigDecimal.ZERO, quantity = 0, currency = "usd").validationError())
        assertNull(valid.copy(revenue = BigDecimal("999999999999.999999")).validationError())
    }

    @Test
    fun `names the field that breaks a rule`() {
        assertEquals("name", ruleFor(valid.copy(name = "")))
        assertEquals("name", ruleFor(valid.copy(name = "a".repeat(256))))
        assertEquals("revenue", ruleFor(valid.copy(revenue = BigDecimal("-0.01"))))
        assertEquals("revenue", ruleFor(valid.copy(revenue = BigDecimal("1000000000000"))))
        assertEquals("currency", ruleFor(valid.copy(currency = "US")))
        assertEquals("currency", ruleFor(valid.copy(currency = "US1")))
        assertEquals("currency", ruleFor(valid.copy(currency = "ŁUK")))
        assertEquals("quantity", ruleFor(valid.copy(quantity = -1)))
        assertEquals("productId", ruleFor(valid.copy(productId = "p".repeat(256))))
        assertEquals("transactionId", ruleFor(valid.copy(transactionId = "")))
        assertEquals("transactionId", ruleFor(valid.copy(transactionId = "t".repeat(256))))
    }

    @Test
    fun `counts length in UTF-16 units like the backend`() {
        // Each emoji is two UTF-16 units, so 128 of them is 256 characters for the backend.
        assertEquals("name", ruleFor(valid.copy(name = "😀".repeat(128))))
    }

    @Test
    fun `message states the rule`() {
        assertEquals("currency must be a three-letter ISO 4217 code", valid.copy(currency = "dollars").validationError())
    }
}
