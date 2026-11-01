package com.nexus.ia

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Fundo preto com linhas vivas ligando bolinhas vermelhas. Sem alocação dentro do onDraw. */
class NetworkView(ctx: Context, private val count: Int = 34) : View(ctx) {
    private val xs = FloatArray(count)
    private val ys = FloatArray(count)
    private val vx = FloatArray(count)
    private val vy = FloatArray(count)
    private var last = 0L

    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = ctx.dp(1).toFloat()
        color = C.RED
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = C.RED }
    private val rDot = ctx.dp(2).toFloat()
    private val rGlow = ctx.dp(6).toFloat()

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val sp = context.dp(22).toFloat()
        for (i in 0 until count) {
            xs[i] = Math.random().toFloat() * w
            ys[i] = Math.random().toFloat() * h
            vx[i] = (Math.random().toFloat() - 0.5f) * 2f * sp
            vy[i] = (Math.random().toFloat() - 0.5f) * 2f * sp
        }
    }

    override fun onDetachedFromWindow() {
        last = 0L
        super.onDetachedFromWindow()
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val now = System.nanoTime()
        val dt = if (last == 0L) 0.016f else ((now - last) / 1e9f).coerceAtMost(0.05f)
        last = now

        c.drawColor(C.BLACK)

        for (i in 0 until count) {
            xs[i] += vx[i] * dt
            ys[i] += vy[i] * dt
            if (xs[i] < 0f) { xs[i] = 0f; vx[i] = -vx[i] }
            if (xs[i] > w) { xs[i] = w; vx[i] = -vx[i] }
            if (ys[i] < 0f) { ys[i] = 0f; vy[i] = -vy[i] }
            if (ys[i] > h) { ys[i] = h; vy[i] = -vy[i] }
        }

        val maxD = min(w, h) * 0.42f
        val maxD2 = maxD * maxD
        for (i in 0 until count) {
            for (j in i + 1 until count) {
                val dx = xs[i] - xs[j]
                val dy = ys[i] - ys[j]
                val d2 = dx * dx + dy * dy
                if (d2 < maxD2) {
                    line.alpha = ((1f - sqrt(d2) / maxD) * 190f).toInt()
                    c.drawLine(xs[i], ys[i], xs[j], ys[j], line)
                }
            }
        }

        val t = now / 4e8
        for (i in 0 until count) {
            glow.alpha = 55 + (40 * sin(t + i)).toInt()
            c.drawCircle(xs[i], ys[i], rGlow, glow)
            c.drawCircle(xs[i], ys[i], rDot, dot)
        }
        postInvalidateOnAnimation()
    }
}

/** Moldura vermelha pulsante em volta do campo de chat da IA. */
class HighlightView(ctx: Context) : View(ctx) {
    var target: Rect? = null
        set(v) {
            field = v
            invalidate()
        }

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = ctx.dp(3).toFloat()
        color = C.RED
    }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = ctx.dp(10).toFloat()
        color = C.RED
    }
    private val chip = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = C.RED }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = ctx.dp(12).toFloat()
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    }
    private val rf = RectF()
    private val cr = RectF()
    private val radius = ctx.dp(14).toFloat()
    private val label = "CHAT DA IA"

    override fun onDraw(c: Canvas) {
        val t = target ?: return
        val pulse = (sin(System.nanoTime() / 2.5e8) * 0.5 + 0.5).toFloat()
        val pad = context.dp(4) + pulse * context.dp(4)
        rf.set(t.left - pad, t.top - pad, t.right + pad, t.bottom + pad)
        glow.alpha = (40 + 70 * pulse).toInt()
        c.drawRoundRect(rf, radius, radius, glow)
        c.drawRoundRect(rf, radius, radius, stroke)

        val tw = txt.measureText(label)
        val ch = context.dp(22).toFloat()
        var top = rf.top - ch - context.dp(4)
        if (top < 0f) top = rf.bottom + context.dp(4)
        cr.set(rf.left, top, rf.left + tw + context.dp(16), top + ch)
        c.drawRoundRect(cr, ch / 2f, ch / 2f, chip)
        c.drawText(label, cr.left + context.dp(8), cr.bottom - context.dp(7), txt)
        postInvalidateOnAnimation()
    }
}
