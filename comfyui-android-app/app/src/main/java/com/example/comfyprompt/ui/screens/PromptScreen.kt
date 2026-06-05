package com.example.comfyprompt.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.comfyprompt.data.AppSettings
import com.example.comfyprompt.data.SeedMode
import com.example.comfyprompt.theme.AccentGray
import com.example.comfyprompt.theme.AccentRed
import com.example.comfyprompt.theme.CardGray
import com.example.comfyprompt.theme.DarkGray
import com.example.comfyprompt.theme.LightGray
import com.example.comfyprompt.theme.SuccessGreen

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
    onAspectRatioChange: (String) -> Unit,
    cooldownSeconds: Int = 0
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
        val provider = settings.apiProvider
        val hasKey = when (provider) {
            "ChatGPT" -> settings.chatgptApiKey.isNotBlank()
            "Claude" -> settings.claudeApiKey.isNotBlank()
            "Grok" -> settings.grokApiKey.isNotBlank()
            "Local / Custom" -> true
            else -> settings.geminiApiKey.isNotBlank()
        }
        val activeModel = when (provider) {
            "ChatGPT" -> settings.chatgptModel
            "Claude" -> settings.claudeModel
            "Grok" -> settings.grokModel
            "Local / Custom" -> settings.localLlmSelectedModel
            else -> settings.geminiModel
        }

        if (!settings.enableEnhancer) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardGray),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "⏸️ $provider Prompt Enhancer is Disabled",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentGray,
                    modifier = Modifier.padding(12.dp),
                    textAlign = TextAlign.Center
                )
            }
        } else if (!hasKey) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardGray),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "💡 Pro Tip: Input your $provider API Key in Settings to enable automatic prompt enhancement!",
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
                    text = "✨ $provider Prompt Enhancement Active ($activeModel)",
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
            enabled = cooldownSeconds <= 0,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = Color.Black,
                disabledContainerColor = DarkGray,
                disabledContentColor = AccentGray
            ),
            shape = RoundedCornerShape(28.dp)
        ) {
            val buttonText = if (cooldownSeconds > 0) "COOLDOWN ($cooldownSeconds)" else "GENERATE"
            Text(buttonText, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, fontSize = 16.sp)
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
