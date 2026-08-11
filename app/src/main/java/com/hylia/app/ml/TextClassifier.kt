package com.hylia.app.ml

import com.hylia.app.core.ClassificationResult
import com.hylia.app.core.Sensitivity

/**
 * 内容分类器接口。
 *
 * M1 里程碑将在此处接入量化后的多标签深度学习模型（MNN/TFLite），
 * 规则引擎仅作为 M0 的快速路径与兜底。
 */
interface TextClassifier {
    fun classify(text: String, sensitivity: Sensitivity): ClassificationResult
}