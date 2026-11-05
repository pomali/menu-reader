package sk.menureader.cloud

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import sk.menureader.MenuReaderApp
import java.util.concurrent.TimeUnit

/**
 * Re-reads a menu with the chosen cloud model once the phone is online (PLAN §3.1: WorkManager
 * with a CONNECTED constraint). The FULL result replaces the basic one; the basic one is kept.
 */
class CloudUpgradeWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_ID) ?: return Result.failure()
        val app = applicationContext as MenuReaderApp
        return try {
            app.repository.upgradeWithAi(id, app.cloud)
            Result.success()
        } catch (e: CloudException) {
            if (e.retryable && runAttemptCount < 3) Result.retry()
            else Result.failure(workDataOf(KEY_ERROR to e.message))
        } catch (e: Exception) {
            Result.failure(workDataOf(KEY_ERROR to (e.message ?: e.javaClass.simpleName)))
        }
    }

    companion object {
        const val KEY_ID = "id"
        const val KEY_ERROR = "error"
        fun workName(id: String) = "ai-$id"

        fun enqueue(ctx: Context, id: String) {
            val req = OneTimeWorkRequestBuilder<CloudUpgradeWorker>()
                .setInputData(workDataOf(KEY_ID to id))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(ctx).enqueueUniqueWork(workName(id), ExistingWorkPolicy.REPLACE, req)
        }
    }
}
