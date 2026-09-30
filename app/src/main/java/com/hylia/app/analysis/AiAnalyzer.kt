package com.hylia.app.analysis

import android.util.Base64
import android.util.Log
import com.hylia.app.core.ContentCategory
import com.hylia.app.ml.ApiConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * AI 深度分析器：将截屏关键帧 + 屏幕文本交由多模态大模型分析，
 * 返回全面的问题、漏洞与风险报告。
 *
 * 支持：
 * - 多模态（文本 + 图片）：视觉模型（GPT-4o / Qwen-VL / GLM-4V 等）
 * - 纯文本：当无截图或模型不支持视觉时降级
 */
class AiAnalyzer(private val configProvider: () -> ApiConfig) {

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)
            .build()
    }

    val config: ApiConfig get() = configProvider()

    /**
     * 执行分析。
     * @param screenText 屏幕提取的文本
     * @param imageBytes 关键帧 JPEG 字节，null 表示无截图
     * @return 分析结果，失败时返回 error 结果
     */
    fun analyze(screenText: String, imageBytes: ByteArray?): AnalysisResult {
        val cfg = configProvider()
        if (!cfg.isConfigured) {
            return AnalysisResult.error("未配置 AI API")
        }

        val start = System.currentTimeMillis()
        return try {
            val body = buildRequestBody(screenText, imageBytes, cfg)
            val request = Request.Builder()
                .url(cfg.endpoint)
                .addHeader("Authorization", "Bearer ${cfg.apiKey}")
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.e(TAG, "API error: ${response.code} - ${responseBody.take(500)}")
                return AnalysisResult.error("API 错误 HTTP ${response.code}")
            }

            val result = parseResponse(responseBody)
            result.copy(
                fromCloud = true,
                latencyMs = System.currentTimeMillis() - start
            )
        } catch (e: Exception) {
            Log.e(TAG, "AI analysis failed", e)
            AnalysisResult.error("分析超时或网络异常: ${e.localizedMessage ?: "未知错误"}")
        }
    }

    private fun buildRequestBody(
        screenText: String,
        imageBytes: ByteArray?,
        cfg: ApiConfig
    ): JSONObject {
        val userContent = JSONArray()

        // 文本部分
        val textPart = JSONObject().apply {
            put("type", "text")
            put("text", buildPrompt(screenText, imageBytes != null))
        }
        userContent.put(textPart)

        // 图片部分（仅当有截图且模型可能支持视觉时）
        if (imageBytes != null && cfg.visionEnabled) {
            val b64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
            val imagePart = JSONObject().apply {
                put("type", "image_url")
                put("image_url", JSONObject().apply {
                    put("url", "data:image/jpeg;base64,$b64")
                })
            }
            userContent.put(imagePart)
        }

        val messages = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put("content", SYSTEM_PROMPT)
            })
            put(JSONObject().apply {
                put("role", "user")
                put("content", userContent)
            })
        }

        return JSONObject().apply {
            put("model", cfg.visionModel.takeIf { it.isNotBlank() && cfg.visionEnabled }
                ?: cfg.model)
            put("messages", messages)
            put("temperature", 0.2)
            put("max_tokens", 800)
        }
    }

    private fun buildPrompt(screenText: String, hasImage: Boolean): String {
        val sb = StringBuilder()
        sb.appendLine("请分析以下手机屏幕内容，判断是否存在有害信息。")
        if (hasImage) {
            sb.appendLine("已附带屏幕截图，请结合截图中的视觉信息（排版、图片、广告样式等）综合判断。")
        }
        sb.appendLine()
        sb.appendLine("【屏幕文本内容】")
        sb.appendLine(screenText.take(2000))
        sb.appendLine()
        sb.appendLine("请以 JSON 格式返回分析结果，字段如下：")
        sb.appendLine("""{
  "category": "NORMAL|MARKETING_ADS|PSEUDOSCIENCE|VULGAR_CONTENT|TOXIC_HATE|CLICKBAIT",
  "score": 0.0-1.0 的置信度,
  "title": "简短标题，概括发现的问题",
  "problems": ["问题1", "问题2", ...],
  "vulnerabilities": ["漏洞/风险1", "漏洞/风险2", ...],
  "recommendation": "给用户的建议",
  "summary": "总体评价，2-3句话"
}""")
        sb.appendLine()
        sb.appendLine("要求：")
        sb.appendLine("1. problems 字段详细列出内容中的具体问题（虚假宣传、诱导消费、谣言谬误、语言暴力等）")
        sb.appendLine("2. vulnerabilities 字段指出潜在漏洞与风险（钓鱼链接、隐私泄露、诈骗套路、逻辑漏洞等）")
        sb.appendLine("3. 只返回 JSON，不要其他文字")
        return sb.toString()
    }

    private fun parseResponse(responseBody: String): AnalysisResult {
        val json = JSONObject(responseBody)
        val choices = json.getJSONArray("choices")
        if (choices.length() == 0) return AnalysisResult.error("AI 返回空结果")

        val content = choices.getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
            .trim()

        // 提取 JSON（容忍 markdown 代码块包裹）
        val jsonStr = extractJson(content)
        return try {
            AnalysisResult.fromJson(JSONObject(jsonStr))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse AI response: $content", e)
            AnalysisResult.error("AI 返回格式异常")
        }
    }

    private fun extractJson(text: String): String {
        // 去除 ```json ... ``` 包裹
        val fenced = Regex("```(?:json)?\\s*([\\s\\S]*?)```").find(text)
        if (fenced != null) return fenced.groupValues[1].trim()
        // 找第一个 { 到最后一个 }
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start >= 0 && end > start) return text.substring(start, end + 1)
        return text
    }

    companion object {
        private const val TAG = "AiAnalyzer"

        private val SYSTEM_PROMPT = """
你是一位专业的内容安全分析师，擅长识别网络上的各类有害信息。

你的职责是：
1. 识别内容类别（营销带货、伪科学谣言、低俗烂梗、引战攻击、无聊爽文）
2. 深入分析内容中存在的具体问题和逻辑漏洞
3. 指出潜在的安全风险与危害
4. 给出专业、客观的评估

你必须始终返回合法的 JSON 格式，不包含任何 JSON 之外的文字。
        """.trimIndent()
    }
}
