package dev.andy.custom_reminders

import android.app.Activity
import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView

/**
 * Health Connect requires apps requesting health permissions to provide a
 * rationale/privacy-explanation screen. If this activity isn't declared,
 * Health Connect's permission request screen silently closes itself instead
 * of showing the grant UI (logged as "App should support rationale intent,
 * finishing!"). This is reached either directly (Android 13 and below, via
 * the ACTION_SHOW_PERMISSIONS_RATIONALE intent) or via the
 * ViewPermissionUsageActivity alias (Android 14+).
 */
class PermissionsRationaleActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val textView = TextView(this).apply {
            text = "Squat Reminders reads your step count from Health Connect " +
                "only to decide whether to skip an hourly reminder when you're " +
                "already active.\n\nStep data is read on-device and is never " +
                "stored, shared, or sent anywhere."
            textSize = 16f
            setPadding(48, 48, 48, 48)
        }
        setContentView(ScrollView(this).apply { addView(textView) })
    }
}
