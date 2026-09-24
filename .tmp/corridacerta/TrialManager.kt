package com.rafael.radarcorrida

import android.content.Context
import kotlin.math.ceil

object TrialManager {
    private const val PREFS = "corrida_certa_trial"
    private const val KEY_START = "trial_start_ms"
    private const val TRIAL_DAYS = 90L
    private const val DAY_MS = 24L * 60L * 60L * 1000L
    private const val TRIAL_MS = TRIAL_DAYS * DAY_MS

    fun ensureStarted(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getLong(KEY_START, 0L)
        if (existing > 0L) return existing

        val now = System.currentTimeMillis()
        prefs.edit().putLong(KEY_START, now).apply()
        return now
    }

    fun millisRemaining(context: Context): Long {
        val start = ensureStarted(context)
        val elapsed = (System.currentTimeMillis() - start).coerceAtLeast(0L)
        return (TRIAL_MS - elapsed).coerceAtLeast(0L)
    }

    fun daysRemaining(context: Context): Int {
        val remaining = millisRemaining(context)
        if (remaining <= 0L) return 0
        return ceil(remaining.toDouble() / DAY_MS.toDouble()).toInt()
    }

    fun isActive(context: Context): Boolean = millisRemaining(context) > 0L
}
