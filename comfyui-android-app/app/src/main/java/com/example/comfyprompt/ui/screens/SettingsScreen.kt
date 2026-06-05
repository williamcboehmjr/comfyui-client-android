package com.example.comfyprompt.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.comfyprompt.data.AppSettings
import com.example.comfyprompt.data.HostType
import com.example.comfyprompt.network.UrlValidator
import com.example.comfyprompt.network.ValidationResult
import com.example.comfyprompt.theme.AccentGray
import com.example.comfyprompt.theme.AccentRed
import com.example.comfyprompt.theme.SuccessGreen
import com.example.comfyprompt.ui.ImportState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    savedWorkflows: List<String>,
    importState: ImportState,
    onImportWorkflowClick: (Context, Uri) -> Unit,
    onClearImportState: () -> Unit,
    onSaveClick: (AppSettings) -> Unit,
    onBackClick: () -> Unit,
    onDownloadWorkflowClick: () -> Unit,
    localModels: List<String> = emptyList(),
    onFetchLocalModelsClick: (String) -> Unit = {},
    onViewLogsClick: () -> Unit = {}
) {
    val context = LocalContext.current
    var hasImportedLocalWorkflow by remember(importState) {
        mutableStateOf(context.getFileStreamPath("imported_workflow.json").exists())
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            onImportWorkflowClick(context, uri)
        }
    }

    var serverUrl by remember { mutableStateOf(settings.serverUrl) }
    var geminiKey by remember { mutableStateOf(settings.geminiApiKey) }
    var geminiModel by remember { mutableStateOf(settings.geminiModel) }
    var chatgptKey by remember { mutableStateOf(settings.chatgptApiKey) }
    var chatgptModel by remember { mutableStateOf(settings.chatgptModel) }
    var claudeKey by remember { mutableStateOf(settings.claudeApiKey) }
    var claudeModel by remember { mutableStateOf(settings.claudeModel) }
    var grokKey by remember { mutableStateOf(settings.grokApiKey) }
    var grokModel by remember { mutableStateOf(settings.grokModel) }
    var localLlmBaseUrl by remember { mutableStateOf(settings.localLlmBaseUrl) }
    var localLlmSelectedModel by remember { mutableStateOf(settings.localLlmSelectedModel) }
    var apiProvider by remember { mutableStateOf(settings.apiProvider) }
    var outputFormat by remember { mutableStateOf(settings.outputFormat) }
    var showApiKey by remember { mutableStateOf(false) }
    var workflowToUse by remember { mutableStateOf(settings.workflowToUse) }

    // Host connection states
    var selectedHostType by remember { mutableStateOf(settings.hostType) }
    var localIpAddress by remember { mutableStateOf(settings.localIpAddress) }
    var comfyDeployApiKey by remember { mutableStateOf(settings.comfyDeployApiKey) }
    var comfyDeployId by remember { mutableStateOf(settings.comfyDeployId) }
    var runpodApiKey by remember { mutableStateOf(settings.runpodApiKey) }
    var runpodEndpointId by remember { mutableStateOf(settings.runpodEndpointId) }
    var falAiApiKey by remember { mutableStateOf(settings.falAiApiKey) }
    var falAiEndpointSlug by remember { mutableStateOf(settings.falAiEndpointSlug) }

    var hostTypeDropdownExpanded by remember { mutableStateOf(false) }
    var showComfyDeployApiKey by remember { mutableStateOf(false) }
    var showRunpodApiKey by remember { mutableStateOf(false) }
    var showFalAiApiKey by remember { mutableStateOf(false) }

    // TRIGGERcmd configuration states
    var triggerCmdEnabled by remember { mutableStateOf(settings.triggerCmdEnabled) }
    var triggerCmdToken by remember { mutableStateOf(settings.triggerCmdToken) }
    var triggerCmdName by remember { mutableStateOf(settings.triggerCmdName) }
    var triggerCmdComputer by remember { mutableStateOf(settings.triggerCmdComputer) }
    var showTriggerCmdToken by remember { mutableStateOf(false) }

    val isSaveEnabled = remember(selectedHostType, localIpAddress) {
        if (selectedHostType == HostType.LOCAL) {
            UrlValidator.validateUrl(localIpAddress) !is ValidationResult.Error
        } else {
            true
        }
    }

    val providers = listOf("Gemini", "ChatGPT", "Claude", "Grok", "Local / Custom")
    val formats = listOf("PNG", "JPEG", "WEBP")

    val geminiModels = listOf(
        "gemini-1.5-flash",
        "gemini-1.5-pro",
        "gemini-2.0-flash",
        "gemini-2.5-flash",
        "gemini-3.5-flash",
        "gemini-flash-lite-3.1"
    )
    val chatgptModels = listOf(
        "gpt-4o",
        "gpt-4o-mini",
        "gpt-3.5-turbo",
        "o1-mini"
    )
    val claudeModels = listOf(
        "claude-3-5-sonnet-latest",
        "claude-3-5-haiku-latest",
        "claude-3-opus-latest"
    )
    val grokModels = listOf(
        "grok-2-1212",
        "grok-2-vision-1212"
    )

    var providerDropdownExpanded by remember { mutableStateOf(false) }
    var modelDropdownExpanded by remember { mutableStateOf(false) }
    var formatDropdownExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Host Connection Configuration Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "HOST CONNECTION CONFIGURATION",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = AccentGray
                    )

                    // Host Type Selector
                    Column {
                        Text("Host Connection Type", style = MaterialTheme.typography.bodySmall, color = AccentGray)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, AccentGray, RoundedCornerShape(8.dp))
                                .clickable { hostTypeDropdownExpanded = true }
                                .padding(16.dp)
                        ) {
                            val hostTypeLabel = when (selectedHostType) {
                                HostType.LOCAL -> "Local"
                                HostType.COMFY_DEPLOY -> "ComfyDeploy"
                                HostType.RUNPOD -> "RunPod Serverless"
                                HostType.FAL_AI -> "Fal.ai"
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(hostTypeLabel, color = Color.White)
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = Color.White
                                )
                            }
                            DropdownMenu(
                                expanded = hostTypeDropdownExpanded,
                                onDismissRequest = { hostTypeDropdownExpanded = false },
                                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                HostType.entries.forEach { type ->
                                    val label = when (type) {
                                        HostType.LOCAL -> "Local"
                                        HostType.COMFY_DEPLOY -> "ComfyDeploy"
                                        HostType.RUNPOD -> "RunPod Serverless"
                                        HostType.FAL_AI -> "Fal.ai"
                                    }
                                    DropdownMenuItem(
                                        text = { Text(label, color = Color.White) },
                                        onClick = {
                                            selectedHostType = type
                                            hostTypeDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Animated visibility layout for active HostType
                    AnimatedVisibility(
                        visible = selectedHostType == HostType.LOCAL,
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "Local Server Address",
                                style = MaterialTheme.typography.bodySmall,
                                color = AccentGray
                            )
                            val validation = remember(localIpAddress) {
                                UrlValidator.validateUrl(localIpAddress)
                            }
                            OutlinedTextField(
                                value = localIpAddress,
                                onValueChange = { localIpAddress = it },
                                modifier = Modifier.fillMaxWidth(),
                                isError = validation is ValidationResult.Error,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.White,
                                    unfocusedBorderColor = AccentGray,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    errorBorderColor = AccentRed
                                ),
                                shape = RoundedCornerShape(8.dp),
                                placeholder = { Text("http://10.0.2.2:8188", color = AccentGray) }
                            )
                            when (validation) {
                                is ValidationResult.Public -> {
                                    Text(
                                        text = "Ensure this is YOUR server - API keys at risk on untrusted endpoints",
                                        color = AccentRed,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                                is ValidationResult.Error -> {
                                    Text(
                                        text = validation.message,
                                        color = AccentRed,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                                else -> {}
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = selectedHostType == HostType.COMFY_DEPLOY,
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    "ComfyDeploy API Key",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AccentGray
                                )
                                OutlinedTextField(
                                    value = comfyDeployApiKey,
                                    onValueChange = { comfyDeployApiKey = it },
                                    visualTransformation = if (showComfyDeployApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color.White,
                                        unfocusedBorderColor = AccentGray,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    trailingIcon = {
                                        Text(
                                            text = if (showComfyDeployApiKey) "HIDE" else "SHOW",
                                            modifier = Modifier
                                                .padding(end = 12.dp)
                                                .clickable { showComfyDeployApiKey = !showComfyDeployApiKey },
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                )
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    "ComfyDeploy Deployment ID",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AccentGray
                                )
                                OutlinedTextField(
                                    value = comfyDeployId,
                                    onValueChange = { comfyDeployId = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color.White,
                                        unfocusedBorderColor = AccentGray,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = selectedHostType == HostType.RUNPOD,
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    "RunPod API Key",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AccentGray
                                )
                                OutlinedTextField(
                                    value = runpodApiKey,
                                    onValueChange = { runpodApiKey = it },
                                    visualTransformation = if (showRunpodApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color.White,
                                        unfocusedBorderColor = AccentGray,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    trailingIcon = {
                                        Text(
                                            text = if (showRunpodApiKey) "HIDE" else "SHOW",
                                            modifier = Modifier
                                                .padding(end = 12.dp)
                                                .clickable { showRunpodApiKey = !showRunpodApiKey },
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                )
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    "RunPod Endpoint ID",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AccentGray
                                )
                                OutlinedTextField(
                                    value = runpodEndpointId,
                                    onValueChange = { runpodEndpointId = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color.White,
                                        unfocusedBorderColor = AccentGray,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = selectedHostType == HostType.FAL_AI,
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    "Fal.ai API Key",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AccentGray
                                )
                                OutlinedTextField(
                                    value = falAiApiKey,
                                    onValueChange = { falAiApiKey = it },
                                    visualTransformation = if (showFalAiApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color.White,
                                        unfocusedBorderColor = AccentGray,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    trailingIcon = {
                                        Text(
                                            text = if (showFalAiApiKey) "HIDE" else "SHOW",
                                            modifier = Modifier
                                                .padding(end = 12.dp)
                                                .clickable { showFalAiApiKey = !showFalAiApiKey },
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                )
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    "Fal.ai Endpoint Slug",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AccentGray
                                )
                                OutlinedTextField(
                                    value = falAiEndpointSlug,
                                    onValueChange = { falAiEndpointSlug = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color.White,
                                        unfocusedBorderColor = AccentGray,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Workflow to Use Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "WORKFLOW TO USE",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = AccentGray
                    )
                    
                    var workflowDropdownExpanded by remember { mutableStateOf(false) }
                    
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { workflowDropdownExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, AccentGray),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = when (workflowToUse) {
                                        "" -> "Built-in Ernie Workflow (Default)"
                                        "imported_workflow.json" -> "Imported Local Workflow (imported_workflow.json)"
                                        else -> workflowToUse
                                    },
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = Color.White
                                )
                            }
                        }
                        
                        DropdownMenu(
                            expanded = workflowDropdownExpanded,
                            onDismissRequest = { workflowDropdownExpanded = false },
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Built-in Ernie Workflow (Default)", color = Color.White) },
                                onClick = {
                                    workflowToUse = ""
                                    workflowDropdownExpanded = false
                                }
                            )
                            if (hasImportedLocalWorkflow) {
                                DropdownMenuItem(
                                    text = { Text("Imported Local Workflow (imported_workflow.json)", color = Color.White) },
                                    onClick = {
                                        workflowToUse = "imported_workflow.json"
                                        workflowDropdownExpanded = false
                                    }
                                )
                            }
                            savedWorkflows.forEach { wf ->
                                DropdownMenuItem(
                                    text = { Text(wf, color = Color.White) },
                                    onClick = {
                                        workflowToUse = wf
                                        workflowDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    OutlinedButton(
                        onClick = { launcher.launch("application/json") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.dp, Color.White),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            text = "IMPORT WORKFLOW.JSON",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    if (savedWorkflows.isEmpty()) {
                        Text(
                            text = "No saved workflows fetched. Make sure ComfyUI is online and workflows are saved under workflows/ folder.",
                            style = MaterialTheme.typography.bodySmall,
                            color = AccentGray
                        )
                    } else {
                        Text(
                            text = "Tip: Custom workflows must be saved in ComfyUI API format (Enable Dev Mode -> 'Save (API format)').",
                            style = MaterialTheme.typography.bodySmall,
                            color = AccentGray
                        )
                    }
                }
            }

            // ComfyUI Workflow Export Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "COMFYUI WORKFLOW EXPORT",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = AccentGray
                    )
                    Text(
                        "Download the cleaned ERNIE 1-stage workflow.json. Drag and drop it on ComfyUI to automatically install and link any required custom nodes.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = onDownloadWorkflowClick,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text("DOWNLOAD WORKFLOW.JSON", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            // Prompt Enhancer Settings Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "PROMPT EXPANDER CONFIGURATION",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = AccentGray
                    )

                    // Provider Dropdown
                    Column {
                        Text("Active AI Provider", style = MaterialTheme.typography.bodySmall, color = AccentGray)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, AccentGray, RoundedCornerShape(8.dp))
                                .clickable { providerDropdownExpanded = true }
                                .padding(16.dp)
                        ) {
                            Text(apiProvider, color = Color.White)
                            DropdownMenu(
                                expanded = providerDropdownExpanded,
                                onDismissRequest = { providerDropdownExpanded = false },
                                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                providers.forEach { prov ->
                                    DropdownMenuItem(
                                        text = { Text(prov, color = Color.White) },
                                        onClick = {
                                            apiProvider = prov
                                            providerDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.surface, thickness = 1.dp)

                    if (apiProvider == "Local / Custom") {
                        Column {
                            Text("Local LLM Base URL", style = MaterialTheme.typography.bodySmall, color = AccentGray)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = localLlmBaseUrl,
                                    onValueChange = { localLlmBaseUrl = it },
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color.White,
                                        unfocusedBorderColor = AccentGray,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                Button(
                                    onClick = { onFetchLocalModelsClick(localLlmBaseUrl) },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.Black),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Fetch Models", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    } else {
                        // Active API Key input field depending on the provider
                        val currentKey: String
                        val onKeyChange: (String) -> Unit
                        val keyLabel: String
                        when (apiProvider) {
                            "ChatGPT" -> {
                                currentKey = chatgptKey
                                onKeyChange = { chatgptKey = it }
                                keyLabel = "ChatGPT API Key (OpenAI)"
                            }
                            "Claude" -> {
                                currentKey = claudeKey
                                onKeyChange = { claudeKey = it }
                                keyLabel = "Claude API Key (Anthropic)"
                            }
                            "Grok" -> {
                                currentKey = grokKey
                                onKeyChange = { grokKey = it }
                                keyLabel = "Grok API Key (xAI)"
                            }
                            else -> {
                                currentKey = geminiKey
                                onKeyChange = { geminiKey = it }
                                keyLabel = "Gemini API Key (Google AI Studio)"
                            }
                        }
    
                        OutlinedTextField(
                            value = currentKey,
                            onValueChange = onKeyChange,
                            label = { Text(keyLabel, color = AccentGray) },
                            visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.White,
                                unfocusedBorderColor = AccentGray,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp),
                            trailingIcon = {
                                Text(
                                    text = if (showApiKey) "HIDE" else "SHOW",
                                    modifier = Modifier
                                        .padding(end = 12.dp)
                                        .clickable { showApiKey = !showApiKey },
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        )
                    }

                    // Active Model Dropdown depending on the provider
                    var currentModel: String = ""
                    var onModelChange: (String) -> Unit = {}
                    var modelsList: List<String> = emptyList()
                    when (apiProvider) {
                        "Local / Custom" -> {
                            currentModel = localLlmSelectedModel
                            onModelChange = { localLlmSelectedModel = it }
                            modelsList = localModels
                        }
                        "ChatGPT" -> {
                            currentModel = chatgptModel
                            onModelChange = { chatgptModel = it }
                            modelsList = chatgptModels
                        }
                        "Claude" -> {
                            currentModel = claudeModel
                            onModelChange = { claudeModel = it }
                            modelsList = claudeModels
                        }
                        "Grok" -> {
                            currentModel = grokModel
                            onModelChange = { grokModel = it }
                            modelsList = grokModels
                        }
                        else -> {
                            currentModel = geminiModel
                            onModelChange = { geminiModel = it }
                            modelsList = geminiModels
                        }
                    }

                    Column {
                        Text("Selected Model ($apiProvider)", style = MaterialTheme.typography.bodySmall, color = AccentGray)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, AccentGray, RoundedCornerShape(8.dp))
                                .clickable { modelDropdownExpanded = true }
                                .padding(16.dp)
                        ) {
                            Text(currentModel, color = Color.White)
                            DropdownMenu(
                                expanded = modelDropdownExpanded,
                                onDismissRequest = { modelDropdownExpanded = false },
                                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                modelsList.forEach { modelName ->
                                    DropdownMenuItem(
                                        text = { Text(modelName, color = Color.White) },
                                        onClick = {
                                            onModelChange(modelName)
                                            modelDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Export Image Format Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "EXPORT IMAGE FORMAT",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = AccentGray
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, AccentGray, RoundedCornerShape(8.dp))
                            .clickable { formatDropdownExpanded = true }
                            .padding(16.dp)
                    ) {
                        Text(outputFormat, color = Color.White)
                        DropdownMenu(
                            expanded = formatDropdownExpanded,
                            onDismissRequest = { formatDropdownExpanded = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            formats.forEach { fmt ->
                                DropdownMenuItem(
                                    text = { Text(fmt, color = Color.White) },
                                    onClick = {
                                        outputFormat = fmt
                                        formatDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // TRIGGERcmd Auto-Wake Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "AUTOMATIC LOCAL HOST WAKE (TRIGGERcmd)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = AccentGray
                            )
                            Text(
                                "Auto-start your ComfyUI server if offline using a TRIGGERcmd task.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = triggerCmdEnabled,
                            onCheckedChange = { triggerCmdEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = SuccessGreen
                            )
                        )
                    }

                    AnimatedVisibility(visible = triggerCmdEnabled) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                "TRIGGERcmd Configuration",
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                "API Token",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = triggerCmdToken,
                                onValueChange = { triggerCmdToken = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.White,
                                    unfocusedBorderColor = AccentGray,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                visualTransformation = if (showTriggerCmdToken) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    Text(
                                        text = if (showTriggerCmdToken) "HIDE" else "SHOW",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .padding(end = 12.dp)
                                            .clickable { showTriggerCmdToken = !showTriggerCmdToken }
                                    )
                                }
                            )

                            Text(
                                "Trigger Name",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = triggerCmdName,
                                onValueChange = { triggerCmdName = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.White,
                                    unfocusedBorderColor = AccentGray,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )

                            Text(
                                "Computer Name (Optional)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = triggerCmdComputer,
                                onValueChange = { triggerCmdComputer = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Defaults to primary computer", color = AccentGray) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.White,
                                    unfocusedBorderColor = AccentGray,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )
                        }
                    }
                }
            }

            // Diagnostics & Logs Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "DIAGNOSTICS & LOGS",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = AccentGray
                    )
                    Text(
                        "View application logs for troubleshooting connection or generation errors.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = onViewLogsClick,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text("VIEW SYSTEM LOGS", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Button: Save Settings
            Button(
                onClick = {
                    val updatedSettings = settings.copy(
                        serverUrl = if (selectedHostType == HostType.LOCAL) localIpAddress else serverUrl,
                        hostType = selectedHostType,
                        localIpAddress = localIpAddress,
                        comfyDeployApiKey = comfyDeployApiKey,
                        comfyDeployId = comfyDeployId,
                        runpodApiKey = runpodApiKey,
                        runpodEndpointId = runpodEndpointId,
                        falAiApiKey = falAiApiKey,
                        falAiEndpointSlug = falAiEndpointSlug,
                        geminiApiKey = geminiKey,
                        geminiModel = geminiModel,
                        chatgptApiKey = chatgptKey,
                        chatgptModel = chatgptModel,
                        claudeApiKey = claudeKey,
                        claudeModel = claudeModel,
                        grokApiKey = grokKey,
                        grokModel = grokModel,
                        localLlmBaseUrl = localLlmBaseUrl,
                        localLlmSelectedModel = localLlmSelectedModel,
                        apiProvider = apiProvider,
                        outputFormat = outputFormat,
                        workflowToUse = workflowToUse,
                        triggerCmdEnabled = triggerCmdEnabled,
                        triggerCmdToken = triggerCmdToken,
                        triggerCmdName = triggerCmdName,
                        triggerCmdComputer = triggerCmdComputer
                    )
                    onSaveClick(updatedSettings)
                },
                enabled = isSaveEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSaveEnabled) Color.White else AccentGray,
                    contentColor = if (isSaveEnabled) Color.Black else Color.White
                ),
                shape = RoundedCornerShape(28.dp)
            ) {
                Text("SAVE SETTINGS", fontWeight = FontWeight.Bold, letterSpacing = 1.sp, fontSize = 16.sp)
            }
        }
    }

    if (importState is ImportState.Loading) {
        androidx.compose.ui.window.Dialog(onDismissRequest = {}) {
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    CircularProgressIndicator(color = Color.White)
                    Text(
                        text = "Attempting server conversion...",
                        color = Color.White,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    if (importState is ImportState.MissingExtension) {
        AlertDialog(
            onDismissRequest = onClearImportState,
            title = {
                Text(
                    text = "Unsupported Workflow Format",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "This workflow is in the visual UI format, not the required API format. The app cannot convert this locally. To use it, please choose one of the following:",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("•", color = Color.White)
                        Text(
                            text = "Option A: Open ComfyUI Manager on your PC, search for and install 'Workflow to API Converter Endpoint' (by SethRobinson). Once installed, this app will convert files automatically.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("•", color = Color.White)
                        Text(
                            text = "Option B: In ComfyUI, enable 'Dev mode Options', click 'Save (API format)', and import that new file instead.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onClearImportState) {
                    Text("OK", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }

    if (importState is ImportState.Error) {
        AlertDialog(
            onDismissRequest = onClearImportState,
            title = {
                Text(
                    text = "Import Error",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            text = {
                Text(
                    text = importState.message,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(onClick = onClearImportState) {
                    Text("OK", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}
