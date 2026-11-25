
package com.idraak.quantum.engine

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

enum class ScreenSwipeDirection {
    UP, DOWN, LEFT, RIGHT
}

class ScreenControlService : AccessibilityService() {

    private data class ClickCandidate(
        val node: AccessibilityNodeInfo,
        val score: Int
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        ScreenControlBridge.attach(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Events are not stored or transmitted.
    }

    override fun onInterrupt() {
        // Android interrupted the service.
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        ScreenControlBridge.detach(this)
        return super.onUnbind(intent)
    }

    internal fun clickTextOnScreen(text: String): Boolean {
        val target = normalizeText(text)
        if (target.isBlank()) return false

        val root = rootInActiveWindow ?: return false
        val candidates = mutableListOf<ClickCandidate>()

        collectClickCandidates(
            node = root,
            target = target,
            candidates = candidates,
            depth = 0
        )

        if (candidates.isEmpty()) return false

        val bestScore = candidates.minOf { it.score }
        val bestMatches = candidates.filter {
            it.score == bestScore
        }

        // Never guess between equally matching controls.
        if (bestMatches.size != 1) return false

        val selected = bestMatches.first().node

        return try {
            selected.isEnabled &&
                selected.isVisibleToUser &&
                selected.performAction(
                    AccessibilityNodeInfo.ACTION_CLICK
                )
        } catch (_: Exception) {
            false
        }
    }

    private fun collectClickCandidates(
        node: AccessibilityNodeInfo,
        target: String,
        candidates: MutableList<ClickCandidate>,
        depth: Int
    ) {
        if (depth > 40 || candidates.size >= 100) return

        try {
            if (node.isVisibleToUser && node.isEnabled) {
                val nodeText = normalizeText(
                    node.text?.toString().orEmpty()
                )

                val description = normalizeText(
                    node.contentDescription?.toString().orEmpty()
                )

                val score = when {
                    nodeText == target || description == target -> 0

                    nodeText.contains(target) ||
                        description.contains(target) -> 1

                    else -> -1
                }

                if (score >= 0) {
                    val clickableNode = findClickableParent(node)

                    if (clickableNode != null) {
                        val alreadyAdded = candidates.any {
                            it.node == clickableNode
                        }

                        if (!alreadyAdded) {
                            candidates.add(
                                ClickCandidate(
                                    node = clickableNode,
                                    score = score
                                )
                            )
                        }
                    }
                }
            }

            for (index in 0 until node.childCount) {
                if (candidates.size >= 100) break

                val child = node.getChild(index) ?: continue

                collectClickCandidates(
                    node = child,
                    target = target,
                    candidates = candidates,
                    depth = depth + 1
                )
            }
        } catch (_: Exception) {
            // Ignore inaccessible nodes.
        }
    }

    private fun findClickableParent(
        start: AccessibilityNodeInfo
    ): AccessibilityNodeInfo? {

        var current: AccessibilityNodeInfo? = start
        var depth = 0

        while (current != null && depth < 8) {
            try {
                if (
                    current.isEnabled &&
                    current.isVisibleToUser &&
                    current.isClickable
                ) {
                    return current
                }

                current = current.parent
                depth++
            } catch (_: Exception) {
                return null
            }
        }

        return null
    }

    private fun normalizeText(value: String): String {
        return value
            .trim()
            .lowercase(Locale.ROOT)
            .replace(
                Regex("[^\\p{L}\\p{M}\\p{N}\\s]"),
                " "
            )
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    internal fun scrollScreenForward(): Boolean {
        return performScroll(
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        )
    }

    internal fun scrollScreenBackward(): Boolean {
        return performScroll(
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        )
    }

    private fun performScroll(action: Int): Boolean {
        val root = rootInActiveWindow ?: return false

        return findScrollableNode(
            node = root,
            action = action,
            depth = 0
        )
    }

    private fun findScrollableNode(
        node: AccessibilityNodeInfo,
        action: Int,
        depth: Int
    ): Boolean {
        if (depth > 40) return false

        try {
            if (
                node.isVisibleToUser &&
                node.isEnabled &&
                node.isScrollable
            ) {
                if (node.performAction(action)) {
                    return true
                }
            }

            for (index in 0 until node.childCount) {
                val child = node.getChild(index) ?: continue

                if (
                    findScrollableNode(
                        child,
                        action,
                        depth + 1
                    )
                ) {
                    return true
                }
            }
        } catch (_: Exception) {
            return false
        }

        return false
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

        return try {
            // true means Android accepted the gesture request.
            dispatchGesture(gesture, null, null)
        } catch (_: Exception) {
            false
        }
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

    internal fun lockDeviceScreen(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
    }

    internal fun openQuickSettingsPanel(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
    }

    internal fun openRecentAppsOverview(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_RECENTS)
    }

    internal fun openSystemPowerMenu(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
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

    fun lockScreen(): Boolean {
        return activeService?.lockDeviceScreen() ?: false
    }

    fun openQuickSettings(): Boolean {
        return activeService?.openQuickSettingsPanel() ?: false
    }

    fun openRecentApps(): Boolean {
        return activeService?.openRecentAppsOverview() ?: false
    }

    fun openPowerMenu(): Boolean {
        return activeService?.openSystemPowerMenu() ?: false
    }
}
