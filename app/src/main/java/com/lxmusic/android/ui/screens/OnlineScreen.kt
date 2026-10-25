package com.lxmusic.android.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.lxmusic.android.data.model.MusicInfo
import com.lxmusic.android.source.LxSource
import com.lxmusic.android.ui.MainViewModel
import com.lxmusic.android.ui.components.SearchBar
import com.lxmusic.android.ui.components.SearchBarHeight
import com.lxmusic.android.ui.components.TrackRow
import com.lxmusic.android.ui.components.toast
import com.lxmusic.android.ui.neon.NeonHairline
import com.lxmusic.android.ui.theme.OledTokens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineScreen(viewModel: MainViewModel, onLongPress: (MusicInfo) -> Unit) {
    val state by viewModel.search.collectAsState()
    val loadedApi by viewModel.loadedApiId.collectAsState()
    var showImport by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(SearchBarHeight + 16.dp)
                .padding(horizontal = OledTokens.spaceMd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchBar(
                value = state.keyword,
                onValueChange = viewModel::setKeyword,
                placeholder = "搜索歌曲 / 歌手",
                modifier = Modifier.weight(1f),
                onSearch = { viewModel.search() },
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = OledTokens.spaceMd),
            horizontalArrangement = Arrangement.spacedBy(OledTokens.spaceSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LxSource.online.forEach { src ->
                FilterChip(
                    selected = state.source == src,
                    onClick = { toast(context, "切换到 " + sourceLabel(src) + " 音源"); viewModel.setSource(src) },
                    label = { Text(sourceLabel(src)) },
                )
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = {
                toast(context, if (showImport) "收起音源导入面板" else "粘贴自定义音源脚本以解析播放地址")
                showImport = !showImport
            }) {
                Text(if (loadedApi == null) "音源" else "已加载")
            }
        }

        if (showImport) SourceImportPanel(viewModel)

        when {
            state.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            state.error != null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("出错: ${state.error}", color = MaterialTheme.colorScheme.error)
            }
            state.results.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("输入关键词后点击搜索", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                itemsIndexed(state.results, key = { _, m -> m.id }) { i, music ->
                    TrackRow(
                        music = music,
                        onClick = { viewModel.playFromResults(i) },
                        onLongClick = { onLongPress(music) },
                    )
                    NeonHairline()
                }
            }
        }
    }
}

@Composable
private fun SourceImportPanel(viewModel: MainViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var text by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text("粘贴自定义源脚本 (JS)", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(OledTokens.spaceSm))
        OutlinedTextField(
            value = text, onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth().height(120.dp),
            placeholder = { Text("/*\n * @name 我的音源\n */\n...") },
        )
        Spacer(Modifier.height(OledTokens.spaceSm))
        TextButton(onClick = {
            toast(context, "解析脚本并加载音源")
            viewModel.importSource(text)
            text = ""
        }) { Text("导入并加载") }
        Spacer(Modifier.height(OledTokens.spaceXs))
        Text("提示：QQ/网易 等音源可在设置中导入对应脚本；已加载的音源用于解析播放地址。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun sourceLabel(s: LxSource): String = when (s) {
    LxSource.KW -> "酷我"
    LxSource.KG -> "酷狗"
    LxSource.TX -> "QQ"
    LxSource.WY -> "网易"
    LxSource.MG -> "咪咕"
    LxSource.XM -> "虾米"
    LxSource.LOCAL -> "本地"
}