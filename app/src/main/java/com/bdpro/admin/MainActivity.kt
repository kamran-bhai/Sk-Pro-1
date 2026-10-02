package com.bdpro.admin

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val modules = listOf(
    "Add Device", "Device List", "Run Command", "Auto Lock", "Anti Theft",
    "Location", "Diagnostics", "Customers", "EMI / Installment", "eNACH",
    "Remove Device", "Admin Profile"
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { BDProApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BDProApp() {
    val context = LocalContext.current
    val session = remember { SessionManager(context) }
    var loggedIn by remember { mutableStateOf(session.isLoggedIn()) }
    var selected by remember { mutableStateOf("Dashboard") }

    MaterialTheme {
        if (!loggedIn) {
            LoginScreen { token ->
                session.saveToken(token)
                loggedIn = true
            }
        } else {
            Scaffold(
                topBar = { TopAppBar(title = { Text("BD Pro • $selected") }) }
            ) { pad ->
                Column(Modifier.padding(pad).padding(16.dp)) {
                    Text("Admin Dashboard", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    Text("Admin-controlled Android device management")
                    Spacer(Modifier.height(16.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(modules) { name ->
                            ElevatedButton(
                                onClick = { selected = name },
                                modifier = Modifier.height(86.dp)
                            ) {
                                Text(name)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoginScreen(onLogin: (String) -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("BD Pro", style = MaterialTheme.typography.headlineLarge)
        Text("Admin Control Panel")
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Admin email") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(20.dp))

        Button(
            onClick = {
                loading = true
                error = null
                Thread {
                    val result = ApiClient.login(email, password)
                    Handler(Looper.getMainLooper()).post {
                        loading = false
                        result
                            .onSuccess { onLogin(it.token) }
                            .onFailure { error = it.message ?: "Login failed" }
                    }
                }.start()
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !loading && email.isNotBlank() && password.isNotBlank()
        ) {
            Text(if (loading) "LOGGING IN..." else "LOGIN")
        }

        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}
