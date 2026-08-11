package com.hylia.app.core

data class ClassificationResult(
    val category: ContentCategory,
    val score: Float,
    val reasons: List<String>
) {
    val isHarmful: Boolean get() = category != ContentCategory.NORMAL

    val severity: Severity
        get() = when {
            !isHarmful -> Severity.NONE
            score >= 0.8f -> Severity.STRONG
            score >= 0.4f -> Severity.LIGHT
            else -> Severity.BADGE
        }
}

enum class Severity { NONE, BADGE, LIGHT, STRONG }