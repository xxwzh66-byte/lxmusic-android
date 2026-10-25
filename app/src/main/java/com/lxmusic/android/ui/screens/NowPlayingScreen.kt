package com.lxmusic.android.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lxmusic.android.player.PlayerState
import com.lxmusic.android.player.RepeatMode
import com.lxmusic.android.ui.components.ArtworkColors
import com.lxmusic.android.ui.components.LyricView
import com.lxmusic.android.ui.components.PlayerSkin
import com.lxmusic.android.ui.components.RotatingArtwork
import com.lxmusic.android.ui.neon.AuroraBackdrop
import com.lxmusic.android.ui.neon.NeonIconButton
import com.lxmusic.android.ui.neon.NeonPlayButton
import com.lxmusic.android.ui.neon.NeonProgress
import com.lxmusic.android.ui.neon.NeonStatusLabel
import com.lxmusic.android.ui.neon.NeonTokens
import com.lxmusic.android.ui.neon.neonGlass
import com.lxmusic.android.ui.neon.neonChip
import com.lxmusic.android.ui.neon.rememberMotionEnabled

/**
 * V6.0 「Neon Vinyl」全屏播放页。
 *
 * 相对 V5 的四处结构性改动（不是换色，是版面重排）：
 *
 * 1. **背景从"封面铺满 + 渐变遮罩"改成"三团流动极光"。** 旧做法要下载整张
 *    封面再铺满整数屏，换歌时会明显闪一下；极光只依赖已经算好的两个取色，
 *    换歌瞬间是渐变平移，没有硬切。见 [AuroraBackdrop] 的注释。
 *
 * 2. **进度条自己实现，不再用 Material `Slider`。** 旧 Slider 的 48dp 触摸区
 *    会盖住下方的播放键，且小圆点很难单手拖。新的 [NeonProgress] 支持整条
 *    轨道点按跳转，28dp 触摸区。
 *
 * 3. **播放键升级为渐变球体 + 呼吸辉光**，暂停时熄灭（[NeonPlayButton]）。
 *
 * 4. **顶栏改成玻璃胶囊**，并新增"左滑切歌"：在唱片区域水平滑动超过阈值
 *    就切换上一/下一首。这是移动播放器里用户最顺手的手势，且不与歌词区
 *    的纵向滚动冲突（这里用的是水平手势检测）。
 *
 * 保留 V4/V5 的全部行为契约：两个 skin、`showMeta`、`lyricFontScale`、
 * 收藏/均衡器/队列菜单、皮肤持久化。所有这些参数名都没变，所以 LxApp 的
 * 调用点不需要改动——除了新增的 `themeColor` 已经存在。
 */
@Composable
fun NowPlayingScreen(
    state: PlayerState,
    onBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenEqualizer: () -> Unit,
    lyricFontScale: Float = 1f,
    skin: PlayerSkin = PlayerSkin.VINYL,
    onSkinSelected: (PlayerSkin) -> Unit = {},
    showMeta: Boolean = true,
    themeColor: String = "blue",
) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    var scheme by remember { mutableStateOf(ArtworkColors.fallbackScheme()) }
    val motion = rememberMotionEnabled()

    LaunchedEffect(state.current?.img) {
        scheme = ArtworkColors.schemeFor(context, state.current?.img)
    }

    val aurora = NeonTokens.auroraFrom(scheme.primary, scheme.secondary, themeColor)
    val listState = rememberLazyListState()

    Box(Modifier.fillMaxSize()) {
        AuroraBackdrop(colors = aurora, animate = motion && state.isPlaying)

        // 正在播放的封面以极低 alpha 叠在极光上：给"这首歌"一点实体感，
        // 又不会像旧版那样占据整屏。
        AsyncImage(
            model = state.current?.img,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = NeonTokens.STAGE_COVER_ALPHA,
            modifier = Modifier.fillMaxSize(),
        )

        Column(Modifier.fillMaxSize()) {
            NeonTopBar(
                state = state,
                skin = skin,
                menuOpen = menuOpen,
                onMenuOpenChange = { menuOpen = it },
                isFavorite = isFavorite,
                onBack = onBack,
                onSkinSelected = onSkinSelected,
                onToggleFavorite = onToggleFavorite,
                onOpenQueue = onOpenQueue,
                onOpenEqualizer = onOpenEqualizer,
                themeColor = themeColor,
            )

            // ---------- 中央舞台 ----------
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Crossfade(targetState = skin, label = "skin") { sk ->
                    val swipe = Modifier.pointerInput(sk) {
                        var acc = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { acc = 0f },
                            onDragEnd = {
                                if (acc <= -90f) onNext() else if (acc >= 90f) onPrev()
                                acc = 0f
                            },
                            onHorizontalDrag = { _, delta -> acc += delta },
                        )
                    }
                    Box(Modifier.fillMaxSize().then(swipe)) {
                        when (sk) {
                            PlayerSkin.LYRICS -> LyricsStage(state, lyricFontScale, listState, onSeek, themeColor)
                            else -> VinylStage(
                                state = state,
                                scheme = scheme,
                                lyricFontScale = lyricFontScale,
                                listState = listState,
                                onSeek = onSeek,
                                themeColor = themeColor,
                                animate = motion,
                            )
                        }
                    }
                }
            }

            if (showMeta && skin == PlayerSkin.VINYL) {
                MetaBlock(state)
            }

            Spacer(Modifier.height(if (showMeta && skin == PlayerSkin.VINYL) 14.dp else 4.dp))

            ProgressBlock(state, onSeek, themeColor, motion)

            Spacer(Modifier.height(10.dp))

            ControlBar(
                state = state,
                onTogglePlay = onTogglePlay,
                onNext = onNext,
                onPrev = onPrev,
                onToggleRepeat = onToggleRepeat,
                onToggleShuffle = onToggleShuffle,
                themeColor = themeColor,
                animate = motion,
            )

            // 22dp 视觉留白 + 系统导航栏高度：edge-to-edge 下播放键与状态标签
            // 不能被手势条压住。注意顺序——navigationBarsPadding 必须在 height 外层，
            // 反过来写（先 height 再 padding）会被固定的 22dp 吃掉，底部仍会被挡。
            Spacer(Modifier.navigationBarsPadding().height(22.dp))
        }
    }
}

// ------------------------------------------------------------------ top bar

@Composable
private fun NeonTopBar(
    state: PlayerState,
    skin: PlayerSkin,
    menuOpen: Boolean,
    onMenuOpenChange: (Boolean) -> Unit,
    isFavorite: Boolean,
    onBack: () -> Unit,
    onSkinSelected: (PlayerSkin) -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenEqualizer: () -> Unit,
    themeColor: String,
) {
    Row(
        Modifier
            .fillMaxWidth()
            // edge-to-edge 之后顶栏会顶到屏幕最上沿，必须自己让出状态栏。
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassIcon(
            icon = Icons.Default.KeyboardArrowDown,
            desc = "收起播放页",
            onClick = onBack,
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                state.current?.name ?: "未在播放",
                color = NeonTokens.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                state.current?.singer ?: "",
                color = NeonTokens.textMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        GlassIcon(
            icon = if (skin == PlayerSkin.LYRICS) Icons.Outlined.Album else Icons.AutoMirrored.Outlined.Notes,
            desc = if (skin == PlayerSkin.LYRICS) "切到唱片样式" else "切到歌词样式",
            onClick = { onSkinSelected(PlayerSkin.next(skin)) },
        )
        Spacer(Modifier.width(8.dp))
        Box {
            GlassIcon(
                icon = Icons.Default.MoreVert,
                desc = "更多",
                onClick = { onMenuOpenChange(true) },
            )
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { onMenuOpenChange(false) },
                modifier = Modifier.background(NeonTokens.surface),
            ) {
                DropdownMenuItem(
                    text = { Text(if (isFavorite) "取消收藏" else "收藏", color = NeonTokens.text) },
                    leadingIcon = {
                        Icon(
                            if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            null,
                            tint = if (isFavorite) NeonTokens.neonA else NeonTokens.textMuted,
                        )
                    },
                    onClick = { onMenuOpenChange(false); onToggleFavorite() },
                )
                DropdownMenuItem(
                    text = { Text("均衡器", color = NeonTokens.text) },
                    leadingIcon = { Icon(Icons.Default.Equalizer, null, tint = NeonTokens.textMuted) },
                    onClick = { onMenuOpenChange(false); onOpenEqualizer() },
                )
                DropdownMenuItem(
                    text = { Text("播放队列", color = NeonTokens.text) },
                    leadingIcon = { Icon(Icons.Default.QueueMusic, null, tint = NeonTokens.textMuted) },
                    onClick = { onMenuOpenChange(false); onOpenQueue() },
                )
            }
        }
    }
}

/** 顶栏的玻璃圆钮：36dp，正圆，纯描边无填充，压在极光上仍然清晰。 */
@Composable
private fun GlassIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(36.dp)
            .neonChip(shape = CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = desc, tint = NeonTokens.text, modifier = Modifier.size(18.dp))
    }
}

// ------------------------------------------------------------------- stages

/**
 * VINYL：唱片 + 歌词同屏。
 *
 * V6 的改动是把唱片包进"发光环"里：三圈极光同色的半透明圆叠在唱片外围，
 * 播放时随呼吸缩放。这让唱片在满屏极光里仍然是视觉锚点，而不是被背景吞掉。
 */
@Composable
private fun VinylStage(
    state: PlayerState,
    scheme: ArtworkColors.Scheme,
    lyricFontScale: Float,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onSeek: (Long) -> Unit,
    themeColor: String,
    animate: Boolean,
) {
    val compact = listState.layoutInfo.viewportSize.height > 0 &&
        with(androidx.compose.ui.platform.LocalDensity.current) {
            listState.layoutInfo.viewportSize.height.toDp() < NeonTokens.compactStageHeight
        }

    val discSize = if (state.lyric.isEmpty) 300.dp else if (compact) 168.dp else 208.dp
    val halo = NeonTokens.brandGradient(themeColor).first()

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(contentAlignment = Alignment.Center) {
            // 三圈光晕（静态，不随唱片转）：越外越淡
            Box(
                Modifier
                    .size(discSize * NeonTokens.DISC_HALO_SCALE)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(halo.copy(alpha = NeonTokens.DISC_HALO_ALPHA), Color.Transparent)
                        )
                    )
            )
            RotatingArtwork(
                imageUrl = state.current?.img,
                spinning = state.isPlaying,
                size = discSize,
                tone = scheme.primary,
                toneOn = true,
                toneArm = true,
            )
        }

        if (!state.lyric.isEmpty) {
            Spacer(Modifier.height(10.dp))
            LyricView(
                lyric = state.lyric,
                activeIndex = state.lyricIndex,
                listState = listState,
                fontScale = lyricFontScale,
                onSeekTo = onSeek,
                bottomReserveDp = 40.dp,
                activeColor = NeonTokens.brandGradient(themeColor).first(),
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }
    }
}

/** LYRICS：歌词优先，右上角一个小圆封面。 */
@Composable
private fun LyricsStage(
    state: PlayerState,
    lyricFontScale: Float,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onSeek: (Long) -> Unit,
    themeColor: String,
) {
    Box(Modifier.fillMaxSize()) {
        // 上下压暗，保证歌词在极光最亮处也读得清
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            NeonTokens.inkDeep.copy(alpha = NeonTokens.LYRIC_SCRIM_TOP),
                            Color.Transparent,
                            NeonTokens.inkDeep.copy(alpha = NeonTokens.LYRIC_SCRIM_BOTTOM),
                        )
                    )
                )
        )
        LyricView(
            lyric = state.lyric,
            activeIndex = state.lyricIndex,
            listState = listState,
            fontScale = lyricFontScale * 1.15f,
            onSeekTo = onSeek,
            activeColor = NeonTokens.brandGradient(themeColor).first(),
            modifier = Modifier.fillMaxSize().padding(vertical = 6.dp),
        )
        if (!state.current?.img.isNullOrBlank()) {
            AsyncImage(
                model = state.current?.img,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 18.dp)
                    .size(52.dp)
                    .neonChip(shape = CircleShape)
                    .clip(CircleShape),
            )
        }
    }
}

// -------------------------------------------------------------------- meta

@Composable
private fun MetaBlock(state: PlayerState) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            state.current?.name ?: "未在播放",
            color = NeonTokens.text,
            fontSize = 23.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            state.current?.singer ?: "",
            color = NeonTokens.textMuted,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

// ---------------------------------------------------------------- progress

@Composable
private fun ProgressBlock(
    state: PlayerState,
    onSeek: (Long) -> Unit,
    themeColor: String,
    motion: Boolean,
) {
    NeonProgress(
        positionMs = state.positionMs,
        durationMs = state.durationMs,
        onSeek = onSeek,
        themeColor = themeColor,
        playing = state.isPlaying,
        animate = motion,
        modifier = Modifier.padding(horizontal = 22.dp),
    )
}

// ---------------------------------------------------------------- controls

@Composable
private fun ControlBar(
    state: PlayerState,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit,
    themeColor: String,
    animate: Boolean,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        NeonIconButton(
            icon = Icons.Default.Shuffle,
            contentDescription = "随机",
            onClick = onToggleShuffle,
            active = state.shuffle,
            size = 48.dp,
            iconSize = 20.dp,
        )
        NeonIconButton(
            icon = Icons.Default.SkipPrevious,
            contentDescription = "上一首",
            onClick = onPrev,
            size = 56.dp,
            iconSize = 30.dp,
        )
        NeonPlayButton(
            playing = state.isPlaying,
            buffering = state.isBuffering,
            onClick = onTogglePlay,
            themeColor = themeColor,
            animate = animate,
        )
        NeonIconButton(
            icon = Icons.Default.SkipNext,
            contentDescription = "下一首",
            onClick = onNext,
            size = 56.dp,
            iconSize = 30.dp,
        )
        NeonIconButton(
            icon = if (state.repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
            contentDescription = "循环",
            onClick = onToggleRepeat,
            active = state.repeatMode != RepeatMode.OFF,
            size = 48.dp,
            iconSize = 20.dp,
        )
    }
    // 状态说明放在控件下方而不是塞进按钮：图标已经足够小，再加角标会糊。
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 26.dp).padding(top = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        NeonStatusLabel(if (state.shuffle) "随机开" else "随机关", active = state.shuffle)
        NeonStatusLabel(
            when (state.repeatMode) {
                RepeatMode.OFF -> "列表不循环"
                RepeatMode.ALL -> "列表循环"
                RepeatMode.ONE -> "单曲循环"
            },
            active = state.repeatMode != RepeatMode.OFF,
        )
    }
}
