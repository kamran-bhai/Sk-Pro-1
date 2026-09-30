package com.protectfinanceddevices.app.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.protectfinanceddevices.app.core.network.ApiClient
import com.protectfinanceddevices.app.core.network.ApiConfig
import com.protectfinanceddevices.app.core.network.AuthSessionStore
import com.protectfinanceddevices.app.ui.theme.*

private data class ControlKeyUi(
    val id: String,
    val last4: String,
    val deviceId: String?,
    val enrollmentId: String?,
    val status: String,
    val expiresAt: String?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyManagementScreen(
    sessionStore: AuthSessionStore,
    onBack: () -> Unit,
    onSessionExpired: () -> Unit
) {
    val apiClient = remember { ApiClient() }
    val scope = rememberCoroutineScope()
    var keys by remember { mutableStateOf<List<ControlKeyUi>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var issuedKey by remember { mutableStateOf<String?>(null) }
    var expiresInDays by remember { mutableStateOf("30") }
    var refreshNonce by remember { mutableIntStateOf(0) }

    LaunchedEffect(refreshNonce) {
        val token = sessionStore.accessToken
        if (token.isNullOrBlank()) {
            onSessionExpired()
            return@LaunchedEffect
        }

        busy = true
        val response = apiClient.get(ApiConfig.ENDPOINT_CONTROL_KEYS, accessToken = token)
        busy = false

        if (response.statusCode == 401 || response.statusCode == 403) {
            sessionStore.clear()
            onSessionExpired()
            return@LaunchedEffect
        }

        if (!response.isSuccess) {
            error = response.errorMessage ?: "Could not load control keys."
            return@LaunchedEffect
        }

        val array = response.data?.optJSONArray("data")
        keys = buildList {
            for (i in 0 until (array?.length() ?: 0)) {
                val item = array?.optJSONObject(i) ?: continue
                add(
                    ControlKeyUi(
                        id = item.optString("id"),
                        last4 = item.optString("keyLast4"),
                        deviceId = item.optString("deviceId").takeIf { it.isNotBlank() },
                        enrollmentId = item.optString("enrollmentId").takeIf { it.isNotBlank() },
                        status = item.optString("status"),
                        expiresAt = item.optString("expiresAt").takeIf { it.isNotBlank() }
                    )
                )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Key Management", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { refreshNonce++ }, enabled = !busy) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
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
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Slate900),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("ONE KEY • ONE DEVICE", color = Slate100, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "The backend issues the key. The raw secret is shown once and is never stored in the Android app.",
                            color = Slate400,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(14.dp))
                        OutlinedTextField(
                            value = expiresInDays,
                            onValueChange = { expiresInDays = it.filter(Char::isDigit).take(3) },
                            label = { Text("Expiry (days)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                val token = sessionStore.accessToken
                                val days = expiresInDays.toIntOrNull()
                                if (token.isNullOrBlank()) {
                                    onSessionExpired()
                                    return@Button
                                }
                                if (days == null || days !in 1..365) {
                                    error = "Expiry must be between 1 and 365 days."
                                    return@Button
                                }

                                scope.launch {
                                    busy = true
                                    error = null
                                    val response = apiClient.post(
                                        ApiConfig.ENDPOINT_CONTROL_KEYS,
                                        org.json.JSONObject().put("expiresInDays", days),
                                        token
                                    )
                                    busy = false

                                    if (response.statusCode == 401 || response.statusCode == 403) {
                                        sessionStore.clear()
                                        onSessionExpired()
                                    } else if (response.isSuccess) {
                                        issuedKey = response.data?.optJSONObject("data")
                                            ?.optString("controlKey")
                                            ?.takeIf { it.isNotBlank() }
                                        refreshNonce++
                                    } else {
                                        error = response.errorMessage ?: "Could not issue key."
                                    }
                                }
                            },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Key, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (busy) "Working..." else "Issue New Control Key")
                        }
                    }
                }
            }

            error?.let { message ->
                item { Text(message, color = CrimsonRed) }
            }

            item {
                Text(
                    "ISSUED KEYS",
                    color = Slate400,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelSmall
                )
            }

            if (keys.isEmpty() && !busy) {
                item { Text("No control keys found.", color = Slate400) }
            }

            items(keys, key = { it.id }) { key ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Slate900),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("•••• ${key.last4}", color = Slate100, fontWeight = FontWeight.Bold)
                            Text(
                                key.status,
                                color = if (key.status == "ACTIVATED") EmeraldGreen else Slate400
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Device: ${key.deviceId ?: "Not bound"}", color = Slate400)
                        Text("Enrollment: ${key.enrollmentId ?: "Not bound"}", color = Slate400)
                        key.expiresAt?.let { Text("Expires: $it", color = Slate500) }

                        if (key.status == "ISSUED" || key.status == "ACTIVATED") {
                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = {
                                    val token = sessionStore.accessToken ?: return@OutlinedButton
                                    scope.launch {
                                        busy = true
                                        val response = apiClient.post(
                                            "${ApiConfig.ENDPOINT_CONTROL_KEYS}/${key.id}/revoke",
                                            org.json.JSONObject(),
                                            token
                                        )
                                        busy = false
                                        if (response.statusCode == 401 || response.statusCode == 403) {
                                            sessionStore.clear()
                                            onSessionExpired()
                                        } else if (response.isSuccess) {
                                            refreshNonce++
                                        } else {
                                            error = response.errorMessage ?: "Could not revoke key."
                                        }
                                    }
                                },
                                enabled = !busy
                            ) {
                                Icon(Icons.Default.Block, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Revoke")
                            }
                        }
                    }
                }
            }
        }
    }

    issuedKey?.let { rawKey ->
        AlertDialog(
            onDismissRequest = { issuedKey = null },
            title = { Text("Control Key Issued") },
            text = {
                Column {
                    Text("Copy this key now. It will not be shown again.")
                    Spacer(Modifier.height(12.dp))
                    SelectionContainer {
                        Text(rawKey, color = PrimaryBlue, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { issuedKey = null }) { Text("Done") }
            }
        )
    }
}
