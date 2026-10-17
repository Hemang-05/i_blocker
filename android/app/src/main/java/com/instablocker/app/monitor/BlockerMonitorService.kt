package com.instablocker.app.monitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import com.instablocker.app.MainActivity
import com.instablocker.app.R
import com.instablocker.app.engine.BlockReason
import com.instablocker.app.engine.EnforcementActionListener
import com.instablocker.app.engine.EnforcementEngine
import com.instablocker.app.engine.HandlerTimerScheduler
import com.instablocker.app.overlay.OverlayController
import com.instablocker.app.storage.SharedPrefsEnforcementStorage
import com.instablocker.app.util.DebugLogger

/**
 * Foreground service that detects the foreground app via [UsageStatsManager] polling
 * and drives the [EnforcementEngine] + [OverlayController].
 *
 * Replaces the old AccessibilityService. UPI / banking apps (Paytm, PhonePe, GPay)
 * refuse payments while a third-party accessibility service is enabled
 * ("background app detected"); Usage Access is not treated as a risk by them.
 *
 * Threading: UsageStats queries run on a dedicated background thread. All engine and
 * overlay calls are posted to the main thread, which keeps the engine single-threaded.
 */
class BlockerMonitorService : Service() {

    companion object {
        private const val CHANNEL_ID = "instablocker_guard"
        private const val NOTIFICATION_ID = 4201
        private const val POLL_INTERVAL_MS = 500L
        private const val INITIAL_LOOKBACK_MS = 60 * 60 * 1000L // 1h to find the current app
        private const val QUERY_OVERLAP_MS = 3_000L // tolerate slightly late event delivery

        @Volatile
        var instance: BlockerMonitorService? = null
            private set

        fun isRunning(): Boolean = instance != null

        fun isInstagramSessionActive(): Boolean {
            val service = instance ?: return false
            if (!service.engineReady) return false
            val engine = service.enforcementEngine
            return engine.isTargetForeground() && engine.getActiveSessionStartTime() != null
        }

        /**
         * Runs [action] against the live engine on the main thread (no-op if the service is not running).
         */
        fun withEngine(action: (EnforcementEngine) -> Unit) {
            val service = instance ?: return
            service.mainHandler.post {
                if (service.engineReady) action(service.enforcementEngine)
            }
        }

        /**
         * Starts the monitor if both required permissions are granted.
         * Must be called while the app is in the foreground (or from BOOT_COMPLETED).
         */
        fun start(context: Context): Boolean {
            if (!PermissionUtils.hasAllRequired(context)) {
                DebugLogger.d("[InstaBlocker][MONITOR] start skipped: missing usage access or overlay permission")
                return false
            }
            if (isRunning()) return true
            val intent = Intent(context, BlockerMonitorService::class.java)
            return try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                true
            } catch (t: Throwable) {
                DebugLogger.e("[InstaBlocker][MONITOR] Failed to start monitor service: ${t.message}", t)
                false
            }
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var pollThread: HandlerThread
    private lateinit var pollHandler: Handler
    private lateinit var usageStatsManager: UsageStatsManager

    private lateinit var overlayController: OverlayController
    internal lateinit var enforcementEngine: EnforcementEngine

    @Volatile
    private var engineReady = false

    @Volatile
    private var isPolling = false

    // Accessed only on the poll thread
    private var lastQueryEnd = 0L
    private var lastProcessedEventTs = 0L
    private var lastForegroundPkg: String? = null

    private var lastHomeNavigationTime = 0L

    private val pollRunnable = object : Runnable {
        override fun run() {
            if (!isPolling) return
            try {
                pollForegroundApp()
            } catch (t: Throwable) {
                DebugLogger.e("[InstaBlocker][MONITOR] Poll failed: ${t.message}", t)
            }
            if (isPolling) pollHandler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> stopPolling()
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> startPolling()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        instance = this

        usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        pollThread = HandlerThread("InstaBlockerMonitor").apply { start() }
        pollHandler = Handler(pollThread.looper)

        setupEngine()

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenReceiver, filter)
        }

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (pm.isInteractive) startPolling()

        DebugLogger.d("[InstaBlocker][MONITOR] BlockerMonitorService created.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForeground must be called on every start command to satisfy the 5s FGS contract.
        startAsForeground()
        return START_STICKY
    }

    override fun onDestroy() {
        stopPolling()
        try {
            unregisterReceiver(screenReceiver)
        } catch (_: Exception) {
        }
        if (::pollThread.isInitialized) pollThread.quitSafely()
        if (engineReady) enforcementEngine.destroy()
        if (::overlayController.isInitialized) overlayController.hide()
        engineReady = false
        if (instance == this) instance = null
        DebugLogger.d("[InstaBlocker][MONITOR] BlockerMonitorService destroyed.")
        super.onDestroy()
    }

    private fun setupEngine() {
        overlayController = OverlayController(this).apply {
            onCooldownExpiredListener = {
                if (engineReady) enforcementEngine.onCooldownExpired()
            }
        }

        val listener = object : EnforcementActionListener {
            override fun onBlockStateChanged(isBlocked: Boolean, reason: BlockReason, remainingCooldownMs: Long) {
                DebugLogger.d("[InstaBlocker][MONITOR] ENFORCEMENT_STATE_CHANGED isBlocked=$isBlocked reason=$reason remainingCooldownMs=$remainingCooldownMs")
                try {
                    if (isBlocked) {
                        overlayController.show(reason, remainingCooldownMs)
                        navigateToHome()
                    } else {
                        overlayController.hide()
                    }
                } catch (t: Throwable) {
                    DebugLogger.e("[InstaBlocker][MONITOR] Exception in onBlockStateChanged: ${t.message}", t)
                }
            }
        }

        enforcementEngine = EnforcementEngine(
            storage = SharedPrefsEnforcementStorage(applicationContext),
            timerScheduler = HandlerTimerScheduler(),
            actionListener = listener
        )
        engineReady = true
    }

    private fun startPolling() {
        if (isPolling || !::pollHandler.isInitialized) return
        isPolling = true
        pollHandler.removeCallbacks(pollRunnable)
        pollHandler.post(pollRunnable)
        DebugLogger.d("[InstaBlocker][MONITOR] Polling started.")
    }

    private fun stopPolling() {
        isPolling = false
        if (::pollHandler.isInitialized) pollHandler.removeCallbacks(pollRunnable)
        DebugLogger.d("[InstaBlocker][MONITOR] Polling stopped.")
    }

    /**
     * Reads new usage events and reports the most recently resumed package to the engine
     * whenever it changes. Runs on the poll thread.
     */
    private fun pollForegroundApp() {
        val now = System.currentTimeMillis()
        val start = if (lastQueryEnd == 0L) now - INITIAL_LOOKBACK_MS else lastQueryEnd - QUERY_OVERLAP_MS
        lastQueryEnd = now

        val events = usageStatsManager.queryEvents(start, now) ?: return
        val event = UsageEvents.Event()
        var latestPkg: String? = null
        var latestTs = lastProcessedEventTs

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            @Suppress("DEPRECATION")
            val isResume = event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND // == ACTIVITY_RESUMED
            if (isResume && event.timeStamp > lastProcessedEventTs && event.timeStamp >= latestTs) {
                latestPkg = event.packageName
                latestTs = event.timeStamp
            }
        }

        if (latestPkg == null) return
        lastProcessedEventTs = latestTs

        if (latestPkg != lastForegroundPkg) {
            lastForegroundPkg = latestPkg
            val pkg: String = latestPkg
            DebugLogger.d("[InstaBlocker][MONITOR] FOREGROUND_DETECTED package=$pkg")
            mainHandler.post {
                if (engineReady) enforcementEngine.onForegroundAppChanged(pkg)
            }
        }
    }

    /**
     * Sends the user to the launcher so Instagram leaves the foreground and stops media playback.
     * Allowed from the background because the app holds SYSTEM_ALERT_WINDOW.
     */
    private fun navigateToHome() {
        val now = System.currentTimeMillis()
        if (now - lastHomeNavigationTime < 1000L) return
        lastHomeNavigationTime = now
        try {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(homeIntent)
        } catch (t: Throwable) {
            DebugLogger.e("[InstaBlocker][MONITOR] navigateToHome failed: ${t.message}", t)
        }
    }

    private fun startAsForeground() {
        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (t: Throwable) {
            DebugLogger.e("[InstaBlocker][MONITOR] startForeground failed: ${t.message}", t)
            stopSelf()
        }
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Focus protection", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Keeps InstaBlocker active so your Instagram limits are enforced."
                    setShowBadge(false)
                }
            )
        }

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("InstaBlocker is protecting your focus")
            .setContentText("Instagram limits are active.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
    }
}
