package com.lxmusic.android.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lxmusic.android.data.model.MusicInfo
import com.lxmusic.android.data.repo.PlaylistWithTracks
import com.lxmusic.android.ui.MainViewModel
import com.lxmusic.android.ui.components.Cover
import com.lxmusic.android.ui.components.CreatePlaylistDialog
import com.lxmusic.android.ui.components.TrackRow
import com.lxmusic.android.ui.components.ConfirmSheet
import com.lxmusic.android.ui.components.HintIconButton
import com.lxmusic.android.ui.components.toast
import com.lxmusic.android.ui.neon.NeonHairline
import com.lxmusic.android.ui.theme.OledTokens


@Composable
fun LibraryScreen(
    viewModel: MainViewModel,
    onLongPress: (MusicInfo, Long?) -> Unit,
) {
    val lib by viewModel.library.collectAsState()
    // V6.0: 与 LocalScreen 一致，当前播放行要能被认出来。
    val nowPlayingId = viewModel.player.collectAsState().value.current?.id
    var showCreate by remember { mutableStateOf(false) }
    var openPlaylist by remember { mutableStateOf<PlaylistWithTracks?>(null) }

    if (openPlaylist != null) {
        PlaylistDetail(
            playlist = openPlaylist!!,
            viewModel = viewModel,
            onBack = { openPlaylist = null },
            onLongPress = { onLongPress(it, openPlaylist!!.id) },
        )
        return
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
        item { LibrarySectionHeader(Icons.Default.Favorite, "我的收藏", lib.favorites.size, null, null) }
        if (lib.favorites.isEmpty()) {
            item { EmptyHint("还没有收藏的歌曲。长按任意歌曲即可收藏。") }
        } else {
            itemsIndexed(lib.favorites, key = { _, m -> "fav_" + m.id }) { i, music ->
                TrackRow(music, onClick = { viewModel.playFavorites(i) }, onLongClick = { onLongPress(music, null) },
                    active = music.id == nowPlayingId)
                NeonHairline()
            }
        }

        item {
            LibrarySectionHeader(Icons.Default.History, "最近播放", lib.recent.size,
                if (lib.recent.isNotEmpty()) "清空" else null, { viewModel.clearHistory() })
        }
        if (lib.recent.isEmpty()) {
            item { EmptyHint("还没有播放记录。") }
        } else {
            itemsIndexed(lib.recent.take(20), key = { _, m -> "rec_" + m.id }) { i, music ->
                TrackRow(music, onClick = { viewModel.playRecent(i) }, onLongClick = { onLongPress(music, null) },
                    active = music.id == nowPlayingId)
                NeonHairline()
            }
        }

        item { LibrarySectionHeader(Icons.Default.PlaylistPlay, "歌单", lib.playlists.size, "新建", { showCreate = true }) }
        if (lib.playlists.isEmpty()) {
            item { EmptyHint("还没有歌单，点右上角新建。") }
        } else {
            items(lib.playlists, key = { "pl_" + it.id }) { pl ->
                PlaylistRow(pl, onClick = { openPlaylist = pl }, onPlay = { viewModel.playPlaylist(pl) },
                    onRename = { name -> viewModel.renamePlaylist(pl.id, name) },
                    onDelete = { viewModel.deletePlaylist(pl.id) })
                NeonHairline()
            }
        }
    }

    if (showCreate) {
        CreatePlaylistDialog(onDismiss = { showCreate = false },
            onCreate = { name -> viewModel.createPlaylist(name); showCreate = false })
    }
}

@Composable
private fun PlaylistDetail(
    playlist: PlaylistWithTracks,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onLongPress: (MusicInfo) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // V6.0: 歌单详情里也能看出哪首在播。
    val nowPlayingId = viewModel.player.collectAsState().value.current?.id
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = OledTokens.spaceMd, vertical = OledTokens.spaceSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { toast(context, "返回歌单列表"); onBack() }) { Text("← 返回") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { toast(context, "从第一首开始播放这个歌单"); viewModel.playPlaylist(playlist) }) { Text("播放全部") }
        }
        Text(playlist.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp))
        Text((playlist.tracks.size.toString() + " 首歌曲"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = OledTokens.screenMargin, vertical = OledTokens.spaceXs))
        if (playlist.tracks.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("歌单还是空的，去长按歌曲添加吧。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                itemsIndexed(playlist.tracks, key = { _, m -> playlist.id.toString() + "_" + m.id }) { i, music ->
                    TrackRow(music, onClick = { viewModel.playPlaylist(playlist, i) }, onLongClick = { onLongPress(music) },
                        active = music.id == nowPlayingId)
                    NeonHairline()
                }
            }
        }
    }
}

@Composable
private fun PlaylistRow(
    playlist: PlaylistWithTracks,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }

    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = OledTokens.screenMargin, vertical = OledTokens.spaceMd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(playlist.coverUrl, 56.dp)
        Spacer(Modifier.width(OledTokens.spaceMd))
        Column(Modifier.weight(1f)) {
            Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            Text(playlist.tracks.size.toString() + " 首歌曲",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HintIconButton(
            icon = Icons.Default.PlaylistPlay,
            contentDescription = "播放",
            hint = "播放整个歌单",
            onClick = onPlay,
        )
        Box {
            HintIconButton(
                icon = Icons.Default.MoreVert,
                contentDescription = "更多",
                hint = "重命名或删除歌单",
                onClick = { menu = true },
            )
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("重命名") }, onClick = { menu = false; showRename = true })
                DropdownMenuItem(text = { Text("删除歌单") }, onClick = { menu = false; showDeleteConfirm = true })
            }
        }
    }

    if (showRename) {
        CreatePlaylistDialog(onDismiss = { showRename = false }, onCreate = { name -> onRename(name); showRename = false })
    }

    if (showDeleteConfirm) {
        ConfirmSheet(
            title = "删除歌单",
            message = "“" + playlist.name + "” 将被移除，歌曲本身不受影响。",
            confirmLabel = "删除",
            onConfirm = onDelete,
            onDismiss = { showDeleteConfirm = false },
        )
    }
}

@Composable
private fun LibrarySectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    count: Int,
    action: String?,
    onAction: (() -> Unit)?,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = OledTokens.screenMargin, vertical = OledTokens.spaceMd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(OledTokens.spaceSm))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(OledTokens.spaceSm))
        Text("$count", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        if (action != null) {
            val context = androidx.compose.ui.platform.LocalContext.current
            TextButton(onClick = { toast(context, if (action == "新建") "创建一个新的歌单" else "清空全部播放记录"); onAction?.invoke() }) { Text(action) }
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = OledTokens.screenMargin, vertical = OledTokens.spaceSm))
}
