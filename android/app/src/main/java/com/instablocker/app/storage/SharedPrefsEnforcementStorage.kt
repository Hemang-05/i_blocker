package com.instablocker.app.storage

import android.content.Context
import android.content.SharedPreferences

/**
 * Android SharedPreferences implementation of [EnforcementStorage].
 * Uses synchronous commit() for critical state updates to guarantee durability across process kills.
 */
class SharedPrefsEnforcementStorage(context: Context) : EnforcementStorage {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun getLastResetDate(): String {
        return prefs.getString(KEY_LAST_RESET_DATE, "") ?: ""
    }

    override fun setLastResetDate(date: String) {
        prefs.edit().putString(KEY_LAST_RESET_DATE, date).commit()
    }

    override fun getSessionsCompletedToday(): Int {
        return prefs.getInt(KEY_SESSIONS_COMPLETED_TODAY, 0)
    }

    override fun setSessionsCompletedToday(count: Int) {
        prefs.edit().putInt(KEY_SESSIONS_COMPLETED_TODAY, count).commit()
    }

    override fun isDailyLocked(): Boolean {
        return prefs.getBoolean(KEY_DAILY_LOCKED, false)
    }

    override fun setDailyLocked(locked: Boolean) {
        prefs.edit().putBoolean(KEY_DAILY_LOCKED, locked).commit()
    }

    override fun getCooldownUntil(): Long {
        return prefs.getLong(KEY_COOLDOWN_UNTIL, 0L)
    }

    override fun setCooldownUntil(timestampMs: Long) {
        prefs.edit().putLong(KEY_COOLDOWN_UNTIL, timestampMs).commit()
    }

    override fun getContinuousLimitMs(): Long {
        return prefs.getLong(KEY_CONTINUOUS_LIMIT_MS, EnforcementStorage.DEFAULT_CONTINUOUS_LIMIT_MS)
    }

    override fun setContinuousLimitMs(limitMs: Long) {
        prefs.edit().putLong(KEY_CONTINUOUS_LIMIT_MS, limitMs).commit()
    }

    override fun getCooldownDurationMs(): Long {
        return prefs.getLong(KEY_COOLDOWN_DURATION_MS, EnforcementStorage.DEFAULT_COOLDOWN_DURATION_MS)
    }

    override fun setCooldownDurationMs(durationMs: Long) {
        prefs.edit().putLong(KEY_COOLDOWN_DURATION_MS, durationMs).commit()
    }

    override fun getMaxSessionsPerDay(): Int {
        return prefs.getInt(KEY_MAX_SESSIONS_PER_DAY, EnforcementStorage.DEFAULT_MAX_SESSIONS_PER_DAY)
    }

    override fun setMaxSessionsPerDay(max: Int) {
        prefs.edit().putInt(KEY_MAX_SESSIONS_PER_DAY, max).commit()
    }

    override fun isLockUntilMidnightEnabled(): Boolean {
        return prefs.getBoolean(KEY_LOCK_UNTIL_MIDNIGHT, EnforcementStorage.DEFAULT_LOCK_UNTIL_MIDNIGHT)
    }

    override fun setLockUntilMidnightEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LOCK_UNTIL_MIDNIGHT, enabled).commit()
    }

    override fun isCooldownNotificationsEnabled(): Boolean {
        return prefs.getBoolean(KEY_COOLDOWN_NOTIFICATIONS, EnforcementStorage.DEFAULT_COOLDOWN_NOTIFICATIONS)
    }

    override fun setCooldownNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_COOLDOWN_NOTIFICATIONS, enabled).commit()
    }

    override fun getDailyTasksJson(): String {
        return prefs.getString(KEY_DAILY_TASKS_JSON, "[]") ?: "[]"
    }

    override fun setDailyTasksJson(json: String) {
        prefs.edit().putString(KEY_DAILY_TASKS_JSON, json).commit()
    }

    override fun getLongTermGoalsJson(): String {
        return prefs.getString(KEY_LONG_TERM_GOALS_JSON, "[]") ?: "[]"
    }

    override fun setLongTermGoalsJson(json: String) {
        prefs.edit().putString(KEY_LONG_TERM_GOALS_JSON, json).commit()
    }

    override fun isOnboardingCompleted(): Boolean {
        return prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
    }

    override fun setOnboardingCompleted(completed: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).commit()
    }

    override fun getSelectedPresetId(): String {
        return prefs.getString(KEY_SELECTED_PRESET_ID, "deep_focus") ?: "deep_focus"
    }

    override fun setSelectedPresetId(presetId: String) {
        prefs.edit().putString(KEY_SELECTED_PRESET_ID, presetId).commit()
    }

    companion object {
        private const val PREFS_NAME = "insta_blocker_enforcement"
        private const val KEY_LAST_RESET_DATE = "last_reset_date"
        private const val KEY_SESSIONS_COMPLETED_TODAY = "sessions_completed_today"
        private const val KEY_DAILY_LOCKED = "daily_locked"
        private const val KEY_COOLDOWN_UNTIL = "cooldown_until"
        private const val KEY_CONTINUOUS_LIMIT_MS = "continuous_limit_ms"
        private const val KEY_COOLDOWN_DURATION_MS = "cooldown_duration_ms"
        private const val KEY_MAX_SESSIONS_PER_DAY = "max_sessions_per_day"
        private const val KEY_LOCK_UNTIL_MIDNIGHT = "lock_until_midnight"
        private const val KEY_COOLDOWN_NOTIFICATIONS = "cooldown_notifications"
        private const val KEY_DAILY_TASKS_JSON = "daily_tasks_json"
        private const val KEY_LONG_TERM_GOALS_JSON = "long_term_goals_json"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_SELECTED_PRESET_ID = "selected_preset_id"
    }
}
