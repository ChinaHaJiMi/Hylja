package com.hylia.app.intervention

import com.hylia.app.core.ClassificationResult

/** 拦截卡片的用户决策回调，由无障碍服务实现并接督导学引擎。 */
interface InterventionListener {
    fun onReturnBlocked(result: ClassificationResult)
    fun onContinueAllowed(result: ClassificationResult, packageName: String, textHash: String)
    fun onMisjudged(result: ClassificationResult)
}