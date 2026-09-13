package com.example.data.worker

import android.content.Context
import android.util.Log
import androidx.work.*
import com.example.data.repository.RetirementRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * 거시경제 지표(ECOS/KRX) 백그라운드 주기적 동기화 및 캐싱 워커
 * 배터리 최적화를 위해 배터리가 부족하지 않고 네트워크가 연결된 상태에서만 작동합니다.
 */
class EconomicSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d("EconomicSyncWorker", "Starting background economic indicators sync...")
            val repository = RetirementRepository(applicationContext)
            // Force-refresh or update cache
            repository.refreshEconomicIndicators()
            Log.d("EconomicSyncWorker", "Background economic indicators sync completed successfully.")
            Result.success()
        } catch (e: Exception) {
            Log.e("EconomicSyncWorker", "Economic indicators sync failed: ${e.message}", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val WORK_NAME = "economic_indicators_sync_work"

        fun schedulePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val periodicRequest = PeriodicWorkRequestBuilder<EconomicSyncWorker>(
                12, TimeUnit.HOURS,
                30, TimeUnit.MINUTES // Flex interval
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicRequest
            )
        }
    }
}

/**
 * 오프라인 상태에서 누적된 지출 구멍(Financial Leaks) 및 프로필 정보를
 * 백그라운드에서 Firestore와 자동 동기화하는 안정화 워커
 */
class UserDataSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val username = inputData.getString(KEY_USERNAME) ?: return@withContext Result.failure()
        return@withContext try {
            Log.d("UserDataSyncWorker", "Starting background data sync for user: $username (attempt $runAttemptCount)")
            val repository = RetirementRepository(applicationContext)
            val success = repository.flushSyncQueue(username)
            if (success) {
                Log.d("UserDataSyncWorker", "User data background sync completed successfully for user: $username.")
                Result.success()
            } else {
                Log.w("UserDataSyncWorker", "Pending items remain in sync queue for $username. Scheduling WorkManager exponential backoff retry.")
                if (runAttemptCount < 5) Result.retry() else Result.failure()
            }
        } catch (e: Exception) {
            Log.e("UserDataSyncWorker", "UserDataSyncWorker exception: ${e.message}", e)
            if (runAttemptCount < 5) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val KEY_USERNAME = "key_username"
        private const val ONE_TIME_TAG = "user_data_sync_tag"

        fun scheduleOneTime(context: Context, username: String) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val inputData = Data.Builder()
                .putString(KEY_USERNAME, username)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<UserDataSyncWorker>()
                .setConstraints(constraints)
                .setInputData(inputData)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                .addTag(ONE_TIME_TAG)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "sync_user_$username",
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }
    }
}
