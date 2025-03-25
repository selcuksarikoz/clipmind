package org.kozmonot.clipmind

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import clipmind.composeapp.generated.resources.Res
import clipmind.composeapp.generated.resources.logo
import org.jetbrains.compose.resources.painterResource
import org.kozmonot.clipmind.screens.MainScreen
import org.kozmonot.clipmind.screens.SettingsScreen
import org.kozmonot.clipmind.services.ClipboardObserver
import org.kozmonot.clipmind.services.KeyboardShortcutManager
import org.kozmonot.clipmind.services.SettingsManager
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.Rectangle

enum class Screen {
    Main,
    Settings
}

fun main() = application {

    val settingsManager = remember { SettingsManager() }
    val settings = remember { settingsManager.getSettings() }
    val showSupportCounter = remember { settings.showSupportCounter }

    // Initial window state with center alignment
    val windowState = rememberWindowState(
        placement = WindowPlacement.Floating,
        position = WindowPosition.Aligned(Alignment.Center),
        size = DpSize(400.dp, 500.dp),
        isMinimized = settings.autoStart,
    )

    var currentScreen by remember { mutableStateOf(Screen.Main) }
    var isVisible by remember { mutableStateOf(!settings.autoStart) }

    val trayState = rememberTrayState()

    // Create and initialize the clipboard observer
    val clipboardObserver = remember { ClipboardObserver() }

    // Create and initialize the keyboard shortcut manager
    val keyboardShortcutManager = remember { KeyboardShortcutManager() }

    // Clean up resources when the app closes
    LaunchedEffect(Unit) {
        // Initialize keyboard shortcut manager
        keyboardShortcutManager.initialize()

        // Set the callback for when the shortcut is pressed
        keyboardShortcutManager.setShortcutCallback {
            val mousePos = keyboardShortcutManager.getCurrentMousePosition()
            val optimalPosition = calculateOptimalWindowPosition(
                mousePos,
                windowState.size.width.value.toInt(),
                windowState.size.height.value.toInt()
            )

            // Update the window state's position directly on the main thread
            java.awt.EventQueue.invokeLater {
                // First make the window visible and focused
                isVisible = true
                windowState.isMinimized = false

                // Get the Java window and position it directly
                java.awt.Window.getWindows().forEach { window ->
                    window.setLocation(optimalPosition.first, optimalPosition.second)
                    window.toFront()
                    window.requestFocus()
                    window.isAlwaysOnTop = true
                }
            }
        }

        Runtime.getRuntime().addShutdownHook(Thread {
            clipboardObserver.clearClipboardListener()
            keyboardShortcutManager.shutdown()
        })

        // update showSupportCounter in settings and settings manager
        settingsManager.saveSettings(
            settings.copy(
                showSupportCounter = showSupportCounter + 1
            )
        )
    }

    Tray(
        state = trayState,
        tooltip = "ClipMind",
        icon = painterResource(Res.drawable.logo),
        menu = {
            Item("Open ClipMind", onClick = {
                // Reset to center alignment
                windowState.position = WindowPosition.Aligned(Alignment.Center)
                isVisible = true
                windowState.isMinimized = false
                focusToWindow()
            })
            Item("Open Settings", onClick = {
                // Reset to center alignment
                windowState.position = WindowPosition.Aligned(Alignment.Center)
                isVisible = true
                windowState.isMinimized = false
                currentScreen = Screen.Settings
                focusToWindow()
            })
            Item("Quit ClipMind", onClick = {
                clipboardObserver.clearClipboardListener()
                keyboardShortcutManager.shutdown()
                exitApplication()
            })
        }
    )

    Window(
        onCloseRequest = {
            isVisible = false
            // Removed notification to simplify
        },
        visible = isVisible,
        title = "ClipMind",
        state = windowState,
        resizable = true,
        alwaysOnTop = false,
        focusable = true,
        // This makes the window not appear in the taskbar
        onPreviewKeyEvent = { false },
        undecorated = false,
        transparent = false
    ) {
        when (currentScreen) {
            Screen.Main -> MainScreen(
                clipboardObserver = clipboardObserver,
                onSettings = { currentScreen = Screen.Settings }
            )

            Screen.Settings -> SettingsScreen(
                clipboardObserver = clipboardObserver,
                onBackClick = { currentScreen = Screen.Main }
            )
        }
    }
}

fun focusToWindow() {
    java.awt.Window.getWindows().forEach { window ->
        window.toFront()
        window.requestFocus()
        window.isAlwaysOnTop = true
    }
}

/**
 * Calculates the optimal window position based on the current mouse cursor position
 * and ensures the window stays within screen bounds.
 * Returns a Pair of (x, y) coordinates.
 */
fun calculateOptimalWindowPosition(
    mousePos: Point,
    windowWidth: Int,
    windowHeight: Int
): Pair<Int, Int> {
    val screenBounds = getScreenBoundsForPoint(mousePos)

    // Calculate position that would center the window on the cursor
    var x = mousePos.x - (windowWidth / 2)
    var y = mousePos.y - 30 // Position slightly above the cursor

    // Ensure window stays within screen bounds
    if (x + windowWidth > screenBounds.x + screenBounds.width) {
        x = screenBounds.x + screenBounds.width - windowWidth
    }
    if (x < screenBounds.x) {
        x = screenBounds.x
    }
    if (y + windowHeight > screenBounds.y + screenBounds.height) {
        y = screenBounds.y + screenBounds.height - windowHeight
    }
    if (y < screenBounds.y) {
        y = screenBounds.y
    }

    val pair = Pair(x, y)

    return pair
}

/**
 * Gets the screen bounds for the screen containing the specified point.
 */
fun getScreenBoundsForPoint(point: Point): Rectangle {
    val graphicsEnvironment = GraphicsEnvironment.getLocalGraphicsEnvironment()
    val screenDevices = graphicsEnvironment.screenDevices

    for (device in screenDevices) {
        val bounds = device.defaultConfiguration.bounds
        if (bounds.contains(point)) {
            return bounds
        }
    }

    // Fall back to the default screen if no matching screen found
    return graphicsEnvironment.defaultScreenDevice.defaultConfiguration.bounds
}