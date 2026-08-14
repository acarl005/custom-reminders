package dev.andy.custom_reminders

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant

/**
 * Thin wrapper around the Health Connect client used to power the "skip if
 * already active" feature. Every function fails open (returns a value that
 * results in the reminder still being shown) if Health Connect is missing,
 * unauthorized, or errors out, so a Health Connect problem never silently
 * suppresses a reminder.
 */
object HealthConnectHelper {
    private const val PROVIDER_PACKAGE = "com.google.android.apps.healthdata"
    private val STEPS_READ_PERMISSION = HealthPermission.getReadPermission(StepsRecord::class)

    // Required in addition to STEPS_READ_PERMISSION because our reads happen
    // from a BroadcastReceiver triggered by AlarmManager, i.e. while the app
    // has no foreground activity. Without this, background reads fail with a
    // security error, which previously looked identical to "zero steps".
    private val BACKGROUND_READ_PERMISSION = HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND

    fun isAvailable(context: Context): Boolean {
        return try {
            HealthConnectClient.getSdkStatus(context, PROVIDER_PACKAGE) ==
                HealthConnectClient.SDK_AVAILABLE
        } catch (e: Exception) {
            false
        }
    }

    suspend fun hasStepsPermission(context: Context): Boolean {
        return try {
            val client = HealthConnectClient.getOrCreate(context)
            val granted = client.permissionController.getGrantedPermissions()
            granted.contains(STEPS_READ_PERMISSION) && granted.contains(BACKGROUND_READ_PERMISSION)
        } catch (e: Exception) {
            false
        }
    }

    fun requiredPermissions(): Set<String> = setOf(STEPS_READ_PERMISSION, BACKGROUND_READ_PERMISSION)

    /** Total steps recorded between [sinceMillis] and now. Returns 0 on any failure. */
    suspend fun getStepsSince(context: Context, sinceMillis: Long): Long {
        return try {
            val client = HealthConnectClient.getOrCreate(context)
            val response = client.aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(
                        Instant.ofEpochMilli(sinceMillis),
                        Instant.now(),
                    ),
                ),
            )
            response[StepsRecord.COUNT_TOTAL] ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
}
