package com.nexus.ia

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Só enxerga os apps listados em accessibility_config.xml (apps de IA + navegadores).
 * Descobre qual IA está aberta (app ou site), acha o campo de texto do chat e preenche o texto escolhido.
 */
class NexusAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: NexusAccessibilityService? = null
        private const val KEEP_MS = 4000L
        private const val MAX_NODES = 600
    }

    @Volatile private var current: Ai? = null
    @Volatile var currentWeb: Boolean = false
        private set
    @Volatile private var seenAt = 0L
    private var lastScan = 0L
    private var visited = 0

    /** IA aberta agora (some sozinha depois de alguns segundos fora dela). */
    fun currentAi(): Ai? =
        if (SystemClock.uptimeMillis() - seenAt > KEEP_MS) null else current

    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e == null) return
        val now = SystemClock.uptimeMillis()
        val important = e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        if (!important && now - lastScan < 1500L) return
        scan()
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    /** Olha a janela ativa e atualiza qual IA está aberta. */
    fun scan() {
        lastScan = SystemClock.uptimeMillis()
        val root = rootInActiveWindow ?: return
        val pkg = root.packageName?.toString() ?: return
        var ai = AiDetector.byPackage(pkg)
        var web = false
        if (ai == null && AiDetector.isBrowser(pkg)) {
            visited = 0
            ai = AiDetector.byUrl(findUrl(root, 0))
            web = ai != null
        }
        current = ai
        currentWeb = web
        if (ai != null) seenAt = lastScan
    }

    private fun findUrl(n: AccessibilityNodeInfo?, depth: Int): String? {
        if (n == null || depth > 14 || ++visited > MAX_NODES) return null
        if (AiDetector.isUrlViewId(n.viewIdResourceName)) {
            val t = n.text?.toString()
            if (!t.isNullOrBlank()) return t
        }
        for (i in 0 until n.childCount) {
            val r = findUrl(n.getChild(i), depth + 1)
            if (r != null) return r
        }
        return null
    }

    private fun findEditable(n: AccessibilityNodeInfo?, depth: Int): AccessibilityNodeInfo? {
        if (n == null || depth > 40) return null
        if (n.isEditable && n.isVisibleToUser && !AiDetector.isUrlViewId(n.viewIdResourceName)) return n
        for (i in 0 until n.childCount) {
            val r = findEditable(n.getChild(i), depth + 1)
            if (r != null) return r
        }
        return null
    }

    private fun findInput(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        val pkg = root.packageName?.toString()
        val isAiApp = AiDetector.byPackage(pkg) != null
        val isAiSite = AiDetector.isBrowser(pkg) && currentAi() != null
        if (!isAiApp && !isAiSite) return null
        return findEditable(root, 0)
    }

    fun findInputBounds(): Rect? {
        val n = findInput() ?: return null
        val r = Rect()
        n.getBoundsInScreen(r)
        return r
    }

    fun setInputText(text: String): Boolean {
        val n = findInput() ?: return false
        val b = Bundle()
        b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        return n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b)
    }
}
