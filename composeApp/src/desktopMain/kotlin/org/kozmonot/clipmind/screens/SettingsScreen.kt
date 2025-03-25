package org.kozmonot.clipmind.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.kozmonot.clipmind.constants.backgroundColor
import org.kozmonot.clipmind.constants.cardColor
import org.kozmonot.clipmind.constants.dangerColor
import org.kozmonot.clipmind.constants.primaryColor
import org.kozmonot.clipmind.constants.secondaryTextColor
import org.kozmonot.clipmind.constants.textColor
import org.kozmonot.clipmind.services.ClipboardObserver
import org.kozmonot.clipmind.services.Settings
import org.kozmonot.clipmind.services.SettingsManager
import java.io.File

@Composable
fun SettingsScreen(
    clipboardObserver: ClipboardObserver,
    onBackClick: () -> Unit
) {
    // Initialize settings manager
    val settingsManager = remember { SettingsManager() }
    val showSupportCounter = settingsManager.getSettings().showSupportCounter

    // Get data directory path for display
    val dataDirectoryPath = remember {
        val userDir = File(System.getProperty("user.home"), ".clipmind")
        userDir.absolutePath
    }

    // State variables with immediate effect on changes
    var maxHistoryItems by remember {
        mutableStateOf(settingsManager.getSettings().maxHistoryItems.toString())
    }
    var autoStart by remember {
        mutableStateOf(settingsManager.getSettings().autoStart)
    }
    var catchScreenshoot by remember {
        mutableStateOf(settingsManager.getSettings().catchScreenshoot)
    }

    // Auto-save settings when any value changes
    LaunchedEffect(maxHistoryItems, autoStart, catchScreenshoot) {
        if (maxHistoryItems.isNotBlank()) {
            val settings = Settings(
                maxHistoryItems = maxHistoryItems.toIntOrNull() ?: 50,
                autoStart = autoStart,
                catchScreenshoot = catchScreenshoot,
                showSupportCounter = showSupportCounter
            )
            settingsManager.saveSettings(settings)

            // Update the clipboard observer with new settings
            clipboardObserver.updateSettings()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Settings",
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        fontFamily = FontFamily.Default
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                backgroundColor = primaryColor,
                elevation = 0.dp // Flat design with no elevation
            )
        },
        backgroundColor = backgroundColor,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Settings cards
            SettingsSection(
                title = "General Settings",
                color = primaryColor,
                backgroundColor = cardColor
            ) {
                // Maximum history items
                SettingRow(
                    title = "Max history items",
                    textColor = textColor
                ) {
                    OutlinedTextField(
                        value = maxHistoryItems,
                        onValueChange = {
                            // Only allow numbers
                            if (it.isEmpty() || it.all { char -> char.isDigit() }) {
                                maxHistoryItems = it
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.scale(scaleY = 0.9F, scaleX = 1F).width(80.dp)
                    )
                }

                // Launch at login
                SettingRow(
                    title = "Launch at login",
                    textColor = textColor
                ) {
                    ModernSwitch(
                        checked = autoStart,
                        onCheckedChange = { autoStart = it }
                    )
                }

                // Save the screensohoot
                SettingRow(
                    title = "Catch Screenshoot",
                    textColor = textColor
                ) {
                    ModernSwitch(
                        checked = catchScreenshoot,
                        onCheckedChange = { catchScreenshoot = it }
                    )
                }
            }

            SettingsSection(
                title = "Data & Storage",
                color = primaryColor, // Using primary color for consistency
                backgroundColor = cardColor
            ) {
                // Data directory
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        "Data Directory:",
                        color = textColor,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = dataDirectoryPath,
                        fontSize = 14.sp,
                        color = secondaryTextColor,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Clear all data button
                Button(
                    onClick = {
                        clipboardObserver.clearAll()
                    },
                    colors = ButtonDefaults.buttonColors(backgroundColor = dangerColor),
                    shape = RoundedCornerShape(4.dp), // More flat appearance
                    modifier = Modifier
                        .padding(vertical = 8.dp, horizontal = 16.dp),
                    elevation = ButtonDefaults.elevation(
                        0.dp,
                        0.dp,
                        0.dp,
                        0.dp,
                        0.dp
                    ) // No elevation for flat design
                ) {
                    Text(
                        "Clear All Clipboard History",
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    color: Color,
    backgroundColor: Color,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(4.dp), // More flat corners
        backgroundColor = backgroundColor,
        elevation = 0.dp // No elevation for flat design
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(color),
                )

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = color
                )
            }

            Divider(
                color = Color.LightGray.copy(alpha = 0.5f),
                thickness = 1.dp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            content()
        }
    }
}

@Composable
fun SettingRow(
    title: String,
    textColor: Color,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            color = textColor,
            fontSize = 15.sp
        )

        content()
    }
}

@Composable
fun ModernSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val trackColor by animateColorAsState(
        targetValue = if (checked) primaryColor else Color.LightGray,
        animationSpec = tween(300)
    )

    val thumbColor by animateColorAsState(
        targetValue = Color.White,
        animationSpec = tween(300)
    )

    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 20.dp else 0.dp,
        animationSpec = tween(300)
    )

    Box(
        modifier = Modifier
            .width(46.dp)
            .height(26.dp)
            .clip(CircleShape)
            .background(trackColor)
            .clickable { onCheckedChange(!checked) }
            .padding(3.dp)
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .offset(x = thumbOffset)
                .clip(CircleShape)
                .background(thumbColor)
        )
    }
}