package com.instablocker.app.bridge

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableMap
import com.instablocker.app.accessibility.BlockerAccessibilityService
import com.instablocker.app.storage.EnforcementStorage
import com.instablocker.app.storage.SharedPrefsEnforcementStorage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * React Native Bridge Module exposing Kotlin native enforcement state and rules configuration.
 *
 * Kotlin remains the source of truth for all enforcement state and logic.
 */
class InstaBlockerModule(private val reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    private val storage: EnforcementStorage by lazy {
        SharedPrefsEnforcementStorage(reactContext.applicationContext)
    }

    override fun getName(): String = "InstaBlocker"

    /**
     * Reads current enforcement status (cooldown remaining, session count, daily lock).
     */
    @ReactMethod
    fun getStatus(promise: Promise) {
        try {
            ensureDayRollover()

            val now = System.currentTimeMillis()
            val sessionsCompleted = storage.getSessionsCompletedToday()
            val maxSessions = storage.getMaxSessionsPerDay()
            val isDailyLocked = storage.isDailyLocked() || sessionsCompleted >= maxSessions
            val cooldownUntil = storage.getCooldownUntil()
            val cooldownRemainingMs = if (now < cooldownUntil) cooldownUntil - now else 0L
            val isInstagramSessionActive = BlockerAccessibilityService.isInstagramSessionActive()

            val stateString = when {
                isDailyLocked -> "DAILY_LOCKED"
                cooldownRemainingMs > 0 -> "COOLDOWN"
                isInstagramSessionActive -> "IN_SESSION"
                else -> "ALLOWED"
            }

            val map = Arguments.createMap().apply {
                putInt("sessionsCompletedToday", sessionsCompleted)
                putInt("maxSessionsPerDay", maxSessions)
                putString("state", stateString)
                putDouble("cooldownRemainingMs", cooldownRemainingMs.toDouble())
                putBoolean("dailyLocked", isDailyLocked)
                putBoolean("isInstagramSessionActive", isInstagramSessionActive)
            }
            promise.resolve(map)
        } catch (e: Exception) {
            promise.reject("GET_STATUS_FAILED", e.message, e)
        }
    }

    /**
     * Reads configured rules from native storage.
     */
    @ReactMethod
    fun getRules(promise: Promise) {
        try {
            val map = Arguments.createMap().apply {
                putDouble("usageLimitMs", storage.getContinuousLimitMs().toDouble())
                putDouble("cooldownDurationMs", storage.getCooldownDurationMs().toDouble())
                putInt("maxSessionsPerDay", storage.getMaxSessionsPerDay())
                putBoolean("lockUntilMidnight", storage.isLockUntilMidnightEnabled())
                putBoolean("cooldownNotifications", storage.isCooldownNotificationsEnabled())
            }
            promise.resolve(map)
        } catch (e: Exception) {
            promise.reject("GET_RULES_FAILED", e.message, e)
        }
    }

    /**
     * Updates rules in native storage with strict validation.
     */
    @ReactMethod
    fun setRules(rules: ReadableMap, promise: Promise) {
        try {
            if (!rules.hasKey("usageLimitMs") || !rules.hasKey("cooldownDurationMs") || !rules.hasKey("maxSessionsPerDay")) {
                promise.reject("INVALID_ARGUMENTS", "Missing required rule parameters.")
                return
            }

            val usageLimitMs = rules.getDouble("usageLimitMs").toLong()
            val cooldownDurationMs = rules.getDouble("cooldownDurationMs").toLong()
            val maxSessionsPerDay = rules.getInt("maxSessionsPerDay")

            if (usageLimitMs <= 0) {
                promise.reject("INVALID_USAGE_LIMIT", "Usage limit must be greater than zero.")
                return
            }
            if (cooldownDurationMs <= 0) {
                promise.reject("INVALID_COOLDOWN_DURATION", "Cooldown duration must be greater than zero.")
                return
            }
            if (maxSessionsPerDay <= 0) {
                promise.reject("INVALID_MAX_SESSIONS", "Max sessions per day must be greater than zero.")
                return
            }

            storage.setContinuousLimitMs(usageLimitMs)
            storage.setCooldownDurationMs(cooldownDurationMs)
            storage.setMaxSessionsPerDay(maxSessionsPerDay)

            if (rules.hasKey("lockUntilMidnight")) {
                storage.setLockUntilMidnightEnabled(rules.getBoolean("lockUntilMidnight"))
            }
            if (rules.hasKey("cooldownNotifications")) {
                storage.setCooldownNotificationsEnabled(rules.getBoolean("cooldownNotifications"))
            }

            // Immediately notify active EnforcementEngine so running timers & checks adapt instantaneously
            BlockerAccessibilityService.instance?.enforcementEngine?.onRulesChanged()
            com.instablocker.app.util.DebugLogger.d("Rules updated and active engine notified: limit=${usageLimitMs}ms, cooldown=${cooldownDurationMs}ms, maxSessions=${maxSessionsPerDay}")

            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("SET_RULES_FAILED", e.message, e)
        }
    }

    /**
     * Development/Debug-only preset:
     * usage limit = 30 seconds
     * cooldown = 60 seconds
     * max sessions = 2
     */
    @ReactMethod
    fun applyDebugPreset(promise: Promise) {
        if (!com.instablocker.app.BuildConfig.DEBUG) {
            promise.reject("NOT_SUPPORTED", "Debug presets are only available in debug builds.")
            return
        }

        try {
            storage.setContinuousLimitMs(30_000L) // 30 seconds
            storage.setCooldownDurationMs(60_000L) // 60 seconds
            storage.setMaxSessionsPerDay(2) // 2 sessions
            storage.setLockUntilMidnightEnabled(true)
            storage.setCooldownNotificationsEnabled(false)

            BlockerAccessibilityService.instance?.enforcementEngine?.onRulesChanged()
            com.instablocker.app.util.DebugLogger.d("Applied DEBUG preset: limit=30s, cooldown=60s, maxSessions=2")

            val map = Arguments.createMap().apply {
                putDouble("usageLimitMs", 30_000.0)
                putDouble("cooldownDurationMs", 60_000.0)
                putInt("maxSessionsPerDay", 2)
                putBoolean("lockUntilMidnight", true)
                putBoolean("cooldownNotifications", false)
            }
            promise.resolve(map)
        } catch (e: Exception) {
            promise.reject("APPLY_DEBUG_PRESET_FAILED", e.message, e)
        }
    }

    /**
     * Resets rules back to default production values:
     * usage limit = 10 minutes
     * cooldown = 90 minutes
     * max sessions = 4
     */
    @ReactMethod
    fun resetToDefaultRules(promise: Promise) {
        try {
            storage.setContinuousLimitMs(EnforcementStorage.DEFAULT_CONTINUOUS_LIMIT_MS)
            storage.setCooldownDurationMs(EnforcementStorage.DEFAULT_COOLDOWN_DURATION_MS)
            storage.setMaxSessionsPerDay(EnforcementStorage.DEFAULT_MAX_SESSIONS_PER_DAY)
            storage.setLockUntilMidnightEnabled(EnforcementStorage.DEFAULT_LOCK_UNTIL_MIDNIGHT)
            storage.setCooldownNotificationsEnabled(EnforcementStorage.DEFAULT_COOLDOWN_NOTIFICATIONS)

            BlockerAccessibilityService.instance?.enforcementEngine?.onRulesChanged()
            com.instablocker.app.util.DebugLogger.d("Reset rules to default production values (10m / 90m / 4 sessions)")

            val map = Arguments.createMap().apply {
                putDouble("usageLimitMs", EnforcementStorage.DEFAULT_CONTINUOUS_LIMIT_MS.toDouble())
                putDouble("cooldownDurationMs", EnforcementStorage.DEFAULT_COOLDOWN_DURATION_MS.toDouble())
                putInt("maxSessionsPerDay", EnforcementStorage.DEFAULT_MAX_SESSIONS_PER_DAY)
                putBoolean("lockUntilMidnight", EnforcementStorage.DEFAULT_LOCK_UNTIL_MIDNIGHT)
                putBoolean("cooldownNotifications", EnforcementStorage.DEFAULT_COOLDOWN_NOTIFICATIONS)
            }
            promise.resolve(map)
        } catch (e: Exception) {
            promise.reject("RESET_DEFAULT_FAILED", e.message, e)
        }
    }

    /**
     * Resets today's enforcement progress (sessions today = 0, cooldown = 0, daily lock = false)
     * and dismisses any active blocker/timer safely.
     * Only available in debug builds.
     */
    @ReactMethod
    fun resetEnforcementState(promise: Promise) {
        if (!com.instablocker.app.BuildConfig.DEBUG) {
            promise.reject("NOT_SUPPORTED", "Resetting enforcement state is only available in debug builds.")
            return
        }

        try {
            storage.setSessionsCompletedToday(0)
            storage.setCooldownUntil(0L)
            storage.setDailyLocked(false)

            val service = BlockerAccessibilityService.instance
            if (service != null && service.enforcementEngine != null) {
                service.enforcementEngine.resetEnforcementState()
            }

            com.instablocker.app.util.DebugLogger.d("[DEBUG] Enforcement state reset: 0 sessions, cooldown=0, dailyLocked=false")

            val maxSessions = storage.getMaxSessionsPerDay()
            val map = Arguments.createMap().apply {
                putInt("sessionsCompletedToday", 0)
                putInt("maxSessionsPerDay", maxSessions)
                putString("state", "ALLOWED")
                putDouble("cooldownRemainingMs", 0.0)
                putBoolean("dailyLocked", false)
                putBoolean("isInstagramSessionActive", false)
            }
            promise.resolve(map)
        } catch (e: Exception) {
            promise.reject("RESET_ENFORCEMENT_STATE_FAILED", e.message, e)
        }
    }

    /**
     * Checks if the Accessibility Service is enabled in system settings.
     */
    @ReactMethod
    fun isAccessibilityServiceEnabled(promise: Promise) {
        try {
            val isEnabled = isServiceEnabled(reactContext, BlockerAccessibilityService::class.java)
            promise.resolve(isEnabled)
        } catch (e: Exception) {
            promise.reject("ACCESSIBILITY_CHECK_FAILED", e.message, e)
        }
    }

    /**
     * Launches Android Accessibility Settings.
     */
    @ReactMethod
    fun openAccessibilitySettings(promise: Promise) {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            reactContext.startActivity(intent)
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("OPEN_SETTINGS_FAILED", e.message, e)
        }
    }

    /**
     * Returns comprehensive service status for onboarding.
     */
    @ReactMethod
    fun getServiceStatus(promise: Promise) {
        try {
            val isEnabled = isServiceEnabled(reactContext, BlockerAccessibilityService::class.java)
            val isConnected = BlockerAccessibilityService.isConnected()
            val map = Arguments.createMap().apply {
                putBoolean("isAccessibilityServiceEnabled", isEnabled)
                putBoolean("isServiceConnected", isConnected)
                putBoolean("isReady", isEnabled && isConnected)
            }
            promise.resolve(map)
        } catch (e: Exception) {
            promise.reject("SERVICE_STATUS_FAILED", e.message, e)
        }
    }

    /**
     * Synchronizes active daily to-do tasks from React Native to native storage.
     */
    @ReactMethod
    fun syncDailyTasks(tasksJson: String, promise: Promise) {
        try {
            storage.setDailyTasksJson(tasksJson)
            com.instablocker.app.util.DebugLogger.d("InstaBlockerModule: Daily tasks synced successfully.")
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("SYNC_TASKS_FAILED", e.message, e)
        }
    }

    /**
     * Reads active daily to-do tasks from native storage.
     */
    @ReactMethod
    fun getDailyTasks(promise: Promise) {
        try {
            val tasks = storage.getDailyTasksJson()
            promise.resolve(tasks)
        } catch (e: Exception) {
            promise.reject("GET_TASKS_FAILED", e.message, e)
        }
    }

    /**
     * Synchronizes long-term vision, goals, and KPIs from React Native to native storage.
     */
    @ReactMethod
    fun syncGoalsAndKPIs(goalsJson: String, promise: Promise) {
        try {
            storage.setLongTermGoalsJson(goalsJson)
            com.instablocker.app.util.DebugLogger.d("InstaBlockerModule: Goals and KPIs synced successfully.")
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("SYNC_GOALS_FAILED", e.message, e)
        }
    }

    /**
     * Reads long-term goals and KPIs from native storage.
     */
    @ReactMethod
    fun getGoalsAndKPIs(promise: Promise) {
        try {
            val goals = storage.getLongTermGoalsJson()
            promise.resolve(goals)
        } catch (e: Exception) {
            promise.reject("GET_GOALS_FAILED", e.message, e)
        }
    }

    @ReactMethod
    fun isOnboardingCompleted(promise: Promise) {
        try {
            promise.resolve(storage.isOnboardingCompleted())
        } catch (e: Exception) {
            promise.reject("GET_ONBOARDING_FAILED", e.message, e)
        }
    }

    @ReactMethod
    fun setOnboardingCompleted(completed: Boolean, promise: Promise) {
        try {
            storage.setOnboardingCompleted(completed)
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("SET_ONBOARDING_FAILED", e.message, e)
        }
    }

    @ReactMethod
    fun getSelectedPresetId(promise: Promise) {
        try {
            promise.resolve(storage.getSelectedPresetId())
        } catch (e: Exception) {
            promise.reject("GET_PRESET_FAILED", e.message, e)
        }
    }

    @ReactMethod
    fun setSelectedPresetId(presetId: String, promise: Promise) {
        try {
            storage.setSelectedPresetId(presetId)
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("SET_PRESET_FAILED", e.message, e)
        }
    }

    private fun ensureDayRollover() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val lastReset = storage.getLastResetDate()
        val now = System.currentTimeMillis()

        if (lastReset.isNotEmpty() && lastReset != today) {
            storage.setLastResetDate(today)
            storage.setSessionsCompletedToday(0)
            storage.setDailyLocked(false)
            if (storage.getCooldownUntil() <= now) {
                storage.setCooldownUntil(0L)
            }
        } else if (lastReset.isEmpty()) {
            storage.setLastResetDate(today)
        }
    }

    private fun isServiceEnabled(context: Context, serviceClass: Class<out AccessibilityService>): Boolean {
        val expectedComponentName = ComponentName(context, serviceClass).flattenToString()
        val expectedShortName = "${context.packageName}/${serviceClass.name}"

        val enabledServicesSetting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServicesSetting)

        while (colonSplitter.hasNext()) {
            val componentName = colonSplitter.next()
            if (componentName.equals(expectedComponentName, ignoreCase = true) ||
                componentName.equals(expectedShortName, ignoreCase = true) ||
                componentName.contains(serviceClass.simpleName, ignoreCase = true)) {
                return true
            }
        }
        return false
    }
}
