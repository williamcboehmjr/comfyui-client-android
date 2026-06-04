package com.example.comfyprompt.network

import com.google.gson.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object TriggerCmdClient {
    private val mediaType = "application/json; charset=utf-8".toMediaType()
    
    // Strict timeout client for quick pings
    private val pingClient = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .writeTimeout(2, TimeUnit.SECONDS)
        .build()

    private val wakeClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    suspend fun wakeServer(token: String, trigger: String, computer: String): Boolean = withContext(Dispatchers.IO) {
        val cleanToken = token.trim()
        val cleanTrigger = trigger.trim()
        val cleanComputer = computer.trim()

        if (cleanToken.isBlank()) {
            AppLogger.e("TriggerCmdClient", "Cannot trigger wake: Token is empty")
            return@withContext false
        }

        try {
            val url = "https://www.triggercmd.com/api/run/trigger"
            val bodyObj = JsonObject().apply {
                addProperty("trigger", cleanTrigger)
                if (cleanComputer.isNotBlank()) {
                    addProperty("computer", cleanComputer)
                }
            }
            val requestBody = bodyObj.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $cleanToken")
                .post(requestBody)
                .build()

            AppLogger.d("TriggerCmdClient", "Sending TRIGGERcmd wake call for trigger: $cleanTrigger on computer: $cleanComputer")
            wakeClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                AppLogger.d("TriggerCmdClient", "RESPONSE: ${response.code} -> $bodyStr")
                if (response.isSuccessful) {
                    AppLogger.d("TriggerCmdClient", "TRIGGERcmd wake call executed successfully!")
                    return@withContext true
                } else {
                    AppLogger.e("TriggerCmdClient", "TRIGGERcmd API rejected request with code ${response.code}: $bodyStr")
                }
            }
        } catch (e: Exception) {
            AppLogger.e("TriggerCmdClient", "Failed to run TRIGGERcmd wake call: ${e.localizedMessage}", e)
        }
        return@withContext false
    }

    suspend fun pollLocalServer(serverUrl: String): Boolean = withContext(Dispatchers.IO) {
        val cleanUrl = serverUrl.removeSuffix("/")
        try {
            // ComfyUI system check or main page ping
            val request = Request.Builder()
                .url(cleanUrl)
                .get()
                .build()

            pingClient.newCall(request).execute().use { response ->
                // If ComfyUI responds with any code, it is online
                AppLogger.d("TriggerCmdClient", "Ping ComfyUI returned code: ${response.code}")
                return@withContext true
            }
        } catch (e: Exception) {
            AppLogger.d("TriggerCmdClient", "Ping local server failed to connect to $cleanUrl (normal if booting): ${e.localizedMessage}")
        }
        return@withContext false
    }
}
