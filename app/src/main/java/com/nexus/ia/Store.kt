package com.nexus.ia

import android.content.Context
import java.security.MessageDigest

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

    /** Pastas antigas eram guardadas pelo pacote do app; agora cada IA tem um id próprio. */
    fun migrate(c: Context) {
        for (ai in AiDetector.KNOWN) {
            if (dir(c, ai.id) != null) continue
            val old = ai.pkgs.firstNotNullOfOrNull { dir(c, it) } ?: continue
            setDir(c, ai.id, old)
        }
    }
}
