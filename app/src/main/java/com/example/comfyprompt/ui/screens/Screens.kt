package com.example.comfyprompt.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.comfyprompt.data.AppSettings
import com.example.comfyprompt.data.GalleryItem
import com.example.comfyprompt.data.GenerationState
import com.example.comfyprompt.data.ProgressInfo
import com.example.comfyprompt.data.SeedMode
import com.example.comfyprompt.data.HostType
import com.example.comfyprompt.theme.AccentGray
import com.example.comfyprompt.theme.AccentRed
import com.example.comfyprompt.theme.CardGray
import com.example.comfyprompt.theme.DarkGray
import com.example.comfyprompt.theme.LightGray
import com.example.comfyprompt.theme.SuccessGreen

@Composable
fun ZoomableImage(
    model: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(20.dp),
    onTap: (() -> Unit)? = null,
    onSuccess: () -> Unit = {}
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val state = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        if (scale > 1f) {
            offset += offsetChange
        } else {
            offset = Offset.Zero
        }
    }

    Box(
        modifier = modifier
            .clip(shape)
            .transformable(state = state)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                    },
                    onTap = {
                        onTap?.invoke()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = model,
            contentDescription = "Zoomable Output Image",
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                ),
            contentScale = contentScale,
            onSuccess = { onSuccess() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptScreen(
    prompt: String,
    onPromptChange: (String) -> Unit,
    settings: AppSettings,
    onGenerateClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onEnhancerToggle: (Boolean) -> Unit,
    onSeedModeChange: (SeedMode, Long) -> Unit,
    onMegapixelChange: (String) -> Unit,
    onAspectRatioChange: (String) -> Unit
) {
    var seedInput by remember { mutableStateOf(settings.customSeedValue.toString()) }
    val context = LocalContext.current

    val configuration = LocalConfiguration.current
    val isExpandedScreen = configuration.screenWidthDp >= 600

    val promptInputCard = @Composable {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardGray),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "ENTER YOUR PROMPT",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = AccentGray
                    )
                    if (prompt.isNotEmpty()) {
                        Text(
                            text = "CLEAR",
                            modifier = Modifier
                                .clickable { onPromptChange("") }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentRed
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = prompt,
                    onValueChange = onPromptChange,
                    placeholder = { Text("e.g. A futuristic glass-domed biodome in a dark rainforest...", color = AccentGray) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = AccentGray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = DarkGray, thickness = 1.dp)
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Gemini Prompt Enhancer",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Switch(
                        checked = settings.enableEnhancer,
                        onCheckedChange = { onEnhancerToggle(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = Color.White,
                            uncheckedThumbColor = AccentGray,
                            uncheckedTrackColor = DarkGray
                        )
                    )
                }
            }
        }
    }

    val seedCard = @Composable {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardGray),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "SEED SETTING",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = AccentGray
                )

                // Seed Mode Selectors
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SeedMode.entries.forEach { mode ->
                        val isSelected = settings.seedMode == mode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Color.White else DarkGray)
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) Color.White else AccentGray,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    onSeedModeChange(mode, seedInput.toLongOrNull() ?: 42L)
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = mode.name,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.Black else Color.White
                            )
                        }
                    }
                }

                // Dynamic Seed Inputs
                when (settings.seedMode) {
                    SeedMode.Fixed -> {
                        Text(
                            "Using standard fixed seed: ${settings.fixedSeedValue}",
                            fontSize = 13.sp,
                            color = LightGray
                        )
                    }
                    SeedMode.LastUsed -> {
                        Text(
                            "Re-using last generated seed: ${settings.lastUsedSeedValue}",
                            fontSize = 13.sp,
                            color = LightGray
                        )
                    }
                    SeedMode.Random -> {
                        Text(
                            "Generating random seed on launch.",
                            fontSize = 13.sp,
                            color = LightGray
                        )
                    }
                    SeedMode.Custom -> {
                        OutlinedTextField(
                            value = seedInput,
                            onValueChange = { newValue ->
                                if (newValue.all { it.isDigit() }) {
                                    seedInput = newValue
                                    onSeedModeChange(SeedMode.Custom, newValue.toLongOrNull() ?: 42L)
                                }
                            },
                            label = { Text("Custom Seed Number", color = AccentGray) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.White,
                                unfocusedBorderColor = AccentGray,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            }
        }
    }

    val resolutionCard = @Composable {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardGray),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    "RESOLUTION & ASPECT RATIO",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = AccentGray
                )

                val megapixelOptions = listOf(
                    "0.1", "0.2", "0.3", "0.4", "0.5", "0.6", "0.7", "0.8", "0.9", "1.0",
                    "1.1", "1.2", "1.3", "1.4", "1.5", "1.6", "1.7", "1.8", "1.9", "2.0",
                    "2.1", "2.2", "2.3", "2.4", "2.5"
                )
                val aspectRatios = listOf(
                    "1:1 (Perfect Square)",
                    "2:3 (Classic Portrait)",
                    "3:4 (Golden Ratio)",
                    "3:5 (Elegant Vertical)",
                    "4:5 (Artistic Frame)",
                    "5:7 (Balanced Portrait)",
                    "5:8 (Tall Portrait)",
                    "7:9 (Modern Portrait)",
                    "9:16 (Slim Vertical)",
                    "9:19 (Tall Slim)",
                    "9:21 (Ultra Tall)",
                    "9:32 (Skyline)",
                    "3:2 (Golden Landscape)",
                    "4:3 (Classic Landscape)",
                    "5:3 (Wide Horizon)",
                    "5:4 (Balanced Frame)",
                    "7:5 (Elegant Landscape)",
                    "8:5 (Cinematic View)",
                    "9:7 (Artful Horizon)",
                    "16:9 (Panorama)",
                    "19:9 (Cinematic Ultrawide)",
                    "21:9 (Epic Ultrawide)",
                    "32:9 (Extreme Ultrawide)"
                )

                var mpDropdownExpanded by remember { mutableStateOf(false) }
                var arDropdownExpanded by remember { mutableStateOf(false) }

                // Aspect Ratio Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Aspect Ratio", fontSize = 14.sp, color = LightGray)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Minus Button
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(DarkGray)
                                .clickable {
                                    val currentIndex = aspectRatios.indexOf(settings.aspectRatio)
                                    if (currentIndex > 0) {
                                        val newVal = aspectRatios[currentIndex - 1]
                                        onAspectRatioChange(newVal)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("-", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }

                        // Dropdown Selector Box
                        Box(
                            modifier = Modifier
                                .width(160.dp)
                                .height(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(DarkGray)
                                .clickable { arDropdownExpanded = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(settings.aspectRatio, color = Color.White, fontSize = 12.sp, maxLines = 1, textAlign = TextAlign.Center)
                            DropdownMenu(
                                expanded = arDropdownExpanded,
                                onDismissRequest = { arDropdownExpanded = false },
                                modifier = Modifier.background(CardGray)
                            ) {
                                aspectRatios.forEach { ar ->
                                    DropdownMenuItem(
                                        text = { Text(ar, color = Color.White) },
                                        onClick = {
                                            onAspectRatioChange(ar)
                                            arDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // Plus Button
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(DarkGray)
                                .clickable {
                                    val currentIndex = aspectRatios.indexOf(settings.aspectRatio)
                                    if (currentIndex < aspectRatios.lastIndex && currentIndex != -1) {
                                        val newVal = aspectRatios[currentIndex + 1]
                                        onAspectRatioChange(newVal)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("+", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                    }
                }

                // Megapixel Size Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Megapixels (Size)", fontSize = 14.sp, color = LightGray)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Minus Button
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(DarkGray)
                                .clickable {
                                    val currentIndex = megapixelOptions.indexOf(settings.megapixel)
                                    if (currentIndex > 0) {
                                        val newVal = megapixelOptions[currentIndex - 1]
                                        onMegapixelChange(newVal)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("-", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }

                        // Dropdown Selector Box
                        Box(
                            modifier = Modifier
                                .width(90.dp)
                                .height(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(DarkGray)
                                .clickable { mpDropdownExpanded = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(settings.megapixel + " MP", color = Color.White, fontSize = 13.sp)
                            DropdownMenu(
                                expanded = mpDropdownExpanded,
                                onDismissRequest = { mpDropdownExpanded = false },
                                modifier = Modifier.background(CardGray)
                            ) {
                                megapixelOptions.forEach { mp ->
                                    DropdownMenuItem(
                                        text = { Text(mp + " MP", color = Color.White) },
                                        onClick = {
                                            onMegapixelChange(mp)
                                            mpDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // Plus Button
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(DarkGray)
                                .clickable {
                                    val currentIndex = megapixelOptions.indexOf(settings.megapixel)
                                    if (currentIndex < megapixelOptions.lastIndex && currentIndex != -1) {
                                        val newVal = megapixelOptions[currentIndex + 1]
                                        onMegapixelChange(newVal)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("+", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                    }
                }
            }
        }
    }

    val geminiStatusCard = @Composable {
        if (!settings.enableEnhancer) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardGray),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "⏸️ Gemini Prompt Enhancer is Disabled",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentGray,
                    modifier = Modifier.padding(12.dp),
                    textAlign = TextAlign.Center
                )
            }
        } else if (settings.geminiApiKey.isBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardGray),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "💡 Pro Tip: Input your Gemini API Key in Settings to enable automatic prompt enhancement!",
                    fontSize = 12.sp,
                    color = AccentGray,
                    modifier = Modifier.padding(12.dp)
                )
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardGray),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "✨ Gemini Prompt Enhancement Active (${settings.geminiModel})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = SuccessGreen,
                    modifier = Modifier.padding(12.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    val generateButton = @Composable {
        Button(
            onClick = {
                if (prompt.isNotBlank()) {
                    onGenerateClick(prompt)
                } else {
                    Toast.makeText(context, "Please enter a prompt.", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = Color.Black
            ),
            shape = RoundedCornerShape(28.dp)
        ) {
            Text("GENERATE", fontWeight = FontWeight.Bold, letterSpacing = 1.sp, fontSize = 16.sp)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "ComfyPrompt",
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = Color.White
                    )
                },
                actions = {
                    IconButton(onClick = onGalleryClick) {
                        Icon(Icons.Default.Image, contentDescription = "Creations Gallery", tint = Color.White)
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = Color.Black
    ) { paddingValues ->
        if (isExpandedScreen) {
            // Unfolded split screen Row layout
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1.1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    promptInputCard()
                    geminiStatusCard()
                    Spacer(modifier = Modifier.height(16.dp))
                    generateButton()
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    seedCard()
                    resolutionCard()
                }
            }
        } else {
            // Folded standard Column layout
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                promptInputCard()
                seedCard()
                resolutionCard()
                geminiStatusCard()
                Spacer(modifier = Modifier.height(16.dp))
                generateButton()
            }
        }
    }
}

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
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardGray),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Original Prompt",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = AccentGray
                )
                Text(
                    text = "\"$prompt\"",
                    fontSize = 14.sp,
                    color = LightGray,
                    textAlign = TextAlign.Center
                )
                if (progressInfo.enhancedPrompt != null && progressInfo.enhancedPrompt != prompt) {
                    HorizontalDivider(color = DarkGray, thickness = 1.dp)
                    Text(
                        text = "✨ Gemini Enhanced Prompt",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = SuccessGreen
                    )
                    Text(
                        text = "\"${progressInfo.enhancedPrompt}\"",
                        fontSize = 13.sp,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    val progressIndicators = @Composable {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            LinearProgressIndicator(
                progress = { progressInfo.percent },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = Color.White,
                trackColor = DarkGray
            )

            Text(
                text = progressInfo.statusText,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
        }
    }

    val imageCanvas = @Composable {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
                .background(CardGray),
            contentAlignment = Alignment.Center
        ) {
            val activeImage = progressInfo.finalImage ?: progressInfo.baseImage

            if (activeImage != null) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = activeImage,
                        contentDescription = "Intermediate Image Output",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )

                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.7f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("ERNIE Image Output", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(color = Color.White)
                    Text(
                        text = when (progressInfo.state) {
                            GenerationState.EnhancingPrompt -> "Prompt Expander running..."
                            GenerationState.ConnectingComfy -> "Connecting to ComfyUI..."
                            else -> "Waiting for generator..."
                        },
                        color = AccentGray,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }

    val stopButton = @Composable {
        if (progressInfo.state == GenerationState.Completed) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { onSaveClick(progressInfo.finalImage ?: "") },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Text("DOWNLOAD", fontWeight = FontWeight.Bold, letterSpacing = 1.sp, fontSize = 16.sp)
                }
                Row(
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { onShareClick(progressInfo.finalImage ?: "") },
                            modifier = Modifier.fillMaxSize(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = BorderStroke(1.dp, Color.White),
                            shape = RoundedCornerShape(28.dp)
                        ) {
                            Text("SHARE", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        }
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = onStopClick,
                            modifier = Modifier.fillMaxSize(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = BorderStroke(1.dp, Color.White),
                            shape = RoundedCornerShape(28.dp)
                        ) {
                            Text("FINISH (GO BACK)", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        }
                    }
                }
            }
        } else {
            OutlinedButton(
                onClick = onStopClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentRed),
                border = BorderStroke(1.dp, AccentRed),
                shape = RoundedCornerShape(28.dp)
            ) {
                Text("STOP GENERATION", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
        }
    }

    Scaffold(
        containerColor = Color.Black
    ) { paddingValues ->
        if (isExpandedScreen) {
            // Unfolded split screen Row layout
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
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
                        text = "Processing Generation",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    promptSummaryCard()
                    progressIndicators()
                    Spacer(modifier = Modifier.height(16.dp))
                    stopButton()
                }

                Box(
                    modifier = Modifier
                        .weight(1.2f)
                        .fillMaxHeight()
                ) {
                    imageCanvas()
                }
            }
        } else {
            // Folded stacked Column layout — scrollable to prevent clinking or warping
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Processing Generation",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                promptSummaryCard()
                Spacer(modifier = Modifier.height(8.dp))
                progressIndicators()
                Spacer(modifier = Modifier.height(16.dp))

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

@Composable
fun ResultScreen(
    finalImageUrl: String,
    seed: Long,
    onSaveClick: () -> Unit,
    onShareClick: () -> Unit,
    onReRunClick: () -> Unit
) {
    var isFullScreen by remember { mutableStateOf(false) }
    val configuration = LocalConfiguration.current
    val isExpandedScreen = configuration.screenWidthDp >= 600

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
                containerColor = Color.White,
                contentColor = Color.Black
            ),
            shape = RoundedCornerShape(28.dp)
        ) {
            Text("DOWNLOAD", fontWeight = FontWeight.Bold, letterSpacing = 1.sp, fontSize = 16.sp)
        }
    }

    val imageCanvas = @Composable {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(20.dp))
                .background(CardGray),
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
        BackHandler(enabled = isFullScreen) {
            isFullScreen = false
        }

        Scaffold(
            containerColor = Color.Black
        ) { paddingValues ->
            if (isExpandedScreen) {
                // Unfolded side-by-side Row layout
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
            } else {
                // Folded stacked Column layout
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

        // Full Screen Immersive Overlay
        androidx.compose.animation.AnimatedVisibility(
            visible = isFullScreen,
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut()
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
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    savedWorkflows: List<String>,
    onSaveClick: (AppSettings) -> Unit,
    onBackClick: () -> Unit,
    onDownloadWorkflowClick: () -> Unit
) {
    var serverUrl by remember { mutableStateOf(settings.serverUrl) }
    var geminiKey by remember { mutableStateOf(settings.geminiApiKey) }
    var geminiModel by remember { mutableStateOf(settings.geminiModel) }
    var chatgptKey by remember { mutableStateOf(settings.chatgptApiKey) }
    var chatgptModel by remember { mutableStateOf(settings.chatgptModel) }
    var claudeKey by remember { mutableStateOf(settings.claudeApiKey) }
    var claudeModel by remember { mutableStateOf(settings.claudeModel) }
    var grokKey by remember { mutableStateOf(settings.grokApiKey) }
    var grokModel by remember { mutableStateOf(settings.grokModel) }
    var apiProvider by remember { mutableStateOf(settings.apiProvider) }
    var outputFormat by remember { mutableStateOf(settings.outputFormat) }
    var showApiKey by remember { mutableStateOf(false) }
    var workflowToUse by remember { mutableStateOf(settings.workflowToUse) }

    // Host connection states
    var selectedHostType by remember { mutableStateOf(settings.hostType) }
    var localIpAddress by remember { mutableStateOf(settings.localIpAddress) }
    var comfyDeployApiKey by remember { mutableStateOf(settings.comfyDeployApiKey) }
    var comfyDeployId by remember { mutableStateOf(settings.comfyDeployId) }
    var runpodApiKey by remember { mutableStateOf(settings.runpodApiKey) }
    var runpodEndpointId by remember { mutableStateOf(settings.runpodEndpointId) }
    var falAiApiKey by remember { mutableStateOf(settings.falAiApiKey) }
    var falAiEndpointSlug by remember { mutableStateOf(settings.falAiEndpointSlug) }

    var hostTypeDropdownExpanded by remember { mutableStateOf(false) }
    var showComfyDeployApiKey by remember { mutableStateOf(false) }
    var showRunpodApiKey by remember { mutableStateOf(false) }
    var showFalAiApiKey by remember { mutableStateOf(false) }

    val providers = listOf("Gemini", "ChatGPT", "Claude", "Grok")
    val formats = listOf("PNG", "JPEG", "WEBP")

    val geminiModels = listOf(
        "gemini-1.5-flash",
        "gemini-1.5-pro",
        "gemini-2.0-flash",
        "gemini-2.5-flash",
        "gemini-3.5-flash",
        "gemini-flash-lite-3.1"
    )
    val chatgptModels = listOf(
        "gpt-4o",
        "gpt-4o-mini",
        "gpt-3.5-turbo",
        "o1-mini"
    )
    val claudeModels = listOf(
        "claude-3-5-sonnet-latest",
        "claude-3-5-haiku-latest",
        "claude-3-opus-latest"
    )
    val grokModels = listOf(
        "grok-2-1212",
        "grok-2-vision-1212"
    )

    var providerDropdownExpanded by remember { mutableStateOf(false) }
    var modelDropdownExpanded by remember { mutableStateOf(false) }
    var formatDropdownExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Server Settings Card (Refactored to Host Connection Configuration Card)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardGray),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "HOST CONNECTION CONFIGURATION",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = AccentGray
                    )

                    // Host Type Selector
                    Column {
                        Text("Host Connection Type", style = MaterialTheme.typography.bodySmall, color = AccentGray)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(DarkGray)
                                .border(1.dp, AccentGray, RoundedCornerShape(8.dp))
                                .clickable { hostTypeDropdownExpanded = true }
                                .padding(16.dp)
                        ) {
                            val hostTypeLabel = when (selectedHostType) {
                                HostType.LOCAL -> "Local"
                                HostType.COMFY_DEPLOY -> "ComfyDeploy"
                                HostType.RUNPOD -> "RunPod Serverless"
                                HostType.FAL_AI -> "Fal.ai"
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(hostTypeLabel, color = Color.White)
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = Color.White
                                )
                            }
                            DropdownMenu(
                                expanded = hostTypeDropdownExpanded,
                                onDismissRequest = { hostTypeDropdownExpanded = false },
                                modifier = Modifier.background(CardGray)
                            ) {
                                HostType.entries.forEach { type ->
                                    val label = when (type) {
                                        HostType.LOCAL -> "Local"
                                        HostType.COMFY_DEPLOY -> "ComfyDeploy"
                                        HostType.RUNPOD -> "RunPod Serverless"
                                        HostType.FAL_AI -> "Fal.ai"
                                    }
                                    DropdownMenuItem(
                                        text = { Text(label, color = Color.White) },
                                        onClick = {
                                            selectedHostType = type
                                            hostTypeDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Animated visibility layout for active HostType
                    AnimatedVisibility(
                        visible = selectedHostType == HostType.LOCAL,
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "Local Server Address",
                                style = MaterialTheme.typography.bodySmall,
                                color = AccentGray
                            )
                            OutlinedTextField(
                                value = localIpAddress,
                                onValueChange = { localIpAddress = it },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.White,
                                    unfocusedBorderColor = AccentGray,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                shape = RoundedCornerShape(8.dp),
                                placeholder = { Text("http://10.0.2.2:8188", color = AccentGray) }
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = selectedHostType == HostType.COMFY_DEPLOY,
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    "ComfyDeploy API Key",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AccentGray
                                )
                                OutlinedTextField(
                                    value = comfyDeployApiKey,
                                    onValueChange = { comfyDeployApiKey = it },
                                    visualTransformation = if (showComfyDeployApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color.White,
                                        unfocusedBorderColor = AccentGray,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    trailingIcon = {
                                        Text(
                                            text = if (showComfyDeployApiKey) "HIDE" else "SHOW",
                                            modifier = Modifier
                                                .padding(end = 12.dp)
                                                .clickable { showComfyDeployApiKey = !showComfyDeployApiKey },
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                )
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    "ComfyDeploy Deployment ID",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AccentGray
                                )
                                OutlinedTextField(
                                    value = comfyDeployId,
                                    onValueChange = { comfyDeployId = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color.White,
                                        unfocusedBorderColor = AccentGray,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = selectedHostType == HostType.RUNPOD,
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    "RunPod API Key",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AccentGray
                                )
                                OutlinedTextField(
                                    value = runpodApiKey,
                                    onValueChange = { runpodApiKey = it },
                                    visualTransformation = if (showRunpodApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color.White,
                                        unfocusedBorderColor = AccentGray,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    trailingIcon = {
                                        Text(
                                            text = if (showRunpodApiKey) "HIDE" else "SHOW",
                                            modifier = Modifier
                                                .padding(end = 12.dp)
                                                .clickable { showRunpodApiKey = !showRunpodApiKey },
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                )
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    "RunPod Endpoint ID",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AccentGray
                                )
                                OutlinedTextField(
                                    value = runpodEndpointId,
                                    onValueChange = { runpodEndpointId = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color.White,
                                        unfocusedBorderColor = AccentGray,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = selectedHostType == HostType.FAL_AI,
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    "Fal.ai API Key",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AccentGray
                                )
                                OutlinedTextField(
                                    value = falAiApiKey,
                                    onValueChange = { falAiApiKey = it },
                                    visualTransformation = if (showFalAiApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color.White,
                                        unfocusedBorderColor = AccentGray,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    trailingIcon = {
                                        Text(
                                            text = if (showFalAiApiKey) "HIDE" else "SHOW",
                                            modifier = Modifier
                                                .padding(end = 12.dp)
                                                .clickable { showFalAiApiKey = !showFalAiApiKey },
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                )
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    "Fal.ai Endpoint Slug",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AccentGray
                                )
                                OutlinedTextField(
                                    value = falAiEndpointSlug,
                                    onValueChange = { falAiEndpointSlug = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color.White,
                                        unfocusedBorderColor = AccentGray,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Workflow to Use Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardGray),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "WORKFLOW TO USE",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = AccentGray
                    )
                    
                    var workflowDropdownExpanded by remember { mutableStateOf(false) }
                    
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { workflowDropdownExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, AccentGray),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (workflowToUse.isBlank()) "Built-in Ernie Workflow (Default)" else workflowToUse,
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = Color.White
                                )
                            }
                        }
                        
                        DropdownMenu(
                            expanded = workflowDropdownExpanded,
                            onDismissRequest = { workflowDropdownExpanded = false },
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .background(CardGray)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Built-in Ernie Workflow (Default)", color = Color.White) },
                                onClick = {
                                    workflowToUse = ""
                                    workflowDropdownExpanded = false
                                }
                            )
                            savedWorkflows.forEach { wf ->
                                DropdownMenuItem(
                                    text = { Text(wf, color = Color.White) },
                                    onClick = {
                                        workflowToUse = wf
                                        workflowDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    if (savedWorkflows.isEmpty()) {
                        Text(
                            text = "No saved workflows fetched. Make sure ComfyUI is online and workflows are saved under workflows/ folder.",
                            style = MaterialTheme.typography.bodySmall,
                            color = AccentGray
                        )
                    } else {
                        Text(
                            text = "Tip: Custom workflows must be saved in ComfyUI API format (Enable Dev Mode -> 'Save (API format)').",
                            style = MaterialTheme.typography.bodySmall,
                            color = AccentGray
                        )
                    }
                }
            }

            // ComfyUI Workflow Export Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardGray),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "COMFYUI WORKFLOW EXPORT",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = AccentGray
                    )
                    Text(
                        "Download the cleaned ERNIE 1-stage workflow.json. Drag and drop it on ComfyUI to automatically install and link any required custom nodes.",
                        fontSize = 13.sp,
                        color = LightGray
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = onDownloadWorkflowClick,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text("DOWNLOAD WORKFLOW.JSON", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            // Prompt Enhancer Settings Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardGray),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "PROMPT EXPANDER CONFIGURATION",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = AccentGray
                    )

                    // Provider Dropdown
                    Column {
                        Text("Active AI Provider", style = MaterialTheme.typography.bodySmall, color = AccentGray)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(DarkGray)
                                .border(1.dp, AccentGray, RoundedCornerShape(8.dp))
                                .clickable { providerDropdownExpanded = true }
                                .padding(16.dp)
                        ) {
                            Text(apiProvider, color = Color.White)
                            DropdownMenu(
                                expanded = providerDropdownExpanded,
                                onDismissRequest = { providerDropdownExpanded = false },
                                modifier = Modifier.background(CardGray)
                            ) {
                                providers.forEach { prov ->
                                    DropdownMenuItem(
                                        text = { Text(prov, color = Color.White) },
                                        onClick = {
                                            apiProvider = prov
                                            providerDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = DarkGray, thickness = 1.dp)

                    // Active API Key input field depending on the provider
                    val currentKey: String
                    val onKeyChange: (String) -> Unit
                    val keyLabel: String
                    when (apiProvider) {
                        "ChatGPT" -> {
                            currentKey = chatgptKey
                            onKeyChange = { chatgptKey = it }
                            keyLabel = "ChatGPT API Key (OpenAI)"
                        }
                        "Claude" -> {
                            currentKey = claudeKey
                            onKeyChange = { claudeKey = it }
                            keyLabel = "Claude API Key (Anthropic)"
                        }
                        "Grok" -> {
                            currentKey = grokKey
                            onKeyChange = { grokKey = it }
                            keyLabel = "Grok API Key (xAI)"
                        }
                        else -> {
                            currentKey = geminiKey
                            onKeyChange = { geminiKey = it }
                            keyLabel = "Gemini API Key (Google AI Studio)"
                        }
                    }

                    OutlinedTextField(
                        value = currentKey,
                        onValueChange = onKeyChange,
                        label = { Text(keyLabel, color = AccentGray) },
                        visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = AccentGray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        trailingIcon = {
                            Text(
                                text = if (showApiKey) "HIDE" else "SHOW",
                                modifier = Modifier
                                    .padding(end = 12.dp)
                                    .clickable { showApiKey = !showApiKey },
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    )

                    // Active Model Dropdown depending on the provider
                    val currentModel: String
                    val onModelChange: (String) -> Unit
                    val modelsList: List<String>
                    when (apiProvider) {
                        "ChatGPT" -> {
                            currentModel = chatgptModel
                            onModelChange = { chatgptModel = it }
                            modelsList = chatgptModels
                        }
                        "Claude" -> {
                            currentModel = claudeModel
                            onModelChange = { claudeModel = it }
                            modelsList = claudeModels
                        }
                        "Grok" -> {
                            currentModel = grokModel
                            onModelChange = { grokModel = it }
                            modelsList = grokModels
                        }
                        else -> {
                            currentModel = geminiModel
                            onModelChange = { geminiModel = it }
                            modelsList = geminiModels
                        }
                    }

                    Column {
                        Text("Selected Model ($apiProvider)", style = MaterialTheme.typography.bodySmall, color = AccentGray)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(DarkGray)
                                .border(1.dp, AccentGray, RoundedCornerShape(8.dp))
                                .clickable { modelDropdownExpanded = true }
                                .padding(16.dp)
                        ) {
                            Text(currentModel, color = Color.White)
                            DropdownMenu(
                                expanded = modelDropdownExpanded,
                                onDismissRequest = { modelDropdownExpanded = false },
                                modifier = Modifier.background(CardGray)
                            ) {
                                modelsList.forEach { modelName ->
                                    DropdownMenuItem(
                                        text = { Text(modelName, color = Color.White) },
                                        onClick = {
                                            onModelChange(modelName)
                                            modelDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Output Format Settings Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardGray),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "EXPORT IMAGE FORMAT",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = AccentGray
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkGray)
                            .border(1.dp, AccentGray, RoundedCornerShape(8.dp))
                            .clickable { formatDropdownExpanded = true }
                            .padding(16.dp)
                    ) {
                        Text(outputFormat, color = Color.White)
                        DropdownMenu(
                            expanded = formatDropdownExpanded,
                            onDismissRequest = { formatDropdownExpanded = false },
                            modifier = Modifier.background(CardGray)
                        ) {
                            formats.forEach { fmt ->
                                DropdownMenuItem(
                                    text = { Text(fmt, color = Color.White) },
                                    onClick = {
                                        outputFormat = fmt
                                        formatDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Button: Save Settings
            Button(
                onClick = {
                    val updatedSettings = settings.copy(
                        serverUrl = if (selectedHostType == HostType.LOCAL) localIpAddress else serverUrl,
                        hostType = selectedHostType,
                        localIpAddress = localIpAddress,
                        comfyDeployApiKey = comfyDeployApiKey,
                        comfyDeployId = comfyDeployId,
                        runpodApiKey = runpodApiKey,
                        runpodEndpointId = runpodEndpointId,
                        falAiApiKey = falAiApiKey,
                        falAiEndpointSlug = falAiEndpointSlug,
                        geminiApiKey = geminiKey,
                        geminiModel = geminiModel,
                        chatgptApiKey = chatgptKey,
                        chatgptModel = chatgptModel,
                        claudeApiKey = claudeKey,
                        claudeModel = claudeModel,
                        grokApiKey = grokKey,
                        grokModel = grokModel,
                        apiProvider = apiProvider,
                        outputFormat = outputFormat,
                        workflowToUse = workflowToUse
                    )
                    onSaveClick(updatedSettings)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(28.dp)
            ) {
                Text("SAVE SETTINGS", fontWeight = FontWeight.Bold, letterSpacing = 1.sp, fontSize = 16.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    items: List<GalleryItem>,
    onBackClick: () -> Unit,
    onReRunClick: (String) -> Unit,
    onShareClick: (String) -> Unit,
    onDeleteClick: (String) -> Unit,
    onDownloadClick: (String) -> Unit
) {
    val configuration = LocalConfiguration.current
    val isExpandedScreen = configuration.screenWidthDp >= 600
    val columnsCount = if (isExpandedScreen) 4 else 2

    var selectedItem by remember { mutableStateOf<GalleryItem?>(null) }
    var isFullScreen by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Generation History", fontWeight = FontWeight.Bold, color = Color.White) },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
                )
            },
            containerColor = Color.Black
        ) { paddingValues ->
            if (items.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Image, contentDescription = "Empty History", tint = AccentGray, modifier = Modifier.size(64.dp))
                        Text("No past creations found.", color = LightGray, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text("Your successful generations will appear here.", color = AccentGray, fontSize = 13.sp)
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columnsCount),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items, key = { it.id }) { item ->
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(CardGray)
                                .clickable { selectedItem = item }
                        ) {
                            AsyncImage(
                                model = item.imageUrl,
                                contentDescription = item.prompt,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            }
        }

        // Expanded detail popup sheet
        AnimatedVisibility(
            visible = selectedItem != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            val item = selectedItem
            if (item != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.9f))
                        .clickable { selectedItem = null },
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(if (isExpandedScreen) 0.8f else 0.95f)
                            .fillMaxHeight(if (isExpandedScreen) 0.85f else 0.92f)
                            .clickable(enabled = false) {}, // Prevent clicks closing card
                        colors = CardDefaults.cardColors(containerColor = CardGray),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Creation Details", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                                IconButton(onClick = { selectedItem = null }) {
                                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                                }
                            }

                            // Responsive content
                            if (isExpandedScreen) {
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    // Left side: large image preview
                                    Box(
                                        modifier = Modifier
                                            .weight(1.2f)
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(DarkGray)
                                            .clickable { isFullScreen = true },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AsyncImage(
                                            model = item.imageUrl,
                                            contentDescription = "Zoomed Gallery Preview",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Fit
                                        )
                                    }

                                    // Right side: Text details inside scrollable container
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .verticalScroll(rememberScrollState()),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        GalleryDetailsContent(item, clipboardManager, context)
                                    }
                                }
                            } else {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(260.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(DarkGray)
                                            .clickable { isFullScreen = true },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AsyncImage(
                                            model = item.imageUrl,
                                            contentDescription = "Zoomed Gallery Preview",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Fit
                                        )
                                    }
                                    GalleryDetailsContent(item, clipboardManager, context)
                                }
                            }

                            // Responsive Action buttons at the bottom
                            if (isExpandedScreen) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = {
                                            onDeleteClick(item.id)
                                            selectedItem = null
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = AccentRed, contentColor = Color.White),
                                        shape = RoundedCornerShape(20.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Delete", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }

                                    OutlinedButton(
                                        onClick = { onShareClick(item.imageUrl) },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                        border = BorderStroke(1.dp, Color.White),
                                        shape = RoundedCornerShape(20.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = "Share", modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Share", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }

                                    OutlinedButton(
                                        onClick = { onDownloadClick(item.imageUrl) },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                        border = BorderStroke(1.dp, Color.White),
                                        shape = RoundedCornerShape(20.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = "Download", modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Save", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Button(
                                        onClick = {
                                            onReRunClick(item.prompt)
                                            selectedItem = null
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                                        shape = RoundedCornerShape(20.dp),
                                        modifier = Modifier.weight(1.2f)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = "Re-run", modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Load Prompt", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            } else {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            onReRunClick(item.prompt)
                                            selectedItem = null
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                                        shape = RoundedCornerShape(20.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = "Re-run", modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Load Prompt", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = { onShareClick(item.imageUrl) },
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                            border = BorderStroke(1.dp, Color.White),
                                            shape = RoundedCornerShape(20.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.Share, contentDescription = "Share", modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Share", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }

                                        OutlinedButton(
                                            onClick = { onDownloadClick(item.imageUrl) },
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                            border = BorderStroke(1.dp, Color.White),
                                            shape = RoundedCornerShape(20.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.Download, contentDescription = "Download", modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Save", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }

                                        Button(
                                            onClick = {
                                                onDeleteClick(item.id)
                                                selectedItem = null
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = AccentRed, contentColor = Color.White),
                                            shape = RoundedCornerShape(20.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Delete", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Full Screen Immersive Overlay for Gallery
        androidx.compose.animation.AnimatedVisibility(
            visible = isFullScreen && selectedItem != null,
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut()
        ) {
            val item = selectedItem
            if (item != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    ZoomableImage(
                        model = item.imageUrl,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                        shape = androidx.compose.ui.graphics.RectangleShape,
                        onTap = { isFullScreen = false }
                    )
                }
            }
        }
    }
}

@Composable
fun GalleryDetailsContent(
    item: GalleryItem,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager,
    context: android.content.Context
) {
    // Original prompt
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkGray),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Original Prompt", fontSize = 11.sp, color = AccentGray, fontWeight = FontWeight.Bold)
                IconButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(item.prompt))
                        Toast.makeText(context, "Copied original prompt", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy Prompt", tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(item.prompt, fontSize = 13.sp, color = Color.White)
        }
    }

    // Enhanced prompt if present
    if (item.enhancedPrompt != null && item.enhancedPrompt != item.prompt) {
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkGray),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Gemini Enhanced Prompt", fontSize = 11.sp, color = SuccessGreen, fontWeight = FontWeight.Bold)
                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(item.enhancedPrompt))
                            Toast.makeText(context, "Copied enhanced prompt", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy Enhanced", tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(item.enhancedPrompt, fontSize = 12.sp, color = LightGray)
            }
        }
    }

    // Metadata (Seed)
    Text("Seed: ${item.seed}", fontSize = 12.sp, color = AccentGray, fontWeight = FontWeight.Medium)
}
