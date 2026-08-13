package dev.andy.custom_reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Handles the "Dismiss" notification action by simply cancelling the notification. */
class DismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val hour = intent.getIntExtra(AlarmScheduler.EXTRA_HOUR, -1)
        if (hour == -1) return
        NotificationHelper.cancel(context, hour)
    }
}
