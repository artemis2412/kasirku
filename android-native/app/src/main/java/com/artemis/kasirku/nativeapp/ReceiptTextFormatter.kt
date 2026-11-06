package com.artemis.kasirku.nativeapp

import java.text.Normalizer

/** Pure receipt text formatting, kept separate from Bluetooth I/O for deterministic tests. */
internal object ReceiptTextFormatter {
    fun printable(value: String): String {
        val normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace("\\p{M}+".toRegex(), "")
            .replace('–', '-').replace('—', '-')
            .replace("’", "'").replace("‘", "'")
            .replace('“', '"').replace('”', '"')
        return normalized.map { if (it.code in 32..126) it else '?' }.joinToString("")
    }

    fun wrap(value: String, width: Int = 32): List<String> {
        require(width > 0) { "Lebar struk harus lebih dari nol." }
        val text = printable(value).trim()
        if (text.isEmpty()) return listOf("")
        val result = mutableListOf<String>()
        var current = StringBuilder()
        text.split(Regex("\\s+")).forEach { word ->
            if (word.length > width) {
                if (current.isNotEmpty()) {
                    result.add(current.toString())
                    current = StringBuilder()
                }
                var remaining = word
                while (remaining.length > width) {
                    result.add(remaining.take(width))
                    remaining = remaining.drop(width)
                }
                if (remaining.isNotEmpty()) current.append(remaining)
            } else if (current.isEmpty()) current.append(word)
            else if (current.length + 1 + word.length <= width) current.append(' ').append(word)
            else {
                result.add(current.toString())
                current = StringBuilder(word)
            }
        }
        if (current.isNotEmpty()) result.add(current.toString())
        return result
    }

    fun money(value: Long): String {
        val digits = value.toString()
        val sign = if (digits.startsWith("-")) "-" else ""
        val grouped = digits.removePrefix("-").reversed().chunked(3).joinToString(".").reversed()
        return sign + grouped
    }

    fun rupiah(value: Long): String = "Rp " + money(value)
}
