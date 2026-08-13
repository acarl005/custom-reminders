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
import androidx.health.connect.client.PermissionController
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class MainActivity : FlutterActivity() {
    private val channelName = "dev.andy.custom_reminders/native"
    private val healthPermissionContract = PermissionController.createRequestPermissionResultContract()

    // FlutterActivity extends the plain framework Activity (not ComponentActivity), so the
    // modern registerForActivityResult() API isn't available; use the classic
    // startActivityForResult()/onActivityResult() pair instead, driving it via the same
    // ActivityResultContract that Health Connect provides.
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // No result parsing needed: the Dart side re-checks permission status on resume.
    }

    @OptIn(DelicateCoroutinesApi::class)
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
                "getSkipIfActiveEnabled" -> result.success(Prefs.getSkipIfActiveEnabled(this))
                "setSkipIfActiveEnabled" -> {
                    Prefs.setSkipIfActiveEnabled(this, call.argument<Boolean>("value") ?: true)
                    result.success(null)
                }
                "wasLastReminderSkippedForActivity" ->
                    result.success(Prefs.getLastSkippedForActivity(this))
                "isHealthConnectAvailable" -> result.success(HealthConnectHelper.isAvailable(this))
                "hasStepsPermission" -> {
                    GlobalScope.launch(Dispatchers.Main) {
                        result.success(HealthConnectHelper.hasStepsPermission(this@MainActivity))
                    }
                }
                "requestStepsPermission" -> {
                    val intent = healthPermissionContract.createIntent(
                        this,
                        HealthConnectHelper.requiredPermissions(),
                    )
                    startActivityForResult(intent, HEALTH_PERMISSION_REQUEST_CODE)
                    result.success(null)
                }
                else -> result.notImplemented()
            }
        }
    }

    companion object {
        private const val HEALTH_PERMISSION_REQUEST_CODE = 9001
    }
}
