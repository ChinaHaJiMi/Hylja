package com.hylia.app.intervention

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.hylia.app.R
import com.hylia.app.core.ClassificationResult
import com.hylia.app.core.ContentCategory

/**
 * 微感拦截卡片：用经典 View 构造，无需 Compose，可在无障碍服务中直接悬浮。
 *
 * 布局：
 * ┌──────────────────────────┐
 * │ ⚠ Hylia 内容提示   [高风险]│
 * │ 类别：营销带货  置信度 92%  │
 * │ 依据：限时抢购、免费领取     │
 * │ 内容预览：……              │
 * │ [返回] [继续访问] [误判]    │
 * └──────────────────────────┘
 */
object InterceptorOverlay {

    fun create(
        context: Context,
        result: ClassificationResult,
        sampleText: String,
        onAction: (OverlayAction) -> Unit
    ): View {
        val density = context.resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density).toInt()

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(dp(18), dp(16), dp(18), dp(14))
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(ContextCompat.getColor(context, R.color.overlay_card))
            }
            elevation = dp(10).toFloat()
        }

        // 标题行：标题 + 风险等级
        val titleRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleRow.addView(TextView(context).apply {
            text = context.getString(R.string.intervention_title)
            setTextSize(15f)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        titleRow.addView(TextView(context).apply {
            text = context.getString(
                if (result.severity.name == "STRONG") R.string.severity_strong
                else R.string.severity_light
            )
            setTextSize(11f)
            setTextColor(ContextCompat.getColor(context, R.color.warn))
            setPadding(dp(10), dp(3), dp(10), dp(3))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(ContextCompat.getColor(context, R.color.warn).withAlpha(0x1A))
            }
        })
        card.addView(titleRow)

        // 类别 + 置信度
        card.addView(TextView(context).apply {
            text = context.getString(R.string.intervention_category) +
                "：" + result.category.displayName + "    " +
                context.getString(R.string.intervention_confidence) +
                " ${(result.score * 100).toInt()}%"
            setTextSize(13f)
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            setPadding(0, dp(8), 0, 0)
        })

        // 判定依据
        if (result.reasons.isNotEmpty()) {
            card.addView(TextView(context).apply {
                text = context.getString(R.string.intervention_reasons) +
                    "：" + result.reasons.joinToString("、")
                setTextSize(12f)
                setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                setPadding(0, dp(4), 0, 0)
            })
        }

        // 内容预览
        card.addView(TextView(context).apply {
            text = context.getString(R.string.intervention_sample) +
                "：" + sampleText.replace(Regex("\\s+"), " ").take(64)
            setTextSize(12f)
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            setPadding(0, dp(4), 0, 0)
        })

        // 按钮行：返回（主）/ 继续访问（次）/ 误判（文本）
        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, 0)
        }

        finalizeButton(
            context, btnRow, string = { context.getString(R.string.action_misjudge) },
            primary = false
        ) { onAction(OverlayAction.MISJUDGED) }

        btnRow.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(8), 1)
        })

        finalizeButton(
            context, btnRow,
            string = { context.getString(R.string.action_continue) },
            primary = false
        ) { onAction(OverlayAction.CONTINUE_ALLOWED) }

        btnRow.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(8), 1)
        })

        finalizeButton(
            context, btnRow, string = { context.getString(R.string.action_return) },
            primary = true
        ) { onAction(OverlayAction.RETURN_BLOCKED) }

        card.addView(btnRow)
        return card
    }

    private fun finalizeButton(
        context: Context,
        row: LinearLayout,
        string: () -> String,
        primary: Boolean,
        onClick: () -> Unit
    ) {
        val button = Button(context)
        button.text = string()
        button.isAllCaps = false
        button.setOnClickListener { onClick() }
        if (primary) {
            button.setBackgroundColor(ContextCompat.getColor(context, R.color.hylia_primary))
            button.setTextColor(android.graphics.Color.WHITE)
        } else {
            button.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            button.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
        }
        row.addView(button, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }
}