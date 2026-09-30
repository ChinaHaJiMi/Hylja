package com.hylia.app.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.hylia.app.R
import com.hylia.app.analysis.AnalysisResult
import com.hylia.app.core.ContentCategory
import com.hylia.app.core.Severity

/**
 * AI 分析结果展示卡片（纯展示，无交互按钮）。
 *
 * 布局：
 * ┌──────────────────────────────────┐
 * │ ⚠ Hylia AI 分析    [高风险]       │
 * │ 标题：营销带货内容  置信度 92%     │
 * │ ─────────────────────────────    │
 * │ 问题分析：                        │
 * │ • 问题1                           │
 * │ • 问题2                           │
 * │ ─────────────────────────────    │
 * │ 漏洞与风险：                      │
 * │ • 漏洞1                           │
 * │ ─────────────────────────────    │
 * │ 建议：……                          │
 * │ 总结：……                          │
 * │ 自动消失                          │
 * └──────────────────────────────────┘
 */
object AnalysisOverlay {

    fun create(context: Context, result: AnalysisResult): View {
        val density = context.resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density).toInt()

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(dp(16), dp(14), dp(16), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(ContextCompat.getColor(context, R.color.overlay_card))
                setStroke(dp(2), severityColor(context, result.severity))
            }
            elevation = dp(8).toFloat()
        }

        // ---- 标题行 ----
        val titleRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleRow.addView(TextView(context).apply {
            text = context.getString(R.string.analysis_title)
            setTextSize(14f)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        titleRow.addView(TextView(context).apply {
            text = when (result.severity) {
                Severity.STRONG -> context.getString(R.string.severity_strong)
                Severity.LIGHT -> context.getString(R.string.severity_light)
                else -> context.getString(R.string.severity_info)
            }
            setTextSize(11f)
            setTextColor(android.graphics.Color.WHITE)
            setPadding(dp(8), dp(2), dp(8), dp(2))
            background = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(severityColor(context, result.severity))
            }
        })
        card.addView(titleRow)

        // ---- 标题 + 置信度 ----
        val headline = result.title.ifBlank { result.category.displayName }
        card.addView(TextView(context).apply {
            text = "$headline  ·  ${context.getString(R.string.analysis_confidence)} ${(result.score * 100).toInt()}%"
            setTextSize(13f)
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            setPadding(0, dp(6), 0, 0)
        })

        // ---- 滚动内容区（问题 + 漏洞 + 建议 + 总结）----
        val scroll = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            isFillViewport = false
        }
        val inner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(6), 0, 0)
        }

        // 问题分析
        if (result.problems.isNotEmpty()) {
            inner.addView(sectionHeader(context, R.string.analysis_problems))
            result.problems.take(6).forEach { inner.addView(bullet(context, it)) }
        }

        // 漏洞与风险
        if (result.vulnerabilities.isNotEmpty()) {
            inner.addView(sectionHeader(context, R.string.analysis_vulnerabilities))
            result.vulnerabilities.take(6).forEach { inner.addView(bullet(context, it)) }
        }

        // 建议
        if (result.recommendation.isNotBlank()) {
            inner.addView(sectionHeader(context, R.string.analysis_recommendation))
            inner.addView(bodyText(context, result.recommendation))
        }

        // 总结
        if (result.summary.isNotBlank()) {
            inner.addView(sectionHeader(context, R.string.analysis_summary))
            inner.addView(bodyText(context, result.summary))
        }

        // 云端标记
        if (result.fromCloud) {
            inner.addView(TextView(context).apply {
                text = context.getString(
                    R.string.analysis_cloud_meta,
                    result.latencyMs / 1000.0
                )
                setTextSize(10f)
                setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                setPadding(0, dp(6), 0, 0)
                gravity = Gravity.END
            })
        }

        scroll.addView(inner)
        card.addView(scroll)

        return card
    }

    private fun sectionHeader(context: Context, resId: Int): TextView =
        TextView(context).apply {
            text = context.getString(resId)
            setTextSize(12f)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(context, R.color.hylia_primary))
            setPadding(0, dp(context, 6), 0, dp(context, 2))
        }

    private fun bullet(context: Context, text: String): TextView =
        TextView(context).apply {
            this.text = "• $text"
            setTextSize(12f)
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            setPadding(dp(context, 8), dp(context, 1), 0, dp(context, 1))
        }

    private fun bodyText(context: Context, text: String): TextView =
        TextView(context).apply {
            this.text = text
            setTextSize(12f)
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            setPadding(dp(context, 8), 0, 0, 0)
        }

    private fun severityColor(context: Context, severity: Severity): Int = when (severity) {
        Severity.STRONG -> ContextCompat.getColor(context, R.color.warn)
        Severity.LIGHT -> ContextCompat.getColor(context, R.color.hylia_primary)
        else -> ContextCompat.getColor(context, R.color.text_secondary)
    }

    private fun dp(context: Context, v: Int): Int =
        (v * context.resources.displayMetrics.density).toInt()
}
