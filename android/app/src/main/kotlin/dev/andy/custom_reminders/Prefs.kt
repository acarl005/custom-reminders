package dev.andy.custom_reminders

import android.content.Context
import java.util.Calendar

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
    private const val KEY_SKIP_IF_ACTIVE = "skip_if_active"
    private const val KEY_LAST_ACTIVITY_CHECK_MILLIS = "last_activity_check_millis"
    private const val KEY_LAST_SKIPPED_FOR_ACTIVITY = "last_skipped_for_activity"
    private const val KEY_LAST_SKIPPED_STEP_COUNT = "last_skipped_step_count"

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

    fun getSkipIfActiveEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SKIP_IF_ACTIVE, true)

    fun setSkipIfActiveEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_SKIP_IF_ACTIVE, value).apply()
    }

    /** Epoch millis marking the start of the window to check steps over next time. */
    fun getLastActivityCheckMillis(context: Context): Long =
        prefs(context).getLong(KEY_LAST_ACTIVITY_CHECK_MILLIS, 0L)

    fun setLastActivityCheckMillis(context: Context, millis: Long) {
        prefs(context).edit().putLong(KEY_LAST_ACTIVITY_CHECK_MILLIS, millis).apply()
    }

    /** Whether the most recent hourly reminder was skipped because the user was already active. */
    fun getLastSkippedForActivity(context: Context): Boolean =
        prefs(context).getBoolean(KEY_LAST_SKIPPED_FOR_ACTIVITY, false)

    fun setLastSkippedForActivity(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_LAST_SKIPPED_FOR_ACTIVITY, value).apply()
    }

    /** Step count recorded during the most recent activity check (whether or not it caused a skip). */
    fun getLastSkippedStepCount(context: Context): Long =
        prefs(context).getLong(KEY_LAST_SKIPPED_STEP_COUNT, 0L)

    fun setLastSkippedStepCount(context: Context, value: Long) {
        prefs(context).edit().putLong(KEY_LAST_SKIPPED_STEP_COUNT, value).apply()
    }

    /**
     * Start of the window to query step counts over: since the last activity
     * check, falling back to the most recent hourly slot boundary (e.g.
     * 1:55, 2:55, ...) when there's no prior check (e.g. right after
     * install) or the last check is stale by more than an hour (e.g.
     * reminders were paused or silenced by DND for a long stretch).
     *
     * The fallback is anchored to a fixed point in time rather than a
     * rolling "now minus one hour" window, so the reported step count for
     * the current interval doesn't drift downward over time as steps taken
     * earlier in the interval roll out of a sliding window.
     */
    fun activityWindowStartMillis(context: Context): Long {
        val now = System.currentTimeMillis()
        val lastCheck = getLastActivityCheckMillis(context)
        val maxLookbackMillis = 60 * 60 * 1000L
        return if (lastCheck <= 0 || now - lastCheck > maxLookbackMillis) {
            mostRecentSlotBoundaryMillis()
        } else {
            lastCheck
        }
    }

    /**
     * Epoch millis of the most recent *past* occurrence of the hourly :55
     * slot boundary, strictly before now.
     *
     * Uses <= (not <) so that a call made right at/just after a :55 slot
     * boundary rolls back a full hour instead of collapsing to essentially
     * "now". That matters because the real background activity check
     * (ReminderAlarmReceiver) runs exactly at these boundaries: if this
     * fallback ever fires there (e.g. due to AlarmManager/Doze jitter
     * nudging the gap between checks slightly past an hour), it must still
     * resolve to the *previous* slot so the lookback window stays close to
     * a full hour rather than shrinking to a few seconds — which would make
     * getStepsSince() see ~0 steps and "skip if already active" never skip.
     */
    private fun mostRecentSlotBoundaryMillis(): Long {
        val cal = Calendar.getInstance()
        if (cal.get(Calendar.MINUTE) <= AlarmScheduler.MINUTE) {
            cal.add(Calendar.HOUR_OF_DAY, -1)
        }
        cal.set(Calendar.MINUTE, AlarmScheduler.MINUTE)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
