package com.example.comfyprompt.ui

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.comfyprompt.data.AppSettings
import com.example.comfyprompt.data.GalleryItem
import com.example.comfyprompt.data.GenerationState
import com.example.comfyprompt.data.ProgressInfo
import com.example.comfyprompt.data.SeedMode
import com.example.comfyprompt.data.SettingsManager
import com.example.comfyprompt.data.FormatType
import com.example.comfyprompt.data.ConversionResult
import com.example.comfyprompt.network.ComfyClient
import com.example.comfyprompt.network.GeminiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class ImportState {
    object Idle : ImportState()
    object Loading : ImportState()
    object MissingExtension : ImportState()
    data class Error(val message: String) : ImportState()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsManager = SettingsManager(application)
    
    private val _settings = MutableStateFlow(settingsManager.getSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState.asStateFlow()

    fun clearImportState() {
        _importState.value = ImportState.Idle
    }

    fun importWorkflow(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _importState.value = ImportState.Loading
            try {
                val jsonString = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.bufferedReader().use { it.readText() }
                } ?: throw Exception("Could not read file contents.")
                
                val format = FormatType.detect(jsonString)
                if (format == FormatType.API_READY) {
                    context.openFileOutput("imported_workflow.json", Context.MODE_PRIVATE).use { output ->
                        output.write(jsonString.toByteArray())
                    }
                    val updatedSettings = _settings.value.copy(workflowToUse = "imported_workflow.json")
                    updateSettings(updatedSettings)
                    _importState.value = ImportState.Idle
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Workflow imported successfully!", Toast.LENGTH_SHORT).show()
                    }
                } else if (format == FormatType.UI_STANDARD) {
                    val result = ComfyClient.convertWorkflowToApi(jsonString, _settings.value.serverUrl)
                    when (result) {
                        is ConversionResult.Success -> {
                            context.openFileOutput("imported_workflow.json", Context.MODE_PRIVATE).use { output ->
                                output.write(result.apiJson.toByteArray())
                            }
                            val updatedSettings = _settings.value.copy(workflowToUse = "imported_workflow.json")
                            updateSettings(updatedSettings)
                            _importState.value = ImportState.Idle
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Workflow converted and imported successfully!", Toast.LENGTH_SHORT).show()
                            }
                        }
                        is ConversionResult.Error.MissingExtension -> {
                            _importState.value = ImportState.MissingExtension
                        }
                        is ConversionResult.Error.Generic -> {
                            _importState.value = ImportState.Error(result.message)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _importState.value = ImportState.Error(e.localizedMessage ?: "Unknown error occurred")
            }
        }
    }

    private val _currentPrompt = MutableStateFlow(settingsManager.getLastPrompt())
    val currentPrompt: StateFlow<String> = _currentPrompt.asStateFlow()

    private val _galleryItems = MutableStateFlow(settingsManager.getGalleryItems())
    val galleryItems: StateFlow<List<GalleryItem>> = _galleryItems.asStateFlow()

    private val _savedWorkflows = MutableStateFlow<List<String>>(emptyList())
    val savedWorkflows: StateFlow<List<String>> = _savedWorkflows.asStateFlow()

    private val _localModels = MutableStateFlow<List<String>>(emptyList())
    val localModels: StateFlow<List<String>> = _localModels.asStateFlow()

    fun fetchLocalModels(baseUrl: String) {
        viewModelScope.launch {
            try {
                val models = GeminiClient.fetchLocalModels(baseUrl)
                _localModels.value = models
                if (models.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(getApplication(), "No models found or error fetching.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(getApplication(), "Fetched ${models.size} models", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "Error fetching local models: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                _localModels.value = emptyList()
            }
        }
    }

    fun refreshSavedWorkflows() {
        ComfyClient.fetchSavedWorkflows(_settings.value.serverUrl) { list ->
            _savedWorkflows.value = list
        }
    }

    init {
        refreshSavedWorkflows()
        viewModelScope.launch {
            ComfyClient.progressFlow.collect { progress ->
                if (progress.state == GenerationState.Completed && progress.finalImage != null) {
                    val currentSettings = _settings.value
                    val seed = when (currentSettings.seedMode) {
                        SeedMode.Fixed -> currentSettings.fixedSeedValue
                        SeedMode.Custom -> currentSettings.customSeedValue
                        else -> currentSettings.lastUsedSeedValue
                    }
                    
                    val items = settingsManager.getGalleryItems().toMutableList()
                    // Avoid duplicate records for the same generation
                    if (items.none { it.imageUrl == progress.finalImage }) {
                        items.add(
                            0, // Prepend new items to display latest first
                            com.example.comfyprompt.data.GalleryItem(
                                imageUrl = progress.finalImage,
                                prompt = currentPrompt.value,
                                enhancedPrompt = progress.enhancedPrompt,
                                seed = seed
                            )
                        )
                        settingsManager.saveGalleryItems(items)
                        _galleryItems.value = items
                    }
                }
            }
        }
    }

    fun deleteGalleryItem(id: String) {
        val items = settingsManager.getGalleryItems().toMutableList()
        if (items.removeAll { it.id == id }) {
            settingsManager.saveGalleryItems(items)
            _galleryItems.value = items
        }
    }

    fun updatePrompt(prompt: String) {
        _currentPrompt.value = prompt
        settingsManager.saveLastPrompt(prompt)
    }

    val progressInfo: StateFlow<ProgressInfo> = ComfyClient.progressFlow

    fun updateSettings(newSettings: AppSettings) {
        val serverUrlChanged = _settings.value.serverUrl != newSettings.serverUrl
        _settings.value = newSettings
        settingsManager.saveSettings(newSettings)
        if (serverUrlChanged) {
            refreshSavedWorkflows()
        }
    }

    fun generateImage(prompt: String) {
        _currentPrompt.value = prompt
        viewModelScope.launch {
            val currentSettings = _settings.value
            
            val hasApiKey = when (currentSettings.apiProvider) {
                "ChatGPT" -> currentSettings.chatgptApiKey.isNotBlank()
                "Claude" -> currentSettings.claudeApiKey.isNotBlank()
                "Grok" -> currentSettings.grokApiKey.isNotBlank()
                else -> currentSettings.geminiApiKey.isNotBlank()
            }
            
            // 1. Enhance prompt if API key is provided AND enhancer is active
            val finalPrompt = if (hasApiKey && currentSettings.enableEnhancer) {
                // Reset state with enhancing status
                ComfyClient.progressFlow.value = ProgressInfo(
                    state = GenerationState.EnhancingPrompt,
                    statusText = "Enhancing prompt with ${currentSettings.apiProvider}..."
                )
                val enhanced = GeminiClient.enhancePrompt(prompt, currentSettings)
                ComfyClient.progressFlow.value = ComfyClient.progressFlow.value.copy(
                    enhancedPrompt = enhanced
                )
                enhanced
            } else {
                // Reset state directly with connecting status
                ComfyClient.progressFlow.value = ProgressInfo(
                    state = GenerationState.ConnectingComfy,
                    statusText = "Bypassing enhancer, connecting to ComfyUI..."
                )
                prompt
            }

            // 2. Start ComfyUI workflow execution
            ComfyClient.startGeneration(getApplication(), finalPrompt, currentSettings) { generatedSeed ->
                // Store the generated seed as "last used" for traceability
                val updated = _settings.value.copy(lastUsedSeedValue = generatedSeed)
                updateSettings(updated)
            }
        }
    }

    fun stopGeneration() {
        ComfyClient.stopGeneration(_settings.value)
    }

    fun resetState() {
        ComfyClient.progressFlow.value = ProgressInfo(state = GenerationState.Idle)
    }

    fun saveImageToDownloads(imageUrl: String, format: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            try {
                // Fetch the bytes from ComfyUI output view API
                val client = OkHttpClient()
                val request = Request.Builder().url(imageUrl).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Failed to download image from server.", Toast.LENGTH_SHORT).show()
                        }
                        return@launch
                    }
                    val bytes = response.body?.bytes() ?: return@launch

                    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    val ext = format.lowercase(Locale.getDefault())
                    val displayName = "ComfyUI_$timeStamp.$ext"
                    val mimeType = when (format) {
                        "JPEG" -> "image/jpeg"
                        "WEBP" -> "image/webp"
                        else -> "image/png"
                    }

                    val resolver = context.contentResolver
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                        put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                            put(MediaStore.MediaColumns.IS_PENDING, 1)
                        } else {
                            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                            val file = File(downloadsDir, displayName)
                            put(MediaStore.MediaColumns.DATA, file.absolutePath)
                        }
                    }

                    val collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI
                    } else {
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    }

                    val uri = resolver.insert(collectionUri, contentValues)
                    if (uri != null) {
                        resolver.openOutputStream(uri).use { outputStream ->
                            if (outputStream != null) {
                                outputStream.write(bytes)
                                outputStream.flush()
                            }
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            contentValues.clear()
                            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                            resolver.update(uri, contentValues, null, null)
                        }

                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Saved to Downloads: $displayName", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Error insert MediaStore row.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Save Failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun shareImage(imageUrl: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            try {
                val client = OkHttpClient()
                val request = Request.Builder().url(imageUrl).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Failed to load image for sharing.", Toast.LENGTH_SHORT).show()
                        }
                        return@launch
                    }
                    val bytes = response.body?.bytes() ?: return@launch

                    // Save the image temporarily in cache
                    val cachePath = File(context.cacheDir, "images")
                    cachePath.mkdirs()
                    val file = File(cachePath, "comfy_shared_output.png")
                    FileOutputStream(file).use { out ->
                        out.write(bytes)
                        out.flush()
                    }

                    // Get FileProvider Uri
                    val contentUri: Uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )

                    // Open share chooser
                    val shareIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_STREAM, contentUri)
                        type = "image/png"
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }

                    withContext(Dispatchers.Main) {
                        val chooser = Intent.createChooser(shareIntent, "Share Comfy Output")
                        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(chooser)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Sharing Failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun downloadWorkflow() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            try {
                // Read from Assets
                val assetManager = context.assets
                val jsonString = assetManager.open("ernie_workflow_ui.json").use { inputStream ->
                    inputStream.bufferedReader().use { it.readText() }
                }

                val fileName = "workflow.json"
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    } else {
                        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        val file = File(downloadsDir, fileName)
                        put(MediaStore.MediaColumns.DATA, file.absolutePath)
                    }
                }

                val collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI
                } else {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI // Fallback
                }

                val uri = resolver.insert(collectionUri, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri).use { outputStream ->
                        if (outputStream != null) {
                            outputStream.write(jsonString.toByteArray())
                            outputStream.flush()
                        }
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        contentValues.clear()
                        contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                        resolver.update(uri, contentValues, null, null)
                    }

                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Saved workflow.json to Downloads!", Toast.LENGTH_LONG).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Failed to create workflow.json in Downloads.", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Download Failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
