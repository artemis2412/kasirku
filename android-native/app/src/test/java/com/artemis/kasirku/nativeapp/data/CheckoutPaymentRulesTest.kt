package com.artemis.kasirku.nativeapp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CheckoutPaymentRulesTest {
    @Test
    fun cashAllowsOverpaymentAndCalculatesChange() {
        val result = CheckoutPaymentRules.validate(25_000L, 5_000L, 25_000L, " cash ")
        assertEquals("CASH", result.method)
        assertEquals(20_000L, result.total)
        assertEquals(5_000L, result.change)
    }

    @Test
    fun cashRejectsUnderpayment() {
        assertThrows(IllegalArgumentException::class.java) {
            CheckoutPaymentRules.validate(20_000L, 0L, 19_999L, "CASH")
        }
    }

    @Test
    fun nonCashRequiresExactAmount() {
        val result = CheckoutPaymentRules.validate(20_000L, 0L, 20_000L, "qris")
        assertEquals("QRIS", result.method)
        assertEquals(0L, result.change)
        assertThrows(IllegalArgumentException::class.java) {
            CheckoutPaymentRules.validate(20_000L, 0L, 21_000L, "TRANSFER")
        }
    }

    @Test
    fun rejectsUnsupportedMethodsAndInvalidDiscounts() {
        assertThrows(IllegalArgumentException::class.java) {
            CheckoutPaymentRules.validate(20_000L, 0L, 20_000L, "EWALLET")
        }
        assertThrows(IllegalArgumentException::class.java) {
            CheckoutPaymentRules.validate(20_000L, 20_001L, 0L, "CASH")
        }
    }

    @Test
    fun rejectsZeroTotalAndBlankMethod() {
        assertThrows(IllegalArgumentException::class.java) {
            CheckoutPaymentRules.validate(20_000L, 20_000L, 0L, "CASH")
        }
        assertThrows(IllegalArgumentException::class.java) {
            CheckoutPaymentRules.validate(20_000L, 0L, 20_000L, " ")
        }
    }
}
