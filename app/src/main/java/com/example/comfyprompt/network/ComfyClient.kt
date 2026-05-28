package com.example.comfyprompt.network

import android.content.Context
import com.example.comfyprompt.data.AppSettings
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
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.InputStreamReader
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.random.Random

object ComfyClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.MINUTES) // Long timeout for large upscales
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    private var activeWebSocket: WebSocket? = null
    private val clientId = UUID.randomUUID().toString()
    private var currentPromptId: String? = null
    private var activeSeed: Long = 42L
    private var activeSaveImageNodeId: String = "760"

    val progressFlow = MutableStateFlow(ProgressInfo())
    private val clientScope = CoroutineScope(Dispatchers.IO)

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
                e.printStackTrace()
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
        clientScope.launch {
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
                val workflowJsonString = if (settings.workflowToUse.isNotBlank()) {
                    val cleanUrl = settings.serverUrl.removeSuffix("/")
                    // ComfyUI's /userdata/{path} endpoint requires the slash in the subpath to be encoded as %2F
                    val encodedPath = java.net.URLEncoder.encode("workflows/${settings.workflowToUse}", "UTF-8").replace("+", "%20")
                    val fetchUrl = "$cleanUrl/userdata/$encodedPath"
                    android.util.Log.d("ComfyClient", "Fetching workflow: $fetchUrl")
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
                    android.util.Log.d("ComfyClient", "Converting UI-format workflow to API format")
                    workflowObj = convertUiFormatToApi(workflowObj, objectInfo)
                }

                // Strip any nodes whose class_type is not installed on this ComfyUI server.
                // This prevents 400 "missing_node_type" errors (e.g. 'Mute / Bypass Relay (rgthree)').
                if (objectInfo.size() > 0) {
                    val unknownNodeIds = mutableSetOf<String>()
                    workflowObj.entrySet().forEach { entry ->
                        val classType = entry.value.asJsonObject.get("class_type")?.asString
                        if (classType != null && !objectInfo.has(classType)) {
                            unknownNodeIds.add(entry.key)
                            android.util.Log.w("ComfyClient", "Stripping unknown node ${entry.key} [$classType] — not installed on server")
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
                android.util.Log.d("ComfyClient", "Using SaveImage node: $saveImageId")

                // Inject prompt, seed, resolution into the API-format workflow
                var promptInjected = false
                workflowObj.entrySet().forEach { entry ->
                    val node = entry.value.asJsonObject
                    val classType = node.get("class_type")?.asString
                    val inputs = node.getAsJsonObject("inputs") ?: return@forEach

                    // A. Positive prompt injection
                    // Covers: CLIPTextEncode with direct text, or linked via primitive/string node
                    if (classType == "CLIPTextEncode") {
                        val textVal = inputs.get("text")
                        when {
                            textVal == null -> { /* skip */ }
                            textVal.isJsonPrimitive && textVal.asJsonPrimitive.isString -> {
                                // Direct string value — inject if it looks like a prompt (not blank/negative)
                                // We inject into ALL CLIPTextEncode nodes with non-empty text
                                // (negative prompts are usually empty or very short)
                                val existing = textVal.asString
                                if (existing.isNotBlank()) {
                                    inputs.addProperty("text", prompt)
                                    promptInjected = true
                                    android.util.Log.d("ComfyClient", "Injected prompt into CLIPTextEncode ${entry.key}")
                                }
                            }
                            textVal.isJsonArray -> {
                                // Linked from another node — follow the link
                                val originId = textVal.asJsonArray[0].asString
                                val originNode = workflowObj.getAsJsonObject(originId)
                                originNode?.getAsJsonObject("inputs")?.apply {
                                    when {
                                        has("value") -> { addProperty("value", prompt); promptInjected = true }
                                        has("string") -> { addProperty("string", prompt); promptInjected = true }
                                        has("text") -> { addProperty("text", prompt); promptInjected = true }
                                    }
                                    android.util.Log.d("ComfyClient", "Injected prompt via linked node $originId")
                                }
                            }
                        }
                    }

                    // Primitive/String nodes that feed directly into CLIPTextEncode
                    if (classType == "PrimitiveNode" || classType == "PrimitiveStringMultiline" ||
                        classType == "PrimitiveString" || classType == "StringNode") {
                        val valEl = inputs.get("value") ?: inputs.get("string") ?: inputs.get("text")
                        if (valEl?.isJsonPrimitive == true && valEl.asJsonPrimitive.isString && valEl.asString.isNotBlank()) {
                            when {
                                inputs.has("value") -> { inputs.addProperty("value", prompt); promptInjected = true }
                                inputs.has("string") -> { inputs.addProperty("string", prompt); promptInjected = true }
                                inputs.has("text") -> { inputs.addProperty("text", prompt); promptInjected = true }
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
                    android.util.Log.d("ComfyClient", "Removed FluxResolutionNode id=$id")
                }

                // 3. Connect to WebSocket first to ensure we receive early execution events
                connectWebSocket(settings.serverUrl)

                // 4. Submit prompt request
                val serverBaseUrl = settings.serverUrl.removeSuffix("/")
                val promptUrl = "$serverBaseUrl/prompt"

                val promptRequestBody = JsonObject().apply {
                    add("prompt", workflowObj)
                    addProperty("client_id", clientId)
                }

                val jsonBody = gson.toJson(promptRequestBody)
                android.util.Log.d("ComfyClient", "Sending prompt: $jsonBody")

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
                        android.util.Log.e("ComfyClient", "Server error ${response.code}: $responseBody")
                        return@launch
                    }

                    val responseObj = gson.fromJson(responseBody, JsonObject::class.java)
                    currentPromptId = responseObj.get("prompt_id")?.asString

                    if (currentPromptId == null) {
                        progressFlow.value = ProgressInfo(
                            state = GenerationState.Failed,
                            statusText = "Server failed to return prompt ID"
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                progressFlow.value = ProgressInfo(
                    state = GenerationState.Failed,
                    statusText = "Connection error: ${e.localizedMessage}"
                )
            }
        }
    }

    fun stopGeneration(settings: AppSettings) {
        clientScope.launch {
            try {
                val serverBaseUrl = settings.serverUrl.removeSuffix("/")
                val interruptUrl = "$serverBaseUrl/interrupt"
                val request = Request.Builder()
                    .url(interruptUrl)
                    .post("{}".toRequestBody(mediaType))
                    .build()
                client.newCall(request).execute().use { }

                progressFlow.value = progressFlow.value.copy(
                    state = GenerationState.Cancelled,
                    statusText = "Generation cancelled."
                )
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                disconnectWebSocket()
            }
        }
    }

    private fun connectWebSocket(serverUrl: String) {
        disconnectWebSocket() // Ensure clean slate

        val cleanUrl = serverUrl.removeSuffix("/").replace("http://", "ws://").replace("https://", "wss://")
        val wsUrl = "$cleanUrl/ws?clientId=$clientId"

        val request = Request.Builder().url(wsUrl).build()
        activeWebSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val msgObj = gson.fromJson(text, JsonObject::class.java)
                    val type = msgObj.get("type")?.asString ?: return
                    val data = msgObj.getAsJsonObject("data") ?: return

                    val promptId = data.get("prompt_id")?.asString
                    // Ensure the WebSocket events belong to our active generation
                    if (promptId != null && promptId != currentPromptId) return

                    when (type) {
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
                                    
                                    if (node == activeSaveImageNodeId) {
                                        progressFlow.value = progressFlow.value.copy(
                                            state = GenerationState.Completed,
                                            percent = 1f,
                                            statusText = "Completed successfully."
                                        )
                                        disconnectWebSocket()
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                t.printStackTrace()
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

    private fun disconnectWebSocket() {
        try {
            activeWebSocket?.close(1000, "Clean closure")
        } catch (e: Exception) {
            e.printStackTrace()
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

    /**
     * Converts a ComfyUI UI-format workflow (has "nodes" + "links" arrays) into
     * the API-format (flat map of nodeId -> {class_type, inputs}) expected by /prompt.
     *
     * In UI format:
     *   - nodes[].inputs is a List of {name, link?, ...} for LINKED inputs
     *   - widget-only inputs are NOT in nodes[].inputs; their values are in widgets_values
     *   - links is a List of [linkId, srcNodeId, srcSlot, dstNodeId, dstSlot, type]
     *
     * In API format:
     *   - Each linked input becomes [srcNodeId_string, srcSlot]
     *   - Each widget input stores its value directly
     *
     * We use objectInfo (from /object_info) to know the ORDERED list of all inputs per node type,
     * so we can correctly assign widgets_values to the right input names.
     */
    private fun convertUiFormatToApi(uiWorkflow: JsonObject, objectInfo: JsonObject): JsonObject {
        val nodes = uiWorkflow.getAsJsonArray("nodes") ?: return uiWorkflow
        val links = uiWorkflow.getAsJsonArray("links") ?: JsonArray()

        // Build link lookup: linkId -> [srcNodeId_string, srcSlot]
        val linkMap = mutableMapOf<Int, JsonArray>()
        links.forEach { linkEl ->
            val link = linkEl.asJsonArray
            if (link.size() >= 6) {
                val linkId = link[0].asInt
                val srcId = link[1].asString
                val srcSlot = link[2].asInt
                linkMap[linkId] = JsonArray().apply { add(srcId); add(srcSlot) }
            }
        }

        // Identify Reroute nodes to bridge them
        val rerouteMap = mutableMapOf<String, JsonArray?>()
        nodes.forEach { nodeEl ->
            val node = nodeEl.asJsonObject
            val nodeType = node.get("type")?.asString ?: ""
            if (nodeType == "Reroute" || nodeType.contains("Reroute")) {
                val id = node.get("id").asString
                val inputs = node.getAsJsonArray("inputs")
                val inputLink = if (inputs != null && inputs.size() > 0) {
                    val inp = inputs[0].asJsonObject
                    if (inp.has("link") && !inp.get("link").isJsonNull) linkMap[inp.get("link").asInt] else null
                } else null
                rerouteMap[id] = inputLink
            }
        }

        fun resolveSource(source: JsonArray): JsonArray {
            var current = source
            var depth = 0
            while (depth < 20) {
                val srcId = current[0].asString
                if (rerouteMap.containsKey(srcId)) {
                    val upStream = rerouteMap[srcId]
                    if (upStream != null) {
                        current = upStream
                        depth++
                        continue
                    }
                }
                break
            }
            return current
        }

        // Dynamically determine connection types by looking at all node outputs
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
        // Remove primitive types that act as widgets
        connectionTypes.removeAll(setOf("INT", "FLOAT", "STRING", "BOOLEAN", "COMBO", "NUMBER", "INTEGER", "DOUBLE"))

        fun isConnType(typeEl: com.google.gson.JsonElement?): Boolean {
            if (typeEl == null) return false
            if (typeEl.isJsonPrimitive && typeEl.asJsonPrimitive.isString) {
                return connectionTypes.contains(typeEl.asString.uppercase())
            }
            if (typeEl.isJsonArray) {
                val arr = typeEl.asJsonArray
                if (arr.size() > 0) {
                    val first = arr[0]
                    if (first.isJsonArray) return false // COMBO list
                    if (first.isJsonPrimitive && first.asJsonPrimitive.isString) {
                        return connectionTypes.contains(first.asString.uppercase())
                    }
                }
            }
            return false
        }

        val apiFormat = JsonObject()

        nodes.forEach { nodeEl ->
            val node = nodeEl.asJsonObject
            val nodeId = node.get("id")?.asString ?: return@forEach
            val nodeType = node.get("type")?.asString ?: return@forEach

            // Skip display/routing nodes with no server implementation
            if (nodeType == "Note" || nodeType == "Reroute" || nodeType.contains("Reroute")) return@forEach

            val rawInputs = node.get("inputs")
            val widgetsValues = node.getAsJsonArray("widgets_values")

            val nodeInfo = objectInfo.getAsJsonObject(nodeType)
            val inputSection = nodeInfo?.getAsJsonObject("input")
            val requiredInputs = inputSection?.getAsJsonObject("required")
            val optionalInputs = inputSection?.getAsJsonObject("optional")
            val orderedInputNames = mutableListOf<String>().apply {
                requiredInputs?.keySet()?.forEach { add(it) }
                optionalInputs?.keySet()?.forEach { add(it) }
            }

            val linkedInputs = mutableMapOf<String, JsonArray>()
            if (rawInputs?.isJsonArray == true) {
                rawInputs.asJsonArray.forEach { inpEl ->
                    if (!inpEl.isJsonObject) return@forEach
                    val inp = inpEl.asJsonObject
                    val name = inp.get("name")?.asString ?: return@forEach
                    val linkId = if (inp.has("link") && !inp.get("link").isJsonNull)
                        inp.get("link")?.asInt else null
                    if (linkId != null && linkMap.containsKey(linkId)) {
                        linkedInputs[name] = resolveSource(linkMap[linkId]!!)
                    }
                }
            }

            val apiInputs = JsonObject()
            var widgetIdx = 0

            if (orderedInputNames.isNotEmpty()) {
                for (inputName in orderedInputNames) {
                    val inputConfig = requiredInputs?.get(inputName) ?: optionalInputs?.get(inputName)
                    val isConn = isConnType(inputConfig)

                    if (isConn) {
                        // Connection types are mapped from links only
                        if (linkedInputs.containsKey(inputName)) {
                            apiInputs.add(inputName, linkedInputs[inputName]!!.deepCopy())
                        }
                    } else {
                        // Widget types are mapped from widgetsValues or links, but ALWAYS consume widgetsValues position
                        if (linkedInputs.containsKey(inputName)) {
                            apiInputs.add(inputName, linkedInputs[inputName]!!.deepCopy())
                            if (widgetsValues != null && widgetIdx < widgetsValues.size()) widgetIdx++
                        } else if (widgetsValues != null && widgetIdx < widgetsValues.size()) {
                            apiInputs.add(inputName, widgetsValues[widgetIdx])
                            widgetIdx++
                        }

                        // Handle frontend-only control_after_generate widget for seeds
                        if ((inputName == "seed" || inputName == "noise_seed") && widgetsValues != null && widgetIdx < widgetsValues.size()) {
                            val nextVal = widgetsValues[widgetIdx]
                            if (nextVal.isJsonPrimitive && nextVal.asJsonPrimitive.isString) {
                                val s = nextVal.asString
                                if (s in setOf("fixed", "randomize", "increment", "decrement")) {
                                    widgetIdx++
                                }
                            }
                        }
                    }
                }
            } else {
                // Fallback for completely unknown nodes
                if (rawInputs?.isJsonArray == true) {
                    rawInputs.asJsonArray.forEach { inpEl ->
                        if (!inpEl.isJsonObject) return@forEach
                        val inp = inpEl.asJsonObject
                        val name = inp.get("name")?.asString ?: return@forEach
                        if (linkedInputs.containsKey(name)) {
                            apiInputs.add(name, linkedInputs[name]!!.deepCopy())
                            if (widgetsValues != null && widgetIdx < widgetsValues.size()) widgetIdx++
                        } else if (widgetsValues != null && widgetIdx < widgetsValues.size()) {
                            apiInputs.add(name, widgetsValues[widgetIdx])
                            widgetIdx++
                        }
                    }
                }
            }

            apiFormat.add(nodeId, JsonObject().apply {
                addProperty("class_type", nodeType)
                add("inputs", apiInputs)
            })
        }
        return apiFormat
    }
}
