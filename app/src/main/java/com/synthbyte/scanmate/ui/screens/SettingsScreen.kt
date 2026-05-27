package com.synthbyte.scanmate.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.synthbyte.scanmate.BuildConfig
import com.synthbyte.scanmate.data.SettingsRepository
import com.synthbyte.scanmate.data.ThemeMode
import com.synthbyte.scanmate.domain.GeminiHelper
import com.synthbyte.scanmate.domain.GeminiModels
import com.synthbyte.scanmate.utils.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { SettingsRepository(context) }
    val apiKey by repository.geminiApiKeyFlow.collectAsState(initial = "")
    val selectedModelId by repository.geminiModelIdFlow.collectAsState(initial = GeminiModels.DEFAULT_MODEL_ID)
    val selectedModel = GeminiModels.optionFor(selectedModelId)
    val themeMode by repository.themeModeFlow.collectAsState(initial = ThemeMode.SYSTEM)
    val defaultWorkspace by repository.defaultWorkspaceFlow.collectAsState(initial = "Inbox")
    var currentKeyInput by remember { mutableStateOf("") }
    var defaultWorkspaceInput by remember { mutableStateOf("Inbox") }
    val coroutineScope = rememberCoroutineScope()
    var isSaved by remember { mutableStateOf(false) }
    var modelMenuExpanded by remember { mutableStateOf(false) }
    var isTestingApi by remember { mutableStateOf(false) }
    var testMessage by remember { mutableStateOf("") }
    var testIsError by remember { mutableStateOf(false) }
    var isOnline by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isOnline = withContext(Dispatchers.IO) { NetworkUtils.isOnline(context) }
    }
    LaunchedEffect(apiKey) { currentKeyInput = apiKey.orEmpty() }
    LaunchedEffect(defaultWorkspace) { defaultWorkspaceInput = defaultWorkspace }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatusCard(isOnline = isOnline)

            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Palette, null, tint = MaterialTheme.colorScheme.primary)
                        Text("Theme", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp))
                    }
                    Text(
                        "ScanMate follows your device theme by default. Choose Light or Dark here only when you want to override it.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = themeMode == mode,
                                onClick = { coroutineScope.launch { repository.saveThemeMode(mode) } },
                                label = { Text(mode.label) }
                            )
                        }
                    }
                    Text(themeMode.description, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                }
            }

            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary)
                        Text("Default workspace", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp))
                    }
                    Text("New organization actions default to this workspace. Existing files are not moved unless you choose bulk move.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = defaultWorkspaceInput,
                            onValueChange = { defaultWorkspaceInput = it },
                            label = { Text("Workspace") },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.weight(1f)
                        )
                        Button(onClick = { coroutineScope.launch { repository.saveDefaultWorkspace(defaultWorkspaceInput) } }) { Text("Save") }
                    }
                }
            }

            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Key, null, tint = MaterialTheme.colorScheme.primary)
                        Text("Gemini AI", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp))
                    }
                    Text(
                        "Add your own Gemini API key to enable optional online AI tools. Scanner, OCR history, PDF export, QR tools and files remain offline-safe without a key.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = currentKeyInput,
                        onValueChange = {
                            currentKeyInput = it
                            isSaved = false
                            testMessage = ""
                        },
                        label = { Text("Gemini API Key") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        shape = RoundedCornerShape(16.dp)
                    )

                    Text("Gemini Model", fontWeight = FontWeight.SemiBold)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { modelMenuExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                                Text(selectedModel.displayName, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(selectedModel.modelId, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                        }
                        DropdownMenu(expanded = modelMenuExpanded, onDismissRequest = { modelMenuExpanded = false }) {
                            GeminiModels.options.forEach { model ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(model.displayName + if (model.isPreview) " · Preview" else "")
                                            Text(model.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    },
                                    onClick = {
                                        modelMenuExpanded = false
                                        testMessage = ""
                                        coroutineScope.launch { repository.saveGeminiModel(model.modelId) }
                                    }
                                )
                            }
                        }
                    }
                    Text(
                        "Default: Gemini 3.5 Flash. If a selected model is unavailable for your key, the app will show a clear model warning instead of crashing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        OutlinedButton(onClick = {
                            coroutineScope.launch {
                                repository.clearApiKey()
                                currentKeyInput = ""
                                isSaved = false
                                testMessage = ""
                            }
                        }) { Text("Clear") }
                        Button(onClick = {
                            coroutineScope.launch {
                                repository.saveApiKey(currentKeyInput.trim())
                                isSaved = true
                                testMessage = ""
                            }
                        }) { Text(if (isSaved) "Saved ✓" else "Save Key") }
                    }

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                val cleanKey = currentKeyInput.trim()
                                when {
                                    cleanKey.isBlank() -> {
                                        testIsError = true
                                        testMessage = "Add Gemini API key in Settings to use AI features."
                                    }
                                    !isOnline -> {
                                        testIsError = true
                                        testMessage = "AI needs internet. Offline tools still work."
                                    }
                                    else -> {
                                        isTestingApi = true
                                        testMessage = ""
                                        val result = GeminiHelper(cleanKey).testConnection(selectedModelId)
                                        testIsError = !result.isSuccess
                                        testMessage = if (result.isSuccess) "Gemini test succeeded for ${selectedModel.displayName}." else result.text
                                        isTestingApi = false
                                    }
                                }
                            }
                        },
                        enabled = !isTestingApi,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        if (isTestingApi) {
                            CircularProgressIndicator(modifier = Modifier.padding(end = 10.dp), strokeWidth = 2.dp)
                            Text("Testing API...")
                        } else {
                            Text("Test API")
                        }
                    }

                    if (testMessage.isNotBlank()) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (testIsError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(testMessage, modifier = Modifier.padding(12.dp), color = if (testIsError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
            }

            SettingsInfoCard(
                icon = Icons.Default.PrivacyTip,
                title = "Privacy & offline-first",
                body = "Files stay inside app-managed storage. AI is optional and only sends prompts when you press Generate/Test with your own key. Offline document intelligence works locally for summaries, cleanup, title suggestions and basic receipt/invoice clues. Sensitive OCR text can also be saved into the Android Keystore-backed local vault."
            )
            SettingsInfoCard(
                icon = Icons.Default.Info,
                title = "Export folder info",
                body = "Exported PDFs, DOCX files, QR images, OCR text, signatures and ZIP backups are saved with safe unique names and shared through Android-safe file access."
            )
            SettingsInfoCard(
                icon = Icons.Default.Info,
                title = "About ScanMate AI Pro",
                body = "Version ${BuildConfig.VERSION_NAME}. Offline-first Android scanner built with Jetpack Compose, Room, CameraX, ML Kit OCR/barcode, PDF export, ZIP backups and optional Gemini AI."
            )

            OutlinedButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://ai.google.dev/"))
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Open Gemini API docs") }
        }
    }
}

@Composable
private fun StatusCard(isOnline: Boolean) {
    Card(
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (isOnline) Icons.Default.Wifi else Icons.Default.CloudOff, null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(if (isOnline) "Online" else "Offline safe", fontWeight = FontWeight.Bold)
                Text(
                    if (isOnline) "AI can work after key + model setup." else "Core scanner, QR, files and exports still work.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SettingsInfoCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(title, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
