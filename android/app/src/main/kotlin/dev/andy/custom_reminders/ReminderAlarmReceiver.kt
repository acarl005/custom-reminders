package dev.andy.custom_reminders

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Fires when a scheduled reminder alarm goes off (either a regular hourly
 * slot or a snoozed one-off). Decides whether to actually show a notification
 * based on the paused toggle, the current Do Not Disturb state, and
 * (optionally) recent step activity, then (for regular, non-snoozed alarms)
 * reschedules the same slot for 24 hours later to keep the daily cycle going
 * indefinitely.
 */
class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val hour = intent.getIntExtra(AlarmScheduler.EXTRA_HOUR, -1)
        val isSnooze = intent.getBooleanExtra(AlarmScheduler.EXTRA_IS_SNOOZE, false)
        if (hour == -1) return

        // Keep the daily cycle alive regardless of pause/DND state so that
        // toggling "paused" off later doesn't require re-scheduling anything.
        if (!isSnooze) {
            AlarmScheduler.scheduleSlot(context, hour)
        } else {
            // The snooze period has elapsed, whether or not we end up showing
            // a notification below.
            Prefs.clearSnoozedUntil(context)
        }

        if (Prefs.getPaused(context)) return

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (notificationManager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL) {
            // Device is in Do Not Disturb mode; skip this reminder entirely.
            return
        }

        // Only the regular hourly cycle can be skipped for activity; a snooze
        // is an explicit request to be reminded again shortly, so always show it.
        if (!isSnooze && Prefs.getSkipIfActiveEnabled(context)) {
            val pendingResult = goAsync()
            val appContext = context.applicationContext
            GlobalScope.launch(Dispatchers.IO) {
                try {
                    val shouldSkip = shouldSkipForActivity(appContext)
                    if (!shouldSkip) {
                        NotificationHelper.show(appContext, hour)
                    }
                } finally {
                    pendingResult.finish()
                }
            }
            return
        }

        Prefs.setLastSkippedForActivity(context, false)
        NotificationHelper.show(context, hour)
    }

    /** Fails open (returns false, i.e. "don't skip") on any error, timeout, or missing setup. */
    private suspend fun shouldSkipForActivity(context: Context): Boolean {
        val since = Prefs.activityWindowStartMillis(context)
        Prefs.setLastActivityCheckMillis(context, System.currentTimeMillis())

        if (!HealthConnectHelper.isAvailable(context)) {
            Prefs.setLastSkippedForActivity(context, false)
            return false
        }
        val hasPermission = withTimeoutOrNull(TIMEOUT_MILLIS) {
            HealthConnectHelper.hasStepsPermission(context)
        } ?: false
        if (!hasPermission) {
            Prefs.setLastSkippedForActivity(context, false)
            return false
        }

        val steps = withTimeoutOrNull(TIMEOUT_MILLIS) {
            HealthConnectHelper.getStepsSince(context, since)
        } ?: 0L

        val shouldSkip = steps >= ACTIVITY_STEP_THRESHOLD
        Prefs.setLastSkippedForActivity(context, shouldSkip)
        return shouldSkip
    }

    companion object {
        const val ACTIVITY_STEP_THRESHOLD = 200L
        private const val TIMEOUT_MILLIS = 5000L
    }
}
