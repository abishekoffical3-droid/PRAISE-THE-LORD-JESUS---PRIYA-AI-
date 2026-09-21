package com.abhishekbhusal.priya

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

object AccessibilityBridge {
    var service: PriyaAccessibilityService? = null
}

class PriyaAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() { super.onServiceConnected(); AccessibilityBridge.service = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onDestroy() { if (AccessibilityBridge.service === this) AccessibilityBridge.service = null; super.onDestroy() }

    fun scrollForward() { rootInActiveWindow?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) }
    fun scrollBack() { rootInActiveWindow?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) }
    fun goBack() = performGlobalAction(GLOBAL_ACTION_BACK)
    fun goHome() = performGlobalAction(GLOBAL_ACTION_HOME)
}
