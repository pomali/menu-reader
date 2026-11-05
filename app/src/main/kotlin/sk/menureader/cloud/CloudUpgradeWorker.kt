package sk.menureader.cloud

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import sk.menureader.MenuReaderApp

/**
 * Re-reads a menu with the chosen cloud model once the phone is online (PLAN §3.1: WorkManager
 * with a CONNECTED constraint). The FULL result replaces the basic one; the basic one is kept.
 * No automatic retry: any error is shown at once and the user retries from the menu screen.
 */
class CloudUpgradeWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_ID) ?: return Result.failure()
        val app = applicationContext as MenuReaderApp
        return try {
            app.repository.upgradeWithAi(id, app.cloud)
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "AI reading failed", e)
            Result.failure(workDataOf(KEY_ERROR to (e.message ?: e.javaClass.simpleName)))
        }
    }

    companion object {
        const val KEY_ID = "id"
        const val KEY_ERROR = "error"
        private const val TAG = "CloudUpgrade"
        fun workName(id: String) = "ai-$id"

        fun enqueue(ctx: Context, id: String) {
            val req = OneTimeWorkRequestBuilder<CloudUpgradeWorker>()
                .setInputData(workDataOf(KEY_ID to id))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(ctx).enqueueUniqueWork(workName(id), ExistingWorkPolicy.REPLACE, req)
        }
    }
}
