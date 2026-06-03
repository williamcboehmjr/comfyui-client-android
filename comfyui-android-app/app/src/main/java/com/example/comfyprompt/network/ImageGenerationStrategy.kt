package com.example.comfyprompt.network

import com.example.comfyprompt.data.AppSettings
import com.example.comfyprompt.data.GenerationState
import com.example.comfyprompt.data.ProgressInfo
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

interface ImageGenerationStrategy {
    suspend fun generateImage(promptJson: String, settings: AppSettings): GenerationResult
}

sealed class GenerationResult {
    data class Success(val imageUrl: String) : GenerationResult()
    data class Failure(val errorMessage: String) : GenerationResult()
}

object LocalStrategy : ImageGenerationStrategy {
    override suspend fun generateImage(promptJson: String, settings: AppSettings): GenerationResult {
        val deferred = kotlinx.coroutines.CompletableDeferred<GenerationResult>()
        
        val job = ComfyClient.clientScope.launch {
            ComfyClient.progressFlow.collect { progress ->
                when (progress.state) {
                    GenerationState.Completed -> {
                        if (progress.finalImage != null) {
                            deferred.complete(GenerationResult.Success(progress.finalImage))
                        } else {
                            deferred.complete(GenerationResult.Failure("Generation completed but no image URL found"))
                        }
                    }
                    GenerationState.Failed -> {
                        deferred.complete(GenerationResult.Failure(progress.statusText))
                    }
                    GenerationState.Cancelled -> {
                        deferred.complete(GenerationResult.Failure("Generation was cancelled"))
                    }
                    else -> {}
                }
            }
        }
        
        try {
            ComfyClient.executeLocalGeneration(promptJson, settings)
            return deferred.await()
        } catch (e: Exception) {
            return GenerationResult.Failure("Local generation failed: ${e.localizedMessage}")
        } finally {
            job.cancel()
        }
    }
}

object ComfyDeployStrategy : ImageGenerationStrategy {
    override suspend fun generateImage(promptJson: String, settings: AppSettings): GenerationResult = withContext<GenerationResult>(Dispatchers.IO) {
        val apiKey = settings.comfyDeployApiKey
        if (apiKey.isBlank()) {
            return@withContext GenerationResult.Failure("ComfyDeploy API Key is required. Please set it in Settings.")
        }
        val deploymentId = settings.comfyDeployId
        if (deploymentId.isBlank()) {
            return@withContext GenerationResult.Failure("ComfyDeploy Deployment ID is required. Please set it in Settings.")
        }

        // Build Inputs
        val inputsJson = JsonObject()
        try {
            val workflowObj = ComfyClient.gson.fromJson(promptJson, JsonObject::class.java)
            var promptText = ""
            var seedVal: Long? = null
            var widthVal: Int? = null
            var heightVal: Int? = null
            
            workflowObj.entrySet().forEach { entry ->
                val node = entry.value.asJsonObject
                val classType = node.get("class_type")?.asString
                val inputs = node.getAsJsonObject("inputs")
                if (inputs != null) {
                    if (classType == "CLIPTextEncode") {
                        val txt = inputs.get("text")?.asString
                        if (!txt.isNullOrBlank()) {
                            promptText = txt
                        }
                    }
                    if (classType != null && (classType.contains("KSampler") || classType.contains("Sampler"))) {
                        inputs.get("seed")?.asLong?.let { seedVal = it }
                    }
                    if (classType == "EmptyLatentImage" || classType == "EmptyFlux2LatentImage") {
                        inputs.get("width")?.asInt?.let { widthVal = it }
                        inputs.get("height")?.asInt?.let { heightVal = it }
                    }
                }
            }
            inputsJson.addProperty("prompt", promptText)
            inputsJson.addProperty("input_prompt", promptText)
            inputsJson.addProperty("positive", promptText)
            seedVal?.let { inputsJson.addProperty("seed", it) }
            widthVal?.let { inputsJson.addProperty("width", it) }
            heightVal?.let { inputsJson.addProperty("height", it) }
            inputsJson.add("workflow", workflowObj)
        } catch (e: Exception) {
            inputsJson.addProperty("prompt", promptJson)
        }

        val payload = JsonObject().apply {
            addProperty("deployment_id", deploymentId)
            add("inputs", inputsJson)
        }

        val requestBody = ComfyClient.gson.toJson(payload).toRequestBody(ComfyClient.mediaType)
        val request = Request.Builder()
            .url("https://api.comfydeploy.com/api/run/deployment/queue")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(requestBody)
            .build()

        try {
            ComfyClient.progressFlow.value = ComfyClient.progressFlow.value.copy(
                state = GenerationState.GeneratingBase,
                statusText = "ComfyDeploy: Queueing generation..."
            )

            val response = ComfyClient.client.newCall(request).execute()
            val code = response.code
            val responseBody = response.body?.string() ?: ""
            response.close()

            if (code == 401) {
                return@withContext GenerationResult.Failure("ComfyDeploy Unauthorized: Invalid API key (HTTP 401)")
            }
            if (code in 500..599) {
                return@withContext GenerationResult.Failure("ComfyDeploy Server Error (HTTP $code): $responseBody")
            }
            if (!response.isSuccessful) {
                return@withContext GenerationResult.Failure("ComfyDeploy failed to queue (HTTP $code): $responseBody")
            }

            val responseObj = ComfyClient.gson.fromJson(responseBody, JsonObject::class.java)
            val runId = responseObj.get("run_id")?.asString ?: responseObj.get("id")?.asString
                ?: return@withContext GenerationResult.Failure("ComfyDeploy failed to return run ID")

            // Polling Loop
            var pollResult: GenerationResult? = null
            while (pollResult == null) {
                delay(2000)
                val pollRequest = Request.Builder()
                    .url("https://api.comfydeploy.com/api/run/$runId")
                    .header("Authorization", "Bearer $apiKey")
                    .build()

                val pollResponse = ComfyClient.client.newCall(pollRequest).execute()
                val pollCode = pollResponse.code
                val pollBody = pollResponse.body?.string() ?: ""
                pollResponse.close()

                if (pollCode == 401) {
                    pollResult = GenerationResult.Failure("ComfyDeploy Unauthorized: Invalid API key during polling (HTTP 401)")
                    break
                }
                if (pollCode in 500..599) {
                    pollResult = GenerationResult.Failure("ComfyDeploy Server Error during polling (HTTP $pollCode)")
                    break
                }
                if (!pollResponse.isSuccessful) {
                    pollResult = GenerationResult.Failure("ComfyDeploy polling failed (HTTP $pollCode): $pollBody")
                    break
                }

                val pollObj = ComfyClient.gson.fromJson(pollBody, JsonObject::class.java)
                val status = pollObj.get("status")?.asString?.lowercase() ?: "unknown"

                ComfyClient.progressFlow.value = ComfyClient.progressFlow.value.copy(
                    state = GenerationState.GeneratingBase,
                    statusText = "ComfyDeploy status: $status"
                )

                if (status == "success" || status == "completed" || status == "complete") {
                    val imageUrl = findImageUrl(pollObj)
                    pollResult = if (imageUrl != null) {
                        GenerationResult.Success(imageUrl)
                    } else {
                        GenerationResult.Failure("ComfyDeploy succeeded but output image URL not found in response")
                    }
                } else if (status == "failed" || status == "error") {
                    pollResult = GenerationResult.Failure("ComfyDeploy generation failed (status: $status)")
                }
            }
            pollResult ?: GenerationResult.Failure("Polling ended unexpectedly")
        } catch (e: Exception) {
            GenerationResult.Failure("ComfyDeploy network error: ${e.localizedMessage}")
        }
    }
}

object RunPodStrategy : ImageGenerationStrategy {
    override suspend fun generateImage(promptJson: String, settings: AppSettings): GenerationResult = withContext<GenerationResult>(Dispatchers.IO) {
        val apiKey = settings.runpodApiKey
        if (apiKey.isBlank()) {
            return@withContext GenerationResult.Failure("RunPod API Key is required. Please set it in Settings.")
        }
        val endpointId = settings.runpodEndpointId
        if (endpointId.isBlank()) {
            return@withContext GenerationResult.Failure("RunPod Endpoint ID is required. Please set it in Settings.")
        }

        val workflowObj = try {
            ComfyClient.gson.fromJson(promptJson, JsonObject::class.java)
        } catch (e: Exception) {
            return@withContext GenerationResult.Failure("Failed to parse workflow JSON: ${e.localizedMessage}")
        }

        val payload = JsonObject().apply {
            val inputObj = JsonObject().apply {
                add("workflow", workflowObj)
            }
            add("input", inputObj)
        }

        val requestBody = ComfyClient.gson.toJson(payload).toRequestBody(ComfyClient.mediaType)
        val request = Request.Builder()
            .url("https://api.runpod.ai/v2/$endpointId/run")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(requestBody)
            .build()

        try {
            ComfyClient.progressFlow.value = ComfyClient.progressFlow.value.copy(
                state = GenerationState.GeneratingBase,
                statusText = "RunPod: Queueing generation..."
            )

            val response = ComfyClient.client.newCall(request).execute()
            val code = response.code
            val responseBody = response.body?.string() ?: ""
            response.close()

            if (code == 401) {
                return@withContext GenerationResult.Failure("RunPod Unauthorized: Invalid API key (HTTP 401)")
            }
            if (code in 500..599) {
                return@withContext GenerationResult.Failure("RunPod Server Error (HTTP $code): $responseBody")
            }
            if (!response.isSuccessful) {
                return@withContext GenerationResult.Failure("RunPod failed to start (HTTP $code): $responseBody")
            }

            val responseObj = ComfyClient.gson.fromJson(responseBody, JsonObject::class.java)
            val jobId = responseObj.get("id")?.asString
                ?: return@withContext GenerationResult.Failure("RunPod failed to return job ID")

            // Polling Loop
            var pollResult: GenerationResult? = null
            while (pollResult == null) {
                delay(2000)
                val pollRequest = Request.Builder()
                    .url("https://api.runpod.ai/v2/$endpointId/status/$jobId")
                    .header("Authorization", "Bearer $apiKey")
                    .build()

                val pollResponse = ComfyClient.client.newCall(pollRequest).execute()
                val pollCode = pollResponse.code
                val pollBody = pollResponse.body?.string() ?: ""
                pollResponse.close()

                if (pollCode == 401) {
                    pollResult = GenerationResult.Failure("RunPod Unauthorized: Invalid API key during polling (HTTP 401)")
                    break
                }
                if (pollCode in 500..599) {
                    pollResult = GenerationResult.Failure("RunPod Server Error during polling (HTTP $pollCode)")
                    break
                }
                if (!pollResponse.isSuccessful) {
                    pollResult = GenerationResult.Failure("RunPod polling failed (HTTP $pollCode): $pollBody")
                    break
                }

                val pollObj = ComfyClient.gson.fromJson(pollBody, JsonObject::class.java)
                val status = pollObj.get("status")?.asString ?: "UNKNOWN"

                ComfyClient.progressFlow.value = ComfyClient.progressFlow.value.copy(
                    state = GenerationState.GeneratingBase,
                    statusText = "RunPod status: $status"
                )

                if (status == "COMPLETED") {
                    val imageUrl = findImageUrl(pollObj)
                    pollResult = if (imageUrl != null) {
                        GenerationResult.Success(imageUrl)
                    } else {
                        GenerationResult.Failure("RunPod completed but no output image URL found in response")
                    }
                } else if (status == "FAILED") {
                    val errorDetail = pollObj.get("error")?.asString ?: "Unknown error"
                    pollResult = GenerationResult.Failure("RunPod generation failed: $errorDetail")
                }
            }
            pollResult ?: GenerationResult.Failure("Polling ended unexpectedly")
        } catch (e: Exception) {
            GenerationResult.Failure("RunPod network error: ${e.localizedMessage}")
        }
    }
}

object FalAiStrategy : ImageGenerationStrategy {
    override suspend fun generateImage(promptJson: String, settings: AppSettings): GenerationResult = withContext<GenerationResult>(Dispatchers.IO) {
        val apiKey = settings.falAiApiKey
        if (apiKey.isBlank()) {
            return@withContext GenerationResult.Failure("Fal.ai API Key is required. Please set it in Settings.")
        }
        val endpointSlug = settings.falAiEndpointSlug
        if (endpointSlug.isBlank()) {
            return@withContext GenerationResult.Failure("Fal.ai Endpoint Slug is required. Please set it in Settings.")
        }

        // Build Payload
        val payloadJson = JsonObject()
        try {
            val workflowObj = ComfyClient.gson.fromJson(promptJson, JsonObject::class.java)
            var promptText = ""
            var seedVal: Long? = null
            var widthVal: Int? = null
            var heightVal: Int? = null
            
            workflowObj.entrySet().forEach { entry ->
                val node = entry.value.asJsonObject
                val classType = node.get("class_type")?.asString
                val inputs = node.getAsJsonObject("inputs")
                if (inputs != null) {
                    if (classType == "CLIPTextEncode") {
                        val txt = inputs.get("text")?.asString
                        if (!txt.isNullOrBlank()) {
                            promptText = txt
                        }
                    }
                    if (classType != null && (classType.contains("KSampler") || classType.contains("Sampler"))) {
                        inputs.get("seed")?.asLong?.let { seedVal = it }
                    }
                    if (classType == "EmptyLatentImage" || classType == "EmptyFlux2LatentImage") {
                        inputs.get("width")?.asInt?.let { widthVal = it }
                        inputs.get("height")?.asInt?.let { heightVal = it }
                    }
                }
            }
            
            payloadJson.addProperty("prompt", promptText)
            seedVal?.let { payloadJson.addProperty("seed", it) }
            widthVal?.let { payloadJson.addProperty("width", it) }
            heightVal?.let { payloadJson.addProperty("height", it) }
            payloadJson.add("workflow", workflowObj)
            payloadJson.add("load_generator", workflowObj)
        } catch (e: Exception) {
            payloadJson.addProperty("prompt", promptJson)
        }

        val requestBody = ComfyClient.gson.toJson(payloadJson).toRequestBody(ComfyClient.mediaType)
        val request = Request.Builder()
            .url("https://queue.fal.run/$endpointSlug")
            .header("Authorization", "Key $apiKey")
            .header("Content-Type", "application/json")
            .post(requestBody)
            .build()

        try {
            ComfyClient.progressFlow.value = ComfyClient.progressFlow.value.copy(
                state = GenerationState.GeneratingBase,
                statusText = "Fal.ai: Queueing generation..."
            )

            val response = ComfyClient.client.newCall(request).execute()
            val code = response.code
            val responseBody = response.body?.string() ?: ""
            response.close()

            if (code == 401) {
                return@withContext GenerationResult.Failure("Fal.ai Unauthorized: Invalid API key (HTTP 401)")
            }
            if (code in 500..599) {
                return@withContext GenerationResult.Failure("Fal.ai Server Error (HTTP $code): $responseBody")
            }
            if (!response.isSuccessful) {
                return@withContext GenerationResult.Failure("Fal.ai failed to start (HTTP $code): $responseBody")
            }

            val responseObj = ComfyClient.gson.fromJson(responseBody, JsonObject::class.java)
            
            val statusUrl = responseObj.get("status_url")?.asString
                ?: responseObj.get("response_url")?.asString
                ?: responseObj.get("request_id")?.asString?.let { "https://queue.fal.run/$endpointSlug/requests/$it" }
                ?: return@withContext GenerationResult.Failure("Fal.ai failed to return status URL or request ID")

            // Polling Loop
            var pollResult: GenerationResult? = null
            while (pollResult == null) {
                delay(2000)
                val pollRequest = Request.Builder()
                    .url(statusUrl)
                    .header("Authorization", "Key $apiKey")
                    .build()

                val pollResponse = ComfyClient.client.newCall(pollRequest).execute()
                val pollCode = pollResponse.code
                val pollBody = pollResponse.body?.string() ?: ""
                pollResponse.close()

                if (pollCode == 401) {
                    pollResult = GenerationResult.Failure("Fal.ai Unauthorized: Invalid API key during polling (HTTP 401)")
                    break
                }
                if (pollCode in 500..599) {
                    pollResult = GenerationResult.Failure("Fal.ai Server Error during polling (HTTP $pollCode)")
                    break
                }
                if (!pollResponse.isSuccessful) {
                    pollResult = GenerationResult.Failure("Fal.ai polling failed (HTTP $pollCode): $pollBody")
                    break
                }

                val pollObj = ComfyClient.gson.fromJson(pollBody, JsonObject::class.java)
                val status = pollObj.get("status")?.asString?.lowercase() ?: "completed"

                ComfyClient.progressFlow.value = ComfyClient.progressFlow.value.copy(
                    state = GenerationState.GeneratingBase,
                    statusText = "Fal.ai status: $status"
                )

                if (status == "in_queue" || status == "in_progress") {
                    continue
                }

                if (pollObj.has("error")) {
                    val errMsg = pollObj.get("error")?.asString ?: "Fal.ai generation failed"
                    pollResult = GenerationResult.Failure("Fal.ai generation failed: $errMsg")
                    break
                }

                val imageUrl = findImageUrl(pollObj)
                pollResult = if (imageUrl != null) {
                    GenerationResult.Success(imageUrl)
                } else {
                    GenerationResult.Failure("Fal.ai completed but output image URL not found in response")
                }
            }
            pollResult ?: GenerationResult.Failure("Polling ended unexpectedly")
        } catch (e: Exception) {
            GenerationResult.Failure("Fal.ai network error: ${e.localizedMessage}")
        }
    }
}

internal fun findImageUrl(element: JsonElement): String? {
    if (element.isJsonPrimitive) {
        val str = element.asString
        if (str.startsWith("http") && (
            str.contains(".png", ignoreCase = true) || 
            str.contains(".jpg", ignoreCase = true) || 
            str.contains(".jpeg", ignoreCase = true) || 
            str.contains(".webp", ignoreCase = true) ||
            str.contains("image", ignoreCase = true)
        )) {
            return str
        }
    } else if (element.isJsonObject) {
        val obj = element.asJsonObject
        val keys = listOf("url", "imageUrl", "image", "file", "download_url", "uri")
        for (key in keys) {
            if (obj.has(key)) {
                val found = findImageUrl(obj.get(key))
                if (found != null) return found
            }
        }
        for (entry in obj.entrySet()) {
            val found = findImageUrl(entry.value)
            if (found != null) return found
        }
    } else if (element.isJsonArray) {
        val arr = element.asJsonArray
        for (item in arr) {
            val found = findImageUrl(item)
            if (found != null) return found
        }
    }
    return null
}
