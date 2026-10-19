package de.barkau.fotomail.wound

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/** Kleine Oberflächen-Bausteine in den FotoMail-Farben. */
internal object Ui {
    val INK = Color.parseColor("#3A4146")
    val MUTED = Color.parseColor("#5A6066")
    val PRIMARY = Color.parseColor("#4A5258")
    val ACCENT = Color.parseColor("#73906B")
    val ACCENT_LIGHT = Color.parseColor("#9CAF88")
    val SURFACE = Color.parseColor("#EDEFF0")
    val SOFT = Color.parseColor("#EAEFE1")
    val WARN = Color.parseColor("#A0522D")
    const val WHITE = Color.WHITE

    fun dp(c: Context, v: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.resources.displayMetrics).toInt()

    fun rounded(c: Context, fill: Int, stroke: Int = 0, radiusDp: Float = 12f): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(c, radiusDp).toFloat()
            if (stroke != 0) setStroke(dp(c, 1f), stroke)
        }

    fun vbox(c: Context): LinearLayout = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }

    fun hbox(c: Context): LinearLayout = LinearLayout(c).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    fun card(c: Context): LinearLayout = vbox(c).apply {
        background = rounded(c, WHITE, 0, 14f)
        val p = dp(c, 16f)
        setPadding(p, p, p, p)
    }

    fun text(c: Context, s: String, sp: Float = 15f, color: Int = INK, bold: Boolean = false): TextView =
        TextView(c).apply {
            text = s
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    fun button(c: Context, s: String, primary: Boolean, onClick: (View) -> Unit): Button =
        Button(c).apply {
            text = s
            isAllCaps = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(if (primary) WHITE else INK)
            background = rounded(c, if (primary) ACCENT else SOFT, 0, 10f)
            minHeight = dp(c, 52f)
            stateListAnimator = null
            setOnClickListener(onClick)
        }

    fun field(c: Context, hint: String, inputType: Int): EditText = EditText(c).apply {
        this.hint = hint
        this.inputType = inputType
        isSingleLine = true
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
        setTextColor(INK)
        background = rounded(c, WHITE, ACCENT_LIGHT, 8f)
        val p = dp(c, 12f)
        setPadding(p, p, p, p)
    }

    fun lp(c: Context, topDp: Float = 0f): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(c, topDp) }

    fun weight(c: Context, w: Float = 1f, sideDp: Float = 0f): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, w).apply {
            marginEnd = dp(c, sideDp)
        }
}

/**
 * Android 15 zeichnet Apps bis unter Status- und Navigationsleiste.
 * Gibt [top] oben und [bottom] unten zusätzlich den Abstand der Systemleisten.
 */
internal fun applySystemBarPadding(root: View, top: View, bottom: View) {
    val t = top.paddingTop
    val b = bottom.paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
        top.setPadding(top.paddingLeft, t + bars.top, top.paddingRight, top.paddingBottom)
        bottom.setPadding(bottom.paddingLeft, bottom.paddingTop, bottom.paddingRight, b + bars.bottom)
        insets
    }
}

/** Fügt eine Ansicht in voller Breite mit Abstand nach oben ein. */
internal fun LinearLayout.add(v: View, topDp: Float = 0f): View {
    addView(v, Ui.lp(context, topDp)); return v
}
