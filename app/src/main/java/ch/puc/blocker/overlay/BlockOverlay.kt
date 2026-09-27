package ch.puc.blocker.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import ch.puc.blocker.domain.Decision
import ch.puc.blocker.domain.Reason
import java.text.DateFormat
import java.util.Date

/**
 * Full-screen TYPE_APPLICATION_OVERLAY card explaining a block. The service closes the blocked app
 * and shows this over the home screen until "OK" ([onDismiss]) or the block ends.
 * The service calls [show] every tick, which keeps the countdown live.
 * Plain Views keep it free of the lifecycle plumbing a ComposeView needs inside a Service.
 */
@SuppressLint("SetTextI18n")
class BlockOverlay(private val context: Context, private val onDismiss: () -> Unit = {}) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private var root: LinearLayout? = null
    private lateinit var title: TextView
    private lateinit var countdown: TextView
    private lateinit var detail: TextView
    private lateinit var quote: TextView

    private val quotes = listOf(
        "Boredom is where good ideas start.",
        "You decided this earlier. Trust that version of you.",
        "Take three slow breaths before you pick up the phone again.",
        "Go for a walk, drink some water or call a friend.",
        "Lies das Buch, nicht den Feed.",
    )

    fun show(appLabel: String, block: Decision.Block) {
        if (!Settings.canDrawOverlays(context)) return
        if (root == null) {
            root = create().also { wm.addView(it, params()) }
            quote.text = quotes.random()
        }
        val until = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(block.untilMs))
        title.text = when (block.reason) {
            Reason.MORNING -> "Good morning ☀"
            Reason.COOLDOWN -> "Take a break"
            Reason.DAILY_CAP -> "That's enough for today"
        }
        detail.text = when (block.reason) {
            Reason.MORNING -> "$appLabel is locked for the first part of your day, so it was closed.\nUnlocks at $until."
            Reason.COOLDOWN -> "You've hit your session limit for $appLabel, so it was closed.\nIt unlocks again at $until."
            Reason.DAILY_CAP -> "You've used your daily time for $appLabel, so it was closed.\nIt resets at midnight."
        }
        countdown.text = format(block.untilMs - System.currentTimeMillis())
    }

    fun hide() {
        root?.let { runCatching { wm.removeView(it) } }
        root = null
    }

    private fun format(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0)
        val h = total / 3600; val m = total % 3600 / 60; val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    private fun text(size: Float, color: Int, bold: Boolean = false) = TextView(context).apply {
        textSize = size
        setTextColor(color)
        gravity = Gravity.CENTER
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun create() = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.rgb(28, 25, 58), Color.rgb(10, 10, 16)))
        setPadding(72, 72, 72, 72)
        val gap = { v: android.view.View, top: Int -> addView(v, LinearLayout.LayoutParams(-2, -2).apply { topMargin = top }) }

        title = text(28f, Color.WHITE, bold = true).also { gap(it, 0) }
        countdown = text(56f, Color.rgb(180, 170, 255), bold = true).also { gap(it, 32) }
        detail = text(16f, Color.rgb(210, 210, 225)).also { gap(it, 24) }
        quote = text(14f, Color.rgb(150, 150, 170)).apply { setTypeface(typeface, Typeface.ITALIC) }.also { gap(it, 48) }
        gap(Button(context).apply {
            text = "OK"
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply { cornerRadius = 64f; setColor(Color.rgb(98, 84, 220)) }
            setPadding(72, 32, 72, 32)
            setOnClickListener { hide(); onDismiss() }
        }, 56)
    }

    private fun params() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.OPAQUE,
    )
}

/**
 * Sends the user to the launcher, which moves whatever app is open to the background.
 * NO_USER_ACTION suppresses the "user is leaving" signal apps use to auto-enter picture-in-picture.
 */
fun Context.goHome() = startActivity(
    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
)
