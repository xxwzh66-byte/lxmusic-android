package com.lxmusic.android.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.lxmusic.android.data.model.MusicInfo
import com.lxmusic.android.data.settings.ListStyle
import com.lxmusic.android.data.settings.SettingsStore
import com.lxmusic.android.ui.neon.NeonTiming
import com.lxmusic.android.ui.theme.OledTokens

/** Rounded album art with a music-note fallback. */
@Composable
fun Cover(url: String?, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(OledTokens.radiusSm))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(
                model = url, contentDescription = null,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(Icons.Default.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * A single track row. Long-press opens the actions sheet.
 *
 * V6.0: [active] 标出"这一行就是正在播的那首"。之前列表里没有任何当前项标记，
 * 用户从播放页返回后要靠记忆找位置。这里不换配色方案（列表正文的对比度是
 * OledTokensTest 锁死的），只加两个不占布局的东西：左侧 2dp 霓虹竖条 + 整行
 * 极淡的品牌色底。颜色都走 alpha，所以深浅两套主题都不会出现硬边。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackRow(
    music: MusicInfo,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    showDuration: Boolean = true,
    active: Boolean = false,
    trailing: @Composable () -> Unit = {},
) {
    // A1: density follows SettingsStore.listStyle; STANDARD keeps the legacy pixels.
    val style = SettingsStore.get(androidx.compose.ui.platform.LocalContext.current).state.value.listStyle
    val coverSize = when (style) {
        ListStyle.COMPACT -> 36.dp
        ListStyle.STANDARD -> 46.dp
        ListStyle.LARGE -> 64.dp
    }
    val vPad = when (style) {
        ListStyle.COMPACT -> 4.dp
        ListStyle.STANDARD -> 10.dp
        ListStyle.LARGE -> 6.dp
    }
    val context = LocalContext.current
    val nameColor = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary
    // 当前行的高亮底用 animate* 淡入，避免播放切歌时整片列表闪一下。
    val highlight by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(NeonTiming.stateMs, easing = FastOutSlowInEasing),
        label = "rowActive",
    )
    Row(
        Modifier.fillMaxWidth()
            .drawBehind {
                if (highlight > 0.01f) {
                    drawRect(accent.copy(alpha = 0.07f * highlight))
                    // 竖条贴左边，宽度固定 2dp，不参与布局所以不会让行内容左右跳。
                    drawRect(
                        color = accent.copy(alpha = 0.9f * highlight),
                        size = androidx.compose.ui.geometry.Size(2.dp.toPx(), size.height * 0.62f),
                        topLeft = androidx.compose.ui.geometry.Offset(0f, size.height * 0.19f),
                    )
                }
            }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = OledTokens.screenMargin, vertical = vPad),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(music.img, coverSize)
        Spacer(Modifier.width(OledTokens.spaceMd))
        Column(Modifier.weight(1f)) {
            Text(music.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge, color = nameColor)
            val subtitle = listOf(music.singer, music.albumName)
                .filter { it.isNotBlank() }.joinToString(" · ")
            if (subtitle.isNotBlank() && style != ListStyle.COMPACT) {
                Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (showDuration && music.interval.isNotBlank()) {
            Spacer(Modifier.width(OledTokens.spaceSm))
            Text(music.interval, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}
