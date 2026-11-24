package dev.tvnexus.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.tvnexus.core.Capability
import dev.tvnexus.core.Finding
import dev.tvnexus.core.Severity
import dev.tvnexus.core.Support

/** Tiny programmatic UI kit: dark, large type, strong D-pad focus ring, no touch dependency. */
object Ui {
    val BG = Color.parseColor("#0E1116")
    val PANEL = Color.parseColor("#161B22")
    val TEXT = Color.parseColor("#E6EDF3")
    val DIM = Color.parseColor("#8B949E")
    val ACCENT = Color.parseColor("#4FC3F7")
    val OK = Color.parseColor("#3FB950")
    val WARN = Color.parseColor("#D29922")
    val BAD = Color.parseColor("#F85149")

    fun dp(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density + 0.5f).toInt()

    fun focusBg(ctx: Context, fill: Int): StateListDrawable {
        fun shape(stroke: Int, width: Int) = GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(ctx, 6).toFloat()
            setStroke(dp(ctx, width), stroke)
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), shape(ACCENT, 3))
            addState(intArrayOf(), shape(fill, 3))
        }
    }

    fun text(ctx: Context, s: CharSequence, sp: Float = 20f, color: Int = TEXT, mono: Boolean = false): TextView =
        TextView(ctx).apply {
            text = s
            textSize = sp
            setTextColor(color)
            if (mono) typeface = Typeface.MONOSPACE
            setLineSpacing(0f, 1.15f)
        }

    fun heading(ctx: Context, s: String): TextView =
        text(ctx, s, 30f, ACCENT).apply {
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(ctx, 16), 0, dp(ctx, 8))
        }

    fun column(ctx: Context): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(ctx, 32), dp(ctx, 16), dp(ctx, 32), dp(ctx, 32))
    }

    fun scroll(content: View): ScrollView = ScrollView(content.context).apply {
        setBackgroundColor(BG)
        isFocusable = false
        isFillViewport = true
        addView(content, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    fun button(ctx: Context, label: String, enabled: Boolean = true, onClick: () -> Unit): Button =
        Button(ctx).apply {
            text = label
            textSize = 20f
            isAllCaps = false
            setTextColor(if (enabled) TEXT else DIM)
            background = focusBg(ctx, PANEL)
            isFocusable = true
            isEnabled = enabled
            setPadding(dp(ctx, 20), dp(ctx, 12), dp(ctx, 20), dp(ctx, 12))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(ctx, 8) }
            setOnClickListener { onClick() }
        }

    /** A focusable block so D-pad can step through (and therefore scroll to) every piece of content. */
    fun card(ctx: Context, vararg children: View): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        background = focusBg(ctx, PANEL)
        isFocusable = true
        setPadding(dp(ctx, 20), dp(ctx, 14), dp(ctx, 20), dp(ctx, 14))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(ctx, 8) }
        children.forEach { addView(it) }
    }

    fun severityColor(s: Severity): Int = when (s) {
        Severity.OK -> OK
        Severity.INFO -> ACCENT
        Severity.WARN -> WARN
        Severity.BAD -> BAD
    }

    fun supportColor(s: Support): Int = when (s) {
        Support.SUPPORTED -> OK
        Support.PARTIAL -> WARN
        Support.UNSUPPORTED -> BAD
        else -> WARN
    }

    fun findingCard(ctx: Context, f: Finding): View = card(
        ctx,
        text(ctx, "[${f.severity.label}] [${f.confidence}]  ${f.title}", 22f, severityColor(f.severity)).apply {
            typeface = Typeface.DEFAULT_BOLD
        },
        text(ctx, f.evidence, 17f, TEXT, mono = true).apply { setPadding(0, dp(ctx, 6), 0, dp(ctx, 6)) },
        text(ctx, f.explanation, 18f, DIM)
    )

    fun capabilityCard(ctx: Context, caps: List<Capability>): View {
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        caps.forEach { c ->
            col.addView(text(ctx, "${c.name}  —  ${c.support.label}", 20f, supportColor(c.support)).apply {
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, dp(ctx, 8), 0, 0)
            })
            col.addView(text(ctx, c.detail, 17f, DIM))
        }
        return card(ctx, col)
    }
}
