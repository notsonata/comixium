package com.notsonata.comixium.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notsonata.comixium.ui.icons.Lucide
import com.notsonata.comixium.ui.theme.LocalComixColors
import com.notsonata.comixium.ui.theme.Poppins
import com.notsonata.comixium.web.Downloads
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Long-press on an image: save it, share it, or copy its link. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageSheet(imageUrl: String, pageTitle: String, userAgent: String?, onDismiss: () -> Unit) {
    val c = LocalComixColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val baseName = pageTitle.replace(Regex("[^\\p{L}\\p{N} ._-]"), "").trim().take(60).ifBlank { "comixium" } +
        "-" + System.currentTimeMillis() / 1000

    fun withImage(action: (ByteArray, String) -> Unit) {
        onDismiss()
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { Downloads.fetch(imageUrl, userAgent) } }
            result.onSuccess { (bytes, mime) -> action(bytes, mime) }
                .onFailure { Toast.makeText(context, "Couldn't load the image", Toast.LENGTH_SHORT).show() }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surface, contentColor = c.textEmphasis) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Text(
                imageUrl,
                style = TextStyle(fontFamily = Poppins, fontSize = 12.sp),
                color = c.text3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp),
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                SheetRow(Lucide.Download, "Save image") {
                    withImage { bytes, mime ->
                        val ok = Downloads.saveImage(context, bytes, mime, baseName)
                        Toast.makeText(context, if (ok) "Saved to Pictures/Comixium" else "Couldn't save the image", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            SheetRow(Lucide.Share, "Share image") {
                withImage { bytes, mime -> Downloads.shareImage(context, bytes, mime, baseName) }
            }
            SheetRow(Lucide.Link, "Copy image link") {
                onDismiss()
                context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Image link", imageUrl))
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

@Composable
private fun SheetRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    val c = LocalComixColors.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).height(52.dp).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = c.text2, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, style = TextStyle(fontFamily = Poppins, fontSize = 15.sp, fontWeight = FontWeight.Medium), color = c.textEmphasis)
    }
}
