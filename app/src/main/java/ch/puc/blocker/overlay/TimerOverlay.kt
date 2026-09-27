package ch.puc.blocker.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import ch.puc.blocker.domain.Remaining
import kotlin.math.abs

/**
 * Compact countdown bubble docked to the screen edge while a tracked app is allowed.
 * Drag it anywhere; on release it snaps to the nearest side. Tap to briefly show what it counts down to.
 * It never takes focus and only consumes touches on itself, so the app underneath stays usable.
 */
@SuppressLint("SetTextI18n", "ClickableViewAccessibility")
class TimerOverlay(private val context: Context) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private val density = context.resources.displayMetrics.density
    private fun dp(v: Int) = (v * density).toInt()

    private var view: TextView? = null
    private var expandedUntil = 0L
    private var onRight = true
    private val bg = GradientDrawable()
    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        y = (screenHeight() * 0.35f).toInt()
    }

    fun show(appLabel: String, r: Remaining, reward: Boolean = false) {
        if (!Settings.canDrawOverlays(context)) return
        val v = view ?: create().also {
            wm.addView(it, params)
            view = it
            it.post { snap(it) }
        }
        val s = (r.ms + 999) / 1000 // round up so it reads 0:00 exactly when the block starts
        val clock = if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
        val expanded = System.currentTimeMillis() < expandedUntil
        val note = when { reward -> "reward left"; r.dailyLimited -> "left today"; else -> "until break" }
        v.text = (if (reward) "🎁 " else "") + if (expanded) "$clock $note" else clock
        v.contentDescription = "$appLabel: $clock remaining"
        bg.setColor(
            when {
                reward -> Color.rgb(124, 77, 255)         // purple: earned reward time
                s <= 30 -> Color.rgb(224, 69, 123)       // last 30 s
                s <= 60 -> Color.rgb(232, 162, 0)        // last minute
                else -> Color.argb(215, 17, 17, 17)
            },
        )
        if (expanded != (v.tag == true)) { v.tag = expanded; v.post { snap(v) } } // width changed: stay on the edge
    }

    fun hide() {
        view?.let { wm.removeView(it) }
        view = null
    }

    private fun screenWidth() = wm.currentWindowMetrics.bounds.width()
    private fun screenHeight() = wm.currentWindowMetrics.bounds.height()

    /** Docks to the nearest side with only the inner corners rounded, like a tab. */
    private fun snap(v: View) {
        onRight = params.x + v.width / 2 > screenWidth() / 2
        params.x = if (onRight) screenWidth() - v.width else 0
        params.y = params.y.coerceIn(dp(48), screenHeight() - v.height - dp(48))
        val r = dp(18).toFloat()
        bg.cornerRadii = if (onRight) floatArrayOf(r, r, 0f, 0f, 0f, 0f, r, r) else floatArrayOf(0f, 0f, r, r, r, r, 0f, 0f)
        wm.updateViewLayout(v, params)
    }

    private fun create() = TextView(context).apply {
        setTextColor(Color.WHITE)
        textSize = 13f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        background = bg
        setPadding(dp(10), dp(7), dp(10), dp(7))
        setOnTouchListener(DragListener())
        params.x = screenWidth() // starts on the right edge; snap() corrects the exact position
    }

    private inner class DragListener : View.OnTouchListener {
        private var startX = 0; private var startY = 0; private var touchX = 0f; private var touchY = 0f
        private var moved = false
        override fun onTouch(v: View, e: MotionEvent): Boolean {
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x; startY = params.y; touchX = e.rawX; touchY = e.rawY; moved = false
                    bg.cornerRadius = dp(18).toFloat() // fully round while dragging
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - touchX; val dy = e.rawY - touchY
                    if (abs(dx) > dp(4) || abs(dy) > dp(4)) moved = true
                    params.x = startX + dx.toInt()
                    params.y = startY + dy.toInt()
                    wm.updateViewLayout(v, params)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!moved) expandedUntil = System.currentTimeMillis() + 3_000 // tap: show the label for 3 s
                    snap(v)
                }
            }
            return true
        }
    }
}
