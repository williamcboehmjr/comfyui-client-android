package com.example.comfyprompt.network

import android.content.Context
import com.example.comfyprompt.data.AppSettings
import com.example.comfyprompt.data.ConversionResult
import com.example.comfyprompt.data.GenerationState
import com.example.comfyprompt.data.ProgressInfo
import com.example.comfyprompt.data.SeedMode
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.InputStreamReader
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.random.Random

object ComfyClient {
    internal val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.MINUTES) // Long timeout for large upscales
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    internal val gson = Gson()
    internal val mediaType = "application/json; charset=utf-8".toMediaType()

    internal var activeWebSocket: WebSocket? = null
    internal val clientId = UUID.randomUUID().toString()
    internal var currentPromptId: String? = null
    private var activeSeed: Long = 42L
    internal var activeSaveImageNodeId: String = "760"

    internal var activeServerUrl: String? = null
    private var webSocketRetryCount = 0
    private const val MAX_WEBSOCKET_RETRIES = 5

    val progressFlow = MutableStateFlow(ProgressInfo())
    internal val clientScope = CoroutineScope(Dispatchers.IO)
    internal var activeGenerationJob: kotlinx.coroutines.Job? = null

    private fun loadWorkflowFromAsset(context: Context): String {
        return context.assets.open("ernie_workflow.json").use { inputStream ->
            InputStreamReader(inputStream).readText()
        }
    }

    fun fetchSavedWorkflows(
        serverUrl: String,
        onResult: (List<String>) -> Unit
    ) {
        clientScope.launch {
            try {
                val cleanUrl = serverUrl.removeSuffix("/")
                val url = "$cleanUrl/userdata?dir=workflows"
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: "[]"
                        val listType = object : com.google.gson.reflect.TypeToken<List<String>>() {}.type
                        val list: List<String> = gson.fromJson(body, listType)
                        // Filter for json extension
                        onResult(list.filter { it.endsWith(".json", ignoreCase = true) })
                    } else {
                        onResult(emptyList())
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("ComfyClient", "Failed to fetch saved workflows")
                onResult(emptyList())
            }
        }
    }

    fun startGeneration(
        context: Context,
        prompt: String,
        settings: AppSettings,
        onSeedGenerated: (Long) -> Unit
    ) {
        activeGenerationJob?.cancel()
        webSocketRetryCount = 0
        activeGenerationJob = clientScope.launch {
            try {
                // Initialize state
                progressFlow.value = ProgressInfo(
                    state = GenerationState.ConnectingComfy,
                    statusText = "Initializing workflow...",
                    enhancedPrompt = progressFlow.value.enhancedPrompt
                )

                // 1. Determine the seed
                activeSeed = when (settings.seedMode) {
                    SeedMode.Fixed -> settings.fixedSeedValue
                    SeedMode.Custom -> settings.customSeedValue
                    SeedMode.LastUsed -> settings.lastUsedSeedValue
                    SeedMode.Random -> {
                        // Generate random positive seed within ComfyUI Seed range
                        Random.nextLong(0, 1125899906842624L)
                    }
                }
                onSeedGenerated(activeSeed)

                // 2. Load workflow JSON — fetch from server if a custom one is selected, else use bundled asset
                val workflowJsonString = if (settings.workflowToUse == "imported_workflow.json") {
                    AppLogger.d("ComfyClient", "Loading imported workflow locally from files storage")
                    context.openFileInput("imported_workflow.json").bufferedReader().use { it.readText() }
                } else if (settings.workflowToUse.isNotBlank()) {
                    val cleanUrl = settings.serverUrl.removeSuffix("/")
                    // ComfyUI's /userdata/{path} endpoint requires the slash in the subpath to be encoded as %2F
                    val encodedPath = java.net.URLEncoder.encode("workflows/${settings.workflowToUse}", "UTF-8").replace("+", "%20")
                    val fetchUrl = "$cleanUrl/userdata/$encodedPath"
                    AppLogger.d("ComfyClient", "Fetching workflow: $fetchUrl")
                    val request = Request.Builder().url(fetchUrl).build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            throw Exception("Failed to fetch workflow '${settings.workflowToUse}' (HTTP ${response.code})")
                        }
                        response.body?.string() ?: throw Exception("Fetched workflow is empty")
                    }
                } else {
                    loadWorkflowFromAsset(context)
                }

                var workflowObj = gson.fromJson(workflowJsonString, JsonObject::class.java)

                // If the workflow is in ComfyUI UI format (has a "nodes" array), convert it to API format
                val serverBase = settings.serverUrl.removeSuffix("/")
                val objectInfo: JsonObject = try {
                    val req = Request.Builder().url("$serverBase/object_info").build()
                    client.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) gson.fromJson(resp.body?.string() ?: "{}", JsonObject::class.java)
                        else JsonObject()
                    }
                } catch (e: Exception) { JsonObject() }

                var apiJson = workflowJsonString
                if (workflowObj.has("nodes")) {
                    AppLogger.d("ComfyClient", "Converting UI-format workflow to API format via server endpoint")
                    progressFlow.value = progressFlow.value.copy(
                        statusText = "Converting workflow on server..."
                    )
                    val conversionResult = convertWorkflowToApi(workflowJsonString, settings.serverUrl)
                    when (conversionResult) {
                        is ConversionResult.Success -> {
                            apiJson = conversionResult.apiJson
                        }
                        is ConversionResult.Error.MissingExtension -> {
                            throw Exception("Failed to convert UI workflow. The server is missing the 'Workflow to API Converter Endpoint' extension. Please install it via ComfyUI Manager or import an API-format JSON.")
                        }
                        is ConversionResult.Error.Generic -> {
                            throw Exception("Failed to convert UI workflow on server: ${conversionResult.message}")
                        }
                    }
                }

                val (promptJson, saveImageId) = WorkflowTransformer.transform(
                    apiJson,
                    prompt,
                    settings,
                    activeSeed,
                    objectInfo
                )
                activeSaveImageNodeId = saveImageId
                AppLogger.d("ComfyClient", "Using SaveImage node: $saveImageId")

                val strategy = when (settings.hostType) {
                    com.example.comfyprompt.data.HostType.LOCAL -> LocalStrategy
                    com.example.comfyprompt.data.HostType.COMFY_DEPLOY -> ComfyDeployStrategy
                    com.example.comfyprompt.data.HostType.RUNPOD -> RunPodStrategy
                    com.example.comfyprompt.data.HostType.FAL_AI -> FalAiStrategy
                }

                val result = strategy.generateImage(promptJson, settings)

                when (result) {
                    is GenerationResult.Success -> {
                        // For non-local, set completed and image path in UI. LocalStrategy handles this inside websocket updates.
                        if (settings.hostType != com.example.comfyprompt.data.HostType.LOCAL) {
                            progressFlow.value = progressFlow.value.copy(
                                state = GenerationState.Completed,
                                percent = 1f,
                                finalImage = result.imageUrl,
                                statusText = "Completed successfully."
                            )
                        }
                    }
                    is GenerationResult.Failure -> {
                        progressFlow.value = progressFlow.value.copy(
                            state = GenerationState.Failed,
                            statusText = result.errorMessage
                        )
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("ComfyClient", "Error during generation")
                progressFlow.value = ProgressInfo(
                    state = GenerationState.Failed,
                    statusText = "Connection error to ${settings.serverUrl}: ${e.localizedMessage}"
                )
            }
        }
    }

    internal suspend fun executeLocalGeneration(promptJson: String, settings: AppSettings) = withContext(Dispatchers.IO) {
        try {
            // Quick check before starting connection
            val pingResult = TriggerCmdClient.pollLocalServer(settings.serverUrl)
            if (pingResult != PingResult.ONLINE) {
                val detail = if (pingResult == PingResult.HOST_UNREACHABLE) {
                    "Host unreachable. Make sure you are on the same network."
                } else {
                    "ComfyUI server is offline."
                }
                throw java.net.ConnectException("Connection failed: $detail")
            }

            connectWebSocket(settings.serverUrl)

            val serverBaseUrl = settings.serverUrl.removeSuffix("/")
            val promptUrl = "$serverBaseUrl/prompt"

            val promptRequestBody = JsonObject().apply {
                val workflowObj = gson.fromJson(promptJson, JsonObject::class.java)
                add("prompt", workflowObj)
                addProperty("client_id", clientId)
            }

            val jsonBody = gson.toJson(promptRequestBody)
            AppLogger.d("ComfyClient", "Sending prompt: $jsonBody")

            val request = Request.Builder()
                .url(promptUrl)
                .post(jsonBody.toRequestBody(mediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    progressFlow.value = ProgressInfo(
                        state = GenerationState.Failed,
                        statusText = "Server rejected request: ${response.code}\n$responseBody"
                    )
                    AppLogger.e("ComfyClient", "Server error returned HTTP ${response.code}")
                    return@withContext
                }

                val responseObj = gson.fromJson(responseBody, JsonObject::class.java)
                currentPromptId = responseObj.get("prompt_id")?.asString
                
                AppLogger.d("ComfyClient", "POST /prompt returned: prompt_id=$currentPromptId, response=$responseBody")

                if (currentPromptId == null) {
                    val err = responseObj.get("error")?.asJsonObject
                    val errType = err?.get("type")?.asString ?: "Unknown"
                    val errMsg = err?.get("message")?.asString ?: "Server failed to return prompt ID"
                    val errDetails = err?.get("details")?.asString ?: ""
                    progressFlow.value = ProgressInfo(
                        state = GenerationState.Failed,
                        statusText = "Error: $errType - $errMsg\n$errDetails"
                    )
                    AppLogger.e("ComfyClient", "ComfyUI returned an execution error")
                }
            }
        } catch (e: Exception) {
            AppLogger.e("ComfyClient", "Exception during execution of local generation")
            progressFlow.value = ProgressInfo(
                state = GenerationState.Failed,
                statusText = "Connection error to ${settings.serverUrl}: ${e.localizedMessage}"
            )
        }
    }

    fun stopGeneration(settings: AppSettings) {
        activeGenerationJob?.cancel()
        activeGenerationJob = null

        clientScope.launch {
            try {
                if (settings.hostType == com.example.comfyprompt.data.HostType.LOCAL) {
                    val serverBaseUrl = settings.serverUrl.removeSuffix("/")
                    val interruptUrl = "$serverBaseUrl/interrupt"
                    val request = Request.Builder()
                        .url(interruptUrl)
                        .post("{}".toRequestBody(mediaType))
                        .build()
                    client.newCall(request).execute().use { }
                }

                progressFlow.value = progressFlow.value.copy(
                    state = GenerationState.Cancelled,
                    statusText = "Generation cancelled."
                )
            } catch (e: Exception) {
                AppLogger.e("ComfyClient", "Exception stopping generation")
            } finally {
                if (settings.hostType == com.example.comfyprompt.data.HostType.LOCAL) {
                    disconnectWebSocket()
                }
            }
        }
    }

    internal fun connectWebSocket(serverUrl: String) {
        disconnectWebSocket() // Ensure clean slate
        activeServerUrl = serverUrl

        val cleanUrl = serverUrl.removeSuffix("/").replace("http://", "ws://").replace("https://", "wss://")
        val wsUrl = "$cleanUrl/ws?clientId=$clientId"
        AppLogger.d("ComfyClient", "Attempting WebSocket connection to: $wsUrl")

        val request = Request.Builder().url(wsUrl).build()
        activeWebSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                AppLogger.d("ComfyClient", "WebSocket successfully connected to server!")
                webSocketRetryCount = 0
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                AppLogger.d("ComfyClient", "WebSocket closed: $code / $reason")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    AppLogger.d("ComfyClient", "WebSocket msg: $text")
                    val msgObj = gson.fromJson(text, JsonObject::class.java)
                    val type = msgObj.get("type")?.asString ?: return
                    val data = msgObj.getAsJsonObject("data") ?: return

                    val promptId = data.get("prompt_id")?.asString
                    // Ensure the WebSocket events belong to our active generation
                    if (promptId != null && currentPromptId != null && promptId != currentPromptId) {
                        AppLogger.d("ComfyClient", "Ignoring WebSocket message for old promptId: $promptId")
                        return
                    }

                    when (type) {
                        "status" -> {
                            val statusObj = data.getAsJsonObject("status")
                            if (statusObj != null && statusObj.has("exec_info")) {
                                val execInfo = statusObj.getAsJsonObject("exec_info")
                                if (execInfo.has("queue_remaining")) {
                                    val remaining = execInfo.get("queue_remaining").asInt
                                    val current = progressFlow.value
                                    if (current.state == GenerationState.ConnectingComfy || current.state == GenerationState.GeneratingBase) {
                                        if (remaining > 0) {
                                            progressFlow.value = current.copy(
                                                state = GenerationState.GeneratingBase,
                                                statusText = "Queued ($remaining remaining)..."
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        "executing" -> {
                            val node = data.get("node")?.let { if (it.isJsonNull) null else it.asString }
                            if (node == null) {
                                // If prompt is completed
                                if (progressFlow.value.state != GenerationState.Completed && 
                                    progressFlow.value.state != GenerationState.Cancelled && 
                                    progressFlow.value.state != GenerationState.Failed) {
                                    val current = progressFlow.value
                                    if (current.finalImage != null) {
                                        progressFlow.value = current.copy(
                                            state = GenerationState.Completed,
                                            percent = 1f,
                                            statusText = "Completed successfully."
                                        )
                                        disconnectWebSocket()
                                    }
                                }
                            } else {
                                handleNodeExecution(node)
                            }
                        }
                        "progress" -> {
                            val value = data.get("value").asInt
                            val max = data.get("max").asInt
                            val percent = value.toFloat() / max.toFloat()
                            val current = progressFlow.value
                            progressFlow.value = current.copy(
                                percent = percent,
                                statusText = current.statusText.substringBefore(" (")
                            )
                        }
                        "executed" -> {
                            val node = data.get("node").asString
                            val output = data.get("output")
                            if (output != null && output.isJsonObject) {
                                val outputObj = output.asJsonObject
                                val images = outputObj.getAsJsonArray("images")
                                if (images != null && images.size() > 0) {
                                    val filename = images[0].asJsonObject.get("filename").asString
                                    val imageUrl = "${serverUrl.removeSuffix("/")}/view?filename=$filename&type=output"
                                    
                                    val current = progressFlow.value
                                    
                                    // Update finalImage candidate dynamically for ANY executed image node
                                    progressFlow.value = current.copy(
                                        finalImage = imageUrl
                                    )
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.e("ComfyClient", "Exception handling WebSocket message")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                AppLogger.e("ComfyClient", "WebSocket connection failed", t)
                
                val current = progressFlow.value
                if (current.state != GenerationState.Completed && 
                    current.state != GenerationState.Cancelled &&
                    current.state != GenerationState.Failed &&
                    currentPromptId != null) {
                    
                    if (webSocketRetryCount < MAX_WEBSOCKET_RETRIES) {
                        webSocketRetryCount++
                        AppLogger.w("ComfyClient", "WebSocket disconnected. Reconnecting in 2s (attempt $webSocketRetryCount/$MAX_WEBSOCKET_RETRIES)...")
                        clientScope.launch {
                            kotlinx.coroutines.delay(2000)
                            activeServerUrl?.let { connectWebSocket(it) }
                        }
                        return
                    }
                }

                // Only show connection error if we aren't already completed/cancelled
                if (progressFlow.value.state != GenerationState.Completed && 
                    progressFlow.value.state != GenerationState.Cancelled) {
                    progressFlow.value = progressFlow.value.copy(
                        state = GenerationState.Failed,
                        statusText = "WebSocket connection failed: ${t.localizedMessage}"
                    )
                }
            }
        })
    }

    private fun handleNodeExecution(node: String) {
        val current = progressFlow.value
        val nodeName = when (node) {
            "777", "95" -> "Base Generation (ERNIE)"
            else -> "Executing Node $node"
        }
        progressFlow.value = current.copy(
            state = GenerationState.GeneratingBase,
            currentNode = node,
            statusText = nodeName
        )
    }

    internal fun disconnectWebSocket() {
        try {
            activeWebSocket?.close(1000, "Clean closure")
        } catch (e: Exception) {
            AppLogger.e("ComfyClient", "Exception disconnecting WebSocket")
        }
        activeWebSocket = null
    }

    suspend fun convertWorkflowToApi(uiJsonString: String, serverUrl: String): ConversionResult = withContext(Dispatchers.IO) {
        try {
            val dedicatedClient = client.newBuilder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                .build()

            val cleanUrl = serverUrl.removeSuffix("/")
            val url = "$cleanUrl/workflow/convert"
            val requestBody = uiJsonString.toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()
            
            dedicatedClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                if (response.code == 404) {
                    return@withContext ConversionResult.Error.MissingExtension
                }
                if (!response.isSuccessful) {
                    return@withContext ConversionResult.Error.Generic("Server error: ${response.code} ${response.message}\n$responseBody")
                }
                return@withContext ConversionResult.Success(responseBody)
            }
        } catch (e: Exception) {
            AppLogger.e("ComfyClient", "Exception converting workflow")
            return@withContext ConversionResult.Error.Generic(e.localizedMessage ?: "Unknown error occurred")
        }
    }
}
