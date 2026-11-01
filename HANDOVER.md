# 极简music (JiJianMusic) — 项目交接报告

> 交接版本：**versionCode 4 / versionName "1.0.3"**
> 报告版本：**V1.0.3**（按代码实况更新；V1.0 之前的逐版本历史已从本报告移除）
> 代码规模：`app/src/main/java` 73 个 .kt / 13407 行；`app/src/test/java` 26 个 .kt / 3223 行 / **235 项测试 0 失败**
> 产物：`build/apk/jijian-music-1.0.3-vc4-debug.apk`（23432086 B）

---

## 一、三十秒概览

这是一个 Android 本地音乐播放器，从 [lx-music-desktop](https://github.com/lyswhut/lx-music-desktop)
的技术栈里继承了「在线音源」的能力，但**当前发布形态是纯本地播放器**
（`FeatureFlags.ONLINE_ENABLED = false`，在线栈完整保留但休眠）。

- **语言/UI**：Kotlin + Jetpack Compose（Material 3）
- **视觉系统**：**"Neon Vinyl"**——Aurora UI 极光背景 + 深色 OLED + Glassmorphism 玻璃，
  双主题（深浅）通用，见第四节「Neon Vinyl 视觉系统」
- **V1.0.1 播放语义（当前）**：随机播放 = **每次换歌抽一次签**（在除当前曲外的所有
  槽位上均匀抽，结果缓存到下一首真正开始为止）；「上一首」用**同一规则**，额外避开
  刚播完的那首，所以双向都是随机的、且连按两次不会在两首之间弹跳。
  **没有 cycle、没有播放历史栈**——`PlayHistory.kt` 与 `advanceCycle` 都已删除。
  静默停顿由 `StallWatchdog` 兜底；均衡器曲线持久化在 `AppSettings.eq`。
  见第五、九节
- **V1.0.1 耳机按键修复**：media3 的 `seekToPrevious()` 有两条路会变成「重播当前曲」——
  位置超过 `getMaxSeekToPreviousPosition()`（默认 3s），或 `getPreviousIndex()` 返回
  `-1`。V1.0 只关了前者；V1.0.1 让抽签**永不返回 -1**，把后者也堵掉。
- **V1.0.1 两个 seam**：`player/ShuffleSink.kt`（装/卸 order）与
  `player/QueueMirror.kt`（镜像 playlist 的五步收在一处），替代 `PlayerController`
  里八处复制粘贴。见第九节「V1.0.1 变更」
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
| JDK 21 | `C:\Program Files (x86)\Android\openjdk\jdk-21.0.8` | **AGP 8.5 不兼容 JDK 25**（系统默认只有 25），必须显式指定这个。旧文档里的 `D:\demo\tools\jdk17\...` **已不存在** |
| Android SDK | `D:\demo\android-sdk` | 只有 `platforms/android-34` + `build-tools/34.0.0` + `platform-tools` |
| Gradle | wrapper 自动下载 | `gradle/wrapper/gradle-wrapper.properties` 指向**华为云镜像**，官方 CDN 极慢 |

`local.properties` 已写死 `sdk.dir=D:\\demo\\android-sdk`。

### 标准构建命令（PowerShell）

```powershell
cd D:\demo\lxmusic-android
$env:JAVA_HOME="C:\Program Files (x86)\Android\openjdk\jdk-21.0.8"
$env:ANDROID_SDK_ROOT="D:\demo\android-sdk"
.\gradlew.bat :app:assembleDebug :app:versionedApk :app:testDebugUnitTest --no-daemon --console=plain
```

- `:app:assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`
- `:app:versionedApk` → `build/apk/jijian-music-<版本>-vc<号>-debug.apk`（**给用户的就是这个**，见第六节）
- `:app:testDebugUnitTest` → 235 项单元测试

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
- 项目**已是 git 仓库**，远端 `https://github.com/xxwzh66-byte/lxmusic-android`（私有）。
  V1.0 起的历史可用 `git log` / `git show` 追溯；`D:\demo\backups\` 里的三个
  v5.3–v5.5 源码 zip 是 V1.0 之前唯一的留档。

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
| `player/ShuffleSink.kt`（V1.0.1） | 装/卸 shuffle order 的 seam：`install(): Boolean` / `dropInstalled()`。生产实现桥到 `PlayerHolder` 的 ExoPlayer，测试实现是内存 sink |
| `player/QueueMirror.kt`（V1.0.1） | 镜像 playlist 的唯一起点：`replace` / `ensure` / `insert` / `move` / `remove` / `clear` / `dropToSingleItem`，五步顺序固定（drop → flag → select → setMediaItems → resync）。取代 `playlistMode` 字段与八处复制粘贴 |
| `player/QueueIndex.kt` | `queueIndexOf(mediaId, queue, playerIndex)`：先按 **mediaId** 查队列槽位，查不到回退播放器报的 index，再不行返 -1 让调用方忽略回调。修 `playIndex(3)` 在单曲模式被报成 0 |
| `player/StallWatchdog.kt` | 纯 Kotlin 的静默停顿看门狗（无 Android 类型，假时钟可测）：8s 不动 → RETRY，再 8s → REBUFFER，缓冲态单独 20s |

### 数据

| 文件 | 职责 |
|---|---|
| `data/db/AppDatabase.kt` | Room **v4**。表：`song_list` / `playlist` / `favorite` / `play_history` / `play_event` / `user_api` / `local_track` |
| `data/model/MusicInfo.kt` | 核心模型 `MusicInfo` / `QualityInfo` / `SongList` / `ListItem` |
| `data/repo/MusicRepository.kt` | 所有 DAO 的门面；`logPlay` / `playRecords` 是统计功能的数据源 |
| `data/settings/SettingsStore.kt` | DataStore；`AppSettings` 是**唯一设置真相**，28 个 KEY 常量（含 `record_stats`、6 个 `eq_*`、V1.0.2 的 `update_token` 与 V1.0.3 的 `last_update_check_at`） |
| `data/settings/EqProfile.kt` | 用户音调曲线的可持久化快照：enabled/preset/bands/bass/virtualizer/loudness；`encodeBands`/`decodeBands` 坏数据丢弃不抛 |
| `data/stats/PlayStats.kt` | 播放统计纯逻辑：日/周/全部三档、直方图分桶、排行 |

### 本地音乐

`local/LocalMusicScanner.kt`（MediaStore 扫描）、`local/SafMusicScanner.kt`（SAF 目录扫描，
含 `DocumentFile.fromSingleUri(uri).parentFile.listFiles()` 找同名 .lrc）、
`local/LocalLibrary.kt`（分组+搜索+排序）、`local/LocalFileDeleter.kt`、`local/LocalTrackMapper.kt`

### 歌词

`lyric/Lyric.kt`（`LrcParser` + `Lyric.indexAt` 二分查找）、`lyric/LyricLoader.kt`、
`lyric/EmbeddedLyrics.kt`（ID3v2 USLT/ULT/TXXX、FLAC VorbisComment、Ogg Vorbis/Opus、MP4 `©lyr`）、
`lyric/LyricEncoding.kt`（严格 UTF-8 → 严格 GBK → ISO-8859-1）

### 网络

| 文件 | 职责 |
|---|---|
| `net/HttpBridge.kt` | OkHttp 封装：`execute(url, opts)` 返回 `HttpResult(statusCode/statusMessage/headersJson/raw)`，Gson 序列化 header，`followRedirects(true)`，默认 15s 超时 |
| `net/UpdateChecker.kt`（V1.0.2） | 一次 HTTP 拉 `repos/{repo}/releases/latest`，解析成 `UpdateInfo(tagName/title/notes/url/apkName/apkSize)`；`compareVersions`/`isNewer` 处理 `v` 前缀、数字分段、预发布后缀排序。**私有仓匿名会 404**，错误信息引导用户在设置里填访问令牌 |

### UI

| 文件 | 职责 |
|---|---|
| `ui/LxApp.kt` | 主脚手架；**注意：类名与 `LxApp`(Application) 重名但包不同**；每个全屏页面用 early-return 切换 |
| `ui/MainViewModel.kt`（30 KB） | 唯一的 ViewModel，所有 UI 状态与动作 |
| `ui/screens/NowPlayingScreen.kt` | 播放页（霓虹舞台）；`PlayerSkin.VINYL`（唱片）/ `LYRICS`（全屏歌词）两种皮肤 |
| `ui/screens/StatsScreen.kt` | 播放统计页（玻璃卡片 + 渐变直方图） |
| `ui/screens/QueueScreen.kt` | 播放队列（自定义顶栏、当前行发光、进页自动定位） |
| `ui/components/RotatingArtwork.kt` | 黑胶唱片（累积角度动画、沟槽、唱臂） |
| `ui/components/LyricView.kt` | 卡拉OK 滚动歌词；`activeColor` 参数让高亮行带霓虹色 |
| `ui/components/Common.kt` / `CommonComponents.kt` | `TrackRow`（含 `active` 当前播放标记）/ `ConfirmSheet` 等共享组件 |
| `ui/theme/OledTokens.kt` | **所有间距/圆角/颜色都从这里取**，不要写字面量 |
| `ui/theme/ColorPalettes.kt` | 8 套配色（blue/purple/green/orange/pink/red/cyan/indigo） |
| `ui/neon/`（9 个文件） | 霓虹设计系统，见下方「Neon Vinyl 视觉系统」 |

### Neon Vinyl 视觉系统

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

1. **禁止 `RenderEffect` 模糊。** 早期一次崩溃排查已把 API31+ 全屏 RenderEffect 从播放页删除
   （`NowPlayingScreen.kt` 有历史注释）。玻璃必须靠「半透明白 + 1dp 描边 + 顶部高光」画出来。
2. **文字色走 `OledTokens`（播放页）/ `MaterialTheme.colorScheme`（主界面外壳与列表）。**
   `OledTokensTest` 锁了 4.5:1；霓虹 token 只负责渐变、描边、活动态，不承担正文。
   外壳硬写亮色会让浅色主题下的文字消失。
3. **霓虹只上「正在发生的事」**：进度、正在播放的封面/行、随机/循环开启态、当前歌词行。
   静态装饰一律中性玻璃——整屏发光等于没有重点。
4. **极光是双主题的**（`NeonSurface.kt`）。浅色主题下铺深色极光会让 `onSurface`（近黑）
   正文压深底，直接不可读。

---

## 五、随机播放：语义、与 media3 的合同、踩过的坑（务必先读）

这是本项目历史最曲折的部分，从 v5.3 到 v1.0.1 迭代了十一个版本。
**当前语义（V1.0.1，`RandomShuffle.kt` 的 KDoc 是权威定义）**：

- **每次换歌抽一次（draw）**：`nextIndex` 在「除当前曲以外的所有槽位」上均匀抽一个，
  结果记在 `nextPick` 里，**在下一首歌真正开始之前反复问都给同一个答案**。
  这条「记住」是为了不重蹈两个坑：v5.3.6 之前是每次查询独立抽样，media3 一次决策里
  会问好几遍，答案互不相同 → 通知栏与按钮打架、播放头被来回拉（卡死）；
  v6.1 的 cycle 又矫枉过正，顺序被定死。
- **两个方向都是抽**：`previousIndex` 与 `nextIndex` 同规则，唯一区别是它额外避开
  **刚播完的那首**（`lastTrack`），所以连按两次「上一首」不会在两首之间弹跳。
  它**不是**「播放历史回退」，也不是「cycle 的前一位」。
- **`PlayHistory` 已删除**（V1.0.1）。以前 `prev()` 弹栈，历史为空时返回 -1，
  media3 把 -1 读成「没有上一首」→ `seekToCurrentItem(0)` = 当前曲从头重播，
  这就是「耳机第一次按上一首会重放本曲」。现在抽签永不返回 -1，那条分支进不去。
- **没有 cycle 了**：`cycleFrom` / `advanceCycle` / `cyclesDrawn` / `generation`
  全部删除。`generation` 当初只为「发现序被重画」而存在，抽签语义下没有序可画。
- **`firstIndex` / `lastIndex` 不是随机结果**：`firstIndex` 恒为 0、`lastIndex` 恒为
  `size-1`。media3 在建 `MediaPeriodQueue` 和 `repeatMode == ALL` 回绕时问它们，
  答案必须稳定（问两遍不同会让它重排缓冲）。「下一首」不走这两个函数。
- **按钮与系统走同一份 state**：App 按钮调 `nextIndex` / `previousIndex`，
  通知栏/锁屏/蓝牙走已安装的 `RandomShuffleOrder` → 同一个 `RandomShuffleState`。

**决策理由**见 [docs/adr/0001](docs/adr/0001-shuffle-cycle-and-play-history.md)。
注意：该 ADR 写于 V6.1，描述的是「一条 cycle 播完才重洗 + prev 走历史」那套已经被
V1.0.1 推翻的语义。它保留下来是因为下面表格里那些根因的推导过程仍有价值，**结论部分过期**。

V1.0.1 之前踩过的坑（**结论全部已被抽签语义取代，留作教训**——同一个错误形状还会再来）：

| # | 坑 | 教训（与具体算法无关的那部分） |
|---|---|---|
| 1 | memo 只按源位置建键、不记 size（v5.3.4） | ExoPlayer 把 `getNextIndex()` 的返回值当子索引**直接用，全程无范围校验**。任何缓存都必须带尺寸校验，越界值会直接损坏 timeline。 |
| 2 | KDoc 声称线程安全，代码里零 `@Synchronized`（v5.3.4） | 裸 `HashMap` 并发 `put` 撞 resize 可让桶链表成环 → 主线程遍历永不终止 = **ANR（无崩溃、无日志、就是卡死）**。当年 CrashLogger 抓不到东西就是这个原因。 |
| 3 | 三个 clone 都调 `invalidate()`（v5.3.5） | `MediaSourceList` 会构造**候选** order 试探长度，候选的副作用抹掉了在役 order 的状态。克隆必须走快照（`copy()`），不能共享可变状态。 |
| 4 | 对每个源位置各抽一次（v5.3.6） | `ShuffleOrder` 在 media3 契约里是一条**全序**；四个查询必须互相自洽，否则 `hasNextWindow` 说没有、`getNextWindowIndex` 又给出索引。 |
| 5 | `MediaControllerImplBase.seekToNextMediaItem()` 是**双动作**（v5.3.6） | 它先让 session 侧前进、再从 controller 镜像的 `PlayerInfo` 算一次 seek。两次答案不一致就把播放头拉回来。**只要一条自洽的序才能让两边同答**——这是当年「通知栏按下一首没反应、App 内按钮正常」这个不对称的根因。 |
| 6 | 每次换歌 `invalidate()`（v6.0 及以前） | 「每次查询独立抽样」在 20 首队列里前 10 首出现重复的概率约 89%。随机性由**什么时候重抽**决定，不由怎么抽决定——这就是 V1.0.1 的整个设计。 |
| 7 | `prev()` 抽 cycle 上一位（v6.0 及以前） | 那是本轮**还没播过**的一首，听起来像往前跳。V6.1 用 `PlayHistory` 弹栈修，V1.0.1 又改成抽签——中间那一版的复杂度（`expected` id 认领）全是为了兜「回退是异步的」。 |
| 8 | `installShuffleOrder()` 每次都装新 order（v6.0 及以前） | `setShuffleOrder` 内部 `createMaskingTimeline()` 造新 `PlaylistTimeline`（**无 equals**）→ `updatePlaybackInfo` 判 timeline 变了 → 派发 `onTimelineChanged` → app 又装一次。一次开关 = 一串 playlist rebuild，跨 binder 放大成卡顿。**判重的 guard 必须存在**。 |
| 9 | 耳机「上一首」重播当前曲 | 两层原因，都在 media3 默认行为里：① `seekToPrevious()` 比对 `getCurrentPosition() <= getMaxSeekToPreviousPosition()`（默认 3s），超过就 `seekToCurrentItem(0)`；② `hasPreviousMediaItem()` 问 `getPreviousIndex()`，答 `-1` 时同样落到重播分支。**两个都要堵**（V1.0 只堵了①）。 |

**改这块之前先读这几个测试文件**：`RandomShuffleTest`、`RandomShuffleStateTest`、
`RandomShuffleOrderTest`、`RandomShuffleCloneTest`、`RandomShuffleStateStalenessTest`、
`ShuffleStallProbe`、`QueueMirrorTest`——它们编码了上表每一行。

---

## 六、发布流程（含一个非显然的坑）

### 版本号

`app/build.gradle` 顶部是**单一真相来源**，`defaultConfig` 与 `versionedApk` 都读它：

```groovy
ext.appVersionCode = 3
ext.appVersionName = "1.0.3"
```

改版本只改这两行，**不要**去动 `defaultConfig`。

### 为什么需要 `versionedApk` 任务

QQ（及多数 IM 文件传输）**按「文件名 + 大小」去重**。此前每个版本都叫
`app-debug.apk`，且相邻版本字节数相同，
所以发新包给用户，QQ 认为「这个文件我已经有了」，**不认是新安装包**。

`versionedApk` 把产物复制成 `jijian-music-1.0.3-vc4-debug.apk`——文件名唯一，问题消失。

### GitHub Actions（V1.0.3 起）

`.github/workflows/ci.yml`：push 到 main 跑全量单测（含 `_cnt.py` 汇总步）；打 `v*` tag 会构建 versionedApk 并把 APK 挂到对应 GitHub Release。发版 = `git tag v1.0.x && git push origin v1.0.x`，正文可后补；App 内的检查更新读的就是这份 Release 的 tag/标题/正文。

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

- **假修复（早期）**：`LyricView` 底部渐隐 `Brush.verticalGradient` 只给了 1 个
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

> **更省事的做法（统一用它）：直接用文件写工具（`write` / `edit`）。**
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
  用 `NeonHairline()`

---

## 九、已知问题与技术债（接手后建议优先处理）

### V1.0.2 变更（设置页可检查更新 + 更新内容公告）

| 需求 | 落地 | 回归 |
|---|---|---|
| 设置页加「检查更新」按钮 | 新增 `net/UpdateChecker.kt`：一次 HTTP 拉 `repos/{repo}/releases/latest`，解析成 `UpdateInfo`。`MainViewModel.checkForUpdates()` 里 `viewModelScope.launch { withContext(Dispatchers.IO) { ... } }`，`checkSeq` 递增驱动对话框只弹一次 | `UpdateCheckerTest`（11） |
| 拉取当前 GitHub 版本并判断是否更新 | `UpdateChecker.compareVersions(a, b)` / `isNewer(remote, current)`：`v1.0.2` 与 `1.0.2` 等价、缺位补 0、数字分段**按数值比**（`1.0.10 > 1.0.9`）、预发布后缀字典比且「无后缀 > 有后缀」 | `UpdateCheckerTest`（6 个版本比较用例） |
| 更新内容公告 | `UpdateDialog` 显示本机版本 / 最新版本 / 安装包大小 + release body（`notes`）；有 release 链接时给「前往下载」ACTION_VIEW。设置页「检查更新」行的副标题实时反映状态（IDLE / CHECKING / UP_TO_DATE / AVAILABLE / FAILED） | 实机点按 |
| **私有仓拿不到数据** | 仓库 `private=True`，匿名 `/releases/latest` 返回 404。`UpdateChecker` 把 404 的文案写成「更新源不可达，或仓库为私有（可在「访问令牌」里填 GitHub 令牌）」，并新增 `SettingsStore` 的 `update_token` 键（V1.0.2，KEY 常量 26 → 27）持久化令牌 | `SettingsRoundTripTest`（+2：`theUpdateTokenSurvivesAWriteAndRead`、`aBlankUpdateTokenIsTheSameAsNone`） |

> **读写同键是硬约束**：`SettingsStore` 的读写两侧必须用同一个 KEY 常量。键名拼错
> **不会报错**，只会永远读回默认值——「均衡器不保存」那一类缺陷就是这么来的。
> `writeEq` / `writeUpdateToken` 之所以抽成 `internal fun`，就是为了能在 JVM 单测里
> 走真实写入侧验证往返。


### V1.0.3 变更（静默更新检查 + 首页公告条 + CI + 清理）

| 变更 | 说明 | 护栏 |
|---|---|---|
| 启动静默检查更新 | `MainViewModel.maybeCheckForUpdatesOnStartup()`：每 24h 一次（`UPDATE_CHECK_INTERVAL_MS` + 新 KEY `last_update_check_at` 节流）。静默失败或「已是最新」**退回 IDLE**——设置页副标题不显示用户没主动发起过的检查结果；手动检查不节流 | 行为锁在 LxApp 的 LaunchedEffect，节流值持久化 |
| 首页新版本公告条 | `LxApp.UpdateBanner`：仅 `AVAILABLE` 时出现在标签栏上方，玻璃语言（neonGlass + 品牌竖条 + NeonDot 呼吸点），点按复用设置页的 `UpdateDialog`（已从 private 改 internal） | 复用对话框，不新建第二份公告 UI |
| GitHub Actions | `.github/workflows/ci.yml`：push → 全量单测；`v*` tag → 构建 versionedApk + 挂到 Release | 发版从手工 API 链变成 `git tag && git push` |
| 死代码清理 | 删 `allowShowUpdateAlert`（`LxModels.kt`/`AppDatabase.kt`，零读者）；三份被 V1.0.1 推翻的设计文档移入 `docs/archive/`；`_cnt.py`/`_per_class.py`/`_probe_stall.ps1` 去 BOM、去硬编码绝对路径；`ShuffleStallProbe` 注释不再指向已删的 `_dex/` | `recordHistory` **有意保留**：MainViewModel 读它 gate `repo.markPlayed`（最近播放），不是死开关 |

### V1.0.1 变更（两个方向都改成抽签 + 两个 seam）

用户报的三条：「这一次的下一首随机，上一首不随机」「耳机切换依然第一次会重新播放这首歌」
「随机播放下这一次的播放顺序是固定的，不是随机的」。三条指向同一件事：
V1.0 的修法只补了一半。

| 缺陷 | 根因 | 修法 | 回归 |
|---|---|---|---|
| 随机模式下「上一首」不随机 | `RandomShuffleState.previousIndex()` 走的是 cycle 前驱——那是**本轮还没播过**的一首，听起来像往前跳；`prev()` 另一条路径弹 `PlayHistory`，历史为空时返回 `-1` → 重播当前曲 | 两个方向都改为**抽签**：`previousIndex` 与 `nextIndex` 同规则（在除当前曲外的槽位上均匀抽），额外避开刚播完的 `lastTrack`，所以连按两次上一首不会在两首之间弹跳。`PlayHistory.kt` 整个删除，`RandomShuffleOrder` 的 `previousOf` 钩子一并删除 | `RandomShuffleStateTest`（18）、`RandomShuffleOrderTest`（11）、`ShuffleStallProbe`（9） |
| 耳机「上一首」第一次必重播当前曲 | V1.0 只堵了 `getMaxSeekToPreviousPosition()` 那条（位置 > 3s）。还有第二条：`hasPreviousMediaItem()` 问 `ShuffleOrder.getPreviousIndex()`，旧的 `previousOf` 在空历史时返回 `-1`，media3 读成「没有上一首」→ `seekToCurrentItem(0)` | 不是给空历史特判，而是**让抽签永不返回 -1**（队列 > 1 时恒返回合法且不同于当前的下标），`else` 分支进不去。阈值覆写保留作顺序模式第二道防线 | `RandomShuffleOrderTest.previousAlwaysAnswersALegalSlot`、实机 `KEYCODE_MEDIA_PREVIOUS`×5 每次换歌 |
| 随机顺序是固定的 | 就是 cycle 本身：一条全序播完才重洗，用户听到的是重复的同一串顺序 | 删除 cycle 全部机制（`cycleFrom` / `advanceCycle` / `cyclesDrawn` / `generation`），改为每次换歌抽一次并缓存到下一首开始 | `RandomShuffleTest`（6）、`ShuffleStallProbe.everyDrawFromTheSameTrackCanReachEveryOtherTrack` |
| 八处复制粘贴的「镜像 playlist 五步」 | `tryMirrorPlaylist` / `ensureMirrorPlaylist` / `enqueue` / `playNext` / `moveInQueue` / `removeFromQueue` / `clearQueue` / `rebuildFrozenSource` 各自手写同一串步骤，v6.1 就因此在 `moveInQueue` 里漏了第 1 步（先移动再装序） | 新增 `player/ShuffleSink.kt`（install / drop 的 seam）与 `player/QueueMirror.kt`（五步按固定顺序收在一处）；`PlayerController.playlistMode` 字段删除，改问 `mirror.isMirrored` | `QueueMirrorTest`（17） |

**同时清掉**：`PlayerController` 里 `shuffleState.advanceCycle` 的两个发散调用点、
`resetShuffle()` 的两行 pass-through 语义模糊、`forgetInstalledOrder()` 在九处被手写调用。

### V1.0 变更（重新编号首发 + 两处播放修复）

版本号从 `6.2.0 / vc26` 回到 **`1.0.0` / `vc1`**，作为正式首发编号；版本号不再表示
「迭代次数」，而是「对外发布序号」。三个改动：

| 缺陷 | 根因 | 修法 | 回归 |
|---|---|---|---|
| 随机播放要按几次「下一首」才真的跳 | `RandomShuffleState.nextIndex()` 在走到全序末位时**在查询内部**重洗（`drawn()` 无条件 `cyclesDrawn++` + `generation.bump()`）。media3 一次决策会反复问 `getNextIndex`（`hasNext`、通知栏按钮、`MediaPeriodQueue`），于是同一个决策拿到**两条不同的洗牌全序**；app 按钮与媒体会话共享同一 state，任一侧重画就让另一侧错位，周期在非边界处被截断 | `nextIndex()` 改为**纯查询**（画序仅发生在 `orderFor` 缺失时，末位只做防御性回绕，且跳过刚播完的那首）；新增 `advanceCycle(size, current)` 作为**周期推进的唯一入口**，只在真的换了歌时调用（`onMediaItemTransition` 与 `playIndex` 两处），且幂等 | `RandomShuffleStateTest`（20，新增 `queryingAtTheCycleEndDoesNotRedrawIt`、`rollingTheCycleTwiceForTheSameTrackIsHarmless`、`queryingAtTheCycleEndNeverAnswersWithTheTrackThatJustPlayed`）、`ShuffleStallProbe`（8） |
| 耳机/蓝牙/锁屏「上一首」把当前曲从头重播 | `BasePlayer.seekToPrevious()` ≠ `seekToPreviousMediaItem()`：它先比对 `getCurrentPosition() <= getMaxSeekToPreviousPosition()`（media3 默认 3000ms），**超过就 `seekToCurrentItem(0)` = 重播当前曲**。这个默认是为播客「回退几秒」设计的，对音乐播放器是错的 | `PlaybackService` 里 `ExoPlayer.Builder().setMaxSeekToPreviousPositionMs(Long.MAX_VALUE)`，让阈值条件不可达，每次「上一首」都走 `seekToPreviousMediaItemInternal`。app 内按钮本来就不受影响（直连 `PlayerController.prev()`） | 反汇编确证 + 手动真机 |
| 版本号语义混乱 | 6.x 系列累积到 vc26，对外发布序号没有意义 | `ext.appVersionCode = 1` / `ext.appVersionName = "1.0.0"`，产物 `jijian-music-1.0.0-vc1-debug.apk` | `aapt2 dump badging` = `versionCode='1' versionName='1.0.0'` |

> **注意升级路径**：`versionCode` 从 26 降到 1，Android 会把它当成**降级**，
> `adb install` 需带 `-d`，用户侧覆盖安装会被系统拒绝，必须卸载重装。

> **V1.0 之前（v6.2.0 / v6.1.x / v6.0.x / v5.3.x）的逐版本缺陷表已移除。**
> 那些修复都还生效，但描述的是已被 V1.0.1 推翻的播放语义，留档只会误导。
> 需要追溯时看 `git log`（V1.0 起）或 `D:\demo\backups\` 里的源码 zip。

### 高优先

0. **文档与代码冲突时改文档**，不要反向迁就旧描述。V1.0.1 这一轮已把 README
   与本节里 V1.0 之前的章节删掉——那些描述的播放语义（全序 / cycle / 播放历史栈）
   已全部作废，留着比没有更危险。

1. **无 release 签名配置**，`minifyEnabled false`，无 ProGuard 规则 → 无法正式发布。

2. **`ui/MainViewModel.kt` 已 30 KB**，是所有 UI 状态的唯一容器，继续增长会难以维护。

### 中优先

3. `data/stats/PlayStats.kt` 的 `inRange` 是 public 但生产路径未使用（只被测试调用）——
   疑似 Speculative Generality。
   建议：删掉，或让 `MainViewModel.refreshStats()` 用它在客户端再过滤一次
   （当前 `buckets()` 对越界记录靠 `if (index in counts.indices)` 静默丢弃，
   `dayBuckets` 靠 `for (i in starts.indices.reversed())` 兜底，两条路径都容错但无测试
   断言「窗口外的记录一定不影响任何一列」）。
4. `ALL` 档的 `windowStart` 返回 `0L`（全历史查询），但直方图只画 30 天，语义不一致。
5. `play_event` 表有 365 天保留期（`MusicRepository.RETENTION_MS`），
   写入时顺带 `prune`，但**没有清理 UI 提示**。
6. `RandomShuffleState` 的缓存（`nextPick` / `previousPick`）无过期机制，靠
   `onTrackStarted` / `invalidate` 两道入口清；任一路径漏调就退化成「按一次不动、
   按第二次才走」——`RandomShuffle.kt` 的 KDoc 专门写了这条，**改换歌路径时必须回来读**。

### 低优先

7. `examples/example-source.js` 全项目零引用（README 只提概念、不给路径）。
8. `docs/adr/0001-shuffle-cycle-and-play-history.md` 与 `docs/v6.1-refactor-plan.md`
   描述的是已被 V1.0.1 推翻的语义。保留是因为推导过程仍有参考价值，但**不要照它改代码**；
   要么补一份 `0003-shuffle-draws.md` 记下当前契约，要么把这两份移进 `docs/archive/`。

---

## 十、测试

**235 项 / 26 个测试类 / 0 失败**（V1.0.3 实测，与 V1.0.2 持平）。全部是 JVM 单元测试（`app/src/test/java/`），
**不需要设备**。

> **V1.0 → 217 项 / 25 类；V1.0.1 → 222 项 / 25 类；V1.0.2 → 235 项 / 26 类；V1.0.3 → 235 项 / 26 类（功能与清理不改测试面）**（`PlayHistoryTest` 9 项随
> `PlayHistory.kt` 删除，新增 `QueueMirrorTest` 17 项，四个 shuffle 测试类按抽签语义重写）。
> V1.0 之前的基线（144/17 → 214/25）见 `git log`。

| 测试类 | 项数 | 覆盖 |
|---|---|---|
| `EmbeddedLyricsTest` | 18 | ID3v2/FLAC/Ogg/MP4 内嵌歌词解析 |
| `PlayStatsTest` | 17 | 日/周分桶、排行、时区（固定 `Asia/Shanghai`） |
| `RandomShuffleStateTest` | 18 | **V1.0.1 抽签契约**：next 排除当前曲、prev 同样排除且额外避开刚播完的那首、一次抽签在下一首开始前反复问答案不变、`onTrackStarted` 清空缓存、2 首队列两个方向都能换 |
| `LocalLibraryTest` | 15 | 分组、搜索、排序 |
| `QueueMirrorTest` | 17 | **V1.0.1**：镜像 playlist 的五步按固定顺序执行、`move`/`remove` 在播放器改动**之后**才丢弃已装 order、`replace` 拒绝无法镜像的队列 |
| `QueueLogicTest` | 12 | 队列增删移纯逻辑 |
| `RandomShuffleOrderTest` | 11 | **V1.0.1** `ShuffleOrder` 契约：prev 恒答一个合法且不等于当前的下标，克隆体继续从同一份 state 抽 |
| `LrcParserTest` | 8 | LRC 时间戳解析 |
| `ShuffleStallProbe` | 9 | **V1.0.1**：app 走 500 步 + 系统走 500 步都不卡、两条路径永远选同一首、从同一首出发的多次抽签能覆盖全部 N-1 个替代 |
| `NeonTokensTest` | 8 | 极光峰值 4.5:1 红线、品牌渐变三色可辨、动效节律 |
| `ThemePaletteTest` | 8 | 8 套配色的 light/dark scheme |
| `RandomShuffleCloneTest` | 7 | 克隆协议 |
| `EqProfileTest` | 7 | 频段编解码往返、坏数据丢弃不抛 |
| `StallWatchdogTest` | 8 | 静默停顿检测——重试预算、二次重缓冲、仅缓冲态停顿、进度一动就清零（假时钟）；缓冲不消耗冻结预算 |
| `OledTokensTest` | 6 | 设计 token 一致性 + 4.5:1 对比度 |
| `RandomShuffleStateStalenessTest` | 6 | 队列缩容后 memo 不越界 |
| `LocalLibraryMergeTest` | 6 | 本地库合并 |
| `RandomShuffleTest` | 6 | **V1.0.1**：`pickOther` 恒落在范围内且永不返回被排除的槽位；600 seed 上的均匀性 |
| `SafMusicScannerTest` | 5 | SAF 扫描 |
| `UpdateCheckerTest` | 11 | **V1.0.2**：release tag 的版本比较（去 `v` 前缀、数字分段、预发布后缀排序）+ GitHub release JSON 解析（取首个 .apk 资产、字段缺失降级、标题为空回落 tag） |
| `SettingsTest` | 5 | 设置默认值与枚举取值 |
| `SettingsRoundTripTest` | 11 | 写入侧与读取侧用同一批 `KEY_*` 往返——每个 EQ 字段 + 枚举回落 + 上次播放指针，堵住「均衡器不保存」那类默认值掩盖的缺陷 |
| `QueueIndexTest` | 5 | 队列槽位只有在该槽位的 **id** 就是正在播的那首时才算数（在线队列 `playIndex(3)` 被报成 0 的缺陷） |
| `LocalTrackMapperTest` | 4 | 曲目映射 |
| `PlayerSkinTest` | 4 | 皮肤解析与未知值回落 |
| `RecordPolicyTest` | 3 | `recordHistory` 与 `recordStats` 互相独立 |

**跑单个测试类**：

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.lxmusic.android.player.RandomShuffleCloneTest" --no-daemon --console=plain
```

**数测试项不要用 PowerShell 的 `[xml]`**（大文件会慢到超时，且格式化属性会把计数搞错）。
用正则扫 `app/build/test-results/testDebugUnitTest/*.xml` 的 `<testcase ` 即可，
项目根的 `_cnt.py` 就是干这个的（`_per_class.py` 输出每个类的项数，
`_probe_stall.ps1` 是「位置冻住」的长播探针，需要模拟器在线）：

```powershell
& python _cnt.py     # files=26 tests=235 failures=0 errors=0
```

---

## 十一、历史版本与备份

**V1.0 起**用 git：`git log` / `git show <ref>:<path>`，远端
`https://github.com/xxwzh66-byte/lxmusic-android`（私有），tag `v1.0.0` 对应首发。

**V1.0 之前**只有 `D:\demo\backups\` 里三个源码 zip（v5.3.0.1 ×2、v5.5.2），
里面是旧的 `ShuffleBag.kt` / `ShuffleBagTest.kt`，**没有 `RandomShuffle` 的历史版本**。

项目根**没有** `UPDATE_PLAN.md`（早期版本的控制台报告里提过这个文件名，实际从未入库）。
版本历史的权威记录是 `README.md` 的版本章节：当前版本在最前面的 `V1.0.3` 一节，
往下是时间线留档（早于 V1.0.3 的章节只用于追溯，不代表当前行为）。

领域词表是 `GLOSSARY.md`（队列 / 镜像 playlist / 抽签 Draw / Next / Previous / 循环 /
静默停顿 / 偏好 / 均衡器曲线 / 效果状态）。

`docs/` 下三份是**早期**设计留档：`docs/adr/0001-shuffle-cycle-and-play-history.md`
（ADR：为什么当时选 cycle，**结论已被 V1.0.1 推翻**）、
`docs/adr/0002-detect-silent-stalls.md`（ADR：静默停顿检测，仍然有效）、
`docs/v6.1-refactor-plan.md`（施工单 + 验收记录，历史）。

---

## 十二、接手检查清单

- [ ] 确认 `C:\Program Files (x86)\Android\openjdk\jdk-21.0.8` 与 `D:\demo\android-sdk` 存在
- [ ] 跑一次标准构建命令，确认 `BUILD SUCCESSFUL` + 235 测试 0 失败
- [ ] 读 `FeatureFlags.kt`（理解在线/离线边界）
- [ ] 读 `player/RandomShuffle.kt` 的 KDoc + `RandomShuffle*` 五个测试类 + `ShuffleStallProbe` + `QueueMirrorTest` + `StallWatchdogTest`
- [ ] 读 `ui/theme/OledTokens.kt` 与 `ui/neon/NeonTokens.kt`（改 UI 前必读）
- [ ] 读本报告第九节，决定先还哪笔技术债
- [ ] 若要真机调试：`adb install -r -d build/apk/jijian-music-1.0.3-vc4-debug.apk`
      （`-d` 必需：`versionCode` 相对 6.x 时代的 26 是降级，系统默认拒绝）

---

## 十三、常用命令速查

```powershell
# 构建 + 出包 + 测试
cd D:\demo\lxmusic-android
$env:JAVA_HOME="C:\Program Files (x86)\Android\openjdk\jdk-21.0.8"; $env:ANDROID_SDK_ROOT="D:\demo\android-sdk"
.\gradlew.bat :app:assembleDebug :app:versionedApk :app:testDebugUnitTest --no-daemon --console=plain

# 看 APK 版本
D:\demo\android-sdk\build-tools\34.0.0\aapt2.exe dump badging build\apk\jijian-music-1.0.3-vc4-debug.apk | Select-String versionCode

# 验签
D:\demo\android-sdk\build-tools\34.0.0\apksigner.bat verify --print-certs build\apk\jijian-music-1.0.3-vc4-debug.apk

# 重新生成搜索 bundle
cd D:\demo\lx-builder; node build.mjs

# 设备（本机已装无头模拟器；无真机）
D:\demo\android-sdk\platform-tools\adb.exe devices -l
D:\demo\android-sdk\platform-tools\adb.exe install -r <apk>
D:\demo\android-sdk\platform-tools\adb.exe shell run-as com.lxmusic.android.debug cat files/last_crash.txt
```

---

*本报告由当前代码库实况生成，所有文件体积、行号、测试数均为实测值。*
