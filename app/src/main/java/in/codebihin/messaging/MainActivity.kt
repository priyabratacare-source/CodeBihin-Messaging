package in.codebihin.messaging

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CodeBihinApp()
                }
            }
        }
    }
}

@Composable
private fun CodeBihinApp() {
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }
    var user by remember { mutableStateOf(auth.currentUser) }
    var displayName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isRegister by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { user = it.currentUser }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    fun saveProfile(uid: String, name: String, userEmail: String) {
        val profile = hashMapOf(
            "displayName" to name,
            "email" to userEmail,
            "createdAt" to FieldValue.serverTimestamp()
        )
        db.collection("users").document(uid).set(profile)
            .addOnSuccessListener { message = "Account ready. Welcome to CodeBihin Messaging!" }
            .addOnFailureListener { message = it.localizedMessage ?: "Could not save profile." }
    }

    if (user == null) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text("CodeBihin Messaging", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(if (isRegister) "Create your account" else "Sign in to continue")
            Spacer(Modifier.height(24.dp))

            if (isRegister) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Your name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
            }
            OutlinedTextField(
                value = email,
                onValueChange = { email = it.trim() },
                label = { Text("Email address") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = {
                    if (email.isBlank() || password.length < 6 ||
                        (isRegister && displayName.isBlank())
                    ) {
                        message = "Enter valid details. Password must be at least 6 characters."
                        return@Button
                    }
                    busy = true
                    message = ""
                    val task = if (isRegister) {
                        auth.createUserWithEmailAndPassword(email, password)
                    } else {
                        auth.signInWithEmailAndPassword(email, password)
                    }
                    task.addOnCompleteListener { result ->
                        busy = false
                        if (result.isSuccessful) {
                            val current = auth.currentUser
                            if (isRegister && current != null) {
                                saveProfile(current.uid, displayName.trim(), email)
                            } else {
                                message = "Signed in successfully."
                            }
                        } else {
                            message = result.exception?.localizedMessage ?: "Authentication failed."
                        }
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (busy) "Please wait..." else if (isRegister) "Create account" else "Sign in")
            }
            TextButton(onClick = {
                isRegister = !isRegister
                message = ""
            }) {
                Text(if (isRegister) "Already have an account? Sign in" else "New here? Create an account")
            }
            if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary)
        }
    } else {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text("Welcome to CodeBihin", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(user?.email ?: "Signed in")
            Spacer(Modifier.height(12.dp))
            Text("Your account is connected to Firebase. Chat features are the next development milestone.")
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { auth.signOut(); message = "" },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Sign out") }
        }
    }
}

internal fun addDeviceToken(token: String) {
    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
    FirebaseFirestore.getInstance()
        .collection("users").document(uid)
        .update("fcmTokens", FieldValue.arrayUnion(token))
}
