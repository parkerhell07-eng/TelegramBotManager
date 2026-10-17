package com.telegrambot.manager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.telegrambot.manager.data.store.AlwaysOnPrefs

/**
 * Handles the «توقف» action button on the engine notification. Stopping the
 * service alone would be undone by the watchdog/boot receiver within minutes,
 * so a persistent user-pause flag is stored too; any always-on switch inside
 * the app clears it again.
 */
class EngineActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_STOP) {
            AlwaysOnPrefs.setPaused(context, true)
            EngineControl.stop(context)
        }
    }

    companion object {
        const val ACTION_STOP = "com.telegrambot.manager.action.STOP_ENGINE"
    }
}
