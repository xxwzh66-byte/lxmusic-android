package com.lxmusic.android.ui.neon

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text

/**
 * V6.0 主播放键。
 *
 * 视觉：品牌渐变的球体 + 外圈两三层同色辉光 + 播放时呼吸。
 * 交互：按下有 0.92 的缩放回弹（`NeonTiming.stateMs`），因为这是个 72dp 的大
 * 目标，没有按下反馈会显得"没点到"。
 *
 * 为什么不用 `IconButton`：Material 的 IconButton 在水波纹之外没有 scale 反馈，
 * 而这里的辉光是每帧画的，配合缩放才像"活物"。用 `clickable` + 手写
 * `interactionSource` 可以拿到 pressed 状态，同时保留无障碍语义
 * （`role = Role.Button` + `contentDescription`），这是可访问性的硬要求。
 *
 * @param buffering 缓冲中显示转圈，替代图标（沿用 V5 的行为）
 */
@Composable
fun NeonPlayButton(
    playing: Boolean,
    buffering: Boolean,
    onClick: () -> Unit,
    themeColor: String = "blue",
    size: Dp = 76.dp,
    animate: Boolean = true,
) {
    val breath by rememberBreath(NeonTiming.pulseMs, animate && playing)
    val gradient = NeonTokens.brandGradient(themeColor)
    val glowColor = gradient.first()

    // 呼吸只在播放时参与；暂停时固定 0.35，球体看起来"熄灭但还在"。
    val glowAlpha = if (playing) 0.30f + 0.30f * breath else 0.30f

    val label = if (playing) "暂停" else "播放"
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    Box(
        modifier = Modifier
            .size(size)
            .semantics {
                contentDescription = label
                role = Role.Button
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) {
            val r = this.size.minDimension / 2f
            val c = Offset(this.size.width / 2f, this.size.height / 2f)
            // 三层辉光，由外到内递增，伪造发光
            drawCircle(glowColor.copy(alpha = glowAlpha * 0.10f), r * 1.42f, c)
            drawCircle(glowColor.copy(alpha = glowAlpha * 0.16f), r * 1.22f, c)
            drawCircle(glowColor.copy(alpha = glowAlpha * 0.24f), r * 1.08f, c)
            // 球体：从左上到右下的品牌渐变
            drawCircle(
                brush = Brush.linearGradient(
                    colors = gradient,
                    start = Offset(c.x - r, c.y - r),
                    end = Offset(c.x + r, c.y + r),
                ),
                radius = r * 0.92f,
                center = c,
            )
            // 顶部高光（玻璃球感）
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.34f), Color.Transparent),
                    center = Offset(c.x - r * 0.30f, c.y - r * 0.38f),
                    radius = r * 0.85f,
                ),
                radius = r * 0.92f,
                center = c,
            )
            // 1dp 内描边，压住边缘让球体不发糊
            drawCircle(
                color = Color.White.copy(alpha = 0.26f),
                radius = r * 0.92f,
                center = c,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()),
            )
        }

        // 图标本身随 pressed 微缩，配合整球的 scale 形成"按下"反馈
        IconOrSpinner(playing = playing, buffering = buffering, pressed = pressed, size = size)
    }
}

@Composable
private fun IconOrSpinner(playing: Boolean, buffering: Boolean, pressed: Boolean, size: Dp) {
    val scale = if (pressed) 0.88f else 1f
    val s = size * 0.42f * scale
    if (buffering) {
        CircularProgressIndicator(
            color = Color.White,
            strokeWidth = 2.4.dp,
            modifier = Modifier.size(s),
        )
    } else {
        Icon(
            imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .size(s)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
        )
    }
}

/**
 * 环绕主键的次级控件（上一首/下一首/随机/循环）。
 *
 * 统一走玻璃胶囊，开启态用 [NeonTokens.accent] 描边+着色。图标外的 40dp
 * 触摸区保证满足 Material 的最小可点尺寸（比 44dp 稍小是为了在 5 个控件
 * 并排时仍能放进 360dp 宽的屏幕：40*2 + 76 + 间距）。
 */
@Composable
fun NeonIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    size: Dp = 52.dp,
    iconSize: Dp = 24.dp,
) {
    val tint by animateColorAsState(
        targetValue = if (active) NeonTokens.accent else NeonTokens.textMuted,
        animationSpec = tween(NeonTiming.stateMs, easing = FastOutSlowInEasing),
        label = "neonTint",
    )
    Box(
        modifier = modifier
            .size(size)
            .then(Modifier.neonChip(shape = CircleShape, active = active))
            .clickable { onClick() }
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** 一行小字标签，用于主键下方提示当前循环/随机状态。 */
@Composable
fun NeonStatusLabel(text: String, modifier: Modifier = Modifier, active: Boolean = false) {
    Text(
        text = text,
        modifier = modifier,
        color = if (active) NeonTokens.accent else NeonTokens.textMuted,
        fontSize = 11.sp,
        letterSpacing = 0.6.sp,
    )
}
