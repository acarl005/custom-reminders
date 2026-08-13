package dev.andy.custom_reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.AlarmManagerCompat
import java.util.Calendar

/**
 * Schedules the hourly reminder alarms (10:55 through 22:55, daily) and
 * one-off snooze alarms using the platform AlarmManager. Alarms are exact and
 * fire even while the device is idle/Doze, and each slot reschedules itself
 * for the next day when it fires so the cycle continues indefinitely without
 * needing the app to be open.
 */
object AlarmScheduler {
    const val EXTRA_HOUR = "hour"
    const val EXTRA_IS_SNOOZE = "is_snooze"

    const val START_HOUR = 10
    const val END_HOUR = 22
    const val MINUTE = 55

    private const val SNOOZE_REQUEST_CODE_OFFSET = 500

    fun scheduleAll(context: Context) {
        for (hour in START_HOUR..END_HOUR) {
            scheduleSlot(context, hour)
        }
    }

    /** Schedules (or re-schedules) the next occurrence of the given hour's daily reminder. */
    fun scheduleSlot(context: Context, hour: Int) {
        val trigger = nextOccurrenceMillis(hour)
        scheduleExact(context, trigger, hour, isSnooze = false, requestCode = hour)
    }

    /** Schedules a one-off reminder 5 minutes from now for the given hour slot (snooze). Returns the trigger time in epoch millis. */
    fun scheduleSnooze(context: Context, hour: Int): Long {
        val trigger = System.currentTimeMillis() + 5 * 60 * 1000L
        scheduleExact(
            context,
            trigger,
            hour,
            isSnooze = true,
            requestCode = SNOOZE_REQUEST_CODE_OFFSET + hour,
        )
        return trigger
    }

    private fun nextOccurrenceMillis(hour: Int): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, MINUTE)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    private fun scheduleExact(
        context: Context,
        triggerAtMillis: Long,
        hour: Int,
        isSnooze: Boolean,
        requestCode: Int,
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            putExtra(EXTRA_HOUR, hour)
            putExtra(EXTRA_IS_SNOOZE, isSnooze)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        try {
            AlarmManagerCompat.setExactAndAllowWhileIdle(
                alarmManager,
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent,
            )
        } catch (e: SecurityException) {
            // Missing SCHEDULE_EXACT_ALARM grant (Android 12+); fall back to inexact.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return alarmManager.canScheduleExactAlarms()
    }
}
