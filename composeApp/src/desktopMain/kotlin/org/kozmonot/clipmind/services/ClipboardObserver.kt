package org.kozmonot.clipmind.services

import java.awt.Image
import java.awt.Toolkit
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.ClipboardOwner
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Timer
import java.util.TimerTask
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import java.util.logging.Logger
import javax.imageio.ImageIO

// Modified data classes to be serializable
data class ScreenshotItem(
    val id: String,
    val createdAt: Date,
    val thumbnailPath: String,
    val fullImagePath: String,
    val type: String = "image" // Added type field
) : Serializable

// Combined data class for history entries
data class HistoryEntry(
    val id: String,
    val type: String, // "text" or "image"
    val createdAt: Date,
    val content: Any, // Can be String (for text) or paths (for images)
    val index: Int,
    // For screenshots
    val thumbnailPath: String? = null,
    val fullImagePath: String? = null
) : Serializable

// Storage for all history items with thread-safe access
private val clipboardHistory = ArrayList<HistoryEntry>()
private val historyLock = Any()

/**
 * Observes system clipboard changes and maintains a history of clipboard content.
 * Supports persistent storage in binary format, clipboard manipulation, and notification of events.
 */
class ClipboardObserver(private val settingsManager: SettingsManager = SettingsManager()) :
    ClipboardOwner {
    // Use a proper logger instead of println
    private val logger = Logger.getLogger(ClipboardObserver::class.java.name)

    private val screenshotDir: File

    private var listener: ClipboardListener? = null
    private val clipboard: Clipboard = Toolkit.getDefaultToolkit().systemClipboard

    private val recentImageHashes = ConcurrentHashMap<String, Long>()
    private val IMAGE_DEDUPLICATION_TIMEOUT_MS = 2000L // 2 seconds

    // History storage file (binary format)
    private val historyFile: File

    // Executor for reliable timing
    private var executor: ScheduledExecutorService? = null

    // Track clipboard state
    private var lastContent: String? = null

    @Volatile
    private var isProcessing = false

    @Volatile
    private var isAppCopying = false

    @Volatile
    private var isFirstClipboardCheck = true

    @Volatile
    private var isShutdown = false

    @Volatile
    private var isTimerActive = false

    // Configuration constants
    private val APP_COPY_TIMEOUT_MS = 500L
    private val POLLING_INTERVAL_MS = 1000L
    private val OWNERSHIP_RECOVERY_DELAY_MS = 200L
    private val MONITORING_RETRY_DELAY_MS = 500L

    private var isCatchScreenshootEnabled = true

    private var isProcessingText = false
    private var isProcessingImage = false
    private var lastTextHash: String? = null

    private var clipboardCheckTimer = Timer("ClipboardChecker", true)

    /**
     * Start clipboard monitoring with safe timer initialization
     */
    fun startClipboardMonitoring() {
        synchronized(this) {
            // Only start if not already active
            if (!isTimerActive) {
                clipboardCheckTimer = Timer("ClipboardChecker", true)
                clipboardCheckTimer.scheduleAtFixedRate(object : TimerTask() {
                    override fun run() {
                        checkClipboard()
                    }
                }, 0, 500) // Check every 500ms
                isTimerActive = true
                logger.info("Clipboard monitoring started")
            } else {
                logger.info("Clipboard monitoring already active, not starting again")
            }
        }
    }

    /**
     * Stop clipboard monitoring safely
     */
    fun stopClipboardMonitoring() {
        synchronized(this) {
            if (isTimerActive) {
                try {
                    clipboardCheckTimer.cancel()
                    clipboardCheckTimer.purge()
                    isTimerActive = false
                    logger.info("Clipboard monitoring stopped")
                } catch (e: Exception) {
                    logger.log(Level.WARNING, "Error stopping clipboard monitoring: ${e.message}")
                }
            }
        }
    }

    init {
        // Use user home directory for configuration
        val userDir = File(System.getProperty("user.home"), ".clipmind")
        if (!userDir.exists()) {
            userDir.mkdirs()
        }

        // Use binary history.data file instead of CSV
        historyFile = File(userDir, "history.data")

        // Initialize screenshot directory
        screenshotDir = File(userDir, "screenshots")
        if (!screenshotDir.exists()) {
            screenshotDir.mkdirs()
        }

        isTimerActive = true

        isCatchScreenshootEnabled = settingsManager.getSettings().catchScreenshoot

        // Load unified history
        loadHistory()
        initExecutor()
    }

    private fun hashString(text: String): String {
        return text.hashCode().toString()
    }

    private fun calculateImageHash(image: Image): String {
        try {
            // Convert to BufferedImage if it's not already
            val bufferedImage = convertToBufferedImage(image)

            // Create a simplified hash based on a sample of pixels
            // This is faster than hashing the entire image
            val width = bufferedImage.width
            val height = bufferedImage.height

            // Sample grid (10x10 points across the image)
            val md = MessageDigest.getInstance("MD5")
            val baos = ByteArrayOutputStream()
            val dos = DataOutputStream(baos)

            // Add image dimensions to hash
            dos.writeInt(width)
            dos.writeInt(height)

            // Sample pixels in a grid pattern
            for (y in 0 until height step (height / 10).coerceAtLeast(1)) {
                for (x in 0 until width step (width / 10).coerceAtLeast(1)) {
                    val rgb = bufferedImage.getRGB(x, y)
                    dos.writeInt(rgb)
                }
            }

            dos.flush()
            val digest = md.digest(baos.toByteArray())
            return digest.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Error calculating image hash", e)
            // Fallback to timestamp-based hash
            return System.currentTimeMillis().toString()
        }
    }

    private fun cleanupOldImageHashes() {
        val currentTime = System.currentTimeMillis()
        val iterator = recentImageHashes.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (currentTime - entry.value > IMAGE_DEDUPLICATION_TIMEOUT_MS) {
                iterator.remove()
            }
        }
    }

    /**
     * Initialize the executor service
     */
    private fun initExecutor() {
        if (executor == null || executor?.isShutdown == true) {
            executor = Executors.newSingleThreadScheduledExecutor { r ->
                val thread = Thread(r, "ClipboardMonitorThread")
                thread.isDaemon = true  // Allow JVM to exit even if thread is running
                thread
            }
            isShutdown = false
        }
    }

    /**
     * Sets a listener to be notified of clipboard changes and initializes monitoring
     */
    fun setClipboardListener(listener: ClipboardListener) {
        this.listener = listener

        // Ensure executor is available
        initExecutor()
        setupClipboardMonitoring()

        // Clear previous items if any
        listener.onClipboardClear()

        // Notify listener of all existing items in history
        synchronized(historyLock) {
            // Get all text items from history
            clipboardHistory.filter { it.type == "text" }.forEach { item ->
                listener.onClipboardTextChange(item.content as String, false)
            }

            // Get all screenshot items from history
            clipboardHistory.filter { it.type == "image" }.forEach { item ->
                val screenshotItem = ScreenshotItem(
                    id = item.id,
                    createdAt = item.createdAt,
                    thumbnailPath = item.thumbnailPath ?: "",
                    fullImagePath = item.fullImagePath ?: ""
                )
                listener.onScreenshotAdded(screenshotItem)
            }
        }
    }

    /**
     * Set up clipboard monitoring by taking ownership
     */
    private fun setupClipboardMonitoring() {
        try {
            val contents = clipboard.getContents(null)

            // If current clipboard contains image data, don't take ownership
            if (contents != null && contents.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                // Start polling without taking ownership of image data
                startPolling()
                return
            }

            // Only take ownership for text or other non-image content
            clipboard.setContents(contents, this)
            startPolling()
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Failed to set clipboard contents: ${e.message}", e)

            executor?.schedule({
                if (!isShutdown) setupClipboardMonitoring()
            }, MONITORING_RETRY_DELAY_MS, TimeUnit.MILLISECONDS)
        }
    }

    /**
     * Start periodic polling of clipboard contents
     */
    private fun startPolling() {
        if (isShutdown) return

        try {
            executor?.scheduleAtFixedRate({
                try {
                    if (!isShutdown) checkClipboard()
                } catch (e: Exception) {
                    logger.log(Level.WARNING, "Error during clipboard polling", e)
                }
            }, POLLING_INTERVAL_MS, POLLING_INTERVAL_MS, TimeUnit.MILLISECONDS)
        } catch (e: Exception) {
            logger.log(Level.SEVERE, "Failed to start clipboard polling", e)
        }
    }

    /**
     * Modified checkClipboard to handle both image and text content properly
     */
    private fun checkClipboard() {
        // Immediately return if app is copying
        if (isAppCopying) {
            return
        }

        // Get clipboard contents once to avoid multiple requests
        val contents = try {
            clipboard.getContents(null)
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Error getting clipboard contents", e)
            null
        } ?: return // If contents is null, just return

        // Track whether we processed anything
        var contentProcessed = false

        // Process image content if available, not currently processing images, and screenshot capturing is enabled
        if (contents.isDataFlavorSupported(DataFlavor.imageFlavor) && !isProcessingImage && isCatchScreenshootEnabled) {
            isProcessingImage = true
            try {
                val image = contents.getTransferData(DataFlavor.imageFlavor) as? Image
                if (image != null) {
                    // Check for recent image duplication first
                    val imageHash = calculateImageHash(image)
                    val currentTime = System.currentTimeMillis()

                    // Log for debugging
                    logger.fine("Checking image hash: $imageHash")

                    // Check if this exact hash exists in our recent hashes
                    val lastProcessedTime = recentImageHashes[imageHash]
                    if (lastProcessedTime != null && currentTime < lastProcessedTime) {
                        // This is a duplicate image that we've recently seen
                        logger.fine("Skipping duplicate image with hash: $imageHash")
                    } else {
                        // Process as a new screenshot
                        saveScreenshot(image)
                        contentProcessed = true
                    }
                }
            } catch (e: Exception) {
                logger.log(Level.WARNING, "Error processing image from clipboard", e)
            } finally {
                isProcessingImage = false
            }
        }

        // Process text content if available and not currently processing text
        if (contents.isDataFlavorSupported(DataFlavor.stringFlavor) && !isProcessingText) {
            isProcessingText = true
            try {
                val text = contents.getTransferData(DataFlavor.stringFlavor) as? String
                if (text != null && text.isNotEmpty()) {
                    // Create a hash for this text
                    val textHash = hashString(text)

                    // Only process if it's not the same as the last processed text
                    if (textHash != lastTextHash) {
                        lastTextHash = textHash
                        logger.info("Processing new text: ${text.take(20)}...")

                        // Add to unified history
                        addTextToHistory(text)

                        // Notify listener directly
                        listener?.onClipboardTextChange(text, true)
                        contentProcessed = true
                    }
                }
            } catch (e: Exception) {
                logger.log(Level.WARNING, "Error processing text from clipboard", e)
            } finally {
                isProcessingText = false
            }
        }

        // Log if we didn't process any content (useful for debugging)
        if (!contentProcessed) {
            logger.fine("No new clipboard content processed")
        }
    }

    /**
     * Save a screenshot from the clipboard
     */
    private fun saveScreenshot(image: Image) {
        try {
            // Convert to BufferedImage if it's not already
            val bufferedImage = convertToBufferedImage(image)

            // Calculate image hash for deduplication
            val imageHash = calculateImageHash(bufferedImage)

            // Check if we've seen this image recently
            val currentTime = System.currentTimeMillis()
            val lastSeenTime = recentImageHashes.put(imageHash, currentTime)

            if (lastSeenTime != null && (currentTime - lastSeenTime) < IMAGE_DEDUPLICATION_TIMEOUT_MS) {
                // This is a duplicate image detected within the timeout window
                return
            }

            // Clean up old hashes occasionally
            if (recentImageHashes.size > 100) {
                cleanupOldImageHashes()
            }

            // Generate unique ID
            val id = UUID.randomUUID().toString()
            val timestamp = Date()
            val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss").format(timestamp)

            // Create file paths
            val fullImageFileName = "${dateStr}_${id}.png"
            val thumbnailFileName = "${dateStr}_${id}_thumb.png"
            val fullImageFile = File(screenshotDir, fullImageFileName)
            val thumbnailFile = File(screenshotDir, thumbnailFileName)

            // Save full image
            ImageIO.write(bufferedImage, "PNG", fullImageFile)

            // Create and save thumbnail
            val thumbnail = createThumbnail(bufferedImage, 120, 120)
            ImageIO.write(thumbnail, "PNG", thumbnailFile)

            // Add to unified history
            val nextIndex = getNextHistoryIndex()
            val historyEntry = HistoryEntry(
                id = id,
                type = "image",
                createdAt = timestamp,
                content = "image", // Use a placeholder string instead of the actual image
                index = nextIndex,
                thumbnailPath = thumbnailFile.absolutePath,
                fullImagePath = fullImageFile.absolutePath
            )

            synchronized(historyLock) {
                // Add to beginning of list
                clipboardHistory.add(0, historyEntry)

                // Trim to max size
                trimHistoryToMaxSize()
            }

            // Save history
            executor?.submit { saveHistory() }

            // Create item for listener notification
            val screenshotItem = ScreenshotItem(
                id = id,
                createdAt = timestamp,
                thumbnailPath = thumbnailFile.absolutePath,
                fullImagePath = fullImageFile.absolutePath
            )

            // Notify listener
            listener?.onScreenshotAdded(screenshotItem)

        } catch (e: Exception) {
            logger.log(Level.WARNING, "Error processing screenshot", e)
        }
    }

    /**
     * Add text item to unified history
     */
    private fun addTextToHistory(text: String) {
        synchronized(historyLock) {
            val nextIndex = getNextHistoryIndex()
            val timestamp = Date()
            val id = UUID.randomUUID().toString()

            // Create history entry
            val historyEntry = HistoryEntry(
                id = id,
                type = "text",
                createdAt = timestamp,
                content = text,
                index = nextIndex
            )

            // Remove if same text already exists
            clipboardHistory.removeIf { it.type == "text" && it.content == text }

            // Add at beginning (newest first)
            clipboardHistory.add(0, historyEntry)

            // Trim if needed
            trimHistoryToMaxSize()

            // Save asynchronously
            executor?.submit { saveHistory() }
        }
    }

    /**
     * Get next index for history items
     */
    private fun getNextHistoryIndex(): Int {
        return if (clipboardHistory.isEmpty()) 1 else
            clipboardHistory.maxByOrNull { it.index }!!.index + 1
    }

    /**
     * Trim history to max size from settings
     */
    private fun trimHistoryToMaxSize() {
        val maxHistorySize = settingsManager.getSettings().maxHistoryItems
        while (clipboardHistory.size > maxHistorySize) {
            val removed = clipboardHistory.removeAt(clipboardHistory.size - 1)

            // If it's an image, delete the files
            if (removed.type == "image") {
                try {
                    if (removed.thumbnailPath != null) File(removed.thumbnailPath).delete()
                    if (removed.fullImagePath != null) File(removed.fullImagePath).delete()
                } catch (e: Exception) {
                    logger.log(Level.WARNING, "Error deleting old screenshot files", e)
                }
            }
        }
    }

    /**
     * Convert Image to BufferedImage
     */
    private fun convertToBufferedImage(image: Image): BufferedImage {
        if (image is BufferedImage) {
            return image
        }

        // Create a buffered image with transparency
        val bufferedImage = BufferedImage(
            image.getWidth(null),
            image.getHeight(null),
            BufferedImage.TYPE_INT_ARGB
        )

        // Draw the image on to the buffered image
        val graphics = bufferedImage.createGraphics()
        graphics.drawImage(image, 0, 0, null)
        graphics.dispose()

        return bufferedImage
    }

    /**
     * Create a thumbnail from a BufferedImage
     */
    private fun createThumbnail(
        image: BufferedImage,
        maxWidth: Int,
        maxHeight: Int
    ): BufferedImage {
        val originalWidth = image.width
        val originalHeight = image.height

        var width = originalWidth
        var height = originalHeight

        // Calculate dimensions to maintain aspect ratio
        if (originalWidth > maxWidth || originalHeight > maxHeight) {
            val widthRatio = maxWidth.toDouble() / originalWidth
            val heightRatio = maxHeight.toDouble() / originalHeight
            val ratio = Math.min(widthRatio, heightRatio)

            width = (originalWidth * ratio).toInt()
            height = (originalHeight * ratio).toInt()
        }

        // Create the thumbnail
        val thumbnail = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val graphics = thumbnail.createGraphics()
        graphics.drawImage(image.getScaledInstance(width, height, Image.SCALE_SMOOTH), 0, 0, null)
        graphics.dispose()

        return thumbnail
    }

    /**
     * Save unified history to disk in binary format
     */
    private fun saveHistory() {
        try {
            if (!historyFile.parentFile.exists()) {
                historyFile.parentFile.mkdirs()
            }

            synchronized(historyLock) {
                // Use object serialization to save history
                ObjectOutputStream(FileOutputStream(historyFile)).use { oos ->
                    // Prepare a list of serializable history entries
                    // For images, we don't save the actual image content, just the paths
                    val historyToSave = clipboardHistory.map { item ->
                        when (item.type) {
                            "image" -> {
                                // Create a new entry without the BufferedImage content
                                HistoryEntry(
                                    id = item.id,
                                    type = item.type,
                                    createdAt = item.createdAt,
                                    content = "image", // Replace with a placeholder string
                                    index = item.index,
                                    thumbnailPath = item.thumbnailPath,
                                    fullImagePath = item.fullImagePath
                                )
                            }

                            "text" -> item   // Text entries are already serializable
                            else -> item     // Handle any future types
                        }
                    }

                    // Write the serializable list
                    oos.writeObject(historyToSave)
                }

                logger.info("History saved to binary file: ${historyFile.absolutePath}")
            }
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Error saving history", e)
        }
    }

    /**
     * Load unified history from disk
     */
    private fun loadHistory() {
        if (!historyFile.exists()) return

        try {
            synchronized(historyLock) {
                clipboardHistory.clear()

                ObjectInputStream(FileInputStream(historyFile)).use { ois ->
                    try {
                        // Read the entire list at once
                        val loadedHistory = ois.readObject() as? ArrayList<*>

                        // Validate entries and filter out any that are invalid
                        loadedHistory?.forEach { rawEntry ->
                            try {
                                if (rawEntry is HistoryEntry) {
                                    // Handle image entries
                                    if (rawEntry.type == "image") {
                                        val thumbnailExists = rawEntry.thumbnailPath != null &&
                                                File(rawEntry.thumbnailPath).exists()
                                        val fullImageExists = rawEntry.fullImagePath != null &&
                                                File(rawEntry.fullImagePath).exists()

                                        if (thumbnailExists && fullImageExists) {
                                            // Ensure index is properly handled as Int
                                            val safeIndex = when (rawEntry.index) {
                                                is Int -> rawEntry.index
                                                is Long -> rawEntry.index.toInt()
                                                else -> 0 // Default if unexpected type
                                            }

                                            // Create a new, clean entry with proper types
                                            val safeEntry = HistoryEntry(
                                                id = rawEntry.id,
                                                type = rawEntry.type,
                                                createdAt = rawEntry.createdAt,
                                                content = "image", // Use placeholder
                                                index = safeIndex,
                                                thumbnailPath = rawEntry.thumbnailPath,
                                                fullImagePath = rawEntry.fullImagePath
                                            )

                                            clipboardHistory.add(safeEntry)
                                        } else {
                                            logger.warning("Skipping invalid image entry: ${rawEntry.id}")
                                        }
                                    } else if (rawEntry.type == "text") {
                                        // Handle text entries
                                        val safeIndex = when (rawEntry.index) {
                                            is Int -> rawEntry.index
                                            is Long -> rawEntry.index.toInt()
                                            else -> 0 // Default if unexpected type
                                        }

                                        // Create a new, clean entry with proper types
                                        val safeEntry = HistoryEntry(
                                            id = rawEntry.id,
                                            type = rawEntry.type,
                                            createdAt = rawEntry.createdAt,
                                            content = rawEntry.content.toString(), // Force to string
                                            index = safeIndex,
                                            thumbnailPath = null,
                                            fullImagePath = null
                                        )

                                        clipboardHistory.add(safeEntry)
                                    }
                                }
                            } catch (e: Exception) {
                                logger.warning("Error processing history entry: ${e.message}")
                            }
                        }
                    } catch (e: ClassCastException) {
                        logger.warning("Error casting history data: ${e.message}")
                        // Try to recover by clearing history
                        clipboardHistory.clear()
                    }
                }

                // Sort by creation time (newest first)
                clipboardHistory.sortByDescending { it.createdAt }

                // Trim to max size just in case
                trimHistoryToMaxSize()

                logger.info("Loaded ${clipboardHistory.size} history items")
            }
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Error loading history", e)

            // If we had an error loading, start with a fresh history
            synchronized(historyLock) {
                clipboardHistory.clear()
            }
        }
    }

    /**
     * Get a list of all text items from history
     */
    fun getClipboardTextHistory(): List<String> {
        synchronized(historyLock) {
            return clipboardHistory
                .filter { it.type == "text" }
                .map { it.content as String }
        }
    }

    /**
     * Get a list of all screenshot items from history
     */
    fun getScreenshotHistory(): List<ScreenshotItem> {
        synchronized(historyLock) {
            return clipboardHistory
                .filter { it.type == "image" }
                .map {
                    ScreenshotItem(
                        id = it.id,
                        createdAt = it.createdAt,
                        thumbnailPath = it.thumbnailPath ?: "",
                        fullImagePath = it.fullImagePath ?: ""
                    )
                }
        }
    }

    /**
     * Called when clipboard ownership is lost - improved to handle both image and text
     */
    override fun lostOwnership(clipboard: Clipboard, contents: Transferable) {
        if (isShutdown) return

        executor?.schedule({
            if (isProcessing || isAppCopying) return@schedule

            try {
                isProcessing = true
                val newContents = clipboard.getContents(null)

                // Skip processing entirely if newContents is null
                if (newContents == null) {
                    isProcessing = false
                    return@schedule
                }

                // Handle both image and text content
                var hasHandledContent = false

                // Handle text content if available
                if (newContents.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                    if (!isFirstClipboardCheck) {
                        hasHandledContent = true
                        processContents(newContents)
                    } else {
                        lastContent = getTextFromContents(newContents)?.trim()
                        isFirstClipboardCheck = false
                        hasHandledContent = true
                    }
                }

                // Handle image content separately
                // Note: We don't try to take ownership of the clipboard for images
                if (newContents.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                    // Just log that we saw an image - actual processing happens in checkClipboard
                    logger.fine("Detected image in clipboard (ownership change)")
                    hasHandledContent = true
                }

                // Only try to take clipboard ownership for text content
                // Important: Do not attempt to take ownership of image data
                if (!isShutdown &&
                    newContents.isDataFlavorSupported(DataFlavor.stringFlavor) &&
                    !newContents.isDataFlavorSupported(DataFlavor.imageFlavor)
                ) {
                    clipboard.setContents(newContents, this)
                }

                if (!hasHandledContent) {
                    logger.fine("No content handled during ownership change")
                }
            } catch (e: Exception) {
                logger.log(Level.WARNING, "Error in lostOwnership", e)
                if (!isShutdown) {
                    setupClipboardMonitoring()
                }
            } finally {
                isProcessing = false
            }
        }, OWNERSHIP_RECOVERY_DELAY_MS, TimeUnit.MILLISECONDS)
    }

    /**
     * Helper to extract String from Transferable
     */
    private fun getTextFromContents(contents: Transferable): String? {
        return try {
            contents.getTransferData(DataFlavor.stringFlavor) as? String
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Error extracting text from clipboard", e)
            null
        }
    }

    /**
     * Updates the clipboard observer settings from the SettingsManager.
     * Call this method when settings have changed to apply the new settings.
     */
    fun updateSettings() {
        // Get the updated settings
        val settings = settingsManager.getSettings()
        val maxHistorySize = settings.maxHistoryItems

        // Update screenshot capturing flag
        isCatchScreenshootEnabled = settings.catchScreenshoot

        // Apply settings to history
        synchronized(historyLock) {
            // Sort by creation time to ensure newest are kept
            clipboardHistory.sortByDescending { it.createdAt }

            // Keep only up to max items
            while (clipboardHistory.size > maxHistorySize) {
                val removed = clipboardHistory.removeAt(clipboardHistory.size - 1)

                // If it's an image, also delete the files
                if (removed.type == "image") {
                    try {
                        if (removed.thumbnailPath != null) File(removed.thumbnailPath).delete()
                        if (removed.fullImagePath != null) File(removed.fullImagePath).delete()
                    } catch (e: Exception) {
                        logger.log(Level.WARNING, "Error deleting image files", e)
                    }
                }
            }

            // Save the updated history
            saveHistory()
        }

        logger.info("Settings updated: maxHistorySize=$maxHistorySize, catchScreenshoot=$isCatchScreenshootEnabled")
    }

    /**
     * Clear the current listener and shutdown monitoring
     */
    fun clearClipboardListener() {
        listener = null
        saveHistory()

        // Stop the clipboard monitoring timer safely
        stopClipboardMonitoring()

        try {
            isShutdown = true
            executor?.shutdown()
            if (executor?.awaitTermination(500, TimeUnit.MILLISECONDS) != true) {
                executor?.shutdownNow()
            }
        } catch (e: Exception) {
            executor?.shutdownNow()
        } finally {
            executor = null
        }

        try {
            clipboard.setContents(clipboard.getContents(null), this)
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Failed to clear clipboard contents", e)
        }

        logger.info("Clipboard listener cleared and monitoring stopped")
    }

    /**
     * Process clipboard contents and update history if needed
     */
    private fun processContents(contents: Transferable) {
        // Skip processing if no string flavor is available
        if (!contents.isDataFlavorSupported(DataFlavor.stringFlavor)) return

        try {
            val trimmedText = getTextFromContents(contents)?.trim() ?: return

            // Only process non-empty text
            if (trimmedText.isBlank()) return

            // Check if this is actually new content
            val textHash = hashString(trimmedText)
            if (textHash == lastTextHash) {
                logger.fine("Skipping duplicate text with hash: $textHash")
                return
            }

            // Update tracking variables
            lastTextHash = textHash
            lastContent = trimmedText

            // Add to unified history
            addTextToHistory(trimmedText)

            // Notify listener
            listener?.onClipboardTextChange(trimmedText, true)

        } catch (e: Exception) {
            logger.log(Level.WARNING, "Error handling clipboard text", e)
        }
    }

    /**
     * Temporarily disable monitoring (used when app is copying)
     */
    fun disableMonitoring() {
        isAppCopying = true

        if (isShutdown) {
            // Fallback if executor is shutdown
            Thread {
                Thread.sleep(APP_COPY_TIMEOUT_MS)
                isAppCopying = false
            }.start()
        } else {
            executor?.schedule({
                isAppCopying = false
            }, APP_COPY_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        }
    }

    /**
     * Copy text to clipboard from the app
     */
    fun copyTextFromApp(text: String) {
        try {
            disableMonitoring()
            val stringSelection = StringSelection(text)
            clipboard.setContents(stringSelection, this)
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Error copying text from app", e)
        }
    }

    /**
     * Copies a screenshot from the app back to the system clipboard
     */
//    fun copyScreenshotFromApp(screenshot: ScreenshotItem) {
//        // Set the flag to avoid processing our own clipboard changes
//        isAppCopying = true
//
//        try {
//            // Load the image from file
//            val file = File(screenshot.fullImagePath)
//            if (file.exists() && file.canRead()) {
//                val image = ImageIO.read(file)
//                if (image != null) {
//                    // Add to recent hashes to prevent re-detection
//                    val imageHash = calculateImageHash(image)
//                    recentImageHashes[imageHash] =
//                        System.currentTimeMillis() + IMAGE_DEDUPLICATION_TIMEOUT_MS * 2
//
//                    // Use our robust transferable
//                    val transferable = RobustImageTransferable(image)
//
//                    // Copy to clipboard without setting us as the owner
//                    clipboard.setContents(transferable, null)
//                    logger.info("Copied screenshot to clipboard: ${screenshot.id}, hash: $imageHash")
//
//                    // Reset the flag after a reasonable delay
//                    executor?.schedule({
//                        isAppCopying = false
//                    }, APP_COPY_TIMEOUT_MS * 3, TimeUnit.MILLISECONDS)
//                } else {
//                    logger.warning("Failed to load image from file: ${screenshot.fullImagePath}")
//                    isAppCopying = false
//                }
//            } else {
//                logger.warning("Screenshot file does not exist or is not readable: ${screenshot.fullImagePath}")
//                isAppCopying = false
//            }
//        } catch (e: Exception) {
//            logger.log(Level.WARNING, "Error copying screenshot to clipboard", e)
//            isAppCopying = false
//        }
//    }

    /**
     * Clear all history items with safe timer handling
     */
    fun clearAll() {
        // First stop monitoring temporarily
        val wasTimerActive = isTimerActive
        if (wasTimerActive) {
            stopClipboardMonitoring()
        }

        synchronized(historyLock) {
            // First, delete all screenshot files
            clipboardHistory.filter { it.type == "image" }.forEach { item ->
                try {
                    if (item.thumbnailPath != null) File(item.thumbnailPath).delete()
                    if (item.fullImagePath != null) File(item.fullImagePath).delete()
                } catch (e: Exception) {
                    logger.log(Level.WARNING, "Error deleting screenshot files", e)
                }
            }

            // Clear the list
            clipboardHistory.clear()

            // Save empty history
            saveHistory()
        }

        // Restart monitoring if it was active before
        if (wasTimerActive) {
            startClipboardMonitoring()
        }

        listener?.onClipboardClear()
        logger.info("All clipboard history cleared")
    }

    /**
     * Removes a specific item from history by ID
     */
    fun removeItemById(id: String) {
        synchronized(historyLock) {
            val item = clipboardHistory.find { it.id == id }

            if (item != null) {
                // Remove from list
                clipboardHistory.remove(item)

                // If it's an image, delete the files
                if (item.type == "image") {
                    try {
                        if (item.thumbnailPath != null) File(item.thumbnailPath).delete()
                        if (item.fullImagePath != null) File(item.fullImagePath).delete()
                    } catch (e: Exception) {
                        logger.log(Level.WARNING, "Error deleting files for item: $id", e)
                    }
                }

                // Save updated history
                executor?.submit { saveHistory() }
            }
        }
    }

    /**
     * Remove a specific text item from history
     */
    fun removeTextItem(text: String) {
        synchronized(historyLock) {
            // Find and remove entries with matching text
            val itemsToRemove = clipboardHistory.filter {
                it.type == "text" && it.content == text
            }

            clipboardHistory.removeAll(itemsToRemove)

            // Save updated history
            executor?.submit { saveHistory() }
        }
    }

}

//private class RobustImageTransferable(private val image: BufferedImage) : Transferable {
//    private val flavors = arrayOf(
//        DataFlavor.imageFlavor,
//        // Add more flavors that are compatible with system paste operations
//        DataFlavor("image/x-java-image; class=java.awt.Image", "Java Image")
//    )
//
//    override fun getTransferDataFlavors(): Array<DataFlavor> {
//        return flavors
//    }
//
//    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean {
//        return flavors.any { it.equals(flavor) }
//    }
//
//    override fun getTransferData(flavor: DataFlavor): Any {
//        if (isDataFlavorSupported(flavor)) {
//            return image
//        }
//        throw UnsupportedFlavorException(flavor)
//    }
//}