package `in`.codebihin.messaging

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

        setContent { MaterialTheme { CodeBihinRoot(::openExternal, ::requestNotificationPermission) } }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName))
    }

    private fun openExternal(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }
}

private val db get() = FirebaseFirestore.getInstance()
private val auth get() = FirebaseAuth.getInstance()

@Composable
private fun CodeBihinRoot(openExternal: (String) -> Unit, requestNotifications: () -> Unit) {
    var currentUser by remember { mutableStateOf(auth.currentUser) }
    var blocked by remember { mutableStateOf(false) }
    DisposableEffect(currentUser?.uid) {
        val uid = currentUser?.uid
        if (uid == null) { onDispose { } } else {
            val registration = db.collection("users").document(uid).addSnapshotListener { snapshot, _ ->
                val data = snapshot?.data
                if (data?.get("disabled") == true) {
                    blocked = true
                    auth.signOut()
                } else if (data?.get("forceLogout") == true) {
                    db.collection("users").document(uid).update("forceLogout", false)
                        .addOnCompleteListener { auth.signOut() }
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
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (currentUser == null) { if (blocked) Text("This account has been blocked or signed out by an administrator.", modifier = Modifier.padding(24.dp)) else AuthScreen() } else MessagingScreen(openExternal, requestNotifications)
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
                                user.updateProfile(com.google.firebase.auth.UserProfileChangeRequest.Builder().setDisplayName(name.trim()).build())
                                val profile = mapOf(
                                    "uid" to user.uid,
                                    "displayName" to name.trim(),
                                    "email" to (user.email ?: email),
                                    "createdAt" to FieldValue.serverTimestamp()
                                )
                                db.collection("users").document(user.uid).set(profile, SetOptions.merge())
                                db.collection("publicProfiles").document(user.uid).set(mapOf("uid" to user.uid, "displayName" to name.trim(), "createdAt" to FieldValue.serverTimestamp()), SetOptions.merge())
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
private fun MessagingScreen(openExternal: (String) -> Unit, requestNotifications: () -> Unit) {
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
            if (tab == "Channels") FloatingActionButton(onClick = { showChannelDialog = true }) { Text("+") }
        },
        bottomBar = {
            NavigationBar {
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
        TopAppBar(title = { Column { Text(channel.name); Text("Public channel", style = MaterialTheme.typography.labelSmall) } }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } })
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
            title = { Column { Text(chat.title); Text(if (chat.isGroup) "Group chat" else "Private chat", style = MaterialTheme.typography.labelSmall) } },
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
