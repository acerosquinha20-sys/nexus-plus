package com.nexus.ia

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import rikka.shizuku.Shizuku
import java.security.MessageDigest

data class Ai(val pkg: String, val name: String)

object AiDetector {
    val KNOWN = listOf(
        Ai("com.openai.chatgpt", "ChatGPT"),
        Ai("com.google.android.apps.bard", "Gemini"),
        Ai("com.anthropic.claude", "Claude"),
        Ai("com.microsoft.copilot", "Copilot"),
        Ai("com.deepseek.chat", "DeepSeek"),
        Ai("ai.x.grok", "Grok"),
        Ai("ai.perplexity.app.android", "Perplexity"),
        Ai("com.poe.android", "Poe"),
        Ai("ai.mistral.chat", "Le Chat (Mistral)"),
        Ai("ai.character.app", "Character.AI"),
        Ai("com.moonshot.kimichat", "Kimi"),
        Ai("com.larus.nova", "Doubao")
    )

    fun installed(ctx: Context): List<Ai> {
        val pm = ctx.packageManager
        return KNOWN.filter {
            try {
                pm.getPackageInfo(it.pkg, 0)
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    fun nameOf(pkg: String?): String? = KNOWN.firstOrNull { it.pkg == pkg }?.name
}

object Perms {
    const val TERMUX = "com.termux.permission.RUN_COMMAND"

    fun shizukuRunning(): Boolean = try {
        Shizuku.pingBinder()
    } catch (t: Throwable) {
        false
    }

    fun shizukuGranted(): Boolean = try {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (t: Throwable) {
        false
    }

    fun termuxInstalled(ctx: Context): Boolean = try {
        ctx.packageManager.getPackageInfo("com.termux", 0)
        true
    } catch (e: Exception) {
        false
    }

    fun termuxGranted(ctx: Context): Boolean =
        ctx.checkSelfPermission(TERMUX) == PackageManager.PERMISSION_GRANTED

    fun unlocked(ctx: Context): Boolean = shizukuGranted() || termuxGranted(ctx)

    fun overlay(ctx: Context): Boolean = Settings.canDrawOverlays(ctx)

    fun accessibility(ctx: Context): Boolean {
        val s = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        return s.split(':').any { it.startsWith(ctx.packageName + "/") }
    }
}

object Store {
    private const val ADMIN_NAME = "NEXUS"
    private const val ADMIN_HASH = "9df8f7c8cf90a2194d8361d6480277661da8d4eec50c684bf018526471f1cdfd"

    private fun p(c: Context) = c.getSharedPreferences("nexus", Context.MODE_PRIVATE)

    fun sha(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    fun login(c: Context, name: String, pass: String): Boolean {
        val n = name.trim()
        if (n.equals(ADMIN_NAME, true)) {
            if (sha(pass) == ADMIN_HASH) {
                p(c).edit().putString("user", ADMIN_NAME).putBoolean("admin", true).apply()
                return true
            }
            return false
        }
        val h = p(c).getString("u_" + n.lowercase(), null) ?: return false
        if (h != sha(pass)) return false
        p(c).edit().putString("user", n).putBoolean("admin", false).apply()
        return true
    }

    fun register(c: Context, name: String, pass: String): String? {
        val n = name.trim()
        if (n.length < 3) return "Nome muito curto (mínimo 3 letras)"
        if (pass.length < 4) return "Senha muito curta (mínimo 4)"
        if (n.equals(ADMIN_NAME, true)) return "Esse nome é reservado"
        if (p(c).contains("u_" + n.lowercase())) return "Esse nome já existe"
        p(c).edit().putString("u_" + n.lowercase(), sha(pass)).apply()
        return null
    }

    fun user(c: Context): String? = p(c).getString("user", null)
    fun isAdmin(c: Context): Boolean = p(c).getBoolean("admin", false)
    fun logout(c: Context) { p(c).edit().remove("user").remove("admin").apply() }

    fun dir(c: Context, key: String): String? = p(c).getString("dir_$key", null)
    fun setDir(c: Context, key: String, v: String?) {
        val e = p(c).edit()
        if (v == null) e.remove("dir_$key") else e.putString("dir_$key", v)
        e.apply()
    }

    fun scale(c: Context): Float = p(c).getFloat("scale", 1f)
    fun setScale(c: Context, v: Float) { p(c).edit().putFloat("scale", v).apply() }

    fun flag(c: Context, k: String): Boolean = p(c).getBoolean(k, false)
    fun setFlag(c: Context, k: String, v: Boolean) { p(c).edit().putBoolean(k, v).apply() }
}
