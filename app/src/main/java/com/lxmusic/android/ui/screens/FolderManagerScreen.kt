package com.lxmusic.android.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lxmusic.android.ui.MainViewModel
import com.lxmusic.android.ui.neon.NeonHairline
import com.lxmusic.android.ui.neon.NeonScreen
import com.lxmusic.android.ui.neon.NeonTokens
import com.lxmusic.android.ui.components.HintIconButton
import com.lxmusic.android.ui.components.toast

/**
 * Manages the authorised scan folders (T2) and manually added files (T3).
 *
 * Folder grants use [ActivityResultContracts.OpenDocumentTree]; single files use
 * [ActivityResultContracts.OpenMultipleDocuments]. Both are persisted by the
 * view model so they survive a reboot.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderManagerScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val scanning by viewModel.scanning.collectAsState()

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? -> if (uri != null) viewModel.addScanFolder(uri.toString()) }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> -> if (uris.isNotEmpty()) viewModel.addManualFiles(uris.map { it.toString() }) }

    NeonScreen(
        title = "扫描文件夹",
        onBack = onBack,
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth().clickable { toast(context, "选择一个文件夹，自动扫描其中的音频"); folderPicker.launch(null) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text("添加文件夹", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "授予一个文件夹的访问权限，自动扫描其中的音频",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth().clickable { toast(context, "手动添加单个音频文件"); filePicker.launch(arrayOf("audio/*")) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Icon(Icons.Default.AudioFile, null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text("添加单个音频文件", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "手动挑选音频文件并加入音乐库",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (scanning) {
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("正在扫描…", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            item { SectionTitle("已授权的文件夹 (${settings.scanFolders.size})") }
            if (settings.scanFolders.isEmpty()) {
                item { EmptyHint("还没有授权任何文件夹") }
            } else {
                items(settings.scanFolders.toList(), key = { it }) { uri ->
                    EntryRow(
                        icon = { Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary) },
                        title = friendlyPath(uri),
                        subtitle = uri,
                        onDelete = { viewModel.removeScanFolder(uri) },
                    )
                }
            }

            item { SectionTitle("手动添加的音频 (${settings.manualUris.size})") }
            if (settings.manualUris.isEmpty()) {
                item { EmptyHint("还没有手动添加音频") }
            } else {
                items(settings.manualUris.toList(), key = { it }) { uri ->
                    EntryRow(
                        icon = { Icon(Icons.Default.AudioFile, null, tint = MaterialTheme.colorScheme.primary) },
                        title = Uri.decode(uri.substringAfterLast('/')),
                        subtitle = uri,
                        onDelete = { viewModel.removeManualFile(uri) },
                    )
                }
            }
        }
    }
}

/** Turns a SAF tree uri into something readable, e.g. "primary:Music". */
internal fun friendlyPath(treeUri: String): String {
    val decoded = Uri.decode(treeUri)
    val idx = decoded.indexOf("tree/")
    val tail = if (idx >= 0) decoded.substring(idx + 5) else decoded
    return tail.substringBeforeLast('/').ifBlank { tail }
}

@Composable
private fun SectionTitle(text: String) {
    Column {
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(width = 3.dp, height = 14.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Brush.verticalGradient(NeonTokens.brandGradient())),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        NeonHairline()
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EntryRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onDelete: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        icon()
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        HintIconButton(
            icon = Icons.Default.Delete,
            contentDescription = "移除",
            hint = "移除这个目录，不会删除文件",
            onClick = onDelete,
            tint = MaterialTheme.colorScheme.error,
        )
    }
}
