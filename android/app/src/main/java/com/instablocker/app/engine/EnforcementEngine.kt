package com.instablocker.app.engine

import com.instablocker.app.storage.EnforcementStorage
import com.instablocker.app.util.DebugLogger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class BlockReason {
    NONE,
    COOLDOWN,
    DAILY_LIMIT_REACHED
}

interface EnforcementActionListener {
    fun onBlockStateChanged(isBlocked: Boolean, reason: BlockReason, remainingCooldownMs: Long)
}

interface TimerScheduler {
    fun schedule(delayMs: Long, action: Runnable)
    fun cancel()
}

/**
 * Pure, independently testable business logic for enforcing Instagram usage limits.
 *
 * Separation of concerns:
 * - Does not reference Android UI or Context directly.
 * - Delegates time and persistence so all edge-cases (cooldowns, daily limits, midnight rollover)
 *   can be deterministically tested with unit tests.
 */
class EnforcementEngine(
    private val storage: EnforcementStorage,
    private val timerScheduler: TimerScheduler,
    private val actionListener: EnforcementActionListener,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val dateProvider: () -> String = {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    },
    val targetPackage: String = TARGET_PACKAGE
) {
    companion object {
        const val TARGET_PACKAGE = "com.instagram.android"
    }

    private var isTargetAppForeground: Boolean = false
    private var activeSessionStartTime: Long? = null

    init {
        checkAndResetDayIfNeeded(clock())
    }

    /**
     * Checks if the device-local calendar day has changed.
     * If so, safely resets the daily sessions counter and dailyLocked status.
     */
    fun checkAndResetDayIfNeeded(now: Long = clock()) {
        val today = dateProvider()
        val lastReset = storage.getLastResetDate()

        if (lastReset.isEmpty()) {
            storage.setLastResetDate(today)
            return
        }

        if (lastReset != today) {
            DebugLogger.d("New local calendar day detected ($today vs $lastReset). Resetting daily session count and locks.")
            storage.setLastResetDate(today)
            storage.setSessionsCompletedToday(0)
            storage.setDailyLocked(false)
            if (storage.getCooldownUntil() <= now) {
                storage.setCooldownUntil(0L)
            }
        }
    }

    /**
     * Checks if Instagram access is currently blocked (due to daily limit or cooldown).
     */
    fun isEnforcementBlocked(now: Long = clock()): Boolean {
        val maxSessions = storage.getMaxSessionsPerDay()
        val completedSessions = storage.getSessionsCompletedToday()
        if (storage.isDailyLocked() || completedSessions >= maxSessions) {
            return true
        }
        if (now < storage.getCooldownUntil()) {
            return true
        }
        return false
    }

    /**
     * Invoked whenever the foreground package changes.
     */
    fun onForegroundAppChanged(packageName: String) {
        val now = clock()
        DebugLogger.d("[InstaBlocker][ENGINE] FOREGROUND_CHANGED pkg=$packageName isTargetAppForeground=$isTargetAppForeground activeSessionStartTime=$activeSessionStartTime isEnforcementBlocked=${isEnforcementBlocked(now)}")
        checkAndResetDayIfNeeded(now)

        if (packageName == targetPackage) {
            isTargetAppForeground = true
            evaluateForegroundTarget(now)
        } else {
            if (isTargetAppForeground) {
                val elapsed = activeSessionStartTime?.let { now - it } ?: 0L
                DebugLogger.d("[InstaBlocker][ENGINE] SESSION_RESET_OR_ABORTED user left $targetPackage after $elapsed ms.")
                timerScheduler.cancel()
                activeSessionStartTime = null
                isTargetAppForeground = false
                if (!isEnforcementBlocked(now)) {
                    DebugLogger.d("[InstaBlocker][ENGINE] Unblocking as app left and not enforcement-blocked.")
                    actionListener.onBlockStateChanged(false, BlockReason.NONE, 0L)
                } else {
                    DebugLogger.d("[InstaBlocker][ENGINE] Preserving block state as enforcement is still active.")
                }
            }
        }
    }

    private fun evaluateForegroundTarget(now: Long) {
        val maxSessions = storage.getMaxSessionsPerDay()
        val completedSessions = storage.getSessionsCompletedToday()
        val isDailyLocked = storage.isDailyLocked() || completedSessions >= maxSessions

        if (isDailyLocked) {
            if (!storage.isDailyLocked()) {
                storage.setDailyLocked(true)
            }
            DebugLogger.d("[InstaBlocker][ENGINE] STATE_BEFORE=sessionsCompleted=$completedSessions, cooldownUntil=${storage.getCooldownUntil()}, dailyLocked=${storage.isDailyLocked()}")
            DebugLogger.d("[InstaBlocker][ENGINE] STATE_AFTER=DAILY_LIMIT_REACHED")
            timerScheduler.cancel()
            activeSessionStartTime = null
            actionListener.onBlockStateChanged(true, BlockReason.DAILY_LIMIT_REACHED, 0L)
            return
        }

        val cooldownUntil = storage.getCooldownUntil()
        if (now < cooldownUntil) {
            val remaining = cooldownUntil - now
            DebugLogger.d("[InstaBlocker][ENGINE] STATE_BEFORE=sessionsCompleted=$completedSessions, cooldownUntil=$cooldownUntil, dailyLocked=false")
            DebugLogger.d("[InstaBlocker][ENGINE] STATE_AFTER=COOLDOWN remainingMs=$remaining")
            timerScheduler.cancel()
            activeSessionStartTime = null
            actionListener.onBlockStateChanged(true, BlockReason.COOLDOWN, remaining)
            return
        } else if (cooldownUntil > 0L) {
            DebugLogger.d("[InstaBlocker][ENGINE] Cooldown expired. Instagram access restored.")
            storage.setCooldownUntil(0L)
        }

        // Allowed to use: dismiss blocker and start session timer
        actionListener.onBlockStateChanged(false, BlockReason.NONE, 0L)
        if (activeSessionStartTime == null) {
            activeSessionStartTime = now
            val limitMs = storage.getContinuousLimitMs()
            DebugLogger.d("[InstaBlocker][ENGINE] SESSION_STARTING pkg=$targetPackage limitMs=$limitMs")
            DebugLogger.d("[InstaBlocker][ENGINE] TIMER_SCHEDULED delayMs=$limitMs")
            timerScheduler.schedule(limitMs, Runnable {
                DebugLogger.d("[InstaBlocker][ENGINE] TIMER_FIRED isTargetAppForeground=$isTargetAppForeground")
                onSessionLimitReached()
            })
        }
    }

    /**
     * Triggered when the continuous-use limit timer expires.
     */
    fun onSessionLimitReached() {
        val now = clock()
        DebugLogger.d("[InstaBlocker][ENGINE] onSessionLimitReached triggered. isTargetAppForeground=$isTargetAppForeground")
        checkAndResetDayIfNeeded(now)

        if (!isTargetAppForeground) {
            DebugLogger.d("[InstaBlocker][ENGINE] SESSION_COMPLETION_ABORTED: isTargetAppForeground is false")
            return
        }

        DebugLogger.d("[InstaBlocker][ENGINE] SESSION_COMPLETING")
        val currentCompleted = storage.getSessionsCompletedToday()
        val maxSessions = storage.getMaxSessionsPerDay()
        DebugLogger.d("[InstaBlocker][ENGINE] STATE_BEFORE=sessionsCompleted=$currentCompleted, cooldownUntil=${storage.getCooldownUntil()}, dailyLocked=${storage.isDailyLocked()}")

        activeSessionStartTime = null
        val nextCompleted = currentCompleted + 1
        storage.setSessionsCompletedToday(nextCompleted)
        DebugLogger.d("[InstaBlocker][ENGINE] Session count incremented to $nextCompleted of $maxSessions.")

        if (nextCompleted >= maxSessions) {
            storage.setDailyLocked(true)
            DebugLogger.d("[InstaBlocker][ENGINE] STATE_AFTER=DAILY_LIMIT_REACHED")
            DebugLogger.d("[InstaBlocker][ENGINE] INVOKING_CALLBACK isBlocked=true reason=DAILY_LIMIT_REACHED remainingMs=0")
            actionListener.onBlockStateChanged(true, BlockReason.DAILY_LIMIT_REACHED, 0L)
        } else {
            val cooldownDuration = storage.getCooldownDurationMs()
            val cooldownUntil = now + cooldownDuration
            storage.setCooldownUntil(cooldownUntil)
            DebugLogger.d("[InstaBlocker][ENGINE] STATE_AFTER=COOLDOWN cooldownDurationMs=$cooldownDuration cooldownUntil=$cooldownUntil")
            DebugLogger.d("[InstaBlocker][ENGINE] INVOKING_CALLBACK isBlocked=true reason=COOLDOWN remainingMs=$cooldownDuration")
            actionListener.onBlockStateChanged(true, BlockReason.COOLDOWN, cooldownDuration)
        }
    }

    /**
     * Called when the cooldown timer naturally expires.
     */
    fun onCooldownExpired() {
        val now = clock()
        DebugLogger.d("[COOLDOWN] Cooldown expiration triggered.")
        storage.setCooldownUntil(0L)
        if (isTargetAppForeground) {
            DebugLogger.d("[COOLDOWN] Instagram is foreground after cooldown expired. Restoring access.")
            evaluateForegroundTarget(now)
        } else {
            actionListener.onBlockStateChanged(false, BlockReason.NONE, 0L)
        }
    }

    /**
     * Called whenever rules are updated at runtime via the bridge.
     * Immediately applies the new configuration to any active session or block state.
     */
    fun onRulesChanged() {
        val now = clock()
        DebugLogger.d("Rules updated at runtime. Re-evaluating enforcement state.")
        checkAndResetDayIfNeeded(now)

        if (isTargetAppForeground) {
            val maxSessions = storage.getMaxSessionsPerDay()
            val completedSessions = storage.getSessionsCompletedToday()
            val isDailyLocked = storage.isDailyLocked() || completedSessions >= maxSessions

            if (isDailyLocked) {
                storage.setDailyLocked(true)
                timerScheduler.cancel()
                activeSessionStartTime = null
                actionListener.onBlockStateChanged(true, BlockReason.DAILY_LIMIT_REACHED, 0L)
                return
            }

            val cooldownUntil = storage.getCooldownUntil()
            if (now < cooldownUntil) {
                timerScheduler.cancel()
                activeSessionStartTime = null
                val remaining = cooldownUntil - now
                actionListener.onBlockStateChanged(true, BlockReason.COOLDOWN, remaining)
                return
            }

            val startTime = activeSessionStartTime
            if (startTime != null) {
                val elapsed = now - startTime
                val newLimit = storage.getContinuousLimitMs()
                if (elapsed >= newLimit) {
                    DebugLogger.d("Elapsed session time ($elapsed ms) meets/exceeds new limit ($newLimit ms). Triggering session completion.")
                    onSessionLimitReached()
                } else {
                    val remaining = newLimit - elapsed
                    DebugLogger.d("Rescheduling active session timer: $remaining ms remaining (new limit: $newLimit ms).")
                    timerScheduler.schedule(remaining, Runnable {
                        onSessionLimitReached()
                    })
                }
            }
        }
    }

    fun isTargetForeground(): Boolean = isTargetAppForeground

    fun getActiveSessionStartTime(): Long? = activeSessionStartTime

    /**
     * Resets enforcement state (completed sessions, daily lock, cooldown, active session).
     * Primarily used for debug testing from a clean state.
     */
    fun resetEnforcementState() {
        DebugLogger.d("Resetting enforcement state.")
        storage.setSessionsCompletedToday(0)
        storage.setCooldownUntil(0L)
        storage.setDailyLocked(false)
        activeSessionStartTime = null
        timerScheduler.cancel()
        actionListener.onBlockStateChanged(false, BlockReason.NONE, 0L)
    }

    fun destroy() {
        timerScheduler.cancel()
        activeSessionStartTime = null
        isTargetAppForeground = false
    }
}
