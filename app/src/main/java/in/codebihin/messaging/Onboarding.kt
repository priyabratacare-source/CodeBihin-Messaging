package `in`.codebihin.messaging

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

internal data class OnboardingPage(
    val emoji: String,
    val title: String,
    val body: String
)

@Composable
internal fun OnboardingScreen(
    darkTheme: Boolean,
    onSkip: () -> Unit,
    onFinished: () -> Unit
) {
    val pages = listOf(
        OnboardingPage(
            emoji = "💬",
            title = "Private messaging",
            body = "Chat one-to-one or in groups. Your conversations stay with the people you choose."
        ),
        OnboardingPage(
            emoji = "📢",
            title = "Channels & updates",
            body = "Follow public channels and official announcements so you never miss important news."
        ),
        OnboardingPage(
            emoji = "🔗",
            title = "Share links safely",
            body = "Send Google Drive or other HTTPS file links in chat — no bulk uploads to our servers."
        ),
        OnboardingPage(
            emoji = "🔔",
            title = "Stay in the loop",
            body = "Turn on notifications for new messages. You can change this anytime in system settings."
        )
    )
    var page by remember { mutableStateOf(0) }
    val last = page == pages.lastIndex
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
                .padding(horizontal = 24.dp)
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
                Text("‹/›", color = Color.White, style = MaterialTheme.typography.headlineSmall)
            }
            Spacer(Modifier.height(10.dp))
            Text("CodeBihin", style = MaterialTheme.typography.titleLarge, color = navy)
            Text("Quick start", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)

            Spacer(Modifier.height(28.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(pages[page].emoji, style = MaterialTheme.typography.displayMedium)
                    Spacer(Modifier.height(18.dp))
                    Text(
                        pages[page].title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = navy
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        pages[page].body,
                        style = MaterialTheme.typography.bodyLarge,
                        color = muted
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                pages.indices.forEach { i ->
                    Box(
                        modifier = Modifier
                            .height(8.dp)
                            .width(if (i == page) 22.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (i == page) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            )
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = {
                    if (last) onFinished() else page += 1
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    if (last) "Get started" else "Next",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            if (!last) {
                TextButton(onClick = onSkip) {
                    Text("Skip guide", color = muted)
                }
            } else {
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}
