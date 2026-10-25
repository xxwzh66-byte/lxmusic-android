# 极简music (JiJianMusic) — 项目交接报告

> 交接版本：**versionCode 26 / versionName "6.2.0"**
> 报告生成：2026-10-19（v6.2.0 更新于 2026-10-19）
> 代码规模：`app/src/main/java` 71 个 .kt / 12859 行；`app/src/test/java` 25 个 .kt / 2928 行 / **214 项测试 0 失败**
> 产物：`build/apk/jijian-music-6.2.0-vc26-debug.apk`（23399318 B）

---

## 一、三十秒概览

这是一个 Android 本地音乐播放器，从 [lx-music-desktop](https://github.com/lyswhut/lx-music-desktop)
的技术栈里继承了「在线音源」的能力，但**当前发布形态是纯本地播放器**
（`FeatureFlags.ONLINE_ENABLED = false`，在线栈完整保留但休眠）。

- **语言/UI**：Kotlin + Jetpack Compose（Material 3）
- **视觉系统**：**v6.0.0 "Neon Vinyl"**——Aurora UI 极光背景 + 深色 OLED + Glassmorphism 玻璃，
  双主题（深浅）通用，见第四节「V6.0 视觉系统」
- **v6.1.0 播放语义**：随机播放 = **shuffle cycle**（一条全序播完才重洗，换歌不丢序）；
  上一首 = **播放历史回退**（`PlayHistory`）；静默停顿由 `StallWatchdog` 兜底；
  均衡器曲线持久化在 `AppSettings.eq`。见第九节「v6.1 变更」
- **v6.2.0 一致性修正**：媒体会话的「上一首」也走播放历史；队列槽位按 **media id**
  解析（修 `playIndex(3)` 被报成 0）；`ShuffleOrder` 克隆体共享 generation，分叉不再
  瞒过安装器；暂停中的播放器不再被判 stuck buffer。见第九节「v6.2.0 变更」
- **播放内核**：Media3 ExoPlayer 1.4.1 + `MediaSessionService`
- **持久化**：Room 2.6.1（v4）+ DataStore Preferences
- **构建**：Gradle 8.7 + AGP 8.5.2 + Kotlin 2.0.20，Groovy DSL
- **minSdk 24 / targetSdk 34 / compileSdk 34**
- **包名**：`com.lxmusic.android`（debug 变体加后缀 `.debug`）

---

## 二、环境搭建（从零接手的第一步）

这台机器上环境**已经就绪**，但都不是系统默认的，必须显式指定：

| 组件 | 位置 | 备注 |
|---|---|---|
| JDK 17 | `D:\demo\tools\jdk17\jdk-17.0.12+7` | **系统只有 JDK 25，AGP 8.5 不兼容**，必须用这个 |
| Android SDK | `D:\demo\android-sdk` | 只有 `platforms/android-34` + `build-tools/34.0.0` + `platform-tools` |
| Gradle | wrapper 自动下载 | `gradle/wrapper/gradle-wrapper.properties` 指向**华为云镜像**，官方 CDN 极慢 |

`local.properties` 已写死 `sdk.dir=D:\\demo\\android-sdk`。

### 标准构建命令（PowerShell）

```powershell
cd D:\demo\lxmusic-android
$env:JAVA_HOME="D:\demo\tools\jdk17\jdk-17.0.12+7"
$env:ANDROID_SDK_ROOT="D:\demo\android-sdk"
.\gradlew.bat :app:assembleDebug :app:versionedApk :app:testDebugUnitTest --no-daemon --console=plain
```

- `:app:assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`
- `:app:versionedApk` → `build/apk/jijian-music-<版本>-vc<号>-debug.apk`（**给用户的就是这个**，见第六节）
- `:app:testDebugUnitTest` → 214 项单元测试

### 这台机器上没有的东西（重要）

- **模拟器是本会话新装的**：`D:\demo\android-sdk\emulator` + AVD `neon33f`
  （`system-images;android-33;default;x86_64`，AVD 目录 `D:\demo\android-avd`）。
  无头启动（**必须用 `Start-Process` 游离启动，`run_in_background` 包装会被杀**）：

  ```powershell
  $env:ANDROID_SDK_ROOT="D:\demo\android-sdk"; $env:ANDROID_HOME="D:\demo\android-sdk"; $env:ANDROID_AVD_HOME="D:\demo\android-avd"
  Start-Process -FilePath "D:\demo\android-sdk\emulator\emulator.exe" `
    -ArgumentList "-avd","neon33f","-no-window","-no-audio","-no-boot-anim","-gpu","swiftshader_indirect","-no-snapshot" `
    -RedirectStandardOutput "D:\demo\_emu.out" -RedirectStandardError "D:\demo\_emu.err" -WindowStyle Hidden
  ```

  已知问题：**无头模拟器跑一段时间会自行退出**（`_emu.err` 无报错，`_emu.out` 末行是
  `Boot completed`），每次调试前先 `adb devices` 确认。**不要给模拟器 `setprop log.tag.ExoPlayerImpl`**，
  会把无头实例搞掉线。
- 测试音乐已推到 `/sdcard/Music/NeonBloom/`（6 首 MP3，只有 `01_neon_drift.lrc`）。
- 项目**不是 git 仓库**（无 `.git`）。历史版本靠 `D:\demo\backups\` 里的三个源码 zip 快照。

---

## 三、架构：为什么是「双 WebView」

这是本项目最需要先理解的一件事。

`lx-music-desktop` 是 Electron + Vue3。它的能力分两块：

1. **内置搜索模块**（酷我/酷狗/QQ/网易/咪咕）住在 `src/renderer/utils/musicSdk/*`
2. **自定义源协议**（用户导入的 .js 音源）由 `src/main/modules/userApi/renderer/preload.js` 注入 `window.lx`

关键发现：**自定义源协议只提供 `musicUrl` / `lyric` / `pic`，不提供搜索**。
所以 Android 端要拿到完整能力，需要**两个独立的 JS 运行时**：

| | WebView #1 | WebView #2 |
|---|---|---|
| 类 | `jsengine/LxSearchRuntime.kt` | `jsengine/LxScriptRuntime.kt` |
| 加载 | `search_host.js`（Node shim）+ `lx-search-bundle.js`（esbuild 打包的**原始 LX 搜索模块**） | 注入与桌面版一致的 `window.lx` shim |
| 用途 | 内置源搜索 | 运行用户导入的 LX 自定义源脚本 |
| 资源 | `app/src/main/assets/jsengine/` | 同左 |

两者共用 `jsengine/SharedNativeBridge.kt` 作为原生桥（OkHttp request、md5、
aesEncrypt/aesDecrypt、randomBytes、inflate、deflate），门面是 `source/SourceManager.kt`。

**搜索 bundle 的再生成**：源码在 `D:\demo\lx-builder\`（esbuild 0.21.5，
`lx/` 是 vendored 的 lx-music-desktop `musicSdk` 源码，`shim/` 是 alias 垫片）。
改完跑 `cd D:\demo\lx-builder; node build.mjs`，产物需手工同步到
`app/src/main/assets/jsengine/lx-search-bundle.js`。

**当前状态**：`FeatureFlags.ONLINE_ENABLED = false` 时 `SourceManager` 在启动任何
WebView 之前就拒绝在线调用，**一个 WebView 进程都不会创建**（省 30–80 MB RAM）。
想重新打开在线功能，把这一个常量改成 `true` 即可，无需恢复任何代码。

---

## 四、代码地图（按「我要改 X，该动哪个文件」组织）

所有路径相对 `app/src/main/java/com/lxmusic/android/`。

### 入口与全局

| 文件 | 职责 |
|---|---|
| `LxApp.kt` | `Application`；持有 `db` / `sourceManager` / `settingsStore` 三个单例；**最先安装 `CrashLogger`** |
| `MainActivity.kt` | 唯一的 Activity；Compose 入口；权限请求、耳机拔出广播、MediaStore 删除的 IntentSender 流程 |
| `FeatureFlags.kt` | 在线功能总开关 |
| `CrashLogger.kt` | 未捕获异常落盘到 `filesDir/last_crash.txt`（**排障第一入口**） |
| `ui/CrashReportDialog.kt` | 把上面的日志展示出来供用户复制 |

### 播放（最容易出 bug 的地方）

| 文件 | 职责 |
|---|---|
| `player/PlayerController.kt`（28 KB，最大的文件） | **播放状态机**。队列、索引、随机、循环、进度回写、歌词加载、队列镜像到 ExoPlayer |
| `player/PlaybackService.kt` | `MediaSessionService`；通知渠道 `jijian_playback`、通知 id 1001、`setSessionActivity` 让点通知回到 App |
| `player/PlayerHolder.kt` | 一个 `@Volatile var player: ExoPlayer?`——因为 `MediaController` 拿不到 `audioSessionId`、也设不了 `ShuffleOrder` |
| `player/RandomShuffle.kt` | 纯逻辑：真随机抽样策略 + 带 memo 的状态 |
| `player/RandomShuffleOrder.kt` | 把上面的策略适配成 ExoPlayer 的 `ShuffleOrder` 接口 |
| `player/EqualizerController.kt` | 均衡器（5 段 + BassBoost + Virtualizer + LoudnessEnhancer） |
| `player/QueueLogic.kt` | 队列增删移的纯函数 |
| `player/PlayHistory.kt`（v6.1） | 播放历史栈（容量 200），「上一首」回退的真实来源；`confirmBack()` + `expected` id **集合**匹配（v6.2 从单槽改成集合，因为 `prev()` 落地是异步的），避免「栈 + 回退标记」被漏复位吃掉整栈 |
| `player/QueueIndex.kt`（v6.2） | `queueIndexOf(mediaId, queue, playerIndex)`：先按 **mediaId** 查队列槽位，查不到回退播放器报的 index，再不行返 -1 让调用方忽略回调。修 `playIndex(3)` 在单曲模式被报成 0 |
| `player/StallWatchdog.kt`（v6.1） | 纯 Kotlin 的静默停顿看门狗（无 Android 类型，假时钟可测）：8s 不动 → RETRY，再 8s → REBUFFER，缓冲态单独 20s |

### 数据

| 文件 | 职责 |
|---|---|
| `data/db/AppDatabase.kt` | Room **v4**。表：`song_list` / `playlist` / `favorite` / `play_history` / `play_event` / `user_api` / `local_track` |
| `data/model/MusicInfo.kt` | 核心模型 `MusicInfo` / `QualityInfo` / `SongList` / `ListItem` |
| `data/repo/MusicRepository.kt` | 所有 DAO 的门面；`logPlay` / `playRecords` 是统计功能的数据源 |
| `data/settings/SettingsStore.kt` | DataStore；`AppSettings` 是**唯一设置真相**，26 个 KEY 常量（含 v5.3.6 的 `record_stats`、v6.1 的 6 个 `eq_*`） |
| `data/settings/EqProfile.kt`（v6.1） | 用户音调曲线的可持久化快照：enabled/preset/bands/bass/virtualizer/loudness；`encodeBands`/`decodeBands` 坏数据丢弃不抛 |
| `data/stats/PlayStats.kt` | 播放统计纯逻辑：日/周/全部三档、直方图分桶、排行 |

### 本地音乐

`local/LocalMusicScanner.kt`（MediaStore 扫描）、`local/SafMusicScanner.kt`（SAF 目录扫描，
含 `DocumentFile.fromSingleUri(uri).parentFile.listFiles()` 找同名 .lrc）、
`local/LocalLibrary.kt`（分组+搜索+排序）、`local/LocalFileDeleter.kt`、`local/LocalTrackMapper.kt`

### 歌词

`lyric/Lyric.kt`（`LrcParser` + `Lyric.indexAt` 二分查找）、`lyric/LyricLoader.kt`、
`lyric/EmbeddedLyrics.kt`（ID3v2 USLT/ULT/TXXX、FLAC VorbisComment、Ogg Vorbis/Opus、MP4 `©lyr`）、
`lyric/LyricEncoding.kt`（严格 UTF-8 → 严格 GBK → ISO-8859-1）

### UI

| 文件 | 职责 |
|---|---|
| `ui/LxApp.kt` | 主脚手架；**注意：类名与 `LxApp`(Application) 重名但包不同**；每个全屏页面用 early-return 切换 |
| `ui/MainViewModel.kt`（30 KB） | 唯一的 ViewModel，所有 UI 状态与动作 |
| `ui/screens/NowPlayingScreen.kt` | 播放页（v6.0 重写为霓虹舞台）；`PlayerSkin.VINYL`（唱片）/ `LYRICS`（全屏歌词）两种皮肤 |
| `ui/screens/StatsScreen.kt` | 播放统计页（v6.0 玻璃卡片 + 渐变直方图） |
| `ui/screens/QueueScreen.kt` | 播放队列（v6.0 重写：自定义顶栏、当前行发光、进页自动定位） |
| `ui/components/RotatingArtwork.kt` | 黑胶唱片（累积角度动画、沟槽、唱臂） |
| `ui/components/LyricView.kt` | 卡拉OK 滚动歌词；`activeColor` 参数让高亮行带霓虹色 |
| `ui/components/Common.kt` / `CommonComponents.kt` | `TrackRow`（含 `active` 当前播放标记）/ `ConfirmSheet` 等共享组件 |
| `ui/theme/OledTokens.kt` | **所有间距/圆角/颜色都从这里取**，不要写字面量 |
| `ui/theme/ColorPalettes.kt` | 8 套配色（blue/purple/green/orange/pink/red/cyan/indigo） |
| `ui/neon/`（v6.0 新增，9 个文件） | 霓虹设计系统，见下方「V6.0 视觉系统」 |

### V6.0 视觉系统（"Neon Vinyl"）

风格来自 `ui-ux-pro-max` 技能：**Aurora UI**（music platform 首选，流动渐变 8-12s，
`cost:low`）+ **Dark Mode (OLED)** + **Glassmorphism**（rgba 0.06-0.13 填充 + 0.14 描边）。

| 文件 | 内容 |
|---|---|
| `ui/neon/NeonTokens.kt` | 色板 + 动效时长。`ink/inkDeep/inkSoft/surface` 底色；`neonA/neonB/neonC/accent` 霓虹；`glass/glassStrong/glassHi/stroke/rail` 玻璃；`brandGradient(themeColor)`、`auroraFrom(primary, secondary, themeColor)`、`glow/lighten/darken` |
| `ui/neon/NeonMotion.kt` | `rememberMotionEnabled()`（读系统"移除动画"开关）、`rememberLoopPhase(ms)`、`rememberBreath(ms)`——**都返回 `State<Float>` 而不是 `Float`**，让 Canvas 只重跑 draw 不重组 |
| `ui/neon/AuroraBackdrop.kt` | 三团 radialGradient 极光走椭圆轨道（11s/17s/23s 互质，避免同步）；`dim` 压暗、`baseColor` 兜底、`scrims` 上下压黑 |
| `ui/neon/GlassSurface.kt` | `Modifier.neonGlass(shape, fillTop, fillBottom, borderColor, borderWidth)`、`NeonPanel`、`Modifier.neonChip(shape, active, accent)` |
| `ui/neon/NeonProgress.kt` | 自实现进度条（不用 Material `Slider`：48dp 触摸区会盖住下方播放键）。`awaitEachGesture` 手写按下/拖动/抬手 |
| `ui/neon/NeonControls.kt` | `NeonPlayButton`（三层辉光渐变球）、`NeonIconButton`、`NeonStatusLabel` |
| `ui/neon/NeonShell.kt` | `NeonTab`/`NeonTabRow`（胶囊选中，形状+颜色双重编码）、`NeonHairline`（渐隐分隔线）、`NeonDot`、`NeonSectionHeader`、`NeonHalo` |
| `ui/neon/NeonSurface.kt` | **双主题材质规格** `NeonSurfaceSpec` + `rememberNeonSurface(dark, themeColor)`。深色=极光+亮色玻璃；浅色=极光压到 ~7%+白色磨砂玻璃 |
| `ui/neon/NeonScaffold.kt` | **二级页面骨架** `NeonScreen(title, onBack, actions) { }`：极光背景 + 56dp 玻璃顶栏 + `navigationBarsPadding()` 内容槽；`NeonGlassAction` 38dp 玻璃圆钮。设置/均衡器/扫描文件夹/播放统计四屏共用 |

**四条不可违背的约束**（违反会崩或掉对比度）：

1. **禁止 `RenderEffect` 模糊。** V5.5.2 崩溃排查已把 API31+ 全屏 RenderEffect 从播放页删除
   （`NowPlayingScreen.kt` 有历史注释）。玻璃必须靠「半透明白 + 1dp 描边 + 顶部高光」画出来。
2. **文字色走 `OledTokens`（播放页）/ `MaterialTheme.colorScheme`（主界面外壳与列表）。**
   `OledTokensTest` 锁了 4.5:1；霓虹 token 只负责渐变、描边、活动态，不承担正文。
   外壳硬写亮色会让浅色主题下的文字消失。
3. **霓虹只上「正在发生的事」**：进度、正在播放的封面/行、随机/循环开启态、当前歌词行。
   静态装饰一律中性玻璃——整屏发光等于没有重点。
4. **极光是双主题的**（`NeonSurface.kt`）。浅色主题下铺深色极光会让 `onSurface`（近黑）
   正文压深底，直接不可读。

---

## 五、随机播放：一条踩了八轮的路径（务必先读）

这是本项目历史最曲折的部分，从 v5.3 到 v6.1 迭代了九个版本。
**当前语义（V6.1，`RandomShuffle.kt` 的 KDoc 是权威定义）**：

- **洗牌周期（shuffle cycle）**：队列被 Fisher-Yates 洗成一条排列，`getNextIndex` /
  `getPreviousIndex` / `getFirstIndex` / `getLastIndex` 全部从这条排列上取，
  四个问题的答案**必然自洽**。
- **一条 cycle 播完才重洗**：cycle 内每首恰好一次。`onMediaItemTransition` **不再**
  `invalidate()`（旧代码每次换歌丢掉整条序，是「随机听起来不随机」的根因）。
- **周期交界处锚定当前曲**：cycle 走到底时 `RandomShuffle.cycleFrom(size, anchor)`
  以刚播完的歌为第 0 位重洗，所以既不会「一轮最后一首 = 下一轮第一首」，也不漏歌。
- **不是「独立均匀抽样」**：v5.3.1–v5.3.5 曾经是每次问都独立抽样，这正是
  v5.3.6 修掉的 bug 来源（见下面第 4 条）。任何文档若还说「允许重复、没有排列」，
  那是过期描述。
- **上一首 = 播放历史回退**：`prev()` 走 `PlayHistory`（容量 200），不是「再抽一次随机」，
  也不是「cycle 的前一位」——后者是一首本轮还没播过的歌，听起来像往前跳。
  历史为空时重播当前曲。
- **周期起点问 prev 返回 `-1`**：ExoPlayer 把它读作「重播当前曲」。
- **按钮与系统走同一条 cycle**：App 按钮调 `nextIndex`（不是 `redrawNext`），
  通知栏/锁屏/蓝牙/自动续播走已安装的 `RandomShuffleOrder` → 同一个
  `RandomShuffleState`。两条路径共用一个状态，不是各抽各的。

**决策理由**见 [docs/adr/0001](docs/adr/0001-shuffle-cycle-and-play-history.md)
（为什么是 cycle 而不是纯随机 / 为什么 prev 走历史）。

五条已修复的根因，每条都有回归测试：

1. **memo 越界重放**（v5.3.4）— `forward`/`backward` 只按源位置建键、不记 size。
   队列变短后旧答案被重放，返回越界索引。而 ExoPlayer 把
   `ShuffleOrder.getNextIndex()` 的返回值当**子索引直接用，全程无范围校验** →
   直接损坏 timeline。修法：读 memo 后验证 `memo in 0 until size`，失效则重抽。
   → `RandomShuffleStateStalenessTest`（6 项）

2. **缺 `synchronized`**（v5.3.4）— KDoc 声称线程安全，代码里一个 `@Synchronized` 都没有。
   state 同时被 ExoPlayer 播放线程与主线程写，裸 `HashMap` 的 `put` 撞 resize
   可让桶链表成环 → 主线程遍历永不终止 = **ANR（无崩溃、无日志、就是卡死）**。
   这也解释了为什么 v5.5.2 装的 CrashLogger 一直抓不到东西。
   → `RandomShuffleStateTest`（16 项）

3. **克隆协议**（v5.3.5）— `MediaSourceList.setShuffleOrder` 为了修长度不匹配会构造
   **候选** order（`cloneAndClear().cloneAndInsert(0, holderCount)`），而该候选仍可能被
   `ExoPlayerImpl` 的长度断言拒绝 → **被克隆的 order 会继续服务会话**。
   原三个 clone 都调 `state.invalidate()`（全局擦除），构造候选的副作用抹掉了
   在役 order 的 memo。修法：新增 `RandomShuffleState.copy()` 快照。
   → `RandomShuffleCloneTest`（7 项）

4. **per-position 独立抽样**（v5.3.6）— `ShuffleOrder` 在 media3 契约里是一条
   **全序排列**，而旧实现对每个源位置各抽一次。于是 `getFirstIndex()` 拿到的锚点
   和从它出发走 `getNextIndex` 得到的序列无关：`hasNextWindow` 说「没有下一首」，
   `getNextWindowIndex` 却给出索引；`MediaPeriodQueue` 重建下一项时拿到 -1。
   应用侧表现就是**通知栏/锁屏/车机的「下一首」按了没反应**，而 App 内按钮（绕过
   ExoPlayer 直接改队列）却是好的——这个不对称是定位这条根因的关键线索。
   修法：整条队列洗成一条排列，四个问题共用它。
   → `RandomShuffleOrderTest`（11 项）+ `ShuffleStallProbe`（8 项）

5. **`MediaControllerImplBase.seekToNextMediaItem()` 的双动作**（v5.3.6）— 它先让
   session 侧的 ExoPlayer 按 `shuffleOrder` 前进，再从 controller 镜像的 `PlayerInfo`
   算一次索引 seek 过去。两次结果不一致时，第二次把播放头**拉回当前曲目**。
   `PlayerController` 的 App 内按钮绕过了这条路径，但系统通知绕不过。
   这与第 4 条同源：只有一条自洽的全序才能让两次计算给出同一个答案。

6. **每次换歌都丢掉整条排列**（v6.0 及以前）— `onMediaItemTransition` 里
   `if (changed) shuffleState.invalidate()`，加上按钮各调 `redrawNext`，
   两条路径都在做「在其余 n−1 首里均匀抽样」。20 首队列前 10 首出现重复的概率
   约 89%。修法：换歌不再 invalidate（cycle 跨曲存活）、按钮改走 `nextIndex`。
   → `RandomShuffleStateTest.theCycleSurvivesATrackChange`（断言 `cycles == 1`）、
   `ShuffleStallProbe.oneCycleNeverRepeatsATrack`

7. **上一首在随机模式下「往前跳」**（v6.0 及以前）— `prev()` 抽的是 cycle 上一位，
   那是一首本轮还没播过的歌。修法：`PlayHistory` 记录真实播放顺序，`prev()` 弹栈。
   用「弹栈后记 `expected` id」代替「历史 + 回退中布尔标记」，因为后者要在每条
   换歌路径复位，漏一处就把栈吃掉。
   → `ShuffleStallProbe.appPrevGoesBackToTheTrackActuallyHeard`、
   `appPrevAtTheStartRestartsWithoutStalling`

8. **切模式卡顿**（v6.0 及以前）— `installShuffleOrder()` 每次都装一份新 order；
   `ExoPlayerImpl.setShuffleOrder` 内部 `createMaskingTimeline()` 造新
   `PlaylistTimeline`（**无 equals 覆盖**）→ `updatePlaybackInfo` 判 timeline 变了 →
   派发 `onTimelineChanged` → app 又装一次。一次开关 = 一串 playlist rebuild，
   跨 binder 放大成主线程停顿。修法：记住已装 order 的 `RandomShuffleState.version`
   与 playlist 长度，没变就 early return；整表重建前 `forgetInstalledOrder()`。

**改这块之前请务必先读这七个测试文件**，它们编码了所有踩过的坑。

---

## 六、发布流程（含一个非显然的坑）

### 版本号

`app/build.gradle` 顶部是**单一真相来源**，`defaultConfig` 与 `versionedApk` 都读它：

```groovy
ext.appVersionCode = 26
ext.appVersionName = "6.2.0"
```

改版本只改这两行，**不要**去动 `defaultConfig`。

### 为什么需要 `versionedApk` 任务

QQ（及多数 IM 文件传输）**按「文件名 + 大小」去重**。此前每个版本都叫
`app-debug.apk`，且相邻版本字节数相同（都是 23182253 B），
所以发新包给用户，QQ 认为「这个文件我已经有了」，**不认是新安装包**。

`versionedApk` 把产物复制成 `jijian-music-6.2.0-vc26-debug.apk`——文件名唯一，问题消失。

### 签名

debug 签名，证书在 `C:\Users\32978\.android\debug.keystore`，
SHA-256 `74df8499788f972c17bd82167eff8d45fa9c7d327177ea0cc11a1fa0e2cddaed`。
apksigner：v2 scheme = true、v1 = false、v3 = false，单 signer。
**正式发布需要换成 release keystore 并打开 `minifyEnabled`。**

---

## 七、排障手册

### 用户报「闪退」/「卡死」

本机无设备时**必须靠用户回传日志**。日志已自动落盘在两处：

- `context.filesDir/last_crash.txt`（App 私有目录）
- `context.getExternalFilesDir(null)/last_crash.txt`（adb 可拉）

用户在 App 内可通过 `CrashReportDialog` 查看并复制。

### 历史教训（值得重复的部分）

- **v5.5.1 假修复**：`LyricView` 底部渐隐 `Brush.verticalGradient` 只给了 1 个
  colorStop → 绘制期抛 `IllegalArgumentException: colors must have length of at least 2
  if colorStops is omitted.`。这个确实是个真 bug（且只在唱片皮肤 + 有歌词时触发），
  但**它不是用户报的那个闪退的根因**——修完用户说「没有效果」。
  教训：**能定位到一个真 bug ≠ 找到了用户报的那个 bug**。
- **探针假红**：`ShuffleConcurrencyProbeTest` 第一版用
  `while (!stop.get())` + `reader.join(6000)`，但 `stop.set()` 写在 `join` 之后 →
  线程永不退出，误判成「HashMap 竞争已复现」。**撤回，不可当根因**。
  教训：**建反馈回路时先验证回路本身能红能绿**。
- **测试断言错，不是代码错**：`everyDrawStaysInRangeAcrossManySizes` 首版断言
  size 1 时 `getNextIndex` 必须在 `0 until 1`，但 `size 1 → -1` 是既有契约。
  **改断言，不改代码**。

---

## 八、工程约定（违反会踩坑）

### 写文件：UTF-8 无 BOM，中文建议走转义

**这是本项目最高频的坑。** PowerShell 5.1 的 `Set-Content -Encoding UTF8` **会写 BOM**，
导致 Gradle/Kotlin 解析失败；用 here-string 直接写含中文的 .kt 会产生 GBK 乱码。

**正确做法**（终止行必须顶格，前后不能有空格）：

```powershell
# 终止行必须顶格、前后无空格；标记可以自定义
$py = @'
import io
src = '''...极简music...'''
io.open(r"path/to/file.kt", "w", encoding="utf-8", newline="\n").write(src)
'@
[System.IO.File]::WriteAllText("$PWD/_x.py", $py, (New-Object System.Text.UTF8Encoding($false)))
python -X utf8 _x.py
```

> 坑：here-string 的终止符只要在正文里**顶格**出现就会提前截断脚本。
> 用 @' / '@ 当标记时，文档里凡是要展示这个符号的地方都会踩到（本报告第一次生成就因此失败）。

改已有文件用「读 → `if s.count(a) != 1: sys.exit(1)` 校验唯一 → `replace(a, b, 1)` → 写回」。

> **更省事的做法（v6.0.0 起统一用它）：直接用文件写工具（`write` / `edit`）。**
> 它自己按 UTF-8 无 BOM 写，中文原样输进去就行，不需要 here-string 那一圈转义，
> 也就没有终止符截断的坑。上面那段 PowerShell 手法只在必须用脚本批量改文件时才有意义。
>
> 读回核实中文时同理：`Get-Content` 在 PowerShell 5.1 下按 ANSI 解码，会把
> 「当前行」显示成「褰撳墠琛岋」——**不是文件坏了，是终端解码错了**。用 `read` 工具，
> 或 `python -X utf8 -c "import io;print(io.open(p,encoding='utf-8').read())"`。

### 其他

- Groovy DSL 不是 Kotlin DSL：`minifyEnabled false`（**不是** `isMinifyEnabled =`）、
  `excludes.add(...)`（**不是** `+=`）
- Room 用 kapt 时 `id("org.jetbrains.kotlin.kapt")` **不带版本号**（避免 classpath 冲突）
- 测试统计**不要用 PowerShell 的 `[xml]`** 解析测试 XML（会崩），用 Python 正则读
  `app/build/test-results/testDebugUnitTest/*.xml`
- `RepeatMode` 是 `com.lxmusic.android.player` 包的**顶层** enum，不是嵌套类；
  `PlayerController.RepeatMode.ALL` 会报 Unresolved reference
- `MediaController` **没有** `audioSessionId`，要从 `PlayerHolder.player` 取
- `Equalizer.getPresetBandLevel(preset, band)` **不存在**；应 `usePreset(preset)` 后读 `getBandLevel(band)`
- `BlurredEdgeTreatment` 在 `androidx.compose.ui.draw`，**不在** `.graphics`
- `Lyric` **没有** `isNotEmpty`，只有 `val isEmpty: Boolean`；判断写 `if (!lyric.isEmpty)`
- `Modifier.padding(horizontal=, top=)` **不存在**这个重载；要链式
  `.padding(horizontal = 26.dp).padding(top = 2.dp)`
- `androidx.compose.foundation.interaction.collectIsPressedAsState()` 才有，别自己造名字
- `Modifier.matchParentSize()` 只能在 `BoxScope` 里调；`Modifier.drawBehind` 里想画满尺寸
  用 `size`（`DrawScope` 的），不要试图在外面调 `matchParentSize`
- 列表分隔线**不要**再用 `androidx.compose.material3.Divider`（已废弃）——本项目统一
  用 `NeonHairline()`（v6.0）

---

## 九、已知问题与技术债（接手后建议优先处理）

### v6.2.0 变更（本轮已落地：一轮 code-review Standards + Spec 双轴 + 缺陷探测）

**无新功能**，全部是「app 与自己意见不一致」的收口。逐条见 README 的 v6.2.0 章节。

| 缺陷 | 根因 | 修法 | 回归 |
|---|---|---|---|
| 通知栏/锁屏「上一首」仍往前跳 | app 按钮走 `PlayHistory`，但媒体会话问的是 `RandomShuffleOrder.getPreviousIndex`，它答「本轮 cycle 的前一位」= 本轮还没播过的歌（正是 ADR 0001 判为错误的行为） | `RandomShuffleOrder` 加可选 `previousOf: ((Int) -> Int)?`；`PlayerController.installShuffleOrder()` 传入 `history.confirmBack()` 映射，栈空返回 `-1`（media3 读作重播当前曲） | `RandomShuffleOrderTest`（14）、`playHistoryHookAnswersPrevious` |
| 连按两次「上一首」后历史错乱 | `PlayHistory.expected` 是**单槽**，而 `prev()` 落地是异步的（`playAt` → `scope.launch { playIndex }`），第二次按会覆盖第一次的认领 | `expected` 改 `LinkedHashSet<String>` | `PlayHistoryTest`（9） |
| 在线队列播第 4 首却显示第 1 首 | `onMediaItemTransition` 把播放器报的 index 直接当队列槽位；单曲模式（`playIndex`，用于恢复与在线曲）ExoPlayer 永远报 0 | 新增 `player/QueueIndex.kt`：先按 **mediaId** 查槽位，查不到回退 reported index，再不行返 -1 让回调被忽略 | `QueueIndexTest`（5） |
| 克隆体重洗周期后安装器看不见分叉 | media3 每次 playlist mutation 都 clone `ShuffleOrder`；克隆体重画自己的 cycle 时原实例 `generation` 不动 → `installShuffleOrder` 的 version guard 判定「没变」，而播放器已在走另一条排列 | `generation: Int` → 共享对象 `ShuffleGeneration`（克隆体按引用持有，任一拷贝 bump 全体可见） | `RandomShuffleCloneTest`（7） |
| 队列走一半被换掉 | `firstIndex`/`lastIndex` 用**无锚**洗牌，而 media3 在建 period queue / seekToFirst 时会问这两个 | 记住 `lastKnown` 作为锚，无有效锚时才退化为无锚洗牌 | `RandomShuffleStateTest`（17） |
| 暂停中的歌被当成 stuck buffer 强行播回来 | 进度循环把 `playing = c.isPlaying` 喂给 watchdog，但 buffering 分支**不看** playing；暂停 + 缓冲 20s 后升级 REBUFFER，两个处理器最后都 `play()` | 进度循环先查 `playWhenReady`，为 false 时 `watchdog.reset()` 并跳过观察 | `StallWatchdogTest`（8） |
| 在线队列上按下一首没反应 | `next()`/`prev()` 先 `ensureMirrorPlaylist()`，失败就 `return`（任何含不可同步解析项的队列都会失败） | 加 `mirror: Boolean` 参数：app 按钮传 `false` 走单曲 `playIndex`，媒体会话传 `true` 拿 playlist；镜像失败改为记日志 | `ShuffleStallProbe`（8） |
| 「重置」一键抹掉已持久化的曲线且不可撤销 | 顶栏 `TextButton("重置")` 直接 `eqReset()` | 改走 `ConfirmSheet` 二次确认 | 模拟器手动 |
| 设置键拼错永远不会被发现 | 写入侧与读取侧共用 `KEY_*`，写错既不编译失败也不抛异常，只是永远读回默认值——正是「均衡器不保存」的形状 | `SettingsStore` 暴露 `readSettings(Preferences)` / `writeEq(MutablePreferences, EqProfile)`，新增 `SettingsRoundTripTest` 覆盖每个 EQ 字段 + 枚举回退 + 上次播放指针 | `SettingsRoundTripTest`（9） |

**同时清掉的技术债**：`source == "local"` 8 处字符串判等 → `MusicInfo.isLocal` / `SOURCE_LOCAL`；
本地路径 → `Uri` 三份重复 → `localFileUri()`；`PlayerController` 陈旧 KDoc（仍在描述 V6.1 之前的
「每次独立均匀抽样」）重写为 cycle 契约；三份 38dp 玻璃圆钮 → 一份 `NeonGlassAction`（`hint` 改可空）；
`NeonMotion` 两个无限动画共用同一段 builder；NowPlaying 的魔数（1.34/0.16/0.10/0.30/0.42/420.dp）进 `NeonTokens`。

### v6.1.1 变更（code review 后补）

| 缺陷 | 根因 | 修法 | 回归 |
|---|---|---|---|
| 按返回键直接退出 app | 六个全屏页都是 `if (x) { Screen(); return }` 的 early return，Compose 没有回退栈，全项目未注册任何 `BackHandler` | 每个分支加 `BackHandler { 与顶栏 X 同一个回调 }` | 模拟器实测（`_shots/back_probe2.ps1`）：均衡器/设置 → Back → 主界面 |
| 均衡器曲线只活到下一次换歌 | `attach()` 会重建效果器并清零，它确实重放 `restoreProfile`；但 `restoreProfile` 只在启动时由 `setProfile()` 写过一次 → 换 session 后恢复的是**开机时**那份曲线，用户刚调的全丢 | 七个 mutator（`setEnabled`/`setBandLevel`/`applyPreset`/`setBassStrength`/`setVirtualizerStrength`/`setLoudnessGainMb`/`reset`）末尾都调 `rememberCurrent()` 把实时状态写回 `restoreProfile` | 代码核实 + `applyProfile` 顺序断言（enabled 最后） |
| 回退落空后播放历史停止增长 | `confirmBack()` 弹栈后若实际播到别的曲，旧代码清 `expected` 且**不记录**这次转换 | 落空分支也记录实际看到的那次转换 | `PlayHistoryTest.aTransitionThatMissesTheExpectedTargetIsStillRecorded` |
| 整表重建后 shuffle order 装错位置 | `moveInQueue` 在 `moveMediaItem` 之前就装序 | 先移动再 `forgetInstalledOrder()` + 重装 | `RandomShuffleCloneTest` / 模拟器 |

### v6.1.0 变更

对应三条用户报障，施工单见 `docs/v6.1-refactor-plan.md`。

| 报障 | 根因 | 修法 | 回归 |
|---|---|---|---|
| 随机播放「不随机」 | `onMediaItemTransition` 每次换歌 `shuffleState.invalidate()` + 按钮 `redrawNext`，两条路径都在「其余 n−1 首均匀抽样」 | 换歌不再 invalidate；按钮改走 `nextIndex`；周期末 `cycleFrom` 锚定当前曲重洗 | `RandomShuffleStateTest`（17）、`ShuffleStallProbe`（8） |
| 「上一首」往前跳 | `prev()` 抽 cycle 上一位 = 本轮还没播过的歌 | 新增 `PlayHistory`（200 条），`prev()` 弹栈回退真实历史 | `ShuffleStallProbe` 两项 |
| 切模式卡顿 | `installShuffleOrder()` 每次重装 → `createMaskingTimeline()` 造新 `PlaylistTimeline`（无 `equals`）→ `onTimelineChanged` → 再装一次 | 记住 `RandomShuffleState.version` + playlist 长度，没变 early return；整表重建前 `forgetInstalledOrder()` | 模拟器手动比对 |
| 播到 1:50 卡住 | 位置冻结但 `isPlaying` 仍 true，不抛 `onPlayerError`，app 无任何路径响应 | 新增 `StallWatchdog`（纯 Kotlin，300ms tick 喂）：8s 不动 → RETRY，再 8s → REBUFFER；缓冲态单独 20s | `StallWatchdogTest`（7） |
| 均衡器不保存 | `attach()` 每个新 audio session 都 release+重建并把 band 清零 | `AppSettings.eq` + 6 个 `KEY_EQ_*`；`EqualizerController.setProfile/snapshot/applyProfile`，`attach()` 后自动灌回 | `EqProfileTest`（7）、`SettingsTest` 默认值 |

**注意**：`StallWatchdog` 是**兜底**，不是根因修复——「1:50 卡住」的真实触发条件
在模拟器上**未能复现**（长播 240s 探针 + CPU 采样都没抓到）。若用户再报，先要
`logcat` 与卡住时的曲目/格式（本地 MP3? 在线流?）。

### 高优先

0. ~~README 有多处过期陈述~~ **（2026-10-19 接手时已修完，本条关闭）**
   已核对并修正：删掉 `data/Changelog.kt` 与 `AnnouncementScreen` 的引用、Room
   v3→v4、`ShuffleBag`/`BagShuffleOrder` 相关 20 处（早期版本章节标注为
   「历史章节」并说明其语义在 v5.3.1 已被替换）、`Project layout` 重写为实测
   目录树、`Building` 补上 `versionedApk` 与 QQ 去重坑、测试表更新为实测表。
   README 顶部加了「历史章节」阅读提示。

   **v6.0.0 又清了一轮**：本报告与 README 的版本号、测试数（169/20 类）、
   APK 体积与文件名、`ui/neon/` 目录、`(current)` 标记（v6.0.0 之前所有章节的
   `(current)` 都已去掉，只有最前面的 v6.0.0 是当前版本）、以及
   **§5 的随机播放语义**——原文还写着「独立均匀抽样、允许重复」，那是 v5.3.5 的
   描述，v5.3.6 已改成全序排列。

1. **文档与代码冲突时改文档**，不要反向迁就旧描述。

2. **无 release 签名配置**，`minifyEnabled false`，无 ProGuard 规则 → 无法正式发布。

3. **`ui/MainViewModel.kt` 已 30 KB**，是所有 UI 状态的唯一容器，继续增长会难以维护。

### 中优先

4. `SettingsStore.AppSettings` 里 ~~5 个设置项没有消费者~~ **（已核实：只剩 0 个，本条关闭）**。
   接手时实测：`listStyle` / `autoScanOnStart` / `pauseOnHeadsetUnplug` / `quality`
   **都有消费者**（ respectively `ui/components/Common.kt:74`、`ui/MainViewModel.kt:284`、
   `MainActivity.kt:42`、`player/PlayerController.kt:505`），`recordHistory` 也已被
   v5.3.6 修正（见第 7 条）。原始陈述把 `quality` 描述成「仅被转发」也不准确——
   `PlayerController.preferredQualityId` 已在 `pickQuality()` 里真实生效。

5. `data/stats/PlayStats.kt` 的 `inRange` 是 public 但生产路径未使用（只被测试调用）——
   疑似 Speculative Generality。
   建议：删掉，或让 `MainViewModel.refreshStats()` 用它在客户端再过滤一次
   （当前 `buckets()` 对越界记录靠 `if (index in counts.indices)` 静默丢弃，
   `dayBuckets` 靠 `for (i in starts.indices.reversed())` 兜底，两条路径都容错但无测试
   断言「窗口外的记录一定不影响任何一列」）。
6. `ALL` 档的 `windowStart` 返回 `0L`（全历史查询），但直方图只画 30 天，语义不一致。
7. ~~**播放统计被 `recordHistory` 开关绑架**~~ **（v5.3.6 已修，本条关闭）**：
   `MainViewModel.onTrackStarted` 曾把 `repo.markPlayed(music)` 与
   `repo.logPlay(music)` 整块包在 `if (recordHistory)` 内，关掉「记录播放历史」
   会连播放统计一起停掉。
   修法：新增独立偏好 `record_stats`（`recordStats`，默认 `true`）与
   `SettingsStore.recordPolicy()` 返回值类型，MainViewModel 按 `history` / `stats`
   分别判定；设置页新增「记录播放统计」开关（副标题说明与最近播放无关）。
   回归：`app/src/test/java/com/lxmusic/android/data/stats/RecordPolicyTest.kt`（3 项）。
8. `play_event` 表有 365 天保留期（`MusicRepository.RETENTION_MS`），
   写入时顺带 `prune`，但**没有清理 UI 提示**。

### 低优先

9. ~~`ui/screens/StatsScreen.kt` 用了已废弃的 `androidx.compose.material3.Divider`~~
   **（v6.0.0 已清，本条关闭）**：`Divider` 早已不再被调用（v6.0 换成了 `NeonHairline`），
   但 import 还留着；顺手把散落在文件末尾的 3 个 `runtime` import 归位、`itemsIndexed` 的
   未用 `i` 参数改成 `_`。
10. ~~`PlayerController.next()` 的 KDoc 有两个连续块~~ **（v6.1.0 已清，本条关闭）**：
    `next()` 的 KDoc 现在紧跟 `suspend fun next()`，`ensureShuffleableQueue()` 也有自己的
    KDoc，不再悬空。
11. ~~`PlayerController.onShuffleOrderRejected` 全项目**零赋值**~~ **（v6.2.0 已清，本条关闭）**：
    钩子在 app 与测试里都没有订阅者，拒绝路径本来就只落在 logcat，钩子只是让代码看起来
    有人在听。删除该字段，两条拒绝路径（deferred / setShuffleOrder 抛异常）改为
    `Log.w` / `Log.e` 带 `TAG`。
12. `examples/example-source.js` 全项目零引用（README 只提概念、不给路径）。
13. `RandomShuffleState` 的 memo 无上界清理，长时间播放会缓慢增长
    （有 `invalidate()` 兜底，风险低）。

---

## 十、测试

**214 项 / 25 个测试类 / 0 失败**（v6.2.0 实测）。全部是 JVM 单元测试（`app/src/test/java/`），
**不需要设备**。

> 历史：接手时 144 项 / 17 类；v5.3.6 起 161 项 / 19 类（`ShuffleStallProbe` 5 项）；
> v6.0.0 起 169 项 / 20 类（新增 `NeonTokensTest` 8 项）；
> v6.1.0 起 187 项 / 22 类，v6.1.1 起 196 项 / 23 类，v6.2.0 起 214 项 / 25 类（新增 `StallWatchdogTest` 7、`EqProfileTest` 7、`PlayHistoryTest` 9、`QueueIndexTest` 5、`SettingsRoundTripTest` 9，
> `ShuffleStallProbe` 5→8，`RandomShuffleStateTest` 16→17）。

| 测试类 | 项数 | 覆盖 |
|---|---|---|
| `EmbeddedLyricsTest` | 18 | ID3v2/FLAC/Ogg/MP4 内嵌歌词解析 |
| `PlayStatsTest` | 17 | 日/周分桶、排行、时区（固定 `Asia/Shanghai`） |
| `RandomShuffleStateTest` | 17 | **v6.1**：洗牌周期契约——一条 cycle 覆盖全队列、换歌不重洗（`cycles == 1`）、周期起点问 prev 返回 -1 |
| `LocalLibraryTest` | 15 | 分组、搜索、排序 |
| `QueueLogicTest` | 12 | 队列增删移纯逻辑 |
| `RandomShuffleOrderTest` | 14 | `ShuffleOrder` 契约（v6.1 周期语义）+ **v6.2** `previousOf` 历史钩子 |
| `PlayHistoryTest` | 9 | **v6.1.1**：有界历史栈、连续重复抑制、回退落空仍记录这次转换 |
| `LrcParserTest` | 8 | LRC 时间戳解析 |
| `ShuffleStallProbe` | 8 | **v6.1**：app 走 500 步 + 系统走 500 步都不卡、一条 cycle 不重复、prev 回到真正听过的歌 |
| `NeonTokensTest` | 8 | **v6.0.0**：极光峰值 4.5:1 红线、品牌渐变三色可辨、动效节律 |
| `ThemePaletteTest` | 8 | 8 套配色的 light/dark scheme |
| `RandomShuffleCloneTest` | 7 | 克隆协议 |
| `EqProfileTest` | 7 | **v6.1**：频段编解码往返、坏数据丢弃不抛 |
| `StallWatchdogTest` | 8 | **v6.1**：静默停顿检测——重试预算、二次重缓冲、仅缓冲态停顿、进度一动就清零（假时钟）；**v6.2** 缓冲不消耗冻结预算 |
| `OledTokensTest` | 6 | 设计 token 一致性 + 4.5:1 对比度 |
| `RandomShuffleStateStalenessTest` | 6 | 队列缩容后 memo 不越界 |
| `LocalLibraryMergeTest` | 6 | 本地库合并 |
| `RandomShuffleTest` | 5 | 抽样分布均匀性（统计断言） |
| `SafMusicScannerTest` | 5 | SAF 扫描 |
| `SettingsTest` | 5 | 设置默认值与枚举取值 |
| `SettingsRoundTripTest` | 9 | **v6.2**：写入侧与读取侧用同一批 `KEY_*` 往返——每个 EQ 字段 + 枚举回落 + 上次播放指针，堵住「均衡器不保存」那类默认值掩盖的缺陷 |
| `QueueIndexTest` | 5 | **v6.2**：队列槽位只有在该槽位的 **id** 就是正在播的那首时才算数（在线队列 `playIndex(3)` 被报成 0 的缺陷） |
| `LocalTrackMapperTest` | 4 | 曲目映射 |
| `PlayerSkinTest` | 4 | 皮肤解析与未知值回落 |
| `RecordPolicyTest` | 3 | v5.3.6：`recordHistory` 与 `recordStats` 互相独立 |

**跑单个测试类**：

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.lxmusic.android.player.RandomShuffleCloneTest" --no-daemon --console=plain
```

**数测试项不要用 PowerShell 的 `[xml]`**（大文件会慢到超时，且格式化属性会把计数搞错）。
用正则扫 `app/build/test-results/testDebugUnitTest/*.xml` 的 `<testcase ` 即可，
项目根的 `_cnt.py` 就是干这个的（`_per_class.py` 输出每个类的项数，
`_probe_stall.ps1` 是「位置冻住」的长播探针，需要模拟器在线）：

```powershell
& python _cnt.py     # files=25 tests=214 failures=0 errors=0
```

---

## 十一、历史版本与备份

`D:\demo\backups\` 有三个源码 zip 快照：

| 文件 | 内容 |
|---|---|
| `lxmusic-android-v5.3.0.1-20261012.zip` | 1769162 B，含旧 `ShuffleBag.kt` |
| `lxmusic-android-v5.3.0.1-20261012b.zip` | 1772415 B，中间态 |
| `lxmusic-android-v5.5.2-20261010-2111.zip` | 1764814 B，内部路径带 `lxmusic-android/` 前缀 |

**注意**：三个 zip 里都只有旧的 `ShuffleBag.kt` / `ShuffleBagTest.kt`，
**没有 `RandomShuffle` 的历史版本**。要对比历史只能解 zip。

项目根**没有** `UPDATE_PLAN.md`（早期版本的控制台报告里提过这个文件名，实际从未入库）。
版本历史的权威记录是 `README.md` 的版本章节：当前版本在最前面的 `v6.2.0` 一节，
往下是时间线留档（早于 v6.2.0 的章节只用于追溯，不代表当前行为）。

v6.1 的设计文档在 `docs/`：`GLOSSARY.md`（队列/镜像队列/洗牌周期/播放历史/静默停顿）、
`docs/adr/0001-shuffle-cycle-and-play-history.md`、`docs/adr/0002-detect-silent-stalls.md`、
`docs/v6.1-refactor-plan.md`（施工单 + 验收记录）。

---

## 十二、接手检查清单

- [ ] 确认 `D:\demo\tools\jdk17\jdk-17.0.12+7` 与 `D:\demo\android-sdk` 存在
- [ ] 跑一次标准构建命令，确认 `BUILD SUCCESSFUL` + 214 测试 0 失败
- [ ] 读 `FeatureFlags.kt`（理解在线/离线边界）
- [ ] 读 `player/RandomShuffle.kt` 的 KDoc + 六个 `RandomShuffle*` 测试类 + `ShuffleStallProbe` + `StallWatchdogTest`
- [ ] 读 `ui/theme/OledTokens.kt` 与 `ui/neon/NeonTokens.kt`（改 UI 前必读）
- [ ] 读本报告第九节，决定先还哪笔技术债
- [ ] 若要真机调试：`adb install -r build/apk/jijian-music-6.2.0-vc26-debug.apk`

---

## 十三、常用命令速查

```powershell
# 构建 + 出包 + 测试
cd D:\demo\lxmusic-android
$env:JAVA_HOME="D:\demo\tools\jdk17\jdk-17.0.12+7"; $env:ANDROID_SDK_ROOT="D:\demo\android-sdk"
.\gradlew.bat :app:assembleDebug :app:versionedApk :app:testDebugUnitTest --no-daemon --console=plain

# 看 APK 版本
D:\demo\android-sdk\build-tools\34.0.0\aapt2.exe dump badging build\apk\jijian-music-6.2.0-vc26-debug.apk | Select-String versionCode

# 验签
D:\demo\android-sdk\build-tools\34.0.0\apksigner.bat verify --print-certs build\apk\jijian-music-6.2.0-vc26-debug.apk

# 重新生成搜索 bundle
cd D:\demo\lx-builder; node build.mjs

# 设备（本机已装无头模拟器；无真机）
D:\demo\android-sdk\platform-tools\adb.exe devices -l
D:\demo\android-sdk\platform-tools\adb.exe install -r <apk>
D:\demo\android-sdk\platform-tools\adb.exe shell run-as com.lxmusic.android.debug cat files/last_crash.txt
```

---

*本报告由当前代码库实况生成，所有文件体积、行号、测试数均为实测值。*
