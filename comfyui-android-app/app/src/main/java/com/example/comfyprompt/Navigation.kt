package com.example.comfyprompt

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.comfyprompt.data.GenerationState
import com.example.comfyprompt.ui.MainViewModel
import com.example.comfyprompt.ui.screens.ProgressScreen
import com.example.comfyprompt.ui.screens.PromptScreen
import com.example.comfyprompt.ui.screens.ResultScreen
import com.example.comfyprompt.ui.screens.SettingsScreen
import com.example.comfyprompt.ui.screens.GalleryScreen
import com.example.comfyprompt.ui.screens.LogsScreen
import com.example.comfyprompt.ui.screens.ServerWakeScreen

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MainNavigation(viewModel: MainViewModel = viewModel()) {
    val backStack = rememberNavBackStack(Prompt as NavKey)
    val context = LocalContext.current

    val settings by viewModel.settings.collectAsState()
    val progressInfo by viewModel.progressInfo.collectAsState()
    val prompt by viewModel.currentPrompt.collectAsState()
    val cooldownSeconds by viewModel.generateCooldownSeconds.collectAsState()
    val savedWorkflows by viewModel.savedWorkflows.collectAsState()
    val workflowGroups by viewModel.workflowGroups.collectAsState()
    val bypassedGroups by viewModel.bypassedGroups.collectAsState()

    val queueJobs by viewModel.queueList.collectAsState()
    val activeJobId by viewModel.activeJobId.collectAsState()
    var showBottomSheet by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState()

    val wakeState by viewModel.serverWakeState.collectAsState()

    // Monitor wake state to navigate to/from ServerWakeScreen
    LaunchedEffect(wakeState) {
        val currentScreen = backStack.lastOrNull()
        when (wakeState) {
            is com.example.comfyprompt.data.ServerWakeState.Waking,
            is com.example.comfyprompt.data.ServerWakeState.Polling,
            is com.example.comfyprompt.data.ServerWakeState.HostUnreachable -> {
                if (currentScreen !is ServerWake) {
                    backStack.add(ServerWake)
                }
            }
            is com.example.comfyprompt.data.ServerWakeState.Success -> {
                if (currentScreen is ServerWake) {
                    backStack.removeLastOrNull()
                    viewModel.resetWakeState()
                }
            }
            is com.example.comfyprompt.data.ServerWakeState.Timeout -> {
                // Keep it on screen so they can read the error and click OK/DISMISS
            }
            is com.example.comfyprompt.data.ServerWakeState.Idle -> {
                if (currentScreen is ServerWake) {
                    backStack.removeLastOrNull()
                }
            }
        }
    }

    // Monitor active generation flow to auto-navigate
    LaunchedEffect(progressInfo.state) {
        val currentScreen = backStack.lastOrNull()
        when (progressInfo.state) {
            GenerationState.Completed -> {
                val finalImage = progressInfo.finalImage
                if (finalImage != null) {
                    val seed = when (settings.seedMode) {
                        com.example.comfyprompt.data.SeedMode.Fixed -> settings.fixedSeedValue
                        com.example.comfyprompt.data.SeedMode.Custom -> settings.customSeedValue
                        else -> settings.lastUsedSeedValue
                    }
                    // Only auto-navigate to Result if the user was actively watching the Progress screen
                    if (currentScreen is Progress && backStack.lastOrNull() !is Result) {
                        backStack.removeLastOrNull() // Remove Progress screen
                        backStack.add(Result(finalImage, seed, progressInfo.previews))
                    }
                }
            }
            GenerationState.Failed -> {
                if (currentScreen is Progress) {
                    val isTriggerCmdEnabled = settings.triggerCmdEnabled && 
                            settings.hostType == com.example.comfyprompt.data.HostType.LOCAL &&
                            viewModel.isConnectionError(progressInfo.statusText)
                    
                    if (!isTriggerCmdEnabled) {
                        Toast.makeText(context, progressInfo.statusText, Toast.LENGTH_LONG).show()
                    }
                    backStack.removeLastOrNull() // Go back from Progress
                    viewModel.resetState()
                }
            }
            GenerationState.Cancelled -> {
                if (currentScreen is Progress) {
                    backStack.removeLastOrNull() // Go back from Progress
                    viewModel.resetState()
                }
            }
            else -> {}
        }
    }

    androidx.compose.foundation.layout.Box(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
        NavDisplay(
            backStack = backStack,
            onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
            entryProvider = entryProvider {
                entry<Prompt> {
                    PromptScreen(
                        prompt = prompt,
                        onPromptChange = { viewModel.updatePrompt(it) },
                        settings = settings,
                        onGenerateClick = { promptText, imageUri ->
                            viewModel.generateImage(promptText, imageUri)
                            Toast.makeText(context, "Prompt added to queue", Toast.LENGTH_SHORT).show()
                            backStack.add(Progress)
                        },
                        onSettingsClick = { backStack.add(Settings) },
                        onGalleryClick = { backStack.add(Gallery) },
                        onEnhancerToggle = { isEnabled ->
                            val updated = settings.copy(enableEnhancer = isEnabled)
                            viewModel.updateSettings(updated)
                        },
                        onSeedModeChange = { mode, customVal ->
                            val updated = settings.copy(
                                seedMode = mode,
                                customSeedValue = customVal
                            )
                            viewModel.updateSettings(updated)
                        },
                        onMegapixelChange = { mp ->
                            val updated = settings.copy(megapixel = mp)
                            viewModel.updateSettings(updated)
                        },
                        onAspectRatioChange = { ar ->
                            val updated = settings.copy(aspectRatio = ar)
                            viewModel.updateSettings(updated)
                        },
                        onStylePresetChange = { preset ->
                            val updated = settings.copy(selectedStylePreset = preset)
                            viewModel.updateSettings(updated)
                        },
                        savedWorkflows = savedWorkflows,
                        onWorkflowChange = { workflow ->
                            val updated = settings.copy(workflowToUse = workflow)
                            viewModel.updateSettings(updated)
                        },
                        workflowGroups = workflowGroups,
                        bypassedGroups = bypassedGroups,
                        onToggleGroupBypass = { viewModel.toggleGroupBypass(it) },
                        onToggleAllGroups = { enableAll -> viewModel.toggleAllGroups(enableAll) },
                        cooldownSeconds = cooldownSeconds
                    )
                }
                entry<Progress> {
                    val activeJobPrompt = queueJobs.firstOrNull { it.id == activeJobId }?.prompt
                    ProgressScreen(
                        progressInfo = progressInfo,
                        prompt = activeJobPrompt ?: prompt,
                        onStopClick = { viewModel.stopGeneration() },
                        onSaveClick = { imageUrl -> viewModel.saveImageToDownloads(imageUrl, settings.outputFormat) },
                        onShareClick = { imageUrl -> viewModel.shareImage(imageUrl) }
                    )
                }
                entry<Result> { key ->
                    ResultScreen(
                        finalImageUrl = key.imageUrl,
                        seed = key.seed,
                        previews = key.previews,
                        settings = settings,
                        onSaveClick = { url -> viewModel.saveImageToDownloads(url, settings.outputFormat) },
                        onShareClick = { url -> viewModel.shareImage(url) },
                        onReRunClick = {
                            viewModel.resetState()
                            // Clear up to Prompt
                            while (backStack.size > 1) {
                                backStack.removeLastOrNull()
                            }
                        },
                        onUsePromptClick = { refinedPrompt ->
                            viewModel.generateImage(refinedPrompt)
                            viewModel.resetState()
                            while (backStack.size > 1) {
                                backStack.removeLastOrNull()
                            }
                            backStack.add(Progress)
                        },
                        viewModel = viewModel
                    )
                }
                entry<Settings> {
                    androidx.compose.runtime.LaunchedEffect(Unit) {
                        viewModel.refreshSavedWorkflows()
                    }
                    val savedWorkflows by viewModel.savedWorkflows.collectAsState()
                    val importState by viewModel.importState.collectAsState()
                    val localModels by viewModel.localModels.collectAsState()
                    SettingsScreen(
                        settings = settings,
                        savedWorkflows = savedWorkflows,
                        importState = importState,
                        localModels = localModels,
                        onFetchLocalModelsClick = { url -> viewModel.fetchLocalModels(url) },
                        onImportWorkflowClick = { ctx, uri -> viewModel.importWorkflow(ctx, uri) },
                        onClearImportState = { viewModel.clearImportState() },
                        onSaveClick = { updatedSettings ->
                            viewModel.updateSettings(updatedSettings)
                            Toast.makeText(context, "Settings Saved!", Toast.LENGTH_SHORT).show()
                            backStack.removeLastOrNull()
                        },
                        onBackClick = { backStack.removeLastOrNull() },
                        onDownloadWorkflowClick = { viewModel.downloadWorkflow() },
                        onViewLogsClick = { backStack.add(Logs) }
                    )
                }
                entry<Logs> {
                    LogsScreen(
                        onBackClick = { backStack.removeLastOrNull() }
                    )
                }
                entry<Gallery> {
                    val galleryItems by viewModel.galleryItems.collectAsState()
                    GalleryScreen(
                        items = galleryItems,
                        enableEnhancer = settings.enableEnhancer,
                        onBackClick = { backStack.removeLastOrNull() },
                        onReRunClick = { promptText ->
                            viewModel.updatePrompt(promptText)
                            // Clear up to Prompt (Home)
                            while (backStack.size > 1) {
                                backStack.removeLastOrNull()
                            }
                        },
                        onShareClick = { url -> viewModel.shareImage(url) },
                        onDeleteClick = { id -> viewModel.deleteGalleryItem(id) },
                        onDownloadClick = { url -> viewModel.saveImageToDownloads(url, settings.outputFormat) },
                        onRefinePromptClick = { imageUrl, seed ->
                            backStack.add(Result(imageUrl, seed))
                        }
                    )
                }
                entry<ServerWake> {
                    ServerWakeScreen(
                        wakeState = wakeState,
                        settings = settings,
                        onCancelClick = {
                            viewModel.cancelWakeSequence()
                        }
                    )
                }
            }
        )

        val currentScreen = backStack.lastOrNull()
        val showFab = queueJobs.isNotEmpty() && currentScreen != null

        if (showFab) {
            com.example.comfyprompt.ui.screens.QueueFAB(
                queueSize = queueJobs.size,
                onClick = { showBottomSheet = true },
                modifier = androidx.compose.ui.Modifier
                    .align(androidx.compose.ui.Alignment.BottomEnd)
                    .padding(16.dp)
            )
        }

        if (showBottomSheet) {
            androidx.compose.material3.ModalBottomSheet(
                onDismissRequest = { showBottomSheet = false },
                sheetState = sheetState,
                containerColor = com.example.comfyprompt.theme.DarkGray,
                scrimColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f)
            ) {
                com.example.comfyprompt.ui.screens.QueueBottomSheetContent(
                    queueJobs = queueJobs,
                    activeJobId = activeJobId,
                    onCancelJob = { jobId -> viewModel.cancelJob(jobId) },
                    onClearPending = { viewModel.clearPendingQueue() },
                    onStopAll = { viewModel.stopAllJobs() },
                    onJobClick = { jobId ->
                        showBottomSheet = false
                        if (backStack.lastOrNull() !is Progress) {
                            backStack.add(Progress)
                        }
                    },
                    onDismiss = { showBottomSheet = false }
                )
            }
        }
    }
}

