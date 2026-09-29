package com.instablocker.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.instablocker.app.MainApplication
import com.instablocker.app.engine.BlockReason
import com.instablocker.app.engine.EnforcementActionListener
import com.instablocker.app.engine.EnforcementEngine
import com.instablocker.app.engine.HandlerTimerScheduler
import com.instablocker.app.overlay.OverlayController
import com.instablocker.app.storage.SharedPrefsEnforcementStorage
import com.instablocker.app.util.DebugLogger

/**
 * Android AccessibilityService responsible for foreground application detection.
 * Delegates all business logic to [EnforcementEngine] and blocking window presentation to [OverlayController].
 */
class BlockerAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: BlockerAccessibilityService? = null
            private set

        fun isConnected(): Boolean = instance != null

        fun isInstagramSessionActive(): Boolean {
            val engine = instance?.enforcementEngine ?: return false
            return engine.isTargetForeground() && engine.getActiveSessionStartTime() != null
        }
    }

    private lateinit var overlayController: OverlayController
    internal lateinit var enforcementEngine: EnforcementEngine

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this

        overlayController = OverlayController(this).apply {
            onCooldownExpiredListener = {
                if (::enforcementEngine.isInitialized) {
                    enforcementEngine.onCooldownExpired()
                }
            }
        }

        val storage = SharedPrefsEnforcementStorage(applicationContext)
        val timerScheduler = HandlerTimerScheduler()

        val listener = object : EnforcementActionListener {
            override fun onBlockStateChanged(
                isBlocked: Boolean,
                reason: BlockReason,
                remainingCooldownMs: Long
            ) {
                DebugLogger.d("[InstaBlocker][SERVICE] ENFORCEMENT_STATE_CHANGED isBlocked=$isBlocked reason=$reason remainingCooldownMs=$remainingCooldownMs")
                DebugLogger.d("[InstaBlocker][SERVICE] SERVICE_CONNECTED=${isConnected()}")
                DebugLogger.d("[InstaBlocker][SERVICE] SERVICE_INSTANCE=$instance")
                try {
                    if (isBlocked) {
                        DebugLogger.d("[InstaBlocker][SERVICE] Invoking overlayController.show")
                        overlayController.show(reason, remainingCooldownMs)
                        DebugLogger.d("[InstaBlocker][SERVICE] Invoking navigateToHome")
                        navigateToHome()
                    } else {
                        DebugLogger.d("[InstaBlocker][SERVICE] Invoking overlayController.hide")
                        overlayController.hide()
                    }
                } catch (t: Throwable) {
                    DebugLogger.e("[InstaBlocker][SERVICE] Exception in onBlockStateChanged: ${t.message}", t)
                }
            }
        }

        enforcementEngine = EnforcementEngine(
            storage = storage,
            timerScheduler = timerScheduler,
            actionListener = listener
        )

        // Ensure service is configured to receive window state events
        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        serviceInfo = info
        DebugLogger.d("[InstaBlocker][SERVICE] BlockerAccessibilityService connected and configured.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val evt = event ?: return
        if (evt.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return
        }

        val pkgName = evt.packageName?.toString() ?: return
        val className = evt.className?.toString() ?: ""

        DebugLogger.d("[InstaBlocker][SERVICE] A11Y_EVENT_DETECTED package=$pkgName class=$className")

        // Ignore transient system packages (status bar, volume slider, navigation gestures)
        if (pkgName == "com.android.systemui" || pkgName == "android") {
            DebugLogger.d("[A11Y] Ignoring system UI event from $pkgName")
            return
        }

        // Filter out our own overlay events (only treat our app as foreground if it's the actual MainActivity)
        if (pkgName == applicationContext.packageName) {
            val isMainActivity = className == "com.instablocker.app.MainActivity" ||
                    (application as? MainApplication)?.isMainActivityForeground == true
            if (!isMainActivity) {
                DebugLogger.d("[A11Y] Ignoring internal overlay event from $pkgName (class=$className)")
                return
            }
        }

        if (::enforcementEngine.isInitialized) {
            enforcementEngine.onForegroundAppChanged(pkgName)
        }
    }

    private var lastHomeNavigationTime: Long = 0L

    /**
     * Navigates the device out of the target application to the Home screen,
     * ensuring Instagram leaves the foreground and stops media/audio playback.
     */
    fun navigateToHome() {
        DebugLogger.d("[InstaBlocker][NAV] NAVIGATION_REQUESTED")
        DebugLogger.d("[InstaBlocker][SERVICE] SERVICE_CONNECTED=${isConnected()}")
        DebugLogger.d("[InstaBlocker][SERVICE] SERVICE_INSTANCE=$instance")
        val now = System.currentTimeMillis()
        if (now - lastHomeNavigationTime < 1000L) {
            DebugLogger.d("[InstaBlocker][NAV] navigateToHome throttled (${now - lastHomeNavigationTime}ms since last call).")
            return
        }
        lastHomeNavigationTime = now
        DebugLogger.d("[InstaBlocker][NAV] CALLING_PERFORM_GLOBAL_ACTION action=GLOBAL_ACTION_HOME")
        try {
            val success = performGlobalAction(GLOBAL_ACTION_HOME)
            DebugLogger.d("[InstaBlocker][NAV] PERFORM_GLOBAL_ACTION_RESULT=$success")
            if (!success) {
                DebugLogger.w("[InstaBlocker][NAV] performGlobalAction(GLOBAL_ACTION_HOME) returned false; using Intent fallback.")
                val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(homeIntent)
            }
        } catch (t: Throwable) {
            DebugLogger.e("[InstaBlocker][NAV] Exception in navigateToHome: ${t.message}", t)
        }
    }

    override fun onInterrupt() {
        // Accessibility service interrupted by system
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
        if (::enforcementEngine.isInitialized) {
            enforcementEngine.destroy()
        }
        if (::overlayController.isInitialized) {
            overlayController.hide()
        }
    }
}
