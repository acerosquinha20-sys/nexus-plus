package com.nexus.ia

import android.content.Context

/**
 * Uma IA conhecida.
 * [pkgs] = apps Android; [domains] = sites (detectados pela barra de endereço do navegador).
 * Para incluir outra IA, basta adicionar uma linha em [AiDetector.KNOWN].
 * Se ela tiver app, repita o pacote em AndroidManifest.xml (queries) e res/xml/accessibility_config.xml.
 */
data class Ai(
    val id: String,
    val name: String,
    val pkgs: List<String> = emptyList(),
    val domains: List<String> = emptyList()
)

object AiDetector {
    val KNOWN = listOf(
        Ai("chatgpt", "ChatGPT", listOf("com.openai.chatgpt"), listOf("chatgpt.com", "chat.openai.com")),
        Ai("gemini", "Gemini", listOf("com.google.android.apps.bard"), listOf("gemini.google.com")),
        Ai("claude", "Claude", listOf("com.anthropic.claude"), listOf("claude.ai")),
        Ai("copilot", "Copilot", listOf("com.microsoft.copilot"), listOf("copilot.microsoft.com")),
        Ai("deepseek", "DeepSeek", listOf("com.deepseek.chat"), listOf("chat.deepseek.com", "deepseek.com")),
        Ai("grok", "Grok", listOf("ai.x.grok"), listOf("grok.com")),
        Ai("perplexity", "Perplexity", listOf("ai.perplexity.app.android"), listOf("perplexity.ai")),
        Ai("poe", "Poe", listOf("com.poe.android"), listOf("poe.com")),
        Ai("lechat", "Le Chat (Mistral)", listOf("ai.mistral.chat"), listOf("chat.mistral.ai")),
        Ai("characterai", "Character.AI", listOf("ai.character.app"), listOf("character.ai")),
        Ai("kimi", "Kimi", listOf("com.moonshot.kimichat"), listOf("kimi.com", "kimi.moonshot.cn")),
        Ai("doubao", "Doubao", listOf("com.larus.nova"), listOf("doubao.com")),
        Ai("qwen", "Qwen", emptyList(), listOf("chat.qwen.ai", "qwen.ai")),
        Ai("metaai", "Meta AI", emptyList(), listOf("meta.ai")),
        Ai("zai", "Z.ai (GLM)", emptyList(), listOf("chat.z.ai")),
        Ai("huggingchat", "HuggingChat", emptyList(), listOf("huggingface.co/chat"))
    )

    /** Navegadores onde o NEXUS lê só a barra de endereço para descobrir qual IA está aberta. */
    val BROWSERS = listOf(
        "com.android.chrome",
        "com.sec.android.app.sbrowser",
        "org.mozilla.firefox",
        "com.brave.browser",
        "com.microsoft.emmx",
        "com.opera.browser",
        "com.kiwibrowser.browser",
        "com.duckduckgo.mobile.android"
    )

    private fun isInstalled(ctx: Context, pkg: String): Boolean = try {
        ctx.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (e: Exception) {
        false
    }

    fun installed(ctx: Context): List<Ai> = KNOWN.filter { ai -> ai.pkgs.any { isInstalled(ctx, it) } }

    fun byPackage(pkg: String?): Ai? =
        if (pkg == null) null else KNOWN.firstOrNull { pkg in it.pkgs }

    fun byUrl(text: String?): Ai? {
        val t = text?.lowercase() ?: return null
        return KNOWN.firstOrNull { ai -> ai.domains.any { t.contains(it) } }
    }

    fun isBrowser(pkg: String?): Boolean = pkg != null && pkg in BROWSERS

    /** Ids de views que parecem barra de endereço (para ler a URL e para NÃO tratar como chat). */
    fun isUrlViewId(id: String?): Boolean {
        if (id == null) return false
        val s = id.lowercase()
        return s.contains("url") || s.contains("location_bar") || s.contains("omnibox") || s.contains("address")
    }
}
