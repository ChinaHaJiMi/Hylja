package com.hylia.app.core

/** 敏感度档位：阈值越低越严格（更多内容被判定为不良）。 */
enum class Sensitivity(val threshold: Float, val displayName: String) {
    RELAXED(0.55f, "宽松"),
    STANDARD(0.40f, "标准"),
    STRICT(0.25f, "严格");

    companion object {
        fun fromName(name: String?): Sensitivity =
            entries.firstOrNull { it.name == name } ?: STANDARD
    }
}