package `in`.codebihin.messaging

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

@Composable
internal fun PermissionsSetupScreen(
    darkTheme: Boolean,
    onRequestPermissions: (Array<String>) -> Unit,
    onContinue: () -> Unit,
    onSkip: () -> Unit
) {
    val context = LocalContext.current

    fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    val notificationsNeeded = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    var notificationsGranted by remember {
        mutableStateOf(
            if (notificationsNeeded) granted(Manifest.permission.POST_NOTIFICATIONS) else true
        )
    }
    var locationGranted by remember {
        mutableStateOf(
            granted(Manifest.permission.ACCESS_COARSE_LOCATION) ||
                granted(Manifest.permission.ACCESS_FINE_LOCATION)
        )
    }

    // Re-check when returning from the system dialog
    LaunchedEffect(Unit) {
        // tick once on enter; parent can recomposes after launcher callback by keying outside
    }

    fun refresh() {
        notificationsGranted =
            if (notificationsNeeded) granted(Manifest.permission.POST_NOTIFICATIONS) else true
        locationGranted =
            granted(Manifest.permission.ACCESS_COARSE_LOCATION) ||
                granted(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    // Allow parent to force refresh by toggling darkTheme or by recompose from activity
    SideEffect { refresh() }

    val navy = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.primary.copy(alpha = if (darkTheme) 0.14f else 0.06f),
                        MaterialTheme.colorScheme.secondary.copy(alpha = if (darkTheme) 0.10f else 0.05f)
                    )
                )
            )
    ) {
        TextButton(
            onClick = onSkip,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 8.dp, end = 8.dp)
        ) {
            Text("Skip", color = muted, style = MaterialTheme.typography.labelLarge)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(top = 72.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF4F46E5), Color(0xFF06B6D4)))),
                contentAlignment = Alignment.Center
            ) {
                Text("🔐", style = MaterialTheme.typography.headlineMedium)
            }
            Spacer(Modifier.height(12.dp))
            Text("Device permissions", style = MaterialTheme.typography.headlineSmall, color = navy)
            Spacer(Modifier.height(6.dp))
            Text(
                "CodeBihin needs a few permissions to work well on this device. You can change them later in system settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = muted
            )

            Spacer(Modifier.height(22.dp))

            PermissionCard(
                emoji = "🔔",
                title = "Notifications",
                body = "Get alerts for new messages, groups, and announcements. Strongly recommended.",
                granted = notificationsGranted,
                requiredLabel = if (notificationsNeeded) "Required on Android 13+" else "Enabled by system",
                buttonLabel = if (notificationsGranted) "Allowed" else "Allow notifications",
                enabled = !notificationsGranted && notificationsNeeded,
                onClick = {
                    if (notificationsNeeded) {
                        onRequestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                    }
                }
            )

            Spacer(Modifier.height(12.dp))

            PermissionCard(
                emoji = "📍",
                title = "Location",
                body = "Optional. Used only when you share live location in a chat. We do not track you in the background.",
                granted = locationGranted,
                requiredLabel = "Optional",
                buttonLabel = if (locationGranted) "Allowed" else "Allow location",
                enabled = !locationGranted,
                onClick = {
                    onRequestPermissions(
                        arrayOf(
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        )
                    )
                }
            )

            Spacer(Modifier.height(28.dp))

            Button(
                onClick = onContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("Continue", style = MaterialTheme.typography.titleMedium)
            }

            TextButton(onClick = onSkip) {
                Text("Not now", color = muted)
            }
        }
    }
}

@Composable
private fun PermissionCard(
    emoji: String,
    title: String,
    body: String,
    granted: Boolean,
    requiredLabel: String,
    buttonLabel: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(Modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(emoji, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        requiredLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (granted) Color(0xFF10B981) else MaterialTheme.colorScheme.primary
                    )
                }
                if (granted) {
                    Text("✓", color = Color(0xFF10B981), style = MaterialTheme.typography.titleLarge)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(14.dp))
            OutlinedButton(
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large
            ) {
                Text(buttonLabel)
            }
        }
    }
}
