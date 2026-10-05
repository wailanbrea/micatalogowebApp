package com.example.bspos.data.micatalogo

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.bspos.domain.model.MiCatalogoResult
import com.example.bspos.domain.repository.MiCatalogoConnectionRepository
import com.example.bspos.domain.repository.MiCatalogoPosSaleRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PosSaleSyncScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    fun enqueue() {
        val request = OneTimeWorkRequestBuilder<PosSaleSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    private companion object {
        const val WORK_NAME = "micatalogo-pos-sale-sync"
    }
}

class PosSaleSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val dependencies = EntryPointAccessors.fromApplication(
            applicationContext,
            PosSaleSyncWorkerDependencies::class.java
        )
        if (dependencies.connectionRepository().accessToken() == null) return Result.success()

        return when (val result = dependencies.posSaleRepository().syncDueSales()) {
            is MiCatalogoResult.Success -> {
                val paymentsPending = dependencies.paymentSyncRepository().sync()
                val catalogPending = dependencies.catalogRefreshRepository().sync()
                if (result.value.retried > 0 || paymentsPending || catalogPending) Result.retry() else Result.success()
            }
            is MiCatalogoResult.Failure -> Result.retry()
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PosSaleSyncWorkerDependencies {
    fun connectionRepository(): MiCatalogoConnectionRepository
    fun posSaleRepository(): MiCatalogoPosSaleRepository
    fun paymentSyncRepository(): PaymentSyncRepository
    fun catalogRefreshRepository(): CatalogRefreshSyncRepository
}
