package com.instablocker.app.monitor

import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings

/**
 * Helpers for the two special permissions required by the UsageStats-based blocker:
 *  - Usage Access (PACKAGE_USAGE_STATS) to detect the foreground app.
 *  - Display over other apps (SYSTEM_ALERT_WINDOW) to show the blocking overlay.
 *
 * Neither of these is an Accessibility Service, so UPI / banking apps
 * (Paytm, PhonePe, GPay) do not flag InstaBlocker as a screen-reading risk.
 */
object PermissionUtils {

    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return if (mode == AppOpsManager.MODE_DEFAULT) {
            context.checkCallingOrSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            mode == AppOpsManager.MODE_ALLOWED
        }
    }

    fun canDrawOverlays(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
    }

    fun hasAllRequired(context: Context): Boolean =
        hasUsageAccess(context) && canDrawOverlays(context)
}
