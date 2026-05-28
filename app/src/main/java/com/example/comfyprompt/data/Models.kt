package com.example.comfyprompt.data

enum class SeedMode {
    Random, Fixed, LastUsed, Custom
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
    val workflowToUse: String = ""
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
