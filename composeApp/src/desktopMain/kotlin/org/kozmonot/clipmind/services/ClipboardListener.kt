package org.kozmonot.clipmind.services

// Modified from fun interface to regular interface to support multiple methods
interface ClipboardListener {
    fun onClipboardTextChange(text: String, shouldScroll: Boolean = true)

    // Method to clear previous items before sending new ones
    // Default implementation does nothing to maintain backward compatibility
    fun onClipboardClear() {}

    /**
     * Called when a screenshot is added to history
     */
    fun onScreenshotAdded(screenshot: ScreenshotItem)
}