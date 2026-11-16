package in.codebihin.messaging

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)

        setContent { MaterialTheme { CodeBihinRoot(::openExternal) } }
    }

    private fun openExternal(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }
}

private val db get() = FirebaseFirestore.getInstance()
private val auth get() = FirebaseAuth.getInstance()

@Composable
private fun CodeBihinRoot(openExternal: (String) -> Unit) {
    var currentUser by remember { mutableStateOf(auth.currentUser) }
    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { currentUser = it.currentUser }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (currentUser == null) AuthScreen() else MessagingScreen(openExternal)
    }
}

@Composable
private fun AuthScreen() {
    var register by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("CodeBihin", style = MaterialTheme.typography.headlineLarge)
        Text("Messaging", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Text(if (register) "Create your account" else "Welcome back")
        Spacer(Modifier.height(22.dp))
        if (register) {
            OutlinedTextField(name, { name = it }, label = { Text("Full name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
        }
        OutlinedTextField(email, { email = it.trim() }, label = { Text("Email") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(password, { password = it }, label = { Text("Password (6+ characters)") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                if (email.isBlank() || password.length < 6 || (register && name.isBlank())) {
                    note = "Please complete all required fields."
                } else {
                    busy = true
                    val task = if (register) auth.createUserWithEmailAndPassword(email, password)
                    else auth.signInWithEmailAndPassword(email, password)
                    task.addOnCompleteListener { result ->
                        busy = false
                        if (result.isSuccessful) {
                            val user = auth.currentUser
                            if (user != null && register) {
                                val profile = mapOf(
                                    "uid" to user.uid,
                                    "displayName" to name.trim(),
                                    "email" to (user.email ?: email),
                                    "createdAt" to FieldValue.serverTimestamp()
                                )
                                db.collection("users").document(user.uid).set(profile, SetOptions.merge())
                                db.collection("publicProfiles").document(user.uid).set(profile, SetOptions.merge())
                            }
                            registerCurrentDeviceToken()
                            note = "Account ready."
                        } else note = result.exception?.localizedMessage ?: "Authentication failed."
                    }
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (busy) "Please wait…" else if (register) "Create account" else "Sign in") }
        TextButton(onClick = { register = !register; note = "" }) {
            Text(if (register) "Already registered? Sign in" else "Create a new account")
        }
        if (note.isNotBlank()) Text(note, color = MaterialTheme.colorScheme.primary)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessagingScreen(openExternal: (String) -> Unit) {
    val uid = auth.currentUser?.uid.orEmpty()
    var tab by remember { mutableStateOf("Chats") }
    var selectedChat by remember { mutableStateOf<ChatItem?>(null) }
    var chats by remember { mutableStateOf<List<ChatItem>>(emptyList()) }
    var people by remember { mutableStateOf<List<Person>>(emptyList()) }
    var error by remember { mutableStateOf("") }
    var showGroupDialog by remember { mutableStateOf(false) }

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
        onDispose { chatListener?.remove() }
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
                title = {
                    Column {
                        Text("CodeBihin Messaging")
                        Text("Private conversations, groups and calls", style = MaterialTheme.typography.labelSmall)
                    }
                },
                actions = {
                    TextButton(onClick = { auth.signOut() }) { Text("Sign out") }
                }
            )
        },
        floatingActionButton = {
            if (tab == "Chats") FloatingActionButton(onClick = { tab = "People" }) { Text("+") }
            if (tab == "Groups") FloatingActionButton(onClick = { showGroupDialog = true }) { Text("+") }
        },
        bottomBar = {
            NavigationBar {
                listOf("Chats", "People", "Groups", "Profile").forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Text(when (item) { "Chats" -> "◉"; "People" -> "◎"; "Groups" -> "◌"; else -> "●" }) },
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
                    Text("Your conversations", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 12.dp))
                    if (chats.isEmpty()) EmptyState("No conversations yet", "Open People and start a private chat.")
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(chats, key = { it.id }) { chat ->
                            Card(Modifier.fillMaxWidth().clickable { selectedChat = chat }) {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                                        Text(if (chat.isGroup) "G" else chat.title.take(1).uppercase(), Modifier.padding(12.dp))
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(chat.title, style = MaterialTheme.typography.titleMedium)
                                        Text(chat.lastMessage.ifBlank { if (chat.isGroup) "Group conversation" else "Start chatting" }, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
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
                    Text("Your account", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 12.dp))
                    Text(auth.currentUser?.displayName ?: auth.currentUser?.email.orEmpty())
                    Text("User ID: $uid", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(18.dp))
                    Text("Attachments use hosted links. CodeBihin does not upload or store your files.")
                    Text("Voice/video calls open a private meeting room in your browser or installed meeting app.")
                }
            }
        }
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

    DisposableEffect(chat.id) {
        val listener = db.collection("chats").document(chat.id).collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, e ->
                if (e != null) note = e.localizedMessage ?: "Could not load messages."
                else messages = snap?.documents?.map { d ->
                    ChatMessage(
                        id = d.id,
                        senderId = d.getString("senderId") ?: "",
                        senderName = d.getString("senderName") ?: "User",
                        text = d.getString("text") ?: "",
                        attachmentUrl = d.getString("attachmentUrl") ?: ""
                    )
                } ?: emptyList()
            }
        onDispose { listener.remove() }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Column { Text(chat.title); Text(if (chat.isGroup) "Group chat" else "Private chat", style = MaterialTheme.typography.labelSmall) } },
            navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            actions = {
                TextButton(onClick = {
                    val room = "https://meet.jit.si/CodeBihin-${chat.id}"
                    openExternal(room)
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
                            if (msg.text.isNotBlank()) Text(msg.text)
                            if (msg.attachmentUrl.isNotBlank()) TextButton(onClick = { openExternal(msg.attachmentUrl) }) { Text("Open shared link ↗") }
                        }
                    }
                }
            }
        }
        HorizontalDivider()
        Column(Modifier.padding(10.dp)) {
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
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                        db.collection("chats").document(chat.id).collection("messages").add(payload)
                            .addOnSuccessListener {
                                db.collection("chats").document(chat.id).update(
                                    mapOf("lastMessage" to if (body.isNotBlank()) body else "Shared a link", "updatedAt" to FieldValue.serverTimestamp())
                                )
                                text = ""; link = ""; busy = false; note = ""
                            }
                            .addOnFailureListener { busy = false; note = it.localizedMessage ?: "Message failed." }
                    }
                }) { Text(if (busy) "Sending…" else "Send") }
            }
        }
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
private data class ChatItem(val id: String, val title: String, val lastMessage: String, val isGroup: Boolean, val members: List<String>)
private data class ChatMessage(val id: String, val senderId: String, val senderName: String, val text: String, val attachmentUrl: String)

internal fun registerCurrentDeviceToken() {
    FirebaseMessaging.getInstance().token.addOnSuccessListener { token -> addDeviceToken(token) }
}

internal fun addDeviceToken(token: String) {
    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
    FirebaseFirestore.getInstance().collection("users").document(uid)
        .set(mapOf("fcmTokens" to FieldValue.arrayUnion(token)), SetOptions.merge())
}
