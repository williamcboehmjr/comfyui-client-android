package com.example.comfyprompt.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.comfyprompt.data.GenerationState
import com.example.comfyprompt.data.ProgressInfo
import com.example.comfyprompt.theme.AccentGray
import com.example.comfyprompt.theme.AccentRed
import com.example.comfyprompt.theme.SuccessGreen

@Composable
fun ProgressScreen(
    progressInfo: ProgressInfo,
    prompt: String,
    onStopClick: () -> Unit,
    onSaveClick: (String) -> Unit,
    onShareClick: (String) -> Unit
) {
    val configuration = LocalConfiguration.current
    val isExpandedScreen = configuration.screenWidthDp >= 600

    val promptSummaryCard = @Composable {
        var isExpanded by remember { mutableStateOf(false) }
        val isEnhanced = !progressInfo.enhancedPrompt.isNullOrBlank()
        val promptToDisplay = if (isEnhanced) progressInfo.enhancedPrompt.orEmpty() else prompt
        val titleText = if (isEnhanced) "ENHANCED PROMPT" else "QUEUED PROMPT"
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .animateContentSize(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        titleText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = AccentGray
                    )
                    Text(
                        if (isExpanded) "TAP TO COLLAPSE" else "TAP TO EXPAND",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentGray
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = promptToDisplay,
                    fontSize = 13.sp,
                    color = Color.White,
                    maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    val progressIndicators = @Composable {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val cleanStatus = progressInfo.statusText.substringBefore(" (")
            val progressText = if (progressInfo.percent > 0) {
                "$cleanStatus (${(progressInfo.percent * 100).toInt()}%)"
            } else {
                cleanStatus
            }

            Text(
                text = progressText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (progressInfo.state == GenerationState.Failed) AccentRed else Color.White,
                textAlign = TextAlign.Center
            )

            LinearProgressIndicator(
                progress = { progressInfo.percent },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (progressInfo.state == GenerationState.Failed) AccentRed else Color.White,
                trackColor = MaterialTheme.colorScheme.surface
            )
        }
    }

    val imageCanvas = @Composable {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            val currentPreview = progressInfo.upscaleImage ?: progressInfo.baseImage
            if (currentPreview != null) {
                AsyncImage(
                    model = currentPreview,
                    contentDescription = "Intermediate Preview",
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp)
                    Text(
                        "Awaiting Preview Stream...",
                        fontSize = 12.sp,
                        color = AccentGray,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }

    val stopButton = @Composable {
        Button(
            onClick = onStopClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = AccentRed
            ),
            shape = RoundedCornerShape(25.dp)
        ) {
            Text("CANCEL GENERATION", fontWeight = FontWeight.Bold, letterSpacing = 1.sp, fontSize = 14.sp)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        if (isExpandedScreen) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    promptSummaryCard()
                    Spacer(modifier = Modifier.height(8.dp))
                    progressIndicators()
                    Spacer(modifier = Modifier.weight(1f))
                    stopButton()
                }

                Box(
                    modifier = Modifier
                        .weight(1.3f)
                        .fillMaxHeight()
                ) {
                    imageCanvas()
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                promptSummaryCard()
                Spacer(modifier = Modifier.height(24.dp))
                progressIndicators()
                Spacer(modifier = Modifier.height(24.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(350.dp)
                ) {
                    imageCanvas()
                }

                Spacer(modifier = Modifier.height(16.dp))
                stopButton()
            }
        }
    }
}
