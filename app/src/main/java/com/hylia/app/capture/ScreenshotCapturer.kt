package com.hylia.app.capture

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Display
import androidx.annotation.RequiresApi

/**
 * 屏幕截取模块。
 * API 30+ 使用 AccessibilityService.takeScreenshot()，低版本返回 null（降级为纯文本分析）。
 */
object ScreenshotCapturer {

    fun capture(service: AccessibilityService, callback: (Bitmap?) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            captureApi30(service, callback)
        } else {
            callback(null)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun captureApi30(service: AccessibilityService, callback: (Bitmap?) -> Unit) {
        val handler = Handler(Looper.getMainLooper())
        var delivered = false

        val fallback = Runnable {
            if (!delivered) {
                delivered = true
                callback(null)
            }
        }
        handler.postDelayed(fallback, 5000L)

        try {
            service.takeScreenshot(
                Display.DEFAULT_DISPLAY,
                service.mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                        handler.removeCallbacks(fallback)
                        if (delivered) {
                            result.hardwareBuffer.close()
                            return
                        }
                        delivered = true
                        val bitmap = Bitmap.wrapHardwareBuffer(
                            result.hardwareBuffer,
                            result.colorSpace
                        )
                        result.hardwareBuffer.close()
                        callback(bitmap?.copy(Bitmap.Config.ARGB_8888, false).also {
                            bitmap?.recycle()
                        })
                    }

                    override fun onFailure(errorCode: Int) {
                        handler.removeCallbacks(fallback)
                        if (!delivered) {
                            delivered = true
                            callback(null)
                        }
                    }
                }
            )
        } catch (e: Exception) {
            handler.removeCallbacks(fallback)
            if (!delivered) {
                delivered = true
                callback(null)
            }
        }
    }
}
