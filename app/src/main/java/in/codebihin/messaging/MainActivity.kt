package `in`.codebihin.messaging

import android.Manifest
import android.content.Intent
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import java.util.UUID

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Create the notification channel during normal app startup. Creating it
        // only inside FirebaseMessagingService means Android may show no channel
        // under App info until the first FCM message starts that service.
        createMessageNotificationChannel()

        setContent {
            val context = LocalContext.current
            val systemDarkTheme = isSystemInDarkTheme()
            val themePrefs = remember { context.getSharedPreferences("codebihin_ui", Context.MODE_PRIVATE) }
            var darkTheme by remember { mutableStateOf(themePrefs.getBoolean("dark_theme", systemDarkTheme)) }
            val updateTheme: (Boolean) -> Unit = { enabled ->
                darkTheme = enabled
                themePrefs.edit().putBoolean("dark_theme", enabled).apply()
            }
            val colors = if (darkTheme) darkColorScheme(
                primary = Color(0xFF818CF8),
                onPrimary = Color(0xFF171638),
                primaryContainer = Color(0xFF30306B),
                onPrimaryContainer = Color(0xFFE0E7FF),
                secondary = Color(0xFF67E8F9),
                onSecondary = Color(0xFF083344),
                background = Color(0xFF080B14),
                surface = Color(0xFF111827),
                onSurface = Color(0xFFF1F5F9),
                surfaceVariant = Color(0xFF1E293B),
                onSurfaceVariant = Color(0xFFB6C2D2),
                outline = Color(0xFF344256)
            ) else lightColorScheme(
                primary = Color(0xFF4F46E5),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFE8EAFE),
                onPrimaryContainer = Color(0xFF25256B),
                secondary = Color(0xFF06B6D4),
                onSecondary = Color(0xFF003B46),
                background = Color(0xFFF8FAFC),
                surface = Color.White,
                onSurface = Color(0xFF0F172A),
                surfaceVariant = Color(0xFFF1F5F9),
                onSurfaceVariant = Color(0xFF64748B),
                outline = Color(0xFFCBD5E1)
            )
            MaterialTheme(
                colorScheme = colors,
                shapes = Shapes(
                    small = RoundedCornerShape(12.dp),
                    medium = RoundedCornerShape(16.dp),
                    large = RoundedCornerShape(22.dp),
                    extraLarge = RoundedCornerShape(28.dp)
                )
            ) { CodeBihinRoot(::openExternal, ::requestNotificationPermission, darkTheme, updateTheme) }
        }

        // Ask after the activity UI is installed so the Android permission dialog
        // is associated with the visible app launch.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun createMessageNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "codebihin_messages",
                "Chat messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "New messages, group chats and CodeBihin updates"
                enableVibration(true)
                setShowBadge(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun requestNotificationPermission() {
        val enabled = NotificationManagerCompat.from(this).areNotificationsEnabled()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else if (!enabled) {
            startActivity(
                Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName)
            )
        }
    }

    private fun openExternal(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }
}

private val db get() = FirebaseFirestore.getInstance()
private val auth get() = FirebaseAuth.getInstance()

@Composable
private fun CodeBihinRoot(openExternal: (String) -> Unit, requestNotifications: () -> Unit, darkTheme: Boolean, onThemeChange: (Boolean) -> Unit) {
    var currentUser by remember { mutableStateOf(auth.currentUser) }
    var remoteActionHandled by remember { mutableStateOf(false) }

    DisposableEffect(currentUser?.uid) {
        val uid = currentUser?.uid
        remoteActionHandled = false
        if (uid == null) {
            onDispose { }
        } else {
            val userRef = db.collection("users").document(uid)
            val registration = userRef.addSnapshotListener { snapshot, error ->
                if (error != null || remoteActionHandled) return@addSnapshotListener
                val data = snapshot?.data ?: return@addSnapshotListener

                // A block is persistent: the user may open the login screen, but cannot
                // enter the app until an administrator unblocks the account.
                if (data["disabled"] == true) {
                    remoteActionHandled = true
                    auth.signOut()
                } else if (data["forceLogout"] == true) {
                    // Clear the one-shot command before signing out. This allows the
                    // same account to sign in again immediately after remote logout.
                    remoteActionHandled = true
                    userRef.update("forceLogout", false)
                        .addOnCompleteListener {
                            auth.signOut()
                        }
                }
            }
            onDispose { registration.remove() }
        }
    }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { currentUser = it.currentUser }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    // Refresh/register this installation's FCM token whenever a signed-in
    // session becomes available, including after an app update.
    LaunchedEffect(currentUser?.uid) {
        if (currentUser != null) registerCurrentDeviceToken()
    }

    val context = LocalContext.current
    val uiPrefs = remember { context.getSharedPreferences("codebihin_ui", Context.MODE_PRIVATE) }
    var onboardingDone by remember { mutableStateOf(uiPrefs.getBoolean("onboarding_done", false)) }

    fun completeOnboarding() {
        onboardingDone = true
        uiPrefs.edit().putBoolean("onboarding_done", true).apply()
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when {
            currentUser != null -> MessagingScreen(openExternal, requestNotifications, darkTheme, onThemeChange)
            !onboardingDone -> OnboardingScreen(
                darkTheme = darkTheme,
                onSkip = { completeOnboarding() },
                onFinished = { completeOnboarding() }
            )
            else -> AuthScreen(darkTheme, onThemeChange)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthScreen(darkTheme: Boolean, onThemeChange: (Boolean) -> Unit) {
    var register by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmVisible by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var resetDialog by remember { mutableStateOf(false) }
    var resetEmail by remember { mutableStateOf("") }

    val navy = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val indigo = MaterialTheme.colorScheme.primary
    val pale = MaterialTheme.colorScheme.background
    val strength = when {
        password.isEmpty() -> 0
        password.length < 6 -> 1
        password.length < 8 -> 2
        password.any { it.isDigit() } && password.any { it.isUpperCase() } -> 4
        else -> 3
    }
    val strengthLabel = when (strength) {
        0 -> "Min. 6 characters"
        1 -> "Too short"
        2 -> "Fair"
        3 -> "Good"
        else -> "Strong"
    }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
        MaterialTheme.colorScheme.background,
        MaterialTheme.colorScheme.primary.copy(alpha = if (darkTheme) 0.12f else 0.055f),
        MaterialTheme.colorScheme.secondary.copy(alpha = if (darkTheme) 0.08f else 0.045f)
    )))) {
        Box(Modifier.align(Alignment.TopEnd).padding(top = 24.dp, end = 20.dp).size(42.dp)
            .clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            IconButton(onClick = { onThemeChange(!darkTheme) }) { Text(if (darkTheme) "☀" else "☾", color = MaterialTheme.colorScheme.primary) }
        }
        Column(
            modifier = Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(Modifier.size(68.dp).clip(RoundedCornerShape(22.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF4F46E5), Color(0xFF06B6D4)))),
                contentAlignment = Alignment.Center) {
                Text("‹/›", color = Color.White, style = MaterialTheme.typography.headlineMedium)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "CodeBihin",
                style = MaterialTheme.typography.headlineLarge,
                color = navy
            )
            Text(
                "MESSAGING",
                style = MaterialTheme.typography.labelMedium,
                color = indigo
            )
            Spacer(Modifier.height(5.dp))
            Text(
                "Private messaging, made simple",
                style = MaterialTheme.typography.bodyMedium,
                color = muted
            )
            Spacer(Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 7.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(MaterialTheme.shapes.large)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(false to "Sign In", true to "Create Account").forEach { (isRegister, label) ->
                            Surface(
                                modifier = Modifier.weight(1f).clickable {
                                    register = isRegister
                                    note = ""
                                },
                                shape = MaterialTheme.shapes.medium,
                                color = if (register == isRegister) MaterialTheme.colorScheme.surface else Color.Transparent,
                                shadowElevation = if (register == isRegister) 2.dp else 0.dp
                            ) {
                                Box(Modifier.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        label,
                                        color = if (register == isRegister) navy else muted,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(
                        if (register) "Create your account" else "Welcome back",
                        style = MaterialTheme.typography.headlineSmall,
                        color = navy
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (register) "Join CodeBihin and start connecting." else "Sign in to continue to your messages.",
                        style = MaterialTheme.typography.bodySmall,
                        color = muted
                    )
                    Spacer(Modifier.height(18.dp))

                    if (register) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it; note = "" },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Full name") },
                            placeholder = { Text("Your full name") },
                            leadingIcon = { Text("●", color = muted) },
                            shape = MaterialTheme.shapes.large,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = indigo,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )
                        Spacer(Modifier.height(11.dp))
                    }

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it.trim(); note = "" },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Email address") },
                        placeholder = { Text("name@example.com") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        leadingIcon = { Text("✉", color = muted) },
                        shape = MaterialTheme.shapes.large,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = indigo,
                            unfocusedBorderColor = Color(0xFFE2E8F0)
                        )
                    )
                    Spacer(Modifier.height(11.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; note = "" },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Password") },
                        placeholder = { Text("At least 6 characters") },
                        leadingIcon = { Text("▣", color = muted) },
                        visualTransformation = if (passwordVisible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            TextButton(
                                onClick = { passwordVisible = !passwordVisible },
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) { Text(if (passwordVisible) "Hide" else "Show") }
                        },
                        shape = MaterialTheme.shapes.large,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = indigo,
                            unfocusedBorderColor = Color(0xFFE2E8F0)
                        )
                    )

                    if (register) {
                        Spacer(Modifier.height(7.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            repeat(4) { index ->
                                Box(
                                    Modifier.weight(1f).height(4.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (index < strength) {
                                                if (strength <= 1) Color(0xFFEF4444)
                                                else if (strength <= 2) Color(0xFFF59E0B)
                                                else Color(0xFF10B981)
                                            } else MaterialTheme.colorScheme.outline
                                        )
                                )
                            }
                        }
                        Text(
                            strengthLabel,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            color = if (strength <= 1 && password.isNotEmpty()) Color(0xFFDC2626) else muted,
                            style = MaterialTheme.typography.labelSmall
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it; note = "" },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Confirm password") },
                            placeholder = { Text("Re-enter password") },
                            visualTransformation = if (confirmVisible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                TextButton(
                                    onClick = { confirmVisible = !confirmVisible },
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) { Text(if (confirmVisible) "Hide" else "Show") }
                            },
                            shape = MaterialTheme.shapes.large,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = indigo,
                                unfocusedBorderColor = Color(0xFFE2E8F0)
                            )
                        )
                        if (confirmPassword.isNotEmpty()) {
                            Text(
                                if (confirmPassword == password) "✓ Passwords match" else "Passwords do not match",
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                color = if (confirmPassword == password) Color(0xFF059669) else Color(0xFFDC2626),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { resetEmail = email; resetDialog = true }) {
                                Text("Forgot password?", color = indigo)
                            }
                        }
                    }

                    if (note.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(note, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(13.dp))
                    Button(
                        onClick = {
                            val cleanEmail = email.trim()
                            when {
                                register && name.trim().length < 2 -> note = "Please enter your full name."
                                !android.util.Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches() -> note = "Please enter a valid email address."
                                password.length < 6 -> note = "Password must be at least 6 characters."
                                register && password != confirmPassword -> note = "Passwords do not match."
                                else -> {
                                    busy = true
                                    note = ""
                                    val task = if (register) auth.createUserWithEmailAndPassword(cleanEmail, password)
                                    else auth.signInWithEmailAndPassword(cleanEmail, password)
                                    task.addOnCompleteListener { result ->
                                        busy = false
                                        if (result.isSuccessful) {
                                            val user = auth.currentUser
                                            if (user != null && register) {
                                                val displayName = name.trim()
                                                user.updateProfile(
                                                    com.google.firebase.auth.UserProfileChangeRequest.Builder()
                                                        .setDisplayName(displayName).build()
                                                )
                                                val profile = mapOf(
                                                    "uid" to user.uid,
                                                    "displayName" to displayName,
                                                    "email" to (user.email ?: cleanEmail),
                                                    "createdAt" to FieldValue.serverTimestamp()
                                                )
                                                db.collection("users").document(user.uid).set(profile, SetOptions.merge())
                                                db.collection("publicProfiles").document(user.uid).set(
                                                    mapOf(
                                                        "uid" to user.uid,
                                                        "displayName" to displayName,
                                                        "createdAt" to FieldValue.serverTimestamp()
                                                    ),
                                                    SetOptions.merge()
                                                )
                                            }
                                            registerCurrentDeviceToken()
                                            note = ""
                                        } else {
                                            note = result.exception?.localizedMessage
                                                ?: "Authentication failed. Please try again."
                                        }
                                    }
                                }
                            }
                        },
                        enabled = !busy,
                        shape = MaterialTheme.shapes.large,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.White),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                        modifier = Modifier.fillMaxWidth().height(54.dp)
                            .background(Brush.horizontalGradient(listOf(Color(0xFF4F46E5), Color(0xFF06B6D4))), MaterialTheme.shapes.large)
                    ) {
                        if (busy) {
                            CircularProgressIndicator(
                                Modifier.size(19.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                if (register) "Create Account   ✓" else "Sign In   →",
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                    }
                    Spacer(Modifier.height(13.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (register) "Already have an account?" else "Don't have an account?",
                            color = muted,
                            style = MaterialTheme.typography.bodySmall
                        )
                        TextButton(onClick = {
                            register = !register
                            note = ""
                            confirmPassword = ""
                        }) {
                            Text(if (register) "Sign In" else "Create Account", color = indigo)
                        }
                    }
                }
            }
            Spacer(Modifier.height(17.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("◆", color = Color(0xFF059669), style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.width(6.dp))
                Text(
                    "Secure sign-in powered by Firebase",
                    color = muted,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Spacer(Modifier.height(7.dp))
            Text(
                "CodeBihin Messaging",
                color = Color(0xFF94A3B8),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }

    if (resetDialog) {
        AlertDialog(
            onDismissRequest = { resetDialog = false },
            title = { Text("Reset password") },
            text = {
                Column {
                    Text("Enter your account email. We’ll send you a secure password reset link.")
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = resetEmail,
                        onValueChange = { resetEmail = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Email address") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val address = resetEmail.trim()
                    if (!android.util.Patterns.EMAIL_ADDRESS.matcher(address).matches()) {
                        note = "Enter a valid email address."
                    } else {
                        auth.sendPasswordResetEmail(address)
                            .addOnSuccessListener {
                                note = "Password reset email sent. Check your inbox."
                                resetDialog = false
                            }
                            .addOnFailureListener {
                                note = it.localizedMessage ?: "Could not send reset email."
                            }
                    }
                }) { Text("Send link") }
            },
            dismissButton = { TextButton(onClick = { resetDialog = false }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessagingScreen(openExternal: (String) -> Unit, requestNotifications: () -> Unit, darkTheme: Boolean, onThemeChange: (Boolean) -> Unit) {
    val uid = auth.currentUser?.uid.orEmpty()
    var tab by remember { mutableStateOf("Chats") }
    var selectedChat by remember { mutableStateOf<ChatItem?>(null) }
    var chats by remember { mutableStateOf<List<ChatItem>>(emptyList()) }
    var people by remember { mutableStateOf<List<Person>>(emptyList()) }
    var error by remember { mutableStateOf("") }
    var showGroupDialog by remember { mutableStateOf(false) }
    var showChannelDialog by remember { mutableStateOf(false) }
    var channels by remember { mutableStateOf<List<ChannelItem>>(emptyList()) }
    var selectedChannel by remember { mutableStateOf<ChannelItem?>(null) }
    var announcements by remember { mutableStateOf<List<AnnouncementItem>>(emptyList()) }
    var showEditProfile by remember { mutableStateOf(false) }
    var profileName by remember { mutableStateOf(auth.currentUser?.displayName.orEmpty()) }
    var profilePhone by remember { mutableStateOf("") }
    var profileBio by remember { mutableStateOf("") }
    var profileNote by remember { mutableStateOf("") }
    var profileBusy by remember { mutableStateOf(false) }
    var chatSearch by remember { mutableStateOf("") }
    var chatFilter by remember { mutableStateOf("All") }

    LaunchedEffect(uid) {
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                profileName = doc.getString("displayName") ?: auth.currentUser?.displayName.orEmpty()
                profilePhone = doc.getString("phone") ?: ""
                profileBio = doc.getString("bio") ?: ""
            }
    }

    DisposableEffect(uid) {
        var chatListener: ListenerRegistration? = null
        chatListener = db.collection("chats").whereArrayContains("members", uid)
            .addSnapshotListener { snapshot, e ->
                if (e != null) error = e.localizedMessage ?: "Could not load chats."
                else chats = snapshot?.documents?.mapNotNull { doc ->
                    ChatItem(
                        id = doc.id,
                        title = doc.getString("title") ?: "Conversation",
                        lastMessage = doc.getString("lastMessage") ?: "",
                        isGroup = doc.getBoolean("isGroup") ?: false,
                        members = (doc.get("members") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                    )
                }?.sortedByDescending { it.lastMessage } ?: emptyList()
            }
        val channelListener = db.collection("channels").whereEqualTo("visibility", "public").limit(100)
            .addSnapshotListener { snapshot, e ->
                if (e != null) error = e.localizedMessage ?: "Could not load channels."
                else channels = snapshot?.documents?.map { d ->
                    ChannelItem(d.id, d.getString("name") ?: "Channel", d.getString("description") ?: "", d.getString("ownerUid") ?: "", (d.get("admins") as? List<*>)?.filterIsInstance<String>() ?: emptyList())
                } ?: emptyList()
            }
        val announcementListener = db.collection("announcements").orderBy("createdAt", Query.Direction.DESCENDING).limit(50)
            .addSnapshotListener { snapshot, e ->
                if (e != null) error = e.localizedMessage ?: "Could not load announcements."
                else announcements = snapshot?.documents?.map { d -> AnnouncementItem(d.id, d.getString("title") ?: "CodeBihin update", d.getString("body") ?: "") } ?: emptyList()
            }
        onDispose { chatListener?.remove(); channelListener.remove(); announcementListener.remove() }
    }

    LaunchedEffect(tab) {
        if (tab == "People" || tab == "New group") {
            db.collection("publicProfiles").limit(100).get()
                .addOnSuccessListener { snap ->
                    people = snap.documents.mapNotNull { d ->
                        val id = d.id
                        if (id == uid) null else Person(id, d.getString("displayName") ?: "User", d.getString("email") ?: "")
                    }
                }
                .addOnFailureListener { error = it.localizedMessage ?: "Could not load users." }
        }
    }

    if (selectedChannel != null) {
        ChannelScreen(channel = selectedChannel!!, onBack = { selectedChannel = null })
        return
    }

    if (selectedChat != null) {
        ChatScreen(
            chat = selectedChat!!,
            onBack = { selectedChat = null },
            openExternal = openExternal
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(38.dp).clip(RoundedCornerShape(13.dp))
                            .background(Brush.linearGradient(listOf(Color(0xFF4F46E5), Color(0xFF06B6D4)))),
                            contentAlignment = Alignment.Center) { Text("‹/›", color = Color.White, style = MaterialTheme.typography.titleMedium) }
                        Column {
                            Text("Codebihin", style = MaterialTheme.typography.titleLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold)
                            Text("MESSAGING", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                actions = {
                    TextButton(onClick = { auth.signOut() }) { Text("Sign out") }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (tab == "Chats") FloatingActionButton(onClick = { tab = "People" }) { Text("+") }
            if (tab == "Groups") FloatingActionButton(onClick = { showGroupDialog = true }) { Text("+") }
            if (tab == "Channels") FloatingActionButton(onClick = { showChannelDialog = true }) { Text("+") }
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface) {
                listOf("Chats", "People", "Groups", "Channels", "Updates", "Profile").forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Icon(when (item) { "Chats" -> Icons.Filled.Chat; "People" -> Icons.Filled.People; "Groups" -> Icons.Filled.Groups; "Channels" -> Icons.Filled.Campaign; "Updates" -> Icons.Filled.Notifications; else -> Icons.Filled.Person }, contentDescription = item) },
                        label = { Text(item) }
                    )
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            when (tab) {
                "Chats" -> {
                    OutlinedTextField(
                        value = chatSearch,
                        onValueChange = { chatSearch = it },
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 8.dp),
                        placeholder = { Text("Search conversations") },
                        leadingIcon = { Icon(Icons.Filled.Chat, contentDescription = null) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.extraLarge
                    )
                    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("All", "Groups", "Private").forEach { filter ->
                            val selected = chatFilter == filter
                            Surface(Modifier.clickable { chatFilter = filter }, shape = CircleShape,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant) {
                                Text(filter, Modifier.padding(horizontal = 15.dp, vertical = 8.dp),
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    Text("Recent conversations", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, bottom = 5.dp))
                    val visibleChats = chats.filter {
                        (it.title.contains(chatSearch, ignoreCase = true) || it.lastMessage.contains(chatSearch, ignoreCase = true)) &&
                        (chatFilter == "All" || (chatFilter == "Groups" && it.isGroup) || (chatFilter == "Private" && !it.isGroup))
                    }
                    if (visibleChats.isEmpty()) EmptyState(if (chatSearch.isBlank()) "No conversations yet" else "No matching chats", "Open People to find someone and start a conversation.")
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        items(visibleChats, key = { it.id }) { chat ->
                            Surface(
                                modifier = Modifier.fillMaxWidth().clickable { selectedChat = chat },
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                Row(Modifier.padding(vertical = 13.dp, horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(54.dp)) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(if (chat.isGroup) "G" else chat.title.take(1).uppercase(), color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.titleLarge)
                                        }
                                    }
                                    Spacer(Modifier.width(13.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(chat.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                                        Spacer(Modifier.height(3.dp))
                                        Text(chat.lastMessage.ifBlank { if (chat.isGroup) "${chat.members.size} members · Group" else "Tap to start a conversation" }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                    }
                                    Icon(Icons.Filled.ArrowBack, contentDescription = "Open chat", tint = MaterialTheme.colorScheme.outline)
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), modifier = Modifier.padding(start = 72.dp))
                        }
                    }
                }
                "People" -> {
                    Text("Find people", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 12.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(people, key = { it.uid }) { person ->
                            Card(Modifier.fillMaxWidth().clickable {
                                val chatId = listOf(uid, person.uid).sorted().joinToString("_")
                                val chat = ChatItem(chatId, person.name, "", false, listOf(uid, person.uid))
                                db.collection("chats").document(chatId).set(
                                    mapOf("members" to listOf(uid, person.uid), "title" to person.name, "isGroup" to false, "updatedAt" to FieldValue.serverTimestamp()),
                                    SetOptions.merge()
                                )
                                selectedChat = chat
                            }) {
                                Column(Modifier.padding(14.dp)) {
                                    Text(person.name, style = MaterialTheme.typography.titleMedium)
                                    Text(person.email, style = MaterialTheme.typography.bodySmall)
                                    Text("Tap to message", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
                "Updates" -> {
                    Text("Announcements", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 12.dp))
                    if (announcements.isEmpty()) EmptyState("No announcements", "Official CodeBihin updates will appear here.")
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(announcements, key = { it.id }) { item ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp)) {
                                    Text(item.title, style = MaterialTheme.typography.titleMedium)
                                    Spacer(Modifier.height(6.dp))
                                    Text(item.body)
                                    Text("Official update", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
                "Channels" -> {
                    Text("Public channels", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 12.dp))
                    if (channels.isEmpty()) EmptyState("No public channels yet", "Tap + to create the first channel.")
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(channels, key = { it.id }) { channel ->
                            Card(Modifier.fillMaxWidth().clickable { selectedChannel = channel }) {
                                Column(Modifier.padding(16.dp)) {
                                    Text(channel.name, style = MaterialTheme.typography.titleMedium)
                                    Text(channel.description, style = MaterialTheme.typography.bodySmall)
                                    Text(if (channel.ownerUid == uid) "You manage this channel" else "Public channel", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
                "Groups" -> {
                    Text("Your groups", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 12.dp))
                    val groups = chats.filter { it.isGroup }
                    if (groups.isEmpty()) EmptyState("No groups yet", "Tap + to create a group.")
                    LazyColumn { items(groups, key = { it.id }) { group ->
                        ListItem(
                            headlineContent = { Text(group.title) },
                            supportingContent = { Text(group.lastMessage.ifBlank { "${group.members.size} members" }) },
                            modifier = Modifier.clickable { selectedChat = group }
                        )
                    } }
                }
                else -> {
                    Text("My Profile", style = MaterialTheme.typography.headlineSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.padding(vertical = 12.dp))
                    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = Color.Transparent)) {
                        Row(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF4F46E5), Color(0xFF4338CA), Color(0xFF06B6D4)))).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(62.dp).clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                                Text(profileName.trim().take(1).uppercase().ifBlank { "C" }, color = Color.White, style = MaterialTheme.typography.headlineMedium)
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(profileName.ifBlank { auth.currentUser?.displayName ?: "CodeBihin user" }, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                Text(auth.currentUser?.email.orEmpty(), color = Color.White.copy(alpha = 0.82f), style = MaterialTheme.typography.bodySmall)
                                if (profileBio.isNotBlank()) Text(profileBio, color = Color.White, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                            }
                        }
                    }
                    if (profilePhone.isNotBlank()) Text("Phone: $profilePhone", modifier = Modifier.padding(top = 10.dp))
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = { showEditProfile = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Person, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Edit profile")
                    }
                    if (profileNote.isNotBlank()) Text(profileNote, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(18.dp))
                    Spacer(Modifier.height(18.dp))
                    val context = LocalContext.current
                    val notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = MaterialTheme.shapes.large) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Dark theme", style = MaterialTheme.typography.titleSmall)
                                Text("Obsidian developer palette", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = darkTheme, onCheckedChange = onThemeChange)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Notifications", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (notificationsEnabled) "Notifications are enabled. New alerts can appear in your device notification bar."
                        else "Notifications are currently disabled. Enable permission to receive message alerts in your notification bar.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = requestNotifications, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Notifications, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (notificationsEnabled) "Notification settings" else "Enable notifications")
                    }
                    Spacer(Modifier.height(18.dp))
                    Text("Attachments use hosted links. CodeBihin does not upload or store your files.")
                    Text("Voice/video calls open a private meeting room in your browser or installed meeting app.")
                }
            }
        }
    }

    if (showEditProfile) {
        AlertDialog(
            onDismissRequest = { if (!profileBusy) showEditProfile = false },
            title = { Text("Edit your profile") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(profileName, { profileName = it }, label = { Text("Display name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(profilePhone, { profilePhone = it }, label = { Text("Phone number") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(profileBio, { profileBio = it }, label = { Text("About you") }, maxLines = 3, modifier = Modifier.fillMaxWidth())
                    Text("Your email address is managed by your sign-in account.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(enabled = !profileBusy && profileName.isNotBlank(), onClick = {
                    profileBusy = true
                    val name = profileName.trim()
                    val phone = profilePhone.trim()
                    val bio = profileBio.trim()
                    val profile = mapOf(
                        "displayName" to name,
                        "phone" to phone,
                        "bio" to bio,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    db.collection("users").document(uid).set(profile, SetOptions.merge())
                        .addOnSuccessListener {
                            db.collection("publicProfiles").document(uid).set(
                                mapOf("displayName" to name, "bio" to bio, "updatedAt" to FieldValue.serverTimestamp()),
                                SetOptions.merge()
                            ).addOnCompleteListener {
                                auth.currentUser?.updateProfile(
                                    com.google.firebase.auth.UserProfileChangeRequest.Builder().setDisplayName(name).build()
                                )?.addOnCompleteListener {
                                    profileBusy = false
                                    profileNote = "Profile updated."
                                    showEditProfile = false
                                } ?: run {
                                    profileBusy = false
                                    profileNote = "Profile saved."
                                    showEditProfile = false
                                }
                            }
                        }
                        .addOnFailureListener {
                            profileBusy = false
                            profileNote = it.localizedMessage ?: "Could not update profile."
                        }
                }) { Text(if (profileBusy) "Saving…" else "Save changes") }
            },
            dismissButton = { TextButton(enabled = !profileBusy, onClick = { showEditProfile = false }) { Text("Cancel") } }
        )
    }

    if (showChannelDialog) {
        ChannelCreateDialog(
            onDismiss = { showChannelDialog = false },
            onCreate = { name, description ->
                val ref = db.collection("channels").document()
                ref.set(mapOf(
                    "name" to name,
                    "description" to description,
                    "ownerUid" to uid,
                    "admins" to listOf(uid),
                    "members" to listOf(uid),
                    "visibility" to "public",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )).addOnFailureListener { error = it.localizedMessage ?: "Could not create channel." }
                showChannelDialog = false
            }
        )
    }

    if (showGroupDialog) {
        GroupCreateDialog(
            people = people,
            onDismiss = { showGroupDialog = false },
            onCreate = { title, selected ->
                val members = (selected + uid).distinct()
                val id = UUID.randomUUID().toString()
                db.collection("chats").document(id).set(
                    mapOf(
                        "members" to members,
                        "admins" to listOf(uid),
                        "title" to title,
                        "isGroup" to true,
                        "createdBy" to uid,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                        "lastMessage" to "Group created"
                    )
                ).addOnSuccessListener {
                    selectedChat = ChatItem(id, title, "Group created", true, members)
                    tab = "Chats"
                }.addOnFailureListener { error = it.localizedMessage ?: "Could not create group." }
                showGroupDialog = false
            }
        )
    }
}


@Composable
private fun ChannelCreateDialog(onDismiss: () -> Unit, onCreate: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create public channel") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("Channel name") }, singleLine = true)
                OutlinedTextField(description, { description = it }, label = { Text("Description") }, maxLines = 3)
                Text("Only channel owners and admins can publish posts.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onCreate(name.trim(), description.trim()) }) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelScreen(channel: ChannelItem, onBack: () -> Unit) {
    val uid = auth.currentUser?.uid.orEmpty()
    var posts by remember { mutableStateOf<List<ChannelPost>>(emptyList()) }
    var draft by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val canPost = channel.ownerUid == uid || channel.admins.contains(uid)
    DisposableEffect(channel.id) {
        val listener = db.collection("channels").document(channel.id).collection("posts")
            .orderBy("createdAt", Query.Direction.DESCENDING).limit(100)
            .addSnapshotListener { snapshot, e ->
                if (e != null) error = e.localizedMessage ?: "Could not load channel posts."
                else posts = snapshot?.documents?.map { d -> ChannelPost(d.id, d.getString("text") ?: "", d.getString("authorUid") ?: "") } ?: emptyList()
            }
        onDispose { listener.remove() }
    }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                actionIconContentColor = MaterialTheme.colorScheme.onSurface
            ),
            title = { Column { Text(channel.name); Text("Public channel", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } })
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp))
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(posts, key = { it.id }) { post ->
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) { Text(post.text); Text(if (post.authorUid == uid) "You" else "Channel admin", style = MaterialTheme.typography.labelSmall) } }
            }
        }
        if (canPost) {
            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(draft, { draft = it }, modifier = Modifier.weight(1f), label = { Text("Write an announcement") }, maxLines = 3)
                IconButton(enabled = draft.isNotBlank(), onClick = {
                    val value = draft.trim()
                    db.collection("channels").document(channel.id).collection("posts").add(mapOf("authorUid" to uid, "text" to value, "attachmentUrl" to "", "createdAt" to FieldValue.serverTimestamp()))
                        .addOnSuccessListener { draft = "" }
                        .addOnFailureListener { error = it.localizedMessage ?: "Could not publish." }
                }) { Icon(Icons.Filled.Send, contentDescription = "Publish") }
            }
        } else {
            Text("Only this channel's owner or administrators can publish.", Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
        }
    }
}

private data class ChannelPost(val id: String, val text: String, val authorUid: String)

@Composable
private fun GroupCreateDialog(people: List<Person>, onDismiss: () -> Unit, onCreate: (String, List<String>) -> Unit) {
    var title by remember { mutableStateOf("") }
    val selected = remember { mutableStateListOf<String>() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create a group") },
        text = {
            Column {
                OutlinedTextField(title, { title = it }, label = { Text("Group name") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                Text("Choose members")
                LazyColumn(Modifier.heightIn(max = 260.dp)) {
                    items(people, key = { it.uid }) { p ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = selected.contains(p.uid), onCheckedChange = {
                                if (it) selected.add(p.uid) else selected.remove(p.uid)
                            })
                            Text(p.name)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = title.isNotBlank() && selected.isNotEmpty(), onClick = { onCreate(title.trim(), selected.toList()) }) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatScreen(chat: ChatItem, onBack: () -> Unit, openExternal: (String) -> Unit) {
    val uid = auth.currentUser?.uid.orEmpty()
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var text by remember { mutableStateOf("") }
    var link by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var replyTarget by remember { mutableStateOf<ChatMessage?>(null) }
    var menuMessageId by remember { mutableStateOf<String?>(null) }
    var editingMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var editText by remember { mutableStateOf("") }
    var deletingMessage by remember { mutableStateOf<ChatMessage?>(null) }

    DisposableEffect(chat.id) {
        val listener = db.collection("chats").document(chat.id).collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, e ->
                if (e != null) note = e.localizedMessage ?: "Could not load messages."
                else messages = snap?.documents?.map { d ->
                    val readBy = (d.get("readBy") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                    val parsed = ChatMessage(
                        id = d.id,
                        senderId = d.getString("senderId") ?: "",
                        senderName = d.getString("senderName") ?: "User",
                        text = d.getString("text") ?: "",
                        attachmentUrl = d.getString("attachmentUrl") ?: "",
                        replyToText = d.getString("replyToText") ?: "",
                        edited = d.getTimestamp("editedAt") != null,
                        readBy = readBy
                    )
                    if (parsed.senderId != uid && uid !in readBy) {
                        d.reference.update("readBy", FieldValue.arrayUnion(uid))
                    }
                    parsed
                } ?: emptyList()
            }
        onDispose { listener.remove() }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primary,
                titleContentColor = Color.White,
                navigationIconContentColor = Color.White,
                actionIconContentColor = Color.White
            ),
            title = { Column { Text(chat.title); Text(if (chat.isGroup) "Group chat" else "Private chat", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } },
            actions = {
                TextButton(onClick = {
                    val room = "https://meet.jit.si/CodeBihin-${UUID.randomUUID()}"
                    val callMessage = mapOf(
                        "senderId" to uid,
                        "senderName" to (auth.currentUser?.displayName ?: "User"),
                        "text" to "Join the voice/video call: $room",
                        "attachmentUrl" to room,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                    db.collection("chats").document(chat.id).collection("messages").add(callMessage)
                        .addOnSuccessListener {
                            db.collection("chats").document(chat.id).update(
                                mapOf("lastMessage" to "Call invitation", "updatedAt" to FieldValue.serverTimestamp())
                            )
                            openExternal(room)
                        }
                        .addOnFailureListener { note = it.localizedMessage ?: "Could not start call." }
                }) { Text("Call") }
            }
        )
        if (note.isNotBlank()) Text(note, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 12.dp))
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp), reverseLayout = false) {
            items(messages, key = { it.id }) { msg ->
                val mine = msg.senderId == uid
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.padding(vertical = 4.dp).widthIn(max = 310.dp)
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            if (chat.isGroup && !mine) Text(msg.senderName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            if (msg.replyToText.isNotBlank()) Text("↪ " + msg.replyToText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            if (msg.text.isNotBlank()) Text(msg.text)
                            if (msg.edited) Text("edited", style = MaterialTheme.typography.labelSmall)
                            if (mine) Text(if (msg.readBy.any { it != uid && it in chat.members }) "Seen" else "Sent", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            if (msg.attachmentUrl.isNotBlank()) TextButton(onClick = { openExternal(msg.attachmentUrl) }) { Text("Open shared link ↗") }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { replyTarget = msg }) { Icon(Icons.Filled.Reply, contentDescription = "Reply"); Text("Reply") }
                                if (mine) {
                                    Box {
                                        IconButton(onClick = { menuMessageId = msg.id }) { Icon(Icons.Filled.MoreVert, contentDescription = "Message options") }
                                        DropdownMenu(expanded = menuMessageId == msg.id, onDismissRequest = { menuMessageId = null }) {
                                            DropdownMenuItem(text = { Text("Edit") }, leadingIcon = { Icon(Icons.Filled.Edit, null) }, onClick = { editingMessage = msg; editText = msg.text; menuMessageId = null })
                                            DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Filled.Delete, null) }, onClick = { deletingMessage = msg; menuMessageId = null })
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        HorizontalDivider()
        Column(Modifier.padding(10.dp)) {
            if (replyTarget != null) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Replying to: " + (replyTarget?.text ?: "").take(90), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { replyTarget = null }) { Text("Cancel") }
                }
            }
            OutlinedTextField(text, { text = it }, label = { Text("Message") }, modifier = Modifier.fillMaxWidth(), maxLines = 3)
            OutlinedTextField(link, { link = it }, label = { Text("Optional hosted file / Drive link") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(enabled = !busy && (text.isNotBlank() || link.isNotBlank()), onClick = {
                    val body = text.trim()
                    val attachment = link.trim()
                    if (attachment.isNotBlank() && !attachment.startsWith("https://")) {
                        note = "Please use a secure https:// hosted link."
                    } else {
                        busy = true
                        val userName = auth.currentUser?.displayName ?: auth.currentUser?.email ?: "User"
                        val payload = mapOf(
                            "senderId" to uid,
                            "senderName" to userName,
                            "text" to body,
                            "attachmentUrl" to attachment,
                            "replyToId" to (replyTarget?.id ?: ""),
                            "replyToText" to (replyTarget?.text?.take(300) ?: ""),
                            "readBy" to emptyList<String>(),
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                        db.collection("chats").document(chat.id).collection("messages").add(payload)
                            .addOnSuccessListener {
                                db.collection("chats").document(chat.id).update(
                                    mapOf("lastMessage" to if (body.isNotBlank()) body else "Shared a link", "updatedAt" to FieldValue.serverTimestamp())
                                )
                                text = ""; link = ""; replyTarget = null; busy = false; note = ""
                            }
                            .addOnFailureListener { busy = false; note = it.localizedMessage ?: "Message failed." }
                    }
                }) { Icon(Icons.Filled.Send, contentDescription = "Send message"); Spacer(Modifier.width(6.dp)); Text(if (busy) "Sending…" else "Send") }
            }
        }
    }

    if (editingMessage != null) {
        AlertDialog(
            onDismissRequest = { editingMessage = null },
            title = { Text("Edit message") },
            text = { OutlinedTextField(editText, { editText = it }, modifier = Modifier.fillMaxWidth(), maxLines = 5) },
            confirmButton = {
                TextButton(enabled = editText.isNotBlank() && editText.length <= 4000, onClick = {
                    val target = editingMessage!!
                    db.collection("chats").document(chat.id).collection("messages").document(target.id)
                        .update(mapOf("text" to editText.trim(), "editedAt" to FieldValue.serverTimestamp()))
                        .addOnSuccessListener { note = "Message updated." }
                        .addOnFailureListener { note = it.localizedMessage ?: "Could not edit message." }
                    editingMessage = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editingMessage = null }) { Text("Cancel") } }
        )
    }

    if (deletingMessage != null) {
        AlertDialog(
            onDismissRequest = { deletingMessage = null },
            title = { Text("Delete message?") },
            text = { Text("This removes the message for everyone in this conversation.") },
            confirmButton = {
                TextButton(onClick = {
                    val target = deletingMessage!!
                    db.collection("chats").document(chat.id).collection("messages").document(target.id)
                        .delete()
                        .addOnSuccessListener { note = "Message deleted." }
                        .addOnFailureListener { note = it.localizedMessage ?: "Could not delete message." }
                    deletingMessage = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deletingMessage = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun EmptyState(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, style = MaterialTheme.typography.bodySmall)
    }
}

private data class Person(val uid: String, val name: String, val email: String)
private data class ChannelItem(val id: String, val name: String, val description: String, val ownerUid: String, val admins: List<String>)
private data class AnnouncementItem(val id: String, val title: String, val body: String)
private data class ChatItem(val id: String, val title: String, val lastMessage: String, val isGroup: Boolean, val members: List<String>)
private data class ChatMessage(val id: String, val senderId: String, val senderName: String, val text: String, val attachmentUrl: String, val replyToText: String = "", val edited: Boolean = false, val readBy: List<String> = emptyList())

internal fun registerCurrentDeviceToken() {
    FirebaseMessaging.getInstance().token.addOnSuccessListener { token -> addDeviceToken(token) }
}

internal fun addDeviceToken(token: String) {
    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
    FirebaseFirestore.getInstance().collection("users").document(uid)
        .set(mapOf("fcmTokens" to FieldValue.arrayUnion(token)), SetOptions.merge())
}
