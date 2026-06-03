package com.example.comfyprompt.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.example.comfyprompt.network.AppLogger
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("comfy_prefs", Context.MODE_PRIVATE)
    
    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
    private val encryptedPrefs: SharedPreferences = EncryptedSharedPreferences.create(
        "comfy_secret_prefs",
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    
    private val gson = Gson()

    init {
        migrateLegacyPrefs()
    }

    private fun migrateLegacyPrefs() {
        val keysToMigrate = listOf(
            "gemini_key",
            "chatgpt_key",
            "claude_key",
            "grok_key",
            "comfy_deploy_api_key",
            "runpod_api_key",
            "fal_ai_api_key"
        )
        
        var migrated = false
        val prefsEditor = prefs.edit()
        val encryptedPrefsEditor = encryptedPrefs.edit()
        
        for (key in keysToMigrate) {
            if (prefs.contains(key)) {
                val value = prefs.getString(key, null)
                if (value != null) {
                    encryptedPrefsEditor.putString(key, value)
                    migrated = true
                }
                prefsEditor.remove(key)
            }
        }
        
        if (migrated) {
            encryptedPrefsEditor.apply()
            prefsEditor.apply()
            AppLogger.i("SettingsManager", "Legacy API keys successfully migrated to secure EncryptedSharedPreferences.")
        }
    }

    private fun getSensitiveKey(key: String, default: String = ""): String {
        return encryptedPrefs.getString(key, null) ?: default
    }

    fun getSettings(): AppSettings {
        val aspectRatios = listOf(
            "1:1 (Perfect Square)", "2:3 (Classic Portrait)", "3:4 (Golden Ratio)",
            "3:5 (Elegant Vertical)", "4:5 (Artistic Frame)", "5:7 (Balanced Portrait)",
            "5:8 (Tall Portrait)", "7:9 (Modern Portrait)", "9:16 (Slim Vertical)",
            "9:19 (Tall Slim)", "9:21 (Ultra Tall)", "9:32 (Skyline)",
            "3:2 (Golden Landscape)", "4:3 (Classic Landscape)", "5:3 (Wide Horizon)",
            "5:4 (Balanced Frame)", "7:5 (Elegant Landscape)", "8:5 (Cinematic View)",
            "9:7 (Artful Horizon)", "16:9 (Panorama)", "19:9 (Cinematic Ultrawide)",
            "21:9 (Epic Ultrawide)", "32:9 (Extreme Ultrawide)"
        )
        val megapixelOptions = listOf(
            "0.1", "0.2", "0.3", "0.4", "0.5", "0.6", "0.7", "0.8", "0.9", "1.0",
            "1.1", "1.2", "1.3", "1.4", "1.5", "1.6", "1.7", "1.8", "1.9", "2.0",
            "2.1", "2.2", "2.3", "2.4", "2.5"
        )

        var mp = prefs.getString("megapixel", "1.0") ?: "1.0"
        if (!megapixelOptions.contains(mp)) {
            mp = "1.0"
        }

        var ar = prefs.getString("aspect_ratio", "16:9 (Panorama)") ?: "16:9 (Panorama)"
        if (!aspectRatios.contains(ar)) {
            ar = "16:9 (Panorama)"
        }

        val hostTypeStr = prefs.getString("host_type", HostType.LOCAL.name) ?: HostType.LOCAL.name
        val hostType = try {
            HostType.valueOf(hostTypeStr)
        } catch (e: Exception) {
            HostType.LOCAL
        }

        return AppSettings(
            serverUrl = prefs.getString("server_url", "http://10.0.2.2:8188") ?: "http://10.0.2.2:8188",
            geminiApiKey = getSensitiveKey("gemini_key", ""),
            geminiModel = prefs.getString("gemini_model", "gemini-1.5-flash") ?: "gemini-1.5-flash",
            chatgptApiKey = getSensitiveKey("chatgpt_key", ""),
            chatgptModel = prefs.getString("chatgpt_model", "gpt-4o-mini") ?: "gpt-4o-mini",
            claudeApiKey = getSensitiveKey("claude_key", ""),
            claudeModel = prefs.getString("claude_model", "claude-3-5-sonnet-latest") ?: "claude-3-5-sonnet-latest",
            grokApiKey = getSensitiveKey("grok_key", ""),
            grokModel = prefs.getString("grok_model", "grok-2-1212") ?: "grok-2-1212",
            apiProvider = prefs.getString("api_provider", "Gemini") ?: "Gemini",
            outputFormat = prefs.getString("output_format", "PNG") ?: "PNG",
            seedMode = try {
                SeedMode.valueOf(prefs.getString("seed_mode", SeedMode.Random.name) ?: SeedMode.Random.name)
            } catch (e: Exception) {
                SeedMode.Random
            },
            fixedSeedValue = prefs.getLong("fixed_seed", 42L),
            customSeedValue = prefs.getLong("custom_seed", 42L),
            lastUsedSeedValue = prefs.getLong("last_seed", 42L),
            megapixel = mp,
            aspectRatio = ar,
            enableEnhancer = prefs.getBoolean("enable_enhancer", true),
            workflowToUse = prefs.getString("workflow_to_use", "") ?: "",
            hostType = hostType,
            localIpAddress = prefs.getString("local_ip_address", "http://10.0.2.2:8188") ?: "http://10.0.2.2:8188",
            comfyDeployApiKey = getSensitiveKey("comfy_deploy_api_key", ""),
            comfyDeployId = prefs.getString("comfy_deploy_id", "") ?: "",
            runpodApiKey = getSensitiveKey("runpod_api_key", ""),
            runpodEndpointId = prefs.getString("runpod_endpoint_id", "") ?: "",
            falAiApiKey = getSensitiveKey("fal_ai_api_key", ""),
            falAiEndpointSlug = prefs.getString("fal_ai_endpoint_slug", "") ?: "",
            localLlmBaseUrl = prefs.getString("local_llm_base_url", "http://10.0.2.2:1234/v1") ?: "http://10.0.2.2:1234/v1",
            localLlmSelectedModel = prefs.getString("local_llm_selected_model", "") ?: "",
            triggerCmdEnabled = prefs.getBoolean("trigger_cmd_enabled", false),
            triggerCmdToken = getSensitiveKey("trigger_cmd_token", ""),
            triggerCmdName = prefs.getString("trigger_cmd_name", "Comfy_Start") ?: "Comfy_Start",
            triggerCmdComputer = prefs.getString("trigger_cmd_computer", "") ?: ""
        )
    }

    fun saveSettings(settings: AppSettings) {
        // Save sensitive keys in EncryptedSharedPreferences
        encryptedPrefs.edit().apply {
            putString("gemini_key", settings.geminiApiKey)
            putString("chatgpt_key", settings.chatgptApiKey)
            putString("claude_key", settings.claudeApiKey)
            putString("grok_key", settings.grokApiKey)
            putString("comfy_deploy_api_key", settings.comfyDeployApiKey)
            putString("runpod_api_key", settings.runpodApiKey)
            putString("fal_ai_api_key", settings.falAiApiKey)
            putString("trigger_cmd_token", settings.triggerCmdToken)
            apply()
        }

        // Save non-sensitive keys in standard SharedPreferences
        prefs.edit().apply {
            putString("server_url", settings.serverUrl)
            putString("gemini_model", settings.geminiModel)
            putString("chatgpt_model", settings.chatgptModel)
            putString("claude_model", settings.claudeModel)
            putString("grok_model", settings.grokModel)
            putString("api_provider", settings.apiProvider)
            putString("output_format", settings.outputFormat)
            putString("seed_mode", settings.seedMode.name)
            putLong("fixed_seed", settings.fixedSeedValue)
            putLong("custom_seed", settings.customSeedValue)
            putLong("last_seed", settings.lastUsedSeedValue)
            putString("megapixel", settings.megapixel)
            putString("aspect_ratio", settings.aspectRatio)
            putBoolean("enable_enhancer", settings.enableEnhancer)
            putString("workflow_to_use", settings.workflowToUse)
            
            // New settings
            putString("host_type", settings.hostType.name)
            putString("local_ip_address", settings.localIpAddress)
            putString("comfy_deploy_id", settings.comfyDeployId)
            putString("runpod_endpoint_id", settings.runpodEndpointId)
            putString("fal_ai_endpoint_slug", settings.falAiEndpointSlug)
            putString("local_llm_base_url", settings.localLlmBaseUrl)
            putString("local_llm_selected_model", settings.localLlmSelectedModel)
            putBoolean("trigger_cmd_enabled", settings.triggerCmdEnabled)
            putString("trigger_cmd_name", settings.triggerCmdName)
            putString("trigger_cmd_computer", settings.triggerCmdComputer)

            // Remove legacy plain-text sensitive keys from regular preferences if present
            remove("gemini_key")
            remove("chatgpt_key")
            remove("claude_key")
            remove("grok_key")
            remove("comfy_deploy_api_key")
            remove("runpod_api_key")
            remove("fal_ai_api_key")
            remove("trigger_cmd_token")
            
            apply()
        }
    }


    fun getLastPrompt(): String {
        return prefs.getString("last_prompt", "") ?: ""
    }

    fun saveLastPrompt(prompt: String) {
        prefs.edit().putString("last_prompt", prompt).apply()
    }

    fun getGalleryItems(): List<GalleryItem> {
        val json = prefs.getString("gallery_items", "[]") ?: "[]"
        return try {
            val type = object : TypeToken<List<GalleryItem>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveGalleryItems(items: List<GalleryItem>) {
        val json = gson.toJson(items)
        prefs.edit().putString("gallery_items", json).apply()
    }
}
