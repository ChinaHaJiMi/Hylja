package com.hylia.app.analysis

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.hylia.app.ui.AnalysisOverlay

/**
 * 分析结果悬浮窗控制器：展示 AI 分析结果，自动消失，不拦截触摸。
 */
class AnalysisController(private val context: Context) {

    private val windowManager get() = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var overlay: View? = null
    private var autoDismiss: Runnable? = null

    fun show(result: AnalysisResult) {
        dismissNow()
        val wm = windowManager ?: return

        val view = AnalysisOverlay.create(context, result)

        val density = context.resources.displayMetrics.density
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = (density * 48).toInt()
        }

        try {
            wm.addView(view, lp)
            overlay = view
        } catch (_: WindowManager.BadTokenException) {
            overlay = null
            return
        } catch (_: SecurityException) {
            overlay = null
            return
        }

        // 自动消失
        val r = Runnable { dismissNow() }
        autoDismiss = r
        handler.postDelayed(r, AUTO_DISMISS_MS)
    }

    fun dismiss() {
        autoDismiss?.let { handler.removeCallbacks(it) }
        autoDismiss = null
        dismissNow()
    }

    private fun dismissNow() {
        overlay?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: IllegalArgumentException) {
            }
        }
        overlay = null
    }

    companion object {
        private const val AUTO_DISMISS_MS = 8000L
    }
}
