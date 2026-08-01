package com.handy.android

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import android.view.accessibility.AccessibilityNodeInfo

class AutoTypeAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onServiceConnected() {
        instance = this
        super.onServiceConnected()
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    fun insertText(text: String): Boolean {
        if (text.isBlank()) return false
        val node = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val setTextSuccess = node?.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments) == true
        if (setTextSuccess) {
            if (SettingsManager.autoSubmitEnabled(this)) {
                performImeEnter(node)
            }
            return true
        }

        val clipboardCopied = runCatching {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                ?: return@runCatching false
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Handy Transcription", text))
            true
        }.getOrDefault(false)
        if (clipboardCopied && node?.performAction(AccessibilityNodeInfo.ACTION_PASTE) == true) {
            if (SettingsManager.autoSubmitEnabled(this)) {
                performImeEnter(node)
            }
            return true
        }

        if (clipboardCopied) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(applicationContext, getString(R.string.text_copied_to_clipboard), Toast.LENGTH_LONG).show()
            }
        }
        return false
    }

    private fun performImeEnter(node: AccessibilityNodeInfo?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || node == null) return
        // ACTION_IME_ENTER is 0x00400000 in the Android accessibility API.
        // Use the numeric id so this module remains source-compatible with older SDK stubs.
        node.performAction(0x00400000)
    }

    companion object {
        @Volatile
        var instance: AutoTypeAccessibilityService? = null
            private set
    }
}
