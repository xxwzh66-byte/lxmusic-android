package com.lxmusic.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lxmusic.android.FeatureFlags
import com.lxmusic.android.data.model.MusicInfo
import com.lxmusic.android.ui.components.Cover
import com.lxmusic.android.ui.components.HintIconButton
import com.lxmusic.android.ui.components.TrackActionsSheet
import com.lxmusic.android.ui.neon.AuroraBackdrop
import com.lxmusic.android.ui.neon.NeonDot
import com.lxmusic.android.ui.neon.NeonGlassAction
import com.lxmusic.android.ui.neon.NeonSurfaceSpec
import com.lxmusic.android.ui.neon.NeonTabRow
import com.lxmusic.android.ui.neon.NeonTiming
import com.lxmusic.android.ui.neon.NeonTokens
import com.lxmusic.android.ui.neon.neonChip
import com.lxmusic.android.ui.neon.neonGlass
import com.lxmusic.android.ui.neon.rememberBreath
import com.lxmusic.android.ui.neon.rememberMotionEnabled
import com.lxmusic.android.ui.neon.rememberNeonSurface
import com.lxmusic.android.ui.screens.EqualizerScreen
import com.lxmusic.android.ui.screens.LocalScreen
import com.lxmusic.android.ui.screens.LibraryScreen
import com.lxmusic.android.ui.screens.OnlineScreen
import com.lxmusic.android.ui.components.PlayerSkin
import com.lxmusic.android.ui.components.toast
import com.lxmusic.android.ui.screens.NowPlayingScreen
import com.lxmusic.android.ui.screens.QueueScreen
import com.lxmusic.android.ui.screens.SettingsScreen
import com.lxmusic.android.ui.screens.StatsScreen
import com.lxmusic.android.ui.screens.FolderManagerScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LxApp(viewModel: MainViewModel) {
    val tab by viewModel.tab.collectAsState()
    val playerState by viewModel.player.collectAsState()
    val libraryState by viewModel.library.collectAsState()
    // V6: 主题色要在主壳与播放页之间共享，所以提到最外层只收一次。
    // 之前它只在 nowPlaying 分支里 collect，主界面的标签与迷你播放条拿不到。
    val settings by viewModel.settings.collectAsState()
    val themeColor = settings.themeColor
    val context = LocalContext.current

    var showNowPlaying by remember { mutableStateOf(false) }
    // V5.1-1: any playback entry tap opens the full-screen player.
    LaunchedEffect(viewModel) {
        viewModel.openNowPlaying.collect { showNowPlaying = true }
    }
    var showQueue by remember { mutableStateOf(false) }
    var showEqualizer by remember { mutableStateOf(false) }
    var actionTarget by remember { mutableStateOf<MusicInfo?>(null) }
    var actionRemoveContext by remember { mutableStateOf<Long?>(null) } // playlistId if removing
    val settingsOpen by viewModel.showSettings.collectAsState()
    if (settingsOpen) {
        // V6.1.1: every full-screen page is an early-return branch, so Compose
        // has no back-stack of its own and the system Back button used to fall
        // straight through to "close the Activity". One handler per branch is
        // what makes Back mean "up", which is what every Android user expects.
        BackHandler { viewModel.closeSettings() }
        SettingsScreen(viewModel = viewModel, onBack = { viewModel.closeSettings() })
        return
    }
    val folderManagerOpen by viewModel.showFolderManager.collectAsState()
    if (folderManagerOpen) {
        BackHandler { viewModel.closeFolderManager() }
        FolderManagerScreen(viewModel = viewModel, onBack = { viewModel.closeFolderManager() })
        return
    }
    val statsOpen by viewModel.showStats.collectAsState()
    if (statsOpen) {
        BackHandler { viewModel.closeStats() }
        StatsScreen(viewModel = viewModel, onBack = { viewModel.closeStats() })
        return
    }
    if (showQueue) {
        BackHandler { showQueue = false }
        QueueScreen(viewModel = viewModel, onBack = { showQueue = false })
        return
    }
    if (showEqualizer) {
        BackHandler { showEqualizer = false }
        EqualizerScreen(viewModel = viewModel, onBack = { showEqualizer = false })
        return
    }
    if (showNowPlaying && playerState.current != null) {
        BackHandler { showNowPlaying = false }
        NowPlayingScreen(
            state = playerState,
            lyricFontScale = settings.lyricFontScale,
            skin = PlayerSkin.from(settings.playerSkin),
            onSkinSelected = { sk -> viewModel.setPlayerSkin(sk.name) },
            showMeta = settings.showPlayerMeta,
            themeColor = settings.themeColor,
            isFavorite = playerState.current?.id?.let { it in libraryState.favoriteIds } ?: false,
            onBack = { showNowPlaying = false },
            onTogglePlay = viewModel::togglePlayPause,
            onNext = viewModel::next,
            onPrev = viewModel::prev,
            onSeek = viewModel::seekTo,
            onToggleRepeat = { viewModel.cycleRepeat() },
            onToggleShuffle = { viewModel.toggleShuffle() },
            onToggleFavorite = { playerState.current?.let { viewModel.toggleFavorite(it) } },
            onOpenQueue = { showQueue = true },
            onOpenEqualizer = { showEqualizer = true },
        )
        return
    }

    // V6.0: 主界面共用播放页的极光，但压暗——列表正文必须比氛围更重要。
    // 压的是极光峰值而不是再盖一层黑，所以色相还在，只是不再抢戏。
    // 深浅两套材质的数值在 NeonSurface.kt 里，这里只做选择。
    val surface = rememberNeonSurface(
        dark = MaterialTheme.colorScheme.surface.luminance() <= 0.5f,
        themeColor = themeColor,
    )
    Box(Modifier.fillMaxSize()) {
        AuroraBackdrop(
            colors = surface.auroraColors,
            animate = rememberMotionEnabled(),
            dim = surface.dim,
            baseColor = surface.baseColor,
            scrims = false,
        )

        Scaffold(
            // 透明容器：让极光从状态栏一路透到导航栏。
            containerColor = Color.Transparent,
            topBar = {
                NeonHomeTopBar(
                    themeColor = themeColor,
                    onOpenQueue = { showQueue = true },
                    onOpenStats = { viewModel.openStats() },
                    onOpenEqualizer = { showEqualizer = true },
                    onOpenSettings = { viewModel.openSettings() },
                )
            },
            bottomBar = {
                MiniPlayer(
                    state = playerState,
                    themeColor = themeColor,
                    surface = surface,
                    onClick = { showNowPlaying = true },
                    onToggle = viewModel::togglePlayPause,
                    onNext = viewModel::next,
                )
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                val visibleTabs = remember {
                    if (FeatureFlags.ONLINE_ENABLED) HomeTab.entries.toList()
                    else HomeTab.entries.filter { it != HomeTab.ONLINE }
                }
                NeonTabRow(
                    labels = visibleTabs.map { it.label },
                    selectedIndex = visibleTabs.indexOf(tab).coerceAtLeast(0),
                    onSelect = { i -> visibleTabs.getOrNull(i)?.let { viewModel.setTab(it) } },
                    themeColor = themeColor,
                )

                when (tab) {
                    HomeTab.LOCAL -> LocalScreen(
                        viewModel = viewModel,
                        onLongPress = { actionTarget = it; actionRemoveContext = null },
                    )
                    HomeTab.LIBRARY -> LibraryScreen(
                        viewModel = viewModel,
                        onLongPress = { music, playlistId ->
                            actionTarget = music; actionRemoveContext = playlistId
                        },
                    )
                    HomeTab.ONLINE -> if (FeatureFlags.ONLINE_ENABLED) {
                        OnlineScreen(
                            viewModel = viewModel,
                            onLongPress = { actionTarget = it; actionRemoveContext = null },
                        )
                    } else {
                        // Online is compiled in but disabled: never render the engine-backed screen.
                        Box(Modifier.fillMaxSize())
                    }
                }
            }
        }
    }

    actionTarget?.let { music ->
        val isLocal = music.isLocal
        TrackActionsSheet(
            music = music,
            isFavorite = music.id in libraryState.favoriteIds,
            playlists = libraryState.playlists,
            onDismiss = { actionTarget = null; actionRemoveContext = null },
            onToggleFavorite = { viewModel.toggleFavorite(music) },
            onAddToPlaylist = { viewModel.addToPlaylist(it, music) },
            onCreatePlaylist = { name -> viewModel.createPlaylist(name) { id -> viewModel.addToPlaylist(id, music) } },
            onRemoveFromPlaylist = actionRemoveContext?.let { pid -> { viewModel.removeFromPlaylist(pid, music.id) } },
            onRemoveFromLibrary = if (isLocal) ({ viewModel.removeFromLibrary(music) }) else null,
            onDeleteFile = if (isLocal) ({
                viewModel.deleteLocalFile(music) { msg -> toast(context, msg) }
            }) else null,
        )
    }
}

@Composable
private fun NeonHomeTopBar(
    themeColor: String,
    onOpenQueue: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    // 不用 Material TopAppBar：它会强制一个不透明的 containerColor 与固定的
    // 64dp 高度，极光透不过去。这里自己排一行，高度与播放页顶栏对齐（56dp）。
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(56.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 标题左侧一个 3dp 的品牌竖条：它是全站统一的"章节锚点"，
        // 与 NeonSectionHeader 复用同一语言。
        Box(
            Modifier
                .width(3.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Brush.verticalGradient(NeonTokens.brandGradient(themeColor)))
        )
        Spacer(Modifier.width(9.dp))
        Text(
            "极简music",
            // 外壳文字一律读 colorScheme：设置里有 LIGHT/DARK 三档，
            // 硬写亮色会让浅色主题下的标题消失在背景里。
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.4.sp,
        )
        Spacer(Modifier.weight(1f))
        NeonGlassAction(Icons.Default.QueueMusic, "播放队列", "查看并调整播放队列", onOpenQueue)
        NeonGlassAction(Icons.Default.BarChart, "播放统计", "按日、周查看播放次数", onOpenStats)
        NeonGlassAction(Icons.Default.Equalizer, "均衡器", "调节音效与均衡器", onOpenEqualizer)
        NeonGlassAction(Icons.Default.Settings, "设置", "主题、扫描目录与播放选项", onOpenSettings)
    }
}

/**
 * 主界面底部迷你播放条。
 *
 * V6.0 的三个改动，每一个都在回答"为什么之前不够酷"：
 * 1. 从纯色卡片变成玻璃条：卡片在极光背景上会切出一条硬边，玻璃条是从背景里
 *    长出来的（透明度 + 顶部 1dp 描边 + 顶部高光渐变）。
 * 2. 封面后面加一圈随歌变色的氛围光，并且**随播放呼吸**——这是主界面上唯一
 *    "活着"的元素，把"正在播放"这件事从文字变成光。
 * 3. 进度线换成品牌渐变，并在播放时叠一层呼吸光晕（只改 alpha 不改长度，
 *    否则用户会误读进度）。
 */
@Composable
private fun MiniPlayer(
    state: com.lxmusic.android.player.PlayerState,
    themeColor: String,
    surface: com.lxmusic.android.ui.neon.NeonSurfaceSpec,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
) {
    AnimatedVisibility(
        visible = state.current != null,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
    ) {
        val current = state.current ?: return@AnimatedVisibility
        val progress = if (state.durationMs > 0)
            (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
        val breath by rememberBreath(NeonTiming.pulseMs, state.isPlaying)
        val gradient = NeonTokens.brandGradient(themeColor)
        // 径向渐变的 radius 是 px，不是 dp，所以要先过一遍 density。
        val glowRadius = with(LocalDensity.current) { 56.dp.toPx() * 0.72f }

        Column(
            Modifier
                .fillMaxWidth()
                .neonGlass(
                    shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
                    fillTop = surface.glassTop,
                    fillBottom = surface.glassBottom,
                    borderColor = surface.stroke,
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
        ) {
            // 顶部进度线。呼吸只作用在 alpha 上（不是长度），所以不会误读进度。
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(surface.rail)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress)
                        .height(2.dp)
                        .background(Brush.horizontalGradient(gradient))
                )
                if (state.isPlaying && progress > 0f) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress)
                            .height(2.dp)
                            .background(
                                Brush.horizontalGradient(
                                    gradient.map { it.copy(alpha = 0.28f + 0.32f * breath) }
                                )
                            )
                    )
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    // edge-to-edge 之后玻璃条一路铺到屏幕最底，但内容要抬到手势条
                    // / 三键导航之上，否则按钮会被系统导航区盖住。
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 封面 + 氛围光。光晕半径是封面的 ~1.7 倍，画在封面下层。
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .size(56.dp)
                            .background(
                                Brush.radialGradient(
                                    gradient.map { it.copy(alpha = 0.24f + 0.16f * breath) },
                                    radius = glowRadius,
                                ),
                                CircleShape,
                            )
                    )
                    Cover(current.img, 42.dp)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        current.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (state.isPlaying) {
                            NeonDot(NeonTokens.accent, pulse = true, size = 5.dp)
                            Spacer(Modifier.width(5.dp))
                        }
                        Text(
                            current.singer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                        )
                    }
                }
                // 两个按钮保持 HintIconButton：全站"先 toast 再执行"的语义
                // 已经存在于 30+ 处，这里破例会变成不一致。
                HintIconButton(
                    icon = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "播放/暂停",
                    hint = if (state.isPlaying) "暂停播放" else "继续播放",
                    onClick = onToggle,
                    tint = MaterialTheme.colorScheme.onSurface,
                )
                HintIconButton(
                    icon = Icons.Default.SkipNext,
                    contentDescription = "下一首",
                    hint = "播放下一首",
                    onClick = onNext,
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}