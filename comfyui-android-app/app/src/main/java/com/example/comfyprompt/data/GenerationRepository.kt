package com.example.comfyprompt.data

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import com.example.comfyprompt.MainActivity
import com.example.comfyprompt.network.AppLogger
import com.example.comfyprompt.network.ComfyClient
import com.example.comfyprompt.network.GeminiClient
import com.example.comfyprompt.network.PingResult
import com.example.comfyprompt.network.TriggerCmdClient
import com.example.comfyprompt.network.UrlValidator
import com.example.comfyprompt.network.ValidationResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GenerationRepository private constructor(private val context: Context) {

    private val repositoryScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val settingsManager = SettingsManager(context)

    private val _settings = MutableStateFlow(settingsManager.getSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _queueList = MutableStateFlow<List<QueueJob>>(emptyList())
    val queueList: StateFlow<List<QueueJob>> = _queueList.asStateFlow()

    private val _activeJobId = MutableStateFlow<String?>(null)
    val activeJobId: StateFlow<String?> = _activeJobId.asStateFlow()

    private val _progressInfo = MutableStateFlow(ProgressInfo())
    val progressInfo: StateFlow<ProgressInfo> = _progressInfo.asStateFlow()

    private val _serverWakeState = MutableStateFlow<ServerWakeState>(ServerWakeState.Idle)
    val serverWakeState: StateFlow<ServerWakeState> = _serverWakeState.asStateFlow()

    private val _galleryItems = MutableStateFlow(settingsManager.getGalleryItems())
    val galleryItems: StateFlow<List<GalleryItem>> = _galleryItems.asStateFlow()

    private val _savedWorkflows = MutableStateFlow<List<String>>(emptyList())
    val savedWorkflows: StateFlow<List<String>> = _savedWorkflows.asStateFlow()

    private var lastWakeTriggerTime: Long = 0L
    @Volatile
    private var isPollingActive: Boolean = false

    init {
        refreshSavedWorkflows()
        observeComfyClientProgress()
    }

    fun refreshSavedWorkflows() {
        ComfyClient.fetchSavedWorkflows(_settings.value.serverUrl) { list ->
            _savedWorkflows.value = list
        }
    }

    private fun observeComfyClientProgress() {
        repositoryScope.launch {
            ComfyClient.progressFlow.collect { progress ->
                val activeId = _activeJobId.value
                val activeJob = _queueList.value.firstOrNull { it.id == activeId }
                val currentSettings = activeJob?.settings ?: _settings.value

                // Clear wake cooldown immediately if connection succeeds
                if (currentSettings.hostType == HostType.LOCAL &&
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

                    var shouldRemoveJob = true
                    if (progress.state == GenerationState.Failed) {
                        if (currentSettings.triggerCmdEnabled &&
                            currentSettings.hostType == HostType.LOCAL &&
                            isConnectionError(progress.statusText)) {

                            val now = System.currentTimeMillis()
                            if (now - lastWakeTriggerTime > 5 * 60 * 1000) {
                                shouldRemoveJob = false
                                lastWakeTriggerTime = now
                                startWakeSequence(currentSettings)
                            } else if (_serverWakeState.value is ServerWakeState.Waking ||
                                       _serverWakeState.value is ServerWakeState.Polling) {
                                shouldRemoveJob = false
                            }
                        }
                    }

                    if (!MainActivity.isAppInForeground) {
                        sendLocalNotification(mergedProgress, activeId)
                    }

                    if (progress.state == GenerationState.Completed && progress.finalImage != null) {
                        val seed = when (currentSettings.seedMode) {
                            SeedMode.Fixed -> currentSettings.fixedSeedValue
                            SeedMode.Custom -> currentSettings.customSeedValue
                            else -> currentSettings.lastUsedSeedValue
                        }

                        val items = settingsManager.getGalleryItems().toMutableList()
                        if (items.none { it.imageUrl == progress.finalImage }) {
                            items.add(
                                0,
                                GalleryItem(
                                    imageUrl = progress.finalImage,
                                    prompt = activeJob?.prompt ?: "",
                                    enhancedPrompt = mergedProgress.enhancedPrompt,
                                    seed = seed
                                )
                            )
                            settingsManager.saveGalleryItems(items)
                            _galleryItems.value = items
                        }
                    }

                    if (shouldRemoveJob) {
                        _queueList.value = _queueList.value.filter { it.id != activeId }
                    }
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

    fun updateSettings(newSettings: AppSettings) {
        if (newSettings.hostType == HostType.LOCAL) {
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

    fun queueGeneration(prompt: String, inputImageUri: Uri? = null, bypassedGroups: List<String> = emptyList()) {
        val job = QueueJob(
            prompt = prompt,
            settings = _settings.value,
            inputImageUri = inputImageUri?.toString(),
            bypassedGroups = bypassedGroups
        )
        _queueList.value = _queueList.value + job
        AppLogger.i("GenerationRepository", "Queued job: ${job.id}. Queue size: ${_queueList.value.size}")
        processQueueNext()
    }

    private fun processQueueNext() {
        val activeId = _activeJobId.value
        if (activeId != null) {
            AppLogger.d("GenerationRepository", "Queue processing: job $activeId is already running")
            return
        }
        val nextJob = _queueList.value.firstOrNull { it.progress.state == GenerationState.Idle }
        if (nextJob == null) {
            AppLogger.d("GenerationRepository", "Queue is empty or all jobs are processed")
            return
        }
        _activeJobId.value = nextJob.id
        AppLogger.i("GenerationRepository", "Starting queued job: ${nextJob.id}")

        repositoryScope.launch {
            runJob(nextJob)
        }
    }

    private suspend fun runJob(job: QueueJob) {
        com.example.comfyprompt.network.GenerationService.start(context)
        val currentSettings = job.settings

        val hasApiKey = when (currentSettings.apiProvider) {
            "ChatGPT" -> currentSettings.chatgptApiKey.isNotBlank()
            "Claude" -> currentSettings.claudeApiKey.isNotBlank()
            "Grok" -> currentSettings.grokApiKey.isNotBlank()
            "Local / Custom" -> true // Bypasses early return
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

        val imageUri = job.inputImageUri?.let { Uri.parse(it) }
        ComfyClient.startGeneration(context, finalPrompt, currentSettings, imageUri, job.bypassedGroups) { generatedSeed ->
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
            AppLogger.i("GenerationRepository", "Cancelling active job: $jobId")
            stopGeneration()
        } else {
            AppLogger.i("GenerationRepository", "Cancelling pending job: $jobId")
            _queueList.value = _queueList.value.filter { it.id != jobId }
        }
    }

    fun stopAllJobs() {
        AppLogger.i("GenerationRepository", "Stopping active generation and clearing all queued jobs")
        val activeId = _activeJobId.value
        if (activeId != null) {
            stopGeneration()
        }
        _queueList.value = emptyList()
        _activeJobId.value = null
    }

    fun clearPendingQueue() {
        AppLogger.i("GenerationRepository", "Clearing pending queued jobs from the queue")
        val activeId = _activeJobId.value
        if (activeId != null) {
            _queueList.value = _queueList.value.filter { it.id == activeId }
        } else {
            _queueList.value = emptyList()
        }
    }

    fun stopGeneration() {
        ComfyClient.stopGeneration(_settings.value)
        com.example.comfyprompt.network.GenerationService.stop(context)
    }

    fun resetState() {
        ComfyClient.progressFlow.value = ProgressInfo(state = GenerationState.Idle)
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

    fun startWakeSequence(settings: AppSettings) {
        val currentState = _serverWakeState.value
        if (currentState is ServerWakeState.Waking ||
            currentState is ServerWakeState.Polling ||
            currentState is ServerWakeState.HostUnreachable ||
            isPollingActive) {
            AppLogger.d("GenerationRepository", "ServerWake: Wake or polling is already in progress. Skipping duplicate trigger.")
            return
        }

        repositoryScope.launch {
            _serverWakeState.value = ServerWakeState.Waking
            AppLogger.i("GenerationRepository", "ServerWake: Triggering TRIGGERcmd wake call...")

            if (!MainActivity.isAppInForeground) {
                sendPersistentWakeNotification(ServerWakeState.Waking)
            }

            val success = TriggerCmdClient.wakeServer(
                token = settings.triggerCmdToken,
                trigger = settings.triggerCmdName,
                computer = settings.triggerCmdComputer
            )

            if (success) {
                _serverWakeState.value = ServerWakeState.Polling

                if (!MainActivity.isAppInForeground) {
                    sendPersistentWakeNotification(ServerWakeState.Polling)
                }

                if (isPollingActive) {
                    AppLogger.d("GenerationRepository", "ServerWake: Polling loop is already active. Skipping duplicate spawn.")
                    return@launch
                }

                repositoryScope.launch(Dispatchers.IO) {
                    isPollingActive = true
                    AppLogger.i("GenerationRepository", "ServerWake: Starting ComfyUI server polling loop...")
                    var serverUp = false
                    val startPollTime = System.currentTimeMillis()
                    while (System.currentTimeMillis() - startPollTime < 5 * 60 * 1000) {
                        if (!isPollingActive) break
                        val result = TriggerCmdClient.pollLocalServer(settings.serverUrl)
                        if (result == PingResult.ONLINE) {
                            serverUp = true
                            break
                        }
                        _serverWakeState.value = ServerWakeState.Polling
                        delay(5000)
                    }

                    if (serverUp) {
                        AppLogger.i("GenerationRepository", "ServerWake: ComfyUI server is online!")
                        lastWakeTriggerTime = 0L
                        _serverWakeState.value = ServerWakeState.Success
                        dismissPersistentWakeNotification()

                        if (!MainActivity.isAppInForeground) {
                            sendLocalServerReadyNotification()
                        }

                        withContext(Dispatchers.Main) {
                            // Reset failed connection-error jobs to Idle to auto-retry
                            _queueList.value = _queueList.value.map { job ->
                                if (job.progress.state == GenerationState.Failed &&
                                    isConnectionError(job.progress.statusText)) {
                                    job.copy(progress = ProgressInfo(state = GenerationState.Idle))
                                } else {
                                    job
                                }
                            }
                            processQueueNext()

                            Toast.makeText(
                                context,
                                "ComfyUI Server is online and ready! ✨",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } else {
                        AppLogger.w("GenerationRepository", "ServerWake: Polling timed out.")
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

        val intent = Intent(context, MainActivity::class.java).apply {
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
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.cancel(PERSISTENT_WAKE_NOTIFICATION_ID)
    }

    private fun sendLocalServerReadyNotification() {
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

        val intent = Intent(context, MainActivity::class.java).apply {
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

    private fun sendLocalNotification(progress: ProgressInfo, jobId: String?) {
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

        val intent = Intent(context, MainActivity::class.java).apply {
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

    companion object {
        @Volatile
        private var INSTANCE: GenerationRepository? = null

        fun getInstance(context: Context): GenerationRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: GenerationRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
