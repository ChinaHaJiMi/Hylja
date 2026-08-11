package com.hylia.app.coaching

import com.hylia.app.profile.ProfileStore

/**
 * 督学引擎：把"拦截+选择"转化为积分与连击，形成自律正反馈闭环。
 *
 * 积分规则：
 *  - 拦截后选择「返回」 +10（自律主奖励）
 *  - 完成专注番茄钟 +20（与专注计时联动）
 *  - 达成每日目标 +30
 *  - 连续达标连击加成 +5/level/天，上限 +50
 *  - 误判反馈被采纳 +5
 *  - 超额「继续访问」 -10（温和约束）
 */
class CoachingEngine(private val store: ProfileStore) {

    fun onReturnBlocked() {
        store.rollToToday()
        store.recordReturn()
        applyPositive(10)
    }

    fun onContinueAllowed() {
        store.rollToToday()
        store.recordContinue()
        if (store.state.value.rewardEnabled) store.addEarned(-10)
    }

    fun onMisjudgeReported() {
        store.recordMisjudge()
        applyPositive(5)
    }

    fun onTomatoCompleted() = applyPositive(20)

    fun onDailyGoalReached() = applyPositive(30)

    private fun applyPositive(base: Int) {
        if (!store.state.value.rewardEnabled) return
        store.addEarned(base)
        store.applyStreakBonus()
    }
}