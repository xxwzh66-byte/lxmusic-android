package com.lxmusic.android.ui.neon

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * V6.0 播放进度条：霓虹渐变填充 + 会呼吸的发光拖点。
 *
 * 为什么不用 `Slider`：Material3 的 Slider 在 24dp 高度下会强制留出 48dp 的
 * 触摸区，而播放页把进度条紧贴在播放键上方，多出来的触摸区会抢走播放键的点击。
 * 这里自己实现，触摸区**只有 28dp**，并且对整条轨道做 tap-to-seek（点哪跳哪），
 * 这比拖动小圆点的命中率高得多——单手操作时尤其明显。
 *
 * 手势用 `awaitEachGesture` 手写，而不是叠 `detectTapGestures` + `detectDragGestures`：
 * 两个 gesture detector 挂在同一区域会互相竞争，先按下再横向拖的开场手势会被
 * 其中一个吞掉，表现为"拖不动"或"要拖两次"。手写循环里按下即 seek、按住拖动
 * 连续 seek、抬手结束，三种意图共用一次状态机，没有竞争。
 */
@Composable
fun NeonProgress(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    themeColor: String = "blue",
    /** 播放中才呼吸；暂停时拖点常亮，避免静止画面里有个东西在闪。 */
    playing: Boolean = false,
    animate: Boolean = true,
) {
    val duration = durationMs.coerceAtLeast(1L)
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val breath by rememberBreath(NeonTiming.pulseMs, animate && playing)

    val liveFraction = (positionMs.toFloat() / duration).coerceIn(0f, 1f)
    val shown = dragFraction ?: liveFraction

    val gradient = NeonTokens.brandGradient(themeColor)

    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .semantics {
                    progressBarRangeInfo = ProgressBarRangeInfo(shown, 0f..1f)
                }
                .pointerInput(duration) {
                    val width = size.width.toFloat().coerceAtLeast(1f)
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        dragFraction = (down.position.x / width).coerceIn(0f, 1f)
                        down.consume()
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            dragFraction = (change.position.x / width).coerceIn(0f, 1f)
                            change.consume()
                        }
                        val final = dragFraction
                        if (final != null) onSeek((final * duration).toLong())
                        dragFraction = null
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Canvas(Modifier.fillMaxWidth().height(28.dp)) {
                val railH = 6.dp.toPx()
                val cy = size.height / 2f
                val x = size.width * shown

                // 未播轨道
                drawLine(
                    color = NeonTokens.rail,
                    start = Offset(0f, cy),
                    end = Offset(size.width, cy),
                    strokeWidth = railH,
                    cap = StrokeCap.Round,
                )
                // 已播：品牌渐变 + 一层更宽的半透明外发光
                if (x > 0f) {
                    drawLine(
                        brush = Brush.horizontalGradient(
                            gradient,
                            startX = 0f,
                            endX = size.width.coerceAtLeast(1f),
                        ),
                        start = Offset(0f, cy),
                        end = Offset(x, cy),
                        strokeWidth = railH,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = gradient.last().copy(alpha = 0.22f),
                        start = Offset(0f, cy),
                        end = Offset(x, cy),
                        strokeWidth = railH + 6.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
                // 拖点：三圈叠加伪造辉光（不用 RenderEffect）
                val active = if (dragFraction != null) 1f else 0.55f + 0.45f * breath
                drawCircle(
                    color = Color.White.copy(alpha = 0.10f * active),
                    radius = 13.dp.toPx(),
                    center = Offset(x, cy),
                )
                drawCircle(
                    color = gradient.first().copy(alpha = 0.28f * active),
                    radius = 9.dp.toPx(),
                    center = Offset(x, cy),
                )
                drawCircle(
                    color = Color.White,
                    radius = 5.dp.toPx(),
                    center = Offset(x, cy),
                )
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
            Text(
                formatClock(if (dragFraction != null) (dragFraction!! * duration).toLong() else positionMs),
                color = NeonTokens.textMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            Box(Modifier.weight(1f))
            Text(
                formatClock(durationMs),
                color = NeonTokens.textMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/** mm:ss（超过一小时补上小时位）。 */
fun formatClock(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

/** 供需要"dp 换算成 px"的地方复用，避免每处都写 LocalDensity。 */
@Composable
internal fun dpToPx(dp: Int): Float = with(LocalDensity.current) { dp.dp.toPx() }
