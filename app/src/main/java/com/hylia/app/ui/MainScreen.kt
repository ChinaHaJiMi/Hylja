package com.hylia.app.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hylia.app.R
import com.hylia.app.profile.ProfileSnapshot

@Composable
fun MainScreen(
    snapshot: ProfileSnapshot,
    onOpenSettings: () -> Unit,
    onOpenAccessibility: () -> Unit
) {
    val context = LocalContext.current
    val enabled = rememberIsAccessibilityEnabled(context)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(Modifier.height(28.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = shieldIcon(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.app_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            StatusCard(enabled, onOpenAccessibility, onOpenSettings)

            Spacer(Modifier.height(16.dp))

            Text(
                stringResource(R.string.section_stats),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            StatsGrid(snapshot)

            Spacer(Modifier.height(16.dp))

            Text(
                stringResource(R.string.about_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatusCard(
    enabled: Boolean,
    onOpenAccessibility: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (enabled) stringResource(R.string.service_status_enabled)
                    else stringResource(R.string.service_status_disabled),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (enabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = onOpenSettings) {
                    Text(stringResource(R.string.settings_title))
                }
            }
            if (!enabled) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.service_status_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Button(onClick = onOpenAccessibility, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_open_accessibility))
                }
            }
        }
    }
}

@Composable
private fun StatsGrid(snapshot: ProfileSnapshot) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatCell(
            modifier = Modifier.weight(1f),
            label = stringResource(R.string.today_blocked),
            value = snapshot.todayBlocked.toString()
        )
        StatCell(
            modifier = Modifier.weight(1f),
            label = stringResource(R.string.today_continued),
            value = snapshot.todayContinued.toString()
        )
        StatCell(
            modifier = Modifier.weight(1f),
            label = stringResource(R.string.points),
            value = snapshot.points.toString()
        )
        StatCell(
            modifier = Modifier.weight(1f),
            label = stringResource(R.string.streak),
            value = "${snapshot.streak}🔥"
        )
    }
}

@Composable
private fun StatCell(modifier: Modifier = Modifier, label: String, value: String) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(
                label,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun rememberIsAccessibilityEnabled(context: Context): Boolean {
    val services = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: ""
    return services.split(':').any { it.contains("com.hylia.app") }
}

@Composable
private fun shieldIcon(): ImageVector {
    return ImageVector.Builder(
        name = "shield",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary)) {
            moveTo(12f, 2f)
            lineTo(20f, 5f)
            lineTo(20f, 11f)
            curveTo(20f, 16f, 16.5f, 20.5f, 12f, 22f)
            curveTo(7.5f, 20.5f, 4f, 16f, 4f, 11f)
            lineTo(4f, 5f)
            close()
        }
        path(fill = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color.White)) {
            moveTo(8f, 11.5f)
            lineTo(11f, 14.5f)
            lineTo(16f, 8.5f)
            lineTo(17.4f, 9.9f)
            lineTo(11f, 16.6f)
            lineTo(6.6f, 12.9f)
            close()
        }
    }.build()
}