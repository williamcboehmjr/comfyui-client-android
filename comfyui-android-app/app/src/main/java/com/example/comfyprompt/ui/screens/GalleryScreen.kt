package com.example.comfyprompt.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.comfyprompt.data.GalleryItem
import com.example.comfyprompt.theme.AccentGray
import com.example.comfyprompt.theme.AccentRed
import com.example.comfyprompt.theme.SuccessGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    items: List<GalleryItem>,
    enableEnhancer: Boolean,
    onBackClick: () -> Unit,
    onReRunClick: (String) -> Unit,
    onShareClick: (String) -> Unit,
    onDeleteClick: (String) -> Unit,
    onDownloadClick: (String) -> Unit,
    onRefinePromptClick: (String, Long) -> Unit
) {
    val configuration = LocalConfiguration.current
    val isExpandedScreen = configuration.screenWidthDp >= 600
    val columnsCount = if (isExpandedScreen) 4 else 2

    var selectedItem by remember { mutableStateOf<GalleryItem?>(null) }
    var isFullScreen by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }

    val filteredItems = remember(items, searchQuery) {
        if (searchQuery.isBlank()) {
            items
        } else {
            items.filter { item ->
                item.prompt.contains(searchQuery, ignoreCase = true) ||
                (item.enhancedPrompt?.contains(searchQuery, ignoreCase = true) == true)
            }
        }
    }

    val detailsPagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { filteredItems.size }
    )

    // Sync selectedItem to detailsPagerState
    LaunchedEffect(selectedItem) {
        if (selectedItem != null) {
            val targetIndex = filteredItems.indexOf(selectedItem)
            if (targetIndex >= 0 && detailsPagerState.currentPage != targetIndex) {
                detailsPagerState.scrollToPage(targetIndex)
            }
        }
    }

    // Sync detailsPagerState.currentPage back to selectedItem
    LaunchedEffect(detailsPagerState.currentPage) {
        if (selectedItem != null && detailsPagerState.currentPage in filteredItems.indices) {
            selectedItem = filteredItems[detailsPagerState.currentPage]
        }
    }

    BackHandler(enabled = isFullScreen || selectedItem != null) {
        if (isFullScreen) {
            isFullScreen = false
        } else {
            selectedItem = null
        }
    }

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
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                if (items.isNotEmpty()) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search creations...", color = AccentGray) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = AccentGray) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear search", tint = AccentGray)
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                if (items.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Image, contentDescription = "Empty History", tint = AccentGray, modifier = Modifier.size(64.dp))
                            Text("No past creations found.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text("Your successful generations will appear here.", color = AccentGray, fontSize = 13.sp)
                        }
                    }
                } else if (filteredItems.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "No Results", tint = AccentGray, modifier = Modifier.size(64.dp))
                            Text("No matching creations.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text("Try searching for different keywords.", color = AccentGray, fontSize = 13.sp)
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columnsCount),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredItems, key = { it.id }) { item ->
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
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
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
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

                            // Swipeable content and action buttons
                            HorizontalPager(
                                state = detailsPagerState,
                                modifier = Modifier.weight(1f),
                                pageSpacing = 16.dp
                            ) { page ->
                                val pageItem = filteredItems.getOrNull(page)
                                if (pageItem != null) {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
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
                                                        .background(MaterialTheme.colorScheme.surface)
                                                        .clickable { isFullScreen = true },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    AsyncImage(
                                                        model = pageItem.imageUrl,
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
                                                    GalleryDetailsContent(pageItem, clipboardManager, context)
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
                                                        .background(MaterialTheme.colorScheme.surface)
                                                        .clickable { isFullScreen = true },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    AsyncImage(
                                                        model = pageItem.imageUrl,
                                                        contentDescription = "Zoomed Gallery Preview",
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentScale = ContentScale.Fit
                                                    )
                                                }
                                                GalleryDetailsContent(pageItem, clipboardManager, context)
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
                                                        onDeleteClick(pageItem.id)
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
                                                    onClick = { onShareClick(pageItem.imageUrl) },
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
                                                    onClick = { onDownloadClick(pageItem.imageUrl) },
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
                                                        onReRunClick(pageItem.prompt)
                                                        selectedItem = null
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.Black),
                                                    shape = RoundedCornerShape(20.dp),
                                                    modifier = Modifier.weight(1.2f)
                                                ) {
                                                    Icon(Icons.Default.Refresh, contentDescription = "Re-run", modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Load Prompt", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                }

                                                if (enableEnhancer) {
                                                    Button(
                                                        onClick = {
                                                            onRefinePromptClick(pageItem.imageUrl, pageItem.seed)
                                                            selectedItem = null
                                                        },
                                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB), contentColor = Color.White),
                                                        shape = RoundedCornerShape(20.dp),
                                                        modifier = Modifier.weight(1.2f)
                                                    ) {
                                                        Icon(Icons.Default.Star, contentDescription = "Refine", modifier = Modifier.size(16.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("Refine Prompt", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                        } else {
                                            Column(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                if (enableEnhancer) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        Button(
                                                            onClick = {
                                                                onReRunClick(pageItem.prompt)
                                                                selectedItem = null
                                                            },
                                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.Black),
                                                            shape = RoundedCornerShape(20.dp),
                                                            modifier = Modifier.weight(1f)
                                                        ) {
                                                            Icon(Icons.Default.Refresh, contentDescription = "Re-run", modifier = Modifier.size(16.dp))
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text("Load Prompt", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                        }

                                                        Button(
                                                            onClick = {
                                                                onRefinePromptClick(pageItem.imageUrl, pageItem.seed)
                                                                selectedItem = null
                                                            },
                                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB), contentColor = Color.White),
                                                            shape = RoundedCornerShape(20.dp),
                                                            modifier = Modifier.weight(1.1f)
                                                        ) {
                                                            Icon(Icons.Default.Star, contentDescription = "Refine", modifier = Modifier.size(16.dp))
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text("Refine Prompt", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                        }
                                                    }
                                                } else {
                                                    Button(
                                                        onClick = {
                                                            onReRunClick(pageItem.prompt)
                                                            selectedItem = null
                                                        },
                                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.Black),
                                                        shape = RoundedCornerShape(20.dp),
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        Icon(Icons.Default.Refresh, contentDescription = "Re-run", modifier = Modifier.size(16.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("Load Prompt", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }

                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    OutlinedButton(
                                                        onClick = { onShareClick(pageItem.imageUrl) },
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
                                                        onClick = { onDownloadClick(pageItem.imageUrl) },
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
                                                            onDeleteClick(pageItem.id)
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
                }
            }
        }

        // Full Screen Immersive Overlay for Gallery
        AnimatedVisibility(
            visible = isFullScreen && selectedItem != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            val initialIndex = remember { filteredItems.indexOf(selectedItem).coerceAtLeast(0) }
            val pagerState = rememberPagerState(
                initialPage = initialIndex,
                pageCount = { filteredItems.size }
            )

            // Sync page changes back to selectedItem
            LaunchedEffect(pagerState.currentPage) {
                if (pagerState.currentPage in filteredItems.indices) {
                    selectedItem = filteredItems[pagerState.currentPage]
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val pageItem = filteredItems.getOrNull(page)
                    if (pageItem != null) {
                        ZoomableImage(
                            model = pageItem.imageUrl,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                            shape = androidx.compose.ui.graphics.RectangleShape,
                            onTap = { isFullScreen = false }
                        )
                    }
                }

                // Close Button overlay in fullscreen
                IconButton(
                    onClick = { isFullScreen = false },
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(16.dp)
                        .align(Alignment.TopEnd)
                        .background(Color.Black.copy(alpha = 0.5f), shape = RoundedCornerShape(50))
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Fullscreen",
                        tint = Color.White
                    )
                }

                // Number Indicator Overlay if there are multiple items
                if (filteredItems.size > 1) {
                    Box(
                        modifier = Modifier
                            .statusBarsPadding()
                            .padding(16.dp)
                            .align(Alignment.TopCenter)
                            .background(Color.Black.copy(alpha = 0.5f), shape = RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "${pagerState.currentPage + 1} / ${filteredItems.size}",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GalleryDetailsContent(
    item: GalleryItem,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager,
    context: Context
) {
    // Original prompt
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                Text(item.enhancedPrompt, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    // Metadata (Seed)
    Text("Seed: ${item.seed}", fontSize = 12.sp, color = AccentGray, fontWeight = FontWeight.Medium)
}
