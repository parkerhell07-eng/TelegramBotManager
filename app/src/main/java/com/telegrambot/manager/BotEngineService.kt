package com.telegrambot.manager

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.telegrambot.manager.data.engine.BotEngine
import com.telegrambot.manager.data.store.AutoReplyStore
import com.telegrambot.manager.data.util.L10n
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps [BotEngine] listening around the clock.
 * START_STICKY so Android revives it after a process kill; a persistent
 * low-priority notification informs the user that their bots are live
 * (required by the platform for foreground services).
 */
class BotEngineService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var engineJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()
        EngineControl.running.value = true
        if (engineJob == null) {
            engineJob = BotEngine.run(applicationContext)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        engineJob?.cancel()
        engineJob = null
        scope.cancel()
        BotEngine.shutdown()
        EngineControl.running.value = false
        super.onDestroy()
    }

    private fun startForegroundCompat() {
        val notif = buildNotification()
        when {
            Build.VERSION.SDK_INT >= 34 ->
                ServiceCompat.startForeground(
                    this, NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            Build.VERSION.SDK_INT >= 29 ->
                ServiceCompat.startForeground(
                    this, NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            else -> startForeground(NOTIF_ID, notif)
        }
    }

    private fun buildNotification(): Notification {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getBroadcast(
            this, 1,
            Intent(this, EngineActionReceiver::class.java).setAction(EngineActionReceiver.ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val count = try {
            AutoReplyStore.get(this).enabledBotIds().size
        } catch (e: Exception) {
            0
        }
        val body = if (count > 1)
            L10n.str(
                "$count ربات ۲۴ ساعته به دستورها جواب می‌دهند.",
                "$count bots answer commands around the clock."
            )
        else
            L10n.str(
                "ربات‌های شما ۲۴ ساعته به دستورات پاسخ می‌دهند.",
                "Your bots answer commands around the clock."
            )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(L10n.str("ربات همیشه فعال است", "Always-on bot is running"))
            .setContentText(body)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(tapIntent)
            .addAction(0, L10n.str("توقف", "Stop"), stopIntent)
            .build()
    }

    private fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val ch = NotificationChannel(
                CHANNEL_ID,
                L10n.str("موتور پاسخ‌گویی ربات", "Bot answer engine"),
                NotificationManager.IMPORTANCE_LOW
            )
            ch.description = L10n.str(
                "اعلان دائمی فعال بودن ربات‌ها",
                "Persistent notification while bots are listening"
            )
            nm.createNotificationChannel(ch)
        }
    }

    // ── Global control surface ───────────────────────────────────────────

    companion object {
        private const val CHANNEL_ID = "bot_engine"
        private const val NOTIF_ID = 4711

        fun start(ctx: Context) = EngineControl.start(ctx)

        fun stop(ctx: Context) = EngineControl.stop(ctx)

        fun restart(ctx: Context) = EngineControl.restart(ctx)
    }
}

/** Start/stop/restart the engine service from anywhere (UI, MainActivity). */
object EngineControl {

    val running = kotlinx.coroutines.flow.MutableStateFlow(false)

    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun start(ctx: Context) {
        if (running.value) return
        val intent = Intent(ctx.applicationContext, BotEngineService::class.java)
        try {
            androidx.core.content.ContextCompat.startForegroundService(ctx.applicationContext, intent)
        } catch (e: Exception) {
            // Background FGS starts can be rejected on some OEM/Android
            // combos; the 15-minute watchdog or next app open retries.
        }
    }

    fun stop(ctx: Context) {
        val app = ctx.applicationContext
        app.stopService(Intent(app, BotEngineService::class.java))
        running.value = false
    }

    /**
     * Applies config changes: destroys the old loops (their long-poll calls
     * get cancelled) and rebuilds from the freshly saved configuration.
     */
    fun restart(ctx: Context) {
        val app = ctx.applicationContext
        uiScope.launch {
            stop(app)
            delay(500)
            start(app)
        }
    }
}
