package com.example.comfyprompt.ui.screens

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.comfyprompt.network.AppLogger
import com.example.comfyprompt.theme.CardGray
import com.example.comfyprompt.theme.DarkGray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    var logText by remember { mutableStateOf("Loading logs...") }
    var logSize by remember { mutableStateOf("0 KB") }
    var showClearDialog by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(refreshTrigger) {
        withContext(Dispatchers.IO) {
            val text = AppLogger.readActiveLogFile()
            val size = AppLogger.getActiveLogSizeFormatted()
            withContext(Dispatchers.Main) {
                logText = if (text.isBlank()) "Log file is empty." else text
                logSize = size
            }
        }
    }

    // Export log function
    fun exportLogFile() {
        val activeFile = AppLogger.getActiveLogFile()
        if (activeFile == null || !activeFile.exists() || activeFile.length() == 0L) {
            Toast.makeText(context, "Log file is empty or does not exist.", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val filename = "comfyprompt_logs.log"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri).use { outputStream ->
                        activeFile.inputStream().use { inputStream ->
                            inputStream.copyTo(outputStream!!)
                        }
                    }
                    Toast.makeText(context, "Exported to Downloads folder", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "Failed to create MediaStore entry", Toast.LENGTH_SHORT).show()
                }
            } else {
                @Suppress("DEPRECATION")
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val destFile = File(downloadsDir, filename)
                FileOutputStream(destFile).use { outputStream ->
                    activeFile.inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                Toast.makeText(context, "Exported to Downloads folder", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Export failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    // Share log function
    fun shareLogText() {
        val text = AppLogger.readActiveLogFile()
        if (text.isBlank()) {
            Toast.makeText(context, "Nothing to share.", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "ComfyPrompt App Logs")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "Share Logs via"))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("App Logs", color = Color.White, fontSize = 18.sp)
                        Text("Size: $logSize", color = Color.Gray, fontSize = 12.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { refreshTrigger++ }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = Color.Black
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Log output viewport (Monospace font, dark aesthetic, scrollable)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(8.dp)
                    .background(Color(0xFF1E1E1E))
                    .padding(8.dp)
            ) {
                val scrollStateVertical = rememberScrollState()
                val scrollStateHorizontal = rememberScrollState()

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollStateVertical)
                        .horizontalScroll(scrollStateHorizontal)
                ) {
                    Text(
                        text = logText,
                        color = Color(0xFFD4D4D4),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(4.dp)
                    )
                }
            }

            // Buttons panel
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Download/Export Button
                Button(
                    onClick = { exportLogFile() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkGray, contentColor = Color.White),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Download", fontSize = 12.sp)
                }

                // Share Button
                Button(
                    onClick = { shareLogText() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkGray, contentColor = Color.White),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Share", fontSize = 12.sp)
                }

                // Clear Button
                Button(
                    onClick = { showClearDialog = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B0000), contentColor = Color.White),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Clear", fontSize = 12.sp)
                }
            }
        }
    }

    // Confirmation dialog for clearing logs
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear Logs?", color = Color.White) },
            text = { Text("This will permanently delete all local log files. This cannot be undone.", color = Color.LightGray) },
            confirmButton = {
                TextButton(
                    onClick = {
                        AppLogger.clearLogs()
                        showClearDialog = false
                        refreshTrigger++
                    }
                ) {
                    Text("DELETE", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("CANCEL", color = Color.White)
                }
            },
            containerColor = CardGray
        )
    }
}
