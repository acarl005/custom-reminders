package dev.andy.custom_reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.AlarmManagerCompat
import java.util.Calendar

/**
 * Schedules the hourly reminder alarms (every :55 within the user's
 * configured start/end hour window, daily) and one-off snooze alarms using
 * the platform AlarmManager. Alarms are exact and fire even while the device
 * is idle/Doze, and each slot reschedules itself for the next day when it
 * fires so the cycle continues indefinitely without needing the app to be
 * open.
 */
object AlarmScheduler {
    const val EXTRA_HOUR = "hour"
    const val EXTRA_IS_SNOOZE = "is_snooze"

    const val DEFAULT_START_HOUR = 9
    const val DEFAULT_END_HOUR = 21
    const val MINUTE = 55

    private const val SNOOZE_REQUEST_CODE_OFFSET = 500

    /** Whether the given hour's :55 slot is inside the user's configured window. */
    fun isHourInWindow(context: Context, hour: Int): Boolean =
        hour in Prefs.getStartHour(context)..Prefs.getEndHour(context)

    /**
     * Schedules every in-window slot and cancels any out-of-window ones, so
     * this can be called after the window changes to reconcile the whole day.
     */
    fun scheduleAll(context: Context) {
        for (hour in 0..23) {
            if (isHourInWindow(context, hour)) {
                scheduleSlot(context, hour)
            } else {
                cancelSlot(context, hour)
            }
        }
    }

    /** Schedules (or re-schedules) the next occurrence of the given hour's daily reminder. */
    fun scheduleSlot(context: Context, hour: Int) {
        val trigger = nextOccurrenceMillis(hour)
        scheduleExact(context, trigger, hour, isSnooze = false, requestCode = hour)
    }

    /** Cancels the daily reminder for the given hour, if one is scheduled. */
    fun cancelSlot(context: Context, hour: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            hour,
            Intent(context, ReminderAlarmReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
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
