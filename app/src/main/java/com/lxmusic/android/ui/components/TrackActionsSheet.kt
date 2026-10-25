package com.lxmusic.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lxmusic.android.data.model.MusicInfo
import com.lxmusic.android.data.repo.PlaylistWithTracks

/** Bottom sheet shown when long-pressing a track: favourite + add to playlist. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackActionsSheet(
    music: MusicInfo,
    isFavorite: Boolean,
    playlists: List<PlaylistWithTracks>,
    onDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: (Long) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    /** Only provided for local tracks: drops the library row, keeps the file (T4). */
    onRemoveFromLibrary: (() -> Unit)? = null,
    /** Only provided for local tracks: deletes the underlying file (T4). */
    onDeleteFile: (() -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showCreate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                music.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            Text(
                music.singer,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(12.dp))
            Divider()

            ActionRow(
                icon = { Icon(if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null) },
                text = if (isFavorite) "取消收藏" else "收藏",
                onClick = { onToggleFavorite(); onDismiss() },
            )
            if (onRemoveFromPlaylist != null) {
                ActionRow(
                    icon = { Icon(Icons.Default.Delete, null) },
                    text = "从歌单移除",
                    onClick = { onRemoveFromPlaylist(); onDismiss() },
                )
            }
            if (onRemoveFromLibrary != null) {
                Divider()
                ActionRow(
                    icon = { Icon(Icons.Default.LibraryMusic, null) },
                    text = "从音乐库移除",
                    subtitle = "仅移出列表，不删除文件",
                    onClick = { onRemoveFromLibrary(); onDismiss() },
                )
            }
            if (onDeleteFile != null) {
                ActionRow(
                    icon = { Icon(Icons.Default.DeleteForever, null,
                        tint = MaterialTheme.colorScheme.error) },
                    text = "删除文件",
                    subtitle = "同时删除设备上的音频文件，不可恢复",
                    textColor = MaterialTheme.colorScheme.error,
                    onClick = { confirmDelete = true },
                )
            }
            Divider()
            Text(
                "添加到歌单",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            LazyColumn(Modifier.height(220.dp)) {
                item {
                    ActionRow(
                        icon = { Icon(Icons.Default.Add, null) },
                        text = "新建歌单…",
                        onClick = { showCreate = true },
                    )
                }
                items(playlists, key = { it.id }) { pl ->
                    ActionRow(
                        icon = {},
                        text = pl.name,
                        subtitle = "${pl.tracks.size} 首",
                        onClick = { onAddToPlaylist(pl.id); onDismiss() },
                    )
                }
            }
        }
    }

    if (showCreate) {
        CreatePlaylistDialog(
            onDismiss = { showCreate = false },
            onCreate = { name -> onCreatePlaylist(name); showCreate = false; onDismiss() },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除文件？") },
            text = { Text("将永久删除设备上的《${music.name}》，此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDeleteFile?.invoke()
                    onDismiss()
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun ActionRow(
    icon: @Composable () -> Unit,
    text: String,
    subtitle: String? = null,
    textColor: androidx.compose.ui.graphics.Color? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        icon()
        Column {
            Text(text, style = MaterialTheme.typography.bodyLarge, color = textColor ?: MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun CreatePlaylistDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建歌单") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = { Text("歌单名称") },
            )
        },
        confirmButton = { TextButton(onClick = { onCreate(name) }) { Text("创建") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}