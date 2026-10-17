package com.telegrambot.manager.data.store

import android.content.Context

/**
 * Tiny persistent flag remembering that the user paused the always-on engine
 * from the notification's «توقف» action. While paused, the boot receiver,
 * the 15-minute watchdog and MainActivity all leave the engine off — the only
 * way back is turning the always-on switch on again inside the app.
 */
object AlwaysOnPrefs {

    private const val FILE = "always_on"
    private const val KEY_PAUSED = "paused"

    fun isPaused(ctx: Context): Boolean =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_PAUSED, false)

    fun setPaused(ctx: Context, paused: Boolean) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_PAUSED, paused).apply()
    }
}
