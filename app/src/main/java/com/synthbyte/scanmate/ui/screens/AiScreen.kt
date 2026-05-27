package com.synthbyte.scanmate.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.synthbyte.scanmate.data.SettingsRepository
import com.synthbyte.scanmate.domain.GeminiHelper
import com.synthbyte.scanmate.domain.GeminiModels
import com.synthbyte.scanmate.utils.AiWorkflow
import com.synthbyte.scanmate.utils.DocumentIntelligence
import com.synthbyte.scanmate.utils.FileUtils
import com.synthbyte.scanmate.utils.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { SettingsRepository(context) }
    val apiKey by repository.geminiApiKeyFlow.collectAsState(initial = "")
    val selectedModelId by repository.geminiModelIdFlow.collectAsState(initial = GeminiModels.DEFAULT_MODEL_ID)
    val selectedModel = GeminiModels.optionFor(selectedModelId)
    var isOnline by remember { mutableStateOf(false) }
    val canUseOnlineAi = isOnline && !apiKey.isNullOrBlank()

    var prompt by remember { mutableStateOf("") }
    var response by remember { mutableStateOf("") }
    var responseTitle by remember { mutableStateOf("AI Response") }
    var responseIsError by remember { mutableStateOf(false) }
    var isOfflineFallback by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    LaunchedEffect(Unit) {
        isOnline = withContext(Dispatchers.IO) { NetworkUtils.isOnline(context) }
    }

    fun showLocalError(message: String) {
        responseTitle = "Needs attention"
        response = message
        responseIsError = true
        isOfflineFallback = false
    }

    fun publishOffline(workflow: AiWorkflow, sourceText: String) {
        responseTitle = "Offline ${workflow.label}"
        response = DocumentIntelligence.buildOfflineResponse(workflow, sourceText)
        responseIsError = response.startsWith("Paste OCR", ignoreCase = true)
        isOfflineFallback = !responseIsError
    }

    fun runSmartWorkflow(workflow: AiWorkflow, sourceText: String) {
        val finalText = sourceText.trim()
        if (finalText.isBlank()) {
            showLocalError("Paste OCR text or type a prompt first.")
            return
        }
        if (!canUseOnlineAi) {
            publishOffline(workflow, finalText)
            return
        }
        coroutineScope.launch {
            isLoading = true
            response = ""
            responseTitle = workflow.label
            isOfflineFallback = false
            val result = GeminiHelper(apiKey.orEmpty()).generateContent(DocumentIntelligence.buildPrompt(workflow, finalText), selectedModelId)
            response = result.text
            responseIsError = !result.isSuccess
            isLoading = false
        }
    }

    fun runFreePrompt() {
        val finalPrompt = prompt.trim()
        if (finalPrompt.isBlank()) {
            showLocalError("Paste OCR text or type a prompt first.")
            return
        }
        if (!canUseOnlineAi) {
            publishOffline(AiWorkflow.SUMMARY, finalPrompt)
            return
        }
        coroutineScope.launch {
            isLoading = true
            response = ""
            responseTitle = "AI Response"
            isOfflineFallback = false
            val result = GeminiHelper(apiKey.orEmpty()).generateContent(finalPrompt, selectedModelId)
            response = result.text
            responseIsError = !result.isSuccess
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Workspace") },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                AiHeroCard(
                    canUseOnlineAi = canUseOnlineAi,
                    isOnline = isOnline,
                    selectedModel = selectedModel.displayName
                )
            }

            item {
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("Paste OCR text, receipt, invoice, homework question, or document notes") },
                    supportingText = {
                        Text(
                            if (canUseOnlineAi) "Online AI uses ${selectedModel.displayName}. Offline fallback still protects the workflow."
                            else "Offline intelligence is active. Add a Gemini key in Settings for deeper online AI."
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(190.dp),
                    maxLines = 8,
                    enabled = !isLoading,
                    shape = RoundedCornerShape(22.dp)
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { runFreePrompt() }, modifier = Modifier.weight(1f), enabled = !isLoading) {
                        Text(if (isLoading) "Working..." else "Generate")
                    }
                    OutlinedButton(onClick = {
                        prompt = DocumentIntelligence.cleanOcrText(prompt)
                        Toast.makeText(context, "OCR text cleaned locally", Toast.LENGTH_SHORT).show()
                    }, modifier = Modifier.weight(1f), enabled = !isLoading) {
                        Text("Clean Text")
                    }
                }
            }

            if (isLoading) {
                item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
            }

            item {
                Text("Smart Workflows", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            items(DocumentIntelligence.workflows) { workflow ->
                AiWorkflowCard(
                    workflow = workflow,
                    onlineReady = canUseOnlineAi,
                    enabled = !isLoading,
                    onClick = { runSmartWorkflow(workflow, prompt) }
                )
            }

            item {
                AnimatedVisibility(response.isNotBlank()) {
                    ResponseCard(
                        title = responseTitle,
                        response = response,
                        isError = responseIsError,
                        isOfflineFallback = isOfflineFallback,
                        onCopy = {
                            clipboardManager.setPrimaryClip(ClipData.newPlainText(responseTitle, response))
                            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                        },
                        onShare = { FileUtils.shareText(context, response) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AiHeroCard(canUseOnlineAi: Boolean, isOnline: Boolean, selectedModel: String) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.72f),
                            Color(0xFF7C4DFF)
                        )
                    )
                )
                .padding(20.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier.size(58.dp).background(Color.White.copy(alpha = 0.20f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(if (canUseOnlineAi) Icons.Default.AutoAwesome else Icons.Default.CloudOff, null, tint = Color.White, modifier = Modifier.size(30.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("AI-first document workspace", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                Text(
                    if (canUseOnlineAi) "Online AI ready · $selectedModel"
                    else if (isOnline) "Offline fallback active · add key for Gemini"
                    else "Offline fallback active · scanner remains usable",
                    color = Color.White.copy(alpha = 0.86f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun AiWorkflowCard(workflow: AiWorkflow, onlineReady: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier.size(44.dp).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Description, null, tint = MaterialTheme.colorScheme.primary)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(workflow.label, fontWeight = FontWeight.Bold)
                Text(workflow.description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            AssistChip(
                onClick = {},
                leadingIcon = { Icon(if (onlineReady) Icons.Default.CheckCircle else Icons.Default.CloudOff, null, modifier = Modifier.size(16.dp)) },
                label = { Text(if (onlineReady) "AI" else "Offline") }
            )
        }
    }
}

@Composable
private fun ResponseCard(
    title: String,
    response: String,
    isError: Boolean,
    isOfflineFallback: Boolean,
    onCopy: () -> Unit,
    onShare: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, if (isError) MaterialTheme.colorScheme.error.copy(alpha = 0.28f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (isOfflineFallback) Text("Generated locally without sending data online", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                Icon(if (isOfflineFallback) Icons.Default.CloudOff else Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
            }
            Text(response, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onCopy) {
                    Icon(Icons.Default.ContentCopy, null)
                    Text("Copy")
                }
                TextButton(onClick = onShare) {
                    Icon(Icons.Default.Share, null)
                    Text("Share")
                }
            }
        }
    }
}
