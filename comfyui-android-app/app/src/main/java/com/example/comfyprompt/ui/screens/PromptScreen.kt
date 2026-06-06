package com.example.comfyprompt.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.comfyprompt.data.AppSettings
import com.example.comfyprompt.data.SeedMode
import com.example.comfyprompt.theme.AccentGray
import com.example.comfyprompt.theme.AccentRed
import com.example.comfyprompt.theme.SuccessGreen

data class StylePreset(
    val name: String,
    val cue: String,
    val icon: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptScreen(
    prompt: String,
    onPromptChange: (String) -> Unit,
    settings: AppSettings,
    onGenerateClick: (String, Uri?) -> Unit,
    onSettingsClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onEnhancerToggle: (Boolean) -> Unit,
    onSeedModeChange: (SeedMode, Long) -> Unit,
    onMegapixelChange: (String) -> Unit,
    onAspectRatioChange: (String) -> Unit,
    onStylePresetChange: (String) -> Unit,
    savedWorkflows: List<String>,
    onWorkflowChange: (String) -> Unit,
    workflowGroups: List<String> = emptyList(),
    bypassedGroups: Set<String> = emptySet(),
    onToggleGroupBypass: (String) -> Unit = {},
    onToggleAllGroups: (Boolean) -> Unit = {},
    cooldownSeconds: Int = 0
) {
    var seedInput by remember { mutableStateOf(settings.customSeedValue.toString()) }
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isExpandedScreen = configuration.screenWidthDp >= 600

    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedImageUri = uri
    }
    var showWorkflowSheet by remember { mutableStateOf(false) }

    var workflowExpanded by remember { mutableStateOf(false) }
    var promptExpanded by remember { mutableStateOf(true) }
    var stylePresetExpanded by remember { mutableStateOf(false) }
    var resolutionExpanded by remember { mutableStateOf(false) }
    var stagesExpanded by remember { mutableStateOf(false) }
    var inputImageExpanded by remember { mutableStateOf(false) }
    var seedExpanded by remember { mutableStateOf(false) }

    val promptInputAccordion = @Composable {
        AccordionSection(
            title = "PROMPT",
            isExpanded = promptExpanded,
            onHeaderClick = { promptExpanded = !promptExpanded }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "ENTER YOUR PROMPT",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                OutlinedTextField(
                    value = prompt,
                    onValueChange = onPromptChange,
                    placeholder = { Text("e.g. A futuristic glass-domed biodome in a dark rainforest...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Gemini Prompt Enhancer",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Switch(
                        checked = settings.enableEnhancer,
                        onCheckedChange = { onEnhancerToggle(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }
            }
        }
    }

    val seedSettingAccordion = @Composable {
        AccordionSection(
            title = "SEED SETTING",
            isExpanded = seedExpanded,
            onHeaderClick = { seedExpanded = !seedExpanded }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                                .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
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
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                when (settings.seedMode) {
                    SeedMode.Fixed -> {
                        Text(
                            "Using standard fixed seed: ${settings.fixedSeedValue}",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    SeedMode.LastUsed -> {
                        Text(
                            "Re-using last generated seed: ${settings.lastUsedSeedValue}",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    SeedMode.Random -> {
                        Text(
                            "Generating random seed on launch.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                            label = { Text("Custom Seed Number", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            }
        }
    }

    val resolutionAccordion = @Composable {
        AccordionSection(
            title = "RESOLUTION & ASPECT RATIO",
            isExpanded = resolutionExpanded,
            onHeaderClick = { resolutionExpanded = !resolutionExpanded }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Aspect Ratio", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .clickable {
                                    val currentIndex = aspectRatios.indexOf(settings.aspectRatio)
                                    if (currentIndex > 0) {
                                        val newVal = aspectRatios[currentIndex - 1]
                                        onAspectRatioChange(newVal)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("-", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }

                        Box(
                            modifier = Modifier
                                .width(200.dp)
                                .height(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .clickable { arDropdownExpanded = true }
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AspectRatioIcon(aspectRatio = settings.aspectRatio)
                                Text(
                                    text = settings.aspectRatio,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = arDropdownExpanded,
                                onDismissRequest = { arDropdownExpanded = false },
                                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                aspectRatios.forEach { ar ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                AspectRatioIcon(aspectRatio = ar)
                                                Text(ar, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                                            }
                                        },
                                        onClick = {
                                            onAspectRatioChange(ar)
                                            arDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .clickable {
                                    val currentIndex = aspectRatios.indexOf(settings.aspectRatio)
                                    if (currentIndex < aspectRatios.lastIndex && currentIndex != -1) {
                                        val newVal = aspectRatios[currentIndex + 1]
                                        onAspectRatioChange(newVal)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("+", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Megapixels (Size)", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .clickable {
                                    val currentIndex = megapixelOptions.indexOf(settings.megapixel)
                                    if (currentIndex > 0) {
                                        val newVal = megapixelOptions[currentIndex - 1]
                                        onMegapixelChange(newVal)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("-", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }

                        Box(
                            modifier = Modifier
                                .width(90.dp)
                                .height(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .clickable { mpDropdownExpanded = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(settings.megapixel + " MP", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                            DropdownMenu(
                                expanded = mpDropdownExpanded,
                                onDismissRequest = { mpDropdownExpanded = false },
                                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                megapixelOptions.forEach { mp ->
                                    DropdownMenuItem(
                                        text = { Text(mp + " MP", color = MaterialTheme.colorScheme.onSurface) },
                                        onClick = {
                                            onMegapixelChange(mp)
                                            mpDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .clickable {
                                    val currentIndex = megapixelOptions.indexOf(settings.megapixel)
                                    if (currentIndex < megapixelOptions.lastIndex && currentIndex != -1) {
                                        val newVal = megapixelOptions[currentIndex + 1]
                                        onMegapixelChange(newVal)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("+", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 18.sp)
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
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "⏸️ $provider Prompt Enhancer is Disabled",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp),
                    textAlign = TextAlign.Center
                )
            }
        } else if (!hasKey) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "💡 Pro Tip: Input your $provider API Key in Settings to enable automatic prompt enhancement!",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp)
                )
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
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

    val stylePresets = remember {
        listOf(
            StylePreset("Cinematic", "cinematic style, high detailed, dramatic lighting, 8k resolution", "🎬"),
            StylePreset("Anime", "anime style, vibrant colors, detailed line art, masterpiece", "🌸"),
            StylePreset("Photorealistic", "award-winning photo, 35mm lens, photorealistic, dslr, high detailed", "📷"),
            StylePreset("3D Render", "3d render, octane render, detailed, trending on artstation, masterpiece", "📦"),
            StylePreset("Cyberpunk", "cyberpunk style, neon lights, high tech, futuristic, detailed", "🏙️"),
            StylePreset("Fantasy", "fantasy digital painting, ethereal, magical, highly detailed, artstation", "🔮"),
            StylePreset("Comic Book", "comic book art style, bold lines, vibrant, detailed, hand-drawn", "💥")
        )
    }

    val workflowSelectorAccordion = @Composable {
        val activeWorkflowDisplay = when (settings.workflowToUse) {
            "" -> "Default Workflow"
            "imported_workflow.json" -> "Imported Local Workflow"
            else -> settings.workflowToUse
        }
        AccordionSection(
            title = "ACTIVE WORKFLOW",
            isExpanded = workflowExpanded,
            onHeaderClick = { workflowExpanded = !workflowExpanded }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Current: $activeWorkflowDisplay",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = { showWorkflowSheet = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Change Workflow", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    val workflowStagesAccordion = @Composable {
        if (workflowGroups.isNotEmpty()) {
            AccordionSection(
                title = "WORKFLOW STAGES (GROUPS)",
                isExpanded = stagesExpanded,
                onHeaderClick = { stagesExpanded = !stagesExpanded }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val allEnabled = bypassedGroups.isEmpty()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onToggleAllGroups(!allEnabled) }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ALL STAGES",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Switch(
                            checked = allEnabled,
                            onCheckedChange = { onToggleAllGroups(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surface
                            )
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 1.dp)

                    workflowGroups.forEach { group ->
                        val isBypassed = bypassedGroups.contains(group)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onToggleGroupBypass(group) }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = group,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isBypassed) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface
                            )
                            Switch(
                                checked = !isBypassed,
                                onCheckedChange = { onToggleGroupBypass(group) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                                    uncheckedTrackColor = MaterialTheme.colorScheme.surface
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    val stylePresetAccordion = @Composable {
        AccordionSection(
            title = "STYLE PRESET",
            isExpanded = stylePresetExpanded,
            onHeaderClick = { stylePresetExpanded = !stylePresetExpanded }
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = settings.selectedStylePreset == "",
                        onClick = { onStylePresetChange("") },
                        label = { Text("None") },
                        leadingIcon = { Text("❌", fontSize = 12.sp) }
                    )
                    stylePresets.forEach { preset ->
                        FilterChip(
                            selected = settings.selectedStylePreset == preset.name,
                            onClick = { onStylePresetChange(preset.name) },
                            label = { Text(preset.name) },
                            leadingIcon = { Text(preset.icon, fontSize = 12.sp) }
                        )
                    }
                }
            }
        }
    }

    val inputImageAccordion = @Composable {
        AccordionSection(
            title = "INPUT IMAGE (IMAGE-TO-IMAGE / CONTROLNET)",
            isExpanded = inputImageExpanded,
            onHeaderClick = { inputImageExpanded = !inputImageExpanded }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selectedImageUri != null) {
                    AsyncImage(
                        model = selectedImageUri,
                        contentDescription = "Selected Image",
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Image Selected", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            text = "CLEAR",
                            modifier = Modifier
                                .clickable { selectedImageUri = null }
                                .padding(vertical = 4.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentRed
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                            .clickable { imagePickerLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add Photo", tint = MaterialTheme.colorScheme.primary)
                            Text("Select Input Image...", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    val generateButton = @Composable {
        Button(
            onClick = {
                if (prompt.isNotBlank()) {
                    val finalPrompt = if (settings.selectedStylePreset.isNotEmpty()) {
                        val cue = stylePresets.firstOrNull { it.name == settings.selectedStylePreset }?.cue
                        if (cue != null) "$prompt, $cue" else prompt
                    } else {
                        prompt
                    }
                    onGenerateClick(finalPrompt, selectedImageUri)
                } else {
                    Toast.makeText(context, "Please enter a prompt.", Toast.LENGTH_SHORT).show()
                }
            },
            enabled = cooldownSeconds <= 0,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.surface,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
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
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                actions = {
                    IconButton(onClick = onGalleryClick) {
                        Icon(Icons.Default.Image, contentDescription = "Creations Gallery", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 160.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(if (isExpandedScreen) 0.8f else 1f)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    workflowSelectorAccordion()
                    promptInputAccordion()
                    stylePresetAccordion()
                    resolutionAccordion()
                    if (workflowGroups.isNotEmpty()) {
                        workflowStagesAccordion()
                    }
                    inputImageAccordion()
                    seedSettingAccordion()
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.background.copy(alpha = 0.9f)
                    )
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(if (isExpandedScreen) 0.8f else 1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    geminiStatusCard()
                    generateButton()
                }
            }
        }
    }

    if (showWorkflowSheet) {
        ModalBottomSheet(
            onDismissRequest = { showWorkflowSheet = false },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            scrimColor = Color.Black.copy(alpha = 0.6f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "SELECT WORKFLOW",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Option for Default Workflow
                val isDefaultActive = settings.workflowToUse == ""
                ListItem(
                    headlineContent = { Text("Default Workflow (ernie_workflow.json)", fontWeight = if (isDefaultActive) FontWeight.Bold else FontWeight.Normal, color = MaterialTheme.colorScheme.onSurface) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            onWorkflowChange("")
                            showWorkflowSheet = false
                        },
                    colors = ListItemDefaults.colors(
                        containerColor = if (isDefaultActive) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                    )
                )

                // Option for Imported Local Workflow if it exists
                val importedFileExists = remember {
                    context.getFileStreamPath("imported_workflow.json").exists()
                }
                if (importedFileExists) {
                    val isImportedActive = settings.workflowToUse == "imported_workflow.json"
                    ListItem(
                        headlineContent = { Text("Imported Local Workflow (imported_workflow.json)", fontWeight = if (isImportedActive) FontWeight.Bold else FontWeight.Normal, color = MaterialTheme.colorScheme.onSurface) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                onWorkflowChange("imported_workflow.json")
                                showWorkflowSheet = false
                            },
                        colors = ListItemDefaults.colors(
                            containerColor = if (isImportedActive) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                        )
                    )
                }

                // Options for saved workflows
                savedWorkflows.forEach { workflow ->
                    val isActive = settings.workflowToUse == workflow
                    ListItem(
                        headlineContent = { Text(workflow, fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal, color = MaterialTheme.colorScheme.onSurface) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                onWorkflowChange(workflow)
                                showWorkflowSheet = false
                            },
                        colors = ListItemDefaults.colors(
                            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun AspectRatioIcon(
    aspectRatio: String,
    color: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    val ratioStr = aspectRatio.substringBefore(" ").trim()
    val parts = ratioStr.split(":")
    val (wRatio, hRatio) = if (parts.size == 2) {
        Pair(parts[0].toFloatOrNull() ?: 1f, parts[1].toFloatOrNull() ?: 1f)
    } else {
        Pair(1f, 1f)
    }

    Box(
        modifier = modifier
            .size(24.dp)
            .border(1.5.dp, color.copy(alpha = 0.3f), RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center
    ) {
        val maxDim = 14.dp
        val width: Dp
        val height: Dp
        if (wRatio >= hRatio) {
            width = maxDim
            height = maxDim * (hRatio / wRatio)
        } else {
            height = maxDim
            width = maxDim * (wRatio / hRatio)
        }
        Box(
            modifier = Modifier
                .size(width, height)
                .border(1.5.dp, color, RoundedCornerShape(1.dp))
                .background(color.copy(alpha = 0.15f))
        )
    }
}

@Composable
fun AccordionSection(
    title: String,
    isExpanded: Boolean,
    onHeaderClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onHeaderClick() }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = isExpanded,
                enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                ) {
                    content()
                }
            }
        }
    }
}


