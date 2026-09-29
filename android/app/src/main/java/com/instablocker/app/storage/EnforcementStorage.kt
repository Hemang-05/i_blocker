package com.instablocker.app.storage

/**
 * Interface abstraction for persistence of the enforcement state.
 * Allows pure unit testing of the EnforcementEngine without Android framework dependencies.
 */
interface EnforcementStorage {
    /**
     * The device-local date (e.g. "yyyy-MM-dd") when the state was last reset.
     */
    fun getLastResetDate(): String
    fun setLastResetDate(date: String)

    /**
     * Number of completed, limit-reached sessions today.
     */
    fun getSessionsCompletedToday(): Int
    fun setSessionsCompletedToday(count: Int)

    /**
     * True if the maximum completed sessions limit for the day has been reached.
     */
    fun isDailyLocked(): Boolean
    fun setDailyLocked(locked: Boolean)

    /**
     * Timestamp (ms since epoch) when the current cooldown expires.
     * 0 if not currently in cooldown.
     */
    fun getCooldownUntil(): Long
    fun setCooldownUntil(timestampMs: Long)

    /**
     * Continuous usage limit in milliseconds (default: 10 minutes).
     */
    fun getContinuousLimitMs(): Long
    fun setContinuousLimitMs(limitMs: Long)

    /**
     * Cooldown duration in milliseconds after reaching usage limit (default: 90 minutes).
     */
    fun getCooldownDurationMs(): Long
    fun setCooldownDurationMs(durationMs: Long)

    /**
     * Maximum allowed completed sessions per day (default: 4).
     */
    fun getMaxSessionsPerDay(): Int
    fun setMaxSessionsPerDay(max: Int)

    /**
     * True if Instagram should be locked until midnight upon completing max daily sessions (default: true).
     */
    fun isLockUntilMidnightEnabled(): Boolean
    fun setLockUntilMidnightEnabled(enabled: Boolean)

    /**
     * True if cooldown notifications should be sent to the user (default: false).
     */
    fun isCooldownNotificationsEnabled(): Boolean
    fun setCooldownNotificationsEnabled(enabled: Boolean)

    /**
     * JSON string of pending daily to-do tasks.
     */
    fun getDailyTasksJson(): String
    fun setDailyTasksJson(json: String)

    /**
     * JSON string of long-term vision / goals & KPIs.
     */
    fun getLongTermGoalsJson(): String
    fun setLongTermGoalsJson(json: String)

    /**
     * Flag indicating whether the user has completed initial onboarding.
     */
    fun isOnboardingCompleted(): Boolean
    fun setOnboardingCompleted(completed: Boolean)

    /**
     * Identifier of the currently selected preset ('gentle', 'deep_focus', 'monk').
     */
    fun getSelectedPresetId(): String
    fun setSelectedPresetId(presetId: String)

    companion object {
        const val DEFAULT_CONTINUOUS_LIMIT_MS = 10 * 60 * 1000L // 10 minutes
        const val DEFAULT_COOLDOWN_DURATION_MS = 90 * 60 * 1000L // 90 minutes
        const val DEFAULT_MAX_SESSIONS_PER_DAY = 4
        const val DEFAULT_LOCK_UNTIL_MIDNIGHT = true
        const val DEFAULT_COOLDOWN_NOTIFICATIONS = false
    }
}
