package dev.andy.custom_reminders

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Fires when a scheduled reminder alarm goes off (either a regular hourly
 * slot or a snoozed one-off). Decides whether to actually show a notification
 * based on the paused toggle and the current Do Not Disturb state, then (for
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

        NotificationHelper.show(context, hour)
    }
}
