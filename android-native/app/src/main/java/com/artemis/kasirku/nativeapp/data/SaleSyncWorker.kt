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

class SaleSyncWorker(
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
            if (tenant.branchTimeZoneId.isNullOrBlank()) return Result.failure()
            val database = KasirkuRoomDatabase.getInstance(applicationContext)
            val report = SaleSyncRepository(database.catalogDao(), cloud).syncPending(tenant)
            if (report.failed > 0 || report.pending > 0) Result.retry() else Result.success()
        } catch (_: CancellationException) {
            throw
        } catch (_: SecurityException) {
            Result.failure()
        } catch (_: IllegalArgumentException) {
            Result.failure()
        } catch (error: IllegalStateException) {
            if (error.message?.contains("Zona waktu cabang belum diatur") == true) {
                Result.failure()
            } else Result.retry()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val KEY_STORE_ID = "store_id"
        const val KEY_BRANCH_ID = "branch_id"
        private const val UNIQUE_PREFIX = "kasirku-sale-sync"

        fun enqueue(context: Context, tenant: VerifiedTenantContext) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val data = androidx.work.Data.Builder()
                .putString(KEY_STORE_ID, tenant.storeId)
                .putString(KEY_BRANCH_ID, tenant.branchId)
                .build()
            val request = OneTimeWorkRequestBuilder<SaleSyncWorker>()
                .setConstraints(constraints)
                .setInputData(data)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_PREFIX + "-" + tenant.storeId + "-" + tenant.branchId,
                ExistingWorkPolicy.KEEP,
                request
            )
            val periodic = PeriodicWorkRequestBuilder<SaleSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setInputData(data)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_PREFIX + "-periodic-" + tenant.storeId + "-" + tenant.branchId,
                ExistingPeriodicWorkPolicy.KEEP,
                periodic
            )
        }
    }
}
