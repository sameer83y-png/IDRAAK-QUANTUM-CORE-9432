package com.idraak.quantum.engine

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Idraak Screen Control
 *
 * This service works only after the user manually enables
 * Idraak in Android Accessibility settings.
 */
class ScreenControlService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        ScreenControlBridge.attach(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Screen events are received here.
        // No automatic clicks or actions are performed.
    }

    override fun onInterrupt() {
        // Android interrupted the accessibility service.
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        ScreenControlBridge.detach(this)
        return super.onUnbind(intent)
    }

    internal fun clickTextOnScreen(text: String): Boolean {
        if (text.isBlank()) return false

        val root = rootInActiveWindow ?: return false
        val matches = root.findAccessibilityNodeInfosByText(text)
            ?: return false

        for (node in matches) {
            var target: AccessibilityNodeInfo? = node
            var depth = 0

            while (target != null && depth < 8) {
                if (target.isEnabled && target.isClickable) {
                    return target.performAction(
                        AccessibilityNodeInfo.ACTION_CLICK
                    )
                }

                target = target.parent
                depth++
            }
        }

        return false
    }

    internal fun scrollScreenForward(): Boolean {
        val root = rootInActiveWindow ?: return false

        return root.performAction(
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        )
    }

    internal fun scrollScreenBackward(): Boolean {
        val root = rootInActiveWindow ?: return false

        return root.performAction(
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        )
    }

    internal fun goBack(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }

    internal fun goHome(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_HOME)
    }

    internal fun openNotifications(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    }
}

/**
 * Safe bridge for future connection with Idraak's command engine.
 */
object ScreenControlBridge {

    @Volatile
    private var activeService: ScreenControlService? = null

    internal fun attach(service: ScreenControlService) {
        activeService = service
    }

    internal fun detach(service: ScreenControlService) {
        if (activeService === service) {
            activeService = null
        }
    }

    fun isServiceConnected(): Boolean {
        return activeService != null
    }

    fun tapText(text: String): Boolean {
        if (text.isBlank()) return false
        return activeService?.clickTextOnScreen(text) ?: false
    }

    fun scrollForward(): Boolean {
        return activeService?.scrollScreenForward() ?: false
    }

    fun scrollBackward(): Boolean {
        return activeService?.scrollScreenBackward() ?: false
    }

    fun back(): Boolean {
        return activeService?.goBack() ?: false
    }

    fun home(): Boolean {
        return activeService?.goHome() ?: false
    }

    fun openNotifications(): Boolean {
        return activeService?.openNotifications() ?: false
    }
}
