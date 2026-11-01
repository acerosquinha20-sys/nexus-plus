package com.nexus.ia

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Só enxerga os apps de IA listados em accessibility_config.xml.
 * Serve para achar o campo de texto do chat e preencher o texto escolhido.
 */
class NexusAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: NexusAccessibilityService? = null
    }

    @Volatile
    var lastAiPkg: String? = null

    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        val p = e?.packageName?.toString() ?: return
        if (AiDetector.nameOf(p) != null) lastAiPkg = p
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

    private fun findEditable(n: AccessibilityNodeInfo?, depth: Int): AccessibilityNodeInfo? {
        if (n == null || depth > 40) return null
        if (n.isEditable && n.isVisibleToUser) return n
        for (i in 0 until n.childCount) {
            val r = findEditable(n.getChild(i), depth + 1)
            if (r != null) return r
        }
        return null
    }

    private fun findInput(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        if (AiDetector.nameOf(root.packageName?.toString()) == null) return null
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
