package com.hylia.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import com.hylia.app.HyliaApp
import com.hylia.app.R
import com.hylia.app.analysis.AnalysisController
import com.hylia.app.analysis.AnalysisResult
import com.hylia.app.capture.KeyframeExtractor
import com.hylia.app.capture.ScreenshotCapturer
import com.hylia.app.core.Severity
import com.hylia.app.prefilter.NodeTextExtractor
import java.util.concurrent.Executors
import kotlin.math.max

/**
 * Hylia AI 内容分析守护服务。
 *
 * 新流程：
 *   屏幕事件 → 文本提取 → 本地规则预筛（快速）
 *     → 可疑/不确定 → 截屏 → 关键帧提取 → AI 深度分析（异步）
 *       → 展示分析结果（纯展示，无按钮，自动消失）
 */
class HyliaAccessibilityService : AccessibilityService() {

    private lateinit var analysisController: AnalysisController
    private val app: HyliaApp get() = application as HyliaApp

    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val recentHashes = ArrayDeque<String>()
    private var lastShownAt = 0L
    private var shownInCurrentWindow = 0
    private var currentPkg = ""
    private var analyzing = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        analysisController = AnalysisController(this)
        serviceInfo = serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        showStatusNotification()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg.startsWith("com.hylia")) return

        val isNewWindow = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        if (isNewWindow && pkg != currentPkg) {
            currentPkg = pkg
            shownInCurrentWindow = 0
        }

        val now = SystemClock.elapsedRealtime()
        if (now - lastShownAt < MIN_SHOW_INTERVAL_MS) return
        if (shownInCurrentWindow >= MAX_SHOW_PER_WINDOW) return
        if (analyzing) return

        val texts = NodeTextExtractor.extract(event.source)
        if (texts.isEmpty()) return

        val combinedText = texts.joinToString("\n")
        val hash = hash(combinedText)
        if (recentHashes.contains("$pkg|$hash")) return

        // 本地规则预筛：仅当可能有害时才触发截屏 + AI 分析
        val sensitivity = app.profileStore.state.value.sensitivity
        val preFiltered = quickPreFilter(combinedText, sensitivity)
        if (!preFiltered) return

        // 触发异步分析流程
        analyzing = true
        rememberHash("$pkg|$hash")
        lastShownAt = now
        shownInCurrentWindow++

        runAnalysis(combinedText, pkg)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        analysisController.dismiss()
        ioExecutor.shutdownNow()
        super.onDestroy()
    }

    /**
     * 本地规则预筛：判断文本是否"值得"交给 AI 分析。
     * 返回 true 表示可疑，需要进一步 AI 分析。
     */
    private fun quickPreFilter(text: String, sensitivity: com.hylia.app.core.Sensitivity): Boolean {
        // 简单启发式：命中关键词特征或文本较长（可能包含广告/谣言）
        val lower = text.lowercase()
        val suspiciousMarkers = listOf(
            "限时", "免费领取", "点击", "链接", "私信", "扫码", "领取",
            "包治", "根治", "秘方", "抗癌", "震惊", "月入", "暴富",
            "傻逼", "脑残", "去死", "垃圾", "滚", "妈的"
        )
        val hitCount = suspiciousMarkers.count { lower.contains(it) }
        if (hitCount >= 2) return true
        if (hitCount >= 1 && sensitivity.ordinal >= com.hylia.app.core.Sensitivity.STANDARD.ordinal) return true

        // 感叹号密集
        val bangs = text.count { it == '！' || it == '!' }
        if (bangs >= 3) return true

        // URL 存在
        if (Regex("""https?://|www\.|[a-z0-9-]+\.(com|cn|top|cc|vip|shop)""").containsMatchIn(lower)) return true

        return false
    }

    private fun runAnalysis(screenText: String, pkg: String) {
        val apiConfig = app.profileStore.apiConfig

        ioExecutor.execute {
            var imageBytes: ByteArray? = null
            var bitmap: android.graphics.Bitmap? = null

            // 1. 截屏
            if (apiConfig.isConfigured) {
                val latch = java.util.concurrent.CountDownLatch(1)
                mainHandler.post {
                    ScreenshotCapturer.capture(this) { bmp ->
                        bitmap = bmp
                        latch.countDown()
                    }
                }
                try {
                    latch.await(6, java.util.concurrent.TimeUnit.SECONDS)
                } catch (_: InterruptedException) {
                }

                // 2. 关键帧提取
                bitmap?.let { bmp ->
                    imageBytes = KeyframeExtractor.extract(bmp)
                    bmp.recycle()
                }
            }

            // 3. AI 深度分析
            val analyzer = com.hylia.app.analysis.AiAnalyzer { app.profileStore.apiConfig }
            val result = if (apiConfig.isConfigured) {
                analyzer.analyze(screenText, imageBytes)
            } else {
                // 未配置 API：返回本地预筛结果
                AnalysisResult(
                    title = "本地检测到可疑内容",
                    summary = "未配置 AI API，仅基于本地规则判断。请在设置中配置云端分析以获得完整报告。",
                    fromCloud = false
                )
            }

            // 4. 展示结果
            mainHandler.post {
                analyzing = false
                if (result.isHarmful || result.fromCloud) {
                    analysisController.show(result)
                    updateStatusNotification()
                }
            }
        }
    }

    private fun rememberHash(hash: String) {
        if (recentHashes.size >= MAX_RECENT) recentHashes.removeFirst()
        recentHashes.addLast(hash)
    }

    private fun hash(text: String): String = text.trim().hashCode().toString()

    private fun showStatusNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
        updateStatusNotification()
    }

    private fun updateStatusNotification() {
        val s = app.profileStore.state.value
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_shield)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(
                getString(
                    R.string.notification_text,
                    s.todayBlocked,
                    s.todayContinued,
                    max(0, s.todayEarned)
                )
            )
            .setOngoing(true)
            .setShowWhen(false)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    private companion object {
        const val TAG = "HyliaService"
        const val CHANNEL_ID = "hylia_status"
        const val NOTIFICATION_ID = 1
        const val MAX_SHOW_PER_WINDOW = 3
        const val MIN_SHOW_INTERVAL_MS = 10_000L
        const val MAX_RECENT = 200
    }
}
