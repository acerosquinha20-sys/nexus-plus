package com.nexus.ia

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.documentfile.provider.DocumentFile
import kotlin.math.abs

class FloatingService : Service() {

    companion object {
        @Volatile
        var running = false
        private const val TYPE = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    }

    private lateinit var wm: WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var scale = 1f

    private var bubble: ImageView? = null
    private var bubbleLp: WindowManager.LayoutParams? = null
    private var panel: FrameLayout? = null
    private var panelLp: WindowManager.LayoutParams? = null
    private var statusTv: TextView? = null
    private var eyeBtn: TextView? = null
    private var hl: HighlightView? = null
    private var confirm: View? = null
    private var settingsOpen = false
    private var textsOpen = false

    private val tick = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 500)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startFg()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        scale = Store.scale(this)
        addBubble()
        running = true
        handler.postDelayed(tick, 500)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onDestroy() {
        running = false
        handler.removeCallbacksAndMessages(null)
        if (::wm.isInitialized) {
            safeRemove(confirm); confirm = null
            safeRemove(hl); hl = null
            safeRemove(panel); panel = null
            safeRemove(bubble); bubble = null
        }
        super.onDestroy()
    }

    private fun startFg() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel("nexus", "NEXUS IA", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "nexus")
            .setSmallIcon(R.drawable.ic_ai)
            .setContentTitle("NEXUS IA")
            .setContentText("Interface flutuante ativa")
            .setOngoing(true)
            .build()
        startForeground(1, n)
    }

    private fun safeRemove(v: View?) {
        if (v == null) return
        try {
            wm.removeView(v)
        } catch (e: Exception) {
        }
    }

    private fun overlayLp(w: Int, h: Int, flags: Int): WindowManager.LayoutParams =
        WindowManager.LayoutParams(w, h, TYPE, flags, PixelFormat.TRANSLUCENT)

    // ---------------------------------------------------------------- bolha com o N

    private fun addBubble() {
        val size = dp(58f * scale)
        val iv = ImageView(this)
        iv.setImageResource(R.drawable.n_icon)
        iv.scaleType = ImageView.ScaleType.FIT_CENTER
        iv.setPadding(dp(7), dp(7), dp(7), dp(7))
        iv.background = rounded(Color.argb(230, 5, 5, 6), size / 2f, dp(2), C.RED)
        iv.clipRound(size / 2f)
        val lp = overlayLp(size, size, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = dp(8)
        lp.y = dp(180)
        iv.setOnTouchListener(object : View.OnTouchListener {
            var sx = 0f
            var sy = 0f
            var ox = 0
            var oy = 0
            var moved = false
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        sx = e.rawX; sy = e.rawY; ox = lp.x; oy = lp.y; moved = false
                        v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(80).start()
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = e.rawX - sx
                        val dy = e.rawY - sy
                        if (abs(dx) > dp(6) || abs(dy) > dp(6)) moved = true
                        if (moved) {
                            lp.x = (ox + dx).toInt()
                            lp.y = (oy + dy).toInt()
                            wm.updateViewLayout(v, lp)
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                        if (!moved) togglePanel()
                    }
                    MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                }
                return true
            }
        })
        wm.addView(iv, lp)
        bubble = iv
        bubbleLp = lp
    }

    private fun resizeBubble() {
        val lp = bubbleLp ?: return
        val b = bubble ?: return
        val size = dp(58f * scale)
        lp.width = size
        lp.height = size
        b.background = rounded(Color.argb(230, 5, 5, 6), size / 2f, dp(2), C.RED)
        b.clipRound(size / 2f)
        wm.updateViewLayout(b, lp)
    }

    // ---------------------------------------------------------------- painel

    private fun togglePanel() {
        if (panel == null) openPanel() else closePanel()
    }

    private fun openPanel() {
        val p = buildPanel()
        val screenW = resources.displayMetrics.widthPixels
        val w = minOf(dp(300f * scale), screenW - dp(16))
        val lp = overlayLp(w, WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = (screenW - w) / 2
        lp.y = dp(80)
        p.alpha = 0f
        p.scaleX = 0.92f
        p.scaleY = 0.92f
        wm.addView(p, lp)
        p.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(200).start()
        panel = p
        panelLp = lp
        if (textsOpen) { /* a lista é preenchida na construção */ }
        refresh()
    }

    private fun closePanel() {
        val p = panel ?: return
        panel = null
        statusTv = null
        p.animate().alpha(0f).scaleX(0.92f).scaleY(0.92f).setDuration(150).withEndAction { safeRemove(p) }.start()
    }

    private fun closePanelNow() {
        safeRemove(panel)
        panel = null
        statusTv = null
    }

    private fun buildPanel(): FrameLayout {
        val s = scale
        val rootFl = FrameLayout(this)
        val rad = dp(22f).toFloat()
        rootFl.setPadding(dp(1), dp(1), dp(1), dp(1))
        rootFl.background = rounded(C.BLACK, rad, dp(1), C.RED_DEEP)
        rootFl.clipRound(rad)
        rootFl.addView(NetworkView(this, 26), FrameLayout.LayoutParams(-1, -1))

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER_HORIZONTAL
        col.setPadding(dp(14f * s), dp(14f * s), dp(14f * s), dp(10f * s))
        rootFl.addView(col, FrameLayout.LayoutParams(-1, -2))

        // título (também serve de alça para arrastar o painel)
        val title = label("NEXUS IA", 26f * s, Color.WHITE, true)
        title.gravity = Gravity.CENTER
        title.letterSpacing = 0.08f
        title.setShadowLayer(dp(10f).toFloat(), 0f, 0f, C.RED)
        title.setOnTouchListener(dragPanel())
        col.addView(title, LinearLayout.LayoutParams(-1, -2))

        val sub = label("o melhor nexus jailbreak para IA", 10f * s, Color.WHITE, true)
        sub.gravity = Gravity.CENTER
        sub.rainbow()
        col.addView(sub, LinearLayout.LayoutParams(-1, -2))

        val st = label("Abra uma IA para começar", 12f * s, C.DIM, false)
        st.gravity = Gravity.CENTER
        statusTv = st
        col.addView(st, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8f * s) })

        // botões principais
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        val eye = nexusButton(if (hl == null) "Mostrar chat" else "Ocultar chat", R.drawable.ic_eye, 12f * s) { toggleHighlight() }
        eyeBtn = eye
        val textsBox = LinearLayout(this)
        textsBox.orientation = LinearLayout.VERTICAL
        val sv = ScrollView(this)
        sv.isVerticalScrollBarEnabled = false
        sv.addView(textsBox, LinearLayout.LayoutParams(-1, -2))
        val svBox = LinearLayout(this)
        svBox.addView(sv, LinearLayout.LayoutParams(-1, dp(150f * s)))
        svBox.visibility = if (textsOpen) View.VISIBLE else View.GONE
        val send = nexusButton("Enviar texto", R.drawable.ic_send, 12f * s) {
            textsOpen = !textsOpen
            if (textsOpen) {
                svBox.visibility = View.VISIBLE
                fillTexts(textsBox)
            } else {
                svBox.visibility = View.GONE
            }
        }
        row.addView(eye, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(4) })
        row.addView(send, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(4) })
        col.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10f * s) })

        // lista de textos da pasta
        col.addView(svBox, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8f * s) })
        if (textsOpen) fillTexts(textsBox)

        // linha de ajustes (escondida)
        val settingsRow = LinearLayout(this)
        settingsRow.orientation = LinearLayout.HORIZONTAL
        settingsRow.gravity = Gravity.CENTER_VERTICAL
        settingsRow.visibility = if (settingsOpen) View.VISIBLE else View.GONE
        val minus = iconBtn(R.drawable.ic_minus, 34f * s) { setScale(scale - 0.1f) }
        val pct = label("${(scale * 100).toInt()}%", 13f * s, C.SILVER, true)
        pct.gravity = Gravity.CENTER
        val plus = iconBtn(R.drawable.ic_plus, 34f * s) { setScale(scale + 0.1f) }
        val close = nexusButton("Fechar", R.drawable.ic_power, 11f * s, true) { showConfirm() }
        settingsRow.addView(minus, LinearLayout.LayoutParams(dp(34f * s), dp(34f * s)))
        settingsRow.addView(pct, LinearLayout.LayoutParams(dp(48f * s), -2))
        settingsRow.addView(plus, LinearLayout.LayoutParams(dp(34f * s), dp(34f * s)))
        settingsRow.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        settingsRow.addView(close, LinearLayout.LayoutParams(-2, -2))

        // engrenagem pequena embaixo
        val bottom = LinearLayout(this)
        bottom.orientation = LinearLayout.HORIZONTAL
        bottom.gravity = Gravity.CENTER_VERTICAL
        val gear = ImageView(this)
        gear.setImageResource(R.drawable.ic_settings)
        gear.setColorFilter(C.DIM)
        gear.setPadding(dp(5), dp(5), dp(5), dp(5))
        gear.setOnClickListener {
            settingsOpen = !settingsOpen
            settingsRow.visibility = if (settingsOpen) View.VISIBLE else View.GONE
            gear.setColorFilter(if (settingsOpen) C.RED else C.DIM)
        }
        gear.pressable()
        gear.setColorFilter(if (settingsOpen) C.RED else C.DIM)
        bottom.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        bottom.addView(gear, LinearLayout.LayoutParams(dp(30f * s), dp(30f * s)))
        col.addView(bottom, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4f * s) })
        col.addView(settingsRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2f * s) })
        return rootFl
    }

    private fun iconBtn(icon: Int, sizeDp: Float, onClick: () -> Unit): ImageView {
        val b = ImageView(this)
        b.setImageResource(icon)
        b.setColorFilter(Color.WHITE)
        val pd = dp(sizeDp * 0.25f)
        b.setPadding(pd, pd, pd, pd)
        b.background = redGradient(dp(10f).toFloat())
        b.setOnClickListener { onClick() }
        b.pressable()
        return b
    }

    private fun dragPanel(): View.OnTouchListener = object : View.OnTouchListener {
        var sx = 0f
        var sy = 0f
        var ox = 0
        var oy = 0
        override fun onTouch(v: View, e: MotionEvent): Boolean {
            val lp = panelLp ?: return false
            val p = panel ?: return false
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; ox = lp.x; oy = lp.y }
                MotionEvent.ACTION_MOVE -> {
                    lp.x = (ox + e.rawX - sx).toInt()
                    lp.y = (oy + e.rawY - sy).toInt()
                    wm.updateViewLayout(p, lp)
                }
            }
            return true
        }
    }

    private fun setScale(v: Float) {
        scale = v.coerceIn(0.7f, 1.5f)
        Store.setScale(this, scale)
        val wasOpen = panel != null
        closePanelNow()
        resizeBubble()
        if (wasOpen) openPanel()
    }

    // ---------------------------------------------------------------- overlay do chat

    private fun toggleHighlight() {
        if (hl == null) {
            val v = HighlightView(this)
            val lp = overlayLp(
                -1, -1,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            )
            wm.addView(v, lp)
            hl = v
            eyeBtn?.text = "OCULTAR CHAT"
            if (NexusAccessibilityService.instance == null) toast("Ative a acessibilidade do NEXUS IA no app")
        } else {
            safeRemove(hl)
            hl = null
            eyeBtn?.text = "MOSTRAR CHAT"
        }
    }

    private fun refresh() {
        if (panel == null && hl == null) return
        val svc = NexusAccessibilityService.instance
        val name = AiDetector.nameOf(svc?.lastAiPkg)
        statusTv?.text = when {
            svc == null -> "Ative a acessibilidade no app"
            name != null -> "IA atual: $name"
            else -> "Abra uma IA para começar"
        }
        if (hl != null) hl?.target = svc?.findInputBounds()
    }

    // ---------------------------------------------------------------- textos da pasta

    private fun fillTexts(list: LinearLayout) {
        list.removeAllViews()
        list.addView(label("Carregando...", 12f * scale, C.DIM, false))
        val pkg = NexusAccessibilityService.instance?.lastAiPkg
        Thread {
            val items = ArrayList<Pair<String, Uri>>()
            for (k in listOfNotNull(pkg, "geral")) {
                val s = Store.dir(this, k) ?: continue
                val tree = (try {
                    DocumentFile.fromTreeUri(this, Uri.parse(s))
                } catch (e: Exception) {
                    null
                }) ?: continue
                for (f in tree.listFiles()) {
                    val n = f.name ?: continue
                    if (f.isFile && n.endsWith(".txt", true)) items.add(Pair(n, f.uri))
                }
            }
            handler.post {
                list.removeAllViews()
                if (items.isEmpty()) {
                    list.addView(label("Nenhum .txt para esta IA. O ADM escolhe a pasta dentro do app.", 12f * scale, C.DIM, false))
                }
                for ((n, u) in items) {
                    val r = label(n.removeSuffix(".txt").removeSuffix(".TXT"), 13f * scale, C.SILVER, true)
                    r.setPadding(dp(12), dp(9), dp(12), dp(9))
                    r.background = rounded(Color.argb(200, 14, 11, 12), dp(10f).toFloat(), dp(1), C.LINE)
                    r.setOnClickListener { sendFile(u) }
                    r.pressable()
                    list.addView(r, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
                }
            }
        }.start()
    }

    private fun sendFile(uri: Uri) {
        Thread {
            val t = try {
                contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            } catch (e: Exception) {
                null
            }
            handler.post {
                if (t.isNullOrBlank()) toast("Não consegui ler o arquivo") else deliver(t.take(100000))
            }
        }.start()
    }

    private fun deliver(text: String) {
        val ok = NexusAccessibilityService.instance?.setInputText(text) ?: false
        if (ok) {
            toast("Texto enviado ao chat")
        } else {
            val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("nexus", text))
            toast("Abra o chat da IA. Texto copiado, é só colar.")
        }
    }

    // ---------------------------------------------------------------- aviso de fechar

    private fun showConfirm() {
        if (confirm != null) return
        val dim = FrameLayout(this)
        dim.setBackgroundColor(Color.argb(210, 0, 0, 0))
        dim.isClickable = true

        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.gravity = Gravity.CENTER_HORIZONTAL
        card.background = rounded(C.BLACK, dp(22f).toFloat(), dp(2), C.RED)
        card.setPadding(dp(22), dp(22), dp(22), dp(18))

        val ic = ImageView(this)
        ic.setImageResource(R.drawable.ic_power)
        ic.setColorFilter(C.RED)
        card.addView(ic, LinearLayout.LayoutParams(dp(44), dp(44)))
        val t = label("Fechar o NEXUS IA?", 20f, Color.WHITE, true)
        t.gravity = Gravity.CENTER
        card.addView(t, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        val d = label("A interface flutuante será encerrada.", 13f, C.DIM, false)
        d.gravity = Gravity.CENTER
        card.addView(d, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        val no = nexusButton("Não", R.drawable.ic_close, 14f, true) { dismissConfirm() }
        val yes = nexusButton("Sim", R.drawable.ic_power, 14f) { stopSelf() }
        row.addView(no, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(6) })
        row.addView(yes, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(6) })
        card.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18) })

        dim.addView(card, FrameLayout.LayoutParams(dp(290), -2, Gravity.CENTER))
        wm.addView(
            dim,
            overlayLp(-1, -1, WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        )
        confirm = dim
        dim.alpha = 0f
        card.scaleX = 0.9f
        card.scaleY = 0.9f
        dim.animate().alpha(1f).setDuration(160).start()
        card.animate().scaleX(1f).scaleY(1f).setDuration(200).start()
    }

    private fun dismissConfirm() {
        safeRemove(confirm)
        confirm = null
    }
}
