package com.artemis.kasirku.nativeapp.data

import java.util.UUID

data class CheckoutLine(
    val productId: String,
    val productName: String,
    val quantity: Int,
    val unitPrice: Long
) {
    val lineTotal: Long get() = Math.multiplyExact(quantity.toLong(), unitPrice)
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
        discount: Long = 0L,
        orderType: String = "Takeaway",
        tableLocalId: String? = null,
        tableLabel: String? = null
    ): CheckoutResult {
        require(orderType in setOf("Takeaway", "Dine In")) { "Jenis pesanan tidak valid." }
        require(if (orderType == "Dine In") !tableLocalId.isNullOrBlank() && !tableLabel.isNullOrBlank() else tableLocalId == null && tableLabel == null) { "Pilih meja untuk pesanan Dine In." }
        require(lines.isNotEmpty()) { "Keranjang masih kosong." }
        require(lines.all { it.productId.isNotBlank() && it.productName.isNotBlank() && it.quantity > 0 && it.unitPrice > 0L }) {
            "Produk, jumlah, atau harga pada keranjang tidak valid."
        }
        val subtotal = lines.fold(0L) { total, line -> Math.addExact(total, line.lineTotal) }
        val payment = CheckoutPaymentRules.validate(subtotal, discount, paid, paymentMethod)
        val total = payment.total
        val normalizedMethod = payment.method
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
            paymentMethod = normalizedMethod,
            createdAt = now,
            orderType = orderType,
            tableLocalId = tableLocalId,
            tableLabel = tableLabel
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

    suspend fun saleItems(tenant: VerifiedTenantContext, saleId: String): List<SaleItemEntity> {
        require(saleId.isNotBlank()) { "Transaksi tidak valid." }
        return dao.getSaleItems(tenant.storeId, tenant.branchId, saleId)
    }

    suspend fun exportRows(tenant: VerifiedTenantContext, startAt: Long, endAt: Long): List<Pair<SaleEntity, List<SaleItemEntity>>> {
        val sales = dao.getSalesRange(tenant.storeId, tenant.branchId, startAt, endAt)
        return sales.map { sale -> sale to dao.getSaleItems(tenant.storeId, tenant.branchId, sale.id) }
    }
}
