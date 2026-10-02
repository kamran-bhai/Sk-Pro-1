package com.bdpro.admin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private val modules = listOf(
    "Add Device","Device List","Run Command","Auto Lock","Anti Theft",
    "Location","Diagnostics","Customers","EMI / Installment","eNACH","Remove Device","Admin Profile"
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
    var loggedIn by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf("Dashboard") }

    MaterialTheme {
        if (!loggedIn) {
            LoginScreen { loggedIn = true }
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
                            ) { Text(name) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoginScreen(onLogin: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("BD Pro", style = MaterialTheme.typography.headlineLarge)
        Text("Admin Control Panel")
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(email, { email = it }, label = { Text("Admin email") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(password, { password = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(20.dp))
        Button(onClick = onLogin, modifier = Modifier.fillMaxWidth(), enabled = email.isNotBlank() && password.isNotBlank()) {
            Text("LOGIN")
        }
    }
}
