package com.lxmusic.android.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lxmusic.android.local.LocalGroup
import com.lxmusic.android.local.LocalSort
import com.lxmusic.android.local.MusicGroup
import com.lxmusic.android.ui.MainViewModel
import com.lxmusic.android.ui.components.Cover
import com.lxmusic.android.ui.components.HintIconButton
import com.lxmusic.android.ui.components.SearchBar
import com.lxmusic.android.ui.components.SearchBarHeight
import com.lxmusic.android.ui.components.EmptyState
import com.lxmusic.android.ui.components.SectionHeader
import com.lxmusic.android.ui.components.SkeletonList
import com.lxmusic.android.ui.components.TrackRow
import com.lxmusic.android.data.model.MusicInfo
import com.lxmusic.android.ui.components.toast
import com.lxmusic.android.ui.neon.NeonHairline
import com.lxmusic.android.ui.theme.OledTokens

@Composable
fun LocalScreen(viewModel: MainViewModel, onLongPress: (MusicInfo) -> Unit) {
    val state by viewModel.local.collectAsState()
    // V5.3-2: one-tap continue-listening card for the last played track.
    val lastPlayed by viewModel.lastPlayed.collectAsState()
    // V6.0: 正在播的那一行要能认出来。只取 id，避免整首歌对象变化触发整列表重组。
    val nowPlayingId = viewModel.player.collectAsState().value.current?.id
    Column(Modifier.fillMaxSize()) {
        // Search + group selector + refresh
        Row(
            Modifier
                .fillMaxWidth()
                .height(SearchBarHeight + 16.dp)
                .padding(horizontal = OledTokens.spaceMd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchBar(
                value = state.query,
                onValueChange = viewModel::setLocalQuery,
                placeholder = "搜索本地歌曲 / 歌手 / 专辑",
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(OledTokens.spaceXs))
            SortMenu(current = state.sort, onSelect = viewModel::setLocalSort)
            HintIconButton(
                icon = Icons.Default.CreateNewFolder,
                contentDescription = "管理文件夹",
                hint = "添加或移除扫描目录",
                onClick = { viewModel.openFolderManager() },
            )
            HintIconButton(
                icon = Icons.Default.Refresh,
                contentDescription = "刷新",
                hint = "重新扫描本地音乐",
                onClick = { viewModel.scanLocal() },
            )
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = OledTokens.spaceMd),
            horizontalArrangement = Arrangement.spacedBy(OledTokens.spaceSm),
        ) {
            GroupChip("歌曲", Icons.Default.QueueMusic, state.group == LocalGroup.SONGS, "按歌曲列出全部本地音乐") {
                viewModel.setLocalGroup(LocalGroup.SONGS)
            }
            GroupChip("专辑", Icons.Default.Album, state.group == LocalGroup.ALBUMS, "按专辑分组浏览") {
                viewModel.setLocalGroup(LocalGroup.ALBUMS)
            }
            GroupChip("歌手", Icons.Default.Person, state.group == LocalGroup.ARTISTS, "按歌手分组浏览") {
                viewModel.setLocalGroup(LocalGroup.ARTISTS)
            }
        }
        Spacer(Modifier.height(OledTokens.spaceXs))
        if (lastPlayed != null) {
            LastPlayedCard(lastPlayed!!, onClick = { viewModel.resumeLastPlayed() })
        }
        SectionHeader(title = "本地音乐", count = state.allTracks.size)

        when {
            state.loading -> SkeletonList()
            state.error != null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("扫描失败: ${state.error}", color = MaterialTheme.colorScheme.error)
            }
            state.group == LocalGroup.SONGS -> {
                if (state.songs.isEmpty()) {
                    EmptyState(
                        text = if (state.query.isBlank()) "未发现本地音乐，点击下方按钮扫描"
                               else "没有匹配的歌曲",
                        actionLabel = if (state.query.isBlank()) "管理扫描目录" else null,
                        onAction = if (state.query.isBlank()) { { viewModel.openFolderManager() } } else null,
                    )
                } else {
                    LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                        itemsIndexed(state.songs, key = { _, m -> m.id }) { i, music ->
                            TrackRow(
                                music = music,
                                onClick = { viewModel.playLocal(i) },
                                onLongClick = { onLongPress(music) },
                                active = music.id == nowPlayingId,
                            )
                            // V6.0: 换成渐隐线，深色底上不再连成硬栅栏。
                            NeonHairline()
                        }
                    }
                }
            }
            else -> {
                if (state.groups.isEmpty()) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Text("没有匹配的分组", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                        items(state.groups, key = { it.key }) { group ->
                            GroupRow(group) { viewModel.playGroup(group) }
                            NeonHairline()
                        }
                    }
                }
            }
        }
    }
}

/**
 * V5.3-2: "last played" resume card shown above the local list.
 */
@Composable
private fun LastPlayedCard(music: MusicInfo, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = OledTokens.screenMargin, vertical = OledTokens.spaceXs)
            .clip(RoundedCornerShape(OledTokens.radiusMd))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .clickable(onClick = onClick)
            .padding(horizontal = OledTokens.spaceMd, vertical = OledTokens.spaceMd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(music.img, 52.dp)
        Spacer(Modifier.width(OledTokens.spaceMd))
        Column(Modifier.weight(1f)) {
            Text(
                "上次播放",
                maxLines = 1,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                music.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                music.singer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.Default.PlayArrow,
            contentDescription = "继续播放",
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun GroupChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    hint: String,
    onClick: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    FilterChip(
        selected = selected,
        onClick = { toast(context, hint); onClick() },
        label = { Text(label) },
        leadingIcon = { Icon(icon, null, modifier = Modifier.width(18.dp)) },
    )
}

@Composable
private fun GroupRow(group: MusicGroup, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = OledTokens.screenMargin, vertical = OledTokens.spaceMd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(group.coverUrl, 56.dp)
        Spacer(Modifier.width(OledTokens.spaceMd))
        Column(Modifier.weight(1f)) {
            Text(group.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge)
            Text("${group.subtitle} · ${group.count} 首 · ${group.totalDuration}",
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable
private fun SortMenu(current: LocalSort, onSelect: (LocalSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        HintIconButton(
            icon = Icons.Default.Sort,
            contentDescription = "排序",
            hint = "选择歌曲排序方式",
            onClick = { open = true },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            LocalSort.entries.forEach { sort ->
                DropdownMenuItem(
                    text = { Text(sort.label + if (sort == current) "  ✓" else "") },
                    onClick = { onSelect(sort); open = false },
                )
            }
        }
    }
}
