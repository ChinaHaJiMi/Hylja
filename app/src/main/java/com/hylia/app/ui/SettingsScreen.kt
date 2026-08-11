package com.hylia.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hylia.app.R
import com.hylia.app.HyliaApp
import com.hylia.app.core.Sensitivity
import com.hylia.app.profile.ProfileSnapshot

@Composable
fun SettingsScreen(
    snapshot: ProfileSnapshot,
    onChangeSensitivity: (Sensitivity) -> Unit,
    onChangeReward: (Boolean) -> Unit,
    onChangeGoal: (Int) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onBack) {
                        Text(
                            stringResource(R.string.back),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            SectionTitle(stringResource(R.string.section_sensitivity))
            SensitivityPicker(snapshot.sensitivity, onChangeSensitivity)
            Text(
                stringResource(R.string.sensitivity_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(24.dp))
            SectionTitle(stringResource(R.string.section_reward))
            SwitchRow(
                title = stringResource(R.string.reward_enabled),
                hint = stringResource(R.string.reward_enabled_hint),
                checked = snapshot.rewardEnabled,
                onChange = onChangeReward
            )

            Spacer(Modifier.height(24.dp))
            SectionTitle(stringResource(R.string.section_goal))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.goal_entertainment_limit),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            stringResource(R.string.goal_minutes, snapshot.dailyEntertainmentLimitMin),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = snapshot.dailyEntertainmentLimitMin.toFloat(),
                        onValueChange = { onChangeGoal(it.toInt()) },
                        valueRange = 0f..180f,
                        steps = 35
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            SectionTitle(stringResource(R.string.section_stats))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp)) {
                    StatRow(stringResource(R.string.stat_points), snapshot.points.toString())
                    StatRow(stringResource(R.string.stat_streak), snapshot.streak.toString())
                    StatRow(stringResource(R.string.stat_blocked), snapshot.allTimeBlocked.toString())
                    StatRow(stringResource(R.string.stat_misjudge), snapshot.allTimeMisjudged.toString())
                }
            }

            Spacer(Modifier.height(24.dp))
            SectionTitle(stringResource(R.string.section_about))
            Text(
                stringResource(R.string.about_text),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun SensitivityPicker(selected: Sensitivity, onChange: (Sensitivity) -> Unit) {
    Row(
        Modifier.padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Sensitivity.entries.forEach { s ->
            FilterChip(
                selected = s == selected,
                onClick = { onChange(s) },
                label = { Text(stringResource(s.nameRes())) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    hint: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(2.dp))
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

private fun Sensitivity.nameRes(): Int = when (this) {
    Sensitivity.RELAXED -> R.string.sensitivity_relaxed
    Sensitivity.STANDARD -> R.string.sensitivity_standard
    Sensitivity.STRICT -> R.string.sensitivity_strict
}

@Composable
fun rememberSettingsState(): ProfileSnapshot {
    val state by HyliaApp.instance.profileStore.state
    return state
}