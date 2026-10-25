package com.lxmusic.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lxmusic.android.data.stats.PlayStats
import com.lxmusic.android.data.stats.StatsBucket
import com.lxmusic.android.data.stats.StatsRange
import com.lxmusic.android.ui.MainViewModel
import com.lxmusic.android.ui.components.ConfirmSheet
import com.lxmusic.android.ui.components.TrackRow
import com.lxmusic.android.ui.components.toast
import com.lxmusic.android.ui.neon.NeonHairline
import com.lxmusic.android.ui.neon.NeonScreen
import com.lxmusic.android.ui.neon.neonChip
import com.lxmusic.android.ui.neon.neonGlass
import com.lxmusic.android.ui.theme.OledTokens

/**
 * V5.3.3: play-count statistics.
 *
 * Two windows the user asked for - a day and a week - plus an all-time view.
 * The histogram switches granularity with the range (hours for a day, days
 * otherwise), and the list below ranks tracks by play count.
 */
@Composable
fun StatsScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val state by viewModel.stats.collectAsState()
    val context = LocalContext.current
    var confirmClear by remember { mutableStateOf(false) }

    NeonScreen(
        title = "播放统计",
        onBack = onBack,
        actions = {
            if (state.totalPlays > 0) {
                TextButton(onClick = { confirmClear = true }) {
                    Text("清空", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            RangePicker(current = state.range, onPick = { viewModel.setStatsRange(it) })

            SummaryCard(
                plays = state.totalPlays,
                tracks = state.tallies.size,
                range = state.range,
            )

            Histogram(buckets = state.buckets, range = state.range)

            Spacer(Modifier.height(OledTokens.spaceMd))

            when {
                state.loading -> Centered("统计中…")
                state.error != null -> Centered("读取失败：" + state.error!!)
                state.tallies.isEmpty() -> Centered(
                    if (state.range == StatsRange.TODAY) "今天还没有播放记录。"
                    else "这段时间还没有播放记录。"
                )
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                    item {
                        SectionLabel("播放次数排行")
                    }
                    itemsIndexed(state.tallies, key = { _, t -> "st_" + t.music.id }) { _, tally ->
                        TrackRow(
                            music = tally.music,
                            onClick = { viewModel.playStatsTrack(tally) },
                            trailing = {
                                Spacer(Modifier.width(OledTokens.spaceSm))
                                Text(
                                    tally.count.toString() + " 次",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            },
                        )
                        // V6.0: 渐隐分隔线，和本地/音乐库两屏统一。
                        NeonHairline()
                    }
                }
            }
        }
    }

    if (confirmClear) {
        ConfirmSheet(
            title = "清空播放统计",
            message = "全部播放记录将被删除，收藏、歌单与最近播放不受影响。",
            confirmLabel = "清空",
            onConfirm = {
                viewModel.clearPlayStats()
                toast(context, "已清空播放统计")
                confirmClear = false
            },
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
private fun RangePicker(current: StatsRange, onPick: (StatsRange) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = OledTokens.screenMargin),
        horizontalArrangement = Arrangement.spacedBy(OledTokens.spaceSm),
    ) {
        StatsRange.entries.forEach { r ->
            val selected = r == current
            // V6.0: 选中态从"实心色块"换成玻璃胶囊 + 品牌色描边。
            // 原来用 primary 实底 + onPrimary 文字，浅色主题下会是一块很重的蓝；
            // 现在选中与否都是玻璃，只靠描边亮度和文字色区分，整页更安静。
            Box(
                Modifier.weight(1f)
                    .neonChip(
                        shape = RoundedCornerShape(OledTokens.radiusSm),
                        active = selected,
                        accent = MaterialTheme.colorScheme.primary,
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                    ) { onPick(r) }
                    .padding(vertical = OledTokens.spaceSm),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    r.label,
                    color = if (selected) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(plays: Int, tracks: Int, range: StatsRange) {
    Row(
        Modifier.fillMaxWidth()
            .padding(horizontal = OledTokens.screenMargin, vertical = OledTokens.spaceMd),
        horizontalArrangement = Arrangement.spacedBy(OledTokens.spaceMd),
    ) {
        StatTile("总播放", plays.toString(), range.label, Modifier.weight(1f))
        StatTile("不重复曲目", tracks.toString(), "首", Modifier.weight(1f))
    }
}

@Composable
private fun StatTile(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    // V6.0: 原来这里硬写 OledTokens.card / foreground / mutedForeground，
    // 浅色主题下就是"近黑底 + 亮白字"，与整页的 onSurface 正文割裂。
    // 现在改成玻璃面板 + colorScheme 文字，两套主题自动成立。
    Column(
        modifier.neonGlass(shape = RoundedCornerShape(OledTokens.radiusMd))
            .padding(OledTokens.spaceMd),
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(OledTokens.spaceXs))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.width(OledTokens.spaceXs))
            Text(unit, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp))
        }
    }
}

/**
 * A plain bar chart.
 *
 * Bars are laid out as weighted columns rather than drawn on a Canvas, so the
 * chart cannot be distorted by a bad density or a zero-height parent: a bucket
 * with no plays simply renders a zero-height bar.
 */
@Composable
private fun Histogram(buckets: List<StatsBucket>, range: StatsRange) {
    if (buckets.isEmpty()) return
    val peak = buckets.maxOf { it.count }.coerceAtLeast(1)
    Column(Modifier.fillMaxWidth().padding(horizontal = OledTokens.screenMargin)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.BarChart, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(OledTokens.spaceXs))
            Text(
                if (range == StatsRange.TODAY) "按小时分布" else "按天分布",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text("峰值 " + peak, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(OledTokens.spaceSm))
        Row(
            Modifier.fillMaxWidth().height(96.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            buckets.forEach { b ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom) {
                    // V6.0: 有数据的柱子用品牌渐变（顶亮底暗），空档保留 rail 色。
                    // 渐变方向从下往上，让"增长"有方向感；峰值那根再亮一点。
                    val isPeak = b.count == peak && b.count > 0
                    Box(
                        Modifier.fillMaxWidth()
                            .height((72f * b.count / peak).dp.coerceAtLeast(if (b.count > 0) 2.dp else 0.dp))
                            .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                            .background(
                                if (b.count > 0) {
                                    Brush.verticalGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.primary.copy(
                                                alpha = if (isPeak) 0.55f else 0.34f
                                            ),
                                        )
                                    )
                                } else {
                                    Brush.verticalGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.surfaceVariant,
                                            MaterialTheme.colorScheme.surfaceVariant,
                                        )
                                    )
                                }
                            ),
                    )
                }
            }
        }
        Spacer(Modifier.height(OledTokens.spaceXs))
        // Only the ends are labelled: 24 hour ticks would overlap on a phone.
        Row(Modifier.fillMaxWidth()) {
            Text(buckets.first().label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(buckets.last().label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = OledTokens.screenMargin, vertical = OledTokens.spaceSm),
    )
}

@Composable
private fun Centered(text: String) {
    Box(Modifier.fillMaxWidth().padding(OledTokens.spaceXl), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}
