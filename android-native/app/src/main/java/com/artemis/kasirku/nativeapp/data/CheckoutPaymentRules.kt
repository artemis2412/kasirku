package com.artemis.kasirku.nativeapp.data

data class ValidatedPayment(
    val method: String,
    val total: Long,
    val change: Long
)

object CheckoutPaymentRules {
    private val supportedMethods = setOf("CASH", "QRIS", "TRANSFER")

    fun validate(subtotal: Long, discount: Long, paid: Long, paymentMethod: String): ValidatedPayment {
        require(subtotal > 0L) { "Subtotal harus lebih dari nol." }
        require(discount >= 0L && discount <= subtotal) { "Diskon tidak valid." }
        require(paymentMethod.isNotBlank()) { "Metode pembayaran wajib ditentukan." }

        val method = paymentMethod.trim().uppercase()
        require(method in supportedMethods) { "Metode pembayaran tidak dikenali." }

        val total = subtotal - discount
        require(total > 0L) { "Total transaksi harus lebih dari nol." }
        require(paid >= total) { "Uang pembayaran kurang." }
        if (method != "CASH") {
            require(paid == total) { "Pembayaran non-tunai harus sesuai total transaksi." }
        }
        return ValidatedPayment(method = method, total = total, change = paid - total)
    }
}
