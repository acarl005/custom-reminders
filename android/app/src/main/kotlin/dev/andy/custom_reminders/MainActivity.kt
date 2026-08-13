package dev.andy.custom_reminders

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    private val channelName = "dev.andy.custom_reminders/native"

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, channelName).setMethodCallHandler { call, result ->
            when (call.method) {
                "getPaused" -> result.success(Prefs.getPaused(this))
                "setPaused" -> {
                    Prefs.setPaused(this, call.argument<Boolean>("value") ?: false)
                    result.success(null)
                }
                "getSoundEnabled" -> result.success(Prefs.getSoundEnabled(this))
                "setSoundEnabled" -> {
                    Prefs.setSoundEnabled(this, call.argument<Boolean>("value") ?: true)
                    result.success(null)
                }
                "scheduleAllAlarms" -> {
                    AlarmScheduler.scheduleAll(this)
                    result.success(null)
                }
                "canScheduleExactAlarms" -> result.success(AlarmScheduler.canScheduleExactAlarms(this))
                "requestScheduleExactAlarmPermission" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val intent = Intent(
                            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:$packageName"),
                        )
                        startActivity(intent)
                    }
                    result.success(null)
                }
                "areNotificationsEnabled" ->
                    result.success(NotificationManagerCompat.from(this).areNotificationsEnabled())
                "requestNotificationPermission" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        ActivityCompat.requestPermissions(
                            this,
                            arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                            1001,
                        )
                    }
                    result.success(null)
                }
                "isDndActive" -> {
                    val notificationManager =
                        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    result.success(
                        notificationManager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL,
                    )
                }
                "getSnoozedUntil" -> result.success(Prefs.getSnoozedUntil(this))
                "isIgnoringBatteryOptimizations" -> {
                    val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                    result.success(powerManager.isIgnoringBatteryOptimizations(packageName))
                }
                "requestIgnoreBatteryOptimizations" -> {
                    val intent = Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:$packageName"),
                    )
                    startActivity(intent)
                    result.success(null)
                }
                else -> result.notImplemented()
            }
        }
    }
}
