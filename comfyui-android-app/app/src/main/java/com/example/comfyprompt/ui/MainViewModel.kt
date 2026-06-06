package com.example.comfyprompt.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.comfyprompt.data.AppSettings
import com.example.comfyprompt.data.GalleryItem
import com.example.comfyprompt.data.GenerationRepository
import com.example.comfyprompt.data.MediaSaver
import com.example.comfyprompt.data.ImageRefinerChatManager
import com.example.comfyprompt.data.SettingsManager
import com.example.comfyprompt.data.FormatType
import com.example.comfyprompt.data.ConversionResult
import com.example.comfyprompt.data.ServerWakeState
import com.example.comfyprompt.network.ComfyClient
import com.example.comfyprompt.network.GeminiClient
import com.example.comfyprompt.network.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class ImportState {
    object Idle : ImportState()
    object Loading : ImportState()
    object MissingExtension : ImportState()
    data class Error(val message: String) : ImportState()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = GenerationRepository.getInstance(application)
    private val refinerManager = ImageRefinerChatManager()
    private val settingsManager = SettingsManager(application)

    val settings = repository.settings
    val queueList = repository.queueList
    val activeJobId = repository.activeJobId
    val progressInfo = repository.progressInfo
    val serverWakeState = repository.serverWakeState
    val galleryItems = repository.galleryItems
    val savedWorkflows = repository.savedWorkflows

    private val _currentPrompt = MutableStateFlow(settingsManager.getLastPrompt())
    val currentPrompt: StateFlow<String> = _currentPrompt.asStateFlow()

    private val _generateCooldownSeconds = MutableStateFlow(0)
    val generateCooldownSeconds: StateFlow<Int> = _generateCooldownSeconds.asStateFlow()

    private val _localModels = MutableStateFlow<List<String>>(emptyList())
    val localModels: StateFlow<List<String>> = _localModels.asStateFlow()

    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState.asStateFlow()

    private val _workflowGroups = MutableStateFlow<List<String>>(emptyList())
    val workflowGroups: StateFlow<List<String>> = _workflowGroups.asStateFlow()

    private val _bypassedGroups = MutableStateFlow<Set<String>>(emptySet())
    val bypassedGroups: StateFlow<Set<String>> = _bypassedGroups.asStateFlow()

    init {
        viewModelScope.launch {
            var lastWorkflow: String? = null
            var lastServerUrl: String? = null
            settings.collect { settingsVal ->
                if (settingsVal.workflowToUse != lastWorkflow || settingsVal.serverUrl != lastServerUrl) {
                    lastWorkflow = settingsVal.workflowToUse
                    lastServerUrl = settingsVal.serverUrl
                    loadWorkflowGroups(getApplication(), settingsVal.workflowToUse, settingsVal.serverUrl)
                }
            }
        }
    }

    val refinerMessages = refinerManager.chatMessages
    val isRefinerLoading = refinerManager.isChatLoading

    fun initRefinerChat() {
        refinerManager.initChat()
    }

    fun clearRefinerChat() {
        refinerManager.clearChat()
    }

    fun sendRefinerMessage(
        context: Context,
        text: String,
        generatedImageUrl: String,
        attachedUris: List<Uri>
    ) {
        viewModelScope.launch {
            refinerManager.sendChatMessage(context, text, generatedImageUrl, attachedUris, settings.value)
        }
    }

    fun deleteGalleryItem(id: String) {
        repository.deleteGalleryItem(id)
    }

    fun updatePrompt(prompt: String) {
        _currentPrompt.value = prompt
        settingsManager.saveLastPrompt(prompt)
    }

    fun updateSettings(newSettings: AppSettings) {
        repository.updateSettings(newSettings)
    }

    fun generateImage(prompt: String, inputImageUri: Uri? = null) {
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
        repository.queueGeneration(prompt, inputImageUri, _bypassedGroups.value.toList())
    }

    fun loadWorkflowGroups(context: Context, workflowToUse: String, serverUrl: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val jsonStr = if (workflowToUse == "imported_workflow.json") {
                    try {
                        context.openFileInput("imported_workflow.json").bufferedReader().use { it.readText() }
                    } catch (e: Exception) { null }
                } else if (workflowToUse.isNotBlank()) {
                    val cleanUrl = serverUrl.removeSuffix("/")
                    val encodedPath = java.net.URLEncoder.encode("workflows/$workflowToUse", "UTF-8").replace("+", "%20")
                    val url = "$cleanUrl/userdata/$encodedPath"
                    val request = okhttp3.Request.Builder().url(url).build()
                    try {
                        ComfyClient.client.newCall(request).execute().use { response ->
                            if (response.isSuccessful) response.body?.string() else null
                        }
                    } catch (e: Exception) { null }
                } else {
                    try {
                        context.assets.open("ernie_workflow.json").use {
                            java.io.InputStreamReader(it).readText()
                        }
                    } catch (e: Exception) { null }
                }

                if (!jsonStr.isNullOrBlank()) {
                    val element = com.google.gson.JsonParser.parseString(jsonStr)
                    if (element.isJsonObject) {
                        val obj = element.asJsonObject
                        if (obj.has("groups")) {
                            val groupsArr = obj.getAsJsonArray("groups")
                            val titles = mutableListOf<String>()
                            groupsArr.forEach { groupEl ->
                                if (groupEl.isJsonObject) {
                                    val groupObj = groupEl.asJsonObject
                                    val title = groupObj.get("title")?.asString
                                    if (!title.isNullOrBlank()) {
                                        titles.add(title)
                                    }
                                }
                            }
                            _workflowGroups.value = titles
                            val titlesSet = titles.toSet()
                            _bypassedGroups.value = titlesSet
                            return@launch
                        }
                    }
                }
                _workflowGroups.value = emptyList()
                _bypassedGroups.value = emptySet()
            } catch (e: Exception) {
                AppLogger.e("MainViewModel", "Error loading workflow groups: ${e.localizedMessage}")
                _workflowGroups.value = emptyList()
                _bypassedGroups.value = emptySet()
            }
        }
    }

    fun toggleGroupBypass(groupTitle: String) {
        val current = _bypassedGroups.value
        _bypassedGroups.value = if (current.contains(groupTitle)) {
            current - groupTitle
        } else {
            current + groupTitle
        }
    }

    fun toggleAllGroups(enableAll: Boolean) {
        _bypassedGroups.value = if (enableAll) {
            emptySet()
        } else {
            _workflowGroups.value.toSet()
        }
    }

    fun clearBypassedGroups() {
        _bypassedGroups.value = emptySet()
    }

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
        repository.refreshSavedWorkflows()
    }

    fun cancelJob(jobId: String) {
        repository.cancelJob(jobId)
    }

    fun stopAllJobs() {
        repository.stopAllJobs()
    }

    fun clearPendingQueue() {
        repository.clearPendingQueue()
    }

    fun stopGeneration() {
        repository.stopGeneration()
    }

    fun resetState() {
        repository.resetState()
    }

    fun getLastWakeTriggerTime(): Long = repository.getLastWakeTriggerTime()

    fun resetWakeState() {
        repository.resetWakeState()
    }

    fun cancelWakeSequence() {
        repository.cancelWakeSequence()
    }

    fun startWakeSequence(settings: AppSettings) {
        repository.startWakeSequence(settings)
    }

    fun onAppBackgrounded() {
        repository.onAppBackgrounded()
    }

    fun onAppForegrounded() {
        repository.onAppForegrounded()
    }

    fun saveImageToDownloads(imageUrl: String, format: String) {
        viewModelScope.launch {
            MediaSaver.saveImageToDownloads(getApplication(), imageUrl, format)
        }
    }

    fun shareImage(imageUrl: String) {
        viewModelScope.launch {
            MediaSaver.shareImage(getApplication(), imageUrl)
        }
    }

    fun downloadWorkflow() {
        viewModelScope.launch {
            MediaSaver.downloadWorkflow(getApplication())
        }
    }

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
                    val updatedSettings = settings.value.copy(workflowToUse = "imported_workflow.json")
                    updateSettings(updatedSettings)
                    _importState.value = ImportState.Idle
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Workflow imported successfully!", Toast.LENGTH_SHORT).show()
                    }
                } else if (format == FormatType.UI_STANDARD) {
                    val result = ComfyClient.convertWorkflowToApi(jsonString, settings.value.serverUrl)
                    when (result) {
                        is ConversionResult.Success -> {
                            context.openFileOutput("imported_workflow.json", Context.MODE_PRIVATE).use { output ->
                                output.write(result.apiJson.toByteArray())
                            }
                            val updatedSettings = settings.value.copy(workflowToUse = "imported_workflow.json")
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

    fun isConnectionError(statusText: String): Boolean {
        return repository.isConnectionError(statusText)
    }
}
