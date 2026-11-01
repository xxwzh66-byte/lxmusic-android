# 极简music (JiJianMusic) for Android

`applicationId = com.lxmusic.android` · 当前版本 **1.0.3** (`versionCode = 4`)

一个从 [lyswhut/lx-music-desktop](https://github.com/lyswhut/lx-music-desktop) 的
音源架构出发、逐步转型为**纯本地极简音乐播放器**的 Android 应用。
在线搜索/自定义音源模块仍然完整保留在代码中，但默认通过
`FeatureFlags.ONLINE_ENABLED = false` 关闭（改一个常量即可恢复）。

An Android music player derived from the architecture of
[lyswhut/lx-music-desktop](https://github.com/lyswhut/lx-music-desktop).

The desktop app is Electron + Vue 3; this project ports its **music-source
architecture** to native Android (Kotlin + Jetpack Compose + Media3/ExoPlayer).

---

接手开发请先读 **[HANDOVER.md](HANDOVER.md)**——环境搭建、代码地图、发布流程、排障手册、技术债清单。

> 版本历史只保留 **V1.0 起的正式发布**章节。V1.0 之前（5.3.x / 6.0–6.2，试用期编号）
> 的章节已移除——那些版本提到的 `ShuffleBag` 洗牌袋、`PlayHistory` 历史栈、
> `Changelog.kt` / `AnnouncementScreen` 等文件全部删除，播放语义也已被 V1.0.1 推翻，
> 留档只会误导。当前行为以 `HANDOVER.md` 与代码为准。

## Why this design

lx-music-desktop separates concerns into two layers:

1. **Built-in music SDK** (`src/renderer/utils/musicSdk/*`) — JS modules that
   implement *search* for each platform (Kw/Kugou/QQ/NetEase/Migu).
2. **Custom sources / "自定义源"** (`src/main/modules/userApi/*`) — a sandboxed
   `window.lx` runtime in which users load JS scripts that resolve a song to a
   **playable URL** (`musicUrl`), plus `lyric`/`pic`. The script declares its
   capabilities via `lx.send('inited', {sources:{...}})` and answers actions
   through `lx.on('request', handler)`.

Android cannot run Electron, so this project reproduces **both layers inside a
headless `WebView`** and bridges them to native code:

```
┌──────────────────────────── Android app (Kotlin) ────────────────────────────┐
│  Compose UI  ─▶  MainViewModel  ─▶  SourceManager                            │
│                                       │            │                          │
│                       ┌───────────────┘            └───────────────┐          │
│                       ▼                                            ▼          │
│          LxSearchRuntime (WebView #1)                LxScriptRuntime (WebView #2)
│          search_host.js  +                           user's custom source     │
│          lx-search-bundle.js                         script (window.lx shim)  │
│                       │                                            │          │
│                       └──────────────┬─────────────────────────────┘          │
│                                      ▼                                        │
│                       SharedNativeBridge  (crypto / zlib / OkHttp)            │
│                                      │                                        │
│                                      ▼                                        │
│                       Media3 ExoPlayer  (PlaybackService + MediaSession)      │
└───────────────────────────────────────────────────────────────────────────────┘
```

### WebView #1 — search engine
`search_host.js` supplies Node-like shims (`Buffer`, `crypto`, `zlib`,
`httpFetch`) backed by the native bridge. `lx-search-bundle.js` is produced by
the `lx-builder` esbuild pipeline and packages the **original LX search modules**
(slightly adapted) so search behaviour matches the desktop app.

### WebView #2 — custom source (playback)
`LxScriptRuntime` injects the exact `window.lx` shim used by the desktop app
(`preload.js`) and evaluates the user's source script verbatim. This means
**existing LX Music custom-source scripts run unmodified on Android**.

### Native bridge
`SharedNativeBridge` implements the primitives scripts rely on:
`lxNative.request` (OkHttp), `md5`, `aesEncrypt/aesDecrypt`, `randomBytes`,
`inflate`, `deflate`.

---

## Supported sources

| Source | Native search (WebView #1) | Playback (custom source) |
|--------|:--------------------------:|:------------------------:|
| 酷我 (kw)  | ✅ live-tested | via user source |
| 酷狗 (kg)  | ✅ live-tested | via user source |
| 咪咕 (mg)  | ✅ live-tested | via user source |
| QQ (tx)    | needs desktop request headers | via user source |
| 网易 (wy)  | needs desktop request headers | via user source |

Playback of every source works through a loaded custom source (`导入音源`),
exactly as in the desktop app.

---

## Building

```bash
# Requires JDK 21 and the Android SDK (platform 34, build-tools 34.0.0).
# JDK 17 also works; AGP 8.5 does *not* support JDK 25.
export JAVA_HOME=/path/to/jdk21
export ANDROID_SDK_ROOT=/path/to/android-sdk
./gradlew :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

For anything you actually hand to a user, also run `:app:versionedApk` —
a second copy is emitted as
`build/apk/jijian-music-<versionName>-vc<versionCode>-debug.apk`.
That rename is load-bearing: QQ (and most IM file transfers) de-duplicate on
**file name + size**, and consecutive `app-debug.apk` builds were byte-identical
in length, so a new package was silently matched against the one already in the
chat. See [HANDOVER.md](HANDOVER.md) §6.

### Rebuilding the search bundle
The search providers are compiled from `lx-music-desktop` sources by
`lx-builder/` (esbuild). To regenerate `app/src/main/assets/jsengine/lx-search-bundle.js`:

```bash
cd lx-builder
node build.mjs
```

`lx-builder/lx/` holds the vendored `musicSdk` sources from
`lx-music-desktop@dev`.

---

## Using a custom source

1. Open the app → **导入音源**.
2. Paste an LX Music custom-source script (the same `.js` files used by the
   desktop app / [lx-music-script](https://github.com/lyswhut/lx-music-script)).
3. Tap **导入并加载**. The script registers its supported sources; search for a
   song, then tap a result to play.

---

## Project layout

```
app/src/main/java/com/lxmusic/android/
├── FeatureFlags.kt              # ONLINE_ENABLED / CUSTOM_SOURCE_ENABLED
├── LxApp.kt                     # Application + singletons
├── MainActivity.kt              # Compose entry point
├── CrashLogger.kt               # uncaught-exception handler -> filesDir/last_crash.txt
├── data/
│   ├── db/AppDatabase.kt        # Room DB v4 (playlists, favorites, history, local_track, play_event)
│   ├── model/MusicInfo.kt
│   ├── repo/MusicRepository.kt  # single DB access point
│   ├── settings/SettingsStore.kt# DataStore preferences (26 keys)
│   ├── settings/EqProfile.kt    # persistable tone curve (bands encode/decode)
│   └── stats/PlayStats.kt       # pure play-count aggregation (day / week / all)
├── jsengine/                    # (dormant) WebView engines
│   ├── LxSearchRuntime.kt       # WebView #1 (search)
│   ├── LxScriptRuntime.kt       # WebView #2 (custom source)
│   ├── LxJsBridge.kt            # window.lx -> native (source runtime)
│   └── SharedNativeBridge.kt    # crypto/zlib/http primitives
├── local/
│   ├── LocalMusicScanner.kt     # MediaStore fallback scan
│   ├── SafMusicScanner.kt       # SAF folder scan (T2)
│   ├── LocalFileDeleter.kt      # file deletion (T4)
│   ├── LocalLibrary.kt          # grouping / sorting / search / merge
│   └── LocalTrackMapper.kt      # entity <-> model
├── lyric/
│   ├── Lyric.kt                 # Lyric model + LrcParser
│   ├── LyricLoader.kt           # .lrc sibling + embedded lookup
│   ├── LyricEncoding.kt         # UTF-8 / GBK / Latin-1 detection
│   └── EmbeddedLyrics.kt        # ID3 / FLAC / Ogg / MP4 tag reader
├── net/
│   ├── HttpBridge.kt            # OkHttp wrapper
│   └── UpdateChecker.kt         # V1.0.2: GitHub Releases latest -> UpdateInfo
├── player/
│   ├── PlaybackService.kt       # MediaSessionService + ExoPlayer
│   ├── PlayerController.kt      # URL resolution + playback state
│   ├── PlayerHolder.kt          # the ExoPlayer instance (audioSessionId, ShuffleOrder)
│   ├── RandomShuffle.kt         # V1.0.1: per-draw shuffle policy (authoritative KDoc)
│   ├── RandomShuffleOrder.kt    # media3 ShuffleOrder adapter
│   ├── ShuffleSink.kt           # V1.0.1: install/drop seam for the ShuffleOrder
│   ├── QueueMirror.kt           # V1.0.1: the one place that mirrors the Queue
│   ├── QueueIndex.kt            # media id -> queue slot resolution
│   ├── StallWatchdog.kt         # silent-stall detection (pure Kotlin)
│   ├── QueueLogic.kt            # queue index bookkeeping
│   └── EqualizerController.kt   # audiofx wrapper (+ EqProfile replay on attach)
├── source/                      # (dormant) LX search/source facade
│   ├── KwSearchProvider.kt      # native HTTP fallback search
│   ├── LxModels.kt
│   ├── SourceManager.kt
│   └── LxUtils.kt
├── ui/
│   ├── LxApp.kt                 # Compose root + navigation
│   ├── MainViewModel.kt         # single state holder
│   ├── CrashReportDialog.kt     # previous-run crash trace, copy/clear
│   ├── components/              # TrackRow, LyricView, RotatingArtwork, PlayerSkin, ArtworkColors, SearchBar, Hints
│   ├── neon/                    # Neon Vinyl design system: tokens, aurora, glass, controls, shell, NeonScreen scaffold
│   ├── screens/                 # Local, Library, Online, NowPlaying, Queue, Equalizer, Stats, FolderManager, Settings
│   └── theme/                   # OledTokens / ColorPalettes / Theme
```

> `data/Changelog.kt`, `ui/screens/AnnouncementScreen.kt`, `player/ShuffleBag.kt`,
> `player/BagShuffleOrder.kt` and `player/PlayHistory.kt` no longer exist —
> `RandomShuffle.kt` is the only shuffle implementation. Any doc still referring
> to the others is stale.

---

## Legal

This project is a technical study of LX Music's source architecture. It ships no
music and hosts no content. All audio URLs are resolved at runtime by
user-provided source scripts. Respect copyright and the laws of your
jurisdiction.
---

## Features

### Local music player
- Scans device audio via `MediaStore` (`LocalMusicScanner`), grouped and sorted.
- Plays local files directly with ExoPlayer (file path **or** `content://` Uri).
- Embedded / sibling `.lrc` lyric loading (`LyricLoader`).

### Lyrics
- LRC parser (`LrcParser`) supporting multiple timestamps per line, `mm:ss.xx`
  with 1–3 fractional digits, and automatic **translation** detection (duplicate
  timestamps become a parallel track).
- Binary-search line lookup (`Lyric.indexAt`) for O(log n) sync during playback.
- Karaoke-style scrolling view (`LyricView`): active line enlarged, highlighted,
  auto-centred, with the translation shown beneath.

### Cool UI — "Neon Vinyl"
- **Aurora backdrop**: three radial-gradient lights drift on elliptical orbits
  (11 s / 17 s / 23 s, coprime so they never line up). On the now-playing screen
  the palette is **extracted from the current artwork**, so the room changes
  colour with the song.
- **Glass, not blur**: panels are translucent white + 1 dp stroke + a top
  highlight. `RenderEffect` blur is deliberately banned — a full-screen
  `RenderEffect` once crashed on API 31+ and was removed (see `HANDOVER` §7).
- **Self-drawn progress bar**: Material's `Slider` forces a 48 dp touch target
  that steals taps from the play button; `NeonProgress` hand-rolls the gesture
  handling with `awaitEachGesture` so press / drag / release never fight.
- **Breathing play button**: 76 dp gradient sphere with three glow rings whose
  alpha tracks playback state; `NeonMotion` honours the system "remove
  animations" accessibility setting.
- **Motion with purpose**: swipe left/right on the stage to change track;
  the currently playing row in the queue gets a rail + pulsing dot + cover halo;
  the queue scrolls to the current track on open.
- **Dual-theme**: the aurora is dimmed to ~7 % on light themes. A dark backdrop
  under near-black `onSurface` text would be unreadable; `NeonSurface.kt` holds
  both specs.
- Vinyl-style rotating album art (`RotatingArtwork`) that spins only while playing,
  with a tone arm that drops when playback starts.

### Playback behaviour (V1.0.1)
- **Shuffle draws once per track change**: the next track is a uniform pick over
  every queue position except the current one. The draw is remembered only for as
  long as the track does not change, so one decision cannot be answered twice with
  two different tracks — that was what stalled the player in earlier versions,
  and what made media3 and the buttons disagree before V1.0.1. The KDoc with the
  full history of this rule lives in `player/RandomShuffle.kt`.
- **Both directions are random.** "Previous" in shuffle mode is the *same* draw as
  "next", with one extra exclusion: it also avoids the track that just played, so
  pressing previous twice cannot bounce between two tracks. It is deliberately
  **not** a walk back through what the user heard — there is no play-history stack
  anymore. `RandomShuffleOrder` hands media3 the same `RandomShuffleState`, so the
  notification, lockscreen, Bluetooth and in-app buttons answer identically.
- **A hardware "previous" never restarts the current track.**
  `PlaybackService` sets `setMaxSeekToPreviousPositionMs(Long.MAX_VALUE)`, so the
  media3 rule "past 3 s, previous means restart" cannot fire. The older failure —
  `getPreviousIndex()` returning `-1`, which media3 reads as "no previous item" and
  answers by seeking to 0 — is gone too, because the draw never returns `-1` for a
  queue with more than one entry.
- **Silent-stall watchdog**: if the position freezes for 8 s while the player
  still claims to be playing, the player is asked to retry; a second freeze at the
  same spot rebuilds the media source from the frozen position. Stuck buffering
  is timed separately (20 s).
- **Equalizer curve survives restarts and session rebuilds**: Android rebuilds the
  audio effect empty on every new audio session, so the curve is stored as a
  preference (`AppSettings.eq`) and replayed after each `attach()` — including the
  ones that happen mid-session when playback moves to the next track.
- **Back means up**: every full-screen page intercepts system Back and
  returns to the shell instead of closing the app.

### Play statistics
- A **播放统计** screen (top bar, next to the equalizer) answering *what did I
  actually listen to?* over 今日 / 本周 / 全部.
- Backed by a dedicated `play_event` table (Room v4, `MIGRATION_3_4`) that keeps
  one row per play — deliberately not `play_history`, which is keyed by track
  and therefore cannot hold a count. Events older than a year are pruned on
  each write.
- Aggregation lives in `data/stats/PlayStats.kt`, a pure object with no Android
  or database dependency, pinned to a fixed clock and `Asia/Shanghai`.
- Turned off by its own switch: Settings → 播放 → **记录播放统计**. It is
  independent of 播放时记录历史, which only governs the 最近播放 list — the two
  used to be wired together, so hiding 最近播放 silently froze the statistics.

### Crash reporting
- `CrashLogger` chains the default uncaught-exception handler and writes the
  trace to both `filesDir/last_crash.txt` and
  `getExternalFilesDir(null)/last_crash.txt`.
- `ui/CrashReportDialog.kt` shows it on next launch with copy/clear.

### Online sources (unchanged)
- Bundled LX search providers (Kw / Kugou / Migu verified live) inside WebView #1.
- User custom-source scripts drive playback URL resolution inside WebView #2.

---

## Tests

```bash
./gradlew :app:testDebugUnitTest
```

235 unit tests across 26 classes, all green (run `./gradlew :app:testDebugUnitTest`):

| Test class | Cases | Covers |
|---|---:|---|
| `lyric/EmbeddedLyricsTest` | 18 | ID3v2 USLT/TXXX, FLAC VorbisComment, Ogg/Opus, MP4 `©lyr`, GBK/UTF-8/BOM |
| `data/stats/PlayStatsTest` | 17 | day/week bucketing, ranking, timezone (pinned `Asia/Shanghai`) |
| `player/RandomShuffleStateTest` | 18 | **V1.0.1**: draw contract — next excludes the current track, prev excludes it too and additionally skips the track that just played, a draw is remembered until a track starts, `onTrackStarted` clears both, a 2-track queue can still step both ways |
| `local/LocalLibraryTest` | 15 | search, sort orders, album/artist grouping, multi-singer split |
| `player/RandomShuffleOrderTest` | 11 | media3 `ShuffleOrder` contract under **V1.0.1** draw semantics — previous always answers a legal in-range slot different from the current one, clones keep drawing from the same state |
| `player/QueueMirrorTest` | 17 | **V1.0.1**: the mirrored playlist's five steps run in one fixed order, `move`/`remove` drop the installed order *after* the player mutation, `replace` refuses a queue it cannot mirror |
| `player/QueueLogicTest` | 12 | queue move/remove/next/prev bookkeeping |
| `lyric/LrcParserTest` | 8 | timestamp parsing, multi-tag lines, translation detection |
| `player/ShuffleStallProbe` | 9 | **V1.0.1**: 500-step app walk + 500-step system walk never stall, the two paths always pick the same track, every draw from one track can reach all N-1 alternatives |
| `player/StallWatchdogTest` | 8 | silent-stall detection — retry budget, re-buffer second stage, buffering-only stall, reset on progress (fake clock); a buffer does not spend the freeze budget |
| `ui/neon/NeonTokensTest` | 8 | aurora peak keeps body text ≥4.5:1 on all 8 palettes, gradient stops, motion cadence |
| `ui/theme/ThemePaletteTest` | 8 | 8 colour palettes, light/dark contrast |
| `data/EqProfileTest` | 7 | band encode/decode round-trip, corrupt entries dropped not thrown |
| `player/RandomShuffleCloneTest` | 7 | clone protocol: candidates never disturb the live order |
| `local/LocalLibraryMergeTest` | 6 | cache merge / prune semantics |
| `player/RandomShuffleStateStalenessTest` | 6 | memo never goes out of range after the queue shrinks |
| `ui/theme/OledTokensTest` | 6 | OLED token contrast ratios |
| `data/SettingsRoundTripTest` | 11 | every preference key is written and read back through the *same* key constant — the "equalizer does not survive a restart" class of bug cannot hide behind a default-only assertion; the V1.0.2 update token is checked the same way |
| `net/UpdateCheckerTest` | 11 | **V1.0.2**: release-tag version comparison (leading `v`, numeric segments, prerelease suffix ordering) and GitHub release JSON parsing (first `.apk` asset, title fallback, graceful degradation on missing fields) — no network in unit tests |
| `data/SettingsTest` | 5 | settings defaults (incl. flat EQ profile) / enum values |
| `local/SafMusicScannerTest` | 5 | audio extension / mime detection |
| `player/QueueIndexTest` | 5 | a queue slot is only real if the *id* at that slot is the track playing — the online-queue `playIndex(3)` → reported-as-0 defect |
| `player/RandomShuffleTest` | 6 | **V1.0.1**: `pickOther` always lands in range and never returns an excluded slot; uniformity over 600 seeds |
| `local/LocalTrackMapperTest` | 4 | entity ↔ MusicInfo round-trip |
| `ui/components/PlayerSkinTest` | 4 | player skin parsing / fallbacks |
| `data/stats/RecordPolicyTest` | 3 | 最近播放 and 播放统计 are independent switches |

The six `RandomShuffle*` classes plus `ShuffleStallProbe` encode every shuffle bug
this project has ever hit — read them before touching that code. [HANDOVER.md](HANDOVER.md) §5
explains the root causes behind them.

`NeonTokensTest` is the same idea for the visual layer: it recomputes WCAG
contrast over the *brightest possible* aurora composite on every palette, so a
future "make it pop more" tweak cannot silently drop body text below AA.

---

## V1.0.3 — Silent update check on startup, and a home-screen banner (`versionCode = 4`)

V1.0.2 put the check behind a button — you had to know to look. V1.0.3 closes
the loop: the app asks once a day on its own, and a new release announces
itself on the home screen.

### What was added

- **Silent startup check** (`MainViewModel.maybeCheckForUpdatesOnStartup`) —
  runs once per 24 h, throttled by a persisted timestamp
  (`last_update_check_at`). It shares the result flow with the manual check but
  only *disturbs* you when it finds something: "already up to date" and
  failures both fall back to `IDLE`, so the settings subtitle never shows the
  result of a check you did not ask for, and an offline phone never sees a
  spurious error dialog.
- **Home-screen banner** — when the state lands on `AVAILABLE`, a glass banner
  sits above the tab row: "发现新版本 vX.Y.Z" plus the release title, with the
  brand accent bar and a pulsing dot. Tapping it opens the same release-notes
  dialog the settings page uses (`UpdateDialog` was promoted to `internal`).
- **CI + automated releases** (`.github/workflows/ci.yml`) — every push runs
  the unit-test suite and counts the results; pushing a `v*` tag builds the
  versioned APK and uploads it to a GitHub Release with generated notes. The
  release notes *are* the update announcement the app fetches — publishing a
  release and announcing it are now the same action.
- **Dead-code sweep** — the never-read `allowShowUpdateAlert` field (both
  `UserApiInfo` and `UserApiEntity`), the superseded design docs moved to
  `docs/archive/`, and the repo helper scripts (`_cnt.py`, `_per_class.py`,
  `_probe_stall.ps1`) rewritten to be path-independent and BOM-free.

### Verification

`build/apk/jijian-music-1.0.3-vc4-debug.apk`, `versionCode = 4`,
`versionName = 1.0.3`, confirmed with `aapt2 dump badging`. 235 unit tests
across 26 classes, 0 failures.

---

## V1.0.3 — Silent update checks, a home-screen banner, and CI (`versionCode = 4`)

### What was added

- **Startup check with a 24h throttle.** `MainViewModel.maybeCheckForUpdatesOnStartup()`
  runs the same fetch as the settings button, but only once a day
  (`last_update_check_at` persisted in `SettingsStore`, 28th key). A silent check
  never disturbs the UI unless it genuinely finds a newer version: "up to date"
  and "failed" both fall back to `IDLE`, because the app may run entirely offline
  and the settings subtitle should not show a result the user never asked for.
- **Home-screen banner.** When the silent check does find a new version, a glass
  banner appears above the tab row ("发现新版本 v1.0.3 …", pulsing brand dot).
  Tapping it opens the same release-notes dialog the settings page uses
  (`UpdateDialog` moved from `private` to `internal` for reuse).
- **GitHub Actions CI** (`.github/workflows/ci.yml`): every push runs the unit
  tests and prints the `_cnt.py` tally; a `v*` tag builds the versioned APK and
  uploads it to the GitHub Release — releases and the in-app announcements now
  come from the same place the app checks.

### What was removed

- `allowShowUpdateAlert` — a dead flag on `UserApiInfo`/`UserApiEntity` with zero
  readers (a leftover from the pre-1.0 announcement screen).
- `docs/adr/0001`, `docs/adr/0002`, `docs/v6.1-refactor-plan.md` moved to
  `docs/archive/` — they describe the shuffle-cycle semantics that V1.0.1
  replaced. `_cnt.py` / `_per_class.py` / `_probe_stall.ps1` lost their BOM and
  hard-coded `D:\demo` paths.

### Verification

`build/apk/jijian-music-1.0.3-vc4-debug.apk`, `versionCode = 4`, `versionName =
1.0.3`, confirmed with `aapt2 dump badging`. 235 unit tests across 26 classes,
0 failures.

---

## V1.0.3 — Silent update check, a home banner, and CI (`versionCode = 4`)

The V1.0.2 checker only ran when you opened the settings page. Now the app
checks for itself, and dead weight from earlier eras is gone.

### What was added

- **Startup check, throttled to once per 24 h** — `MainViewModel.maybeCheckForUpdatesOnStartup()`
  runs on launch, persists its last-run timestamp in a new `last_update_check_at`
  preference, and only reports when it genuinely finds a newer release: a failed
  or up-to-date silent check folds back to `IDLE` so the settings page never
  shows a result the user never asked for. The manual check in settings stays
  unthrottled.
- **Home banner** — when the silent check finds a new version, a glass banner
  sits above the tab row ("发现新版本 v1.0.x"), reusing the shell's glass
  language and the palette seed colour. Tapping it opens the same release-notes
  dialog the settings page uses (`UpdateDialog` is now `internal` for that).
- **GitHub Actions** (`.github/workflows/ci.yml`) — every push runs the full
  unit suite (`_cnt.py` summary step included); a `v*` tag builds the versioned
  APK and attaches it to a GitHub Release. Releases are now "publish a tag",
  not a manual upload chain — which matters because the V1.0.2 checker reads
  release notes as its announcement channel.
- **Dead-code pass** — `allowShowUpdateAlert` (a leftover flag with zero
  readers in `LxModels.kt` / `AppDatabase.kt`) is gone; the three pre-V1.0
  design documents moved to `docs/archive/` (their semantics were superseded by
  the V1.0.1 shuffle rewrite); `_cnt.py` / `_per_class.py` / `_probe_stall.ps1`
  lost their BOM and hardcoded absolute paths; `ShuffleStallProbe` no longer
  points at the deleted `_dex/` scratch directory.

### Deliberately kept

`recordHistory` looked dead but is not: `MainViewModel` reads it to gate
`repo.markPlayed` (最近播放). It stays.

### Verification

`build/apk/jijian-music-1.0.3-vc4-debug.apk` (23,432,086 B), `versionCode = 4`,
`versionName = 1.0.3`, confirmed with `aapt2 dump badging`. 235 unit tests
across 26 classes, 0 failures.

---

## V1.0.2 — Check for updates from the settings page (`versionCode = 3`)

The app could tell you its own version number, but not whether a newer build
existed. The settings page now asks GitHub for the latest release and shows its
release notes in place.

### What was added

- `net/UpdateChecker.kt` — one HTTP call to
  `https://api.github.com/repos/<repo>/releases/latest` through the existing
  `HttpBridge`. It parses `tag_name`, `name`, `body`, `html_url` and the first
  `.apk` asset into an `UpdateInfo`. Version comparison strips a leading `v`,
  compares numeric segments as numbers (so `1.0.10` beats `1.0.9`), and treats a
  release with no suffix as newer than a prerelease carrying the same number.
- `SettingsStore` gains one key, `update_token`, together with its read/write
  pair (`writeUpdateToken` / `toSettings`) and tests that walk both sides — the
  same treatment the equalizer profile got after a "does not survive a restart"
  bug turned out to be a mistyped key name that compiled fine and silently fell
  back to the default forever.
- `MainViewModel` owns a small `UpdateCheckState` flow
  (`IDLE / CHECKING / UP_TO_DATE / AVAILABLE / FAILED`) and a `checkForUpdates()`
  that runs the fetch off the main thread.
- The settings page has a **更新** section: a "检查更新" row and an optional
  **访问令牌** field. The result opens a dialog showing the current version, the
  latest version, the APK size, and the full release notes.

### Why there is a token field

The GitHub repository is private. An anonymous `releases/latest` request on a
private repo answers `404`, which is indistinguishable from "this repo has no
releases", so the checker reports 404 as "the update source is unreachable, or
the repo is private" and offers the token field instead of a dead end. With a
`repo`-scoped personal access token the same request succeeds. A public mirror
needs no token.

### Verification

`build/apk/jijian-music-1.0.2-vc3-debug.apk` (23,432,086 B), `versionCode = 3`,
`versionName = 1.0.2`, confirmed with `aapt2 dump badging`. 235 unit tests across
26 classes, 0 failures — 13 of them new (`UpdateCheckerTest`, plus two token
round-trip cases in `SettingsRoundTripTest`).

---

## V1.0.1 — Both shuffle directions are draws, and prev never restarts (`versionCode = 2`)

The V1.0 fixes each solved one half of the problem and left the other half in place.

### "Next is random, previous is not"

`RandomShuffleState.previousIndex()` was still walking the shuffle cycle — it answered
"the slot before this one *in the order*", which is a track this pass has not reached
yet. It read as a random jump *forward*, and the app's own prev button and the media
session's prev button agreed only by accident. `prev()` had the same shape: it popped
`PlayHistory`, so the first press of a session had an empty stack and did nothing useful.

Both directions are now **draws**: a uniform pick over every queue position except the
current one, made once per track change and remembered until the next track actually
starts. `previousIndex()` adds one exclusion — it also skips the track that just played,
so pressing prev twice cannot bounce between two tracks. `PlayHistory` is gone; so is the
`previousOf` hook on `RandomShuffleOrder`, because there is nothing left to hook.

The cycle itself is gone with it. `cycleFrom()`, `advanceCycle()`, `cyclesDrawn` and the
`generation` counter that existed to notice a redraw all disappear; what remains is
`nextPick` / `previousPick` and one `onTrackStarted()` entry point that clears them.

### A headset "previous" restarts the current track, even after the V1.0 fix

`setMaxSeekToPreviousPositionMs(Long.MAX_VALUE)` closed the "past 3 seconds, prev means
restart" path, but media3 has a *second* way to reach `seekToCurrentItem(0)`:

```java
// BasePlayer.seekToPrevious()
if (hasPreviousMediaItem() && pos <= maxSeekToPrevious) seekToPreviousMediaItemInternal(7)
else seekToCurrentItem(0L, 7)
```

`hasPreviousMediaItem()` asks `getPreviousMediaItemIndex()`, which asks the installed
`ShuffleOrder`. With the old `previousOf` hook, an empty history returned `-1`, media3
read that as "there is no previous item", and the `else` branch replayed the track from
zero. That is why the very first headset press always restarted.

The fix is not to special-case the empty stack. A draw never returns `-1` when the queue
holds more than one entry, so the `else` branch cannot be reached in shuffle mode. The
threshold override is kept as a second line of defence for sequential mode, where a real
`-1` at index 0 is still possible.

### Two seams, so this cannot silently fork again

`player/ShuffleSink.kt` (install / drop the order) and `player/QueueMirror.kt` (the whole
mirrored-playlist lifecycle, five steps in one fixed order) replace eight copy-pasted
blocks in `PlayerController`. `QueueMirrorTest` locks the ordering — the historical bug where
`moveInQueue` installed an order describing the *pre-move* list is now a failing test
rather than a code review note.

### Verification

`./gradlew :app:assembleDebug :app:versionedApk :app:testDebugUnitTest` →
**222 tests / 25 classes / 0 failures**; APK
`build/apk/jijian-music-1.0.1-vc2-debug.apk` (23,399,318 B), `versionCode = 2`,
`versionName = 1.0.1` (confirmed with `aapt2 dump badging`).

On the emulator, with a 6-track queue and shuffle on: ten presses of the in-app next
button, six presses of the in-app prev button, five `KEYCODE_MEDIA_PREVIOUS` and five
`KEYCODE_MEDIA_NEXT` events all moved to a different track every time, with no repeat of
the track that had just played and no fixed overall order.

---

## V1.0 — Renumbered for release, with two playback fixes (`versionCode = 1`)

The version number stops counting iterations and starts counting releases: `6.2.0 / vc26`
becomes **`1.0.0` / `vc1`**. Alongside the renumber, two long-standing playback bugs.

> **Upgrade note**: `versionCode` goes *down* from 26 to 1, so Android treats this as a
> downgrade. `adb install -d` is required, and an in-place upgrade by an end user is
> refused by the system — it has to be uninstalled and reinstalled.

### Next now actually advances, once per press

`RandomShuffleState.nextIndex()` used to redraw the whole cycle **inside the query** when
it reached the last slot of the order:

```kotlin
if (pos < size - 1) return o[pos + 1]
return drawn(RandomShuffle.cycleFrom(size, current, random))[1]   // old
```

`drawn()` bumps `cyclesDrawn` and `generation` unconditionally, so it was not idempotent —
despite the KDoc claiming it was. media3 asks `getNextIndex` several times per decision
(`hasNext`, the notification button target, `MediaPeriodQueue`), and the app's own buttons
share the same `RandomShuffleState`. Any query could therefore hand back a *different*
order mid-decision, which truncated the cycle and left the user pressing "next"
several times before anything moved.

`nextIndex()` is now a pure query — it draws only when no order exists yet, and the
last-slot case is a defensive wrap to `o[0]`. Cycle advancement moved to a new
`advanceCycle(size, current)`, the **single** mutating entry point, called only when a
track really started (`onMediaItemTransition` and `playIndex`). It is idempotent: after a
redraw the current track sits at slot 0, so calling it twice changes nothing.

### A headset "previous" goes back one track, never restarts this one

`BasePlayer.seekToPrevious()` is not `seekToPreviousMediaItem()`. It first compares
`getCurrentPosition()` against `getMaxSeekToPreviousPosition()` (media3's default is
3000 ms) and, past that threshold, calls `seekToCurrentItem(0, …)` — i.e. it replays the
current track from the top. That default exists for podcast "skip back a few seconds"
UIs and is exactly wrong for a music player. `PlaybackService` now builds the player with
`setMaxSeekToPreviousPositionMs(Long.MAX_VALUE)`, making the threshold unreachable so
every press takes the `seekToPreviousMediaItemInternal` branch. The on-screen prev button
was never affected: it calls `PlayerController.prev()` directly and never goes through
`BasePlayer.seekToPrevious()`.

### Verification

`./gradlew :app:assembleDebug :app:versionedApk :app:testDebugUnitTest` →
**217 tests / 25 classes / 0 failures**; APK
`build/apk/jijian-music-1.0.0-vc1-debug.apk` (23,513,794 B), `versionCode = 1`,
`versionName = 1.0.0` (confirmed with `aapt2 dump badging`).
