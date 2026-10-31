package com.example.jedain

import android.animation.ValueAnimator
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
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.jedain.ui.theme.DurationRedDark
import com.example.jedain.ui.theme.DurationRedLight
import com.example.jedain.ui.theme.DurationYellowDark
import com.example.jedain.ui.theme.DurationYellowLight
import com.example.jedain.ui.theme.SurfaceDarkCharcoal
import com.example.jedain.ui.theme.SurfaceWhite
import com.example.jedain.ui.theme.TextNearBlack
import com.example.jedain.ui.theme.TextPrimaryDark
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds

class FloatingBubbleController(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isEnabled(): Boolean = prefs.getBoolean(KEY_FLOATING_BUBBLE_ENABLED, true)

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FLOATING_BUBBLE_ENABLED, enabled).apply()
    }

    fun getLastY(): Int = prefs.getInt(KEY_BUBBLE_Y, -1)

    fun saveLastY(y: Int) {
        prefs.edit().putInt(KEY_BUBBLE_Y, y).apply()
    }

    companion object {
        const val PREFS_NAME = "jedain_prefs"
        const val KEY_FLOATING_BUBBLE_ENABLED = "floating_bubble_enabled"
        const val KEY_BUBBLE_Y = "bubble_y"
    }
}

class FloatingBubbleService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var repository: AppMonitorRepository
    private lateinit var targetPrefs: TargetSettingsPreferences
    private lateinit var controller: FloatingBubbleController

    private var bubbleView: LinearLayout? = null
    private var logoImageView: ImageView? = null
    private var timeTextView: TextView? = null
    private var pillBackground: GradientDrawable? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private var isSnappedToRight = true
    private var isViewAttached = false
    private var snapAnimator: ValueAnimator? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var updateJob: Job? = null
    private var screenReceiver: BroadcastReceiver? = null

    private var currentForegroundPackage: String? = null
    private var lastUsageEventTimestamp: Long = 0L

    // Real-time monotonic screen time tracking
    private var baseTodayMillis: Long = 0L
    private var baseElapsedRealtime: Long = 0L
    private var isMonitoredAppForegroundNow: Boolean = false
    private var lastDisplayedText: String? = null
    private var lastBorderColor: Int? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "FloatingBubbleService onCreate")

        controller = FloatingBubbleController(this)
        if (!controller.isEnabled() || !PermissionHelper.hasOverlayPermission(this)) {
            Log.w(TAG, "Floating bubble is disabled or overlay permission not granted, stopping service")
            stopSelf()
            return
        }

        instanceRef = WeakReference(this)
        repository = AppMonitorRepository(applicationContext)
        targetPrefs = TargetSettingsPreferences(applicationContext)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        createNotificationChannel()
        SessionReminderNotificationHelper.cancelAllReminders(this)
        val notification = buildForegroundNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        initBubbleView()
        registerScreenStateReceiver()

        val powerManager = getSystemService(POWER_SERVICE) as? PowerManager
        val isInteractive = powerManager?.isInteractive ?: true
        Log.d(TAG, "Initial screen state: isInteractive=$isInteractive")
        if (isInteractive) {
            startUpdateLoop()
        } else {
            refreshNotificationScreenTime()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "FloatingBubbleService onStartCommand (action=${intent?.action})")
        if (intent?.action == ACTION_HIDE_BUBBLE) {
            Log.d(TAG, "Action 'Sembunyikan' clicked, disabling bubble and stopping service")
            controller.setEnabled(false)
            stopSelf()
            return START_NOT_STICKY
        }

        if (!controller.isEnabled()) {
            stopSelf()
            return START_NOT_STICKY
        }

        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.d(TAG, "onConfigurationChanged triggered")
        repositionOnConfigurationChange()
        lastDisplayedText = null
        lastBorderColor = null
        updateRealtimeTimer()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "FloatingBubbleService onDestroy")
        if (instanceRef?.get() === this) {
            instanceRef = null
        }
        snapAnimator?.cancel()
        snapAnimator = null
        stopUpdateLoop()
        serviceScope.cancel()
        unregisterScreenStateReceiver()
        FloatingSnackbarManager.hideImmediate()

        if (isViewAttached) {
            bubbleView?.let { view ->
                runCatching {
                    windowManager.removeView(view)
                }.onFailure { e ->
                    Log.e(TAG, "Failed to remove bubbleView on destroy", e)
                }
            }
            isViewAttached = false
        }
    }

    private fun initBubbleView() {
        val density = resources.displayMetrics.density
        val heightPx = (40 * density).roundToInt()
        val screenWidth = getScreenWidth()
        val screenHeight = getScreenHeight()

        val view = createPillView(density, heightPx)

        val savedY = controller.getLastY()
        val initialY = if (savedY >= 0) {
            savedY.coerceIn(0, (screenHeight - heightPx).coerceAtLeast(0))
        } else {
            (screenHeight * 0.25f).toInt()
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            heightPx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screenWidth
            y = initialY
        }
        layoutParams = params

        // Awalnya sembunyikan view sampai loop mendeteksi aplikasi dipantau terbuka
        view.visibility = View.GONE

        runCatching {
            windowManager.addView(view, params)
            isViewAttached = true
            Log.d(TAG, "Bubble view attached to WindowManager with height=${heightPx}px (initialY=$initialY)")
        }.onFailure { e ->
            Log.e(TAG, "Failed to add bubble view to WindowManager", e)
        }
    }

    private fun createPillView(density: Float, heightPx: Int): LinearLayout {
        val iconSizePx = (22 * density).roundToInt()
        val paddingHorizontalPx = (14 * density).roundToInt()
        val iconMarginEndPx = (8 * density).roundToInt()

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = heightPx
            setPadding(paddingHorizontalPx, 0, paddingHorizontalPx, 0)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                heightPx,
            )
            clipToOutline = true
            elevation = 0f
        }

        val logoView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(iconSizePx, iconSizePx).apply {
                marginEnd = iconMarginEndPx
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        val textView = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            fontFeatureSettings = "tnum"
            maxLines = 1
            includeFontPadding = false
            gravity = Gravity.CENTER_VERTICAL
        }

        container.addView(logoView)
        container.addView(textView)

        logoImageView = logoView
        timeTextView = textView
        bubbleView = container

        val isDark = isSystemDarkMode()
        val initialBg = if (isDark) SurfaceDarkCharcoal.toArgb() else SurfaceWhite.toArgb()
        val initialBorder = resolveBubbleBorderColor(0.0, isDark)
        val strokeWidthPx = (1.5f * density).roundToInt().coerceAtLeast(1)

        val background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 20f * density
            setColor(initialBg)
            setStroke(strokeWidthPx, initialBorder)
        }
        container.background = background
        pillBackground = background

        setupTouchAndClickListeners(container)
        setupLayoutChangeListener(container)

        val initialText = if (repository.hasUsageStatsPermission()) {
            val remaining = BreakSessionManager.getRemainingSessionMillis(this)
            formatTimerDigital(remaining)
        } else {
            "--"
        }
        updatePillUI(
            text = initialText,
            borderColor = initialBorder,
            isDark = isDark,
        )

        return container
    }

    private fun setupTouchAndClickListeners(view: View) {
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isMoving = false

        view.setOnTouchListener { v, event ->
            val params = layoutParams ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    snapAnimator?.cancel()
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isMoving = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (!isMoving && (hypot(dx.toDouble(), dy.toDouble()) > touchSlop)) {
                        isMoving = true
                    }
                    if (isMoving) {
                        val screenWidth = getScreenWidth()
                        val screenHeight = getScreenHeight()
                        val viewWidth = v.width
                        val viewHeight = v.height
                        params.x = (initialX + dx).coerceIn(0, (screenWidth - viewWidth).coerceAtLeast(0))
                        params.y = (initialY + dy).coerceIn(0, (screenHeight - viewHeight).coerceAtLeast(0))
                        runCatching {
                            windowManager.updateViewLayout(v, params)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isMoving) {
                        v.performClick()
                    } else {
                        val screenWidth = getScreenWidth()
                        val viewWidth = v.width
                        val viewCenterX = params.x + viewWidth / 2
                        val targetX = if (viewCenterX >= screenWidth / 2) {
                            isSnappedToRight = true
                            (screenWidth - viewWidth).coerceAtLeast(0)
                        } else {
                            isSnappedToRight = false
                            0
                        }

                        controller.saveLastY(params.y)
                        animateSnap(v, params, params.x, targetX)
                    }
                    true
                }
                else -> false
            }
        }

        view.setOnClickListener {
            Log.d(TAG, "Floating bubble clicked, launching JedaIn MainActivity")
            val openIntent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(openIntent)
        }
    }

    private fun animateSnap(view: View, params: WindowManager.LayoutParams, startX: Int, targetX: Int) {
        snapAnimator?.cancel()
        snapAnimator = ValueAnimator.ofInt(startX, targetX).apply {
            duration = 200L
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                val animatedX = animation.animatedValue as Int
                params.x = animatedX
                runCatching {
                    windowManager.updateViewLayout(view, params)
                }
            }
            start()
        }
        Log.d(
            TAG,
            "Bubble snap animated from $startX to $targetX (200ms). isSnappedToRight=$isSnappedToRight, savedY=${params.y}",
        )
    }

    private fun setupLayoutChangeListener(view: View) {
        view.addOnLayoutChangeListener { v, left, _, right, _, oldLeft, _, oldRight, _ ->
            val oldWidth = oldRight - oldLeft
            val newWidth = right - left
            if (newWidth > 0 && newWidth != oldWidth) {
                val params = layoutParams ?: return@addOnLayoutChangeListener
                val screenWidth = getScreenWidth()
                Log.d(
                    TAG,
                    "Pill width changed from $oldWidth to $newWidth (isSnappedToRight=$isSnappedToRight)",
                )
                if (isSnappedToRight) {
                    params.x = (screenWidth - newWidth).coerceAtLeast(0)
                    runCatching {
                        windowManager.updateViewLayout(v, params)
                    }
                } else {
                    params.x = 0
                    runCatching {
                        windowManager.updateViewLayout(v, params)
                    }
                }
            }
        }
    }

    private fun updatePillUI(
        text: String,
        borderColor: Int,
        isDark: Boolean,
    ) {
        val textChanged = (text != lastDisplayedText)
        val borderChanged = (borderColor != lastBorderColor)

        if (!textChanged && !borderChanged) {
            return
        }

        val density = resources.displayMetrics.density
        val strokeWidthPx = (1.5f * density).roundToInt().coerceAtLeast(1)
        val bgColor = if (isDark) SurfaceDarkCharcoal.toArgb() else SurfaceWhite.toArgb()
        val textColor = if (isDark) TextPrimaryDark.toArgb() else TextNearBlack.toArgb()
        val logoRes = if (isDark) R.drawable.dark_logo else R.drawable.logo

        if (textChanged) {
            timeTextView?.text = text
            lastDisplayedText = text
        }

        timeTextView?.setTextColor(textColor)
        logoImageView?.setImageResource(logoRes)

        if (borderChanged) {
            pillBackground?.setColor(bgColor)
            pillBackground?.setStroke(strokeWidthPx, borderColor)
            lastBorderColor = borderColor
        }

        Log.d(
            TAG,
            "Bubble UI updated: text='$text', borderColor=#${Integer.toHexString(borderColor)}, isDark=$isDark",
        )
    }

    private var lastInterventionLaunchTime: Long = 0L

    private fun startInterventionScreen(mode: String) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastInterventionLaunchTime < 2000L) {
            return
        }
        lastInterventionLaunchTime = now
        FloatingSnackbarManager.hideImmediate()
        SessionReminderNotificationHelper.cancelAllReminders(this)
        Log.d(TAG, "Launching InterventionActivity with mode=$mode")
        val intent = InterventionActivity.createIntent(this, mode)
        startActivity(intent)
    }

    private fun startUpdateLoop() {
        if (updateJob?.isActive == true) {
            Log.d(TAG, "Update loop already active, skipping duplicate start")
            return
        }
        Log.d(TAG, "Starting foreground app check & real-time timer update loop")
        updateJob = serviceScope.launch {
            var tickCount = 0
            if (repository.hasUsageStatsPermission()) {
                refreshScreenTimeBase()
            }

            while (isActive) {
                val isMonitoredAppOpen = checkIsMonitoredAppInForeground()
                val wasMonitored = isMonitoredAppForegroundNow
                isMonitoredAppForegroundNow = isMonitoredAppOpen

                val isResting = BreakSessionManager.isResting(this@FloatingBubbleService)

                if (isMonitoredAppOpen) {
                    if (isResting) {
                        setBubbleVisibility(false)
                        FloatingSnackbarManager.hideImmediate()
                        startInterventionScreen(InterventionActivity.MODE_RESTING)
                    } else {
                        if (!wasMonitored) {
                            BreakSessionManager.onMonitoredAppEntered(this@FloatingBubbleService)
                            refreshScreenTimeBase()
                        }

                        val expired = BreakSessionManager.tick(this@FloatingBubbleService)
                        if (expired) {
                            setBubbleVisibility(false)
                            FloatingSnackbarManager.hideImmediate()
                            startInterventionScreen(InterventionActivity.MODE_EXPIRED)
                        } else {
                            val shouldShow = !isAppInForeground && controller.isEnabled()
                            setBubbleVisibility(shouldShow)

                            if (shouldShow) {
                                // Resync dengan UsageStatsManager setiap 30 detik untuk daily target
                                if (tickCount > 0 && tickCount % 30 == 0) {
                                    refreshScreenTimeBase()
                                }
                                updateRealtimeTimer()
                            }

                            // Periksa dan tampilkan Floating Snackbar Pengingat Awal / Countdown
                            checkAndTriggerFloatingSnackbar()
                        }
                    }
                } else {
                    if (wasMonitored) {
                        BreakSessionManager.onMonitoredAppExited(this@FloatingBubbleService)
                    }
                    setBubbleVisibility(false)
                    FloatingSnackbarManager.hideImmediate()
                }

                // Pembaruan notifikasi silent setiap 1 menit (60 detik) atau saat tick pertama (tick 0)
                if (tickCount % 60 == 0) {
                    refreshNotificationScreenTime()
                }

                tickCount++
                delay(1.seconds)
            }
        }
    }

    private fun checkAndTriggerFloatingSnackbar() {
        val targetSettings = targetPrefs.loadSettings()
        if (!targetSettings.reminderOnSessionEnd) {
            return
        }

        val remainingMillis = BreakSessionManager.getRemainingSessionMillis(this)
        val totalSessionMillis = BreakSessionManager.getTotalSessionMillis(this)

        // 1. Trigger 60 Detik: Countdown persiapan jeda sebelum batas waktu tercapai (remaining <= 60s)
        if (remainingMillis in 1L..BreakSessionManager.SIXTY_SECONDS_MILLIS) {
            val secondsLeft = ((remainingMillis + 999L) / 1000L).toInt().coerceIn(1, 60)

            if (!BreakSessionManager.isCountdownDismissed(this)) {
                if (FloatingSnackbarManager.getCurrentType() == FloatingSnackbarManager.SnackbarType.COUNTDOWN_60SEC &&
                    FloatingSnackbarManager.isVisible()
                ) {
                    FloatingSnackbarManager.updateCountdown(secondsLeft)
                } else if (!BreakSessionManager.is60SecReminderSent(this)) {
                    BreakSessionManager.set60SecReminderSent(this, true)
                    FloatingSnackbarManager.showCountdownReminder(
                        context = this,
                        secondsLeft = secondsLeft,
                        onDismiss = {
                            BreakSessionManager.setCountdownDismissed(this, true)
                        },
                        onTakeBreak = {
                            startInterventionScreen(InterventionActivity.MODE_EXPIRED)
                        },
                    )
                }
            }
        }
        // 2. Trigger 3 Menit: One-time countdown threshold tepat saat sisa waktu mencapai 03:00 (remaining in 60s+1..3m)
        else if (remainingMillis in (BreakSessionManager.SIXTY_SECONDS_MILLIS + 1L)..BreakSessionManager.THREE_MINUTES_MILLIS &&
            totalSessionMillis >= BreakSessionManager.THREE_MINUTES_MILLIS
        ) {
            if (!BreakSessionManager.is3MinReminderSent(this)) {
                BreakSessionManager.set3MinReminderSent(this, true)
                FloatingSnackbarManager.showInitialReminder(
                    context = this,
                    onDismiss = {
                        // Pengguna memilih tutup
                    },
                    onTakeBreak = {
                        startInterventionScreen(InterventionActivity.MODE_EXPIRED)
                    },
                )
            }
        }
    }

    private fun stopUpdateLoop() {
        if (updateJob != null) {
            Log.d(TAG, "Stopping screen time update loop")
            updateJob?.cancel()
            updateJob = null
        }
    }

    private suspend fun refreshScreenTimeBase() {
        val summary = repository.getMonitoredScreenTimeSummary()
        baseTodayMillis = summary.todayTotalMillis
        baseElapsedRealtime = SystemClock.elapsedRealtime()
        Log.d(
            TAG,
            "Screen time base refreshed from UsageStatsManager: baseTodayMillis=$baseTodayMillis (${formatTimerDigital(baseTodayMillis)})",
        )
    }

    private fun updateRealtimeTimer() {
        val isDark = isSystemDarkMode()
        val hasUsagePermission = repository.hasUsageStatsPermission()
        if (!hasUsagePermission) {
            val neutralBorder = resolveBubbleBorderColor(0.0, isDark)
            updatePillUI(text = "--", borderColor = neutralBorder, isDark = isDark)
            return
        }

        val monitoredPackages = repository.getMonitoredPackageSet()
        if (monitoredPackages.isEmpty()) {
            val neutralBorder = resolveBubbleBorderColor(0.0, isDark)
            updatePillUI(text = "00s", borderColor = neutralBorder, isDark = isDark)
            return
        }

        val remainingMillis = BreakSessionManager.getRemainingSessionMillis(this)
        val timerText = formatTimerDigital(remainingMillis)

        val totalSessionMillis = BreakSessionManager.getTotalSessionMillis(this)
        val sessionElapsedRatio = if (totalSessionMillis > 0L) {
            (totalSessionMillis - remainingMillis).toDouble() / totalSessionMillis.toDouble()
        } else {
            0.0
        }

        val nowElapsed = SystemClock.elapsedRealtime()
        val activeDelta = if (isMonitoredAppForegroundNow && baseElapsedRealtime > 0L) {
            (nowElapsed - baseElapsedRealtime).coerceAtLeast(0L)
        } else {
            0L
        }

        val currentTotalMillis = baseTodayMillis + activeDelta
        val targetSettings = targetPrefs.loadSettings()
        val targetMinutes = targetSettings.validatedDailyTargetMinutes
        val targetMillis = targetMinutes * 60_000L

        val dailyRatio = if (targetMillis > 0L) {
            currentTotalMillis.toDouble() / targetMillis.toDouble()
        } else {
            0.0
        }

        val effectiveRatio = maxOf(sessionElapsedRatio, dailyRatio)
        val borderColor = resolveBubbleBorderColor(effectiveRatio, isDark)

        updatePillUI(text = timerText, borderColor = borderColor, isDark = isDark)
    }

    private fun checkIsMonitoredAppInForeground(): Boolean {
        if (isAppInForeground) return false

        val monitoredPackages = repository.getMonitoredPackageSet()
        if (monitoredPackages.isEmpty()) return false
        if (!repository.hasUsageStatsPermission()) return false

        val usageStatsManager = getSystemService(USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return false

        val now = System.currentTimeMillis()
        val queryStart = if (lastUsageEventTimestamp > 0L) {
            (lastUsageEventTimestamp - 1000L).coerceAtLeast(now - 60_000L)
        } else {
            now - 60_000L
        }

        val events = runCatching {
            usageStatsManager.queryEvents(queryStart, now)
        }.getOrNull() ?: return false

        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            lastUsageEventTimestamp = event.timeStamp.coerceAtLeast(lastUsageEventTimestamp)

            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    currentForegroundPackage = event.packageName
                }
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED -> {
                    if (currentForegroundPackage == event.packageName) {
                        currentForegroundPackage = null
                    }
                }
                16, 17 -> { // SCREEN_NON_INTERACTIVE or KEYGUARD_SHOWN
                    currentForegroundPackage = null
                }
            }
        }

        val activePkg = currentForegroundPackage
        val isMonitored = activePkg != null && activePkg != packageName && activePkg in monitoredPackages
        return isMonitored
    }

    private fun setBubbleVisibility(visible: Boolean) {
        val view = bubbleView ?: return
        val currentVisibility = view.visibility
        val targetVisibility = if (visible) View.VISIBLE else View.GONE
        if (currentVisibility != targetVisibility) {
            view.visibility = targetVisibility
            Log.d(TAG, "Bubble visibility changed: visible=$visible (currentPkg=$currentForegroundPackage)")
        }
    }

    private fun registerScreenStateReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_ON -> {
                        Log.d(TAG, "ACTION_SCREEN_ON received, running calculation immediately then continuing loop")
                        stopUpdateLoop()
                        startUpdateLoop()
                    }
                    Intent.ACTION_SCREEN_OFF -> {
                        Log.d(TAG, "ACTION_SCREEN_OFF received, stopping update loop")
                        currentForegroundPackage = null
                        setBubbleVisibility(false)
                        FloatingSnackbarManager.hideImmediate()
                        stopUpdateLoop()
                    }
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenReceiver, filter)
        }
    }

    private fun unregisterScreenStateReceiver() {
        screenReceiver?.let {
            runCatching { unregisterReceiver(it) }
            screenReceiver = null
        }
    }

    private fun repositionOnConfigurationChange() {
        val screenWidth = getScreenWidth()
        val view = bubbleView ?: return
        val params = layoutParams ?: return
        val viewWidth = view.width

        if (isSnappedToRight) {
            params.x = (screenWidth - viewWidth).coerceAtLeast(0)
        } else {
            params.x = 0
        }
        runCatching {
            windowManager.updateViewLayout(view, params)
        }
    }

    fun updateAppVisibility(inForeground: Boolean) {
        serviceScope.launch(Dispatchers.Main) {
            isAppInForeground = inForeground
            if (inForeground) {
                setBubbleVisibility(false)
            } else {
                val isMonitored = checkIsMonitoredAppInForeground()
                val isResting = BreakSessionManager.isResting(this@FloatingBubbleService)
                val isExpired = BreakSessionManager.getSessionState(this@FloatingBubbleService) == BreakSessionState.EXPIRED
                if (isMonitored && !isResting && !isExpired) {
                    refreshScreenTimeBase()
                    setBubbleVisibility(controller.isEnabled())
                } else {
                    setBubbleVisibility(false)
                }
            }
            Log.d(
                TAG,
                "JedaIn inForeground=$inForeground -> bubble visibility=${bubbleView?.visibility == View.VISIBLE}",
            )
        }
    }

    private fun isSystemDarkMode(): Boolean {
        val nightModeFlags = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightModeFlags == Configuration.UI_MODE_NIGHT_YES
    }

    private fun getScreenWidth(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = windowManager.currentWindowMetrics
            windowMetrics.bounds.width()
        } else {
            resources.displayMetrics.widthPixels
        }
    }

    private fun getScreenHeight(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = windowManager.currentWindowMetrics
            windowMetrics.bounds.height()
        } else {
            resources.displayMetrics.heightPixels
        }
    }

    private var lastNotifiedScreenTimeText: String? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Bubble Waktu Layar JedaIn",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Menampilkan status pemantauan waktu layar aplikasi JedaIn"
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    private fun buildForegroundNotification(dailyScreenTimeMillis: Long = 0L): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val hideIntent = Intent(this, FloatingBubbleService::class.java).apply {
            action = ACTION_HIDE_BUBBLE
        }
        val hidePendingIntent = PendingIntent.getService(
            this,
            1,
            hideIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val formattedDuration = formatDailyScreenTimeNotification(dailyScreenTimeMillis)
        val contentText = "⏱ Total waktu layar hari ini: $formattedDuration"
        val bigText = "⏱ Total waktu layar hari ini\n$formattedDuration"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_jedain)
            .setContentTitle("JedaIn")
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setContentIntent(pendingIntent)
            .addAction(0, "Sembunyikan", hidePendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateForegroundNotification(dailyScreenTimeMillis: Long) {
        val formattedDuration = formatDailyScreenTimeNotification(dailyScreenTimeMillis)
        if (formattedDuration == lastNotifiedScreenTimeText) {
            return
        }
        lastNotifiedScreenTimeText = formattedDuration
        val notification = buildForegroundNotification(dailyScreenTimeMillis)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.notify(NOTIFICATION_ID, notification)
    }

    private fun refreshNotificationScreenTime() {
        serviceScope.launch(Dispatchers.IO) {
            val totalMillis = repository.getTotalDeviceScreenTimeMillis()
            withContext(Dispatchers.Main) {
                updateForegroundNotification(totalMillis)
            }
        }
    }

    companion object {
        const val TAG = "JedaInBubble"
        const val ACTION_HIDE_BUBBLE = "com.example.jedain.ACTION_HIDE_BUBBLE"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "jedain_floating_bubble_channel"

        const val BORDER_NEUTRAL_LIGHT = 0xFFBDBDBD.toInt()
        const val BORDER_NEUTRAL_DARK = 0xFF4A4A4A.toInt()

        private var isAppInForeground: Boolean = false
        private var instanceRef: WeakReference<FloatingBubbleService>? = null

        fun start(context: Context) {
            val controller = FloatingBubbleController(context)
            if (!controller.isEnabled()) {
                Log.d(TAG, "Cannot start FloatingBubbleService: bubble is disabled in settings")
                return
            }
            if (!PermissionHelper.hasOverlayPermission(context)) {
                Log.d(TAG, "Cannot start FloatingBubbleService: overlay permission not granted")
                return
            }
            val intent = Intent(context, FloatingBubbleService::class.java)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start FloatingBubbleService", e)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, FloatingBubbleService::class.java)
            context.stopService(intent)
        }

        fun setAppInForeground(inForeground: Boolean) {
            isAppInForeground = inForeground
            instanceRef?.get()?.updateAppVisibility(inForeground)
        }

        internal fun formatTimerDigital(millis: Long): String {
            if (millis <= 0L) return "00s"
            val totalSeconds = millis / 1000L
            val hours = totalSeconds / 3600L
            val minutes = (totalSeconds % 3600L) / 60L
            val seconds = totalSeconds % 60L

            return when {
                hours > 0L -> String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
                minutes > 0L -> String.format(Locale.US, "%02d:%02d", minutes, seconds)
                else -> String.format(Locale.US, "%02ds", seconds)
            }
        }

        internal fun resolveBubbleBorderColor(
            ratio: Double,
            isDark: Boolean,
        ): Int {
            return when {
                ratio >= 1.0 -> {
                    if (isDark) DurationRedDark.toArgb() else DurationRedLight.toArgb()
                }
                ratio >= 0.8 -> {
                    if (isDark) DurationYellowDark.toArgb() else DurationYellowLight.toArgb()
                }
                else -> {
                    if (isDark) BORDER_NEUTRAL_DARK else BORDER_NEUTRAL_LIGHT
                }
            }
        }
    }
}
