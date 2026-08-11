package com.hylia.app.intervention

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.hylia.app.core.ClassificationResult

/** 负责把拦截卡片以无障碍悬浮窗的形式展示与回收。 */
class InterventionController(
    private val context: Context,
    private val listener: InterventionListener
) {

    private val windowManager get() = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    private var overlay: View? = null

    fun show(result: ClassificationResult, sampleText: String, packageName: String, textHash: String) {
        if (overlay != null || windowManager == null) return

        val view = InterceptorOverlay.create(context, result, sampleText) { action ->
            when (action) {
                OverlayAction.RETURN_BLOCKED -> listener.onReturnBlocked(result)
                OverlayAction.CONTINUE_ALLOWED ->
                    listener.onContinueAllowed(result, packageName, textHash)
                OverlayAction.MISJUDGED -> listener.onMisjudged(result)
            }
            dismiss()
        }

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = (context.resources.displayMetrics.density * 48).toInt()
        }

        try {
            windowManager.addView(view, lp)
            overlay = view
        } catch (_: WindowManager.BadTokenException) {
            overlay = null
        } catch (_: SecurityException) {
            overlay = null
        }
    }

    fun dismiss() {
        overlay?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: IllegalArgumentException) {
            }
        }
        overlay = null
    }