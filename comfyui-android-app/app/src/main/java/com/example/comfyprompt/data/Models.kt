package com.example.comfyprompt.data

enum class SeedMode {
    Random, Fixed, LastUsed, Custom
}

enum class HostType {
    LOCAL, COMFY_DEPLOY, RUNPOD, FAL_AI
}

enum class GenerationState {
    Idle, 
    EnhancingPrompt, 
    ConnectingComfy, 
    GeneratingBase, 
    UpscalingSeedVR, 
    RefiningSD15, 
    Completed, 
    Failed, 
    Cancelled
}

sealed class ServerWakeState {
    object Idle : ServerWakeState()
    object Waking : ServerWakeState()
    object Polling : ServerWakeState()
    object HostUnreachable : ServerWakeState()
    object Success : ServerWakeState()
    data class Timeout(val message: String) : ServerWakeState()
}

data class AppSettings(
    val serverUrl: String = "http://10.0.2.2:8188",
    val geminiApiKey: String = "",
    val geminiModel: String = "gemini-1.5-flash",
    val chatgptApiKey: String = "",
    val chatgptModel: String = "gpt-4o-mini",
    val claudeApiKey: String = "",
    val claudeModel: String = "claude-3-5-sonnet-latest",
    val grokApiKey: String = "",
    val grokModel: String = "grok-2-1212",
    val apiProvider: String = "Gemini", // Gemini, ChatGPT, Claude, Grok
    val outputFormat: String = "PNG", // PNG, JPEG, WEBP
    val seedMode: SeedMode = SeedMode.Random,
    val fixedSeedValue: Long = 42L,
    val customSeedValue: Long = 42L,
    val lastUsedSeedValue: Long = 42L,
    val megapixel: String = "1.0",
    val aspectRatio: String = "16:9 (Panorama)",
    val enableEnhancer: Boolean = true,
    val workflowToUse: String = "",
    val hostType: HostType = HostType.LOCAL,
    val localIpAddress: String = "http://10.0.2.2:8188",
    val comfyDeployApiKey: String = "",
    val comfyDeployId: String = "",
    val runpodApiKey: String = "",
    val runpodEndpointId: String = "",
    val falAiApiKey: String = "",
    val falAiEndpointSlug: String = "",
    val localLlmBaseUrl: String = "http://10.0.2.2:1234/v1",
    val localLlmSelectedModel: String = "",
    val triggerCmdEnabled: Boolean = false,
    val triggerCmdToken: String = "",
    val triggerCmdName: String = "Comfy_Start",
    val triggerCmdComputer: String = ""
)

data class ProgressInfo(
    val state: GenerationState = GenerationState.Idle,
    val percent: Float = 0f,
    val currentNode: String = "",
    val statusText: String = "",
    val baseImage: String? = null,      // Image view path/URL
    val upscaleImage: String? = null,   // Image view path/URL
    val finalImage: String? = null,     // Image view path/URL
    val enhancedPrompt: String? = null  // Enhanced prompt from Gemini
)

data class GalleryItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val imageUrl: String,
    val prompt: String,
    val enhancedPrompt: String?,
    val seed: Long,
    val timestamp: Long = System.currentTimeMillis()
)

enum class FormatType {
    UI_STANDARD,
    API_READY;
    
    companion object {
        fun detect(jsonString: String): FormatType {
            val element = com.google.gson.JsonParser.parseString(jsonString)
            if (element.isJsonArray) return UI_STANDARD
            if (element.isJsonObject) {
                val obj = element.asJsonObject
                if (obj.has("nodes") && obj.has("links")) {
                    return UI_STANDARD
                }
                // Check if keys are numeric and point to objects with class_type & inputs
                var hasNumericKeys = false
                var matchesApi = true
                obj.keySet().forEach { key ->
                    if (key.toIntOrNull() != null) {
                        hasNumericKeys = true
                        val node = obj.get(key)
                        if (node.isJsonObject) {
                            val nodeObj = node.asJsonObject
                            if (!nodeObj.has("class_type") || !nodeObj.has("inputs")) {
                                matchesApi = false
                            }
                        } else {
                            matchesApi = false
                        }
                    }
                }
                if (hasNumericKeys && matchesApi) {
                    return API_READY
                }
            }
            throw IllegalArgumentException("Invalid or unsupported workflow JSON format")
        }
    }
}

sealed class ConversionResult {
    data class Success(val apiJson: String) : ConversionResult()
    sealed class Error : ConversionResult() {
        object MissingExtension : Error()
        data class Generic(val message: String) : Error()
    }
}

data class QueueJob(
    val id: String = java.util.UUID.randomUUID().toString(),
    val prompt: String,
    val timestamp: Long = System.currentTimeMillis(),
    val progress: ProgressInfo = ProgressInfo(),
    val settings: AppSettings
)

enum class MessageSender {
    USER, AI
}

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: MessageSender,
    val text: String,
    val imageUrls: List<String> = emptyList(),
    val refinedPrompt: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

