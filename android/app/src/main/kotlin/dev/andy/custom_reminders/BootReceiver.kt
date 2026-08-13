package dev.andy.custom_reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Re-schedules all reminder alarms after a device reboot (AlarmManager alarms don't persist). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            AlarmScheduler.scheduleAll(context)
        }
    }
}
