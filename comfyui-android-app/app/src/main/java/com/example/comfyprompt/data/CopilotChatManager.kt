package com.example.comfyprompt.data

import android.content.Context
import android.net.Uri
import com.example.comfyprompt.network.AppLogger
import com.example.comfyprompt.network.GeminiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class CopilotChatManager {

    private val _copilotMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val copilotMessages: StateFlow<List<ChatMessage>> = _copilotMessages.asStateFlow()

    private val _isCopilotLoading = MutableStateFlow(false)
    val isCopilotLoading: StateFlow<Boolean> = _isCopilotLoading.asStateFlow()

    private val copilotImageBase64Cache = mutableMapOf<String, String>()

    fun initCopilotChat() {
        if (_copilotMessages.value.isEmpty()) {
            _copilotMessages.value = listOf(
                ChatMessage(
                    sender = MessageSender.AI,
                    text = "Hi! I'm your Prompt Refiner Assistant. I've analyzed your generated image. Tell me what changes you'd like to make, or upload reference images for context, and I'll help you refine the prompt!"
                )
            )
        }
    }

    fun clearCopilotChat() {
        _copilotMessages.value = emptyList()
        copilotImageBase64Cache.clear()
    }

    suspend fun sendCopilotMessage(
        context: Context,
        text: String,
        generatedImageUrl: String,
        attachedUris: List<Uri>,
        settings: AppSettings
    ) {
        if (text.isBlank() && attachedUris.isEmpty()) return

        _isCopilotLoading.value = true

        val imageUrls = mutableListOf<String>()

        val isFirstUserTurn = _copilotMessages.value.none { it.sender == MessageSender.USER }
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

        val userMessage = ChatMessage(
            sender = MessageSender.USER,
            text = text,
            imageUrls = imageUrls
        )

        _copilotMessages.value = _copilotMessages.value + userMessage

        val allMessages = _copilotMessages.value
        val responseText = GeminiClient.chatWithCopilot(allMessages, copilotImageBase64Cache, settings)

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

        val aiMessage = ChatMessage(
            sender = MessageSender.AI,
            text = conversationalText,
            refinedPrompt = refinedPrompt
        )

        _copilotMessages.value = _copilotMessages.value + aiMessage
        _isCopilotLoading.value = false
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
            AppLogger.e("CopilotChatManager", "Failed to download image for base64: ${e.message}")
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
            AppLogger.e("CopilotChatManager", "Failed to convert URI to base64: ${e.message}")
            null
        }
    }
}
