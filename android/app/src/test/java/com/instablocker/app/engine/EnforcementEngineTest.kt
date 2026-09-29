package com.instablocker.app.engine

import com.instablocker.app.storage.EnforcementStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EnforcementEngineTest {

    private lateinit var mockStorage: FakeEnforcementStorage
    private lateinit var mockScheduler: FakeTimerScheduler
    private lateinit var mockListener: FakeActionListener

    private var currentTime: Long = 1_000_000L
    private var currentDate: String = "2026-09-19"

    private fun createEngine(): EnforcementEngine {
        return EnforcementEngine(
            storage = mockStorage,
            timerScheduler = mockScheduler,
            actionListener = mockListener,
            clock = { currentTime },
            dateProvider = { currentDate }
        )
    }

    @Before
    fun setUp() {
        currentTime = 1_000_000L
        currentDate = "2026-09-19"
        mockStorage = FakeEnforcementStorage()
        mockScheduler = FakeTimerScheduler()
        mockListener = FakeActionListener()
    }

    @Test
    fun testInitialOpen_AllowedAndTimerStarted() {
        val engine = createEngine()

        engine.onForegroundAppChanged("com.instagram.android")

        assertFalse("Should not be blocked on first valid attempt", mockListener.isBlocked)
        assertEquals(BlockReason.NONE, mockListener.lastReason)
        assertTrue("Instagram should be detected as foreground", engine.isTargetForeground())
        assertEquals("Timer should be scheduled for 10 minutes", 600_000L, mockScheduler.lastDelayMs)
        assertNotNull("Scheduled action should not be null", mockScheduler.scheduledAction)
    }

    @Test
    fun testUserLeavesBeforeLimit_AbortsSessionWithoutCounting() {
        val engine = createEngine()
        engine.onForegroundAppChanged("com.instagram.android")

        // User leaves after 5 minutes
        currentTime += 300_000L
        engine.onForegroundAppChanged("com.google.android.apps.nexuslauncher")

        assertFalse("Instagram should no longer be foreground", engine.isTargetForeground())
        assertTrue("Timer should be cancelled when leaving app", mockScheduler.isCancelled)
        assertEquals("Sessions completed today should still be 0", 0, mockStorage.getSessionsCompletedToday())
        assertFalse("Daily locked should be false", mockStorage.isDailyLocked())
    }

    @Test
    fun testSessionLimitReached_IncrementsSessionAndStarts90MinCooldown() {
        val engine = createEngine()
        engine.onForegroundAppChanged("com.instagram.android")

        // 10 minutes elapsed, timer triggers
        currentTime += 600_000L
        mockScheduler.trigger()

        assertEquals("Should have 1 completed session", 1, mockStorage.getSessionsCompletedToday())
        val expectedCooldownUntil = currentTime + 5_400_000L // 90 mins
        assertEquals(expectedCooldownUntil, mockStorage.getCooldownUntil())
        assertTrue("Should be blocked after session limit", mockListener.isBlocked)
        assertEquals(BlockReason.COOLDOWN, mockListener.lastReason)
        assertEquals(5_400_000L, mockListener.remainingCooldownMs)
    }

    @Test
    fun testReopeningDuringCooldown_ImmediatelyBlocked() {
        val engine = createEngine()
        engine.onForegroundAppChanged("com.instagram.android")

        // Complete first session
        currentTime += 600_000L
        mockScheduler.trigger()

        // User leaves app
        engine.onForegroundAppChanged("com.android.launcher")

        // User tries to reopen Instagram 30 minutes into cooldown
        currentTime += 1_800_000L // 30 minutes
        mockScheduler.scheduledAction = null
        engine.onForegroundAppChanged("com.instagram.android")

        assertTrue("Must be immediately blocked during cooldown", mockListener.isBlocked)
        assertEquals(BlockReason.COOLDOWN, mockListener.lastReason)
        val expectedRemaining = 5_400_000L - 1_800_000L
        assertEquals(expectedRemaining, mockListener.remainingCooldownMs)
        assertNull("Should not start a session timer during cooldown", mockScheduler.scheduledAction)
    }

    @Test
    fun testCooldownExpires_AllowsNewSession() {
        val engine = createEngine()
        engine.onForegroundAppChanged("com.instagram.android")
        currentTime += 600_000L
        mockScheduler.trigger()
        engine.onForegroundAppChanged("com.android.launcher")

        // Advance 90 minutes and 1 second (cooldown expired)
        currentTime += 5_400_001L
        mockScheduler.scheduledAction = null
        engine.onForegroundAppChanged("com.instagram.android")

        assertFalse("Should not be blocked after cooldown expires", mockListener.isBlocked)
        assertEquals(BlockReason.NONE, mockListener.lastReason)
        assertEquals(600_000L, mockScheduler.lastDelayMs)
    }

    @Test
    fun testFourthSessionLocksUntilMidnight() {
        val engine = createEngine()

        // Complete sessions 1, 2, 3
        for (i in 1..3) {
            engine.onForegroundAppChanged("com.instagram.android")
            currentTime += 600_000L
            mockScheduler.trigger()
            engine.onForegroundAppChanged("com.android.launcher")
            currentTime += 5_400_001L // wait out cooldown
        }
        assertEquals(3, mockStorage.getSessionsCompletedToday())
        assertFalse(mockStorage.isDailyLocked())

        // 4th session
        engine.onForegroundAppChanged("com.instagram.android")
        currentTime += 600_000L
        mockScheduler.trigger()

        assertEquals(4, mockStorage.getSessionsCompletedToday())
        assertTrue("Daily lock must be engaged after 4th session", mockStorage.isDailyLocked())
        assertTrue(mockListener.isBlocked)
        assertEquals(BlockReason.DAILY_LIMIT_REACHED, mockListener.lastReason)

        // 5th attempt: user tries opening Instagram again
        engine.onForegroundAppChanged("com.android.launcher")
        mockScheduler.scheduledAction = null
        engine.onForegroundAppChanged("com.instagram.android")

        assertTrue("5th attempt must be blocked until midnight", mockListener.isBlocked)
        assertEquals(BlockReason.DAILY_LIMIT_REACHED, mockListener.lastReason)
        assertNull("No timer should be scheduled when daily locked", mockScheduler.scheduledAction)
    }

    @Test
    fun testMidnightReset_ClearsSessionCountAndUnlocks() {
        val engine = createEngine()

        // Complete 4 sessions to trigger daily lock
        mockStorage.setSessionsCompletedToday(4)
        mockStorage.setDailyLocked(true)
        mockStorage.setLastResetDate("2026-09-19")

        // New calendar day arrives
        currentDate = "2026-09-20"
        currentTime += 10_000_000L

        engine.onForegroundAppChanged("com.instagram.android")

        assertEquals("Date should be updated", "2026-09-20", mockStorage.getLastResetDate())
        assertEquals("Sessions must reset to 0 on new day", 0, mockStorage.getSessionsCompletedToday())
        assertFalse("Daily lock must be released on new day", mockStorage.isDailyLocked())
        assertFalse("Instagram should be allowed after midnight reset", mockListener.isBlocked)
        assertEquals(600_000L, mockScheduler.lastDelayMs)
    }

    @Test
    fun testPersistenceSurvivesServiceRestart() {
        // First session completed
        val engine1 = createEngine()
        engine1.onForegroundAppChanged("com.instagram.android")
        currentTime += 600_000L
        mockScheduler.trigger()

        // Simulate app kill / reboot: create a new engine with the same persistent storage
        val engine2 = createEngine()

        assertEquals(1, mockStorage.getSessionsCompletedToday())
        assertTrue("Cooldown must still be active", mockStorage.getCooldownUntil() > currentTime)

        // User attempts to open Instagram immediately after reboot
        engine2.onForegroundAppChanged("com.instagram.android")
        assertTrue("Must still be blocked across restarts", mockListener.isBlocked)
        assertEquals(BlockReason.COOLDOWN, mockListener.lastReason)
    }

    @Test
    fun testRuntimeConfigurationChange_ReschedulesActiveSessionTimer() {
        val engine = createEngine()
        engine.onForegroundAppChanged("com.instagram.android")

        // Initial 10-minute timer scheduled
        assertEquals(600_000L, mockScheduler.lastDelayMs)

        // 2 minutes elapse
        currentTime += 120_000L

        // Rule change arrived via bridge: user reduced usage limit to 5 minutes (300,000 ms)
        mockStorage.setContinuousLimitMs(300_000L)
        engine.onRulesChanged()

        // Active timer must immediately be rescheduled for the remaining 3 minutes (180,000 ms)
        assertEquals(180_000L, mockScheduler.lastDelayMs)
        assertFalse("Should not be blocked yet", mockListener.isBlocked)

        // 3 minutes elapse, timer fires
        currentTime += 180_000L
        mockScheduler.trigger()

        assertEquals(1, mockStorage.getSessionsCompletedToday())
        assertTrue("Must be blocked after remaining 3 minutes complete", mockListener.isBlocked)
        assertEquals(BlockReason.COOLDOWN, mockListener.lastReason)
    }

    @Test
    fun testRuntimeConfigurationChange_ElapsedExceedsNewLimitTriggersImmediateBlock() {
        val engine = createEngine()
        engine.onForegroundAppChanged("com.instagram.android")

        // 6 minutes elapse inside Instagram
        currentTime += 360_000L

        // Rule change arrived: limit reduced from 10m to 5m (300,000 ms)
        mockStorage.setContinuousLimitMs(300_000L)
        mockStorage.setCooldownDurationMs(60_000L)
        engine.onRulesChanged()

        // Because 6m > 5m, the session is immediately completed and blocked!
        assertTrue("Must immediately block when elapsed time exceeds new limit", mockListener.isBlocked)
        assertEquals(BlockReason.COOLDOWN, mockListener.lastReason)
        assertEquals(1, mockStorage.getSessionsCompletedToday())
    }

    @Test
    fun testRuntimeConfigurationChange_LoweringMaxSessionsTriggersDailyLock() {
        val engine = createEngine()
        // 2 sessions completed earlier today
        mockStorage.setSessionsCompletedToday(2)

        // User opens Instagram
        engine.onForegroundAppChanged("com.instagram.android")
        assertFalse("Allowed under default 4 max sessions", mockListener.isBlocked)

        // Admin/User lowers max sessions to 2
        mockStorage.setMaxSessionsPerDay(2)
        engine.onRulesChanged()

        assertTrue("Must immediately lock daily when max sessions reduced to current completed count", mockListener.isBlocked)
        assertEquals(BlockReason.DAILY_LIMIT_REACHED, mockListener.lastReason)
        assertTrue(mockStorage.isDailyLocked())
    }

    @Test
    fun testOnCooldownExpired_WhileForeground_DismissesBlockerAndStartsNewSession() {
        val engine = createEngine()
        engine.onForegroundAppChanged("com.instagram.android")

        // Complete 1st session -> cooldown engaged
        currentTime += 600_000L
        mockScheduler.trigger()
        assertTrue("Must be blocked in cooldown", mockListener.isBlocked)
        assertEquals(BlockReason.COOLDOWN, mockListener.lastReason)

        // Advance time to after cooldown
        currentTime += 5_400_000L // 90 min
        mockScheduler.scheduledAction = null

        // Trigger cooldown expiration (as OverlayController does when countdown reaches 0)
        engine.onCooldownExpired()

        assertEquals("Cooldown timestamp must be cleared", 0L, mockStorage.getCooldownUntil())
        assertFalse("Blocker overlay must be dismissed once cooldown expires", mockListener.isBlocked)
        assertEquals(BlockReason.NONE, mockListener.lastReason)
        assertEquals("New session timer should be scheduled", 600_000L, mockScheduler.lastDelayMs)
        assertNotNull("Session timer must be active", mockScheduler.scheduledAction)
    }

    @Test
    fun testOnCooldownExpired_WhileNotForeground_DismissesBlockerWithoutStartingSession() {
        val engine = createEngine()
        engine.onForegroundAppChanged("com.instagram.android")

        // Complete 1st session -> cooldown engaged
        currentTime += 600_000L
        mockScheduler.trigger()
        assertTrue("Must be blocked in cooldown", mockListener.isBlocked)

        // User navigated home
        engine.onForegroundAppChanged("com.android.launcher")
        currentTime += 5_400_000L
        mockScheduler.scheduledAction = null

        // Trigger cooldown expiration
        engine.onCooldownExpired()

        assertEquals(0L, mockStorage.getCooldownUntil())
        assertFalse(mockListener.isBlocked)
        assertNull("Should not schedule session timer when not in foreground", mockScheduler.scheduledAction)
    }

    @Test
    fun testResetEnforcementState_ClearsAllCountersTimersAndOverlay() {
        val engine = createEngine()
        engine.onForegroundAppChanged("com.instagram.android")

        // Trigger session completion -> in cooldown, 1 session completed
        currentTime += 600_000L
        mockScheduler.trigger()

        assertTrue("Must be blocked in cooldown", mockListener.isBlocked)
        assertEquals(1, mockStorage.getSessionsCompletedToday())
        assertTrue("Cooldown must be active", mockStorage.getCooldownUntil() > currentTime)

        // Also simulate daily lock state
        mockStorage.setDailyLocked(true)
        mockStorage.setSessionsCompletedToday(4)

        // Perform debug reset
        engine.resetEnforcementState()

        assertEquals("Completed sessions must reset to 0", 0, mockStorage.getSessionsCompletedToday())
        assertEquals("Cooldown must reset to 0", 0L, mockStorage.getCooldownUntil())
        assertFalse("Daily lock must be released", mockStorage.isDailyLocked())
        assertFalse("Blocker must be dismissed", mockListener.isBlocked)
        assertEquals(BlockReason.NONE, mockListener.lastReason)
        assertNull("Active session start time must be null", engine.getActiveSessionStartTime())
        assertTrue("Active timer must be cancelled", mockScheduler.isCancelled)
    }

    // --- Test Doubles ---

    private class FakeEnforcementStorage : EnforcementStorage {
        private var lastResetDate: String = ""
        private var sessionsCompletedToday: Int = 0
        private var dailyLocked: Boolean = false
        private var cooldownUntil: Long = 0L
        private var continuousLimitMs: Long = 600_000L
        private var cooldownDurationMs: Long = 5_400_000L
        private var maxSessionsPerDay: Int = 4

        override fun getLastResetDate(): String = lastResetDate
        override fun setLastResetDate(date: String) { lastResetDate = date }

        override fun getSessionsCompletedToday(): Int = sessionsCompletedToday
        override fun setSessionsCompletedToday(count: Int) { sessionsCompletedToday = count }

        override fun isDailyLocked(): Boolean = dailyLocked
        override fun setDailyLocked(locked: Boolean) { dailyLocked = locked }

        override fun getCooldownUntil(): Long = cooldownUntil
        override fun setCooldownUntil(timestampMs: Long) { cooldownUntil = timestampMs }

        override fun getContinuousLimitMs(): Long = continuousLimitMs
        override fun setContinuousLimitMs(limitMs: Long) { continuousLimitMs = limitMs }

        override fun getCooldownDurationMs(): Long = cooldownDurationMs
        override fun setCooldownDurationMs(durationMs: Long) { cooldownDurationMs = durationMs }

        override fun getMaxSessionsPerDay(): Int = maxSessionsPerDay
        override fun setMaxSessionsPerDay(max: Int) { maxSessionsPerDay = max }

        private var lockUntilMidnight: Boolean = true
        private var cooldownNotifications: Boolean = false
        private var dailyTasksJson: String = "[]"
        private var longTermGoalsJson: String = "[]"

        override fun isLockUntilMidnightEnabled(): Boolean = lockUntilMidnight
        override fun setLockUntilMidnightEnabled(enabled: Boolean) { lockUntilMidnight = enabled }

        override fun isCooldownNotificationsEnabled(): Boolean = cooldownNotifications
        override fun setCooldownNotificationsEnabled(enabled: Boolean) { cooldownNotifications = enabled }

        override fun getDailyTasksJson(): String = dailyTasksJson
        override fun setDailyTasksJson(json: String) { dailyTasksJson = json }

        override fun getLongTermGoalsJson(): String = longTermGoalsJson
        override fun setLongTermGoalsJson(json: String) { longTermGoalsJson = json }

        private var onboardingCompleted: Boolean = false
        private var selectedPresetId: String = "deep_focus"

        override fun isOnboardingCompleted(): Boolean = onboardingCompleted
        override fun setOnboardingCompleted(completed: Boolean) { onboardingCompleted = completed }

        override fun getSelectedPresetId(): String = selectedPresetId
        override fun setSelectedPresetId(presetId: String) { selectedPresetId = presetId }
    }

    private class FakeTimerScheduler : TimerScheduler {
        var lastDelayMs: Long = 0L
        var scheduledAction: Runnable? = null
        var isCancelled: Boolean = false

        override fun schedule(delayMs: Long, action: Runnable) {
            lastDelayMs = delayMs
            scheduledAction = action
            isCancelled = false
        }

        override fun cancel() {
            isCancelled = true
            scheduledAction = null
        }

        fun trigger() {
            scheduledAction?.run()
        }
    }

    private class FakeActionListener : EnforcementActionListener {
        var isBlocked: Boolean = false
        var lastReason: BlockReason = BlockReason.NONE
        var remainingCooldownMs: Long = 0L

        override fun onBlockStateChanged(isBlocked: Boolean, reason: BlockReason, remainingCooldownMs: Long) {
            this.isBlocked = isBlocked
            this.lastReason = reason
            this.remainingCooldownMs = remainingCooldownMs
        }
    }
}
