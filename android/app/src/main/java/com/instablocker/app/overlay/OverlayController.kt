package com.instablocker.app.overlay

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.instablocker.app.engine.BlockReason
import com.instablocker.app.util.DebugLogger
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Manages the native Android full-screen blocking overlay window.
 *
 * Utilizes WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY (requires "Display over other apps")
 * with PixelFormat.OPAQUE and FLAG_NOT_FOCUSABLE to provide a flicker-free full-screen barrier.
 * Previously used TYPE_ACCESSIBILITY_OVERLAY, which required an AccessibilityService that
 * caused UPI apps (Paytm/PhonePe/GPay) to block payments.
 */
class OverlayController(private val context: Context) {

    private val windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private val mainHandler = Handler(Looper.getMainLooper())
    private var overlayView: View? = null
    private var isShowing = false

    private var currentReason: BlockReason = BlockReason.NONE
    private var cooldownEndsAt: Long = 0L

    var onCooldownExpiredListener: (() -> Unit)? = null

    // UI references for dynamic updates
    private var titleTextView: TextView? = null
    private var badgeTextView: TextView? = null
    private var countdownTextView: TextView? = null
    private var messageTextView: TextView? = null

    private val countdownTicker = object : Runnable {
        override fun run() {
            if (!isShowing) return
            val now = System.currentTimeMillis()
            if (currentReason == BlockReason.COOLDOWN) {
                if (now < cooldownEndsAt) {
                    updateCountdownOnly(now)
                    mainHandler.postDelayed(this, 1000L)
                } else {
                    DebugLogger.d("[OVERLAY] Cooldown period reached 0 in OverlayController. Triggering cooldown expiration callback.")
                    updateCountdownOnly(cooldownEndsAt)
                    onCooldownExpiredListener?.invoke()
                }
            }
        }
    }

    /**
     * Displays the full-screen blocking overlay.
     * Ensures the overlay is created and attached ONCE, ignoring redundant calls.
     */
    fun show(reason: BlockReason, remainingCooldownMs: Long) {
        DebugLogger.d("[InstaBlocker][OVERLAY] SHOW_REQUESTED reason=$reason remainingCooldownMs=$remainingCooldownMs isShowing=$isShowing")
        runOnMainThread {
            if (isShowing) {
                if (currentReason == reason) {
                    DebugLogger.d("[InstaBlocker][OVERLAY] SHOW_COMPLETED (already visible for reason=$reason)")
                    return@runOnMainThread
                }
                // Reason changed while showing (e.g. COOLDOWN -> DAILY_LIMIT_REACHED)
                DebugLogger.d("[InstaBlocker][OVERLAY] Overlay reason changed while showing: $currentReason -> $reason. Updating content.")
                currentReason = reason
                if (reason == BlockReason.COOLDOWN) {
                    cooldownEndsAt = System.currentTimeMillis() + remainingCooldownMs
                }
                updateContent()
                if (currentReason == BlockReason.COOLDOWN) {
                    startCountdownTicker()
                } else {
                    stopCountdownTicker()
                }
                DebugLogger.d("[InstaBlocker][OVERLAY] SHOW_COMPLETED (reason updated)")
                return@runOnMainThread
            }

            currentReason = reason
            cooldownEndsAt = if (reason == BlockReason.COOLDOWN) {
                System.currentTimeMillis() + remainingCooldownMs
            } else {
                0L
            }

            if (overlayView == null) {
                createOverlayView()
            }

            updateContent()

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                overlayWindowType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.OPAQUE
            ).apply {
                gravity = Gravity.CENTER
            }

            try {
                windowManager.addView(overlayView, params)
                isShowing = true
                DebugLogger.d("[InstaBlocker][OVERLAY] SHOW_COMPLETED Overlay attached to WindowManager (reason=$reason, remainingCooldown=${remainingCooldownMs}ms).")
            } catch (e: Exception) {
                DebugLogger.e("[InstaBlocker][OVERLAY] Failed to add overlay view to WindowManager: ${e.message}", e)
            }

            if (currentReason == BlockReason.COOLDOWN) {
                startCountdownTicker()
            }
        }
    }

    /**
     * Hides and tears down the blocking overlay.
     */
    fun hide() {
        runOnMainThread {
            if (!isShowing) return@runOnMainThread
            stopCountdownTicker()
            if (overlayView != null) {
                try {
                    windowManager.removeView(overlayView)
                    DebugLogger.d("[OVERLAY] Overlay removed from WindowManager.")
                } catch (e: Exception) {
                    DebugLogger.e("[OVERLAY] Error removing overlay from WindowManager: ${e.message}")
                }
            }
            isShowing = false
            currentReason = BlockReason.NONE
        }
    }

    fun isOverlayShowing(): Boolean = isShowing

    private fun startCountdownTicker() {
        stopCountdownTicker()
        updateCountdownOnly(System.currentTimeMillis())
        mainHandler.postDelayed(countdownTicker, 1000L)
    }

    private fun stopCountdownTicker() {
        mainHandler.removeCallbacks(countdownTicker)
    }

    private fun updateCountdownOnly(now: Long) {
        val remainingMs = (cooldownEndsAt - now).coerceAtLeast(0L)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(remainingMs)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(remainingMs) % 60
        val formatted = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        countdownTextView?.text = formatted
        DebugLogger.d("[OVERLAY] Overlay updated: countdown = $formatted (${remainingMs}ms remaining).")
    }

    private fun createOverlayView() {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#0B0F19")) // Solid deep focus background
            val pad = dpToPx(32)
            setPadding(pad, pad, pad, pad)
            isClickable = true
            isFocusable = false
        }

        // Lock Icon Container
        val iconBadge = TextView(context).apply {
            text = "🛡️"
            textSize = 54f
            gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(24)
            }
            layoutParams = lp
        }
        root.addView(iconBadge)

        // Main Title
        titleTextView = TextView(context).apply {
            text = "Instagram Paused"
            setTextColor(Color.parseColor("#F8FAFC"))
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(8)
            }
            layoutParams = lp
        }
        root.addView(titleTextView)

        // Reason Badge
        badgeTextView = TextView(context).apply {
            setTextColor(Color.parseColor("#38BDF8"))
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B"))
                cornerRadius = dpToPx(16).toFloat()
            }
            background = bg
            setPadding(dpToPx(14), dpToPx(6), dpToPx(14), dpToPx(6))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(24)
            }
            layoutParams = lp
        }
        root.addView(badgeTextView)

        // Countdown Timer Text
        countdownTextView = TextView(context).apply {
            setTextColor(Color.parseColor("#38BDF8"))
            textSize = 40f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(16)
            }
            layoutParams = lp
        }
        root.addView(countdownTextView)

        // Informative message
        messageTextView = TextView(context).apply {
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 15f
            gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(36)
            }
            layoutParams = lp
        }
        // Dynamic Tasks / Goals Container
        val extraContentContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(24)
            }
            layoutParams = lp
        }
        root.addView(extraContentContainer)
        this.extraContentContainer = extraContentContainer

        // Exit / Home Screen Button (No unlock button!)
        val homeButton = Button(context).apply {
            text = "Return to Home Screen"
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            isAllCaps = false
            val btnBg = GradientDrawable().apply {
                setColor(Color.parseColor("#2563EB"))
                cornerRadius = dpToPx(12).toFloat()
            }
            background = btnBg
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(52)
            )
            layoutParams = lp
            setOnClickListener {
                navigateToHomeScreen()
            }
        }
        root.addView(homeButton)

        DebugLogger.d("[OVERLAY] Overlay view hierarchy created.")
        overlayView = root
    }

    private var extraContentContainer: LinearLayout? = null

    private fun updateContent() {
        extraContentContainer?.removeAllViews()
        extraContentContainer?.clearAnimation()

        val storage = com.instablocker.app.storage.SharedPrefsEnforcementStorage(context)

        when (currentReason) {
            BlockReason.DAILY_LIMIT_REACHED -> {
                titleTextView?.text = "Daily Limit Reached"
                badgeTextView?.text = "Daily Focus Session Cap"
                badgeTextView?.setTextColor(Color.parseColor("#F43F5E"))
                countdownTextView?.visibility = View.GONE
                messageTextView?.text =
                    "Instagram is locked until midnight to protect your life vision & momentum."

                renderGoalsAndKpis(storage.getLongTermGoalsJson())
            }
            BlockReason.COOLDOWN -> {
                titleTextView?.text = "Instagram Paused"
                badgeTextView?.text = "Cooldown in Progress"
                badgeTextView?.setTextColor(Color.parseColor("#38BDF8"))
                countdownTextView?.visibility = View.VISIBLE
                updateCountdownOnly(System.currentTimeMillis())
                messageTextView?.text =
                    "Time limit reached. Shift your focus to what truly matters today."

                renderDailyTasks(storage.getDailyTasksJson())
            }
            BlockReason.NONE -> {
                // Not blocked
            }
        }
    }

    private fun renderDailyTasks(tasksJson: String) {
        val container = extraContentContainer ?: return
        try {
            val jsonArray = org.json.JSONArray(tasksJson)
            val pendingTasks = mutableListOf<String>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                if (!obj.optBoolean("isCompleted", false)) {
                    val title = obj.optString("title", "").trim()
                    if (title.isNotEmpty()) pendingTasks.add(title)
                }
            }

            if (pendingTasks.isNotEmpty()) {
                val card = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    val bg = GradientDrawable().apply {
                        setColor(Color.parseColor("#131B2E"))
                        cornerRadius = dpToPx(12).toFloat()
                        setStroke(dpToPx(1), Color.parseColor("#1E3A8A"))
                    }
                    background = bg
                    val p = dpToPx(16)
                    setPadding(p, p, p, p)
                    val lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    layoutParams = lp
                }

                val header = TextView(context).apply {
                    text = "📝 What you planned to do today:"
                    setTextColor(Color.parseColor("#60A5FA"))
                    textSize = 14f
                    typeface = Typeface.DEFAULT_BOLD
                    val lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = dpToPx(8) }
                    layoutParams = lp
                }
                card.addView(header)

                for (task in pendingTasks.take(4)) {
                    val taskItem = TextView(context).apply {
                        text = "•  $task"
                        setTextColor(Color.parseColor("#E2E8F0"))
                        textSize = 14f
                        val lp = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { bottomMargin = dpToPx(4) }
                        layoutParams = lp
                    }
                    card.addView(taskItem)
                }
                container.addView(card)
            }
        } catch (e: Exception) {
            DebugLogger.e("Failed to parse daily tasks for overlay: ${e.message}", e)
        }
    }

    private fun renderGoalsAndKpis(goalsJson: String) {
        val container = extraContentContainer ?: return
        try {
            val jsonArray = org.json.JSONArray(goalsJson)
            val goals = mutableListOf<Pair<String, String>>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val title = obj.optString("title", "").trim()
                val kpi = obj.optString("kpiTarget", "").trim()
                if (title.isNotEmpty()) goals.add(Pair(title, kpi))
            }

            val card = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                val bg = GradientDrawable().apply {
                    setColor(Color.parseColor("#271216"))
                    cornerRadius = dpToPx(12).toFloat()
                    setStroke(dpToPx(2), Color.parseColor("#F43F5E"))
                }
                background = bg
                val p = dpToPx(16)
                setPadding(p, p, p, p)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                layoutParams = lp
            }

            val header = TextView(context).apply {
                text = "⚡ REMEMBER YOUR BIG VISION & GOALS ⚡"
                setTextColor(Color.parseColor("#FDA4AF"))
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dpToPx(8) }
                layoutParams = lp
            }
            card.addView(header)

            if (goals.isEmpty()) {
                val defaultGoal = TextView(context).apply {
                    text = "• Build the discipline your future self will thank you for."
                    setTextColor(Color.parseColor("#FECDD3"))
                    textSize = 14f
                }
                card.addView(defaultGoal)
            } else {
                for ((title, kpi) in goals.take(3)) {
                    val goalItem = TextView(context).apply {
                        text = if (kpi.isNotEmpty()) "🎯 $title (KPI: $kpi)" else "🎯 $title"
                        setTextColor(Color.parseColor("#FFE4E6"))
                        textSize = 14f
                        typeface = Typeface.DEFAULT_BOLD
                        val lp = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { bottomMargin = dpToPx(4) }
                        layoutParams = lp
                    }
                    card.addView(goalItem)
                }
            }

            // High-intensity cognitive flash animation requested by user
            val flashAnim = android.view.animation.AlphaAnimation(1.0f, 0.45f).apply {
                duration = 700L
                repeatMode = android.view.animation.Animation.REVERSE
                repeatCount = android.view.animation.Animation.INFINITE
            }
            card.startAnimation(flashAnim)

            container.addView(card)
        } catch (e: Exception) {
            DebugLogger.e("Failed to parse goals for overlay: ${e.message}", e)
        }
    }

    private fun navigateToHomeScreen() {
        hide()
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(homeIntent)
    }

    private fun overlayWindowType(): Int =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

    private fun runOnMainThread(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }
}
