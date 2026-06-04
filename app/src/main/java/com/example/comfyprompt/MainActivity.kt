package com.example.comfyprompt

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.comfyprompt.theme.ComfyPromptTheme

class MainActivity : ComponentActivity() {
    private val viewModel by lazy {
        androidx.lifecycle.ViewModelProvider(this)[com.example.comfyprompt.ui.MainViewModel::class.java]
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            com.example.comfyprompt.network.AppLogger.i("MainActivity", "Notification permission granted")
        } else {
            com.example.comfyprompt.network.AppLogger.w("MainActivity", "Notification permission denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        com.example.comfyprompt.network.AppLogger.init(applicationContext)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        checkNotificationPermission()
        setContent {
            ComfyPromptTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainNavigation(viewModel = viewModel)
                }
            }
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        isAppInForeground = true
        viewModel.onAppForegrounded()
    }

    override fun onStop() {
        super.onStop()
        isAppInForeground = false
        viewModel.onAppBackgrounded()
    }

    companion object {
        var isAppInForeground = false
    }
}
