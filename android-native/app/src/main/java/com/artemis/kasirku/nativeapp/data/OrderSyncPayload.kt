package com.artemis.kasirku.nativeapp.data

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire representation for the existing public.orders / public.order_items schema.
 * The caller must provide the branch timezone; silently assuming the device
 * timezone would shift business dates when staff travel or devices differ.
 */
@Serializable
data class OrderSyncDto(
    @SerialName("store_id") val storeId: String,
    @SerialName("branch_id") val branchId: String,
    @SerialName("local_id") val localId: String,
    @SerialName("order_number") val orderNumber: String,
    @SerialName("order_date") val orderDate: String,
    @SerialName("order_time") val orderTime: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("created_by") val createdBy: String,
    @SerialName("order_type") val orderType: String,
    val subtotal: Long,
    @SerialName("discount_amount") val discountAmount: Long,
    val total: Long,
    @SerialName("order_status") val orderStatus: String,
    @SerialName("payment_status") val paymentStatus: String,
    @SerialName("payment_method") val paymentMethod: String,
    @SerialName("paid_amount") val paidAmount: Long,
    @SerialName("change_amount") val changeAmount: Long
)

@Serializable
data class OrderItemSyncDto(
    @SerialName("order_id") val orderId: String,
    @SerialName("product_id") val productId: String?,
    @SerialName("product_local_id") val productLocalId: String,
    @SerialName("product_name") val productName: String,
    @SerialName("unit_price") val unitPrice: Long,
    val quantity: Int,
    @SerialName("line_total") val lineTotal: Long
)

data class OrderSyncPayload(
    val order: OrderSyncDto,
    val items: List<OrderItemSyncDto>
)

object OrderSyncPayloadMapper {
    fun map(
        sale: SaleEntity,
        items: List<SaleItemEntity>,
        branchTimeZone: ZoneId
    ): OrderSyncPayload {
        require(sale.id.isNotBlank() && sale.storeId.isNotBlank() && sale.branchId.isNotBlank()) {
            "Identitas transaksi tidak lengkap."
        }
        require(sale.cashierUserId.isNotBlank()) { "Identitas kasir tidak tersedia." }
        require(items.isNotEmpty() && items.all { it.saleId == sale.id }) {
            "Rincian transaksi tidak cocok."
        }
        val instant = Instant.ofEpochMilli(sale.createdAt)
        val localDateTime = instant.atZone(branchTimeZone)
        val subtotalFromItems = items.fold(0L) { sum, item ->
            require(item.quantity > 0 && item.unitPrice > 0L) { "Rincian transaksi tidak valid." }
            val lineTotal = Math.multiplyExact(item.quantity.toLong(), item.unitPrice)
            require(item.lineTotal == lineTotal) { "Total rincian transaksi tidak sesuai." }
            Math.addExact(sum, lineTotal)
        }
        require(subtotalFromItems == sale.subtotal) { "Subtotal transaksi tidak sesuai rincian." }
        require(sale.discount >= 0L && sale.discount <= sale.subtotal) { "Diskon transaksi tidak valid." }
        require(sale.total == sale.subtotal - sale.discount && sale.total > 0L) { "Total transaksi tidak valid." }
        require(sale.paid >= sale.total && sale.change == sale.paid - sale.total) { "Pembayaran transaksi tidak valid." }
        require(sale.paymentMethod in setOf("CASH", "QRIS", "TRANSFER")) { "Metode pembayaran tidak dikenali." }
        if (sale.paymentMethod != "CASH") {
            require(sale.paid == sale.total && sale.change == 0L) { "Pembayaran non-tunai tidak sesuai total." }
        }

        val order = OrderSyncDto(
            storeId = sale.storeId,
            branchId = sale.branchId,
            localId = sale.id,
            orderNumber = sale.id,
            orderDate = localDateTime.toLocalDate().toString(),
            orderTime = localDateTime.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm:ss")),
            createdAt = DateTimeFormatter.ISO_INSTANT.format(instant),
            createdBy = sale.cashierUserId,
            orderType = "Takeaway",
            subtotal = sale.subtotal,
            discountAmount = sale.discount,
            total = sale.total,
            orderStatus = "Selesai",
            paymentStatus = "Lunas",
            paymentMethod = sale.paymentMethod,
            paidAmount = sale.paid,
            changeAmount = sale.change
        )
        val mappedItems = items.map { item ->
            val productUuid = runCatching { UUID.fromString(item.productId).toString() }.getOrNull()
            OrderItemSyncDto(
                orderId = sale.id,
                productId = productUuid,
                productLocalId = item.productId,
                productName = item.productName,
                unitPrice = item.unitPrice,
                quantity = item.quantity,
                lineTotal = item.lineTotal
            )
        }
        return OrderSyncPayload(order, mappedItems)
    }
}
