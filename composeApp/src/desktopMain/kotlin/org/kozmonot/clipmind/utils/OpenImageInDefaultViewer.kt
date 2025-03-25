package org.kozmonot.clipmind.utils

import java.awt.Desktop
import java.io.File

fun openImageInDefaultViewer(imagePath: String) {
    try {
        val file = File(imagePath)
        if (!file.exists()) {
            return
        }

        // Get the desktop instance
        val desktop = if (Desktop.isDesktopSupported()) Desktop.getDesktop() else null

        if (desktop != null && desktop.isSupported(Desktop.Action.OPEN)) {
            // Open the file with default system application
            desktop.open(file)
        } else {
            // Fallback for systems where Desktop is not supported
            val osName = System.getProperty("os.name").lowercase()

            val command = when {
                osName.contains("win") -> arrayOf("cmd", "/c", "start", "", file.absolutePath)
                osName.contains("mac") -> arrayOf("open", file.absolutePath)
                osName.contains("nix") || osName.contains("nux") -> arrayOf(
                    "xdg-open",
                    file.absolutePath
                )

                else -> null
            }

            if (command != null) {
                Runtime.getRuntime().exec(command)
            }
        }
    } catch (e: Exception) {
        println(e)
    }
}