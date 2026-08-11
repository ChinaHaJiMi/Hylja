package com.hylia.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import com.hylia.app.HyliaApp
import com.hylia.app.R
import com.hylia.app.core.ClassificationResult
import com.hylia.app.core.Severity
import com.hylia.app.intervention.InterventionController
import com.hylia.app.intervention.InterventionListener
import com.hylia.app.ml.RuleClassifier
import com.hylia.app.prefilter.NodeTextExtractor
import kotlin.math.max

/**
 * Hylia 无障碍守护服务。
 *
 * 流程：节点树抽文本 → 规则/模型分类 → 命中则弹出微感拦截卡片。
 * 用户决策（返回/继续/误判）回督导学引擎。
 */
class HyliaAccessibilityService : AccessibilityService(), InterventionListener {

    private lateinit var classifier: RuleClassifier
    private lateinit var controller: InterventionController
    private val app: HyliaApp get() = application as HyliaApp

    private val recentHashes = ArrayDeque<String>()
    private val sessionAllowed = HashSet<String>()
    private var lastShownAt = 0L
    private var shownInCurrentWindow = 0
    private var currentPkg = ""

    override fun onServiceConnected() {
        super.onServiceConnected()
        classifier = RuleClassifier()
        controller = InterventionController(this, this)
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

        val texts = NodeTextExtractor.extract(event.source)
        if (texts.isEmpty()) return

        val sensitivity = app.profileStore.state.value.sensitivity
        val best = texts
            .mapNotNull { text ->
                val result = classifier.classify(text, sensitivity)
                if (result.isHarmful && result.severity != Severity.BADGE) {
                    val hash = hash(text)
                    if (sessionAllowed.contains("$pkg|$hash")) null else Triple(result, text, hash)
                } else null
            }
            .maxByOrNull { it.first.severity.ordinal * 100_000 + (it.first.score * 1000).toInt() }

        if (best == null) return

        val (result, sample, textHash) = best
        if (recentHashes.contains("$pkg|$textHash")) return

        controller.show(result, sample, pkg, textHash)
        lastShownAt = now
        shownInCurrentWindow++
        rememberHash("$pkg|$textHash")
        updateStatusNotification()
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        controller.dismiss()
        super.onDestroy()
    }

    // ---- 用户决策入口（体现微感 + 督学闭环） ----

    override fun onReturnBlocked(result: ClassificationResult) {
        app.coachingEngine.onReturnBlocked()
        updateStatusNotification()
    }

    override fun onContinueAllowed(result: ClassificationResult, packageName: String, textHash: String) {
        app.coachingEngine.onContinueAllowed()
        sessionAllowed.add("$packageName|$textHash")
        rememberHash("$packageName|$textHash")
        updateStatusNotification()
    }

    override fun onMisjudged(result: ClassificationResult) {
        app.coachingEngine.onMisjudgeReported()
        // TODO(M1): 匿名化上报该样本进入标注流水线
        rememberHash(MISJUDGE_MARK)
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
        const val CHANNEL_ID = "hylia_status"
        const val NOTIFICATION_ID = 1
        const val MAX_SHOW_PER_WINDOW = 3
        const val MIN_SHOW_INTERVAL_MS = 8_000L
        const val MAX_RECENT = 200
        const val MISJUDGE_MARK = "__misjudged__"
    }
}