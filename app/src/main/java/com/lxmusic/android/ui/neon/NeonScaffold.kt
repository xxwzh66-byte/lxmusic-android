package com.lxmusic.android.ui.neon

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lxmusic.android.ui.components.toast

/**
 * 二级页面的统一骨架：极光背景 + 56dp 玻璃顶栏 + 内容槽。
 *
 * 为什么需要它：v6.0 先改造了主页、播放页、播放队列，剩下的设置 / 均衡器 /
 * 扫描文件夹 / 播放统计还挂着 Material `TopAppBar`。那些页面不透明、没有背景，
 * 从主页点进去像**换了一个 App**——视觉系统的完整性不在于每一屏都发光，而在于
 * 每一屏都承认自己是同一个应用。
 *
 * 三条约束被固化在这里，调用方不需要重复判断：
 * 1. 背景永远是 [AuroraBackdrop]，深浅两种主题的颜色来自 [rememberNeonSurface]。
 * 2. 顶栏永远 56dp、永远左侧有一个玻璃返回钮，与主页顶栏同高度，
 *    页面之间切换时标题不会上下跳。
 * 3. 内容区自带 `navigationBarsPadding()`；顶栏自带 `statusBarsPadding()`，
 *    两者不重叠，所以列表滑到底时最后一行的下方不会被系统手势条盖住。
 */
@Composable
fun NeonScreen(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    themeColor: String = "blue",
    subtitle: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val surface = rememberNeonSurface(dark = !isLightSurface(), themeColor = themeColor)
    val motion = rememberMotionEnabled()
    Box(modifier.fillMaxSize()) {
        AuroraBackdrop(
            colors = surface.auroraColors,
            animate = motion,
            dim = surface.dim,
            baseColor = surface.baseColor,
            scrims = false,
        )
        Column(Modifier.fillMaxSize()) {
            NeonBar(
                title = title,
                subtitle = subtitle,
                onBack = onBack,
                themeColor = themeColor,
                actions = actions,
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .navigationBarsPadding(),
            ) {
                content()
            }
        }
    }
}

/** 56dp 玻璃顶栏，与主页 [com.lxmusic.android.ui.LxApp] 的顶栏同构。 */
@Composable
private fun NeonBar(
    title: String,
    subtitle: String?,
    onBack: () -> Unit,
    themeColor: String,
    actions: (@Composable RowScope.() -> Unit)?,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(56.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NeonGlassAction(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "返回",
            hint = "返回上一页",
            onClick = onBack,
        )
        Spacer(Modifier.width(12.dp))
        // 与 NeonSectionHeader / 主页顶栏同一套"章节锚点"：3dp 品牌竖条。
        Box(
            Modifier
                .width(3.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Brush.verticalGradient(NeonTokens.brandGradient(themeColor))),
        )
        Spacer(Modifier.width(9.dp))
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle != null) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                maxLines = 1,
            )
        }
        Spacer(Modifier.weight(1f))
        actions?.invoke(this)
    }
}

/**
 * 顶栏的玻璃圆钮。
 *
 * 38dp 是视觉尺寸，`clickable` 铺满整个 Box 后靠外面的 2dp padding 把触摸
 * 目标撑到 42dp——比 Material 的 48dp 略小，但这里几个按钮之间至少隔 4dp，
 * 误触概率低于把顶栏撑成两行。
 *
 * @param hint 非空时先 `toast(hint)` 再执行——全站 30+ 处 `HintIconButton` 都是
 *   "先说一句再执行" 的语义，圆钮版本必须保持一致。传 null 表示调用方已经自己
 *   说过了（队列页的清空按钮就是先弹确认再执行），不要再叠一层 toast。
 */
@Composable
fun NeonGlassAction(
    icon: ImageVector,
    contentDescription: String,
    hint: String?,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val context = LocalContext.current
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.90f else 1f,
        animationSpec = tween(NeonTiming.stateMs, easing = FastOutSlowInEasing),
        label = "neonGlassActionScale",
    )
    Box(
        Modifier
            .padding(horizontal = 2.dp)
            .size(38.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .neonChip(shape = RoundedCornerShape(50))
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = {
                    if (hint != null) toast(context, hint)
                    onClick()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(19.dp),
        )
    }
}
