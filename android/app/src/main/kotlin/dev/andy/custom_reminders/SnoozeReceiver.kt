package dev.andy.custom_reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Handles the "Snooze 5 min" notification action. Only affects the specific
 * reminder instance that was snoozed; the regular daily schedule for that
 * hour (and every other hour) is untouched.
 */
class SnoozeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val hour = intent.getIntExtra(AlarmScheduler.EXTRA_HOUR, -1)
        if (hour == -1) return
        NotificationHelper.cancel(context, hour)
        val triggerAt = AlarmScheduler.scheduleSnooze(context, hour)
        Prefs.setSnoozedUntil(context, triggerAt)
    }
}
