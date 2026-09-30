package com.hylia.app.ml

import android.util.Log
import com.hylia.app.core.ClassificationResult
import com.hylia.app.core.ContentCategory
import com.hylia.app.core.Sensitivity
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class CloudClassifier(private val configProvider: () -> ApiConfig) : TextClassifier {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val config: ApiConfig get() = configProvider()

    override fun classify(text: String, sensitivity: Sensitivity): ClassificationResult {
        val config = configProvider()
        if (!config.isConfigured) {
            return ClassificationResult(ContentCategory.NORMAL, 0f, emptyList())
        }

        return try {
            val requestBody = buildRequestBody(text, config)
            val request = Request.Builder()
                .url(config.endpoint)
                .addHeader("Authorization", "Bearer ${config.apiKey}")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.e(TAG, "API error: ${response.code} - $responseBody")
                return ClassificationResult(ContentCategory.NORMAL, 0f, emptyList())
            }

            parseResponse(responseBody, sensitivity)
        } catch (e: Exception) {
            Log.e(TAG, "Cloud classification failed", e)
            ClassificationResult(ContentCategory.NORMAL, 0f, emptyList())
        }
    }

    private fun buildRequestBody(text: String, config: ApiConfig): JSONObject {
        val messages = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put("content", SYSTEM_PROMPT)
            })
            put(JSONObject().apply {
                put("role", "user")
                put("content", text.take(MAX_TEXT_LENGTH))
            })
        }

        return JSONObject().apply {
            put("model", config.model)
            put("messages", messages)
            put("temperature", 0.1)
            put("max_tokens", 100)
        }
    }

    private fun parseResponse(responseBody: String, sensitivity: Sensitivity): ClassificationResult {
        val json = JSONObject(responseBody)
        val choices = json.getJSONArray("choices")
        if (choices.length() == 0) {
            return ClassificationResult(ContentCategory.NORMAL, 0f, emptyList())
        }

        val content = choices.getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
            .trim()

        return parseClassificationResult(content, sensitivity)
    }

    private fun parseClassificationResult(content: String, sensitivity: Sensitivity): ClassificationResult {
        val lines = content.lines()
        var category = ContentCategory.NORMAL
        var score = 0f
        val reasons = mutableListOf<String>()

        for (line in lines) {
            when {
                line.startsWith("CATEGORY:") -> {
                    val catName = line.substringAfter("CATEGORY:").trim().uppercase()
                    category = try {
                        ContentCategory.valueOf(catName)
                    } catch (_: Exception) {
                        ContentCategory.NORMAL
                    }
                }
                line.startsWith("SCORE:") -> {
                    score = line.substringAfter("SCORE:").trim().toFloatOrNull() ?: 0f
                }
                line.startsWith("REASONS:") -> {
                    val reasonsStr = line.substringAfter("REASONS:").trim()
                    if (reasonsStr.isNotBlank()) {
                        reasons.addAll(reasonsStr.split(",").map { it.trim() }.filter { it.isNotBlank() })
                    }
                }
            }
        }

        val harmful = score >= sensitivity.threshold && category != ContentCategory.NORMAL
        return if (harmful) {
            ClassificationResult(category, score.coerceAtMost(1f), reasons.take(3))
        } else {
            ClassificationResult(ContentCategory.NORMAL, score, emptyList())
        }
    }

    companion object {
        private const val TAG = "CloudClassifier"
        private const val MAX_TEXT_LENGTH = 500
        private val SYSTEM_PROMPT = """
你是一个内容分类器。分析用户提供的文本，判断其内容类别。

返回格式（严格遵循，每行一个字段）：
CATEGORY: 类别名（NORMAL/MARKETING_ADS/PSEUDOSCIENCE/VULGAR_CONTENT/TOXIC_HATE/CLICKBAIT）
SCORE: 置信度分数（0.0-1.0）
REASONS: 判定依据（逗号分隔，最多3个）

类别说明：
- NORMAL: 正常内容
- MARKETING_ADS: 营销带货、广告推销
- PSEUDOSCIENCE: 伪科学、谣言、偏方
- VULGAR_CONTENT: 低俗烂梗、网络用语
- TOXIC_HATE: 引战攻击、人身攻击
- CLICKBAIT: 标题党、夸张营销

只返回分析结果，不要其他解释。
        """.trimIndent()
    }
}
