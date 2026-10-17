package com.telegrambot.manager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.telegrambot.manager.data.store.AlwaysOnPrefs
import com.telegrambot.manager.data.store.AutoReplyStore

/**
 * Brings the 24/7 answer engine back to life after a phone reboot or an app
 * update — two moments when every process is guaranteed dead. Receivers of
 * protected system broadcasts may start foreground services; a user pause
 * from the notification is always respected.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                // Re-arm the watchdog first: WorkManager re-persists its jobs
                // on boot by itself, but scheduling is idempotent (KEEP) and
                // covers OEMs that drop the database on force-stop.
                KeepAliveWorker.schedule(context)
                if (!AlwaysOnPrefs.isPaused(context) &&
                    AutoReplyStore.get(context).enabledBotIds().isNotEmpty()
                ) {
                    EngineControl.start(context)
                }
            }
        }
    }
}
