package com.telegrambot.manager

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.telegrambot.manager.data.store.AlwaysOnPrefs
import com.telegrambot.manager.data.store.AutoReplyStore
import java.util.concurrent.TimeUnit

/**
 * 15-minute belt-and-suspenders watchdog. OEM task killers (MIUI, Samsung,
 * Huawei…) and user force-stops can kill even a START_STICKY foreground
 * service; this periodic job relaunches the answer engine whenever it should
 * be running but is not. Background foreground-service starts are rejected on
 * some Android combinations — [EngineControl.start] swallows that and the
 * next boot / app open retries instead.
 */
class KeepAliveWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val shouldRun = !AlwaysOnPrefs.isPaused(ctx) &&
                AutoReplyStore.get(ctx).enabledBotIds().isNotEmpty()
        if (shouldRun && !EngineControl.running.value) {
            EngineControl.start(ctx)
        }
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "always_on_keepalive"

        /** Idempotent; safe to call from MainActivity, BootReceiver, anywhere. */
        fun schedule(ctx: Context) {
            val request = PeriodicWorkRequestBuilder<KeepAliveWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(ctx.applicationContext).enqueueUniquePeriodicWork(
                UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }
    }
}
