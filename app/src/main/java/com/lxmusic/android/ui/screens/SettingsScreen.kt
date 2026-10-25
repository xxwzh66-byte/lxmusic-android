package com.lxmusic.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lxmusic.android.data.settings.ThemeMode
import com.lxmusic.android.ui.MainViewModel
import com.lxmusic.android.ui.neon.NeonHairline
import com.lxmusic.android.ui.neon.NeonScreen
import com.lxmusic.android.ui.neon.NeonTokens
import com.lxmusic.android.ui.theme.AppPalettes
import com.lxmusic.android.ui.components.toast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val s by viewModel.settings.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    var showClearDialog by remember { mutableStateOf(false) }

    NeonScreen(
        title = "设置",
        onBack = onBack,
        themeColor = s.themeColor,
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            // ---------------- appearance ----------------
            item { SectionHeader("外观") }

            item {
                ChoiceRow(
                    title = "主题模式",
                    options = listOf(
                        ThemeMode.SYSTEM to "跟随系统",
                        ThemeMode.LIGHT to "亮色",
                        ThemeMode.DARK to "暗色",
                    ),
                    selected = s.themeMode,
                    onSelect = viewModel::setThemeMode,
                )
            }

            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("主题色", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AppPalettes.forEach { p ->
                            val selected = p.id == s.themeColor
                            Box(
                                Modifier.size(36.dp).clip(CircleShape)
                                    .background(Brush.linearGradient(p.gradient))
                                    .border(
                                        width = if (selected) 3.dp else 0.dp,
                                        color = if (selected) MaterialTheme.colorScheme.onSurface
                                        else MaterialTheme.colorScheme.surface,
                                        shape = CircleShape,
                                    )
                                    .clickable {
                                        toast(context, "切换主题色：" + p.label)
                                        viewModel.setThemeColor(p.id)
                                    },
                            )
                        }
                    }
                }
            }

            item {
                SwitchRow(
                    title = "播放页显示歌名信息",
                    subtitle = "关闭后唱片样式不再显示歌名与歌手",
                    checked = s.showPlayerMeta,
                    onCheckedChange = viewModel::setShowPlayerMeta,
                )
            }

            // V6-5: launcher icon switcher (activity-alias based).
            // V5-2: 列表样式 / 播放页样式入口已删除，数据层保留（存量密度继续生效，换肤入口在播放页右上角）

            // ---------------- music library ----------------
            item { SectionHeader("音乐库") }

            item {
                InfoRow(
                    title = "管理扫描文件夹",
                    subtitle = "已授权 ${s.scanFolders.size} 个文件夹 · 已手动添加 ${s.manualUris.size} 首",
                    onClick = viewModel::openFolderManager,
                )
            }

            item { SwitchRow("启动时自动扫描", null, s.autoScanOnStart, viewModel::setAutoScan) }
            item { SwitchRow("扫描时读取内嵌封面", null, s.readEmbeddedCover, viewModel::setReadEmbeddedCover) }

            // ---------------- playback ----------------
            item { SectionHeader("播放") }

            // V4-4: 默认音质入口已屏蔽，数据层保留（AudioQuality / setQuality 不动）
            item { SwitchRow("播放时记录历史", null, s.recordHistory, viewModel::setRecordHistory) }
            item { SwitchRow("记录播放统计", "只影响「播放统计」页，与「最近播放」列表无关", s.recordStats, viewModel::setRecordStats) }
            item { SwitchRow("耳机拔出时暂停", null, s.pauseOnHeadsetUnplug, viewModel::setPauseOnHeadsetUnplug) }

            // ---------------- lyrics ----------------
            item { SectionHeader("歌词") }

            item {
                SliderRow(
                    title = "歌词字体大小",
                    value = s.lyricFontScale,
                    valueRange = 0.7f..1.6f,
                    valueText = "%.2f×".format(s.lyricFontScale),
                    onValueChange = viewModel::setLyricFontScale,
                )
            }

            item {
                SliderRow(
                    title = "歌词偏移校准",
                    value = s.lyricOffsetMs.toFloat(),
                    valueRange = -500f..500f,
                    valueText = "${s.lyricOffsetMs} ms",
                    onValueChange = { viewModel.setLyricOffset(it.toInt()) },
                )
            }

            item { SwitchRow("优先读取同目录 .lrc", null, s.preferLrcFile, viewModel::setPreferLrcFile) }

            item {
                InfoRow(
                    title = "版本",
                    subtitle = viewModel.versionName,
                    onClick = {
                        val cm = context.getSystemService(
                            android.content.Context.CLIPBOARD_SERVICE,
                        ) as android.content.ClipboardManager
                        cm.setPrimaryClip(
                            android.content.ClipData.newPlainText("version", viewModel.versionName),
                        )
                        toast(context, "已复制版本号：" + viewModel.versionName)
                    },
                )
            }

            // V5-1/V5-3: 更新公告与关于分组已删除
            item {
                InfoRow(
                    title = "清空数据",
                    subtitle = "删除收藏、歌单、历史与全部设置",
                    danger = true,
                    onClick = { showClearDialog = true },
                )
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清空数据") },
            text = { Text("将删除所有收藏、自建歌单、播放历史与设置，不可恢复。是否继续？") },
            confirmButton = {
                TextButton(onClick = { viewModel.clearAllData(); showClearDialog = false }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showClearDialog = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Column {
        Spacer(Modifier.height(8.dp))
        // V6.0：分组标题从"换色小字"升级为"品牌竖条 + 标题"，
        // 与主页 NeonSectionHeader、二级页顶栏共用同一个章节锚点。
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Brush.verticalGradient(NeonTokens.brandGradient())),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        NeonHairline()
    }
}

@Composable
private fun <T> ChoiceRow(title: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (value, label) ->
                val isSel = value == selected
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isSel) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .background(
                            if (isSel) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .clickable { onSelect(value) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SliderRow(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueText: String,
    onValueChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = valueRange)
    }
}

@Composable
private fun InfoRow(title: String, subtitle: String?, danger: Boolean = false, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
