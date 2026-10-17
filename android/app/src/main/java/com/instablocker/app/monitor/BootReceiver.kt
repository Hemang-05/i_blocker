package com.instablocker.app.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.instablocker.app.util.DebugLogger

/**
 * Restarts the monitor after a reboot or app update so protection survives without
 * the user having to open the app.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                DebugLogger.d("[InstaBlocker][BOOT] ${intent.action} received, starting monitor.")
                BlockerMonitorService.start(context.applicationContext)
            }
        }
    }
}
