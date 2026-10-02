package com.edgelite.panel

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo

class EdgeAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: EdgeAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    fun toggleSplitScreen(): Boolean = performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)

    /** Jumlah jendela aplikasi yang sedang tampil (2 atau lebih berarti split screen). */
    fun appWindowCount(): Int = windows.count { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
}
