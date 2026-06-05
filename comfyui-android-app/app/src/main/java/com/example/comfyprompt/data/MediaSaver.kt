package com.example.comfyprompt.data

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.comfyprompt.network.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object MediaSaver {

    suspend fun saveImageToDownloads(context: Context, imageUrl: String, format: String) = withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient()
            val request = Request.Builder().url(imageUrl).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Failed to download image from server.", Toast.LENGTH_SHORT).show()
                    }
                    return@withContext
                }
                val bytes = response.body?.bytes() ?: return@withContext

                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val ext = format.lowercase(Locale.getDefault())
                val displayName = "ComfyUI_$timeStamp.$ext"
                val mimeType = when (format) {
                    "JPEG" -> "image/jpeg"
                    "WEBP" -> "image/webp"
                    else -> "image/png"
                }

                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    } else {
                        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        val file = File(downloadsDir, displayName)
                        put(MediaStore.MediaColumns.DATA, file.absolutePath)
                    }
                }

                val collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI
                } else {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }

                val uri = resolver.insert(collectionUri, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri).use { outputStream ->
                        if (outputStream != null) {
                            outputStream.write(bytes)
                            outputStream.flush()
                        }
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        contentValues.clear()
                        contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                        resolver.update(uri, contentValues, null, null)
                    }

                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Saved to Downloads: $displayName", Toast.LENGTH_LONG).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Error inserting MediaStore row.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.e("MediaSaver", "Save image to downloads failed: ${e.message}")
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Save Failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    suspend fun shareImage(context: Context, imageUrl: String) = withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient()
            val request = Request.Builder().url(imageUrl).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Failed to load image for sharing.", Toast.LENGTH_SHORT).show()
                    }
                    return@withContext
                }
                val bytes = response.body?.bytes() ?: return@withContext

                // Save the image temporarily in cache
                val cachePath = File(context.cacheDir, "images")
                cachePath.mkdirs()
                val file = File(cachePath, "comfy_shared_output.png")
                FileOutputStream(file).use { out ->
                    out.write(bytes)
                    out.flush()
                }

                // Get FileProvider Uri
                val contentUri: Uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )

                // Open share chooser
                val shareIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    type = "image/png"
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                withContext(Dispatchers.Main) {
                    val chooser = Intent.createChooser(shareIntent, "Share Comfy Output")
                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(chooser)
                }
            }
        } catch (e: Exception) {
            AppLogger.e("MediaSaver", "Sharing image failed: ${e.message}")
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Sharing Failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    suspend fun downloadWorkflow(context: Context) = withContext(Dispatchers.IO) {
        try {
            val assetManager = context.assets
            val jsonString = assetManager.open("ernie_workflow_ui.json").use { inputStream ->
                inputStream.bufferedReader().use { it.readText() }
            }

            val fileName = "workflow.json"
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                } else {
                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val file = File(downloadsDir, fileName)
                    put(MediaStore.MediaColumns.DATA, file.absolutePath)
                }
            }

            val collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Downloads.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI // Fallback
            }

            val uri = resolver.insert(collectionUri, contentValues)
            if (uri != null) {
                resolver.openOutputStream(uri).use { outputStream ->
                    if (outputStream != null) {
                        outputStream.write(jsonString.toByteArray())
                        outputStream.flush()
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Saved workflow.json to Downloads!", Toast.LENGTH_LONG).show()
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to create workflow.json in Downloads.", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            AppLogger.e("MediaSaver", "Download workflow failed: ${e.message}")
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Download Failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
