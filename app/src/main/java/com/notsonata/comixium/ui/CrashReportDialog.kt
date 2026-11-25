package com.notsonata.comixium.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notsonata.comixium.MainActivity
import com.notsonata.comixium.ui.theme.LocalComixColors

/** After a crash: what went wrong, to copy or share with the developer. Shown once, on the next launch. */
@Composable
fun CrashReportDialog(report: String, onDone: () -> Unit) {
    val c = LocalComixColors.current
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Comixium closed unexpectedly") },
        text = {
            Column {
                Text("This report says where it went wrong. It stays on your phone unless you share it.")
                Spacer(Modifier.height(12.dp))
                Text(
                    report,
                    Modifier.fillMaxWidth().heightIn(max = 220.dp).clip(RoundedCornerShape(8.dp)).background(c.bg)
                        .verticalScroll(rememberScrollState()).padding(10.dp),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    color = c.text2,
                )
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = { copy(context, report); onDone() }) { Text("Copy", color = c.text2) }
                TextButton(onClick = { share(context, report); onDone() }) { Text("Share", color = c.accent) }
            }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Close", color = c.text2) } },
        containerColor = c.surface,
        titleContentColor = c.textEmphasis,
        textContentColor = c.text2,
    )
}

private fun copy(context: Context, report: String) {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Comixium crash report", report))
    // Android 13+ confirms copies itself.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
}

private fun share(context: Context, report: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report)
    val chooser = Intent.createChooser(send, "Share crash report")
        // Comixium takes shared text itself (for Google sign-in); keep it out of the list.
        .putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(context, MainActivity::class.java)))
    runCatching { context.startActivity(chooser) }
}
