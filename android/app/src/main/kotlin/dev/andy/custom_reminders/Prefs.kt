package dev.andy.custom_reminders

import android.content.Context

/**
 * Simple wrapper around a dedicated SharedPreferences file used to store the
 * user's toggle states. This is intentionally separate from anything a Flutter
 * plugin might use, since this file must be readable/writable from both the
 * Flutter UI (via MethodChannel) and from broadcast receivers that run without
 * any Dart engine at all.
 */
object Prefs {
    private const val FILE = "reminder_prefs"
    private const val KEY_PAUSED = "paused"
    private const val KEY_SOUND_ENABLED = "sound_enabled"
    private const val KEY_SNOOZED_UNTIL = "snoozed_until"

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun getPaused(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PAUSED, false)

    fun setPaused(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_PAUSED, value).apply()
    }

    fun getSoundEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SOUND_ENABLED, true)

    fun setSoundEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_SOUND_ENABLED, value).apply()
    }

    /** Epoch millis at which the currently-snoozed reminder will fire again, or 0 if none. */
    fun getSnoozedUntil(context: Context): Long =
        prefs(context).getLong(KEY_SNOOZED_UNTIL, 0L)

    fun setSnoozedUntil(context: Context, millis: Long) {
        prefs(context).edit().putLong(KEY_SNOOZED_UNTIL, millis).apply()
    }

    fun clearSnoozedUntil(context: Context) {
        prefs(context).edit().remove(KEY_SNOOZED_UNTIL).apply()
    }
}
