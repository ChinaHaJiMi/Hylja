package com.hylia.app.profile

import android.content.Context
import com.hylia.app.core.Sensitivity
import com.hylia.app.ml.ApiConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

data class ProfileSnapshot(
    val sensitivity: Sensitivity = Sensitivity.STANDARD,
    val rewardEnabled: Boolean = true,
    val dailyEntertainmentLimitMin: Int = 60,
    val points: Int = 0,
    val streak: Int = 0,
    val todayBlocked: Int = 0,
    val todayContinued: Int = 0,
    val todayEarned: Int = 0,
    val streakBonusClaimedToday: Boolean = false,
    val allTimeBlocked: Int = 0,
    val allTimeContinued: Int = 0,
    val allTimeMisjudged: Int = 0,
    val apiConfig: ApiConfig = ApiConfig()
)

/** 基于 SharedPreferences 的持久化档案 + 响应式状态。 */
class ProfileStore(context: Context) {

    private val prefs = context.getSharedPreferences("hylia_profile", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<ProfileSnapshot> = _state.asStateFlow()

    private fun load(): ProfileSnapshot = ProfileSnapshot(
        sensitivity = Sensitivity.fromName(prefs.getString(KEY_SENSITIVITY, null)),
        rewardEnabled = prefs.getBoolean(KEY_REWARD, true),
        dailyEntertainmentLimitMin = prefs.getInt(KEY_GOAL, 60),
        points = prefs.getInt(KEY_POINTS, 0),
        streak = prefs.getInt(KEY_STREAK, 0),
        todayBlocked = prefs.getInt(KEY_TODAY_BLOCKED, 0),
        todayContinued = prefs.getInt(KEY_TODAY_CONTINUED, 0),
        todayEarned = prefs.getInt(KEY_TODAY_EARNED, 0),
        streakBonusClaimedToday = prefs.getBoolean(KEY_BONUS_CLAIMED, false),
        allTimeBlocked = prefs.getInt(KEY_ALL_BLOCKED, 0),
        allTimeContinued = prefs.getInt(KEY_ALL_CONTINUED, 0),
        allTimeMisjudged = prefs.getInt(KEY_ALL_MISJUDGE, 0),
        apiConfig = ApiConfig(
            endpoint = prefs.getString(KEY_API_ENDPOINT, ApiConfig.DEFAULT_ENDPOINT) ?: ApiConfig.DEFAULT_ENDPOINT,
            apiKey = prefs.getString(KEY_API_KEY, "") ?: "",
            model = prefs.getString(KEY_API_MODEL, ApiConfig.DEFAULT_MODEL) ?: ApiConfig.DEFAULT_MODEL,
            enabled = prefs.getBoolean(KEY_API_ENABLED, false),
            timeoutSeconds = prefs.getLong(KEY_API_TIMEOUT, 30),
            visionEnabled = prefs.getBoolean(KEY_API_VISION_ENABLED, true),
            visionModel = prefs.getString(KEY_API_VISION_MODEL, ApiConfig.DEFAULT_VISION_MODEL) ?: ApiConfig.DEFAULT_VISION_MODEL
        )
    )

    private fun emit(next: ProfileSnapshot) {
        prefs.edit().apply {
            putString(KEY_SENSITIVITY, next.sensitivity.name)
            putBoolean(KEY_REWARD, next.rewardEnabled)
            putInt(KEY_GOAL, next.dailyEntertainmentLimitMin)
            putInt(KEY_POINTS, next.points)
            putInt(KEY_STREAK, next.streak)
            putInt(KEY_TODAY_BLOCKED, next.todayBlocked)
            putInt(KEY_TODAY_CONTINUED, next.todayContinued)
            putInt(KEY_TODAY_EARNED, next.todayEarned)
            putBoolean(KEY_BONUS_CLAIMED, next.streakBonusClaimedToday)
            putInt(KEY_ALL_BLOCKED, next.allTimeBlocked)
            putInt(KEY_ALL_CONTINUED, next.allTimeContinued)
            putInt(KEY_ALL_MISJUDGE, next.allTimeMisjudged)
            putString(KEY_TODAY_DATE, today())
            putString(KEY_API_ENDPOINT, next.apiConfig.endpoint)
            putString(KEY_API_KEY, next.apiConfig.apiKey)
            putString(KEY_API_MODEL, next.apiConfig.model)
            putBoolean(KEY_API_ENABLED, next.apiConfig.enabled)
            putLong(KEY_API_TIMEOUT, next.apiConfig.timeoutSeconds)
            putBoolean(KEY_API_VISION_ENABLED, next.apiConfig.visionEnabled)
            putString(KEY_API_VISION_MODEL, next.apiConfig.visionModel)
        }.apply()
        _state.value = next
    }

    // ---- setter 区 ----

    var sensitivity: Sensitivity
        get() = _state.value.sensitivity
        set(value) = emit(_state.value.copy(sensitivity = value))

    var rewardEnabled: Boolean
        get() = _state.value.rewardEnabled
        set(value) = emit(_state.value.copy(rewardEnabled = value))

    var dailyEntertainmentLimitMin: Int
        get() = _state.value.dailyEntertainmentLimitMin
        set(value) = emit(_state.value.copy(dailyEntertainmentLimitMin = value.coerceIn(0, 180)))

    var apiConfig: ApiConfig
        get() = _state.value.apiConfig
        set(value) = emit(_state.value.copy(apiConfig = value))

    // ---- 跨天滚动 ----

    fun rollToToday() {
        val s = _state.value
        if (prefs.getString(KEY_TODAY_DATE, null) == today()) return
        emit(s.copy(
            todayBlocked = 0,
            todayContinued = 0,
            todayEarned = 0,
            streakBonusClaimedToday = false
        ))
    }

    // ---- 记录事件（由 CoachingEngine 调用） ----

    fun recordReturn() {
        rollToToday()
        val s = _state.value
        emit(s.copy(
            todayBlocked = s.todayBlocked + 1,
            todayEarned = s.todayEarned + 10,
            allTimeBlocked = s.allTimeBlocked + 1
        ))
    }

    fun recordContinue() {
        rollToToday()
        val s = _state.value
        emit(s.copy(
            todayContinued = s.todayContinued + 1,
            allTimeContinued = s.allTimeContinued + 1
        ))
    }

    fun recordMisjudge() {
        val s = _state.value
        emit(s.copy(allTimeMisjudged = s.allTimeMisjudged + 1))
    }

    fun addEarned(delta: Int) {
        rollToToday()
        val s = _state.value
        emit(s.copy(points = s.points + delta, todayEarned = s.todayEarned + delta))
    }

    /** 正向事件时更新连击，并发放当日连击加成。 */
    fun applyStreakBonus() {
        rollToToday()
        val s = _state.value
        if (s.streakBonusClaimedToday) return
        val newStreak = when (prefs.getString(KEY_LAST_DATE, null)) {
            yesterday() -> s.streak + 1
            today() -> s.streak
            else -> 1
        }
        val bonus = 5 * newStreak.coerceAtMost(10)
        emit(s.copy(
            streak = newStreak,
            points = s.points + bonus,
            todayEarned = s.todayEarned + bonus,
            streakBonusClaimedToday = true
        ))
        prefs.edit().putString(KEY_LAST_DATE, today()).apply()
    }

    private companion object {
        const val PREFIX = "hylia_profile"
        const val KEY_SENSITIVITY = "$PREFIX.sensitivity"
        const val KEY_REWARD = "$PREFIX.reward"
        const val KEY_GOAL = "$PREFIX.goal"
        const val KEY_POINTS = "$PREFIX.points"
        const val KEY_STREAK = "$PREFIX.streak"
        const val KEY_LAST_DATE = "$PREFIX.last_date"
        const val KEY_TODAY_DATE = "$PREFIX.today_date"
        const val KEY_TODAY_BLOCKED = "$PREFIX.today_blocked"
        const val KEY_TODAY_CONTINUED = "$PREFIX.today_continued"
        const val KEY_TODAY_EARNED = "$PREFIX.today_earned"
        const val KEY_BONUS_CLAIMED = "$PREFIX.bonus_claimed"
        const val KEY_ALL_BLOCKED = "$PREFIX.all_blocked"
        const val KEY_ALL_CONTINUED = "$PREFIX.all_continued"
        const val KEY_ALL_MISJUDGE = "$PREFIX.all_misjudge"
        const val KEY_API_ENDPOINT = "$PREFIX.api_endpoint"
        const val KEY_API_KEY = "$PREFIX.api_key"
        const val KEY_API_MODEL = "$PREFIX.api_model"
        const val KEY_API_ENABLED = "$PREFIX.api_enabled"
        const val KEY_API_TIMEOUT = "$PREFIX.api_timeout"
        const val KEY_API_VISION_ENABLED = "$PREFIX.api_vision_enabled"
        const val KEY_API_VISION_MODEL = "$PREFIX.api_vision_model"

        fun today(): String = LocalDate.now().toString()
        fun yesterday(): String = LocalDate.now().minusDays(1).toString()
    }
}