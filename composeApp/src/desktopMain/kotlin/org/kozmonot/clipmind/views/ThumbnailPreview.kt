import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.sp
import java.io.File
import javax.imageio.ImageIO

@Composable
fun ThumbnailPreview(
    thumbnailPath: String,
    hasThumbnail: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.background(Color(0xFFEEEEEE)),
        contentAlignment = Alignment.Center
    ) {
        if (hasThumbnail) {
            // Load the image from file
            val imageBitmap = remember(thumbnailPath) {
                try {
                    val file = File(thumbnailPath)
                    if (file.exists()) {
                        val bufferedImage = ImageIO.read(file)
                        if (bufferedImage != null) {
                            return@remember bufferedImage.toComposeImageBitmap()
                        }
                    }
                    null
                } catch (e: Exception) {
                    println("Error loading thumbnail: $thumbnailPath")
                    e.printStackTrace()
                    null
                }
            }

            if (imageBitmap != null) {
                // Display the actual image
                Image(
                    bitmap = imageBitmap,
                    contentDescription = "Screenshot thumbnail",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Display error message if loading failed
                Text(
                    text = "Error",
                    color = Color.Red,
                    fontSize = 10.sp
                )
            }
        } else {
            // Display placeholder if thumbnail doesn't exist
            Text(
                text = "No Preview",
                color = Color.Gray,
                fontSize = 10.sp
            )
        }
    }
}