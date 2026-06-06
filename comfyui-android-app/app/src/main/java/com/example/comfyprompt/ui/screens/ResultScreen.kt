package com.example.comfyprompt.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.comfyprompt.data.AppSettings
import com.example.comfyprompt.theme.AccentGray
import com.example.comfyprompt.theme.AccentRed
import com.example.comfyprompt.theme.SuccessGreen

@Composable
fun ResultScreen(
    finalImageUrl: String,
    seed: Long,
    settings: AppSettings,
    onSaveClick: () -> Unit,
    onShareClick: () -> Unit,
    onReRunClick: () -> Unit,
    onUsePromptClick: (String) -> Unit,
    viewModel: com.example.comfyprompt.ui.MainViewModel
) {
    var isFullScreen by remember { mutableStateOf(false) }
    var isChatOpen by remember { mutableStateOf(false) }
    var attachedUris by remember { mutableStateOf<List<android.net.Uri>>(emptyList()) }
    val messages by viewModel.copilotMessages.collectAsState()
    val isCopilotLoading by viewModel.isCopilotLoading.collectAsState()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isExpandedScreen = configuration.screenWidthDp >= 600
    val clipboardManager = LocalClipboardManager.current
    val progressInfo by viewModel.progressInfo.collectAsState()
    val enhancedPrompt = progressInfo.enhancedPrompt
    var isPromptFullScreen by remember { mutableStateOf(false) }

    val enhancedPromptCard = @Composable {
        if (!enhancedPrompt.isNullOrBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "✨ ENHANCED PROMPT (TAP TO EXPAND)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = SuccessGreen
                        )
                        Text(
                            text = "COPY",
                            modifier = Modifier
                                .clickable {
                                    clipboardManager.setText(AnnotatedString(enhancedPrompt))
                                    android.widget.Toast.makeText(context, "Copied to clipboard!", android.widget.Toast.LENGTH_SHORT).show()
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = enhancedPrompt,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clickable { isPromptFullScreen = true }
                            .fillMaxWidth()
                    )
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.clearCopilotChat()
        }
    }

    val reRunButton = @Composable {
        OutlinedButton(
            onClick = onReRunClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            border = BorderStroke(1.dp, Color.White),
            shape = RoundedCornerShape(28.dp)
        ) {
            Text("FINISH (GO BACK)", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
    }

    val shareButton = @Composable {
        OutlinedButton(
            onClick = onShareClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            border = BorderStroke(1.dp, Color.White),
            shape = RoundedCornerShape(28.dp)
        ) {
            Text("SHARE", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
    }

    val saveButton = @Composable {
        Button(
            onClick = onSaveClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.Black
            ),
            shape = RoundedCornerShape(28.dp)
        ) {
            Text("DOWNLOAD", fontWeight = FontWeight.Bold, letterSpacing = 1.sp, fontSize = 16.sp)
        }
    }

    val copilotButton = @Composable {
        Button(
            onClick = {
                isChatOpen = !isChatOpen
                if (isChatOpen) {
                    viewModel.initCopilotChat()
                } else {
                    viewModel.clearCopilotChat()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isChatOpen) SuccessGreen else Color(0xFF2563EB),
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(28.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("REFINE PROMPT", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
    }

    val imageCanvas = @Composable {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            ZoomableImage(
                model = finalImageUrl,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                onTap = { isFullScreen = true }
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        BackHandler(enabled = isFullScreen || isPromptFullScreen) {
            isFullScreen = false
            isPromptFullScreen = false
        }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background
        ) { paddingValues ->
            if (isExpandedScreen) {
                // Expanded landscape screen
                if (isChatOpen && settings.enableEnhancer) {
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
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                imageCanvas()
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    OutlinedButton(
                                        onClick = onReRunClick,
                                        modifier = Modifier.fillMaxSize(),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                        border = BorderStroke(1.dp, Color.White),
                                        shape = RoundedCornerShape(25.dp),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("BACK", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    OutlinedButton(
                                        onClick = onShareClick,
                                        modifier = Modifier.fillMaxSize(),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                        border = BorderStroke(1.dp, Color.White),
                                        shape = RoundedCornerShape(25.dp),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("SHARE", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                                Box(modifier = Modifier.weight(1.2f)) {
                                    Button(
                                        onClick = onSaveClick,
                                        modifier = Modifier.fillMaxSize(),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.Black),
                                        shape = RoundedCornerShape(25.dp),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("DOWNLOAD", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }
                        }

                        CopilotChatPanel(
                            messages = messages,
                            isLoading = isCopilotLoading,
                            onSendMessage = { txt, uris ->
                                viewModel.sendCopilotMessage(context, txt, finalImageUrl, uris)
                                attachedUris = emptyList()
                            },
                            onUsePromptClick = onUsePromptClick,
                            onCloseChat = {
                                isChatOpen = false
                                viewModel.clearCopilotChat()
                            },
                            attachedUris = attachedUris,
                            onAddAttachment = { uri -> attachedUris = attachedUris + uri },
                            onRemoveAttachment = { uri -> attachedUris = attachedUris.filter { it != uri } },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "COMPLETED OUTPUT",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = AccentGray,
                                letterSpacing = 1.5.sp
                            )

                            Spacer(modifier = Modifier.height(24.dp))
                            reRunButton()
                            shareButton()
                            if (settings.enableEnhancer) {
                                copilotButton()
                            }
                            enhancedPromptCard()
                            Spacer(modifier = Modifier.height(16.dp))
                            saveButton()
                        }

                        Box(
                            modifier = Modifier
                                .weight(1.3f)
                                .fillMaxHeight()
                        ) {
                            imageCanvas()
                        }
                    }
                }
            } else {
                // Phones portrait screen
                if (isChatOpen && settings.enableEnhancer) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(0.7f)
                        ) {
                            imageCanvas()
                        }

                        CopilotChatPanel(
                            messages = messages,
                            isLoading = isCopilotLoading,
                            onSendMessage = { txt, uris ->
                                viewModel.sendCopilotMessage(context, txt, finalImageUrl, uris)
                                attachedUris = emptyList()
                            },
                            onUsePromptClick = onUsePromptClick,
                            onCloseChat = {
                                isChatOpen = false
                                viewModel.clearCopilotChat()
                            },
                            attachedUris = attachedUris,
                            onAddAttachment = { uri -> attachedUris = attachedUris + uri },
                            onRemoveAttachment = { uri -> attachedUris = attachedUris.filter { it != uri } },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1.3f)
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            "COMPLETED OUTPUT",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = AccentGray,
                            letterSpacing = 1.5.sp
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            imageCanvas()
                        }

                        enhancedPromptCard()

                        if (settings.enableEnhancer) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    reRunButton()
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    shareButton()
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    copilotButton()
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    saveButton()
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    reRunButton()
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    shareButton()
                                }
                            }

                            saveButton()
                        }
                    }
                }
            }
        }

        // Full Screen Immersive Overlay
        AnimatedVisibility(
            visible = isFullScreen,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                ZoomableImage(
                    model = finalImageUrl,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    shape = androidx.compose.ui.graphics.RectangleShape,
                    onTap = { isFullScreen = false }
                )
            }
        }

        // Full Screen Prompt Overlay
        AnimatedVisibility(
            visible = isPromptFullScreen,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.95f))
                    .clickable { isPromptFullScreen = false }
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "✨ ENHANCED PROMPT",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = SuccessGreen,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    Text(
                        text = enhancedPrompt ?: "",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        lineHeight = 26.sp
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Tap anywhere to close",
                        fontSize = 12.sp,
                        color = AccentGray,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
