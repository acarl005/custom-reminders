package dev.andy.custom_reminders

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.health.connect.client.PermissionController
import io.flutter.embedding.android.FlutterFragmentActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

// Health Connect's permission request relies on the AndroidX Activity Result API
// (registerForActivityResult), which requires a ComponentActivity. Flutter's default
// FlutterActivity extends the plain framework Activity and doesn't support this, so
// FlutterFragmentActivity (which extends ComponentActivity via FragmentActivity) is used instead.
class MainActivity : FlutterFragmentActivity() {
    private val channelName = "dev.andy.custom_reminders/native"
    private lateinit var healthPermissionLauncher: ActivityResultLauncher<Set<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Must be registered before the activity reaches STARTED, so this has to
        // happen in onCreate rather than lazily inside the MethodChannel handler.
        healthPermissionLauncher = registerForActivityResult(
            PermissionController.createRequestPermissionResultContract(),
        ) {
            // No-op: the Dart side re-checks permission status when the app resumes.
        }
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
                "getLastSkippedStepCount" -> result.success(Prefs.getLastSkippedStepCount(this))
                "isHealthConnectAvailable" -> result.success(HealthConnectHelper.isAvailable(this))
                "hasStepsPermission" -> {
                    GlobalScope.launch(Dispatchers.Main) {
                        result.success(HealthConnectHelper.hasStepsPermission(this@MainActivity))
                    }
                }
                "requestStepsPermission" -> {
                    healthPermissionLauncher.launch(HealthConnectHelper.requiredPermissions())
                    result.success(null)
                }
                "getCurrentIntervalSteps" -> {
                    GlobalScope.launch(Dispatchers.Main) {
                        val since = Prefs.activityWindowStartMillis(this@MainActivity)
                        result.success(HealthConnectHelper.getStepsSince(this@MainActivity, since))
                    }
                }
                "getActivityStepThreshold" ->
                    result.success(ReminderAlarmReceiver.ACTIVITY_STEP_THRESHOLD)
                else -> result.notImplemented()
            }
        }
    }
}
