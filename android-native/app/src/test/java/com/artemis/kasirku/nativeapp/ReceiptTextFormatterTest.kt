package com.artemis.kasirku.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptTextFormatterTest {
    @Test fun formatsRupiahWithIndonesianThousandsSeparators() {
        assertEquals("1.234.567", ReceiptTextFormatter.money(1_234_567L))
        assertEquals("Rp 1.234.567", ReceiptTextFormatter.rupiah(1_234_567L))
        assertEquals("-1.234.567", ReceiptTextFormatter.money(-1_234_567L))
        assertEquals("0", ReceiptTextFormatter.money(0L))
    }

    @Test fun transliteratesAccentsAndSmartPunctuation() {
        assertEquals("Cafe 'Pagi' - \"Enak\"", ReceiptTextFormatter.printable("Café ‘Pagi’ – “Enak”"))
    }

    @Test fun wrapsWordsAndLongUnbrokenTokensToPrinterWidth() {
        val wrapped = ReceiptTextFormatter.wrap("Nama produk superpanjang ABCDEFGHIJKLMNOPQRSTUVWXYZ", 16)
        assertEquals(listOf("Nama produk", "superpanjang", "ABCDEFGHIJKLMNOP", "QRSTUVWXYZ"), wrapped)
        assertTrue(wrapped.all { it.length <= 16 })
    }

    @Test fun blankTextProducesOneBlankLine() {
        assertEquals(listOf(""), ReceiptTextFormatter.wrap("   "))
    }
}
