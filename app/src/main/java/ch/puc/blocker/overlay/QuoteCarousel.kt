package ch.puc.blocker.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.MotionEvent
import android.widget.LinearLayout
import android.widget.TextView
import ch.puc.blocker.domain.Quotes
import kotlin.math.abs

/**
 * A few quotes, one at a time: slides to the next every [AUTO_MS], or swipe left/right.
 * Large plain text with the source on its own line, so it reads at a glance.
 */
@SuppressLint("ViewConstructor", "SetTextI18n", "ClickableViewAccessibility")
class QuoteCarousel(context: Context) : LinearLayout(context) {
    companion object { private const val AUTO_MS = 10_000L }

    private val density = resources.displayMetrics.density
    // Serif in warm cream, under a big quote mark, so the quote reads apart from the card's sans-serif text.
    private val mark = TextView(context).apply {
        text = "“"
        textSize = 44f
        typeface = Typeface.SERIF
        setTextColor(Color.rgb(214, 186, 130))
        gravity = Gravity.CENTER
        includeFontPadding = false
    }
    private val text = TextView(context).apply {
        textSize = 20f
        typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        setTextColor(Color.rgb(246, 236, 214))
        gravity = Gravity.CENTER
        setLineSpacing(0f, 1.3f)
    }
    private val source = TextView(context).apply {
        textSize = 14f
        typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
        setTextColor(Color.rgb(165, 165, 185))
        gravity = Gravity.CENTER
    }
    private val dots = TextView(context).apply {
        textSize = 12f
        setTextColor(Color.rgb(165, 165, 185))
        gravity = Gravity.CENTER
        letterSpacing = 0.3f
    }
    private var quotes: List<Quotes.Quote> = emptyList()
    private var index = 0
    /** One slide at a time: a second swipe during the animation would skip a quote. */
    private var sliding = false
    private val advance = Runnable { go(index + 1, fromRight = true) }

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        val pad = (8 * density).toInt()
        setPadding(pad, pad, pad, pad)
        addView(mark, LayoutParams(-1, -2))
        addView(text, LayoutParams(-1, -2))
        addView(source, LayoutParams(-1, -2).apply { topMargin = (10 * density).toInt() })
        addView(dots, LayoutParams(-1, -2).apply { topMargin = (14 * density).toInt() })
        var downX = 0f
        setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> downX = e.rawX
                MotionEvent.ACTION_UP -> {
                    val dx = e.rawX - downX
                    if (abs(dx) > 48 * density) go(if (dx < 0) index + 1 else index - 1, fromRight = dx < 0)
                }
            }
            true
        }
    }

    fun show(list: List<Quotes.Quote>) {
        quotes = list
        index = 0
        render()
    }

    /** Slides out, swaps the quote, slides in from the other side; restarts the 10 s clock. */
    private fun go(i: Int, fromRight: Boolean) {
        if (quotes.size < 2 || sliding) return
        sliding = true
        index = (i + quotes.size) % quotes.size
        val shift = width * 0.3f * if (fromRight) 1 else -1
        animate().translationX(-shift).alpha(0f).setDuration(160).withEndAction {
            render()
            translationX = shift
            animate().translationX(0f).alpha(1f).setDuration(220).withEndAction { sliding = false }.start()
        }.start()
    }

    private fun render() {
        val q = quotes.getOrNull(index) ?: return
        text.text = q.text
        source.text = if (q.source.isEmpty()) "" else "— ${q.source}"
        source.visibility = if (q.source.isEmpty()) GONE else VISIBLE
        dots.text = quotes.indices.joinToString(" ") { if (it == index) "●" else "○" }
        removeCallbacks(advance)
        postDelayed(advance, AUTO_MS)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(advance)
        super.onDetachedFromWindow()
    }
}
