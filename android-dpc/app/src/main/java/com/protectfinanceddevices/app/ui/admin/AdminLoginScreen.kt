package com.protectfinanceddevices.app.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.protectfinanceddevices.app.core.network.ApiClient
import com.protectfinanceddevices.app.core.network.ApiConfig
import com.protectfinanceddevices.app.core.network.AuthSessionStore
import com.protectfinanceddevices.app.ui.theme.*

@Composable
fun AdminLoginScreen(
    sessionStore: AuthSessionStore,
    onLoggedIn: () -> Unit,
    onBack: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val apiClient = remember { ApiClient() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Admin Login") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Slate900,
                    titleContentColor = Slate100,
                    navigationIconContentColor = Slate100
                )
            )
        },
        containerColor = Slate950
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = PrimaryBlue,
                modifier = Modifier.size(48.dp)
            )

            Spacer(Modifier.height(16.dp))

            Text(
                "PROTECT YOUR FINANCED DEVICES",
                style = MaterialTheme.typography.titleLarge,
                color = Slate100
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "Admin authentication is required for control-key management.",
                color = Slate400
            )

            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it; error = null },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Admin email") },
                singleLine = true
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it; error = null },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )

            error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = CrimsonRed)
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = {
                    if (email.isBlank() || password.isBlank()) {
                        error = "Email and password are required."
                        return@Button
                    }

                    busy = true
                    error = null

                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (busy) "Signing in..." else "Sign in")
            }

            LaunchedEffect(busy) {
                if (!busy) return@LaunchedEffect

                val response = apiClient.post(
                    ApiConfig.ENDPOINT_LOGIN,
                    org.json.JSONObject()
                        .put("email", email.trim())
                        .put("password", password)
                )

                busy = false

                if (response.isSuccess) {
                    val data = response.data?.optJSONObject("data")
                    val accessToken = data?.optString("accessToken").orEmpty()
                    val refreshToken = data?.optString("refreshToken").orEmpty()

                    if (accessToken.isNotBlank() && refreshToken.isNotBlank()) {
                        sessionStore.save(accessToken, refreshToken)
                        onLoggedIn()
                    } else {
                        error = "Login response did not contain valid session tokens."
                    }
                } else {
                    error = response.errorMessage ?: "Login failed."
                }
            }
        }
    }
}
