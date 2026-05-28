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
            android.util.Log.d("GeminiClient", "$provider API key is blank. Bypassing enhancer.")
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
                            android.util.Log.e("GeminiClient", "ChatGPT API failed with code: ${response.code}, body: $bodyString")
                            return@withContext userPrompt
                        }
                        val jsonObject = gson.fromJson(bodyString, JsonObject::class.java)
                        val choices = jsonObject.getAsJsonArray("choices")
                        if (choices != null && choices.size() > 0) {
                            val message = choices[0].asJsonObject.getAsJsonObject("message")
                            if (message != null) {
                                val enhancedText = message.get("content").asString.trim()
                                if (enhancedText.isNotEmpty()) {
                                    android.util.Log.d("GeminiClient", "ChatGPT enhanced prompt: $enhancedText")
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
                            android.util.Log.e("GeminiClient", "Grok API failed with code: ${response.code}, body: $bodyString")
                            return@withContext userPrompt
                        }
                        val jsonObject = gson.fromJson(bodyString, JsonObject::class.java)
                        val choices = jsonObject.getAsJsonArray("choices")
                        if (choices != null && choices.size() > 0) {
                            val message = choices[0].asJsonObject.getAsJsonObject("message")
                            if (message != null) {
                                val enhancedText = message.get("content").asString.trim()
                                if (enhancedText.isNotEmpty()) {
                                    android.util.Log.d("GeminiClient", "Grok enhanced prompt: $enhancedText")
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
                            android.util.Log.e("GeminiClient", "Local LLM API failed with code: ${response.code}, body: $bodyString")
                            return@withContext userPrompt
                        }
                        val jsonObject = gson.fromJson(bodyString, JsonObject::class.java)
                        val choices = jsonObject.getAsJsonArray("choices")
                        if (choices != null && choices.size() > 0) {
                            val message = choices[0].asJsonObject.getAsJsonObject("message")
                            if (message != null) {
                                val enhancedText = message.get("content").asString.trim()
                                if (enhancedText.isNotEmpty()) {
                                    android.util.Log.d("GeminiClient", "Local LLM enhanced prompt: $enhancedText")
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
                            android.util.Log.e("GeminiClient", "Claude API failed with code: ${response.code}, body: $bodyString")
                            return@withContext userPrompt
                        }
                        val jsonObject = gson.fromJson(bodyString, JsonObject::class.java)
                        val content = jsonObject.getAsJsonArray("content")
                        if (content != null && content.size() > 0) {
                            val textObj = content[0].asJsonObject
                            if (textObj != null) {
                                val enhancedText = textObj.get("text").asString.trim()
                                if (enhancedText.isNotEmpty()) {
                                    android.util.Log.d("GeminiClient", "Claude enhanced prompt: $enhancedText")
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
                    android.util.Log.d("GeminiClient", "Starting prompt enhancement with model: $cleanModel (mapped from: ${settings.geminiModel})")

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
                            android.util.Log.e("GeminiClient", "Gemini API failed with code: ${response.code}, body: $bodyString")
                            return@withContext userPrompt
                        }
                        
                        android.util.Log.d("GeminiClient", "Gemini API response successful: $bodyString")
                        val jsonObject = gson.fromJson(bodyString, JsonObject::class.java)
                        val candidates = jsonObject.getAsJsonArray("candidates")
                        if (candidates != null && candidates.size() > 0) {
                            val content = candidates[0].asJsonObject.getAsJsonObject("content")
                            val parts = content.getAsJsonArray("parts")
                            if (parts != null && parts.size() > 0) {
                                val enhancedText = parts[0].asJsonObject.get("text").asString.trim()
                                if (enhancedText.isNotEmpty()) {
                                    android.util.Log.d("GeminiClient", "Successfully enhanced prompt: $enhancedText")
                                    return@withContext enhancedText
                                }
                            }
                        }
                        userPrompt
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("GeminiClient", "Exception during prompt enhancement", e)
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
                    android.util.Log.e("GeminiClient", "fetchLocalModels failed: HTTP ${response.code}")
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
            android.util.Log.e("GeminiClient", "Exception during fetchLocalModels", e)
            emptyList()
        }
    }
}
