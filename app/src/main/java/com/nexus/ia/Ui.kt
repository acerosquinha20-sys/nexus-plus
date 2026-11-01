package com.nexus.ia

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat

object C {
    val RED = Color.parseColor("#FF1E2D")
    val RED_DEEP = Color.parseColor("#9E0A15")
    val BLACK = Color.parseColor("#050506")
    val PANEL = Color.parseColor("#0E0B0C")
    val LINE = Color.parseColor("#4A1419")
    val SILVER = Color.parseColor("#E8EAEE")
    val DIM = Color.parseColor("#9A9CA4")
    val GREEN = Color.parseColor("#37E86B")
    val RAINBOW = intArrayOf(
        Color.parseColor("#FF3B30"), Color.parseColor("#FF9500"), Color.parseColor("#FFE600"),
        Color.parseColor("#34C759"), Color.parseColor("#00C7FF"), Color.parseColor("#5E5CE6"),
        Color.parseColor("#FF2D95")
    )
}

fun Context.dp(v: Float): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
fun Context.dp(v: Int): Int = dp(v.toFloat())

fun Context.toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

fun rounded(color: Int, radius: Float, stroke: Int = 0, strokeColor: Int = 0): GradientDrawable {
    val g = GradientDrawable()
    g.setColor(color)
    g.cornerRadius = radius
    if (stroke > 0) g.setStroke(stroke, strokeColor)
    return g
}

fun redGradient(radius: Float): GradientDrawable {
    val g = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(C.RED, C.RED_DEEP))
    g.cornerRadius = radius
    return g
}

fun View.pressable() {
    setOnTouchListener { v, e ->
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.94f).scaleY(0.94f).setDuration(80).start()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f).setDuration(140).start()
        }
        false
    }
}

fun View.clipRound(r: Float) {
    clipToOutline = true
    outlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(v: View, o: Outline) {
            o.setRoundRect(0, 0, v.width, v.height, r)
        }
    }
}

fun TextView.rainbow() {
    post {
        val w = paint.measureText(text.toString()).coerceAtLeast(1f)
        paint.shader = LinearGradient(0f, 0f, w, 0f, C.RAINBOW, null, Shader.TileMode.CLAMP)
        invalidate()
    }
}

fun View.shake() {
    ObjectAnimator.ofFloat(this, "translationX", 0f, 18f, -18f, 12f, -12f, 0f).setDuration(350).start()
}

fun View.breathe(amount: Float = 0.04f) {
    val a = ValueAnimator.ofFloat(0f, 1f)
    a.duration = 1800
    a.repeatCount = ValueAnimator.INFINITE
    a.repeatMode = ValueAnimator.REVERSE
    a.addUpdateListener {
        val s = 1f + amount * (it.animatedValue as Float)
        scaleX = s
        scaleY = s
    }
    addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {}
        override fun onViewDetachedFromWindow(v: View) { a.cancel() }
    })
    a.start()
}

fun Context.label(s: String, sp: Float, color: Int = C.SILVER, bold: Boolean = false): TextView {
    val t = TextView(this)
    t.text = s
    t.textSize = sp
    t.setTextColor(color)
    t.typeface = Typeface.create("sans-serif-condensed", if (bold) Typeface.BOLD_ITALIC else Typeface.NORMAL)
    return t
}

fun Context.nexusButton(
    text: String,
    icon: Int,
    sp: Float = 14f,
    outline: Boolean = false,
    onClick: () -> Unit
): TextView {
    val t = TextView(this)
    t.text = text.uppercase()
    t.setTextColor(Color.WHITE)
    t.textSize = sp
    t.typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC)
    t.letterSpacing = 0.06f
    t.gravity = Gravity.CENTER
    t.compoundDrawablePadding = dp(8)
    val d = ContextCompat.getDrawable(this, icon)!!.mutate()
    val sz = dp(sp * 1.5f)
    d.setBounds(0, 0, sz, sz)
    d.setTint(Color.WHITE)
    t.setCompoundDrawables(d, null, null, null)
    t.setPadding(dp(14), dp(sp * 0.75f), dp(14), dp(sp * 0.75f))
    t.background = if (outline) rounded(Color.TRANSPARENT, dp(14f).toFloat(), dp(1), C.RED) else redGradient(dp(14f).toFloat())
    t.isClickable = true
    t.setOnClickListener { onClick() }
    t.pressable()
    return t
}

fun Context.field(hint: String, pass: Boolean): EditText {
    val e = EditText(this)
    e.hint = hint
    e.setHintTextColor(C.DIM)
    e.setTextColor(C.SILVER)
    e.textSize = 16f
    e.inputType = if (pass) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
    else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
    e.setSingleLine(true)
    e.background = rounded(C.PANEL, dp(14f).toFloat(), dp(1), C.LINE)
    e.setPadding(dp(16), dp(14), dp(16), dp(14))
    return e
}
