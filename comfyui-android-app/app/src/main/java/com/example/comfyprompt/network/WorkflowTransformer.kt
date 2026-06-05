package com.example.comfyprompt.network

import com.example.comfyprompt.data.AppSettings
import com.example.comfyprompt.data.SeedMode
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import kotlin.math.round
import kotlin.math.sqrt

object WorkflowTransformer {
    private val gson = Gson()

    internal fun isPositivePrompt(text: String): Boolean {
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

    internal fun calculateDimensions(megapixel: String, aspectRatio: String, divisibleBy: Int = 16): Pair<Int, Int> {
        val mp = megapixel.toDoubleOrNull() ?: 1.0
        val totalPixels = mp * 1000000.0

        val ratioStr = aspectRatio.substringBefore(" ").trim()
        val parts = ratioStr.split(":")
        val (wRatio, hRatio) = if (parts.size == 2) {
            Pair(parts[0].toDoubleOrNull() ?: 1.0, parts[1].toDoubleOrNull() ?: 1.0)
        } else {
            Pair(1.0, 1.0)
        }

        val ratio = wRatio / hRatio
        val h = sqrt(totalPixels / ratio)
        val w = h * ratio

        val finalW = (round(w / divisibleBy) * divisibleBy).toInt().coerceAtLeast(divisibleBy)
        val finalH = (round(h / divisibleBy) * divisibleBy).toInt().coerceAtLeast(divisibleBy)

        return Pair(finalW, finalH)
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

        val textKeys = listOf("value", "string", "text", "Text")
        for (key in textKeys) {
            val valEl = inputs.get(key)
            if (valEl != null && valEl.isJsonPrimitive && valEl.asJsonPrimitive.isString) {
                val existing = valEl.asString
                if (isPositivePrompt(existing)) {
                    inputs.addProperty(key, prompt)
                    AppLogger.d("WorkflowTransformer", "Recursively overrode direct string in node $nodeId key $key")
                    return true
                }
            }
        }

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

    fun transform(
        rawWorkflowJson: String,
        prompt: String,
        settings: AppSettings,
        activeSeed: Long,
        objectInfo: JsonObject
    ): Pair<String, String> { // Returns: Pair<TransformedJsonString, SaveImageNodeId>
        var workflowObj = gson.fromJson(rawWorkflowJson, JsonObject::class.java)

        // 1. Strip any nodes whose class_type is not installed on this ComfyUI server.
        if (objectInfo.size() > 0) {
            val unknownNodeIds = mutableSetOf<String>()
            workflowObj.entrySet().forEach { entry ->
                val classType = entry.value.asJsonObject.get("class_type")?.asString
                if (classType != null && !objectInfo.has(classType)) {
                    unknownNodeIds.add(entry.key)
                    AppLogger.w("WorkflowTransformer", "Stripping unknown node ${entry.key} [$classType] — not installed on server")
                }
            }
            unknownNodeIds.forEach { workflowObj.remove(it) }

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

            fun isConn(typeEl: JsonElement?): Boolean {
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

            if (unknownNodeIds.isNotEmpty()) {
                workflowObj.entrySet().forEach { entry ->
                    val classType = entry.value.asJsonObject.get("class_type")?.asString ?: return@forEach
                    val inputs = entry.value.asJsonObject.getAsJsonObject("inputs") ?: return@forEach
                    
                    val nodeInfo = objectInfo.getAsJsonObject(classType)
                    val inputSection = nodeInfo?.getAsJsonObject("input")
                    val requiredInputs = inputSection?.getAsJsonObject("required")
                    val optionalInputs = inputSection?.getAsJsonObject("optional")

                    val keysToReplace = mutableMapOf<String, JsonElement>()
                    val keysToRemove = mutableListOf<String>()

                    inputs.entrySet().forEach { inputEntry ->
                        val v = inputEntry.value
                        if (v.isJsonArray && v.asJsonArray.size() >= 1) {
                            val srcId = v.asJsonArray[0].asString
                            if (srcId in unknownNodeIds) {
                                val inputName = inputEntry.key
                                val inputConfig = requiredInputs?.get(inputName) ?: optionalInputs?.get(inputName)
                                
                                if (inputConfig != null && !isConn(inputConfig)) {
                                    var defaultVal: JsonElement? = null
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
                                            "INT", "INTEGER" -> JsonPrimitive(0)
                                            "FLOAT", "NUMBER", "DOUBLE" -> JsonPrimitive(0.0)
                                            "BOOLEAN" -> JsonPrimitive(false)
                                            else -> JsonPrimitive("")
                                        }
                                    }
                                    keysToReplace[inputName] = defaultVal
                                } else {
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

        // 2. Calculate dimensions
        val (width, height) = calculateDimensions(settings.megapixel, settings.aspectRatio)

        // 3. Find FluxResolutionNode IDs
        val fluxResNodeIds = mutableSetOf<String>()
        workflowObj.entrySet().forEach { entry ->
            if (entry.value.asJsonObject.get("class_type")?.asString == "FluxResolutionNode") {
                fluxResNodeIds.add(entry.key)
            }
        }

        // 4. Find the SaveImage node ID
        var saveImageId = "760"
        val saveImageCandidates = mutableListOf<String>()
        workflowObj.entrySet().forEach { entry ->
            val node = entry.value.asJsonObject
            if (node.get("class_type")?.asString == "SaveImage") {
                saveImageCandidates.add(entry.key)
            }
        }
        if (saveImageCandidates.size == 1) {
            saveImageId = saveImageCandidates[0]
        } else if (saveImageCandidates.isNotEmpty()) {
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
            if (saveImageId == "760") saveImageId = saveImageCandidates.last()
        }

        // 5. Inject parameters
        var promptInjected = false
        workflowObj.entrySet().forEach { entry ->
            val node = entry.value.asJsonObject
            val classType = node.get("class_type")?.asString
            val inputs = node.getAsJsonObject("inputs") ?: return@forEach

            // Prompt injection
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
                        AppLogger.d("WorkflowTransformer", "Injected prompt into primitive string node ${entry.key}")
                    }
                }
            }

            // Seed injection
            if (classType != null && (classType.contains("KSampler") || classType.contains("Sampler"))) {
                val seedVal = inputs.get("seed")
                when {
                    seedVal == null -> {}
                    seedVal.isJsonPrimitive && seedVal.asJsonPrimitive.isNumber -> {
                        inputs.addProperty("seed", activeSeed)
                    }
                    seedVal.isJsonArray -> {
                        val originId = seedVal.asJsonArray[0].asString
                        val originNode = workflowObj.getAsJsonObject(originId)
                        originNode?.getAsJsonObject("inputs")?.apply {
                            if (has("seed")) addProperty("seed", activeSeed)
                            else if (has("value")) addProperty("value", activeSeed)
                        }
                    }
                }
            }
            if (classType == "Seed (rgthree)" || classType == "Seed" || classType == "SeedNode") {
                if (inputs.has("seed")) inputs.addProperty("seed", activeSeed)
                else if (inputs.has("value")) inputs.addProperty("value", activeSeed)
            }

            // Resolution injection
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

        if (!promptInjected) {
            workflowObj.getAsJsonObject("757")?.getAsJsonObject("inputs")?.addProperty("value", prompt)
        }

        for (id in fluxResNodeIds) {
            workflowObj.remove(id)
            AppLogger.d("WorkflowTransformer", "Removed FluxResolutionNode id=$id")
        }

        return Pair(gson.toJson(workflowObj), saveImageId)
    }
}
