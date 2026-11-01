# lxmusic-android v1.0.1 · 按 skill 文件夹分类

> 来源 release：`https://github.com/xxwzh66-byte/lxmusic-android/releases/tag/v1.0.1`
> 本地已是该 tag 的 checkout（`git tag` 含 `v1.0.1`，`versionName=1.0.1` / `versionCode=2`，
>  产物 `build/apk/jijian-music-1.0.1-vc2-debug.apk` 23,399,318 B 已就位）。
> 分类依据：`D:\demo\skill\INDEX.md` 的 12 类用途 taxonomy。

## skill 分类总览（参考）

| 目录 | 用途 | 本项目是否命中 |
|---|---|---|
| `00-路由` | 路由/入口选择：不确定走哪条流程时先问它 | — 无直接对应 |
| `01-调研与规划` | 调研与规划：想清楚做什么，拆成可执行单元 | ✅ 命中 |
| `02-开发实现` | 开发实现：真的开始写代码 | ✅ 命中 |
| `03-代码质量` | 代码质量：写完后的审查、排障、复盘 | ✅ 命中 |
| `04-架构与设计` | 架构与设计：模块边界、领域语言、长期可维护性 | ✅ 命中 |
| `05-写作与文档` | 写作与文档：写给人和写给 agent 的文本 | ✅ 命中 |
| `06-协作与沟通` | 协作与沟通：拷问思路、交接、多 agent 协作 | — 无直接对应 |
| `07-环境与工具` | 环境与工具：一次性配置，装好长期有效 | ✅ 命中 |
| `08-UI与设计` | UI与设计：界面、配色、排版、可访问性 | ✅ 命中 |
| `09-学习与教学` | 学习与教学：从零搞懂一个概念 | — 无直接对应 |
| `10-逆向工程` | 逆向工程：分析二进制、固件、Electron 应用 | ✅ 命中 |
| `11-角色行为蒸馏` | 角色行为蒸馏：把二次元角色变成可执行的 agent 行为包 | — 无直接对应 |

## 各分类文件清单

### `00-路由` · 路由/入口选择 — 无直接对应内容

### `01-调研与规划` · 调研与规划（1 项）

- `docs/v6.1-refactor-plan.md` — V6.1 重构计划（调研/规划）

### `02-开发实现` · 开发实现（33 项）

- `.gitattributes` — 项目文件
- `.gitignore` — 项目文件
- `app/src/main/AndroidManifest.xml` — 项目文件
- `app/src/main/java/com/lxmusic/android/CrashLogger.kt` — Kotlin 源码 · root 模块
- `app/src/main/java/com/lxmusic/android/FeatureFlags.kt` — Kotlin 源码 · root 模块
- `app/src/main/java/com/lxmusic/android/LxApp.kt` — Kotlin 源码 · root 模块
- `app/src/main/java/com/lxmusic/android/MainActivity.kt` — Kotlin 源码 · root 模块
- `app/src/main/java/com/lxmusic/android/data/db/AppDatabase.kt` — Kotlin 源码 · data 模块
- `app/src/main/java/com/lxmusic/android/data/model/MusicInfo.kt` — Kotlin 源码 · data 模块
- `app/src/main/java/com/lxmusic/android/data/repo/MusicRepository.kt` — Kotlin 源码 · data 模块
- `app/src/main/java/com/lxmusic/android/data/settings/EqProfile.kt` — Kotlin 源码 · data 模块
- `app/src/main/java/com/lxmusic/android/data/settings/SettingsStore.kt` — Kotlin 源码 · data 模块
- `app/src/main/java/com/lxmusic/android/data/stats/PlayStats.kt` — Kotlin 源码 · data 模块
- `app/src/main/java/com/lxmusic/android/local/LocalFileDeleter.kt` — Kotlin 源码 · local 模块
- `app/src/main/java/com/lxmusic/android/local/LocalLibrary.kt` — Kotlin 源码 · local 模块
- `app/src/main/java/com/lxmusic/android/local/LocalMusicScanner.kt` — Kotlin 源码 · local 模块
- `app/src/main/java/com/lxmusic/android/local/LocalTrackMapper.kt` — Kotlin 源码 · local 模块
- `app/src/main/java/com/lxmusic/android/local/SafMusicScanner.kt` — Kotlin 源码 · local 模块
- `app/src/main/java/com/lxmusic/android/lyric/EmbeddedLyrics.kt` — Kotlin 源码 · lyric 模块
- `app/src/main/java/com/lxmusic/android/lyric/Lyric.kt` — Kotlin 源码 · lyric 模块
- `app/src/main/java/com/lxmusic/android/lyric/LyricEncoding.kt` — Kotlin 源码 · lyric 模块
- `app/src/main/java/com/lxmusic/android/lyric/LyricLoader.kt` — Kotlin 源码 · lyric 模块
- `app/src/main/java/com/lxmusic/android/player/EqualizerController.kt` — Kotlin 源码 · player 模块
- `app/src/main/java/com/lxmusic/android/player/PlaybackService.kt` — Kotlin 源码 · player 模块
- `app/src/main/java/com/lxmusic/android/player/PlayerController.kt` — Kotlin 源码 · player 模块
- `app/src/main/java/com/lxmusic/android/player/PlayerHolder.kt` — Kotlin 源码 · player 模块
- `app/src/main/java/com/lxmusic/android/player/QueueIndex.kt` — Kotlin 源码 · player 模块
- `app/src/main/java/com/lxmusic/android/player/QueueLogic.kt` — Kotlin 源码 · player 模块
- `app/src/main/java/com/lxmusic/android/player/QueueMirror.kt` — Kotlin 源码 · player 模块
- `app/src/main/java/com/lxmusic/android/player/RandomShuffle.kt` — Kotlin 源码 · player 模块
- `app/src/main/java/com/lxmusic/android/player/RandomShuffleOrder.kt` — Kotlin 源码 · player 模块
- `app/src/main/java/com/lxmusic/android/player/ShuffleSink.kt` — Kotlin 源码 · player 模块
- `app/src/main/java/com/lxmusic/android/player/StallWatchdog.kt` — Kotlin 源码 · player 模块

### `03-代码质量` · 代码质量（26 项）

- `_probe_stall.ps1` — 静默停顿探测脚本（排障）
- `app/src/test/java/com/lxmusic/android/data/EqProfileTest.kt` — 单元测试（JVM）
- `app/src/test/java/com/lxmusic/android/data/SettingsRoundTripTest.kt` — 单元测试（JVM）
- `app/src/test/java/com/lxmusic/android/data/SettingsTest.kt` — 单元测试（JVM）
- `app/src/test/java/com/lxmusic/android/data/stats/PlayStatsTest.kt` — 单元测试（JVM）
- `app/src/test/java/com/lxmusic/android/data/stats/RecordPolicyTest.kt` — 单元测试（JVM）
- `app/src/test/java/com/lxmusic/android/local/LocalLibraryMergeTest.kt` — 单元测试（JVM）
- `app/src/test/java/com/lxmusic/android/local/LocalLibraryTest.kt` — 单元测试（JVM）
- `app/src/test/java/com/lxmusic/android/local/LocalTrackMapperTest.kt` — 单元测试（JVM）
- `app/src/test/java/com/lxmusic/android/local/SafMusicScannerTest.kt` — 单元测试（JVM）
- `app/src/test/java/com/lxmusic/android/lyric/EmbeddedLyricsTest.kt` — 单元测试（JVM）
- `app/src/test/java/com/lxmusic/android/lyric/LrcParserTest.kt` — 单元测试（JVM）
- `app/src/test/java/com/lxmusic/android/player/QueueIndexTest.kt` — 单元测试（JVM）
- `app/src/test/java/com/lxmusic/android/player/QueueLogicTest.kt` — 单元测试（JVM）
- `app/src/test/java/com/lxmusic/android/player/QueueMirrorTest.kt` — 单元测试（JVM）
- `app/src/test/java/com/lxmusic/android/player/RandomShuffleCloneTest.kt` — 单元测试（JVM）
- `app/src/test/java/com/lxmusic/android/player/RandomShuffleOrderTest.kt` — 单元测试（JVM）
- `app/src/test/java/com/lxmusic/android/player/RandomShuffleStateStalenessTest.kt` — 单元测试（JVM）
- `app/src/test/java/com/lxmusic/android/player/RandomShuffleStateTest.kt` — 单元测试（JVM）
- `app/src/test/java/com/lxmusic/android/player/RandomShuffleTest.kt` — 单元测试（JVM）
- `app/src/test/java/com/lxmusic/android/player/ShuffleStallProbe.kt` — 单元测试（JVM）
- `app/src/test/java/com/lxmusic/android/player/StallWatchdogTest.kt` — 单元测试（JVM）
- `app/src/test/java/com/lxmusic/android/ui/components/PlayerSkinTest.kt` — 单元测试（JVM）
- `app/src/test/java/com/lxmusic/android/ui/neon/NeonTokensTest.kt` — 单元测试（JVM）
- `app/src/test/java/com/lxmusic/android/ui/theme/OledTokensTest.kt` — 单元测试（JVM）
- `app/src/test/java/com/lxmusic/android/ui/theme/ThemePaletteTest.kt` — 单元测试（JVM）

### `04-架构与设计` · 架构与设计（2 项）

- `docs/adr/0001-shuffle-cycle-and-play-history.md` — 架构决策记录（ADR）
- `docs/adr/0002-detect-silent-stalls.md` — 架构决策记录（ADR）

### `05-写作与文档` · 写作与文档（3 项）

- `GLOSSARY.md` — 领域术语表
- `HANDOVER.md` — 项目交接报告：环境、代码地图、发布流程、排障
- `README.md` — 项目总说明：架构、功能、V1.0/V1.0.1 变更史

### `06-协作与沟通` · 协作与沟通 — 无直接对应内容

### `07-环境与工具` · 环境与工具（13 项）

- `_cnt.py` — 源码行数统计脚本
- `_per_class.py` — 逐类统计脚本
- `app/build.gradle` — Gradle 工程配置
- `app/proguard-rules.pro` — 混淆规则
- `build.gradle` — Gradle 工程配置
- `gradle.properties` — Gradle 全局属性
- `gradle/libs.versions.toml` — Gradle wrapper（含华为云镜像）
- `gradle/wrapper/gradle-wrapper.jar` — Gradle wrapper（含华为云镜像）
- `gradle/wrapper/gradle-wrapper.properties` — Gradle wrapper（含华为云镜像）
- `gradlew` — Gradle 包装器启动脚本
- `gradlew.bat` — Gradle 包装器启动脚本
- `local.properties` — 本地 SDK 路径（写死 sdk.dir）
- `settings.gradle` — Gradle 工程配置

### `08-UI与设计` · UI与设计（53 项）

- `app/src/main/java/com/lxmusic/android/ui/CrashReportDialog.kt` — Kotlin 源码 · ui 模块
- `app/src/main/java/com/lxmusic/android/ui/LxApp.kt` — Kotlin 源码 · ui 模块
- `app/src/main/java/com/lxmusic/android/ui/MainViewModel.kt` — Kotlin 源码 · ui 模块
- `app/src/main/java/com/lxmusic/android/ui/components/ArtworkColors.kt` — Kotlin 源码 · ui 模块
- `app/src/main/java/com/lxmusic/android/ui/components/Common.kt` — Kotlin 源码 · ui 模块
- `app/src/main/java/com/lxmusic/android/ui/components/CommonComponents.kt` — Kotlin 源码 · ui 模块
- `app/src/main/java/com/lxmusic/android/ui/components/Hints.kt` — Kotlin 源码 · ui 模块
- `app/src/main/java/com/lxmusic/android/ui/components/LyricView.kt` — Kotlin 源码 · ui 模块
- `app/src/main/java/com/lxmusic/android/ui/components/PlayerSkin.kt` — Kotlin 源码 · ui 模块
- `app/src/main/java/com/lxmusic/android/ui/components/RotatingArtwork.kt` — Kotlin 源码 · ui 模块
- `app/src/main/java/com/lxmusic/android/ui/components/SearchBar.kt` — Kotlin 源码 · ui 模块
- `app/src/main/java/com/lxmusic/android/ui/components/TrackActionsSheet.kt` — Kotlin 源码 · ui 模块
- `app/src/main/java/com/lxmusic/android/ui/neon/AuroraBackdrop.kt` — Kotlin 源码 · ui 模块
- `app/src/main/java/com/lxmusic/android/ui/neon/GlassSurface.kt` — Kotlin 源码 · ui 模块
- `app/src/main/java/com/lxmusic/android/ui/neon/NeonControls.kt` — Kotlin 源码 · ui 模块
- `app/src/main/java/com/lxmusic/android/ui/neon/NeonMotion.kt` — Kotlin 源码 · ui 模块
- `app/src/main/java/com/lxmusic/android/ui/neon/NeonProgress.kt` — Kotlin 源码 · ui 模块
- `app/src/main/java/com/lxmusic/android/ui/neon/NeonScaffold.kt` — Kotlin 源码 · ui 模块
- `app/src/main/java/com/lxmusic/android/ui/neon/NeonShell.kt` — Kotlin 源码 · ui 模块
- `app/src/main/java/com/lxmusic/android/ui/neon/NeonSurface.kt` — Kotlin 源码 · ui 模块
- `app/src/main/java/com/lxmusic/android/ui/neon/NeonTokens.kt` — Kotlin 源码 · ui 模块
- `app/src/main/java/com/lxmusic/android/ui/screens/EqualizerScreen.kt` — Kotlin 源码 · ui 模块
- `app/src/main/java/com/lxmusic/android/ui/screens/FolderManagerScreen.kt` — Kotlin 源码 · ui 模块
- `app/src/main/java/com/lxmusic/android/ui/screens/LibraryScreen.kt` — Kotlin 源码 · ui 模块
- `app/src/main/java/com/lxmusic/android/ui/screens/LocalScreen.kt` — Kotlin 源码 · ui 模块
- `app/src/main/java/com/lxmusic/android/ui/screens/NowPlayingScreen.kt` — Kotlin 源码 · ui 模块
- `app/src/main/java/com/lxmusic/android/ui/screens/OnlineScreen.kt` — Kotlin 源码 · ui 模块
- `app/src/main/java/com/lxmusic/android/ui/screens/QueueScreen.kt` — Kotlin 源码 · ui 模块
- `app/src/main/java/com/lxmusic/android/ui/screens/SettingsScreen.kt` — Kotlin 源码 · ui 模块
- `app/src/main/java/com/lxmusic/android/ui/screens/StatsScreen.kt` — Kotlin 源码 · ui 模块
- `app/src/main/java/com/lxmusic/android/ui/theme/ColorPalettes.kt` — Kotlin 源码 · ui 模块
- `app/src/main/java/com/lxmusic/android/ui/theme/OledTokens.kt` — Kotlin 源码 · ui 模块
- `app/src/main/java/com/lxmusic/android/ui/theme/Theme.kt` — Kotlin 源码 · ui 模块
- `app/src/main/res/drawable/ic_launcher_background.xml` — Android 资源（图标/配色/字符串/主题）
- `app/src/main/res/drawable/ic_launcher_foreground.xml` — Android 资源（图标/配色/字符串/主题）
- `app/src/main/res/drawable/ic_launcher_monochrome.xml` — Android 资源（图标/配色/字符串/主题）
- `app/src/main/res/drawable/ic_notification.xml` — Android 资源（图标/配色/字符串/主题）
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` — Android 资源（图标/配色/字符串/主题）
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml` — Android 资源（图标/配色/字符串/主题）
- `app/src/main/res/mipmap-hdpi/ic_launcher.png` — Android 资源（图标/配色/字符串/主题）
- `app/src/main/res/mipmap-hdpi/ic_launcher_round.png` — Android 资源（图标/配色/字符串/主题）
- `app/src/main/res/mipmap-mdpi/ic_launcher.png` — Android 资源（图标/配色/字符串/主题）
- `app/src/main/res/mipmap-mdpi/ic_launcher_round.png` — Android 资源（图标/配色/字符串/主题）
- `app/src/main/res/mipmap-xhdpi/ic_launcher.png` — Android 资源（图标/配色/字符串/主题）
- `app/src/main/res/mipmap-xhdpi/ic_launcher_round.png` — Android 资源（图标/配色/字符串/主题）
- `app/src/main/res/mipmap-xxhdpi/ic_launcher.png` — Android 资源（图标/配色/字符串/主题）
- `app/src/main/res/mipmap-xxhdpi/ic_launcher_round.png` — Android 资源（图标/配色/字符串/主题）
- `app/src/main/res/mipmap-xxxhdpi/ic_launcher.png` — Android 资源（图标/配色/字符串/主题）
- `app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png` — Android 资源（图标/配色/字符串/主题）
- `app/src/main/res/values-night/themes.xml` — Android 资源（图标/配色/字符串/主题）
- `app/src/main/res/values/colors.xml` — Android 资源（图标/配色/字符串/主题）
- `app/src/main/res/values/strings.xml` — Android 资源（图标/配色/字符串/主题）
- `app/src/main/res/values/themes.xml` — Android 资源（图标/配色/字符串/主题）

### `09-学习与教学` · 学习与教学 — 无直接对应内容

### `10-逆向工程` · 逆向工程（13 项）

- `app/src/main/assets/jsengine/lx-search-bundle.js` — 由 lx-builder 打包的 LX 桌面版搜索模块（逆向移植）
- `app/src/main/assets/jsengine/search_host.js` — WebView #1 的 Node 风格 shim（Buffer/crypto/zlib/httpFetch）
- `app/src/main/java/com/lxmusic/android/jsengine/LxJsBridge.kt` — Kotlin 源码 · jsengine 模块
- `app/src/main/java/com/lxmusic/android/jsengine/LxScriptRuntime.kt` — Kotlin 源码 · jsengine 模块
- `app/src/main/java/com/lxmusic/android/jsengine/LxSearchRuntime.kt` — Kotlin 源码 · jsengine 模块
- `app/src/main/java/com/lxmusic/android/jsengine/SharedNativeBridge.kt` — Kotlin 源码 · jsengine 模块
- `app/src/main/java/com/lxmusic/android/net/HttpBridge.kt` — Kotlin 源码 · net 模块
- `app/src/main/java/com/lxmusic/android/net/UpdateChecker.kt` — Kotlin 源码 · net 模块
- `app/src/main/java/com/lxmusic/android/source/KwSearchProvider.kt` — Kotlin 源码 · source 模块
- `app/src/main/java/com/lxmusic/android/source/LxModels.kt` — Kotlin 源码 · source 模块
- `app/src/main/java/com/lxmusic/android/source/LxUtils.kt` — Kotlin 源码 · source 模块
- `app/src/main/java/com/lxmusic/android/source/SourceManager.kt` — Kotlin 源码 · source 模块
- `examples/example-source.js` — LX 自定义音源脚本示例（逆向契约样例）

### `11-角色行为蒸馏` · 角色行为蒸馏 — 无直接对应内容

**合计归类文件：144 项**（不含 build/.gradle/.git 等生成产物）。

## 跨类说明
- **08-UI与设计** 与 **02-开发实现** 高度重叠：所有 `ui/` 与 `res/` 既是实现也是设计系统（Neon Vinyl）。
- **10-逆向工程** 对应本项目对 lx-music-desktop 音源架构的逆向移植：WebView 双引擎、自定义音源 `window.lx` shim、
  原生桥（crypto/zlib/http）、内置 LX 搜索 Provider。在线栈默认休眠（`FeatureFlags.ONLINE_ENABLED=false`）。
- **04-架构与设计** 由 `docs/adr/*` 承载（洗牌周期、静默停顿检测等架构决策）。
- **00-路由 / 06-协作与沟通 / 09-学习与教学 / 11-角色行为蒸馏** 在本工程无直接对应模块。