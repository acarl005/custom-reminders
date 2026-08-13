package dev.andy.custom_reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Builds and displays the hourly reminder notification.
 *
 * Two notification channels are used (sound vs. silent/vibrate-only) because
 * on Android 8+ the sound associated with a notification is fixed by its
 * channel at creation time and can't be changed per-notification. Which
 * channel is used is decided at show()-time based on the user's toggle.
 */
object NotificationHelper {
    private const val CHANNEL_SOUND_ID = "reminders_sound"
    private const val CHANNEL_SILENT_ID = "reminders_silent"

    private fun ensureChannels(context: Context) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val alarmSoundUri = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val alarmAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val soundChannel = NotificationChannel(
            CHANNEL_SOUND_ID,
            "Hourly reminders (sound)",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Hourly reminder alarms with sound"
            enableVibration(true)
            setSound(alarmSoundUri, alarmAttributes)
        }

        val silentChannel = NotificationChannel(
            CHANNEL_SILENT_ID,
            "Hourly reminders (vibrate only)",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Hourly reminder alarms, vibration only"
            enableVibration(true)
            setSound(null, null)
        }

        notificationManager.createNotificationChannel(soundChannel)
        notificationManager.createNotificationChannel(silentChannel)
    }

    fun show(context: Context, hour: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        ensureChannels(context)
        val channelId = if (Prefs.getSoundEnabled(context)) CHANNEL_SOUND_ID else CHANNEL_SILENT_ID

        val snoozeIntent = Intent(context, SnoozeReceiver::class.java)
            .putExtra(AlarmScheduler.EXTRA_HOUR, hour)
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            2000 + hour,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val dismissIntent = Intent(context, DismissReceiver::class.java)
            .putExtra(AlarmScheduler.EXTRA_HOUR, hour)
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            3000 + hour,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            4000 + hour,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Reminder")
            .setContentText("It's ${formatHour(hour)} — time for your reminder")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .setDeleteIntent(dismissPendingIntent)
            .addAction(0, "Snooze 5 min", snoozePendingIntent)
            .addAction(0, "Dismiss", dismissPendingIntent)

        NotificationManagerCompat.from(context).notify(hour, builder.build())
    }

    fun cancel(context: Context, hour: Int) {
        NotificationManagerCompat.from(context).cancel(hour)
    }

    private fun formatHour(hour: Int): String {
        val h12 = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        val amPm = if (hour < 12) "AM" else "PM"
        return "$h12:55 $amPm"
    }
}
