package com.nexus.ia

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

// Menu flutuante quadrado estilo mod menu. O FloatingService controla janela, arrasto e acoes.
class FloatingMenu(private val ctx: Context, private val scale: Float, private val act: Actions) {

    interface Actions {
        fun minimize()
        fun toggleChat()
        fun cycleSize()
        fun close()
        fun loadTexts()
        fun sendText(uri: Uri)
    }

    private class Tile(val root: LinearLayout, val icon: ImageView, val sub: TextView)

    /** Lado do quadrado em pixels. */
    val side: Int = ctx.dp(224f * scale)
    val view = FrameLayout(ctx)

    /** Parte do menu que serve para arrastar. */
    val handle = LinearLayout(ctx)

    var onTextsPage = false
        private set

    private val body = FrameLayout(ctx)
    private val dot = TextView(ctx)
    private val status = TextView(ctx)
    private var chatTile: Tile? = null
    private var chatOn = false

    private fun d(v: Float): Int = ctx.dp(v * scale)
    private fun sp(v: Float): Float = v * scale
    private fun lp(w: Int, h: Int, weight: Float = 0f) = LinearLayout.LayoutParams(w, h, weight)

    init {
        val radius = d(18f).toFloat()
        val bg = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.parseColor("#1A0A0D"), C.BLACK))
        bg.cornerRadius = radius
        bg.setStroke(ctx.dp(1), C.RED_DEEP)
        view.background = bg
        view.clipRound(radius)

        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        view.addView(col, FrameLayout.LayoutParams(-1, -1))

        // alça: logo + título + minimizar
        handle.orientation = LinearLayout.HORIZONTAL
        handle.gravity = Gravity.CENTER_VERTICAL
        handle.setPadding(d(10f), 0, d(6f), 0)
        val logo = ImageView(ctx)
        logo.setImageResource(R.drawable.n_icon)
        logo.scaleType = ImageView.ScaleType.FIT_CENTER
        handle.addView(logo, lp(d(22f), d(22f)))
        val title = ctx.label("NEXUS IA", sp(13f), Color.WHITE, true)
        title.letterSpacing = 0.1f
        title.setShadowLayer(ctx.dp(6f).toFloat(), 0f, 0f, C.RED)
        handle.addView(title, lp(0, -2, 1f).apply { leftMargin = d(8f) })
        val min = ImageView(ctx)
        min.setImageResource(R.drawable.ic_minus)
        min.setColorFilter(C.DIM)
        min.setPadding(d(7f), d(7f), d(7f), d(7f))
        min.setOnClickListener { act.minimize() }
        min.pressable()
        handle.addView(min, lp(d(30f), d(30f)))
        col.addView(handle, lp(-1, d(38f)))
        col.addView(View(ctx).apply { setBackgroundColor(C.LINE) }, lp(-1, 1))

        // linha da IA detectada
        val st = LinearLayout(ctx)
        st.orientation = LinearLayout.HORIZONTAL
        st.gravity = Gravity.CENTER_VERTICAL
        st.setPadding(d(12f), 0, d(12f), 0)
        dot.text = "●"
        dot.textSize = sp(10f)
        status.textSize = sp(11f)
        status.typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        status.maxLines = 1
        status.ellipsize = TextUtils.TruncateAt.END
        st.addView(dot, lp(-2, -2))
        st.addView(status, lp(0, -2, 1f).apply { leftMargin = d(6f) })
        col.addView(st, lp(-1, d(26f)))

        col.addView(body, lp(-1, 0, 1f))
        showHome()
        setStatus(null, false, false)
    }

    // ------------------------------------------------------------ estado

    fun setStatus(ai: Ai?, accOk: Boolean, web: Boolean) {
        when {
            !accOk -> {
                dot.setTextColor(C.RED)
                status.setTextColor(C.DIM)
                status.text = "ative a acessibilidade"
            }
            ai != null -> {
                dot.setTextColor(C.GREEN)
                status.setTextColor(C.SILVER)
                status.text = if (web) ai.name + " · site" else ai.name
            }
            else -> {
                dot.setTextColor(C.DIM)
                status.setTextColor(C.DIM)
                status.text = "abra uma IA"
            }
        }
    }

    fun setChat(on: Boolean) {
        chatOn = on
        val t = chatTile ?: return
        t.sub.text = if (on) "ligado" else "desligado"
        paintTile(t, on)
    }

    private fun paintTile(t: Tile, on: Boolean) {
        t.root.background = rounded(
            if (on) Color.parseColor("#26080B") else C.PANEL,
            d(12f).toFloat(), ctx.dp(1), if (on) C.RED else C.LINE
        )
        t.icon.setColorFilter(if (on) C.RED else C.SILVER)
        t.sub.setTextColor(if (on) C.RED else C.DIM)
    }

    // ------------------------------------------------------------ página inicial (grade 2x2)

    fun showHome() {
        onTextsPage = false
        body.removeAllViews()

        val grid = LinearLayout(ctx)
        grid.orientation = LinearLayout.VERTICAL
        grid.setPadding(d(6f), d(6f), d(6f), d(6f))

        val chat = tile(R.drawable.ic_eye, "CHAT", "desligado") { act.toggleChat() }
        val texts = tile(R.drawable.ic_send, "TEXTOS", "enviar") {
            showTexts(null)
            act.loadTexts()
        }
        val size = tile(R.drawable.ic_plus, "TAMANHO", "${(scale * 100).toInt()}%") { act.cycleSize() }
        val off = tile(R.drawable.ic_power, "FECHAR", "encerrar") { act.close() }
        off.icon.setColorFilter(C.RED)

        grid.addView(row(chat, texts), lp(-1, 0, 1f))
        grid.addView(row(size, off), lp(-1, 0, 1f))
        body.addView(grid, FrameLayout.LayoutParams(-1, -1))

        chatTile = chat
        setChat(chatOn)
    }

    private fun row(a: Tile, b: Tile): LinearLayout {
        val r = LinearLayout(ctx)
        r.orientation = LinearLayout.HORIZONTAL
        val m = d(3f)
        r.addView(a.root, lp(0, -1, 1f).apply { setMargins(m, m, m, m) })
        r.addView(b.root, lp(0, -1, 1f).apply { setMargins(m, m, m, m) })
        return r
    }

    private fun tile(icon: Int, title: String, sub: String, onClick: () -> Unit): Tile {
        val t = LinearLayout(ctx)
        t.orientation = LinearLayout.VERTICAL
        t.gravity = Gravity.CENTER
        val iv = ImageView(ctx)
        iv.setImageResource(icon)
        t.addView(iv, lp(d(22f), d(22f)))
        val tt = ctx.label(title, sp(11f), Color.WHITE, true)
        tt.letterSpacing = 0.08f
        tt.gravity = Gravity.CENTER
        t.addView(tt, lp(-2, -2).apply { topMargin = d(4f) })
        val s = ctx.label(sub, sp(9f), C.DIM, false)
        s.gravity = Gravity.CENTER
        t.addView(s, lp(-2, -2))
        t.isClickable = true
        t.setOnClickListener { onClick() }
        t.pressable()
        val tile = Tile(t, iv, s)
        iv.setColorFilter(C.SILVER)
        paintTile(tile, false)
        return tile
    }

    // ------------------------------------------------------------ página de textos

    /** [items] = null mostra "Carregando...". */
    fun showTexts(items: List<Pair<String, Uri>>?) {
        onTextsPage = true
        body.removeAllViews()

        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(d(8f), d(6f), d(8f), d(8f))

        val back = ctx.label("‹  TEXTOS", sp(12f), C.SILVER, true)
        back.setPadding(d(4f), d(2f), d(4f), d(6f))
        back.setOnClickListener { showHome() }
        back.pressable()
        col.addView(back, lp(-1, -2))

        val list = LinearLayout(ctx)
        list.orientation = LinearLayout.VERTICAL
        when {
            items == null -> list.addView(note("Carregando..."))
            items.isEmpty() -> list.addView(note("Nenhum .txt para esta IA. O ADM escolhe a pasta dentro do app."))
            else -> for ((n, u) in items) {
                val r = ctx.label(n.removeSuffix(".txt").removeSuffix(".TXT"), sp(12f), C.SILVER, true)
                r.maxLines = 1
                r.ellipsize = TextUtils.TruncateAt.END
                r.setPadding(d(10f), d(8f), d(10f), d(8f))
                r.background = rounded(C.PANEL, d(10f).toFloat(), ctx.dp(1), C.LINE)
                r.setOnClickListener { act.sendText(u) }
                r.pressable()
                list.addView(r, lp(-1, -2).apply { bottomMargin = d(5f) })
            }
        }
        val sv = ScrollView(ctx)
        sv.isVerticalScrollBarEnabled = false
        sv.addView(list, lp(-1, -2))
        col.addView(sv, lp(-1, 0, 1f))
        body.addView(col, FrameLayout.LayoutParams(-1, -1))
    }

    private fun note(s: String): TextView = ctx.label(s, sp(11f), C.DIM, false)
}
