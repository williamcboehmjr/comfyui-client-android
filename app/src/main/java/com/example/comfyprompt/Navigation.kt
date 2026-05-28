package com.example.comfyprompt

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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

@Composable
fun MainNavigation(viewModel: MainViewModel = viewModel()) {
    val backStack = rememberNavBackStack(Prompt as NavKey)
    val context = LocalContext.current

    val settings by viewModel.settings.collectAsState()
    val progressInfo by viewModel.progressInfo.collectAsState()
    val prompt by viewModel.currentPrompt.collectAsState()

    // Monitor active generation flow to auto-navigate
    LaunchedEffect(progressInfo.state) {
        when (progressInfo.state) {
            GenerationState.Completed -> {
                val finalImage = progressInfo.finalImage
                if (finalImage != null) {
                    val seed = when (settings.seedMode) {
                        com.example.comfyprompt.data.SeedMode.Fixed -> settings.fixedSeedValue
                        com.example.comfyprompt.data.SeedMode.Custom -> settings.customSeedValue
                        else -> settings.lastUsedSeedValue
                    }
                    // Only add if not already there to avoid duplicates
                    if (backStack.lastOrNull() !is Result) {
                        backStack.add(Result(finalImage, seed))
                    }
                }
            }
            GenerationState.Failed -> {
                Toast.makeText(context, progressInfo.statusText, Toast.LENGTH_LONG).show()
                backStack.removeLastOrNull() // Go back from Progress
                viewModel.resetState()
            }
            GenerationState.Cancelled -> {
                backStack.removeLastOrNull() // Go back from Progress
                viewModel.resetState()
            }
            else -> {}
        }
    }

    NavDisplay(
        backStack = backStack,
        onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<Prompt> {
                PromptScreen(
                    prompt = prompt,
                    onPromptChange = { viewModel.updatePrompt(it) },
                    settings = settings,
                    onGenerateClick = { promptText ->
                        viewModel.generateImage(promptText)
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
                    }
                )
            }
            entry<Progress> {
                ProgressScreen(
                    progressInfo = progressInfo,
                    prompt = prompt,
                    onStopClick = { viewModel.stopGeneration() },
                    onSaveClick = { imageUrl -> viewModel.saveImageToDownloads(imageUrl, settings.outputFormat) },
                    onShareClick = { imageUrl -> viewModel.shareImage(imageUrl) }
                )
            }
            entry<Result> { key ->
                ResultScreen(
                    finalImageUrl = key.imageUrl,
                    seed = key.seed,
                    onSaveClick = { viewModel.saveImageToDownloads(key.imageUrl, settings.outputFormat) },
                    onShareClick = { viewModel.shareImage(key.imageUrl) },
                    onReRunClick = {
                        viewModel.resetState()
                        // Clear up to Prompt
                        while (backStack.size > 1) {
                            backStack.removeLastOrNull()
                        }
                    }
                )
            }
            entry<Settings> {
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    viewModel.refreshSavedWorkflows()
                }
                val savedWorkflows by viewModel.savedWorkflows.collectAsState()
                SettingsScreen(
                    settings = settings,
                    savedWorkflows = savedWorkflows,
                    onSaveClick = { updatedSettings ->
                        viewModel.updateSettings(updatedSettings)
                        Toast.makeText(context, "Settings Saved!", Toast.LENGTH_SHORT).show()
                        backStack.removeLastOrNull()
                    },
                    onBackClick = { backStack.removeLastOrNull() },
                    onDownloadWorkflowClick = { viewModel.downloadWorkflow() }
                )
            }
            entry<Gallery> {
                val galleryItems by viewModel.galleryItems.collectAsState()
                GalleryScreen(
                    items = galleryItems,
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
                    onDownloadClick = { url -> viewModel.saveImageToDownloads(url, settings.outputFormat) }
                )
            }
        }
    )
}
