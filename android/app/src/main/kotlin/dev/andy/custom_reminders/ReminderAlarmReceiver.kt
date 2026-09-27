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
 * slot or a snoozed one-off). Decides whether to show a notification (and how
 * many squats it should ask for) based on the paused toggle, the current Do
 * Not Disturb state, and (optionally) recent step activity, then (for
 * regular, non-snoozed alarms) reschedules the same slot for 24 hours later
 * to keep the daily cycle going indefinitely.
 */
class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val hour = intent.getIntExtra(AlarmScheduler.EXTRA_HOUR, -1)
        val isSnooze = intent.getBooleanExtra(AlarmScheduler.EXTRA_IS_SNOOZE, false)
        if (hour == -1) return

        // Keep the daily cycle alive regardless of pause/DND state so that
        // toggling "paused" off later doesn't require re-scheduling anything.
        if (!isSnooze) {
            // The window may have shrunk since this alarm was scheduled, in
            // which case let the slot die out instead of renewing it.
            if (!AlarmScheduler.isHourInWindow(context, hour)) return
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

        // A snooze is an explicit request to be reminded again shortly, so it
        // re-asks for whatever the reminder being snoozed asked for rather
        // than re-measuring activity (which would let the walk to the kitchen
        // during the snooze erode the set the user already agreed to).
        if (isSnooze) {
            val squats = Prefs.getLastReminderSquats(context).takeIf { it > 0 } ?: Squats.MAX
            NotificationHelper.show(context, hour, squats, Prefs.getLastReminderStepCount(context))
            return
        }

        if (!Prefs.getScaleWithActivityEnabled(context)) {
            Prefs.setLastReminder(context, Squats.MAX, Prefs.UNKNOWN)
            NotificationHelper.show(context, hour, Squats.MAX, Prefs.UNKNOWN)
            return
        }

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val steps = recentSteps(appContext)
                val squats = if (steps == Prefs.UNKNOWN) Squats.MAX else Squats.forSteps(steps)
                Prefs.setLastReminder(appContext, squats, steps)
                // Already walked off the whole set, so stay quiet entirely.
                if (squats > 0) {
                    NotificationHelper.show(appContext, hour, squats, steps)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    /**
     * Steps taken since the last activity check, or [Prefs.UNKNOWN] when step
     * data isn't readable. Fails open (i.e. the caller asks for a full set of
     * squats) on any error, timeout, or missing setup.
     */
    private suspend fun recentSteps(context: Context): Long {
        val since = Prefs.activityWindowStartMillis(context)
        Prefs.setLastActivityCheckMillis(context, System.currentTimeMillis())

        if (!HealthConnectHelper.isAvailable(context)) return Prefs.UNKNOWN
        val hasPermission = withTimeoutOrNull(TIMEOUT_MILLIS) {
            HealthConnectHelper.hasStepsPermission(context)
        } ?: false
        if (!hasPermission) return Prefs.UNKNOWN

        return withTimeoutOrNull(TIMEOUT_MILLIS) {
            HealthConnectHelper.getStepsSince(context, since)
        } ?: Prefs.UNKNOWN
    }

    companion object {
        private const val TIMEOUT_MILLIS = 5000L
    }
}
