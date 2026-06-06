package com.example.comfyprompt.network

import com.example.comfyprompt.data.AppSettings
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

object GeminiClient {
    private val client = OkHttpClient()
    private val gson = Gson()
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun enhancePrompt(
        userPrompt: String,
        settings: AppSettings
    ): String = withContext(Dispatchers.IO) {
        val provider = settings.apiProvider
        val apiKey = when (provider) {
            "ChatGPT" -> settings.chatgptApiKey
            "Claude" -> settings.claudeApiKey
            "Grok" -> settings.grokApiKey
            "Local / Custom" -> "dummy"
            else -> settings.geminiApiKey
        }

        if (provider != "Local / Custom" && apiKey.isBlank()) {
            AppLogger.d("GeminiClient", "$provider API key is blank. Bypassing enhancer.")
            return@withContext userPrompt // Fallback to raw prompt if no API key is set
        }

        val systemInstruction = "You are a professional prompt enhancer specializing in the ERNIE-Image DiT diffusion model. " +
                "Your task is to take a simple user prompt and expand it into a highly detailed visual prompt optimized for ERNIE, " +
                "adhering to the following 5-part formula: [Subject] + [Scene/Context] + [Medium/Style] + [Lighting/Mood] + [Quality/Composition].\n\n" +
                "Follow these strict guidelines:\n" +
                "1. **Explicit Medium Wording**: Clearly specify the type of medium (e.g., 'cinematic DSLR photograph', 'oil painting style', 'digital concept art', 'pencil sketch illustration', 'macro product photography') to guide the model's aesthetic structure.\n" +
                "2. **Handle Text Rendering**: If the prompt requests text, wrap the target text in double quotation marks (e.g., 'with the text \"Hello World\" written on it') so ERNIE can render it legibly.\n" +
                "3. **Spatial Layout**: Use explicit spatial terms (e.g., 'centered', 'left third', 'extreme close-up', 'wide-angle perspective') for clean layout composition.\n" +
                "4. **Format**: Output ONLY the enhanced prompt block. Do NOT include markdown formatting, quotes, or conversational explanations."

        try {
            when (provider) {
                "ChatGPT" -> {
                    val url = "https://api.openai.com/v1/chat/completions"
                    val jsonRequest = mapOf(
                        "model" to settings.chatgptModel,
                        "messages" to listOf(
                            mapOf("role" to "system", "content" to systemInstruction),
                            mapOf("role" to "user", "content" to userPrompt)
                        )
                    )
                    val requestBody = gson.toJson(jsonRequest).toRequestBody(mediaType)
                    val request = Request.Builder()
                        .url(url)
                        .post(requestBody)
                        .addHeader("Authorization", "Bearer $apiKey")
                        .addHeader("Content-Type", "application/json")
                        .build()

                    client.newCall(request).execute().use { response ->
                        val bodyString = response.body?.string() ?: ""
                        if (!response.isSuccessful) {
                            AppLogger.e("GeminiClient", "ChatGPT API request failed with HTTP ${response.code}")
                            return@withContext userPrompt
                        }
                        val jsonObject = gson.fromJson(bodyString, JsonObject::class.java)
                        val choices = jsonObject.getAsJsonArray("choices")
                        if (choices != null && choices.size() > 0) {
                            val message = choices[0].asJsonObject.getAsJsonObject("message")
                            if (message != null) {
                                val enhancedText = message.get("content").asString.trim()
                                if (enhancedText.isNotEmpty()) {
                                    AppLogger.d("GeminiClient", "ChatGPT enhanced prompt: $enhancedText")
                                    return@withContext enhancedText
                                }
                            }
                        }
                        userPrompt
                    }
                }
                "Grok" -> {
                    val url = "https://api.x.ai/v1/chat/completions"
                    val jsonRequest = mapOf(
                        "model" to settings.grokModel,
                        "messages" to listOf(
                            mapOf("role" to "system", "content" to systemInstruction),
                            mapOf("role" to "user", "content" to userPrompt)
                        )
                    )
                    val requestBody = gson.toJson(jsonRequest).toRequestBody(mediaType)
                    val request = Request.Builder()
                        .url(url)
                        .post(requestBody)
                        .addHeader("Authorization", "Bearer $apiKey")
                        .addHeader("Content-Type", "application/json")
                        .build()

                    client.newCall(request).execute().use { response ->
                        val bodyString = response.body?.string() ?: ""
                        if (!response.isSuccessful) {
                            AppLogger.e("GeminiClient", "Grok API request failed with HTTP ${response.code}")
                            return@withContext userPrompt
                        }
                        val jsonObject = gson.fromJson(bodyString, JsonObject::class.java)
                        val choices = jsonObject.getAsJsonArray("choices")
                        if (choices != null && choices.size() > 0) {
                            val message = choices[0].asJsonObject.getAsJsonObject("message")
                            if (message != null) {
                                val enhancedText = message.get("content").asString.trim()
                                if (enhancedText.isNotEmpty()) {
                                    AppLogger.d("GeminiClient", "Grok enhanced prompt: $enhancedText")
                                    return@withContext enhancedText
                                }
                            }
                        }
                        userPrompt
                    }
                }
                "Local / Custom" -> {
                    val url = "${settings.localLlmBaseUrl.removeSuffix("/")}/chat/completions"
                    val jsonRequest = mapOf(
                        "model" to settings.localLlmSelectedModel,
                        "messages" to listOf(
                            mapOf("role" to "system", "content" to systemInstruction),
                            mapOf("role" to "user", "content" to userPrompt)
                        )
                    )
                    val requestBody = gson.toJson(jsonRequest).toRequestBody(mediaType)
                    val request = Request.Builder()
                        .url(url)
                        .post(requestBody)
                        .addHeader("Authorization", "Bearer dummy")
                        .addHeader("Content-Type", "application/json")
                        .build()

                    client.newCall(request).execute().use { response ->
                        val bodyString = response.body?.string() ?: ""
                        if (!response.isSuccessful) {
                            AppLogger.e("GeminiClient", "Local LLM API request failed with HTTP ${response.code}")
                            return@withContext userPrompt
                        }
                        val jsonObject = gson.fromJson(bodyString, JsonObject::class.java)
                        val choices = jsonObject.getAsJsonArray("choices")
                        if (choices != null && choices.size() > 0) {
                            val message = choices[0].asJsonObject.getAsJsonObject("message")
                            if (message != null) {
                                val enhancedText = message.get("content").asString.trim()
                                if (enhancedText.isNotEmpty()) {
                                    AppLogger.d("GeminiClient", "Local LLM enhanced prompt: $enhancedText")
                                    return@withContext enhancedText
                                }
                            }
                        }
                        userPrompt
                    }
                }
                "Claude" -> {
                    val url = "https://api.anthropic.com/v1/messages"
                    val jsonRequest = mapOf(
                        "model" to settings.claudeModel,
                        "max_tokens" to 1024,
                        "system" to systemInstruction,
                        "messages" to listOf(
                            mapOf("role" to "user", "content" to userPrompt)
                        )
                    )
                    val requestBody = gson.toJson(jsonRequest).toRequestBody(mediaType)
                    val request = Request.Builder()
                        .url(url)
                        .post(requestBody)
                        .addHeader("x-api-key", apiKey)
                        .addHeader("anthropic-version", "2023-06-01")
                        .addHeader("content-type", "application/json")
                        .build()

                    client.newCall(request).execute().use { response ->
                        val bodyString = response.body?.string() ?: ""
                        if (!response.isSuccessful) {
                            AppLogger.e("GeminiClient", "Claude API request failed with HTTP ${response.code}")
                            return@withContext userPrompt
                        }
                        val jsonObject = gson.fromJson(bodyString, JsonObject::class.java)
                        val content = jsonObject.getAsJsonArray("content")
                        if (content != null && content.size() > 0) {
                            val textObj = content[0].asJsonObject
                            if (textObj != null) {
                                val enhancedText = textObj.get("text").asString.trim()
                                if (enhancedText.isNotEmpty()) {
                                    AppLogger.d("GeminiClient", "Claude enhanced prompt: $enhancedText")
                                    return@withContext enhancedText
                                }
                            }
                        }
                        userPrompt
                    }
                }
                else -> {
                    // Default to Gemini
                    val mappedModel = when (settings.geminiModel.trim()) {
                        "gemini-flash-lite-3.1", "gemini flash lite 3.1" -> "gemini-3.1-flash-lite"
                        "gemini-flash-3.5", "gemini flash 3.5" -> "gemini-3.5-flash"
                        else -> settings.geminiModel
                    }
                    val cleanModel = if (mappedModel.contains("/")) mappedModel else "models/$mappedModel"
                    val url = "https://generativelanguage.googleapis.com/v1beta/$cleanModel:generateContent?key=$apiKey"
                    AppLogger.d("GeminiClient", "Starting prompt enhancement with model: $cleanModel (mapped from: ${settings.geminiModel})")

                    val jsonRequest = mapOf(
                        "contents" to listOf(
                            mapOf(
                                "parts" to listOf(
                                    mapOf("text" to userPrompt)
                                )
                            )
                        ),
                        "systemInstruction" to mapOf(
                            "parts" to listOf(
                                mapOf("text" to systemInstruction)
                            )
                        )
                    )

                    val jsonRequestBodyString = gson.toJson(jsonRequest)
                    val requestBody = jsonRequestBodyString.toRequestBody(mediaType)
                    val request = Request.Builder()
                        .url(url)
                        .post(requestBody)
                        .build()

                    client.newCall(request).execute().use { response ->
                        val bodyString = response.body?.string() ?: ""
                        if (!response.isSuccessful) {
                            AppLogger.e("GeminiClient", "Gemini API request failed with HTTP ${response.code}")
                            return@withContext userPrompt
                        }
                        
                        AppLogger.d("GeminiClient", "Gemini API response successful: $bodyString")
                        val jsonObject = gson.fromJson(bodyString, JsonObject::class.java)
                        val candidates = jsonObject.getAsJsonArray("candidates")
                        if (candidates != null && candidates.size() > 0) {
                            val content = candidates[0].asJsonObject.getAsJsonObject("content")
                            val parts = content.getAsJsonArray("parts")
                            if (parts != null && parts.size() > 0) {
                                val enhancedText = parts[0].asJsonObject.get("text").asString.trim()
                                if (enhancedText.isNotEmpty()) {
                                    AppLogger.d("GeminiClient", "Successfully enhanced prompt: $enhancedText")
                                    return@withContext enhancedText
                                }
                            }
                        }
                        userPrompt
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.e("GeminiClient", "Prompt enhancement failed due to an exception", e)
            userPrompt
        }
    }

    suspend fun fetchLocalModels(baseUrl: String): List<String> = withContext(Dispatchers.IO) {
        if (baseUrl.isBlank()) return@withContext emptyList()
        val url = "${baseUrl.removeSuffix("/")}/models"
        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("Authorization", "Bearer dummy")
            .build()
            
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    AppLogger.e("GeminiClient", "Failed to fetch local models with HTTP ${response.code}")
                    return@withContext emptyList()
                }
                val bodyString = response.body?.string() ?: return@withContext emptyList()
                val jsonObject = gson.fromJson(bodyString, JsonObject::class.java)
                val dataArray = jsonObject.getAsJsonArray("data") ?: return@withContext emptyList()
                
                val models = mutableListOf<String>()
                for (element in dataArray) {
                    val modelObj = element.asJsonObject
                    val id = modelObj.get("id")?.asString
                    if (!id.isNullOrBlank()) {
                        models.add(id)
                    }
                }
                models
            }
        } catch (e: Exception) {
            AppLogger.e("GeminiClient", "Failed to fetch local models due to an exception", e)
            emptyList()
        }
    }

    suspend fun chatWithCopilot(
        messages: List<com.example.comfyprompt.data.ChatMessage>,
        base64Images: Map<String, String>,
        settings: AppSettings
    ): String = withContext(Dispatchers.IO) {
        val apiKey = settings.geminiApiKey
        if (apiKey.isBlank()) {
            return@withContext "Error: Gemini API key is missing. Please set it in Settings."
        }

        val systemInstruction = "You are an expert AI Co-Pilot prompt refiner for ComfyUI. " +
                "The user will provide a generated image, and optionally other images, and describe what they want to change. " +
                "Your job is to discuss with them and then suggest a new, modified prompt.\n\n" +
                "Guidelines:\n" +
                "1. Speak in a helpful, conversational, and direct tone. Keep responses relatively concise (2-4 sentences max).\n" +
                "2. Look at the generated image and any attached images to understand the user's intent.\n" +
                "3. At the end of your response, always output a single, separate line starting exactly with 'REFINED_PROMPT: ' followed by your recommended updated prompt string. For example:\n" +
                "REFINED_PROMPT: A beautiful digital painting of a mystical castle, centered, dramatic sunset lighting, high detail\n\n" +
                "Do not put quotes or backticks around the prompt. Keep the prompt string clean so it can be copied directly."

        val cleanModel = "models/gemini-3.5-flash"
        val url = "https://generativelanguage.googleapis.com/v1beta/$cleanModel:generateContent?key=$apiKey"

        val contentsList = messages.map { chatMsg ->
            val parts = mutableListOf<Map<String, Any>>()
            
            chatMsg.imageUrls.forEach { imgUrl ->
                val base64 = base64Images[imgUrl]
                if (base64 != null) {
                    val mimeType = if (imgUrl.contains("webp", ignoreCase = true)) "image/webp" 
                                   else if (imgUrl.contains("jpg", ignoreCase = true) || imgUrl.contains("jpeg", ignoreCase = true)) "image/jpeg"
                                   else "image/png"
                    parts.add(mapOf(
                        "inlineData" to mapOf(
                            "mimeType" to mimeType,
                            "data" to base64
                        )
                    ))
                }
            }
            
            parts.add(mapOf("text" to chatMsg.text))
            
            mapOf(
                "role" to if (chatMsg.sender == com.example.comfyprompt.data.MessageSender.USER) "user" else "model",
                "parts" to parts
            )
        }

        val jsonRequest = mapOf(
            "contents" to contentsList,
            "systemInstruction" to mapOf(
                "parts" to listOf(
                    mapOf("text" to systemInstruction)
                )
            )
        )

        try {
            val jsonRequestBodyString = gson.toJson(jsonRequest)
            val requestBody = jsonRequestBodyString.toRequestBody(mediaType)
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    AppLogger.e("GeminiClient", "Copilot API request failed with HTTP ${response.code}: $bodyString")
                    return@withContext "Error: API request failed with HTTP ${response.code}."
                }
                
                val jsonObject = gson.fromJson(bodyString, JsonObject::class.java)
                val candidates = jsonObject.getAsJsonArray("candidates")
                if (candidates != null && candidates.size() > 0) {
                    val content = candidates[0].asJsonObject.getAsJsonObject("content")
                    val parts = content.getAsJsonArray("parts")
                    if (parts != null && parts.size() > 0) {
                        return@withContext parts[0].asJsonObject.get("text").asString.trim()
                    }
                }
                "Error: No response generated by AI."
            }
        } catch (e: Exception) {
            AppLogger.e("GeminiClient", "Copilot chat request failed: ${e.message}")
            "Error: ${e.localizedMessage ?: "Unknown error"}"
        }
    }
}
