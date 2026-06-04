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

                if (workflowObj.has("nodes")) {
                    AppLogger.d("ComfyClient", "Converting UI-format workflow to API format via server endpoint")
                    progressFlow.value = progressFlow.value.copy(
                        statusText = "Converting workflow on server..."
                    )
                    val conversionResult = convertWorkflowToApi(workflowJsonString, settings.serverUrl)
                    when (conversionResult) {
                        is ConversionResult.Success -> {
                            workflowObj = gson.fromJson(conversionResult.apiJson, JsonObject::class.java)
                        }
                        is ConversionResult.Error.MissingExtension -> {
                            throw Exception("Failed to convert UI workflow. The server is missing the 'Workflow to API Converter Endpoint' extension. Please install it via ComfyUI Manager or import an API-format JSON.")
                        }
                        is ConversionResult.Error.Generic -> {
                            throw Exception("Failed to convert UI workflow on server: ${conversionResult.message}")
                        }
                    }
                }

                // Strip any nodes whose class_type is not installed on this ComfyUI server.
                // This prevents 400 "missing_node_type" errors (e.g. 'Mute / Bypass Relay (rgthree)').
                if (objectInfo.size() > 0) {
                    val unknownNodeIds = mutableSetOf<String>()
                    workflowObj.entrySet().forEach { entry ->
                        val classType = entry.value.asJsonObject.get("class_type")?.asString
                        if (classType != null && !objectInfo.has(classType)) {
                            unknownNodeIds.add(entry.key)
                            AppLogger.w("ComfyClient", "Stripping unknown node ${entry.key} [$classType] — not installed on server")
                        }
                    }
                    // Remove the unknown nodes from the workflow
                    unknownNodeIds.forEach { workflowObj.remove(it) }

                    // Compute connectionTypes for stripping
                    val connectionTypes = mutableSetOf<String>("*")
                    objectInfo.entrySet().forEach { entry ->
                        val nodeDef = entry.value.asJsonObject
                        val outputs = nodeDef.getAsJsonArray("output")
                        outputs?.forEach { outEl ->
                            if (outEl.isJsonPrimitive && outEl.asJsonPrimitive.isString) {
                                connectionTypes.add(outEl.asString.uppercase())
                            }
                        }
                    }
                    connectionTypes.removeAll(setOf("INT", "FLOAT", "STRING", "BOOLEAN", "COMBO", "NUMBER", "INTEGER", "DOUBLE"))

                    fun isConn(typeEl: com.google.gson.JsonElement?): Boolean {
                        if (typeEl == null) return false
                        if (typeEl.isJsonPrimitive && typeEl.asJsonPrimitive.isString) {
                            return connectionTypes.contains(typeEl.asString.uppercase())
                        }
                        if (typeEl.isJsonArray && typeEl.asJsonArray.size() > 0) {
                            val first = typeEl.asJsonArray[0]
                            if (first.isJsonArray) return false
                            if (first.isJsonPrimitive && first.asJsonPrimitive.isString) {
                                return connectionTypes.contains(first.asString.uppercase())
                            }
                        }
                        return false
                    }

                    // Clean up any dangling link references to the stripped nodes in remaining inputs
                    if (unknownNodeIds.isNotEmpty()) {
                        workflowObj.entrySet().forEach { entry ->
                            val classType = entry.value.asJsonObject.get("class_type")?.asString ?: return@forEach
                            val inputs = entry.value.asJsonObject.getAsJsonObject("inputs") ?: return@forEach
                            
                            val nodeInfo = objectInfo.getAsJsonObject(classType)
                            val inputSection = nodeInfo?.getAsJsonObject("input")
                            val requiredInputs = inputSection?.getAsJsonObject("required")
                            val optionalInputs = inputSection?.getAsJsonObject("optional")

                            val keysToReplace = mutableMapOf<String, com.google.gson.JsonElement>()
                            val keysToRemove = mutableListOf<String>()

                            inputs.entrySet().forEach { inputEntry ->
                                val v = inputEntry.value
                                if (v.isJsonArray && v.asJsonArray.size() >= 1) {
                                    val srcId = v.asJsonArray[0].asString
                                    if (srcId in unknownNodeIds) {
                                        val inputName = inputEntry.key
                                        val inputConfig = requiredInputs?.get(inputName) ?: optionalInputs?.get(inputName)
                                        
                                        if (inputConfig != null && !isConn(inputConfig)) {
                                            // Widget input: fallback to default value
                                            var defaultVal: com.google.gson.JsonElement? = null
                                            if (inputConfig.isJsonArray && inputConfig.asJsonArray.size() >= 2) {
                                                val configObj = inputConfig.asJsonArray[1]
                                                if (configObj.isJsonObject && configObj.asJsonObject.has("default")) {
                                                    defaultVal = configObj.asJsonObject.get("default")
                                                }
                                            }
                                            if (defaultVal == null) {
                                                val firstEl = if (inputConfig.isJsonArray && inputConfig.asJsonArray.size() > 0) inputConfig.asJsonArray[0] else null
                                                val typeName = if (firstEl?.isJsonPrimitive == true && firstEl.asJsonPrimitive.isString) firstEl.asString.uppercase() else ""
                                                defaultVal = when (typeName) {
                                                    "INT", "INTEGER" -> com.google.gson.JsonPrimitive(0)
                                                    "FLOAT", "NUMBER", "DOUBLE" -> com.google.gson.JsonPrimitive(0.0)
                                                    "BOOLEAN" -> com.google.gson.JsonPrimitive(false)
                                                    else -> com.google.gson.JsonPrimitive("")
                                                }
                                            }
                                            keysToReplace[inputName] = defaultVal
                                        } else {
                                            // Connection input: remove since it's broken
                                            keysToRemove.add(inputName)
                                        }
                                    }
                                }
                            }
                            keysToReplace.forEach { (k, v) -> inputs.add(k, v) }
                            keysToRemove.forEach { inputs.remove(it) }
                        }
                    }
                }

                // Calculate resolution early so we can replace any link references
                val (width, height) = calculateDimensions(settings.megapixel, settings.aspectRatio)

                // Find FluxResolutionNode IDs (so we can remove them and replace their outputs)
                val fluxResNodeIds = mutableSetOf<String>()
                workflowObj.entrySet().forEach { entry ->
                    if (entry.value.asJsonObject.get("class_type")?.asString == "FluxResolutionNode") {
                        fluxResNodeIds.add(entry.key)
                    }
                }

                // Find the SaveImage node ID — prefer the one whose input comes from a VAEDecode
                // (i.e. the main output), not a comparison/preview node
                var saveImageId = "760" // Fallback
                val saveImageCandidates = mutableListOf<String>()
                workflowObj.entrySet().forEach { entry ->
                    val node = entry.value.asJsonObject
                    if (node.get("class_type")?.asString == "SaveImage") {
                        saveImageCandidates.add(entry.key)
                    }
                }
                // Pick the SaveImage whose input chain leads back to a VAEDecode
                if (saveImageCandidates.size == 1) {
                    saveImageId = saveImageCandidates[0]
                } else if (saveImageCandidates.isNotEmpty()) {
                    // Try to find one connected to VAEDecode
                    for (candId in saveImageCandidates) {
                        val candInputs = workflowObj.getAsJsonObject(candId)?.getAsJsonObject("inputs")
                        val imagesInput = candInputs?.get("images")
                        if (imagesInput?.isJsonArray == true) {
                            val srcId = imagesInput.asJsonArray[0].asString
                            val srcType = workflowObj.getAsJsonObject(srcId)?.get("class_type")?.asString
                            if (srcType == "VAEDecode") {
                                saveImageId = candId
                                break
                            }
                        }
                    }
                    // If none found from VAEDecode, just use the last candidate
                    if (saveImageId == "760") saveImageId = saveImageCandidates.last()
                }
                activeSaveImageNodeId = saveImageId
                AppLogger.d("ComfyClient", "Using SaveImage node: $saveImageId")

                // Inject prompt, seed, resolution into the API-format workflow
                var promptInjected = false
                workflowObj.entrySet().forEach { entry ->
                    val node = entry.value.asJsonObject
                    val classType = node.get("class_type")?.asString
                    val inputs = node.getAsJsonObject("inputs") ?: return@forEach

                    // A. Positive prompt injection
                    // Covers: CLIPTextEncode with direct text, or linked via primitive/string/DF_Text_Box node
                    if (classType == "CLIPTextEncode") {
                        val textVal = inputs.get("text")
                        if (textVal != null) {
                            val isPos = when {
                                textVal.isJsonPrimitive && textVal.asJsonPrimitive.isString -> {
                                    isPositivePrompt(textVal.asString)
                                }
                                else -> {
                                    val title = node.get("title")?.asString?.lowercase() ?: ""
                                    !title.contains("negative") && !title.contains("neg")
                                }
                            }
                            if (isPos) {
                                if (overrideTextNode(entry.key, workflowObj, prompt)) {
                                    promptInjected = true
                                }
                            }
                        }
                    }

                    // Primitive/String nodes that feed directly into CLIPTextEncode (including custom DF_Text_Box)
                    if (classType == "PrimitiveNode" || classType == "PrimitiveStringMultiline" ||
                        classType == "PrimitiveString" || classType == "StringNode" || classType == "DF_Text_Box") {
                        val valEl = inputs.get("value") ?: inputs.get("string") ?: inputs.get("text") ?: inputs.get("Text")
                        if (valEl?.isJsonPrimitive == true && valEl.asJsonPrimitive.isString) {
                            if (isPositivePrompt(valEl.asString)) {
                                when {
                                    inputs.has("value") -> { inputs.addProperty("value", prompt); promptInjected = true }
                                    inputs.has("string") -> { inputs.addProperty("string", prompt); promptInjected = true }
                                    inputs.has("text") -> { inputs.addProperty("text", prompt); promptInjected = true }
                                    inputs.has("Text") -> { inputs.addProperty("Text", prompt); promptInjected = true }
                                }
                                AppLogger.d("ComfyClient", "Injected prompt into primitive string node ${entry.key}")
                            }
                        }
                    }

                    // B. Seed injection — KSampler variants and dedicated seed nodes
                    if (classType != null && (classType.contains("KSampler") || classType.contains("Sampler"))) {
                        val seedVal = inputs.get("seed")
                        when {
                            seedVal == null -> { /* no seed input */ }
                            seedVal.isJsonPrimitive && seedVal.asJsonPrimitive.isNumber -> {
                                inputs.addProperty("seed", activeSeed)
                            }
                            seedVal.isJsonArray -> {
                                // Seed is linked — inject into the source node
                                val originId = seedVal.asJsonArray[0].asString
                                val originNode = workflowObj.getAsJsonObject(originId)
                                originNode?.getAsJsonObject("inputs")?.apply {
                                    if (has("seed")) addProperty("seed", activeSeed)
                                    else if (has("value")) addProperty("value", activeSeed)
                                }
                            }
                        }
                    }
                    // Dedicated seed nodes (rgthree Seed, etc.)
                    if (classType == "Seed (rgthree)" || classType == "Seed" || classType == "SeedNode") {
                        if (inputs.has("seed")) inputs.addProperty("seed", activeSeed)
                        else if (inputs.has("value")) inputs.addProperty("value", activeSeed)
                    }

                    // C. Resolution injection — replace link arrays pointing at FluxResolutionNode
                    // or directly set resolution on any Latent/Image generator node
                    val isLatentGenerator = classType != null && (
                        classType == "EmptyFlux2LatentImage" ||
                        classType == "EmptyLatentImage" ||
                        classType.contains("LatentImage") ||
                        classType.contains("EmptyLatent") ||
                        classType.startsWith("EmptySD3") ||
                        classType.startsWith("SD3LatentImage")
                    )

                    if (isLatentGenerator) {
                        inputs.addProperty("width", width)
                        inputs.addProperty("height", height)
                    } else {
                        // General fallback: if any node has width/height inputs linked to a FluxResolutionNode, replace them!
                        val wVal = inputs.get("width")
                        val hVal = inputs.get("height")
                        if (wVal?.isJsonArray == true) {
                            val srcId = wVal.asJsonArray[0].asString
                            if (srcId in fluxResNodeIds) {
                                inputs.addProperty("width", width)
                            }
                        }
                        if (hVal?.isJsonArray == true) {
                            val srcId = hVal.asJsonArray[0].asString
                            if (srcId in fluxResNodeIds) {
                                inputs.addProperty("height", height)
                            }
                        }
                    }
                }

                // Fallback: the Ernie-specific primitive text node used in bundled workflow
                if (!promptInjected) {
                    workflowObj.getAsJsonObject("757")?.getAsJsonObject("inputs")?.addProperty("value", prompt)
                }

                // Remove all FluxResolutionNode nodes — they're no longer needed since we injected values directly
                for (id in fluxResNodeIds) {
                    workflowObj.remove(id)
                    AppLogger.d("ComfyClient", "Removed FluxResolutionNode id=$id")
                }

                // 3. Serialise final workflow and execute chosen strategy!
                val promptJson = gson.toJson(workflowObj)

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

        val cleanUrl = serverUrl.removeSuffix("/").replace("http://", "ws://").replace("https://", "wss://")
        val wsUrl = "$cleanUrl/ws?clientId=$clientId"
        AppLogger.d("ComfyClient", "Attempting WebSocket connection to: $wsUrl")

        val request = Request.Builder().url(wsUrl).build()
        activeWebSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                AppLogger.d("ComfyClient", "WebSocket successfully connected to server!")
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
                                statusText = "${current.statusText.substringBefore(" (")} (${(percent * 100).toInt()}%)"
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
                AppLogger.e("ComfyClient", "WebSocket connection failed")
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

    private fun calculateDimensions(megapixel: String, aspectRatio: String, divisibleBy: Int = 16): Pair<Int, Int> {
        val mp = megapixel.toDoubleOrNull() ?: 1.0
        val totalPixels = mp * 1000000.0

        // Parse aspect ratio (e.g. "16:9 (Panorama)" or "3:4 (Golden Ratio)")
        val ratioStr = aspectRatio.substringBefore(" ").trim()
        val parts = ratioStr.split(":")
        val (wRatio, hRatio) = if (parts.size == 2) {
            Pair(parts[0].toDoubleOrNull() ?: 1.0, parts[1].toDoubleOrNull() ?: 1.0)
        } else {
            Pair(1.0, 1.0)
        }

        // Solve: w * h = totalPixels and w / h = ratio
        // w = h * ratio => h^2 * ratio = totalPixels => h = sqrt(totalPixels / ratio)
        val ratio = wRatio / hRatio
        val h = Math.sqrt(totalPixels / ratio)
        val w = h * ratio

        // Round to nearest multiple of divisibleBy
        val finalW = (Math.round(w / divisibleBy) * divisibleBy).toInt().coerceAtLeast(divisibleBy)
        val finalH = (Math.round(h / divisibleBy) * divisibleBy).toInt().coerceAtLeast(divisibleBy)

        return Pair(finalW, finalH)
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

    private fun isPositivePrompt(text: String): Boolean {
        val lower = text.lowercase()
        val negativeKeywords = listOf(
            "bad", "blurry", "lowres", "worst", "quality", "deformed", "watermark", 
            "logo", "text", "signature", "cropped", "ugly", "error", "mutilated", 
            "disfigured", "extra limbs", "bad anatomy", "nsfw"
        )
        if (text.isBlank()) return true
        for (kw in negativeKeywords) {
            if (lower.contains(kw)) return false
        }
        return true
    }

    private fun overrideTextNode(
        nodeId: String,
        workflowObj: JsonObject,
        prompt: String,
        visited: MutableSet<String> = mutableSetOf()
    ): Boolean {
        if (nodeId in visited) return false
        visited.add(nodeId)

        val node = workflowObj.getAsJsonObject(nodeId) ?: return false
        val inputs = node.getAsJsonObject("inputs") ?: return false

        // 1. If this is a direct text/string node, override its value if it looks like a positive prompt
        val textKeys = listOf("value", "string", "text", "Text")
        for (key in textKeys) {
            val valEl = inputs.get(key)
            if (valEl != null && valEl.isJsonPrimitive && valEl.asJsonPrimitive.isString) {
                val existing = valEl.asString
                if (isPositivePrompt(existing)) {
                    inputs.addProperty(key, prompt)
                    AppLogger.d("ComfyClient", "Recursively overrode direct string in node $nodeId key $key")
                    return true
                }
            }
        }

        // 2. Otherwise, follow linked inputs recursively, avoiding negative/clip/model paths
        var overrode = false
        inputs.entrySet().forEach { entry ->
            val key = entry.key
            val valEl = entry.value
            val lowerKey = key.lowercase()
            if (valEl.isJsonArray && valEl.asJsonArray.size() >= 1) {
                val shouldFollow = !lowerKey.contains("negative") && 
                                   !lowerKey.contains("neg") && 
                                   !lowerKey.contains("clip") && 
                                   !lowerKey.contains("model") && 
                                   !lowerKey.contains("vae") &&
                                   !lowerKey.contains("image") &&
                                   !lowerKey.contains("latent")
                if (shouldFollow) {
                    val originId = valEl.asJsonArray[0].asString
                    if (overrideTextNode(originId, workflowObj, prompt, visited)) {
                        overrode = true
                    }
                }
            }
        }
        return overrode
    }
}
