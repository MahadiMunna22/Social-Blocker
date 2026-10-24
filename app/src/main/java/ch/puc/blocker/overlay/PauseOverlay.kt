package ch.puc.blocker.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import ch.puc.blocker.domain.Quotes

/**
 * "Take a breath" card shown before each new session of a tracked app (the One Sec / ScreenZen idea).
 * Open only becomes tappable after [WAIT_S] seconds; Not now sends the user home.
 */
@SuppressLint("SetTextI18n")
class PauseOverlay(
    private val context: Context,
    private val onOpen: (packageName: String) -> Unit,
    private val onClose: () -> Unit,
) {
    companion object { private const val WAIT_S = 5 }

    private val wm = context.getSystemService(WindowManager::class.java)
    private var root: LinearLayout? = null
    private lateinit var count: TextView
    private lateinit var detail: TextView
    private lateinit var open: Button
    /** Set from the quote setting; 5 new quotes are picked each time the card appears. */
    var quoteSources = setOf(Quotes.Source.POPULAR)
    private lateinit var quotes: QuoteCarousel
    /** Package the card is for, or null when hidden. */
    var shownFor: String? = null
        private set

    fun show(packageName: String, label: String, opensLeft: Int?) {
        if (!Settings.canDrawOverlays(context) || shownFor == packageName) return
        hide()
        shownFor = packageName
        root = create().also { wm.addView(it, params()) }
        detail.text = "You're about to open $label." +
            (opensLeft?.let { "\n$it ${if (it == 1) "open" else "opens"} left today, including this one." } ?: "")
        quotes.show(Quotes.pick(context, quoteSources))
        tick(WAIT_S, label)
    }

    fun hide() {
        root?.let { runCatching { wm.removeView(it) } }
        root = null
        shownFor = null
    }

    /** Counts down once a second on the view itself, so it stops by itself when the card is removed. */
    private fun tick(left: Int, label: String) {
        val view = root ?: return
        count.text = if (left > 0) "$left" else "🌿"
        open.isEnabled = left <= 0
        open.alpha = if (left <= 0) 1f else 0.4f
        open.text = if (left <= 0) "Open $label" else "Breathe…"
        if (left > 0) view.postDelayed({ if (root === view) tick(left - 1, label) }, 1_000)
    }

    private fun text(size: Float, color: Int, bold: Boolean = false) = TextView(context).apply {
        textSize = size
        setTextColor(color)
        gravity = Gravity.CENTER
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun button(label: String, color: Int, onClick: () -> Unit) = Button(context).apply {
        text = label
        isAllCaps = false
        setTextColor(Color.WHITE)
        background = GradientDrawable().apply { cornerRadius = 64f; setColor(color) }
        setPadding(72, 32, 72, 32)
        setOnClickListener { onClick() }
    }

    private fun create() = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        background = ColorDrawable(Color.rgb(15, 30, 28))
        setPadding(72, 72, 72, 72)
        val gap = { v: android.view.View, top: Int -> addView(v, LinearLayout.LayoutParams(-2, -2).apply { topMargin = top }) }
        gap(text(28f, Color.WHITE, bold = true).apply { text = "Take a breath" }, 0)
        count = text(64f, Color.rgb(140, 220, 190), bold = true).also { gap(it, 32) }
        detail = text(16f, Color.rgb(210, 225, 220)).also { gap(it, 24) }
        quotes = QuoteCarousel(context).also { addView(it, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 32 }) }
        open = button("Breathe…", Color.rgb(46, 140, 110)) { shownFor?.let { pkg -> hide(); onOpen(pkg) } }.also { gap(it, 56) }
        // onClose first: Android 15+ only lets us open the home screen while our window is still visible.
        gap(button("Not now", Color.rgb(70, 70, 80)) { onClose(); hide() }, 24)
    }

    private fun params() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.OPAQUE,
    )
}
