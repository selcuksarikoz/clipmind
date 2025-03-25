package org.kozmonot.clipmind.views

// Remove the imports that are causing issues
import ThumbnailPreview
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow.Companion.Ellipsis
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.kozmonot.clipmind.constants.accentColor
import org.kozmonot.clipmind.constants.dangerColor
import org.kozmonot.clipmind.constants.textColor
import org.kozmonot.clipmind.services.ScreenshotItem
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun ScreenshotClipboardItem(
    item: ScreenshotItem,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Define a specific color for screenshots
    val borderColor = Color(0xFF9B59B6) // Purple color for screenshots

    // Format date
    val dateFormatter = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    val formattedDate = remember(item.createdAt) { dateFormatter.format(item.createdAt) }

    // We'll use a simpler approach to handle the image
    val hasThumbnail = remember(item.thumbnailPath) {
        val file = File(item.thumbnailPath)
        val exists = file.exists()
        println("Thumbnail check: ${item.thumbnailPath}, exists: $exists, readable: ${if (exists) file.canRead() else false}")
        exists
    }

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .then(modifier)
            .fillMaxWidth()
            .border(
                border = BorderStroke(
                    width = 1.dp,
                    color = Color.LightGray
                ),
                shape = RoundedCornerShape(10.dp)
            )
    ) {
        // Content section with left border
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left color border
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(60.dp)
                    .background(borderColor)
            )

            // Add the thumbnail with constraints
            Box(
                modifier = Modifier
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .height(48.dp)
                    .width(60.dp)
            ) {
                println("Rendering thumbnail: ${item.thumbnailPath}")
                ThumbnailPreview(
                    thumbnailPath = item.thumbnailPath,
                    hasThumbnail = hasThumbnail,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
            }

            // Screenshot info
            Text(
                text = "Screenshot - $formattedDate",
                maxLines = 1,
                overflow = Ellipsis,
                color = textColor,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.OpenInNew,
                    contentDescription = "Copy",
                    tint = accentColor,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onOpen() }
                        .padding(5.dp)
                )

                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete",
                    tint = dangerColor,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onDelete() }
                        .padding(5.dp)
                )
            }
        }

        // Bottom section with item type and ID
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF5F5F5))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Screenshot",
                color = borderColor,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp
            )
        }
    }
}