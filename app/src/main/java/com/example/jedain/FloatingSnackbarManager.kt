package com.example.jedain

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt

/**
 * Floating Snackbar Reminder yang muncul di bagian atas layar di atas aplikasi yang dipantau (TikTok, Instagram, dll.).
 * Menggunakan konsep visual dan posisi menyerupai WhatsApp Incoming Call Heads-up Overlay:
 * - Muncul di bagian atas layar (Heads-up).
 * - Berbentuk rounded card compact dengan identitas visual JedaIn.
 * - Non-blocking: Pengguna tetap bisa berinteraksi dengan aplikasi di belakangnya.
 * - Tipe 1: Pengingat 3 menit scrolling (auto-dismiss 5 detik).
 * - Tipe 2: Countdown 60 detik sebelum batas penggunaan (countdown live & progress bar).
 */
object FloatingSnackbarManager {

    private const val TAG = "JedaInFloatingSnackbar"
    const val AUTO_DISMISS_DURATION_MS = 7000L

    enum class SnackbarType {
        INITIAL_3MIN,
        COUNTDOWN_60SEC,
        INTENTION_REMINDER,
    }

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private var windowManager: WindowManager? = null

    private var rootView: FrameLayout? = null
    private var cardView: LinearLayout? = null
    private var tvHeaderTitle: TextView? = null
    private var tvHeadline: TextView? = null
    private var tvSubtitle: TextView? = null
    private var tvCountdownBadge: TextView? = null
    private var countdownProgressContainer: LinearLayout? = null
    private var progressBar: ProgressBar? = null
    private var btnDismiss: TextView? = null
    private var btnTakeBreak: TextView? = null
    private var btnClose: TextView? = null

    private var currentType: SnackbarType? = null
    private var isViewAttached = false
    private var isAnimatingOut = false

    private var onDismissCallback: (() -> Unit)? = null
    private var onTakeBreakCallback: (() -> Unit)? = null

    private val autoDismissRunnable = Runnable {
        Log.d(TAG, "Auto-dismissing floating snackbar after $AUTO_DISMISS_DURATION_MS ms")
        hide()
    }

    fun isVisible(): Boolean = isViewAttached && !isAnimatingOut

    fun getCurrentType(): SnackbarType? = currentType

    /**
     * Menampilkan Floating Snackbar Tipe 1: Pengingat 3 Menit Menjelang Batas Sesi (03:00).
     */
    fun showInitialReminder(
        context: Context,
        onDismiss: () -> Unit = {},
        onTakeBreak: () -> Unit = {},
    ) {
        mainHandler.post {
            if (!PermissionHelper.hasOverlayPermission(context)) {
                Log.w(TAG, "Overlay permission not granted, skipping floating snackbar")
                return@post
            }

            onDismissCallback = onDismiss
            onTakeBreakCallback = onTakeBreak

            ensureViewCreated(context)
            setupInitial3MinUI()

            attachAndAnimateIn(context, SnackbarType.INITIAL_3MIN)

            // Auto-dismiss setelah 7 detik
            mainHandler.removeCallbacks(autoDismissRunnable)
            mainHandler.postDelayed(autoDismissRunnable, AUTO_DISMISS_DURATION_MS)
        }
    }

    /**
     * Menampilkan Floating Snackbar Tipe 2: Countdown 60 Detik Sebelum Batas Penggunaan.
     */
    fun showCountdownReminder(
        context: Context,
        secondsLeft: Int,
        onDismiss: () -> Unit = {},
        onTakeBreak: () -> Unit = {},
    ) {
        mainHandler.post {
            if (!PermissionHelper.hasOverlayPermission(context)) {
                Log.w(TAG, "Overlay permission not granted, skipping countdown snackbar")
                return@post
            }

            onDismissCallback = onDismiss
            onTakeBreakCallback = onTakeBreak

            ensureViewCreated(context)
            setupCountdownUI(secondsLeft)

            if (isViewAttached && currentType == SnackbarType.COUNTDOWN_60SEC) {
                // Hanya update nilainya jika view sudah terpasang
                updateCountdown(secondsLeft)
            } else {
                attachAndAnimateIn(context, SnackbarType.COUNTDOWN_60SEC)
                // Nonaktifkan auto-dismiss: snackbar countdown 1 menit tetap tampil sampai ditutup manual
                mainHandler.removeCallbacks(autoDismissRunnable)
            }
        }
    }

    /**
     * Menampilkan Floating Snackbar Tipe 3: Pengingat Niat Sesi (muncul 1x saat baru masuk aplikasi tujuan).
     * Auto-dismiss setelah 7 detik, dapat ditutup manual.
     * Tidak boleh tampil bersamaan dengan snackbar 3 menit atau 1 menit (prioritas lebih rendah).
     */
    fun showIntentionReminder(
        context: Context,
        intentChoice: String,
        reminderMessage: String,
        onDismiss: () -> Unit = {},
    ) {
        mainHandler.post {
            if (!PermissionHelper.hasOverlayPermission(context)) {
                Log.w(TAG, "Overlay permission not granted, skipping intention snackbar")
                return@post
            }

            // Aturan prioritas: Jika snackbar 3 menit atau countdown 60 detik sedang aktif, abaikan
            if (isViewAttached && (currentType == SnackbarType.INITIAL_3MIN || currentType == SnackbarType.COUNTDOWN_60SEC)) {
                Log.d(TAG, "Higher priority snackbar ($currentType) is active, skipping intention snackbar")
                return@post
            }

            onDismissCallback = onDismiss
            onTakeBreakCallback = null

            ensureViewCreated(context)
            setupIntentionUI(intentChoice, reminderMessage)

            attachAndAnimateIn(context, SnackbarType.INTENTION_REMINDER)

            // Auto-dismiss setelah 7 detik
            mainHandler.removeCallbacks(autoDismissRunnable)
            mainHandler.postDelayed(autoDismissRunnable, AUTO_DISMISS_DURATION_MS)
        }
    }

    /**
     * Memperbarui angka hitung mundur dan progress bar secara real-time.
     */
    fun updateCountdown(secondsLeft: Int) {
        mainHandler.post {
            if (!isViewAttached || currentType != SnackbarType.COUNTDOWN_60SEC) {
                return@post
            }

            val clampedSeconds = secondsLeft.coerceIn(0, 60)
            val timeString = formatCountdownText(clampedSeconds)
            tvCountdownBadge?.text = timeString
            progressBar?.progress = clampedSeconds

            if (clampedSeconds == 0) {
                tvCountdownBadge?.setTextColor(Color.parseColor("#EF4444"))
                tvHeadline?.text = "Waktumu sudah habis!"
                tvSubtitle?.text = "Waktu sesi penggunaan telah berakhir. Silakan ambil jeda sekarang."
            } else if (clampedSeconds <= 10) {
                tvCountdownBadge?.setTextColor(Color.parseColor("#EF4444")) // Merah lembut saat kritis
                tvHeadline?.text = "Waktumu hampir habis!"
                tvSubtitle?.text = "Bersiap untuk mengambil jeda. Selesaikan aktivitasmu."
            } else {
                tvCountdownBadge?.setTextColor(Color.parseColor("#FFFFFF"))
                tvHeadline?.text = "Waktumu hampir habis!"
                tvSubtitle?.text = "Bersiap untuk mengambil jeda. Selesaikan aktivitasmu."
            }
        }
    }

    fun formatCountdownText(seconds: Int): String {
        val clamped = seconds.coerceAtLeast(0)
        val m = clamped / 60
        val s = clamped % 60
        return String.format(java.util.Locale.US, "%02d:%02d", m, s)
    }

    /**
     * Menyembunyikan snackbar dengan animasi slide up dan fade out.
     */
    fun hide() {
        mainHandler.post {
            mainHandler.removeCallbacks(autoDismissRunnable)
            if (!isViewAttached || isAnimatingOut) {
                return@post
            }

            val card = cardView
            val wm = windowManager
            val root = rootView

            if (card == null || wm == null || root == null) {
                removeViewImmediate()
                return@post
            }

            isAnimatingOut = true
            val height = card.height.takeIf { it > 0 } ?: (140 * root.resources.displayMetrics.density).roundToInt()

            card.animate()
                .translationY(-height.toFloat())
                .alpha(0f)
                .setDuration(220L)
                .setInterpolator(AccelerateInterpolator())
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        removeViewImmediate()
                    }

                    override fun onAnimationCancel(animation: Animator) {
                        removeViewImmediate()
                    }
                })
                .start()
        }
    }

    /**
     * Menghapus view langsung tanpa animasi (misal saat service destroy atau screen off).
     */
    fun hideImmediate() {
        mainHandler.post {
            mainHandler.removeCallbacks(autoDismissRunnable)
            removeViewImmediate()
        }
    }

    private fun removeViewImmediate() {
        isAnimatingOut = false
        val wm = windowManager
        val root = rootView
        if (isViewAttached && wm != null && root != null) {
            runCatching {
                wm.removeView(root)
            }.onFailure { e ->
                Log.e(TAG, "Error removing FloatingSnackbar from WindowManager", e)
            }
        }
        isViewAttached = false
        currentType = null
        rootView = null
        cardView = null
        tvHeaderTitle = null
        tvHeadline = null
        tvSubtitle = null
        tvCountdownBadge = null
        countdownProgressContainer = null
        progressBar = null
        btnDismiss = null
        btnTakeBreak = null
        btnClose = null
    }

    private fun ensureViewCreated(context: Context) {
        if (windowManager == null) {
            windowManager = context.applicationContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        }

        if (rootView != null) {
            return
        }

        val density = context.resources.displayMetrics.density

        // Root container untuk padding dan animasai
        val root = FrameLayout(context)
        rootView = root

        // Card Container dengan estetika minimalis modern JedaIn
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 20f * density
                setColor(Color.parseColor("#18181B")) // Dark surface solid/semi-solid
                setStroke((1f * density).roundToInt(), Color.parseColor("#2E2E35"))
            }
            background = bg
            setPadding(
                (16 * density).roundToInt(),
                (14 * density).roundToInt(),
                (16 * density).roundToInt(),
                (14 * density).roundToInt(),
            )
            elevation = 12f * density
        }
        cardView = card
        root.addView(card, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))

        // --- 1. Header Row (Icon + App Name + Live Countdown Badge) ---
        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val iconBadge = ImageView(context).apply {
            val iconBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 6f * density
                setColor(Color.parseColor("#27272A"))
            }
            background = iconBg
            setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_stat_jedain))
            setColorFilter(Color.WHITE)
            setPadding(
                (4 * density).roundToInt(),
                (4 * density).roundToInt(),
                (4 * density).roundToInt(),
                (4 * density).roundToInt(),
            )
        }
        val iconSizePx = (24 * density).roundToInt()
        headerRow.addView(iconBadge, LinearLayout.LayoutParams(iconSizePx, iconSizePx))

        val titleView = TextView(context).apply {
            text = "JedaIn"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding((8 * density).roundToInt(), 0, 0, 0)
        }
        tvHeaderTitle = titleView
        headerRow.addView(titleView, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val countdownBadge = TextView(context).apply {
            text = "01:00"
            textSize = 15f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.WHITE)
            visibility = View.GONE
        }
        tvCountdownBadge = countdownBadge
        headerRow.addView(countdownBadge, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // Tombol Close (X) di pojok kanan atas kartu yang jelas dan mudah diakses
        val closeBtn = TextView(context).apply {
            text = "✕"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#A1A1AA"))
            gravity = Gravity.CENTER
            val rippleColor = ColorStateList.valueOf(Color.parseColor("#444444"))
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#27272A"))
            }
            background = RippleDrawable(rippleColor, bg, null)
            isClickable = true
            isFocusable = false
            contentDescription = "Tutup pengingat"
            setOnClickListener {
                Log.d(TAG, "Tombol close (X) diklik")
                hide()
                onDismissCallback?.invoke()
            }
        }
        btnClose = closeBtn
        val closeBtnSizePx = (24 * density).roundToInt()
        val closeMarginStart = (10 * density).roundToInt()
        val closeParams = LinearLayout.LayoutParams(closeBtnSizePx, closeBtnSizePx).apply {
            marginStart = closeMarginStart
        }
        headerRow.addView(closeBtn, closeParams)

        card.addView(headerRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // Spacer kecil
        val spacerAfterHeader = View(context)
        card.addView(spacerAfterHeader, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (8 * density).roundToInt()))

        // --- 2. Headline & Subtitle ---
        val headline = TextView(context).apply {
            textSize = 14.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setLineSpacing(0f, 1.15f)
        }
        tvHeadline = headline
        card.addView(headline, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val subtitle = TextView(context).apply {
            textSize = 12.5f
            setTextColor(Color.parseColor("#A1A1AA"))
            setPadding(0, (3 * density).roundToInt(), 0, 0)
            setLineSpacing(0f, 1.2f)
        }
        tvSubtitle = subtitle
        card.addView(subtitle, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // --- 3. Countdown Progress Bar Section ---
        val progressContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(0, (10 * density).roundToInt(), 0, (2 * density).roundToInt())
        }
        countdownProgressContainer = progressContainer

        val pBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            val bgTrack = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 2f * density
                setColor(Color.parseColor("#27272A"))
            }
            val fgProgress = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 2f * density
                setColor(Color.parseColor("#FFFFFF"))
            }
            val clipFg = ClipDrawable(fgProgress, Gravity.START, ClipDrawable.HORIZONTAL)
            progressDrawable = LayerDrawable(arrayOf(bgTrack, clipFg)).apply {
                setId(0, android.R.id.background)
                setId(1, android.R.id.progress)
            }
            max = 60
            progress = 60
        }
        progressBar = pBar
        val progressHeightPx = (4 * density).roundToInt()
        progressContainer.addView(pBar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, progressHeightPx))
        card.addView(progressContainer, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // Spacer sebelum tombol
        val spacerBeforeActions = View(context)
        card.addView(spacerBeforeActions, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (12 * density).roundToInt()))

        // --- 4. Action Buttons Row (Tutup & Ambil jeda) ---
        val buttonsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }

        // Spacer pendorong ke kanan
        val buttonsPushSpacer = View(context)
        buttonsRow.addView(buttonsPushSpacer, LinearLayout.LayoutParams(0, 1, 1f))

        // Tombol Sekunder: "Tutup"
        val dismissBtn = TextView(context).apply {
            text = "Tutup"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#D4D4D8"))
            gravity = Gravity.CENTER

            val normalBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 50f * density
                setColor(Color.parseColor("#27272A"))
                setStroke((1f * density).roundToInt(), Color.parseColor("#3F3F46"))
            }
            val rippleColor = ColorStateList.valueOf(Color.parseColor("#444444"))
            background = RippleDrawable(rippleColor, normalBg, null)

            setPadding(
                (14 * density).roundToInt(),
                (7 * density).roundToInt(),
                (14 * density).roundToInt(),
                (7 * density).roundToInt(),
            )
            isClickable = true
            isFocusable = false

            setOnClickListener {
                Log.d(TAG, "Tombol 'Tutup' diklik")
                hide()
                onDismissCallback?.invoke()
            }
        }
        btnDismiss = dismissBtn
        buttonsRow.addView(dismissBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val buttonGap = View(context)
        buttonsRow.addView(buttonGap, LinearLayout.LayoutParams((10 * density).roundToInt(), 1))

        // Tombol Primer: "Ambil jeda"
        val takeBreakBtn = TextView(context).apply {
            text = "Ambil jeda"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#121212"))
            gravity = Gravity.CENTER

            val normalBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 50f * density
                setColor(Color.WHITE)
            }
            val rippleColor = ColorStateList.valueOf(Color.parseColor("#CCCCCC"))
            background = RippleDrawable(rippleColor, normalBg, null)

            setPadding(
                (16 * density).roundToInt(),
                (7 * density).roundToInt(),
                (16 * density).roundToInt(),
                (7 * density).roundToInt(),
            )
            isClickable = true
            isFocusable = false

            setOnClickListener {
                Log.d(TAG, "Tombol 'Ambil jeda' diklik")
                hide()
                onTakeBreakCallback?.invoke()
            }
        }
        btnTakeBreak = takeBreakBtn
        buttonsRow.addView(takeBreakBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        card.addView(buttonsRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    private fun setupInitial3MinUI() {
        tvHeaderTitle?.text = "JedaIn"
        tvCountdownBadge?.visibility = View.GONE
        countdownProgressContainer?.visibility = View.GONE

        tvHeadline?.text = "Waktunya segera jeda!"
        tvSubtitle?.text = "Tinggal 3 menit lagi. Selesaikan aktivitasmu dan bersiap untuk beristirahat sejenak dari layar."

        btnDismiss?.visibility = View.VISIBLE
        btnDismiss?.text = "Tutup"
        btnTakeBreak?.visibility = View.VISIBLE
        btnTakeBreak?.text = "Ambil jeda"
    }

    private fun setupCountdownUI(secondsLeft: Int) {
        tvHeaderTitle?.text = "JedaIn"
        val clamped = secondsLeft.coerceIn(0, 60)
        tvCountdownBadge?.visibility = View.VISIBLE
        tvCountdownBadge?.text = formatCountdownText(clamped)

        countdownProgressContainer?.visibility = View.VISIBLE
        progressBar?.progress = clamped

        btnDismiss?.visibility = View.VISIBLE
        btnDismiss?.text = "Tutup"
        btnTakeBreak?.visibility = View.VISIBLE
        btnTakeBreak?.text = "Ambil jeda"

        if (clamped == 0) {
            tvCountdownBadge?.setTextColor(Color.parseColor("#EF4444"))
            tvHeadline?.text = "Waktumu sudah habis!"
            tvSubtitle?.text = "Waktu sesi penggunaan telah berakhir. Silakan ambil jeda sekarang."
        } else if (clamped <= 10) {
            tvCountdownBadge?.setTextColor(Color.parseColor("#EF4444"))
            tvHeadline?.text = "Waktumu hampir habis!"
            tvSubtitle?.text = "Bersiap untuk mengambil jeda. Selesaikan aktivitasmu."
        } else {
            tvCountdownBadge?.setTextColor(Color.parseColor("#FFFFFF"))
            tvHeadline?.text = "Waktumu hampir habis!"
            tvSubtitle?.text = "Bersiap untuk mengambil jeda. Selesaikan aktivitasmu."
        }
    }

    private fun setupIntentionUI(intentChoice: String, reminderMessage: String) {
        tvHeaderTitle?.text = "JedaIn · Niat Kamu"
        tvCountdownBadge?.visibility = View.GONE
        countdownProgressContainer?.visibility = View.GONE

        tvHeadline?.text = intentChoice
        tvSubtitle?.text = reminderMessage

        btnDismiss?.visibility = View.VISIBLE
        btnDismiss?.text = "Tutup"
        btnTakeBreak?.visibility = View.GONE
    }

    private fun attachAndAnimateIn(context: Context, type: SnackbarType) {
        val wm = windowManager ?: return
        val root = rootView ?: return
        val card = cardView ?: return

        currentType = type

        val density = context.resources.displayMetrics.density
        val screenWidth = context.resources.displayMetrics.widthPixels
        val horizontalMargin = (14 * density).roundToInt()
        val cardWidth = screenWidth - (horizontalMargin * 2)

        val statusBarHeight = getStatusBarHeight(context)
        val topOffset = statusBarHeight + (8 * density).roundToInt()

        val params = WindowManager.LayoutParams(
            cardWidth,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = topOffset
        }

        if (!isViewAttached) {
            card.alpha = 0f
            card.translationY = - (120 * density)

            runCatching {
                wm.addView(root, params)
                isViewAttached = true
                isAnimatingOut = false
                Log.d(TAG, "FloatingSnackbar attached to WindowManager with width=$cardWidth, topOffset=$topOffset, type=$type")

                card.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(280L)
                    .setInterpolator(DecelerateInterpolator())
                    .setListener(null)
                    .start()
            }.onFailure { e ->
                Log.e(TAG, "Failed to attach FloatingSnackbar to WindowManager", e)
                isViewAttached = false
            }
        } else {
            runCatching {
                wm.updateViewLayout(root, params)
            }
        }
    }

    fun getStatusBarHeight(context: Context): Int {
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) {
            context.resources.getDimensionPixelSize(resourceId)
        } else {
            (28 * context.resources.displayMetrics.density).roundToInt()
        }
    }
}
