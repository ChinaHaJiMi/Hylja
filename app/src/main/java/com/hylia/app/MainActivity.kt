package com.hylia.app

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import com.hylia.app.ui.HyliaTheme
import com.hylia.app.ui.MainScreen
import com.hylia.app.ui.SettingsScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HyliaTheme {
                var screen by rememberSaveable { mutableStateOf("main") }
                val app = HyliaApp.instance
                val snapshot by app.profileStore.state.collectAsState()
                when (screen) {
                    "settings" -> SettingsScreen(
                        snapshot = snapshot,
                        onChangeSensitivity = { app.profileStore.sensitivity = it },
                        onChangeReward = { app.profileStore.rewardEnabled = it },
                        onChangeGoal = { app.profileStore.dailyEntertainmentLimitMin = it },
                        onChangeApiConfig = { app.profileStore.apiConfig = it },
                        onBack = { screen = "main" }
                    )
                    else -> MainScreen(
                        snapshot = snapshot,
                        onOpenSettings = { screen = "settings" },
                        onOpenAccessibility = {
                            startActivity(
                                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            )
                        }
                    )
                }
            }
        }
    }
}