package com.lxmusic.android.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import com.lxmusic.android.ui.theme.OledTokens
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lxmusic.android.lyric.Lyric

/**
 * A karaoke-style scrolling lyric view: the active line is highlighted and
 * auto-centred, with a smooth scroll animation.
 */
@Composable
fun LyricView(
    lyric: Lyric,
    activeIndex: Int,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    fontScale: Float = 1f,
    onSeekTo: ((Long) -> Unit)? = null,
    bottomReserveDp: Dp = 0.dp,
    /**
     * V6.0: 当前行的着色。播放页传品牌色让高亮行带"霓虹"感；
     * 默认值保持 V5 的白色，所以其它调用点（如有）行为不变。
     */
    activeColor: Color = OledTokens.onStage,
) {
    if (lyric.isEmpty) {
        Column(
            modifier = modifier.fillMaxWidth(),            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "暂无歌词",
                color = OledTokens.onStageMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        return
    }

    // V5.2-3: padding scales with the viewport so short stages (vinyl
    // co-display) no longer lose half their height to fixed whitespace.
    val density = LocalDensity.current
    val padDp = with(density) {
        (listState.layoutInfo.viewportSize.height * 0.32f).toInt()
            .coerceIn((if (bottomReserveDp > 0.dp) 96.dp else 72.dp).roundToPx(), 140.dp.roundToPx())
            .toDp()
    }

    // Auto-scroll: keep one line above and at least two below the active line.
    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0) {
            val offset = with(density) { (-padDp + 8.dp).roundToPx() }
            listState.animateScrollToItem(index = activeIndex, scrollOffset = offset)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .drawWithContent {
                drawContent()
                if (bottomReserveDp > 0.dp) {
                    // Fade the lowest 24dp into the stage background so the
                    // next line slips out softly instead of being clipped.
                    val fadeFrom = size.height - 24.dp.toPx()
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Transparent, OledTokens.background.copy(alpha = 0.75f)),
                            startY = fadeFrom,
                            endY = size.height,
                        ),
                    )
                }
            },
        // V5.2-3: spacing grows with the active line so an enlarged line never
        // collides with the next one.
        verticalArrangement = Arrangement.spacedBy(LyricMetrics.lineGap),
        contentPadding = PaddingValues(top = padDp, bottom = padDp + bottomReserveDp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        itemsIndexed(lyric.lines) { i, line ->
            val active = i == activeIndex
            val color by animateColorAsState(
                targetValue = if (active) activeColor else OledTokens.onStageMuted,
                animationSpec = tween(250),
                label = "lyricColor",
            )
            val scale by animateFloatAsState(
                targetValue = if (active) 1f else 0f,
                animationSpec = tween(220),
                label = "lyricScale",
            )
            val translation = lyric.translationFor(i)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = line.text,
                    color = color,
                    fontSize = (16f + 4f * scale).sp * fontScale,
                    lineHeight = (22f + 6f * scale).sp * fontScale,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                )
                if (!translation.isNullOrBlank()) {
                    Text(
                        text = translation,
                        color = OledTokens.onStageMuted,
                        fontSize = 13.sp * fontScale,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                    )
                }
            }
        }
    }
}

/**
 * V5.2-3: lyric typography metrics. The gap grows together with the active
 * line (16sp -> 20sp) so the enlarged line never touches the next one.
 */
object LyricMetrics {
    /** Base gap between lyric lines (inactive). */
    val lineGap = 14.dp + 6.dp
}
