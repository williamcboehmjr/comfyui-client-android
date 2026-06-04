package com.example.comfyprompt.network

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.comfyprompt.MainActivity
import com.example.comfyprompt.data.GenerationState
import com.example.comfyprompt.data.ProgressInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class GenerationService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var collectJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        AppLogger.i("GenerationService", "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AppLogger.i("GenerationService", "Service started onStartCommand")
        startForegroundWithNotification()
        observeProgress()
        return START_NOT_STICKY
    }

    private fun startForegroundWithNotification() {
        val channelId = "comfyui_generation_service"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Ongoing Generation Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows the progress of ComfyUI image generation"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = createNotification(channelId, "Initializing...", 0f)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotification(channelId: String, statusText: String, percent: Float): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val progressInt = (percent * 100).toInt()
        val contentText = if (progressInt > 0) "$statusText ($progressInt%)" else statusText

        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("ComfyUI Generating Image... 🎨")
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setProgress(100, progressInt, percent == 0f && (statusText.contains("Connecting") || statusText.contains("Initializing")))
            .build()
    }

    private fun observeProgress() {
        collectJob?.cancel()
        collectJob = serviceScope.launch {
            ComfyClient.progressFlow.collectLatest { progress ->
                updateNotification(progress)
                if (progress.state == GenerationState.Completed ||
                    progress.state == GenerationState.Failed ||
                    progress.state == GenerationState.Cancelled) {
                    AppLogger.i("GenerationService", "Job ended, stopping service")
                    stopSelf()
                }
            }
        }
    }

    private fun updateNotification(progress: ProgressInfo) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = createNotification("comfyui_generation_service", progress.statusText, progress.percent)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        AppLogger.i("GenerationService", "Service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIFICATION_ID = 10001

        fun start(context: Context) {
            val intent = Intent(context, GenerationService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, GenerationService::class.java)
            context.stopService(intent)
        }
    }
}
