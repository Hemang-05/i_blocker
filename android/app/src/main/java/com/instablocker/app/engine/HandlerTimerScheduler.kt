package com.instablocker.app.engine

import android.os.Handler
import android.os.Looper

/**
 * Android Main Looper implementation of [TimerScheduler].
 */
class HandlerTimerScheduler(
    private val handler: Handler = Handler(Looper.getMainLooper())
) : TimerScheduler {

    private var scheduledRunnable: Runnable? = null

    override fun schedule(delayMs: Long, action: Runnable) {
        cancel()
        scheduledRunnable = action
        handler.postDelayed(action, delayMs)
    }

    override fun cancel() {
        scheduledRunnable?.let { handler.removeCallbacks(it) }
        scheduledRunnable = null
    }
}
