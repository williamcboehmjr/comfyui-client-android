package com.example.comfyprompt.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.comfyprompt.data.AppSettings
import com.example.comfyprompt.data.ServerWakeState
import com.example.comfyprompt.theme.*

@Composable
fun ServerWakeScreen(
    wakeState: ServerWakeState,
    settings: AppSettings,
    onCancelClick: () -> Unit
) {
    // Pulse animation setup
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_transition")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .padding(24.dp)
    ) {
        // Glowing background gradient accents
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(300.dp)
                .scale(pulseScale)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.08f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Pulsing status ring
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(100.dp)
            ) {
                // Outer pulsing ring
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = pulseAlpha))
                )

                // Inner core ring
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(CardGray)
                        .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Pinging",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            // Main Status Text
            val statusHeader = when (wakeState) {
                is ServerWakeState.Waking -> "Waking ComfyUI Server..."
                is ServerWakeState.Polling -> "Waiting for ComfyUI to load... ⏳"
                is ServerWakeState.Success -> "Server Found! ✨"
                is ServerWakeState.Timeout -> "Wake Timeout"
                else -> "Offline Check"
            }

            Text(
                text = statusHeader,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Dynamic Subtitle Status Detail
            val statusDetail = when (wakeState) {
                is ServerWakeState.Waking -> {
                    "Sent wake command \"${settings.triggerCmdName}\" to computer \"${settings.triggerCmdComputer.ifBlank { "default" }}\" via TRIGGERcmd API."
                }
                is ServerWakeState.Polling -> {
                    "Pinging local server at:\n${settings.serverUrl}\n\nIt can take up to 2-3 minutes for the server shell/process to finish booting up."
                }
                is ServerWakeState.Success -> {
                    "Your ComfyUI server is successfully online and ready!"
                }
                is ServerWakeState.Timeout -> {
                    wakeState.message
                }
                else -> ""
            }

            Text(
                text = statusDetail,
                fontSize = 14.sp,
                color = LightGray,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .padding(horizontal = 8.dp),
                lineHeight = 20.sp
            )
        }

        // Cancel / Dismiss button at the bottom
        Button(
            onClick = onCancelClick,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(56.dp)
                .padding(bottom = 8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = CardGray,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(28.dp),
            border = borderStroke()
        ) {
            Text(
                text = if (wakeState is ServerWakeState.Timeout) "DISMISS" else "CANCEL WAKE",
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
private fun borderStroke(): androidx.compose.foundation.BorderStroke {
    return androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
}
