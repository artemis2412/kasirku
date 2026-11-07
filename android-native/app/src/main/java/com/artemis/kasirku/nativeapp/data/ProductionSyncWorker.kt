package com.artemis.kasirku.nativeapp.data

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

data class ProductionSyncReport(val uploaded: Int, val pending: Int, val failed: Int)

class ProductionSyncRepository(
    private val dao: ProductionDao,
    private val cloud: CloudAuthRepository
) {
    suspend fun syncPending(tenant: VerifiedTenantContext): ProductionSyncReport {
        val queue = dao.getPendingProductionSync(tenant.storeId, tenant.branchId, tenant.userId)
        var uploaded = 0
        var failed = 0
        for (entry in queue) {
            try {
                val batch = dao.getProductionBatch(tenant.storeId, tenant.branchId, entry.batchId)
                    ?: throw IllegalStateException("Data batch produksi lokal tidak ditemukan.")
                check(batch.producedBy == tenant.userId && entry.producedBy == tenant.userId) {
                    "Batch produksi hanya dapat disinkronkan oleh akun pembuatnya."
                }
                cloud.uploadProductionBatch(tenant, batch)
                check(dao.markProductionSynced(
                    tenant.storeId, tenant.branchId, entry.batchId, System.currentTimeMillis()
                ) == 1) { "Status antrean produksi tidak berhasil diperbarui." }
                uploaded++
            } catch (error: CancellationException) {
                throw error
            } catch (error: SecurityException) {
                throw error
            } catch (error: Exception) {
                failed++
                dao.updateProductionSyncStatus(
                    storeId = tenant.storeId,
                    branchId = tenant.branchId,
                    batchId = entry.batchId,
                    status = "RETRY",
                    error = (error.message ?: "Sinkronisasi produksi gagal").take(500),
                    updatedAt = System.currentTimeMillis()
                )
            }
        }
        val remaining = dao.getPendingProductionSync(tenant.storeId, tenant.branchId, tenant.userId).size
        return ProductionSyncReport(uploaded, remaining, failed)
    }
}

class ProductionSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val storeId = inputData.getString(KEY_STORE_ID) ?: return Result.failure()
        val branchId = inputData.getString(KEY_BRANCH_ID) ?: return Result.failure()
        return try {
            val cloud = CloudAuthRepository()
            val access = cloud.restoreSession() ?: return Result.failure()
            val tenant = access.verifiedContext(storeId, branchId)
            val report = ProductionSyncRepository(
                KasirkuRoomDatabase.getInstance(applicationContext).productionDao(),
                cloud
            ).syncPending(tenant)
            if (report.failed > 0 || report.pending > 0) Result.retry() else Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (_: SecurityException) {
            Result.failure()
        } catch (_: IllegalArgumentException) {
            Result.failure()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val KEY_STORE_ID = "store_id"
        const val KEY_BRANCH_ID = "branch_id"
        private const val UNIQUE_PREFIX = "kasirku-production-sync"

        fun enqueue(context: Context, tenant: VerifiedTenantContext) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val data = androidx.work.Data.Builder()
                .putString(KEY_STORE_ID, tenant.storeId)
                .putString(KEY_BRANCH_ID, tenant.branchId)
                .build()
            val request = OneTimeWorkRequestBuilder<ProductionSyncWorker>()
                .setConstraints(constraints)
                .setInputData(data)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "$UNIQUE_PREFIX-${tenant.storeId}-${tenant.branchId}",
                ExistingWorkPolicy.KEEP,
                request
            )
            val periodic = PeriodicWorkRequestBuilder<ProductionSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setInputData(data)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "$UNIQUE_PREFIX-periodic-${tenant.storeId}-${tenant.branchId}",
                ExistingPeriodicWorkPolicy.KEEP,
                periodic
            )
        }
    }
}
