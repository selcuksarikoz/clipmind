package org.kozmonot.clipmind.services

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Properties

/**
 * Data class representing app settings
 */
data class Settings(
    val maxHistoryItems: Int,
    val autoStart: Boolean,
    val catchScreenshoot: Boolean,
    val showSupportCounter: Int
)

/**
 * Class for handling settings storage and retrieval
 */
@Suppress("DEPRECATION")
class SettingsManager {
    // Default settings
    private val defaultSettings = Settings(
        maxHistoryItems = 50,
        autoStart = false,
        catchScreenshoot = true,
        showSupportCounter = 0
    )

    // Current settings
    private var currentSettings = defaultSettings.copy()

    // Settings file location
    private val settingsFile: File

    init {
        // Use user home directory for settings
        val userDir = File(System.getProperty("user.home"), ".clipmind")
        if (!userDir.exists()) {
            userDir.mkdirs()
        }
        settingsFile = File(userDir, "settings.properties")

        // Load settings on initialization
        loadSettings()
    }

    /**
     * Load settings from the properties file
     */
    fun loadSettings() {
        if (!settingsFile.exists()) {
            println("Settings file not found. Using defaults.")
            return
        }

        try {
            val properties = Properties()
            FileInputStream(settingsFile).use { stream ->
                properties.load(stream)
            }

            currentSettings = Settings(
                maxHistoryItems = properties.getProperty("maxHistoryItems")?.toIntOrNull()
                    ?: defaultSettings.maxHistoryItems,

                autoStart = properties.getProperty("autoStart")?.toBoolean()
                    ?: defaultSettings.autoStart,

                catchScreenshoot = properties.getProperty("catchScreenshoot")?.toBoolean()
                    ?: defaultSettings.catchScreenshoot,

                showSupportCounter = properties.getProperty("showSupportCounter")?.toInt()
                    ?: defaultSettings.showSupportCounter
            )

            println("Settings loaded successfully")
        } catch (e: Exception) {
            println("Error loading settings: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Save settings to the properties file
     */
    fun saveSettings(settings: Settings) {
        try {
            val properties = Properties()
            properties.setProperty("maxHistoryItems", settings.maxHistoryItems.toString())
            properties.setProperty("autoStart", settings.autoStart.toString())
            properties.setProperty("catchScreenshoot", settings.catchScreenshoot.toString())
            properties.setProperty("showSupportCounter", settings.showSupportCounter.toString())

            // Create parent directories if they don't exist
            settingsFile.parentFile?.mkdirs()

            FileOutputStream(settingsFile).use { stream ->
                properties.store(stream, "ClipMind Settings")
            }

            // Check if auto-start setting has changed
            val autoStartChanged = currentSettings.autoStart != settings.autoStart

            currentSettings = settings
            println("Settings saved successfully to: ${settingsFile.absolutePath}")

            // Only update auto-start if the setting has changed
            if (autoStartChanged) {
                updateAutoStartSetting(settings.autoStart)
            }

        } catch (e: Exception) {
            println("Error saving settings: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Get current settings
     */
    fun getSettings(): Settings {
        return currentSettings
    }

    /**
     * Update auto start setting based on operating system
     */
    private fun updateAutoStartSetting(autoStart: Boolean) {
        val os = System.getProperty("os.name").lowercase()

        try {
            when {
                os.contains("win") -> {
                    setupWindowsAutoStart(autoStart)
                }

                os.contains("mac") -> {
                    setupMacOSAutoStart(autoStart)
                }

                os.contains("nix") || os.contains("nux") || os.contains("aix") -> {
                    setupLinuxAutoStart(autoStart)
                }
            }
        } catch (e: Exception) {
            println("Error updating auto-start setting: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Set up auto-start for Windows
     */
    private fun setupWindowsAutoStart(autoStart: Boolean) {
        try {
            // Get application path
            val appPath = File(System.getProperty("java.class.path")).absolutePath

            // Create registry script
            val tempFile = File.createTempFile("clipmind_autostart", ".reg")
            val regContent = if (autoStart) {
                """Windows Registry Editor Version 5.00

[HKEY_CURRENT_USER\Software\Microsoft\Windows\CurrentVersion\Run]
"ClipMind"="${appPath.replace("\\", "\\\\")}"
"""
            } else {
                """Windows Registry Editor Version 5.00

[HKEY_CURRENT_USER\Software\Microsoft\Windows\CurrentVersion\Run]
"ClipMind"=-
"""
            }

            // Write script to file
            tempFile.writeText(regContent)

            // Execute script
            val process = Runtime.getRuntime().exec("regedit /s ${tempFile.absolutePath}")
            process.waitFor()

            // Clean up
            tempFile.delete()

            println("Windows auto-start setting ${if (autoStart) "enabled" else "disabled"}")
        } catch (e: Exception) {
            println("Failed to set Windows auto-start: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Set up auto-start for macOS that properly shows in System Settings > Login Items
     */
    private fun setupMacOSAutoStart(autoStart: Boolean) {
        try {
            // First, try using AppleScript which will make the app show in the Login Items UI
            val appPath = getAppBundlePath()

            // Extract app name from path for removal
            val appName = appPath.substringAfterLast("/").substringBefore(".app")

            val script = if (autoStart) {
                """
            tell application "System Events"
                make new login item at end with properties {path:"$appPath", hidden:false}
            end tell
            """
            } else {
                """
            tell application "System Events"
                set theAppName to "$appName"
                set loginItems to login items
                
                repeat with i from count of loginItems to 1 by -1
                    set currentItem to item i of loginItems
                    if name of currentItem contains theAppName then
                        delete currentItem
                    end if
                end repeat
            end tell
            """
            }

            // Execute the AppleScript
            val process = Runtime.getRuntime().exec(arrayOf("osascript", "-e", script))
            val exitCode = process.waitFor()

            if (exitCode == 0) {
                println("macOS auto-start ${if (autoStart) "enabled" else "disabled"} successfully")
                return
            } else {
                println("AppleScript execution failed with exit code: $exitCode")
            }
        } catch (e: Exception) {
            println("Failed to set macOS auto-start with AppleScript: ${e.message}")
            e.printStackTrace()

            // Fall back to LaunchAgents method if AppleScript fails
            setupMacOSAutoStartUsingLaunchAgents(autoStart)
        }
    }

    /**
     * Set up auto-start for Linux
     */
    private fun setupLinuxAutoStart(autoStart: Boolean) {
        val autoStartDir = File(System.getProperty("user.home"), ".config/autostart")
        if (!autoStartDir.exists() && autoStart) {
            autoStartDir.mkdirs()
        }

        val desktopFile = File(autoStartDir, "clipmind.desktop")

        try {
            if (autoStart) {
                // Get application path
                val appPath = File(System.getProperty("java.class.path")).absolutePath

                // Create desktop file content
                val desktopContent = """[Desktop Entry]
Type=Application
Exec=$appPath
Hidden=false
NoDisplay=false
X-GNOME-Autostart-enabled=true
Name=ClipMind
Comment=Clipboard History Manager"""

                // Write desktop file
                desktopFile.writeText(desktopContent)

                println("Linux auto-start enabled")
            } else {
                // Delete the file if it exists
                if (desktopFile.exists()) {
                    desktopFile.delete()
                }

                println("Linux auto-start disabled")
            }
        } catch (e: Exception) {
            println("Failed to set Linux auto-start: ${e.message}")
            e.printStackTrace()
        }
    }
}

/**
 * Fallback method using LaunchAgents
 */
private fun setupMacOSAutoStartUsingLaunchAgents(autoStart: Boolean) {
    val launchAgentsDir = File(System.getProperty("user.home"), "Library/LaunchAgents")
    if (!launchAgentsDir.exists()) {
        launchAgentsDir.mkdirs()
    }

    val plistFile = File(launchAgentsDir, "org.kozmonot.clipmind.plist")

    try {
        if (autoStart) {
            // Get the app bundle path
            val appPath = getAppBundlePath()

            // Create plist content
            val plistContent = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key>
    <string>org.kozmonot.clipmind</string>
    <key>ProgramArguments</key>
    <array>
        <string>open</string>
        <string>$appPath</string>
    </array>
    <key>RunAtLoad</key>
    <true/>
</dict>
</plist>"""

            // Write plist file
            plistFile.writeText(plistContent)

            // Load the plist
            val process = Runtime.getRuntime().exec("launchctl load -w ${plistFile.absolutePath}")
            process.waitFor()

            println("macOS auto-start enabled using LaunchAgents (fallback method)")
        } else {
            // Unload if it exists
            if (plistFile.exists()) {
                val process =
                    Runtime.getRuntime().exec("launchctl unload -w ${plistFile.absolutePath}")
                process.waitFor()

                // Delete the file
                plistFile.delete()
            }

            println("macOS auto-start disabled using LaunchAgents (fallback method)")
        }
    } catch (e: Exception) {
        println("Failed to set macOS auto-start using LaunchAgents: ${e.message}")
        e.printStackTrace()
    }
}

/**
 * Get the path to the application bundle
 * This works for both development and packaged app scenarios
 */
private fun getAppBundlePath(): String {
    // For packaged apps
    val codeSource = SettingsManager::class.java.protectionDomain.codeSource
    if (codeSource != null) {
        val jarPath = codeSource.location.path
        // Check if we're in an .app bundle
        if (jarPath.contains(".app")) {
            val appPathRegex = "(.+?\\.app)".toRegex()
            val matchResult = appPathRegex.find(jarPath)
            if (matchResult != null) {
                return matchResult.groupValues[1]
            }
        }
    }

    // Try to get the running app name
    val appName = "ClipMind.app"
    val applications = File("/Applications")
    val appFile = File(applications, appName)
    if (appFile.exists()) {
        return appFile.absolutePath
    }

    // Try user applications folder
    val userApps = File(System.getProperty("user.home"), "Applications")
    val userAppFile = File(userApps, appName)
    if (userAppFile.exists()) {
        return userAppFile.absolutePath
    }

    // Last resort - just return the class path, but this likely won't work correctly
    return File(System.getProperty("java.class.path")).absolutePath
}