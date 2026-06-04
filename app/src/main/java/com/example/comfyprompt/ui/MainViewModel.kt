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
import com.example.comfyprompt.data.ServerWakeState
import com.example.comfyprompt.network.ComfyClient
import com.example.comfyprompt.network.GeminiClient
import com.example.comfyprompt.network.UrlValidator
import com.example.comfyprompt.network.ValidationResult
import com.example.comfyprompt.network.AppLogger
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

    private var lastWakeTriggerTime: Long = 0L
    @kotlin.jvm.Volatile
    private var isPollingActive: Boolean = false

    private val _serverWakeState = MutableStateFlow<ServerWakeState>(ServerWakeState.Idle)
    val serverWakeState: StateFlow<ServerWakeState> = _serverWakeState.asStateFlow()

    private val _queueList = MutableStateFlow<List<com.example.comfyprompt.data.QueueJob>>(emptyList())
    val queueList: StateFlow<List<com.example.comfyprompt.data.QueueJob>> = _queueList.asStateFlow()

    private val _activeJobId = MutableStateFlow<String?>(null)
    val activeJobId: StateFlow<String?> = _activeJobId.asStateFlow()


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

    private val _generateCooldownSeconds = MutableStateFlow(0)
    val generateCooldownSeconds: StateFlow<Int> = _generateCooldownSeconds.asStateFlow()

    private val _galleryItems = MutableStateFlow(settingsManager.getGalleryItems())
    val galleryItems: StateFlow<List<GalleryItem>> = _galleryItems.asStateFlow()

    private val _savedWorkflows = MutableStateFlow<List<String>>(emptyList())
    val savedWorkflows: StateFlow<List<String>> = _savedWorkflows.asStateFlow()

    private val _localModels = MutableStateFlow<List<String>>(emptyList())
    val localModels: StateFlow<List<String>> = _localModels.asStateFlow()

    private val _progressInfo = MutableStateFlow(ProgressInfo())
    val progressInfo: StateFlow<ProgressInfo> = _progressInfo.asStateFlow()

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
                val activeId = _activeJobId.value
                val activeJob = _queueList.value.firstOrNull { it.id == activeId }
                val currentSettings = activeJob?.settings ?: _settings.value

                // Clear wake cooldown immediately if connection succeeds
                if (currentSettings.hostType == com.example.comfyprompt.data.HostType.LOCAL &&
                    (progress.state == GenerationState.ConnectingComfy || 
                     progress.state == GenerationState.GeneratingBase ||
                     progress.state == GenerationState.Completed)) {
                    lastWakeTriggerTime = 0L
                }

                val currentEnhancedPrompt = activeJob?.progress?.enhancedPrompt
                val mergedProgress = progress.copy(
                    enhancedPrompt = currentEnhancedPrompt ?: progress.enhancedPrompt
                )

                if (activeId != null) {
                    updateJobProgress(activeId, mergedProgress)
                    _progressInfo.value = mergedProgress
                } else {
                    _progressInfo.value = mergedProgress
                }

                if (progress.state == GenerationState.Completed || 
                    progress.state == GenerationState.Failed || 
                    progress.state == GenerationState.Cancelled) {
                    
                    if (progress.state == GenerationState.Failed) {
                        if (currentSettings.triggerCmdEnabled && 
                            currentSettings.hostType == com.example.comfyprompt.data.HostType.LOCAL &&
                            isConnectionError(progress.statusText)) {
                            
                            val now = System.currentTimeMillis()
                            if (now - lastWakeTriggerTime > 5 * 60 * 1000) {
                                lastWakeTriggerTime = now
                                startWakeSequence(currentSettings)
                            }
                        }
                    }

                    if (!com.example.comfyprompt.MainActivity.isAppInForeground) {
                        sendLocalNotification(mergedProgress, activeId)
                    }

                    if (progress.state == GenerationState.Completed && progress.finalImage != null) {
                        val currentSettings = activeJob?.settings ?: _settings.value
                        val seed = when (currentSettings.seedMode) {
                            SeedMode.Fixed -> currentSettings.fixedSeedValue
                            SeedMode.Custom -> currentSettings.customSeedValue
                            else -> currentSettings.lastUsedSeedValue
                        }
                        
                        val items = settingsManager.getGalleryItems().toMutableList()
                        if (items.none { it.imageUrl == progress.finalImage }) {
                            items.add(
                                0,
                                com.example.comfyprompt.data.GalleryItem(
                                    imageUrl = progress.finalImage,
                                    prompt = activeJob?.prompt ?: currentPrompt.value,
                                    enhancedPrompt = mergedProgress.enhancedPrompt,
                                    seed = seed
                                )
                            )
                            settingsManager.saveGalleryItems(items)
                            _galleryItems.value = items
                        }
                    }

                    _queueList.value = _queueList.value.filter { it.id != activeId }
                    _activeJobId.value = null
                    processQueueNext()
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

    fun updateSettings(newSettings: AppSettings) {
        if (newSettings.hostType == com.example.comfyprompt.data.HostType.LOCAL) {
            val validation = UrlValidator.validateUrl(newSettings.localIpAddress)
            if (validation is ValidationResult.Error) {
                return
            }
        }
        val serverUrlChanged = _settings.value.serverUrl != newSettings.serverUrl
        _settings.value = newSettings
        settingsManager.saveSettings(newSettings)
        if (serverUrlChanged) {
            refreshSavedWorkflows()
        }
    }

    fun generateImage(prompt: String) {
        if (_generateCooldownSeconds.value > 0) {
            AppLogger.w("MainViewModel", "Generate image blocked: active cooldown is ${_generateCooldownSeconds.value} seconds.")
            return
        }
        AppLogger.i("MainViewModel", "Generate image triggered. Starting 5-second cooldown.")
        _generateCooldownSeconds.value = 5
        viewModelScope.launch {
            while (_generateCooldownSeconds.value > 0) {
                kotlinx.coroutines.delay(1000)
                _generateCooldownSeconds.value = _generateCooldownSeconds.value - 1
            }
            AppLogger.i("MainViewModel", "Cooldown complete. Generation unlocked.")
        }

        _currentPrompt.value = prompt
        queueGeneration(prompt)
    }

    private fun queueGeneration(prompt: String) {
        val job = com.example.comfyprompt.data.QueueJob(
            prompt = prompt,
            settings = _settings.value
        )
        _queueList.value = _queueList.value + job
        AppLogger.i("MainViewModel", "Queued job: ${job.id}. Queue size: ${_queueList.value.size}")
        processQueueNext()
    }

    private fun processQueueNext() {
        val activeId = _activeJobId.value
        if (activeId != null) {
            AppLogger.d("MainViewModel", "Queue processing: job $activeId is already running")
            return
        }
        val nextJob = _queueList.value.firstOrNull { it.progress.state == GenerationState.Idle }
        if (nextJob == null) {
            AppLogger.d("MainViewModel", "Queue is empty or all jobs are processed")
            return
        }
        _activeJobId.value = nextJob.id
        AppLogger.i("MainViewModel", "Starting queued job: ${nextJob.id}")
        
        viewModelScope.launch {
            runJob(nextJob)
        }
    }

    private suspend fun runJob(job: com.example.comfyprompt.data.QueueJob) {
        val currentSettings = job.settings
        
        val hasApiKey = when (currentSettings.apiProvider) {
            "ChatGPT" -> currentSettings.chatgptApiKey.isNotBlank()
            "Claude" -> currentSettings.claudeApiKey.isNotBlank()
            "Grok" -> currentSettings.grokApiKey.isNotBlank()
            else -> currentSettings.geminiApiKey.isNotBlank()
        }
        
        val finalPrompt = if (hasApiKey && currentSettings.enableEnhancer) {
            val progress1 = ProgressInfo(
                state = GenerationState.EnhancingPrompt,
                statusText = "Enhancing prompt with ${currentSettings.apiProvider}..."
            )
            updateJobProgress(job.id, progress1)
            _progressInfo.value = progress1

            val enhanced = GeminiClient.enhancePrompt(job.prompt, currentSettings)

            val progress2 = ProgressInfo(
                state = GenerationState.ConnectingComfy,
                enhancedPrompt = enhanced,
                statusText = "Enhanced with ${currentSettings.apiProvider}, connecting to ComfyUI..."
            )
            updateJobProgress(job.id, progress2)
            _progressInfo.value = progress2
            enhanced
        } else {
            val progress1 = ProgressInfo(
                state = GenerationState.ConnectingComfy,
                statusText = "Bypassing enhancer, connecting to ComfyUI..."
            )
            updateJobProgress(job.id, progress1)
            _progressInfo.value = progress1
            job.prompt
        }

        ComfyClient.startGeneration(getApplication(), finalPrompt, currentSettings) { generatedSeed ->
            val updated = _settings.value.copy(lastUsedSeedValue = generatedSeed)
            updateSettings(updated)
        }
    }

    private fun updateJobProgress(jobId: String, progress: ProgressInfo) {
        _queueList.value = _queueList.value.map {
            if (it.id == jobId) {
                it.copy(progress = progress)
            } else {
                it
            }
        }
    }

    fun cancelJob(jobId: String) {
        val activeId = _activeJobId.value
        if (jobId == activeId) {
            AppLogger.i("MainViewModel", "Cancelling active job: $jobId")
            stopGeneration()
        } else {
            AppLogger.i("MainViewModel", "Cancelling pending job: $jobId")
            _queueList.value = _queueList.value.filter { it.id != jobId }
        }
    }

    fun stopAllJobs() {
        AppLogger.i("MainViewModel", "Stopping active generation and clearing all queued jobs")
        val activeId = _activeJobId.value
        if (activeId != null) {
            stopGeneration()
        }
        _queueList.value = emptyList()
        _activeJobId.value = null
    }

    fun clearPendingQueue() {
        AppLogger.i("MainViewModel", "Clearing pending queued jobs from the queue")
        val activeId = _activeJobId.value
        if (activeId != null) {
            _queueList.value = _queueList.value.filter { it.id == activeId }
        } else {
            _queueList.value = emptyList()
        }
    }

    private fun sendLocalNotification(progress: ProgressInfo, jobId: String?) {
        val context = getApplication<Application>()
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                "comfyui_notifications",
                "Image Generation Status",
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for ComfyUI generation jobs"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val title = when (progress.state) {
            GenerationState.Completed -> "Generation Complete! ✨"
            GenerationState.Failed -> "Generation Failed ❌"
            else -> "Job Status Update"
        }
        val text = when (progress.state) {
            GenerationState.Completed -> "Your image is ready to view in Gallery."
            GenerationState.Failed -> progress.statusText
            else -> progress.statusText
        }

        val intent = Intent(context, com.example.comfyprompt.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            context,
            0,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val notification = androidx.core.app.NotificationCompat.Builder(context, "comfyui_notifications")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(jobId?.hashCode() ?: 9999, notification)
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

    private val _copilotMessages = MutableStateFlow<List<com.example.comfyprompt.data.ChatMessage>>(emptyList())
    val copilotMessages: StateFlow<List<com.example.comfyprompt.data.ChatMessage>> = _copilotMessages.asStateFlow()

    private val _isCopilotLoading = MutableStateFlow(false)
    val isCopilotLoading: StateFlow<Boolean> = _isCopilotLoading.asStateFlow()

    private val copilotImageBase64Cache = mutableMapOf<String, String>()

    fun initCopilotChat() {
        if (_copilotMessages.value.isEmpty()) {
            _copilotMessages.value = listOf(
                com.example.comfyprompt.data.ChatMessage(
                    sender = com.example.comfyprompt.data.MessageSender.AI,
                    text = "Hi! I'm your Prompt Refiner Assistant. I've analyzed your generated image. Tell me what changes you'd like to make, or upload reference images for context, and I'll help you refine the prompt!"
                )
            )
        }
    }

    fun clearCopilotChat() {
        _copilotMessages.value = emptyList()
        copilotImageBase64Cache.clear()
    }

    fun sendCopilotMessage(
        context: Context,
        text: String,
        generatedImageUrl: String,
        attachedUris: List<Uri>
    ) {
        if (text.isBlank() && attachedUris.isEmpty()) return

        viewModelScope.launch {
            _isCopilotLoading.value = true

            val imageUrls = mutableListOf<String>()

            val isFirstUserTurn = _copilotMessages.value.none { it.sender == com.example.comfyprompt.data.MessageSender.USER }
            if (isFirstUserTurn) {
                imageUrls.add(generatedImageUrl)
                val cached = copilotImageBase64Cache[generatedImageUrl]
                if (cached == null) {
                    val base64 = downloadImageAsBase64(generatedImageUrl)
                    if (base64 != null) {
                        copilotImageBase64Cache[generatedImageUrl] = base64
                    }
                }
            }

            attachedUris.forEach { uri ->
                val uriString = uri.toString()
                imageUrls.add(uriString)
                val base64 = getUriAsBase64(context, uri)
                if (base64 != null) {
                    copilotImageBase64Cache[uriString] = base64
                }
            }

            val userMessage = com.example.comfyprompt.data.ChatMessage(
                sender = com.example.comfyprompt.data.MessageSender.USER,
                text = text,
                imageUrls = imageUrls
            )

            _copilotMessages.value = _copilotMessages.value + userMessage

            val allMessages = _copilotMessages.value
            val responseText = GeminiClient.chatWithCopilot(allMessages, copilotImageBase64Cache, _settings.value)

            val marker = "REFINED_PROMPT:"
            val refinedPrompt = if (responseText.contains(marker)) {
                responseText.substringAfter(marker).trim()
            } else {
                null
            }
            val conversationalText = if (responseText.contains(marker)) {
                responseText.substringBefore(marker).trim()
            } else {
                responseText
            }

            val aiMessage = com.example.comfyprompt.data.ChatMessage(
                sender = com.example.comfyprompt.data.MessageSender.AI,
                text = conversationalText,
                refinedPrompt = refinedPrompt
            )

            _copilotMessages.value = _copilotMessages.value + aiMessage
            _isCopilotLoading.value = false
        }
    }

    private suspend fun downloadImageAsBase64(imageUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient()
            val request = Request.Builder().url(imageUrl).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bytes = response.body?.bytes()
                    if (bytes != null) {
                        return@withContext android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.e("MainViewModel", "Failed to download image for base64: ${e.message}")
        }
        null
    }

    private fun getUriAsBase64(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val bytes = inputStream.readBytes()
                android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            }
        } catch (e: Exception) {
            AppLogger.e("MainViewModel", "Failed to convert URI to base64: ${e.message}")
            null
        }
    }

    fun getLastWakeTriggerTime(): Long = lastWakeTriggerTime

    fun resetWakeState() {
        _serverWakeState.value = ServerWakeState.Idle
        dismissPersistentWakeNotification()
    }

    fun cancelWakeSequence() {
        isPollingActive = false
        _serverWakeState.value = ServerWakeState.Idle
        dismissPersistentWakeNotification()
    }

    fun isConnectionError(statusText: String): Boolean {
        val lower = statusText.lowercase(java.util.Locale.ROOT)
        return lower.contains("connection error") || 
               lower.contains("connection failed") || 
               lower.contains("failed to connect") ||
               lower.contains("unreachable") ||
               lower.contains("socket timeout") ||
               lower.contains("refused")
    }

    fun startWakeSequence(settings: AppSettings) {
        val currentState = _serverWakeState.value
        if (currentState is ServerWakeState.Waking || 
            currentState is ServerWakeState.Polling || 
            currentState is ServerWakeState.HostUnreachable ||
            isPollingActive) {
            AppLogger.d("MainViewModel", "ServerWake: Wake or polling is already in progress. Skipping duplicate trigger.")
            return
        }

        viewModelScope.launch {
            _serverWakeState.value = ServerWakeState.Waking
            AppLogger.i("MainViewModel", "ServerWake: Triggering TRIGGERcmd wake call...")
            
            if (!com.example.comfyprompt.MainActivity.isAppInForeground) {
                sendPersistentWakeNotification(ServerWakeState.Waking)
            }

            val success = com.example.comfyprompt.network.TriggerCmdClient.wakeServer(
                token = settings.triggerCmdToken,
                trigger = settings.triggerCmdName,
                computer = settings.triggerCmdComputer
            )

            if (success) {
                _serverWakeState.value = ServerWakeState.Polling
                
                if (!com.example.comfyprompt.MainActivity.isAppInForeground) {
                    sendPersistentWakeNotification(ServerWakeState.Polling)
                }

                if (isPollingActive) {
                    AppLogger.d("MainViewModel", "ServerWake: Polling loop is already active. Skipping duplicate spawn.")
                    return@launch
                }

                viewModelScope.launch(Dispatchers.IO) {
                    isPollingActive = true
                    AppLogger.i("MainViewModel", "ServerWake: Starting ComfyUI server polling loop...")
                    var serverUp = false
                    val startPollTime = System.currentTimeMillis()
                    while (System.currentTimeMillis() - startPollTime < 5 * 60 * 1000) {
                        if (!isPollingActive) break
                        val result = com.example.comfyprompt.network.TriggerCmdClient.pollLocalServer(settings.serverUrl)
                        if (result == com.example.comfyprompt.network.PingResult.ONLINE) {
                            serverUp = true
                            break
                        }
                        
                        if (result == com.example.comfyprompt.network.PingResult.HOST_UNREACHABLE) {
                            _serverWakeState.value = ServerWakeState.HostUnreachable
                        } else {
                            _serverWakeState.value = ServerWakeState.Polling
                        }
                        
                        kotlinx.coroutines.delay(5000)
                    }

                    if (serverUp) {
                        AppLogger.i("MainViewModel", "ServerWake: ComfyUI server is online!")
                        lastWakeTriggerTime = 0L
                        _serverWakeState.value = ServerWakeState.Success
                        dismissPersistentWakeNotification()

                        if (!com.example.comfyprompt.MainActivity.isAppInForeground) {
                            sendLocalServerReadyNotification()
                        }

                        withContext(Dispatchers.Main) {
                            android.widget.Toast.makeText(
                                getApplication(),
                                "ComfyUI Server is online and ready! ✨",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    } else {
                        AppLogger.w("MainViewModel", "ServerWake: Polling timed out.")
                        dismissPersistentWakeNotification()
                        if (isPollingActive) {
                            _serverWakeState.value = ServerWakeState.Timeout("Local ComfyUI server failed to respond within 5 minutes.")
                        }
                    }
                    isPollingActive = false
                }
            } else {
                _serverWakeState.value = ServerWakeState.Timeout("TRIGGERcmd failed to trigger. Please check your token and trigger config.")
                dismissPersistentWakeNotification()
            }
        }
    }

    private val PERSISTENT_WAKE_NOTIFICATION_ID = 9999

    fun onAppBackgrounded() {
        val state = _serverWakeState.value
        if (state is ServerWakeState.Waking || state is ServerWakeState.Polling) {
            sendPersistentWakeNotification(state)
        }
    }

    fun onAppForegrounded() {
        dismissPersistentWakeNotification()
    }

    private fun sendPersistentWakeNotification(state: ServerWakeState) {
        val context = getApplication<Application>()
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                "comfyui_notifications",
                "Image Generation Status",
                android.app.NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val title = "Waiting for ComfyUI to load... ⏳"
        val text = when (state) {
            is ServerWakeState.Waking -> "Waking local server via TRIGGERcmd..."
            else -> "Pinging server at ${_settings.value.serverUrl}..."
        }

        val intent = Intent(context, com.example.comfyprompt.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            context,
            0,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val notification = androidx.core.app.NotificationCompat.Builder(context, "comfyui_notifications")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(PERSISTENT_WAKE_NOTIFICATION_ID, notification)
    }

    private fun dismissPersistentWakeNotification() {
        val context = getApplication<Application>()
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.cancel(PERSISTENT_WAKE_NOTIFICATION_ID)
    }

    private fun sendLocalServerReadyNotification() {
        val context = getApplication<Application>()
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                "comfyui_notifications",
                "Image Generation Status",
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for ComfyUI generation jobs"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val title = "Found Server! ✨"
        val text = "Your local ComfyUI server is online and ready."

        val intent = Intent(context, com.example.comfyprompt.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            context,
            0,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val notification = androidx.core.app.NotificationCompat.Builder(context, "comfyui_notifications")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(8888, notification)
    }
}
