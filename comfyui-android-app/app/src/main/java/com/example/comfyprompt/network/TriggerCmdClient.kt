package com.example.comfyprompt.network

import com.google.gson.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class PingResult {
    ONLINE,
    HOST_ALIVE_SERVER_DOWN,
    HOST_UNREACHABLE
}

object TriggerCmdClient {
    private val mediaType = "application/json; charset=utf-8".toMediaType()
    
    // Strict timeout client for quick pings
    private val pingClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    private val wakeClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
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

    private fun isHostUnreachableException(e: Exception): Boolean {
        if (e is java.net.UnknownHostException) return true
        if (e is java.net.NoRouteToHostException) return true
        val msg = e.localizedMessage ?: ""
        val lower = msg.lowercase(java.util.Locale.ROOT)
        return lower.contains("no route to host") || 
               lower.contains("network is unreachable") ||
               lower.contains("enetunreach") ||
               lower.contains("ehostunreach")
    }

    suspend fun pollLocalServer(serverUrl: String): PingResult = withContext(Dispatchers.IO) {
        val cleanUrl = serverUrl.removeSuffix("/")
        try {
            // ComfyUI system check or main page ping
            val request = Request.Builder()
                .url(cleanUrl)
                .get()
                .build()

            pingClient.newCall(request).execute().use { response ->
                AppLogger.d("TriggerCmdClient", "Ping ComfyUI returned code: ${response.code}")
                return@withContext PingResult.ONLINE
            }
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: ""
            AppLogger.d("TriggerCmdClient", "Ping local server failed to connect to $cleanUrl (normal if booting): $msg")
            
            if (isHostUnreachableException(e)) {
                return@withContext PingResult.HOST_UNREACHABLE
            }

            val isRefused = msg.contains("refused", ignoreCase = true) || 
                            msg.contains("Connection refused", ignoreCase = true)
            if (isRefused) {
                return@withContext PingResult.HOST_ALIVE_SERVER_DOWN
            }

            // Run a quick ICMP ping check to see if the host is reachable
            try {
                val uri = java.net.URI(cleanUrl)
                val host = uri.host
                if (host != null && host.isNotBlank()) {
                    val process = Runtime.getRuntime().exec(arrayOf("/system/bin/ping", "-c", "1", "-W", "2", host))
                    val exitVal = process.waitFor()
                    if (exitVal == 0) {
                        AppLogger.d("TriggerCmdClient", "ICMP ping to $host succeeded. Host is alive, ComfyUI server is down/booting.")
                        return@withContext PingResult.HOST_ALIVE_SERVER_DOWN
                    } else {
                        AppLogger.d("TriggerCmdClient", "ICMP ping to $host failed with exit code $exitVal.")
                    }
                }
            } catch (pingEx: Exception) {
                AppLogger.d("TriggerCmdClient", "Failed to run ICMP ping check: ${pingEx.localizedMessage}")
            }

            return@withContext PingResult.HOST_UNREACHABLE
        }
    }
}
