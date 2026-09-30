package com.hylia.app.analysis

import com.hylia.app.core.ContentCategory
import com.hylia.app.core.Severity

/**
 * AI 深度分析结果：全面指出内容的问题、漏洞与风险。
 */
data class AnalysisResult(
    val category: ContentCategory = ContentCategory.NORMAL,
    val score: Float = 0f,
    val severity: Severity = Severity.NONE,
    val title: String = "",
    val problems: List<String> = emptyList(),
    val vulnerabilities: List<String> = emptyList(),
    val recommendation: String = "",
    val summary: String = "",
    val fromCloud: Boolean = false,
    val latencyMs: Long = 0
) {
    val isHarmful: Boolean get() = category != ContentCategory.NORMAL

    companion object {
        fun fromJson(json: org.json.JSONObject): AnalysisResult {
            val catName = json.optString("category", "NORMAL").uppercase()
            val category = try {
                ContentCategory.valueOf(catName)
            } catch (_: Exception) {
                ContentCategory.NORMAL
            }
            val score = json.optDouble("score", 0.0).toFloat().coerceIn(0f, 1f)
            val severity = when {
                !isHarmful(category) -> Severity.NONE
                score >= 0.8f -> Severity.STRONG
                score >= 0.4f -> Severity.LIGHT
                else -> Severity.BADGE
            }
            return AnalysisResult(
                category = category,
                score = score,
                severity = severity,
                title = json.optString("title", category.displayName),
                problems = json.optJSONArray("problems").toStringList(),
                vulnerabilities = json.optJSONArray("vulnerabilities").toStringList(),
                recommendation = json.optString("recommendation", ""),
                summary = json.optString("summary", "")
            )
        }

        private fun isHarmful(c: ContentCategory) = c != ContentCategory.NORMAL

        private fun org.json.JSONArray?.toStringList(): List<String> {
            if (this == null) return emptyList()
            return (0 until length()).mapNotNull { optString(it).takeIf { s -> s.isNotBlank() } }
        }

        fun error(msg: String): AnalysisResult = AnalysisResult(
            title = "分析失败",
            summary = msg,
            fromCloud = false
        )
    }
}
