package com.idraak.quantum.engine

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

enum class ScreenSwipeDirection {
    UP, DOWN, LEFT, RIGHT
}

class ScreenControlService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        ScreenControlBridge.attach(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Screen events are monitored only.
    }

    override fun onInterrupt() {
        // Android interrupted the service.
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

    internal fun swipeScreen(
        direction: ScreenSwipeDirection
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return false
        }

        val width = resources.displayMetrics.widthPixels.toFloat()
        val height = resources.displayMetrics.heightPixels.toFloat()

        if (width <= 0f || height <= 0f) return false

        val centerX = width / 2f
        val centerY = height / 2f

        val path = Path()

        when (direction) {
            ScreenSwipeDirection.UP -> {
                path.moveTo(centerX, height * 0.78f)
                path.lineTo(centerX, height * 0.28f)
            }

            ScreenSwipeDirection.DOWN -> {
                path.moveTo(centerX, height * 0.28f)
                path.lineTo(centerX, height * 0.78f)
            }

            ScreenSwipeDirection.LEFT -> {
                path.moveTo(width * 0.82f, centerY)
                path.lineTo(width * 0.18f, centerY)
            }

            ScreenSwipeDirection.RIGHT -> {
                path.moveTo(width * 0.18f, centerY)
                path.lineTo(width * 0.82f, centerY)
            }
        }

        val gesture = GestureDescription.Builder()
            .addStroke(
                GestureDescription.StrokeDescription(
                    path,
                    0L,
                    350L
                )
            )
            .build()

        return dispatchGesture(gesture, null, null)
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

    fun swipeUp(): Boolean {
        return activeService?.swipeScreen(
            ScreenSwipeDirection.UP
        ) ?: false
    }

    fun swipeDown(): Boolean {
        return activeService?.swipeScreen(
            ScreenSwipeDirection.DOWN
        ) ?: false
    }

    fun swipeLeft(): Boolean {
        return activeService?.swipeScreen(
            ScreenSwipeDirection.LEFT
        ) ?: false
    }

    fun swipeRight(): Boolean {
        return activeService?.swipeScreen(
            ScreenSwipeDirection.RIGHT
        ) ?: false
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
