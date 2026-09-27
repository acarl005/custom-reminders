package dev.andy.custom_reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.net.Uri
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
    // Bumped when the channel's sound changes, because a channel's sound is
    // locked in at creation and can't be updated later; this forces a fresh
    // channel with the new sound (previously the long default alarm ringtone,
    // now a short bundled chime).
    private const val CHANNEL_SOUND_ID = "reminders_sound_v2"
    // Bumped each time the vibration pattern changes, because a channel's
    // vibration pattern is locked in at creation and can't be updated later;
    // this forces a fresh channel with the new pattern.
    private const val CHANNEL_SILENT_ID = "reminders_silent_v3"

    // Pulses repeatedly for ~15s total instead of a single default buzz, so a
    // vibration-only reminder is much harder to miss.
    private val SILENT_VIBRATION_PATTERN = buildPulsePattern(
        totalMillis = 15_000,
        onMillis = 500,
        offMillis = 300,
    )

    private fun buildPulsePattern(totalMillis: Long, onMillis: Long, offMillis: Long): LongArray {
        val pattern = mutableListOf(0L) // no initial delay
        var elapsed = 0L
        while (elapsed < totalMillis) {
            pattern.add(onMillis)
            elapsed += onMillis
            if (elapsed >= totalMillis) break
            pattern.add(offMillis)
            elapsed += offMillis
        }
        return pattern.toLongArray()
    }

    /** A short bundled chime (res/raw/squat_chime.mp3), instead of the (often long) system default alarm sound. */
    private fun chimeUri(context: Context): Uri =
        Uri.parse("android.resource://${context.packageName}/${R.raw.squat_chime}")

    private fun ensureChannels(context: Context) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

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
            setSound(chimeUri(context), alarmAttributes)
        }

        val silentChannel = NotificationChannel(
            CHANNEL_SILENT_ID,
            "Hourly reminders (vibrate only)",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Hourly reminder alarms, vibration only"
            enableVibration(true)
            vibrationPattern = SILENT_VIBRATION_PATTERN
            setSound(null, null)
        }

        notificationManager.createNotificationChannel(soundChannel)
        notificationManager.createNotificationChannel(silentChannel)
    }

    /**
     * Shows a reminder asking for [squats] squats. [steps] is the step count
     * the ask was scaled from, or [Prefs.UNKNOWN] when activity wasn't measured.
     */
    fun show(context: Context, hour: Int, squats: Int, steps: Long) {
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

        val squatLabel = if (squats == 1) "1 squat" else "$squats squats"
        val stepLabel = if (steps == 1L) "1 step" else "$steps steps"
        val contentText = if (steps == Prefs.UNKNOWN) {
            "Do $squatLabel"
        } else {
            "Do $squatLabel — $stepLabel since the last reminder"
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Squat time!")
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            // Not sensitive content, so show it fully (and allow dismissing it)
            // on the lock screen even when "show sensitive content" is off.
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
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
}
