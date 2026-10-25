package com.lxmusic.android.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lxmusic.android.CrashLogger

private const val TITLE = "上次崩溃日志（可复制反馈）"
private const val COPY = "复制"
private const val CLEAR = "清除"

/**
 * Shows the trace captured by [CrashLogger] on the previous run, if any.
 * Copy (or screenshot) the text, then clear it so the dialog stops appearing.
 */
@Composable
fun CrashReportDialog(onClear: () -> Unit = {}) {
    val context = LocalContext.current
    var report by remember { mutableStateOf(CrashLogger.lastCrash(context)) }
    val shown = report ?: return
    AlertDialog(
        onDismissRequest = { report = null },
        title = { Text(TITLE) },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                Text(shown, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                runCatching {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("crash", shown))
                }
                report = null
            }) { Text(COPY) }
        },
        dismissButton = {
            TextButton(onClick = { onClear(); report = null }) { Text(CLEAR) }
        },
    )
}
