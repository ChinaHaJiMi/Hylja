package com.hylia.app.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import android.os.Bundle

/**
 * 独立设置页：作为无障碍系统设置入口的落地页。
 */
class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HyliaTheme {
                val app = com.hylia.app.HyliaApp.instance
                val snapshot by app.profileStore.state.collectAsState()
                SettingsScreen(
                    snapshot = snapshot,
                    onChangeSensitivity = { app.profileStore.sensitivity = it },
                    onChangeReward = { app.profileStore.rewardEnabled = it },
                    onChangeGoal = { app.profileStore.dailyEntertainmentLimitMin = it },
                    onBack = { finish() }
                )
            }
        }
    }
}