package com.hylia.app.ml

import com.hylia.app.core.ClassificationResult
import com.hylia.app.core.ContentCategory
import com.hylia.app.core.Sensitivity
import com.hylia.app.core.Severity
import java.util.concurrent.Executors

class HybridClassifier(
    private val ruleClassifier: RuleClassifier,
    private val cloudClassifier: CloudClassifier
) : TextClassifier {

    private val executor = Executors.newSingleThreadExecutor()

    override fun classify(text: String, sensitivity: Sensitivity): ClassificationResult {
        val ruleResult = ruleClassifier.classify(text, sensitivity)

        if (!cloudClassifier.config.isConfigured) {
            return ruleResult
        }

        if (ruleResult.isHarmful && ruleResult.severity.ordinal >= Severity.LIGHT.ordinal) {
            return ruleResult
        }

        if (!ruleResult.isHarmful) {
            return ruleResult
        }

        return try {
            val future = executor.submit<ClassificationResult> {
                cloudClassifier.classify(text, sensitivity)
            }
            future.get()
        } catch (e: Exception) {
            ruleResult
        }
    }
}
