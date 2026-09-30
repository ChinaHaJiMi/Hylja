package com.hylia.app.ml

data class ApiConfig(
    val endpoint: String = DEFAULT_ENDPOINT,
    val apiKey: String = "",
    val model: String = DEFAULT_MODEL,
    val enabled: Boolean = false,
    val timeoutSeconds: Long = 30,
    val visionEnabled: Boolean = true,
    val visionModel: String = DEFAULT_VISION_MODEL
) {
    val isConfigured: Boolean get() = enabled && apiKey.isNotBlank()

    companion object {
        const val DEFAULT_ENDPOINT = "https://api.deepseek.com/v1/chat/completions"
        const val DEFAULT_MODEL = "deepseek-chat"
        const val DEFAULT_VISION_MODEL = "deepseek-chat"
    }
}
