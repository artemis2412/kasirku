package com.artemis.kasirku.nativeapp.data

import java.time.ZoneId

data class SaleSyncReport(val uploaded: Int, val pending: Int, val failed: Int)

class SaleSyncRepository(
    private val dao: CatalogDao,
    private val cloud: CloudAuthRepository
) {
    suspend fun syncPending(tenant: VerifiedTenantContext): SaleSyncReport {
        val zoneId = tenant.branchTimeZoneId
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { ZoneId.of(it) }.getOrNull() }
            ?: throw IllegalStateException(
                "Zona waktu cabang belum diatur. Transaksi tetap tersimpan di perangkat."
            )
        val queue = dao.getPendingSaleSync(tenant.storeId, tenant.branchId)
        var uploaded = 0
        var failed = 0
        for (entry in queue) {
            if (entry.storeId != tenant.storeId || entry.branchId != tenant.branchId) continue
            try {
                val sale = dao.getSaleForSync(tenant.storeId, tenant.branchId, entry.saleId)
                    ?: throw IllegalStateException("Data transaksi lokal tidak ditemukan.")
                val items = dao.getSaleItems(tenant.storeId, tenant.branchId, entry.saleId)
                val payload = OrderSyncPayloadMapper.map(sale, items, zoneId)
                cloud.uploadOrder(payload)
                val marked = dao.markSaleSynced(
                    tenant.storeId, tenant.branchId, entry.saleId, System.currentTimeMillis()
                )
                check(marked == 1) { "Status antrean transaksi tidak berhasil diperbarui." }
                uploaded++
            } catch (error: Exception) {
                failed++
                dao.updateSaleSyncStatus(
                    storeId = tenant.storeId,
                    branchId = tenant.branchId,
                    saleId = entry.saleId,
                    status = "RETRY",
                    error = (error.message ?: "Sinkronisasi gagal").take(500),
                    updatedAt = System.currentTimeMillis()
                )
            }
        }
        val remaining = dao.getPendingSaleSync(tenant.storeId, tenant.branchId).size
        return SaleSyncReport(uploaded, remaining, failed)
    }
}
