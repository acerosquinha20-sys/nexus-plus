package com.nexus.ia

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    private lateinit var root: FrameLayout
    private var screen = "login"
    private var pendingKey: String? = null

    private val pickDir = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val key = pendingKey
        if (uri != null && key != null) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
            }
            Store.setDir(this, key, uri.toString())
        }
        showAdmin()
    }
    private val termuxReq = registerForActivityResult(ActivityResultContracts.RequestPermission()) { onPermChange() }
    private val notifReq = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val shizukuListener = Shizuku.OnRequestPermissionResultListener { _, _ -> runOnUiThread { onPermChange() } }
    private val binderListener = Shizuku.OnBinderReceivedListener { runOnUiThread { if (screen == "hub") showHub() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = FrameLayout(this)
        root.setBackgroundColor(C.BLACK)
        setContentView(root)
        try {
            Shizuku.addRequestPermissionResultListener(shizukuListener)
            Shizuku.addBinderReceivedListenerSticky(binderListener)
        } catch (t: Throwable) {
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (screen == "admin") {
                    showHub()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
        if (Store.user(this) != null) showHub() else showLogin()
    }

    override fun onResume() {
        super.onResume()
        if (screen == "hub") showHub()
        autoStartIfReady()
    }

    override fun onDestroy() {
        try {
            Shizuku.removeRequestPermissionResultListener(shizukuListener)
            Shizuku.removeBinderReceivedListener(binderListener)
        } catch (t: Throwable) {
        }
        super.onDestroy()
    }

    // ------------------------------------------------------------ estrutura

    private fun setScreen(name: String, content: View) {
        screen = name
        root.removeAllViews()
        root.addView(NetworkView(this, 40), ViewGroup.LayoutParams(-1, -1))
        root.addView(content, FrameLayout.LayoutParams(-1, -1))
        content.alpha = 0f
        content.translationY = dp(14).toFloat()
        content.animate().alpha(1f).translationY(0f).setDuration(280).start()
    }

    private fun lp(top: Int = 0, bottom: Int = 0): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(-1, -2)
        p.topMargin = dp(top)
        p.bottomMargin = dp(bottom)
        return p
    }

    private fun card(): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.background = rounded(Color.argb(235, 14, 11, 12), dp(18f).toFloat(), dp(1), C.LINE)
        c.setPadding(dp(16), dp(14), dp(16), dp(16))
        return c
    }

    private fun section(s: String): TextView {
        val t = label(s, 12f, C.RED, true)
        t.letterSpacing = 0.14f
        return t
    }

    private fun statusRow(name: String, ok: Boolean, pending: String): LinearLayout {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.addView(label(name, 16f, C.SILVER, true), LinearLayout.LayoutParams(0, -2, 1f))
        r.addView(label(if (ok) "● ATIVO" else "● $pending", 12f, if (ok) C.GREEN else C.DIM, true))
        return r
    }

    // ------------------------------------------------------------ login

    private fun showLogin() {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER
        col.setPadding(dp(24), dp(24), dp(24), dp(24))

        val logo = ImageView(this)
        logo.setImageResource(R.drawable.logo)
        logo.scaleType = ImageView.ScaleType.CENTER_CROP
        logo.clipRound(dp(28f).toFloat())
        col.addView(logo, LinearLayout.LayoutParams(dp(150), dp(150)))
        logo.breathe()

        val tag = label("o melhor nexus jailbreak para IA", 11f, Color.WHITE, true)
        tag.gravity = Gravity.CENTER
        tag.rainbow()
        col.addView(tag, lp(top = 14, bottom = 18))

        val box = card()
        val name = field("Nome", false)
        val pass = field("Senha", true)
        val msg = label("", 12f, C.RED, false)
        msg.gravity = Gravity.CENTER
        box.addView(name, lp())
        box.addView(pass, lp(top = 10))
        box.addView(msg, lp(top = 8))

        val enter = nexusButton("Entrar", R.drawable.ic_lock, 15f) {
            if (Store.login(this, name.text.toString(), pass.text.toString())) {
                showHub()
            } else {
                msg.text = "Nome ou senha incorretos"
                box.shake()
            }
        }
        val create = nexusButton("Criar conta", R.drawable.ic_user, 13f, true) {
            val err = Store.register(this, name.text.toString(), pass.text.toString())
            if (err != null) {
                msg.setTextColor(C.RED)
                msg.text = err
                box.shake()
            } else {
                msg.setTextColor(C.GREEN)
                msg.text = "Conta criada. Toque em Entrar."
            }
        }
        box.addView(enter, lp(top = 6))
        box.addView(create, lp(top = 10))
        col.addView(box, LinearLayout.LayoutParams(-1, -2))

        val sv = ScrollView(this)
        sv.isVerticalScrollBarEnabled = false
        sv.isFillViewport = true
        sv.addView(col, ViewGroup.LayoutParams(-1, -1))
        setScreen("login", sv)
    }

    // ------------------------------------------------------------ painel

    private fun showHub() {
        val admin = Store.isAdmin(this)
        val shizOk = Perms.shizukuGranted()
        val termOk = Perms.termuxGranted(this)
        val unlocked = shizOk || termOk
        val overlayOk = Perms.overlay(this)
        val accOk = Perms.accessibility(this)

        if (Build.VERSION.SDK_INT >= 33 && !Store.flag(this, "askedNotif") &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            Store.setFlag(this, "askedNotif", true)
            notifReq.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(18), dp(22), dp(18), dp(28))

        // cabeçalho
        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        val logo = ImageView(this)
        logo.setImageResource(R.drawable.logo)
        logo.scaleType = ImageView.ScaleType.CENTER_CROP
        logo.clipRound(dp(16f).toFloat())
        head.addView(logo, LinearLayout.LayoutParams(dp(68), dp(68)))
        val tx = LinearLayout(this)
        tx.orientation = LinearLayout.VERTICAL
        tx.setPadding(dp(14), 0, 0, 0)
        val ttl = label("NEXUS IA", 26f, Color.WHITE, true)
        ttl.setShadowLayer(dp(10f).toFloat(), 0f, 0f, C.RED)
        tx.addView(ttl)
        val tg = label("o melhor nexus jailbreak para IA", 10f, Color.WHITE, true)
        tg.rainbow()
        tx.addView(tg)
        tx.addView(label((if (admin) "ADM · " else "") + (Store.user(this) ?: ""), 12f, C.DIM, false))
        head.addView(tx, LinearLayout.LayoutParams(0, -2, 1f))
        val out = ImageView(this)
        out.setImageResource(R.drawable.ic_power)
        out.setColorFilter(C.DIM)
        out.setPadding(dp(8), dp(8), dp(8), dp(8))
        out.setOnClickListener {
            Store.logout(this)
            showLogin()
        }
        out.pressable()
        head.addView(out, LinearLayout.LayoutParams(dp(44), dp(44)))
        col.addView(head, lp(bottom = 16))

        // permissões
        val perm = card()
        perm.addView(section("PERMISSÕES"))
        perm.addView(label("Libere o Shizuku ou o Termux para ativar o flutuante.", 13f, C.DIM, false), lp(top = 4, bottom = 10))
        perm.addView(statusRow("Shizuku", shizOk, if (Perms.shizukuRunning()) "AGUARDANDO" else "PARADO"))
        if (!shizOk) {
            val running = Perms.shizukuRunning()
            perm.addView(nexusButton(if (running) "Conceder Shizuku" else "Abrir Shizuku", R.drawable.ic_shield, 12f) {
                if (running) requestShizuku() else openApp("moe.shizuku.privileged.api", "Instale e inicie o Shizuku primeiro")
            }, lp(top = 8))
        }
        perm.addView(statusRow("Termux", termOk, if (Perms.termuxInstalled(this)) "AGUARDANDO" else "NÃO INSTALADO"), lp(top = 14))
        if (!termOk) {
            perm.addView(nexusButton("Conceder Termux", R.drawable.ic_lock, 12f) {
                if (Perms.termuxInstalled(this)) termuxReq.launch(Perms.TERMUX) else toast("Instale o Termux primeiro")
            }, lp(top = 8))
        }
        if (unlocked) {
            perm.addView(statusRow("Sobreposição", overlayOk, "PENDENTE"), lp(top = 14))
            if (!overlayOk) {
                perm.addView(nexusButton("Permitir sobreposição", R.drawable.ic_eye, 12f) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                }, lp(top = 8))
            }
            perm.addView(statusRow("Acessibilidade", accOk, "PENDENTE"), lp(top = 14))
            if (!accOk) {
                perm.addView(nexusButton("Ativar acessibilidade", R.drawable.ic_settings, 12f) {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }, lp(top = 8))
            }
        }
        col.addView(perm, lp(bottom = 12))

        // IAs detectadas
        val ais = card()
        ais.addView(section("IAS NO SEU CELULAR"))
        val found = AiDetector.installed(this)
        if (found.isEmpty()) {
            ais.addView(label("Nenhuma IA conhecida encontrada.", 14f, C.DIM, false), lp(top = 8))
        } else {
            for (a in found) {
                ais.addView(label("● " + a.name, 16f, C.SILVER, true), lp(top = 8))
            }
        }
        col.addView(ais, lp(bottom = 12))

        // controles do flutuante
        val ctl = card()
        ctl.addView(section("INTERFACE FLUTUANTE"))
        val ready = unlocked && overlayOk
        val start = nexusButton(if (FloatingService.running) "Reiniciar flutuante" else "Iniciar flutuante", R.drawable.ic_ai, 14f) {
            if (FloatingService.running) stopService(Intent(this, FloatingService::class.java))
            startFloating(true)
        }
        start.alpha = if (ready) 1f else 0.4f
        ctl.addView(start, lp(top = 10))
        ctl.addView(nexusButton("Parar flutuante", R.drawable.ic_power, 13f, true) {
            stopService(Intent(this, FloatingService::class.java))
            Store.setFlag(this, "auto", true)
        }, lp(top = 8))
        col.addView(ctl, lp(bottom = 12))

        if (admin) {
            col.addView(nexusButton("Pastas por IA (ADM)", R.drawable.ic_folder, 14f) { showAdmin() }, lp())
        }

        val sv = ScrollView(this)
        sv.isVerticalScrollBarEnabled = false
        sv.addView(col, ViewGroup.LayoutParams(-1, -2))
        setScreen("hub", sv)
    }

    // ------------------------------------------------------------ admin: pastas

    private fun showAdmin() {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(18), dp(22), dp(18), dp(28))

        col.addView(label("PASTAS POR IA", 24f, Color.WHITE, true).apply {
            setShadowLayer(dp(10f).toFloat(), 0f, 0f, C.RED)
        })
        col.addView(
            label("Escolha uma pasta com arquivos .txt para cada IA. No flutuante, \"Enviar texto\" mostra os arquivos da IA que está aberta.", 13f, C.DIM, false),
            lp(top = 6, bottom = 14)
        )

        val keys = ArrayList<Pair<String, String>>()
        keys.add(Pair("geral", "Geral (todas as IAs)"))
        for (a in AiDetector.installed(this)) keys.add(Pair(a.pkg, a.name))

        for ((key, nm) in keys) {
            val c = card()
            c.addView(label(nm, 17f, Color.WHITE, true))
            val cur = Store.dir(this, key)
            val shown = if (cur == null) "sem pasta" else (
                try {
                    DocumentFile.fromTreeUri(this, Uri.parse(cur))?.name ?: "pasta escolhida"
                } catch (e: Exception) {
                    "pasta escolhida"
                }
                )
            c.addView(label("● $shown", 12f, if (cur == null) C.DIM else C.GREEN, true), lp(top = 2, bottom = 10))
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.addView(nexusButton("Escolher pasta", R.drawable.ic_folder, 12f) {
                pendingKey = key
                pickDir.launch(null)
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(6) })
            if (cur != null) {
                row.addView(nexusButton("Limpar", R.drawable.ic_close, 12f, true) {
                    Store.setDir(this, key, null)
                    showAdmin()
                }, LinearLayout.LayoutParams(-2, -2))
            }
            c.addView(row)
            col.addView(c, lp(bottom = 10))
        }

        col.addView(nexusButton("Voltar", R.drawable.ic_close, 14f, true) { showHub() }, lp(top = 6))

        val sv = ScrollView(this)
        sv.isVerticalScrollBarEnabled = false
        sv.addView(col, ViewGroup.LayoutParams(-1, -2))
        setScreen("admin", sv)
    }

    // ------------------------------------------------------------ ações

    private fun requestShizuku() {
        try {
            if (Shizuku.shouldShowRequestPermissionRationale()) {
                toast("Permissão negada. Libere nos ajustes do Shizuku.")
            } else {
                Shizuku.requestPermission(100)
            }
        } catch (t: Throwable) {
            toast("Shizuku indisponível")
        }
    }

    private fun openApp(pkg: String, fallbackMsg: String) {
        val i = packageManager.getLaunchIntentForPackage(pkg)
        if (i != null) startActivity(i) else toast(fallbackMsg)
    }

    private fun onPermChange() {
        if (screen == "hub") showHub()
        autoStartIfReady()
    }

    private fun autoStartIfReady() {
        if (!Perms.unlocked(this)) {
            Store.setFlag(this, "auto", false)
            return
        }
        if (Perms.overlay(this) && !Store.flag(this, "auto")) {
            Store.setFlag(this, "auto", true)
            startFloating(false)
        }
    }

    private fun startFloating(showToast: Boolean) {
        if (!Perms.unlocked(this)) {
            if (showToast) toast("Libere o Shizuku ou o Termux primeiro")
            return
        }
        if (!Perms.overlay(this)) {
            if (showToast) toast("Permita a sobreposição de tela")
            return
        }
        ContextCompat.startForegroundService(this, Intent(this, FloatingService::class.java))
        if (showToast) toast("Flutuante iniciado")
    }
}
