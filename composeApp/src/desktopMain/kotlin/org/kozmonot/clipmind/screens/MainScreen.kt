package org.kozmonot.clipmind.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.kozmonot.clipmind.services.ClipboardListener
import org.kozmonot.clipmind.services.ClipboardObserver
import org.kozmonot.clipmind.services.ScreenshotItem
import org.kozmonot.clipmind.services.SettingsManager
import org.kozmonot.clipmind.utils.openImageInDefaultViewer
import org.kozmonot.clipmind.views.ClipboardItem
import org.kozmonot.clipmind.views.ScreenshotClipboardItem
import org.kozmonot.clipmind.views.Support

// Define enum for clipboard content types
enum class ClipboardContentType {
    TEXT,
    IMAGE
}

// Create a unified model for all clipboard items
data class ClipboardContent(
    val id: String,
    val content: Any,
    val type: ClipboardContentType
)

@Preview
@Composable
fun MainScreen(clipboardObserver: ClipboardObserver, onSettings: () -> Unit) {

    val settingsManager = remember { SettingsManager() }
    val showSupportCounter = settingsManager.getSettings().showSupportCounter
    val showSupport = rememberSaveable { mutableStateOf(showSupportCounter % 5 == 0) }

    val clipboardItems = remember { mutableStateListOf<ClipboardContent>() }
    var filterText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Add these variables to your MainScreen composable function
    val lastProcessedScreenshotTime = remember { mutableStateOf(0L) }
    val screenshotDebounceMs = 500L // 500ms debounce period

    // Map to store screenshot hashes to prevent duplicates
    val screenshotHashes = remember { mutableSetOf<String>() }

    // Filter items based on search text (only text items can be filtered)
    val filteredItems = clipboardItems.filter {
        when (it.type) {
            ClipboardContentType.TEXT -> (it.content as String).contains(
                filterText,
                ignoreCase = true
            )

            ClipboardContentType.IMAGE -> filterText.isEmpty() // Show images only when not filtering
        }
    }

    // Load history when composable is first created
    LaunchedEffect(Unit) {
        // Get existing text history from the observer
        val textHistoryItems = clipboardObserver.getClipboardTextHistory()
        val screenshotHistoryItems = clipboardObserver.getScreenshotHistory()

        // Clear any existing items first
        clipboardItems.clear()

        // Add all text history items to our list
        textHistoryItems.forEachIndexed { index, text ->
            clipboardItems.add(
                ClipboardContent(
                    id = "text-${index}",
                    content = text,
                    type = ClipboardContentType.TEXT
                )
            )
        }
        println("Loaded ${textHistoryItems.size} text items from history")

        // Add all screenshot history items to our list
        screenshotHistoryItems.forEach { screenshot ->
            clipboardItems.add(
                ClipboardContent(
                    id = screenshot.id,
                    content = screenshot,
                    type = ClipboardContentType.IMAGE
                )
            )
            // Add ID to hash set for duplicate detection
            screenshotHashes.add(screenshot.id)
        }
        println("Loaded ${screenshotHistoryItems.size} screenshots from history")

        // Sort all items by creation time (newest first) with safe comparisons
        clipboardItems.sortWith { a, b ->
            try {
                when {
                    a.type == ClipboardContentType.TEXT && b.type == ClipboardContentType.TEXT -> 0
                    a.type == ClipboardContentType.IMAGE && b.type == ClipboardContentType.IMAGE -> {
                        val timeA = (a.content as ScreenshotItem).createdAt.time
                        val timeB = (b.content as ScreenshotItem).createdAt.time
                        timeB.compareTo(timeA) // Reverse for descending order
                    }

                    a.type == ClipboardContentType.IMAGE -> -1 // Images come first
                    else -> 1
                }
            } catch (e: Exception) {
                println("Error comparing items: ${e.message}")
                0 // Default to no change in order if comparison fails
            }
        }
    }

    // Use DisposableEffect to properly manage clipboard listener lifecycle
    DisposableEffect(Unit) {
        println("Setting up clipboard listener")

        // Create our listener
        val listener = object : ClipboardListener {
            override fun onClipboardTextChange(text: String, shouldScroll: Boolean) {
                println("Text changed: ${text.take(20)}...")

                // Generate a unique ID for the text item
                val textId = "text-${System.currentTimeMillis()}"

                // Check if this text is already in our list
                val existingItemIndex = clipboardItems.indexOfFirst {
                    it.type == ClipboardContentType.TEXT && it.content == text
                }

                if (existingItemIndex >= 0) {
                    // If exists, remove it first (to update position)
                    // clipboardItems.removeAt(existingItemIndex)
                    return
                }

                // Add the new or updated item at the beginning
                clipboardItems.add(
                    0,
                    ClipboardContent(
                        id = textId,
                        content = text,
                        type = ClipboardContentType.TEXT
                    )
                )

                // Keep the list limited to max history size
                val maxSize = SettingsManager().getSettings().maxHistoryItems
                if (clipboardItems.size > maxSize) {
                    // Remove the oldest items (from the end)
                    while (clipboardItems.size > maxSize) {
                        clipboardItems.removeAt(clipboardItems.lastIndex)
                    }
                }

                println("Added/updated text in clipboard items, current count: ${clipboardItems.size}")
            }

            override fun onClipboardClear() {
                println("Clipboard cleared")
                // Clear the list and hash tracking when clipboard is cleared
                clipboardItems.clear()
                screenshotHashes.clear()
            }

            override fun onScreenshotAdded(screenshot: ScreenshotItem) {
                val isSSAvailable = SettingsManager().getSettings().catchScreenshoot
                if (!isSSAvailable) return

                println("Screenshot added: ${screenshot.id}")

                // Apply debounce to prevent multiple rapid calls
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastProcessedScreenshotTime.value < screenshotDebounceMs) {
                    println("Screenshot debounced (${currentTime - lastProcessedScreenshotTime.value}ms < ${screenshotDebounceMs}ms)")
                    return
                }

                // Update last processed time
                lastProcessedScreenshotTime.value = currentTime

                // Check if we've already processed this screenshot by ID
                if (screenshotHashes.contains(screenshot.id)) {
                    println("Duplicate screenshot detected and skipped: ${screenshot.id}")
                    return
                }

                // Add ID to our tracking set
                screenshotHashes.add(screenshot.id)

                // Check if this screenshot is already in our list by ID
                val existingScreenshotIndex = clipboardItems.indexOfFirst {
                    it.type == ClipboardContentType.IMAGE &&
                            (it.content as ScreenshotItem).id == screenshot.id
                }

                if (existingScreenshotIndex >= 0) {
                    // If it exists, remove the old one first (to update the position)
                    clipboardItems.removeAt(existingScreenshotIndex)
                    println("Updating existing screenshot position: ${screenshot.id}")
                }

                // Add the screenshot at the beginning of the list
                clipboardItems.add(
                    0,
                    ClipboardContent(
                        id = screenshot.id,
                        content = screenshot,
                        type = ClipboardContentType.IMAGE
                    )
                )

                // Keep the list limited to max history size
                val maxSize = SettingsManager().getSettings().maxHistoryItems
                if (clipboardItems.size > maxSize) {
                    // Remove the oldest items (from the end)
                    while (clipboardItems.size > maxSize) {
                        clipboardItems.removeAt(clipboardItems.lastIndex)
                    }
                }

                println("Screenshot added to clipboard history: ${screenshot.id}, time: $currentTime")
            }
        }

        // Register the listener
        clipboardObserver.setClipboardListener(listener)

        // Make sure clipboard monitoring is active
        clipboardObserver.startClipboardMonitoring()

        // When the composable leaves composition, clean up the listener
        onDispose {
            println("Disposing clipboard listener")
            clipboardObserver.clearClipboardListener()
        }
    }

    MaterialTheme {
        Column {

            AnimatedVisibility(visible = showSupport.value) {
                Support(onClose = { showSupport.value = false })
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .background(Color.White, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.CenterStart
                ) {
                    BasicTextField(
                        value = filterText,
                        onValueChange = { filterText = it },
                        maxLines = 1,
                        textStyle = TextStyle(
                            color = Color(0xFF232323),
                            fontSize = 16.sp
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                    )

                    if (filterText.isEmpty()) {
                        Text(
                            text = "Filter",
                            color = Color.Gray.copy(alpha = 0.7f),
                            fontSize = 16.sp,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = { onSettings() },
                    modifier = Modifier.height(40.dp).clip(RoundedCornerShape(10.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings"
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Settings")
                }
            }

            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LazyColumn(
                    modifier = Modifier.padding(10.dp).fillMaxSize(),
                    state = listState
                ) {
                    itemsIndexed(filteredItems) { index, item ->
                        when (item.type) {
                            ClipboardContentType.TEXT -> {
                                ClipboardItem(
                                    item = item.content as String,
                                    onCopy = {
                                        println(item.content)
                                        clipboardObserver.copyTextFromApp(item.content)
                                    },
                                    onDelete = {
                                        clipboardItems.remove(item)
                                        clipboardObserver.removeTextItem(item.content)
                                    },
                                    modifier = Modifier.background(
                                        if (index % 2 == 0) Color(0xFFF8F9FA) else Color(0xFFECF0F1)
                                    )
                                )
                            }

                            ClipboardContentType.IMAGE -> {
                                // Display screenshot item
                                ScreenshotClipboardItem(
                                    item = item.content as ScreenshotItem,
                                    onOpen = {
                                        // Open the image in the default viewer
                                        openImageInDefaultViewer(item.content.fullImagePath)
                                    },
                                    onDelete = {
                                        // Remove from UI list
                                        screenshotHashes.remove(item.content.id)
                                        clipboardItems.remove(item)

                                        // Remove from storage
                                        clipboardObserver.removeItemById(item.content.id)
                                    },
                                    modifier = Modifier.background(
                                        if (index % 2 == 0) Color(0xFFF8F9FA) else Color(0xFFECF0F1)
                                    )
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(5.dp))
                    }
                }
            }
        }
    }
}