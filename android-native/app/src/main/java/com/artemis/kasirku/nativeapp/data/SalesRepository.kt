package com.artemis.kasirku.nativeapp.data

import java.util.UUID

data class CheckoutLine(
    val productId: String,
    val productName: String,
    val quantity: Int,
    val unitPrice: Long
) {
    val lineTotal: Long get() = quantity.toLong() * unitPrice
}

data class CheckoutResult(
    val saleId: String,
    val total: Long,
    val change: Long
)

class SalesRepository(private val dao: CatalogDao) {
    suspend fun checkout(
        tenant: VerifiedTenantContext,
        lines: List<CheckoutLine>,
        paid: Long,
        paymentMethod: String = "CASH",
        discount: Long = 0L
    ): CheckoutResult {
        require(lines.isNotEmpty()) { "Keranjang masih kosong." }
        require(lines.all { it.quantity > 0 && it.unitPrice > 0L }) { "Isi keranjang tidak valid." }
        require(discount >= 0L) { "Diskon tidak valid." }
        val subtotal = lines.sumOf { it.lineTotal }
        require(discount <= subtotal) { "Diskon melebihi subtotal." }
        val total = subtotal - discount
        require(total > 0L) { "Total transaksi harus lebih dari nol." }
        require(paid >= total) { "Uang pembayaran kurang." }
        val saleId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val sale = SaleEntity(
            id = saleId,
            storeId = tenant.storeId,
            branchId = tenant.branchId,
            cashierUserId = tenant.userId,
            subtotal = subtotal,
            discount = discount,
            total = total,
            paid = paid,
            change = paid - total,
            paymentMethod = paymentMethod,
            createdAt = now
        )
        val items = lines.map { line ->
            SaleItemEntity(
                id = UUID.randomUUID().toString(),
                saleId = saleId,
                productId = line.productId,
                productName = line.productName,
                quantity = line.quantity,
                unitPrice = line.unitPrice,
                lineTotal = line.lineTotal
            )
        }
        dao.completeSale(sale, items)
        return CheckoutResult(saleId, total, paid - total)
    }

    fun observeSales(tenant: VerifiedTenantContext, startAt: Long, endAt: Long) =
        dao.observeSales(tenant.storeId, tenant.branchId, startAt, endAt)

    fun observeSaleCount(tenant: VerifiedTenantContext, startAt: Long, endAt: Long) =
        dao.observeSaleCount(tenant.storeId, tenant.branchId, startAt, endAt)

    fun observeSalesTotal(tenant: VerifiedTenantContext, startAt: Long, endAt: Long) =
        dao.observeSalesTotal(tenant.storeId, tenant.branchId, startAt, endAt)

    suspend fun saleItems(saleId: String): List<SaleItemEntity> {
        require(saleId.isNotBlank()) { "Transaksi tidak valid." }
        return dao.getSaleItems(saleId)
    }
}
