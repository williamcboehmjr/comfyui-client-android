package com.example.comfyprompt.network

import java.net.URI
import java.net.URL

sealed class ValidationResult {
    object Local : ValidationResult()
    object Public : ValidationResult()
    data class Error(val message: String) : ValidationResult()
}

object UrlValidator {
    fun validateUrl(url: String): ValidationResult {
        if (url.isBlank()) {
            return ValidationResult.Error("URL cannot be empty")
        }

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return ValidationResult.Error("URL must start with http:// or https://")
        }

        val host: String = try {
            val uri = URI(url)
            val hostStr = uri.host
            if (hostStr.isNullOrEmpty()) {
                val u = URL(url)
                u.host ?: return ValidationResult.Error("Invalid host in URL")
            } else {
                hostStr
            }
        } catch (e: Exception) {
            return ValidationResult.Error("Malformed URL: ${e.message}")
        }

        if (host.isBlank()) {
            return ValidationResult.Error("Invalid host in URL")
        }

        val isLocal = when {
            host.equals("localhost", ignoreCase = true) -> true
            host.equals("127.0.0.1") -> true
            host.startsWith("10.") -> true
            host.startsWith("192.168.") -> true
            host.matches(Regex("^172\\.(1[6-9]|2[0-9]|3[0-1])\\..*")) -> true
            else -> false
        }

        return if (isLocal) {
            ValidationResult.Local
        } else {
            ValidationResult.Public
        }
    }
}
