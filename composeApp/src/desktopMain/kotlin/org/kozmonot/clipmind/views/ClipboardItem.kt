package org.kozmonot.clipmind.views

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
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow.Companion.Ellipsis
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.kozmonot.clipmind.constants.ItemType
import org.kozmonot.clipmind.constants.accentColor
import org.kozmonot.clipmind.constants.dangerColor
import org.kozmonot.clipmind.constants.secondaryTextColor
import org.kozmonot.clipmind.constants.textColor

@Composable
fun ClipboardItem(
    item: String,
    onCopy: () -> Unit = {},
    onDelete: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val itemType = determineItemType(item)
    val borderColor = getColorForItemType(itemType)
    val charCount = item.length
    val typeLabel = getTypeLabel(itemType)

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
//                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left color border
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(60.dp)
                    .background(borderColor)
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = item.take(200).replace("\n", ""),
                    maxLines = 1,
                    overflow = Ellipsis,
                    color = textColor,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = "Copy",
                    tint = accentColor,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            //show inform or toast on kotlin
                            onCopy()
                        }
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

        // Bottom section with item type and character count
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF5F5F5))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = typeLabel,
                color = borderColor,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp
            )

            Text(
                text = "$charCount characters",
                color = secondaryTextColor,
                fontSize = 12.sp
            )
        }
    }
}

/**
 * Determines the type of content in the clipboard item
 */
private fun determineItemType(text: String): ItemType {
    // Check if it's a URL
    if (text.trim().matches(Regex("^(https?|ftp)://.*$")) ||
        text.trim().matches(Regex("^www\\..*$"))
    ) {
        return ItemType.URL
    }

    // Check if it's code (simplified check - looks for common programming patterns)
    if (text.contains("{") && text.contains("}") &&
        (text.contains("function") || text.contains("class") ||
                text.contains("def ") || text.contains("import ") ||
                text.contains("public ") || text.contains("private "))
    ) {
        return ItemType.CODE
    }

    return ItemType.TEXT
}

/**
 * Returns the appropriate color based on content type
 */
private fun getColorForItemType(type: ItemType): Color {
    return when (type) {
        ItemType.URL -> Color(0xFF3498DB)    // Bright blue for URLs
        ItemType.CODE -> Color(0xFF16A085)   // Teal for code
        ItemType.TEXT -> Color(0xFF95A5A6)   // Gray for plain text
    }
}

/**
 * Returns a readable label for the item type
 */
private fun getTypeLabel(type: ItemType): String {
    return when (type) {
        ItemType.URL -> "URL"
        ItemType.CODE -> "Code Snippet"
        ItemType.TEXT -> "Text"
    }
}