package com.lxmusic.android.ui.screens

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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lxmusic.android.ui.MainViewModel
import com.lxmusic.android.ui.components.Cover
import com.lxmusic.android.ui.components.ConfirmSheet
import com.lxmusic.android.ui.components.EmptyState
import com.lxmusic.android.ui.components.HintIconButton
import com.lxmusic.android.ui.components.toast
import com.lxmusic.android.ui.neon.AuroraBackdrop
import com.lxmusic.android.ui.neon.NeonDot
import com.lxmusic.android.ui.neon.NeonHairline
import com.lxmusic.android.ui.neon.NeonGlassAction
import com.lxmusic.android.ui.neon.NeonTiming
import com.lxmusic.android.ui.neon.NeonTokens
import com.lxmusic.android.ui.neon.neonChip
import com.lxmusic.android.ui.neon.neonGlass
import com.lxmusic.android.ui.neon.rememberMotionEnabled
import com.lxmusic.android.ui.neon.rememberNeonSurface

/**
 * 播放队列。
 *
 * V6.0 改了三处，都是"让状态可读"而不是"让它更花"：
 *
 * 1. **顶栏去掉 Material `TopAppBar`**。它会给一整个不透明色块，把页面从极光里
 *    切出来；这里改成与主页顶栏同构的自定义行（56dp + 36dp 玻璃圆钮）。
 * 2. **当前行从"淡底色"变成"发光行"**。原来的 `primary@0.12` 在这一屏里要同时
 *    和"上移/下移/移除"三个按钮抢注意力，太弱；现在当前行有左侧竖条 + 呼吸光点 +
 *    封面外圈光环，一眼能定位。
 * 3. **进页面自动滚到当前曲目**。队列常常几十首，当前曲目往往在屏幕外——
 *    用户打开"播放队列"的第一个诉求就是看"接下来播什么"，落点必须在当前曲目上。
 *
 * 三个按钮（↑ ↓ ✕）保留原样：它们已经带 toast 提示，换成图标后 hint 文案
 * 会丢失语义，不值得为了统一而牺牲可用性。
 */
@Composable
fun QueueScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val player by viewModel.player.collectAsState()
    val queue = player.queue
    val currentIndex = player.queueIndex
    val context = androidx.compose.ui.platform.LocalContext.current
    val themeColor = viewModel.settings.collectAsState().value.themeColor
    val surface = rememberNeonSurface(
        dark = MaterialTheme.colorScheme.surface.luminance() <= 0.5f,
        themeColor = themeColor,
    )
    val motion = rememberMotionEnabled()
    val listState = rememberLazyListState()

    // 只在进入时滚一次：如果跟着 currentIndex 滚，用户手动翻看别处会被拽回来。
    LaunchedEffect(Unit) {
        if (currentIndex >= 0 && currentIndex < queue.size) {
            listState.scrollToItem((currentIndex - 1).coerceAtLeast(0))
        }
    }

    var showClearConfirm by remember { mutableStateOf(false) }
    if (showClearConfirm) {
        ConfirmSheet(
            title = "清空播放队列",
            message = "将移除队列中的全部歌曲。",
            confirmLabel = "清空",
            onConfirm = { viewModel.queueClear() },
            onDismiss = { showClearConfirm = false },
        )
    }

    Box(Modifier.fillMaxSize()) {
        AuroraBackdrop(
            colors = surface.auroraColors,
            animate = motion,
            dim = surface.dim,
            baseColor = surface.baseColor,
            scrims = false,
        )
        Column(Modifier.fillMaxSize()) {
            QueueTopBar(
                themeColor = themeColor,
                count = queue.size,
                onBack = onBack,
                onClear = if (queue.isNotEmpty()) ({ showClearConfirm = true }) else null,
            )
            if (queue.isEmpty()) {
                Box(Modifier.fillMaxSize()) {
                    EmptyState(text = "队列是空的", actionLabel = "去挑选歌曲", onAction = onBack)
                }
            } else {
                LazyColumn(
                    state = listState,
                    // 只给列表让出导航栏：极光仍然铺满整屏，但最后一行不会被手势条压住。
                    modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
                ) {
                    itemsIndexed(queue, key = { i, m -> m.id + "#" + i }) { i, music ->
                        val isCurrent = i == currentIndex
                        QueueRow(
                            index = i,
                            name = music.name,
                            singer = music.singer,
                            img = music.img,
                            isCurrent = isCurrent,
                            playing = player.isPlaying,
                            themeColor = themeColor,
                            motion = motion,
                            onClick = { viewModel.queueJumpTo(i) },
                            onUp = { viewModel.queueMove(i, (i - 1).coerceAtLeast(0)) },
                            onDown = { viewModel.queueMove(i, (i + 1).coerceAtMost(queue.size - 1)) },
                            onRemove = { viewModel.queueRemove(i) },
                        )
                        NeonHairline()
                    }
                }
            }
        }
    }
}

/** 与主页顶栏同构：56dp、左侧品牌竖条、右侧玻璃圆钮。 */
@Composable
private fun QueueTopBar(
    themeColor: String,
    count: Int,
    onBack: () -> Unit,
    onClear: (() -> Unit)?,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(56.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NeonGlassAction(Icons.AutoMirrored.Filled.ArrowBack, "返回", "返回上一页") { onBack() }
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier
                .width(3.dp)
                .height(18.dp)
                .background(
                    Brush.verticalGradient(NeonTokens.brandGradient(themeColor)),
                    RoundedCornerShape(2.dp),
                )
        )
        Spacer(Modifier.width(9.dp))
        Text(
            "播放队列",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            if (count == 0) "" else "$count 首",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
        )
        Spacer(Modifier.weight(1f))
        if (onClear != null) {
            NeonGlassAction(Icons.Default.DeleteSweep, "清空队列", hint = null) {
                toast(context, "清空播放队列")
                onClear()
            }
        }
    }
}

/**
 * 队列里的一行。
 *
 * 当前行的三层标记都只动"光"不动"尺寸"，所以切换曲目时不会有任何布局跳动：
 * 左侧 3dp 竖条（drawBehind，不参与测量）、封面外一圈品牌色光环、名字前面的呼吸点。
 */
@Composable
private fun QueueRow(
    index: Int,
    name: String,
    singer: String,
    img: String?,
    isCurrent: Boolean,
    playing: Boolean,
    themeColor: String,
    motion: Boolean,
    onClick: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onRemove: () -> Unit,
) {
    val gradient = NeonTokens.brandGradient(themeColor)
    val accent = gradient.first()
    val glow by animateFloatAsState(
        targetValue = if (isCurrent) 1f else 0f,
        animationSpec = tween(NeonTiming.stateMs, easing = FastOutSlowInEasing),
        label = "queueRowGlow",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                if (glow > 0.01f) {
                    drawRect(accent.copy(alpha = 0.08f * glow))
                    drawRect(
                        brush = Brush.verticalGradient(
                            listOf(
                                accent.copy(alpha = 0f),
                                accent.copy(alpha = 0.95f * glow),
                                accent.copy(alpha = 0f),
                            ),
                        ),
                        size = Size(3.dp.toPx(), size.height),
                        topLeft = Offset.Zero,
                    )
                }
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 封面 + 光环。光环用 drawBehind 画在封面外，不改变 Row 的高度。
        Box(
            Modifier
                .drawBehind {
                    if (glow > 0.01f) {
                        val r = size.minDimension * 0.5f + 3.dp.toPx()
                        drawCircle(
                            brush = Brush.radialGradient(
                                listOf(accent.copy(alpha = 0.30f * glow), Color.Transparent),
                                center = center,
                                radius = r,
                            ),
                            radius = r,
                            center = center,
                        )
                    }
                }
        ) {
            Cover(img, 42.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isCurrent) {
                    NeonDot(accent, pulse = playing && motion, size = 5.dp)
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isCurrent) accent else MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
            Text(
                singer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
        }
        // 三个操作按钮保持在 40dp 触摸区内，且保留 toast 提示。
        HintIconButton(
            contentDescription = "上移",
            hint = "在队列中上移一位",
            onClick = onUp,
            buttonSize = 38.dp,
        ) {
            Text("↑", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HintIconButton(
            contentDescription = "下移",
            hint = "在队列中下移一位",
            onClick = onDown,
            buttonSize = 38.dp,
        ) {
            Text("↓", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HintIconButton(
            icon = Icons.Default.Close,
            contentDescription = "移除",
            hint = "从队列中移除这首",
            onClick = onRemove,
            buttonSize = 38.dp,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            iconSize = 17.dp,
        )
    }
}
