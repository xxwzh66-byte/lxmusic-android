package com.lxmusic.android.ui.neon

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * V6.0 主界面的外壳组件。
 *
 * 播放页是"沉浸舞台"，主界面是"工作台"，两者的设计语法必须不同：播放页可以
 * 整屏极光 + 大发光球，主界面如果照搬，列表正文会被光晕吃掉。所以这里只把
 * **层级**和**活动态**带过来。
 *
 * 一条贯穿全文件的规则：**外壳的文字色一律读 `MaterialTheme.colorScheme`，
 * 不读 [NeonTokens.text]。** 因为设置里有 LIGHT / DARK 三档，列表页正文本身
 * 就是 `colorScheme.onSurface`；外壳读同一个 token，才能保证深色模式下是浅字、
 * 浅色模式下是深字，两边都不会出现"同色压同色"。霓虹 token 只负责玻璃填充、
 * 描边、渐变这些"材质"部分。
 */

// ------------------------------------------------------------------- tabs

/**
 * 胶囊式主标签。
 *
 * 为什么不用 `TabRow`：Material3 的 TabRow 用指示条（下划线）表达选中，而在这个
 * 渐变背景上，1-2dp 的指示条几乎看不见，用户得靠文字颜色猜。胶囊把"选中"
 * 从一条线变成一个形状，即使色觉有障碍也能分辨（形状差异 + 颜色差异双重编码）。
 */
@Composable
fun NeonTab(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    themeColor: String = "blue",
    modifier: Modifier = Modifier,
) {
    val gradient = NeonTokens.brandGradient(themeColor)
    val textColor by animateColorAsState(
        targetValue = if (active) gradient.first() else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(NeonTiming.stateMs, easing = FastOutSlowInEasing),
        label = "tabText",
    )
    // 选中底：深色下是白色低透明玻璃，浅色下是白色高透明实底。
    // 判据用 surface 的亮度而不是读开关——Theme 只暴露了 colorScheme，
    // luminance 是它自带的、永远与当前 scheme 一致的信息。
    val light = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    val activeFill = if (light) Color.White.copy(alpha = 0.82f) else NeonTokens.glassStrong
    Box(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(50))
            .background(if (active) activeFill else Color.Transparent)
            .then(if (active) Modifier.drawTabEdge(gradient) else Modifier)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 13.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 18.dp),
        )
    }
}

/**
 * 选中胶囊的 1dp 渐变描边。
 *
 * 用 `drawBehind` + `Stroke` 自己画，而不是 `Modifier.border`：border 只吃单色，
 * 而这里的描边要在左右两端各露一点霓虹色，这是"霓虹管"的观感来源。
 */
private fun Modifier.drawTabEdge(gradient: List<Color>): Modifier = this.then(
    Modifier.drawBehind {
        val w = 1.dp.toPx()
        drawRoundRect(
            brush = Brush.horizontalGradient(gradient, startX = 0f, endX = size.width),
            cornerRadius = CornerRadius(size.height / 2f),
            style = Stroke(width = w),
            topLeft = Offset(w / 2f, w / 2f),
            size = Size(size.width - w, size.height - w),
        )
    }
)

/** 一行标签容器。标签数量少时左对齐，不为凑满宽度拉伸。 */
@Composable
fun NeonTabRow(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    themeColor: String = "blue",
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.forEachIndexed { i, label ->
            NeonTab(
                label = label,
                active = i == selectedIndex,
                onClick = { onSelect(i) },
                themeColor = themeColor,
            )
        }
    }
}

// ------------------------------------------------------------------ divider

/**
 * 列表分隔线：一条从中段向外渐隐的 1dp 线。
 *
 * 普通 `Divider` 是等宽实线，在深色底上会连成一条硬栅栏；渐隐线让每一行像浮在
 * 背景上，滚动时不会出现"表格"的观感。颜色取 onSurface 的 12%，
 * 这样深浅两套主题都自动成立。
 */
@Composable
fun NeonHairline(modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    Canvas(modifier.fillMaxWidth().height(1.dp).padding(horizontal = 16.dp)) {
        drawRect(
            brush = Brush.horizontalGradient(
                listOf(Color.Transparent, c, c, Color.Transparent)
            ),
        )
    }
}

// --------------------------------------------------------------- status dot

/** 一个活动指示点，用于"正在播放"等状态；[pulse] 时呼吸。 */
@Composable
fun NeonDot(
    color: Color,
    modifier: Modifier = Modifier,
    pulse: Boolean = false,
    size: Dp = 6.dp,
) {
    val breath by rememberBreath(NeonTiming.pulseMs, pulse)
    val alpha = if (pulse) 0.45f + 0.55f * breath else 1f
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha))
    )
}

// ------------------------------------------------------------ section title

/**
 * 分区标题：一条短的霓虹竖条 + 标题 + 计数。
 *
 * 竖条是"章节"的视觉锚点，比加粗文字更能把内容分块；它只有 3dp 宽、不发光，
 * 所以不会抢走"正在播放项"的注意力。
 */
@Composable
fun NeonSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
    themeColor: String = "blue",
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(14.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Brush.verticalGradient(NeonTokens.brandGradient(themeColor)))
        )
        Spacer(Modifier.width(8.dp))
        Text(
            title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        if (count != null) {
            Spacer(Modifier.width(6.dp))
            Text(
                count.toString(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
        }
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

// --------------------------------------------------------------- empty slot

/** 供其它 neon 组件复用：判断当前是否为浅色主题（按 surface 亮度）。 */
@Composable
fun isLightSurface(): Boolean = MaterialTheme.colorScheme.surface.luminance() > 0.5f

/** 一个柔软的空态图标底：径向渐变的彩色雾，代替灰掉的图标。 */
@Composable
fun NeonHalo(color: Color, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .background(
                Brush.radialGradient(
                    listOf(color.copy(alpha = 0.22f), color.copy(alpha = 0.04f), Color.Transparent),
                ),
                CircleShape,
            )
    )
}
