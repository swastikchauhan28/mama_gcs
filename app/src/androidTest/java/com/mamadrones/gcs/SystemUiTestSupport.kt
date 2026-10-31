package com.mamadrones.gcs

import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** A fresh Android image shows this education window the first time we go fullscreen.
 * Acknowledge only that system-owned prompt, never ANRs, permissions, or app dialogs.
 * Uses the instrumentation's existing automation connection, not a second client.
 */
internal fun acknowledgeFullscreenEducation() {
    val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
    val original = automation.serviceInfo
    val withIds = automation.serviceInfo.apply {
        flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
    }
    automation.serviceInfo = withIds
    try {
        val root = automation.rootInActiveWindow ?: return
        if (root.packageName?.toString() != "android") return
        val title = root.findAccessibilityNodeInfosByViewId("android:id/immersive_cling_title")
        if (title.none { it.isVisibleToUser }) return
        root.findAccessibilityNodeInfosByViewId("android:id/ok")
            .firstOrNull { it.isVisibleToUser && it.isEnabled && it.isClickable }
            ?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    } finally {
        automation.serviceInfo = original
    }
}

/** Compose can be idle while Android's system screenshot compositor is not ready.
 * Keep the real window capture used for visual evidence, with a bounded failure
 * if the platform never returns pixels; never substitute a fixture/mock image.
 */
internal fun ComposeTestRule.awaitSystemScreenshot(): Bitmap {
    waitForIdle()
    val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
    var screenshot: Bitmap? = null
    waitUntil(10_000) {
        screenshot = automation.takeScreenshot()
        screenshot != null
    }
    return requireNotNull(screenshot) { "Android system screenshot unavailable after 10 seconds" }
}

/** A newly opened dialog can receive focus before Android attaches the IME.
 * Retry the same user focus action once, then require real IME insets; do not
 * replace the platform keyboard with a fixture or accept a hidden keyboard.
 */
internal fun ComposeTestRule.awaitRealKeyboard(visible: () -> Boolean, refocus: () -> Unit) {
    try {
        waitUntil(5_000) { visible() }
    } catch (_: androidx.compose.ui.test.ComposeTimeoutException) {
        println("IME not visible after initial focus; retrying the same field once")
        refocus()
        waitUntil(20_000) { visible() }
    }
}

/** Compose semantics may be idle before the rotated window has submitted a frame.
 * Wait for a real render commit, then the next vsync, before taking system pixels.
 * This still captures native map surfaces through UiAutomation, not a drawn mock.
 */
internal fun ComposeTestRule.awaitScreenshotFrame(view: View) {
    waitForIdle()
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val committed = CountDownLatch(1)
    val callback = Runnable { view.postOnAnimation { committed.countDown() } }
    var usesCommitCallback = false
    instrumentation.runOnMainSync {
        assertTrue("Screenshot view must be attached and laid out", view.isAttachedToWindow && view.isLaidOut)
        if (Build.VERSION.SDK_INT >= 29 && view.isHardwareAccelerated) {
            usesCommitCallback = true
            view.viewTreeObserver.registerFrameCommitCallback(callback)
            view.postInvalidateOnAnimation()
        } else {
            // API 26-28 have no frame-commit callback. Cross two actual vsyncs.
            view.postOnAnimation { view.invalidate(); view.postOnAnimation { committed.countDown() } }
        }
    }
    try {
        assertTrue("Timed out waiting for screenshot render commit", committed.await(10, TimeUnit.SECONDS))
    } finally {
        if (Build.VERSION.SDK_INT >= 29 && usesCommitCallback) instrumentation.runOnMainSync {
            if (view.viewTreeObserver.isAlive) view.viewTreeObserver.unregisterFrameCommitCallback(callback)
        }
    }
}

/** These station screens have an opaque, non-black header across the window.
 * Reject rotation's black/partially blank compositor buffers, not just null images.
 * Small patches avoid mistaking a black text glyph for a missing surface.
 */
private fun hasRenderedStationSurface(bitmap: Bitmap, view: View): Boolean {
    val bounds = Rect()
    InstrumentationRegistry.getInstrumentation().runOnMainSync { view.getGlobalVisibleRect(bounds) }
    if (bounds.isEmpty || bounds.left < 0 || bounds.top < 0 ||
        bounds.right > bitmap.width || bounds.bottom > bitmap.height) return false
    val y = bounds.top + bounds.height() / 10
    return listOf(0.1f, 0.5f, 0.9f).all { fraction ->
        val x = bounds.left + (bounds.width() * fraction).toInt()
        (-4..4).any { dx -> (-4..4).any { dy ->
            val color = bitmap.getPixel((x + dx).coerceIn(0, bitmap.width - 1),
                (y + dy).coerceIn(0, bitmap.height - 1))
            maxOf((color shr 16) and 255, (color shr 8) and 255, color and 255) >= 16
        } }
    }
}

/** Frame commits can precede the end of the system rotation animation.
 * Require real header pixels to settle, excluding the asynchronous map body.
 */
internal class StationScreenshotStability {
    private var signature: Long? = null
    private var unchangedSince = 0L
    private var rejectedSurfaces = 0
    private var changedSignatures = 0
    private var waitingForSettle = 0

    fun diagnostics(): String = "rejected surfaces=$rejectedSurfaces, changed signatures=$changedSignatures, " +
        "waiting for 500 ms=$waitingForSettle"

    fun accepts(bitmap: Bitmap, view: View): Boolean {
        if (!hasRenderedStationSurface(bitmap, view)) {
            rejectedSurfaces++
            signature = null
            return false
        }
        var current = bitmap.width.toLong() * 31 + bitmap.height
        for (y in 0 until bitmap.height / 8 step 4) {
            for (x in 0 until bitmap.width step 4) {
                current = current * 31 + bitmap.getPixel(x, y)
            }
        }
        val now = SystemClock.uptimeMillis()
        if (current != signature) {
            changedSignatures++
            signature = current
            unchangedSince = now
        }
        val settled = now - unchangedSince >= 500L
        if (!settled) waitingForSettle++
        return settled
    }
}
