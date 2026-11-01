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
import androidx.documentfile.provider.DocumentFile
import kotlin.math.abs

/**
 * Dono das janelas flutuantes: bolinha com o N, menu quadrado, moldura do chat e aviso de fechar.
 * O desenho do menu fica em FloatingMenu.kt.
 */
class FloatingService : Service() {

    companion object {
        @Volatile
        var running = false
        private const val TYPE = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        private val SIZES = floatArrayOf(0.8f, 1f, 1.2f, 1.4f)
        private const val NOT_FOCUSABLE = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        private const val NO_LIMITS = WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
    }

    private lateinit var wm: WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var scale = 1f

    private var bubble: ImageView? = null
    private var bubbleLp: WindowManager.LayoutParams? = null
    private var menu: FloatingMenu? = null
    private var menuLp: WindowManager.LayoutParams? = null
    private var menuX = -1
    private var menuY = -1
    private var hl: HighlightView? = null
    private var confirm: View? = null
    private var ticking = false

    /** Só roda enquanto o menu ou a moldura estiverem na tela. */
    private val tick = object : Runnable {
        override fun run() {
            refresh()
            if (menu != null || hl != null) handler.postDelayed(this, 500) else ticking = false
        }
    }

    private val actions = object : FloatingMenu.Actions {
        override fun minimize() = closePanel()
        override fun toggleChat() = toggleHighlight()
        override fun cycleSize() {
            val next = SIZES.firstOrNull { it > scale + 0.01f } ?: SIZES.first()
            setScale(next)
        }
        override fun close() = showConfirm()
        override fun loadTexts() = fillTexts()
        override fun sendText(uri: Uri) = sendFile(uri)
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
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onDestroy() {
        running = false
        handler.removeCallbacksAndMessages(null)
        if (::wm.isInitialized) {
            safeRemove(confirm); confirm = null
            safeRemove(hl); hl = null
            safeRemove(menu?.view); menu = null
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

    private fun overlayLp(w: Int, h: Int, flags: Int) =
        WindowManager.LayoutParams(w, h, TYPE, flags, PixelFormat.TRANSLUCENT)

    private fun ensureTick() {
        if (ticking) return
        ticking = true
        handler.post(tick)
    }

    // ---------------------------------------------------------------- bolinha com o N

    private fun addBubble() {
        val size = dp(58f * scale)
        val iv = ImageView(this)
        iv.setImageResource(R.drawable.n_icon)
        iv.scaleType = ImageView.ScaleType.FIT_CENTER
        iv.setPadding(dp(7), dp(7), dp(7), dp(7))
        iv.background = rounded(Color.argb(230, 5, 5, 6), size / 2f, dp(2), C.RED)
        iv.clipRound(size / 2f)
        val lp = overlayLp(size, size, NOT_FOCUSABLE or NO_LIMITS)
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

    // ---------------------------------------------------------------- menu quadrado

    private fun togglePanel() {
        if (menu == null) openPanel() else closePanel()
    }

    private fun openPanel() {
        val m = FloatingMenu(this, scale, actions)
        val dm = resources.displayMetrics
        val lp = overlayLp(m.side, m.side, NOT_FOCUSABLE or NO_LIMITS)
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = if (menuX < 0) (dm.widthPixels - m.side) / 2 else menuX
        lp.y = if (menuY < 0) dp(120) else menuY
        lp.x = lp.x.coerceIn(0, (dm.widthPixels - m.side).coerceAtLeast(0))
        lp.y = lp.y.coerceIn(0, (dm.heightPixels - m.side).coerceAtLeast(0))
        m.handle.setOnTouchListener(dragPanel())
        m.setChat(hl != null)
        m.view.alpha = 0f
        m.view.scaleX = 0.92f
        m.view.scaleY = 0.92f
        wm.addView(m.view, lp)
        m.view.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(200).start()
        menu = m
        menuLp = lp
        refresh()
        ensureTick()
    }

    private fun closePanel() {
        val m = menu ?: return
        menu = null
        m.view.animate().alpha(0f).scaleX(0.92f).scaleY(0.92f).setDuration(150)
            .withEndAction { safeRemove(m.view) }.start()
    }

    private fun closePanelNow() {
        safeRemove(menu?.view)
        menu = null
    }

    private fun dragPanel(): View.OnTouchListener = object : View.OnTouchListener {
        var sx = 0f
        var sy = 0f
        var ox = 0
        var oy = 0
        override fun onTouch(v: View, e: MotionEvent): Boolean {
            val lp = menuLp ?: return false
            val m = menu ?: return false
            val dm = resources.displayMetrics
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; ox = lp.x; oy = lp.y }
                MotionEvent.ACTION_MOVE -> {
                    lp.x = (ox + e.rawX - sx).toInt().coerceIn(0, (dm.widthPixels - m.side).coerceAtLeast(0))
                    lp.y = (oy + e.rawY - sy).toInt().coerceIn(0, (dm.heightPixels - m.side).coerceAtLeast(0))
                    menuX = lp.x
                    menuY = lp.y
                    wm.updateViewLayout(m.view, lp)
                }
            }
            return true
        }
    }

    private fun setScale(v: Float) {
        scale = v.coerceIn(0.7f, 1.5f)
        Store.setScale(this, scale)
        val wasOpen = menu != null
        closePanelNow()
        resizeBubble()
        if (wasOpen) openPanel()
    }

    // ---------------------------------------------------------------- moldura do chat

    private fun toggleHighlight() {
        if (hl == null) {
            val v = HighlightView(this)
            val lp = overlayLp(
                -1, -1,
                NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or NO_LIMITS
            )
            wm.addView(v, lp)
            hl = v
            if (NexusAccessibilityService.instance == null) toast("Ative a acessibilidade do NEXUS IA no app")
        } else {
            safeRemove(hl)
            hl = null
        }
        menu?.setChat(hl != null)
        refresh()
        ensureTick()
    }

    private fun refresh() {
        if (menu == null && hl == null) return
        val svc = NexusAccessibilityService.instance
        svc?.scan()
        val ai = svc?.currentAi()
        menu?.setStatus(ai, svc != null, svc?.currentWeb == true)
        hl?.let {
            it.label = if (ai != null) "CHAT · " + ai.name.uppercase() else "CHAT DA IA"
            it.target = svc?.findInputBounds()
        }
    }

    // ---------------------------------------------------------------- textos da pasta

    private fun fillTexts() {
        val ai = NexusAccessibilityService.instance?.currentAi()
        Thread {
            val items = ArrayList<Pair<String, Uri>>()
            for (k in listOfNotNull(ai?.id, "geral")) {
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
                val m = menu
                if (m != null && m.onTextsPage) m.showTexts(items)
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
            overlayLp(-1, -1, WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or NO_LIMITS)
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
