# 极简music (JiJianMusic) for Android

`applicationId = com.lxmusic.android` · 当前版本 **1.0.1** (`versionCode = 2`)

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

> 本文件后半部分的版本历史是**时间线留档**：早期章节提到的 `ShuffleBag` 洗牌袋、
> `Changelog.kt` / `AnnouncementScreen` 等文件均已被删除，凡标注「历史章节」的
> 段落只用于追溯，不代表当前行为。当前行为以 `HANDOVER.md` 与代码为准。

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
│   ├── settings/EqProfile.kt    # V6.1: persistable tone curve (bands encode/decode)
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
├── net/HttpBridge.kt            # OkHttp wrapper
├── player/
│   ├── PlaybackService.kt       # MediaSessionService + ExoPlayer
│   ├── PlayerController.kt      # URL resolution + playback state
│   ├── PlayerHolder.kt          # the ExoPlayer instance (audioSessionId, ShuffleOrder)
│   ├── RandomShuffle.kt         # V1.0.1: per-draw shuffle policy (authoritative KDoc)
│   ├── RandomShuffleOrder.kt    # media3 ShuffleOrder adapter
│   ├── ShuffleSink.kt           # V1.1: install/drop seam for the ShuffleOrder
│   ├── QueueMirror.kt           # V1.1: the one place that mirrors the Queue
│   ├── QueueIndex.kt            # V6.2: media id -> queue slot resolution
│   ├── StallWatchdog.kt         # V6.1: silent-stall detection (pure Kotlin)
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
│   ├── neon/                    # V6.0 design system: tokens, aurora, glass, controls, shell, NeonScreen scaffold
│   ├── screens/                 # Local, Library, Online, NowPlaying, Queue, Equalizer, Stats, FolderManager, Settings
│   └── theme/                   # OledTokens / ColorPalettes / Theme
```

> `data/Changelog.kt` and `ui/screens/AnnouncementScreen.kt` no longer exist.
> `player/ShuffleBag.kt` and `player/BagShuffleOrder.kt` (the Fisher-Yates bag)
> were **deleted** in v5.3.1 and replaced by `RandomShuffle` — see the v5.3.1
> section below for why. Any doc still referring to them is stale.

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

### Cool UI — "Neon Vinyl" (V6.0)
- **Aurora backdrop**: three radial-gradient lights drift on elliptical orbits
  (11 s / 17 s / 23 s, coprime so they never line up). On the now-playing screen
  the palette is **extracted from the current artwork**, so the room changes
  colour with the song.
- **Glass, not blur**: panels are translucent white + 1 dp stroke + a top
  highlight. `RenderEffect` blur is deliberately banned — a full-screen
  `RenderEffect` crashed on API 31+ in V5.5.2 and was removed (see `HANDOVER` §8).
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
  two different tracks — that was what stalled the player before V5.3.6, and what
  made media3 and the buttons disagree before V1.0.1. The KDoc with the full
  history of this rule lives in `player/RandomShuffle.kt`.
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
- **Back means up** (V6.1.1): every full-screen page intercepts system Back and
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
  independent of 播放时记录历史, which only governs the 最近播放 list — before
  V5.3.6 the two were wired together and hiding 最近播放 silently froze the
  statistics.

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

222 unit tests across 25 classes, all green (run `./gradlew :app:testDebugUnitTest`):

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
| `player/StallWatchdogTest` | 8 | **V6.1**: silent-stall detection — retry budget, re-buffer second stage, buffering-only stall, reset on progress (fake clock); **V6.2** a buffer does not spend the freeze budget |
| `ui/neon/NeonTokensTest` | 8 | **V6.0**: aurora peak keeps body text ≥4.5:1 on all 8 palettes, gradient stops, motion cadence |
| `ui/theme/ThemePaletteTest` | 8 | 8 colour palettes, light/dark contrast |
| `data/EqProfileTest` | 7 | **V6.1**: band encode/decode round-trip, corrupt entries dropped not thrown |
| `player/RandomShuffleCloneTest` | 7 | clone protocol: candidates never disturb the live order |
| `local/LocalLibraryMergeTest` | 6 | cache merge / prune semantics |
| `player/RandomShuffleStateStalenessTest` | 6 | memo never goes out of range after the queue shrinks |
| `ui/theme/OledTokensTest` | 6 | OLED token contrast ratios |
| `data/SettingsRoundTripTest` | 9 | **V6.2**: every preference key is written and read back through the *same* key constant — the "equalizer does not survive a restart" class of bug cannot hide behind a default-only assertion |
| `data/SettingsTest` | 5 | settings defaults (incl. **V6.1** flat EQ profile) / enum values |
| `local/SafMusicScannerTest` | 5 | audio extension / mime detection |
| `player/QueueIndexTest` | 5 | **V6.2**: a queue slot is only real if the *id* at that slot is the track playing — the online-queue `playIndex(3)` → reported-as-0 defect |
| `player/RandomShuffleTest` | 6 | **V1.0.1**: `pickOther` always lands in range and never returns an excluded slot; uniformity over 600 seeds |
| `local/LocalTrackMapperTest` | 4 | entity ↔ MusicInfo round-trip |
| `ui/components/PlayerSkinTest` | 4 | player skin parsing / fallbacks |
| `data/stats/RecordPolicyTest` | 3 | V5.3.6: 最近播放 and 播放统计 are independent switches |

The six `RandomShuffle*` classes plus `ShuffleStallProbe` encode every shuffle bug
this project has ever hit — read them before touching that code. [HANDOVER.md](HANDOVER.md) §5
explains the root causes behind them.

`NeonTokensTest` is the same idea for the visual layer: it recomputes WCAG
contrast over the *brightest possible* aurora composite on every palette, so a
future "make it pop more" tweak cannot silently drop body text below AA.

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
blocks in `PlayerController`. `QueueMirrorTest` locks the ordering — the V6.1 bug where
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

---

## v6.2.0 — Review findings: one honest previous, one shared history (`versionCode = 26`)

Patch release from a `code-review` (Standards + Spec axes) and a `diagnosing-bugs`
pass over v6.1.x. No new features; every change removes a way the player could
quietly disagree with itself.

### The notification's "previous" now walks the history too

The app's own prev button used `PlayHistory`, but the media session asked
`ShuffleOrder.getPreviousIndex`, which still answered "the slot before this one in
the current cycle" — exactly the behaviour ADR 0001 says is wrong (that track has
not been heard yet, so it feels like a random jump forward). `RandomShuffleOrder`
takes an optional `previousOf: ((Int) -> Int)?` hook; `PlayerController` installs one
that maps a queue index to `PlayHistory.confirmBack()` and falls back to `-1` (media3
reads `-1` as "restart the current track") when the stack is empty. Lock screen,
notification, Bluetooth and car head-unit prev are now the same gesture as the one
on screen.

`PlayHistory` also stopped keeping one `expected` id and keeps a set of them: `prev()`
lands asynchronously, so two fast taps used to overwrite each other's claim and the
back stack went wrong.

### A queue slot is only real if the id at that slot is playing

`onMediaItemTransition` used to translate the player's reported index straight into a
queue slot. In single-track mode (`playIndex`, used for restored and online tracks)
ExoPlayer always reports index 0, so playing entry 3 of an online queue announced
entry 0: wrong title, wrong history entry, wrong "current" highlight. The new
`player/QueueIndex.kt` resolves the slot by **media id first**, falls back to the
reported position, then gives up (-1) so the callback is ignored instead of lying.

### The shuffle order cannot fork behind the app's back

media3 clones the `ShuffleOrder` on every playlist mutation, and a clone that redraws
its own cycle used to leave the live instance's `generation` untouched — the
`installShuffleOrder()` version guard then saw "nothing changed" while the player was
walking a different order. `generation` is now a small shared
`ShuffleGeneration` object that clones hold by reference, so a redraw on any copy
bumps the counter everyone reads. `firstIndex`/`lastIndex` also stopped drawing an
unanchored order (media3 asks for them while building the period queue, which used to
throw away a half-finished cycle).

### A paused player is never "stuck buffering"

The progress loop fed `playing = c.isPlaying` into `StallWatchdog`, but the buffering
branch never looks at it — so a track the user paused while the network buffer was
still filling escalated to `REBUFFER` after 20 s, and both handlers end in `play()`.
The loop now checks `playWhenReady` first and resets the watchdog while paused.

### `next()`/`prev()` mirror best-effort instead of returning silently

Both called `ensureMirrorPlaylist()` and `return`ed when it failed (any queue holding
a track that cannot be resolved synchronously, i.e. every online queue). The buttons
did nothing at all. `PlayerController.next()` / `prev()` now take a `mirror: Boolean`
flag: the in-app buttons pass `false` and use the `playIndex` single-track path, the
media session keeps `true` and gets the playlist. Mirror failures log instead of
swallowing the tap.

### Settings keys are tested as a round trip

Every preference is written and read through the same `KEY_*` constant, so a typo on
either side compiles, never throws, and just returns the default forever — which is
the shape of the original "the equalizer does not save" report. `SettingsStore` now
exposes `readSettings(Preferences)` and `writeEq(MutablePreferences, EqProfile)` to the
test source set, and `data/SettingsRoundTripTest` (9 cases) drives every EQ field plus
the enum-fallback and last-played-pointer paths through them.

### Also

- Equalizer "重置" opens a `ConfirmSheet` — it used to wipe a persisted curve with one
  unconfirmed tap, with no undo.
- `MusicInfo.isLocal` / `SOURCE_LOCAL` replaces eight `source == "local"` string
  comparisons, and the local-path→`Uri` dance exists once (`localFileUri`) instead of
  three times.
- `PlayerController`'s stale KDoc (it still described "independent uniform sample per
  draw", the pre-V6.1 behaviour) was rewritten to the cycle contract.
- Three near-identical 38 dp glass buttons collapsed into one `NeonGlassAction`
  (`hint` is now nullable, so a caller that already showed its own toast can say so).
- `NeonMotion`'s two infinite-animation helpers share one phase builder; the Now
  Playing magic numbers moved into `NeonTokens`.
- `PlayerController.onShuffleOrderRejected` was removed — it had zero subscribers in the
  app and the tests, so both rejection paths now go to logcat (`Log.w` / `Log.e` on the
  same tag) instead of a hook that only looked like it was being watched.

### Verification

`./gradlew :app:assembleDebug :app:versionedApk :app:testDebugUnitTest` →
**214 tests / 25 classes / 0 failures**; APK
`build/apk/jijian-music-6.2.0-vc26-debug.apk` (23,399,318 B), `versionCode = 26`,
`versionName = 6.2.0`.

---

## v6.1.1 — Back means up, EQ survives the session (`versionCode = 25`)

Patch release from a code review of v6.1.0. Five defects, all in behaviour, none in layout.

### Back now means "up"

Every full-screen page (`settings` / `folderManager` / `stats` / `queue` / `equalizer` /
`nowPlaying`) is an early-return branch in `LxApp`, so Compose had **no back stack** and a
`BackHandler` was registered nowhere. Pressing system Back on the equalizer fell straight
through to `finish()` and closed the app. Each branch now wraps its content in
`BackHandler { <same callback as the top bar's X> }`, so the gesture and the visible
control cannot drift apart. Verified on the emulator: equalizer → Back → shell, settings →
Back → shell (`_shots/back_probe2.ps1`).

### The equalizer curve survives an audio-session rebuild

`EqualizerController.attach()` rebuilds the system effect objects for every new audio
session and force-flattens them. The persisted profile was read back only at startup
(`MainViewModel` → `setProfile`), so the moment playback moved to another track — a new
session — the user's curve was replaced by the **boot-time** profile. The data on disk was
correct; nothing read it back.

`attach()` already re-applied `restoreProfile`; the gap was that `restoreProfile` went
stale. Every mutator (`setEnabled` / `setBandLevel` / `applyPreset` / `setBassStrength` /
`setVirtualizerStrength` / `setLoudnessGainMb` / `reset`) now ends with `rememberCurrent()`,
which snapshots the live state into `restoreProfile`. Restoring also has an ordering
constraint — bands and strengths first, `enabled` **last** — because
`setBassStrength` is guarded by `if (strength > 0 && state.enabled)` and would otherwise be
swallowed.

### A missed back-step no longer freezes history

`PlayHistory.confirmBack()` pops the target and remembers it as `expected`. If the player
landed on some *other* track (unresolvable URL, decode failure), the old code cleared
`expected` and then **skipped** recording the transition, so after one failure history
stopped growing for the rest of the session. The mismatch branch now records the transition
it actually saw. Regression: `PlayHistoryTest.aTransitionThatMissesTheExpectedTargetIsStillRecorded`.

### Smaller

- `next()` / `prev()` no longer install a shuffle order against a stale mirrored playlist;
  the `forgetInstalledOrder()` call sites now cover the whole-table rebuilds.
- Dead `lastState` field removed from `EqualizerController`.

### Verification

- **196 项 / 23 类 / 0 失败**（+9：`PlayHistoryTest`）。
- 产物 `build/apk/jijian-music-6.1.1-vc25-debug.apk`。
- Emulator: `adb install -r`, Back navigation, EQ screen reachable.

---

## v6.1.0 — Shuffle cycles, play history, silent-stall watchdog (`versionCode = 24`)

Behavioural release: the player engine, not the visuals. Three user reports drove it —
「切模式卡顿」/「有些歌播到 1 分 50 秒会卡住」/「按键选择后应该保存，下次打开 apk 一样生效」。
Plan and acceptance record: [docs/v6.1-refactor-plan.md](docs/v6.1-refactor-plan.md).

### 随机播放：一条 cycle 播完才重洗

Before, `onMediaItemTransition` called `shuffleState.invalidate()` on *every* track
change, throwing away the whole order; `next()`/`prev()` separately called
`redrawNext`/`redrawPrevious`, drawing a fresh order per press. Both paths
independently sampled the remaining tracks — i.e. uniform random over the other
N−1, with repeats legal. Over a 20-track queue the chance of hearing a repeat
within the first 10 tracks was ≈89%.

Now one order (**shuffle cycle**) survives track changes. A cycle plays each
track exactly once; only when it runs out does `RandomShuffle.cycleFrom(size, anchor)`
deal a new one, anchored on the track that just finished so the seam never replays
it. Buttons, notification, Bluetooth, lock screen and automatic advance all walk
the *same* cycle. Asking for `previous` at the cycle start returns `-1`, which
ExoPlayer reads as "restart the current track".

### 上一首 = 播放历史回退

New `player/PlayHistory.kt` (bounded, 200 entries). `prev()` pops the last track
actually heard; only an empty history falls back to a random draw. The class avoids
the classic "stack + rewinding boolean" trap: `confirmBack()` records an `expected`
id, and `onTrackChanged()` refrains from pushing only when that id matches — one
boolean would have to be reset on every playback path, and a single missed reset
eats the stack.

### 切换模式卡顿：不再重装同一个 shuffle order

`ExoPlayerImpl.setShuffleOrder` internally calls `createMaskingTimeline()`, which
allocates a **new** `PlaylistTimeline`; that class has no `equals` override, so
`updatePlaybackInfo` always sees a changed timeline, dispatches `onTimelineChanged`,
and the app's handler installed the order again — one toggle became a burst of
playlist rebuilds, amplified across the `MediaController` binder boundary.
`installShuffleOrder()` now remembers the installed order's
`RandomShuffleState.version` plus the playlist length and returns early when
neither moved; full rebuilds (`setMediaItems` / `clearMediaItems`) call
`forgetInstalledOrder()` first.

### 1:50 卡住：静默停顿看门狗

Symptom: position freezes while `isPlaying` stays true, no `onPlayerError`, so
nothing in the app reacted. New `player/StallWatchdog.kt` (pure Kotlin, no Android
types) is fed by the existing 300 ms progress tick:

| condition | reaction |
|---|---|
| playing, position unmoved 8 s | `RETRY` — `prepare()` + seek back + `play()` |
| same spot frozen another 8 s | `REBUFFER` — re-resolve the URL (stream URLs expire) or reopen the file |
| buffering and not playing for 20 s | `RETRY`, tracked separately (the `isPlaying` branch can never see it) |
| any position movement | counters reset |

### 均衡器设置持久化

Android's `Equalizer` is bound to an audio session, and `EqualizerController.attach()`
releases and rebuilds the effects on every new session — unavoidably flat again, so
tuned curves were lost on each launch. The curve is now kept app-side:
`data/settings/EqProfile.kt` (enabled / preset / per-band levels / bass /
virtualizer / loudness) + six `KEY_EQ_*` preferences + `setEq()`; the controller
gained `setProfile()` / `snapshot()` / `applyProfile()`, and `attach()` replays the
stored profile once the new session is live. `applyProfile()` sets `enabled`
**last**, because `setBassStrength` and friends are guarded by
`if (strength > 0 && state.enabled)` and would otherwise be swallowed.

### 文档

New [GLOSSARY.md](GLOSSARY.md) (queue / mirror playlist / shuffle cycle / play
history / silent stall), [docs/adr/0001](docs/adr/0001-shuffle-cycle-and-play-history.md)
and [docs/adr/0002](docs/adr/0002-detect-silent-stalls.md).
The old README claim that "`repeat = OFF` stops at the end of the list" only holds
for the sequential path; under shuffle a cycle always has a next track.

### 验证

- 全量 **187 项 / 22 类 / 0 失败**（新增 `StallWatchdogTest` 7 项、`EqProfileTest` 7 项，
  `ShuffleStallProbe` 从 5 涨到 8，`RandomShuffleStateTest` 从 16 到 17）。
- 产物 `build/apk/jijian-music-6.1.0-vc24-debug.apk`。

---

## v6.0.0 — "Neon Vinyl" visual system (`versionCode = 23`)

纯视觉版本，播放内核、数据层、设置项**零改动**。目标是把「配色已经不错但整体不酷」
这件事补上：V5 的问题不是颜色不对，而是**没有发光、没有层级、没有转场**。

风格来自 `ui-ux-pro-max` 技能检索：

| 来源 | 命中 | 采纳的部分 |
|---|---|---|
| `--design-system "music player entertainment immersive"` | Dark Mode (OLED)，配色恰好就是 `OledTokens` 那套 | 深色底、克制的高亮面积 |
| `--domain style "aurora gradient glow immersive"` | **Aurora UI**（music platform 首选，流动渐变 8-12s，`cost:low`） | 三团极光漂移背景 |
| 同上 | **Glassmorphism**（rgba 0.06-0.13 填充 + 1px/0.14 描边，支持 dark mode） | 玻璃面板、胶囊按钮 |

### 新增：`ui/neon/`（9 个文件）

`NeonTokens.kt`（色板 + `NeonTiming` 动效时长）、`NeonMotion.kt`（动画开关 + 相位/呼吸）、
`AuroraBackdrop.kt`（极光画布）、`GlassSurface.kt`（`neonGlass` / `NeonPanel` / `neonChip`）、
`NeonProgress.kt`（自实现进度条）、`NeonControls.kt`（播放键/图标键/状态标签）、
`NeonShell.kt`（胶囊标签/渐隐分隔线/发光点/分区标题）、`NeonSurface.kt`（双主题材质规格）、
`NeonScaffold.kt`（二级页面骨架 `NeonScreen`：极光 + 56dp 玻璃顶栏 + 内容槽）。

### 改写的页面

**播放页 `NowPlayingScreen.kt`（完全重写，签名保持不变）**
极光背景（颜色来自封面取色 `ArtworkColors`，随歌变化）→ 10% 封面氛围层 → 自定义顶栏 →
`Crossfade` 皮肤切换 + **左滑切歌**（±90dp 阈值）→ 唱片三圈光晕 →
自实现进度条（品牌渐变 + 拖动发光）→ 76dp 呼吸发光播放键 + 状态标签。
`LyricView` 新增 `activeColor` 参数，当前歌词行带品牌色。

**播放队列 `QueueScreen.kt`（完全重写）**
去掉 Material `TopAppBar`（不透明色块会切开极光）→ 同构自定义顶栏；
当前行从「淡底色」升级为「左侧竖条 + 呼吸光点 + 封面光环」；
**进页面自动滚到当前曲目**（队列几十首时当前曲目常在屏幕外）。

**主界面外壳 `LxApp.kt`**
`Scaffold` 容器透明让极光透到状态栏；`TopAppBar` → 自定义 56dp 玻璃顶栏；
`TabRow` → `NeonTabRow` 胶囊标签（选中是**形状**变化，不只颜色，色觉障碍也能分辨）；
`MiniPlayer` → 玻璃条 + 封面氛围光（随播放呼吸）+ 品牌渐变进度线。

**列表与统计**
`TrackRow` 新增 `active` 参数：当前播放行加左侧 2dp 竖条 + 7% 品牌色底 + 名字变色；
`Divider` 换成 `NeonHairline`（中段实、两端渐隐）；
`StatsScreen` 的 `StatTile` 从硬编码 `OledTokens.card` 改为玻璃面板，
直方图柱子改成从下往上的品牌渐变（峰值柱更亮）。

**二级页面统一骨架**
设置 / 均衡器 / 扫描文件夹 / 播放统计四屏原本各自挂着 Material `TopAppBar`（不透明色块，
且没有背景），从主页点进去像换了一个 App。现统一收进 `NeonScreen`：
极光背景 + 56dp 玻璃顶栏（左侧玻璃返回钮，与主页顶栏同高，切页时标题不跳）
+ 自带 `navigationBarsPadding()` 的内容槽。分组标题也从「换色小字」换成
「3dp 品牌渐变竖条 + 标题 + 渐隐分隔线」，与主页 `NeonSectionHeader` 同构。

### 四条硬约束

1. **禁用 `RenderEffect` 模糊** —— V5.5.2 崩溃排查已把 API31+ 全屏 RenderEffect 从播放页删除。
   玻璃靠「半透明白 + 1dp 描边 + 顶部高光」画出来。
2. **文字色不许自建** —— 播放页走 `OledTokens`（`OledTokensTest` 锁 4.5:1），
   主界面外壳与列表走 `MaterialTheme.colorScheme`。霓虹 token 只负责渐变/描边/活动态。
3. **霓虹只上「正在发生的事」** —— 进度、正在播放的封面与行、随机/循环开启态、当前歌词行。
4. **极光必须双主题** —— 设置里 `themeMode` 有 LIGHT/DARK 三档；浅色主题下铺深色极光
   会让近黑的 `onSurface` 正文压深底，直接不可读。`NeonSurface.kt` 提供两套数值。

### 验证

- 新增 `ui/neon/NeonTokensTest`（8 项）：8 套调色板 × 3 段渐变的最亮极光叠加后
  正文仍 ≥4.5:1；`AURORA_PEAK` 锁在 0.34；极光周期 ≥8s 且两两相差 ≥3s；开关反馈 ≤300ms。
- 全量 **169 项 / 20 类 / 0 失败**（v6.0.0 当时的基线；v6.1.0 起为 187 项 / 22 类，
  v6.1.1 起为 196 项 / 23 类，v6.2.0 起为 214 项 / 25 类，V1.0 起为 217 项 / 25 类）。
- 产物 `build/apk/jijian-music-6.0.0-vc23-debug.apk`（23558018 B，`versionCode = 23`、
  `versionName = 6.0.0`、label `极简music`，已用 `aapt2 dump badging` 核对）。

---

## v1.1 — Local library, playlists & grouping (old numbering, superseded)

### Local library views
- **Group by songs / album / artist** (`LocalGroup`). Album and artist views
  show cover art, track count and total duration; tap a group to play it.
- **Local search** over song name, singer and album (case-insensitive).
- Multi-singer tags (`、/&,`) are split so an artist view lists every performer.

### Music library (new tab)
- **Favourites** — long-press any track → 收藏.
- **Recently played** — recorded automatically as tracks start (one row per track,
  most-recent first), with a clear-all action.
- **User playlists** — create, rename, delete, add/remove tracks; the first added
  track's artwork becomes the playlist cover.

### Track actions
Long-press any row in any tab opens a bottom sheet to favourite the track or add
it to a playlist (including creating a new one inline).

### Database
Room schema upgraded to **v2** adding `playlist`, `playlist_track`, `favorite`
and `play_history` tables (`MusicRepository` is the single access point).

### Tests
- `LocalLibraryTest` (8 tests): search matching, case-insensitivity, album /
  artist grouping, multi-singer splitting, duration aggregation, fallbacks.
- `LrcParserTest` (6 tests): as before.


---

## v1.2 — Queue, sorting & equalizer (old numbering, superseded)

### Play queue
- New **queue screen** opened from the top bar or the now-playing screen.
- Shows the full queue with the current track highlighted; tap any row to jump.
- Reorder with ↑ / ↓, remove individual entries, or clear the whole queue
  (`QueueLogic` holds the pure index bookkeeping; `PlayerController` delegates).
- Queue editing keeps the *currently-playing track* selected across moves/removals.

### Sorting
- 排序 menu in the local tab: title / artist / album / duration (asc & desc)
  plus 最近添加 (date added).
- Sorting is applied after search filtering, so both compose cleanly.

### Equalizer
- Full system-audio effects via `android.media.audiofx`:
  **Equalizer** (5 bands), **BassBoost**, **Virtualizer**, **LoudnessEnhancer**.
- `EqualizerController` binds to ExoPlayer's audio session lazily once it is ready.
- Built-in **presets** (read from the platform, labelled in Chinese) + a master
  on/off switch + per-band sliders + strength sliders for the extra effects.
- Reachable from the top bar and the now-playing screen.

### Tests (33 total)
- `QueueLogicTest` (12): move/remove index bookkeeping, next/prev resolution.
- `LocalLibraryTest` (15): search + all sort orders.
- `LrcParserTest` (6): LRC parsing.


---


- **OLED 播放页改版**：新增 `ui/theme/OledTokens.kt`（background `#0F0F23` / card `#1B1B30` / muted `#27273B` / accent `#22C55E` / 4dp 间距栅格），播放页与 Mini 播放条全部改为令牌取值，正文对比度均达 WCAG AA 4.5:1。
- **洗牌袋随机（历史，已于 v5.3.1 删除）**：曾新增 `player/ShuffleBag.kt`——一轮内每首恰好播一次，不再出现连播重复；上一首按序回退。该实现已在 v5.3.1 被 `RandomShuffle` 整体替换，此条仅作历史留档。
- **歌词不再遮挡下一句**：歌词留白按视口高度自适应（32%，clamp 72–140dp），行距随字号联动（`LyricMetrics.lineGap = 20dp`），活动行滚动定位改用 scrollOffset；唱片+歌词同屏时短屏自动降为 160dp 唱片。
- 新增 `OledTokensTest`（6 项对比度/栅格断言）与（当时的）`ShuffleBagTest`；测试总数 85 → 99，0 失败。
- 版本 versionCode=8 / versionName=5.2.0。
## v5.3 — True-shuffle unification & home resume card

> **历史章节。** v5.3 的地基是「洗牌袋」：一条 Fisher-Yates 全序，一轮内每首
> 恰好播一次，并靠 `BagShuffleOrder` 把这只袋子适配给 ExoPlayer，让通知栏/
> 锁屏/蓝牙的「下一首」和 App 内按钮走同一条序。这一整套设计（`ShuffleBag`
> / `BagShuffleOrder`）在 v5.3.1 已被判定为错误语义并整体删除。保留本章仅为
> 追溯历史，不代表当前行为。

- **One shuffle bag for every next/previous.** The app-local Fisher-Yates bag
  (`player/ShuffleBag.kt`) used to be bypassed whenever the queue was mirrored
  into ExoPlayer (`playlistMode`), so the notification / lockscreen / bluetooth
  / fluid-cloud next button consumed ExoPlayer's own RNG while the in-app button
  consumed the bag. `player/BagShuffleOrder.kt` then adapted the bag to media3's
  `ShuffleOrder` interface and `PlayerController.installShuffleOrder()` pushed it
  to the real `ExoPlayer` via `PlayerHolder`, so both paths walked the *same*
  order. `onMediaItemTransition` syncs `shuffleCursor` back to whatever
  ExoPlayer actually landed on.
- **Is it "true random"?** Yes, in the sense that matters for a player: each
  round is a uniform Fisher-Yates order generated by `kotlin.random.Random`,
  every track appears exactly once per round, and a new round is reshuffled when
  the bag is exhausted (`ShuffleBagTest.firstSlotIsUniformAcrossSeeds`,
  `everySeedProducesAPermutation`, `freshRoundsAreNotIdentical`). It is *not*
  statistically-audited CSPRNG randomness — that is not needed to avoid
  repeats, which is the actual user-visible complaint.
- **Home screen "last played" card** (`ui/screens/LocalScreen.kt`): above the
  local list, a resume card shows artwork / title / artist of the last track,
  resolved by `MainViewModel.lastPlayed` (combines `settingsStore.lastPlayedId`
  with the scanned library) and tapping it plays the whole local queue from that
  position (`MainViewModel.resumeLastPlayed()`).

## v5.3.0.1 — Shuffle next/previous fixes (`versionCode = 16`)

> **历史章节。** 下面四个「根因」全部建立在洗牌袋语义之上。v5.3.1 把「轮」
> （round）这个概念整个删掉之后，根因 1/3/4 自然消失，根因 2 演化为
> `RandomShuffleState` 的 memo 重绘（`redrawNext` / `redrawPrevious`）。
> 表格中的 API 均已不存在。仅作历史留档。

上一版发现：**开启随机后点「上一首 / 下一首」有时不换歌、或跳在已经听过的歌上**。四个真因果：

1. **`BagShuffleOrder.cloneAndInsert` / `cloneAndRemove` 重新发牌**。
   ExoPlayer 在 `addMediaItems` / `removeMediaItem` 时会克隆它的
   `ShuffleOrder`；旧实现直接拿 `size` 又画一条**全新随机序**，
   于是镜像队列走的序与应用内 `shuffleOrder` 彻底脱钩。
   现在保持旧有相对顺序，新索引插到它们在播放列表里的位置。
2. **`playAt()` 不同步 cursor**。从队列页直接点某首歌时，
   游标停在旧槽位 → 下一次「下一首」从错误的地方出发。现在切到
   `ShuffleBag.cursorOf(shuffleOrder, i)`。
3. **提示器 / 锁屏 / 蓝牙「下一首」在轮末重放整轮**。
   `playlistMode` 下的 skip-next 直接交给 ExoPlayer，而它在轮末短路绕回槽 0
   —— 也就是重放已经听完的那一轮。现在先 `redealIfRoundExhausted()`
   重发一轮再步进。
4. **跨轮循环时在轮末「上一首」回不去**。现在把刚结束的那轮留作
   `previousOrder`，跨轮反向连续行走（`rewindRound()`）。

当时的牌库 API（`ShuffleBag` / `BagShuffleOrder`，**两者均已删除**）：

| API | 语义 |
| --- | --- |
| `build(size)` | Fisher-Yates 全序（一轮一遍） |
| `redeal(size, avoid)` | 新一轮，不以 `avoid` 开头（歌曲自然播完时） |
| `redealAfter(size, current)` | 新一轮，`current` 占槽 0（跳歌跨轮时，不中断当前曲） |
| `roundTail(order)` | 上一轮末尾槽位 |
| `cloneAndInsert/Remove` | 延续当前轮，不重新发牌 |

设置页新增「版本」行（点击复制版本号），方便确认装机的包。

## v5.3.0.1 — Previous across the round boundary

> **历史章节。** `previousOrder` / `rewindRound()` 随洗牌袋一起删除。现在的
> `prev()` 就是一次独立均匀抽样（见 v5.3.2），没有「轮首」「上一轮」概念。

`prev()` used to dead-stop on the first track of a round: the cursor is clamped
at slot 0 by design (`prevCursor = (cursor - 1).coerceAtLeast(0)`), so at the head
of the bag the button did nothing at all - the same song just kept playing.

v5.3.0.1 keeps the round that finished most recently in `previousOrder`. When
`prev()` finds the cursor already at slot 0 it restores that finished round as the
active bag, parks the cursor on its tail and plays it. From there, further `prev()`
calls walk the old round in reverse, so back-stepping is continuous instead of
ping-ponging between two songs.

- `player/ShuffleBag.kt` - `roundTail(order)` pure helper (`-1` for an empty round)
- `player/PlayerController.kt` - `previousOrder` field, `rewindRound()`, `prev()` rewind branch in both mirrored (`playlistMode`) and single-item modes
- `previousOrder` is dropped whenever a deliberate re-deal happens: `reshuffle()`, `toggleShuffle()` off, `clearQueue()`
- Tests: `roundTailIsTheLastSlot`, `rewindingThePreviousRoundWalksItBackwards`, `prevCursorAtHeadStaysPutSoTheCallerCanRewind`

## v5.3.5 — Shuffle clone protocol

第三条根因，也是最隐蔽的一条：克隆是「试探」，不是「承诺」。

`MediaSourceList.setShuffleOrder` 为了修长度不匹配会构造候选 order：

```
if (shuffleOrder.getLength() != holderCount) {
    shuffleOrder = shuffleOrder.cloneAndClear().cloneAndInsert(0, holderCount);
}
this.shuffleOrder = shuffleOrder;
```

而这个候选仍可能被 `ExoPlayerImpl.setShuffleOrder` 的长度断言拒绝 —— 也就是说，
**被克隆的那个 order 会继续服务会话**。而三个 clone 原本都调 `state.invalidate()`，
是**全局擦除** —— 构造一个候选的副作用会抹掉正在服务的 order 的 memo，
下一次 `getNextIndex` 就换了一首歌。

修法：新增 `RandomShuffleState.copy()` 快照，三个 clone 各拿一份快照（`state.copy()`），
父对象不受影响。App 侧不依赖克隆的副作用：每条队列变更路径（`enqueue` / `playNext` /
`moveInQueue` / `removeFromQueue` / `clearQueue` / `tryMirrorPlaylist` / `playIndex` / `applyPersistedModes` /
`prepareRestored` / `toggleShuffle`）都已显式调 `resetShuffle()` 或 `shuffleState.invalidate()`。

### 验证

- 新增 `RandomShuffleCloneTest`（6 项）：克隆不得改写正在服务的 order（insert / remove / clear 三路）、
  克隆继承父策略、长度修复路径可用、快照不回流到父对象。
- 探针先跑红 3/4（`before=5 after=2` / `before=0 after=3` / `expected 3 but was 6`），修后转绿。
- 全量 **144 项测试 0 失败**（当时的基线，v6.0.0 起为 169 项）。

## v5.3.4 — Shuffle freeze hotfix

「随机播放时直接卡死」的两个根因，都在 `player/RandomShuffle.kt` 里，且都不会抛异常。

### 1. memo 只按源位置建键，不记 size

`RandomShuffleState.forward` / `backward` 以 `current` 为键。`PlayerController.installShuffleOrder()`
每次都用同一个 state 对象去 new 一个 `RandomShuffleOrder(queue.size, shuffleState)`，
所以队列变短后，旧答案会被重放。ExoPlayer 把 `getNextIndex()` 的返回值当作
**child（媒体源）索引**直接回填窗口索引（`AbstractConcatenatedTimeline.getNextChildIndex`），
且从不做范围校验 —— 越界索引不是「选错歌」，而是直接损坏 timeline。

修法：读 memo 后先验证 `memo in 0 until size`，失效则重抽；`last` / `first` 同样处理。

### 2. KDoc 声称线程安全，代码里没有一个 synchronized

state 同时被 ExoPlayer 播放线程（`setShuffleOrder` → `cloneAndInsert` → `invalidate`）
和主线程（按键 `redrawNext`）写。memo 是普通 `HashMap`，`put` 与 resize 竞争可使
桶链表成环，之后主线程的一次遍历永不终止 —— **ANR：无崩溃、无日志、就是卡死**。

修法：全部公开方法加 `@Synchronized`（`memoised` 用 `@get:Synchronized`）。

### 验证

- `RandomShuffleStateStalenessTest`（6 项）回归：缩短后不再重放越界索引、多种 size 下全部在范围、
  有效 memo 仍稳定、反复 resize 不泄漏 memo。
- 探针先跑红（`a shrunk queue replayed a stale index 36 (valid 0 until 8)`），修后转绿。
- 全量 **138 项测试 0 失败**。

## v5.3.3 — Play-count statistics

A new **播放统计** screen, reachable from the top bar next to the
equalizer. It answers one question — *what did I actually listen to?* — over a
day, a week, or everything.

**What is stored.** A new `play_event` table (Room v4, `MIGRATION_3_4`) records one
row per play: the track id, its serialised `MusicInfo`, and the timestamp. It is
deliberately *not* the existing `play_history` table, which is keyed by track and
overwrites on replay — that one answers "what did I listen to most recently" and
by construction throws away the count. Events older than a year are pruned on
each write, so the table cannot grow without bound.

**How it is aggregated.** `PlayStats` is a pure object — no Android, no database —
so the whole of it is unit-tested against a pinned clock and timezone (17 tests).
A day is a calendar day and a week starts on **Monday**, computed with `Calendar`
rather than `java.time` because `minSdk` is 24 and core-library desugaring is off.

**The screen.** Three range chips (今日 / 本周 / 全部), two summary tiles (total
plays, distinct tracks), a bar chart, and a ranked list of tracks. The chart is a
weighted `Row` of boxes rather than a `Canvas`: a bucket with no plays then
renders a zero-height bar instead of a distorted chart, and nothing depends on
the parent having a sane density. Granularity follows the range — 24 hours for
today, days otherwise. 清空 wipes the events only; favourites, playlists and
recent plays are untouched.

**Queue repair moved to press time.** `PlayerController.onNeedBackfill` lets the
controller ask for the surrounding library when the queue has collapsed to one
track (the state after restoring the last-played track). Previously that repair
ran once during restore and silently did nothing if the scan had not finished;
now `next()` and `prev()` repair the queue on the press that needs it, so a
shuffle step is never a no-op.

`versionCode = 19`, `versionName = "5.3.3"`, 132 unit tests, 0 failures.

## v5.3.2 — Shuffle hotfix: random previous, no end-of-queue stall

> **历史章节。** 本章描述的 `prev()`「均匀随机」与「无历史栈」已被 **V6.1** 推翻：
> 现在 `prev()` 回退的是真实播放历史（`PlayHistory`）。下面的「3.」之外仍然有效的
> 只有 `getNextIndex()` 永不返回 -1 的结论。

Two user-reported bugs from v5.3.1, both rooted in how ExoPlayer consumes a
`ShuffleOrder`.

**1. "Previous" was not random.** `RandomShuffleState.previousIndex()` walked a
deterministic back-stack of played tracks, so the button always returned the same
track. In a random order there is no canonical "the one before this", so
`previousIndex()` is now a plain uniform draw over the other tracks, exactly like
`nextIndex()`. The history stack, the pass counter and `repeatAll` are gone.

**2. Playback stopped when a track ended.** `RandomShuffleOrder.getNextIndex()`
returned `-1` once a "pass" was used up. ExoPlayer reads `-1` as *playlist
finished* and stops the player for good — a one-way door. The gate is removed:
`getNextIndex()` always yields a track while one exists. `repeat = OFF` stops only
the sequential path, where the end of the playlist is a real place.

**Also fixed:** a `ShuffleOrder` is a pure function to ExoPlayer — it asks the same
index several times per decision (whether a next item exists, where to seek, which
notification buttons to draw). Answers are now memoised per source position and
invalidated on track change, so the player cannot be told two different things in
one breath. A user press uses `redrawNext()` / `redrawPrevious()`, which replaces
the memo so ExoPlayer agrees with the button.

Regression tests: `player/RandomShuffleOrderTest` (7) + rewritten
`player/RandomShuffleStateTest` (12). **115 tests, 0 failed.**

**3. QQ would not treat the new build as a new package.** Every build wrote the
same file name, `app/build/outputs/apk/debug/app-debug.apk`, and consecutive
builds were byte-identical in size (23,182,253 B for both v5.3.0.1 and v5.3.1).
QQ's file transfer dedupes on name + size, so the fresh package was matched
against the copy the chat already held. A `versionedApk` Gradle task now emits
`build/apk/jijian-music-<versionName>-vc<versionCode>-debug.apk` as well. The
package body itself was never at fault: signature, `versionCode`/`versionName`,
zipalign and entry counts all checked out.

## v5.3.1 — Pure random shuffle

> **历史章节。** 本章把「独立均匀抽样」定为契约；**V6.1 已改回「一条 cycle 播完才重洗」**
> （`docs/adr/0001`）。下面的 before/after 表只用于追溯为什么中间出现过纯随机。

Shuffle is now **independent uniform sampling**, not a bag. Every press of next
draws uniformly from the *other* N-1 tracks of the queue, so the same track may
come back immediately — that is what was asked for and it is now the contract.

### What changed

| Before (v5.3 / v5.6) | Now (v5.3.1) |
| --- | --- |
| Fisher-Yates bag, one round = every track exactly once | independent uniform draw over the remaining N-1 tracks |
| `ShuffleBag` + `BagShuffleOrder` (2 classes, 30 tests) | deleted — replaced by `RandomShuffle` + `RandomShuffleOrder` + `RandomShuffleState` |
| `prev()` walked the bag backwards | `prev()` walks the **play history stack** (`RandomShuffleState.history`) |
| next at a round boundary re-dealt with an "avoid" argument | next simply draws again — no boundary concept outside repeat = OFF |
| ExoPlayer's shuffle order could silently fail to install (`runCatching` swallowed the length assertion) | install failures are surfaced through `onShuffleOrderRejected` and retried from `onTimelineChanged` |

### The three defects that made it feel non-random

1. **`ExoPlayer.setShuffleOrder` was never actually applied.** `tryMirrorPlaylist`
   called `setMediaItems(...)` (an asynchronous dispatch to the session) and then
   immediately `setShuffleOrder(...)`. At that instant ExoPlayer still held the
   *old* item count, so `ExoPlayerImpl`'s
   `Assertions.checkArgument(shuffleOrder.getLength() == mediaSourceHolderSnapshots.size())`
   threw — and `runCatching` swallowed it. The "unified shuffle" therefore never
   existed on the system side. Fixed: `onTimelineChanged` re-installs the order
   once the new playlist has actually landed, and failures are reported instead
   of swallowed.
2. **The controller could not know the app-side index.**
   `MediaControllerImplBase.seekToNextMediaItem()` is a *double* action — it
   dispatches a remote `seekToNextMediaItem()` **and** re-derives the index from
   its local `playerInfo` mirror. Any index computed in the app was immediately
   overwritten. Fixed: next/previous compute the target index locally and call
   `seekTo(target)`; `seekToNextMediaItem()` is no longer used for shuffle jumps.
3. **`repeatMode == OFF` had no next at the end of a pass.** Nothing was drawn
   because the bag was exhausted. Fixed: a deliberate press calls
   `RandomShuffleState.restartPass()` and starts a fresh pass; only *automatic*
   continuation stops at the end of a repeat = OFF pass.

### Semantics (as of v5.3.1 — superseded by V6.1)

- `next()` — uniform over the other N-1 tracks; may repeat what you just heard.
- `prev()` — steps back through the history of tracks actually played this
  session (bounded to 100 entries); falls back to a random draw when the history
  is empty. Stepping back does not count towards a repeat = OFF pass.
- Automatic advance (track ended) — same random draw, but stops when a
  repeat = OFF pass has been through `size` tracks.
- Notification / lock screen / Bluetooth / fluid-cloud next/previous — go through
  the same `RandomShuffleState`, because `RandomShuffleOrder.getNextIndex()` draws
  live on every call rather than walking a fixed order.

### Files

- `app/src/main/java/com/lxmusic/android/player/RandomShuffle.kt` — `pickOther()` + `RandomShuffleState`
- `app/src/main/java/com/lxmusic/android/player/RandomShuffleOrder.kt` — the `ShuffleOrder` handed to ExoPlayer
- `app/src/main/java/com/lxmusic/android/player/PlayerController.kt` — `next()` / `prev()` / `installShuffleOrder()` / `onTimelineChanged()`
- `app/src/test/java/com/lxmusic/android/player/RandomShuffleTest.kt` — 5 tests (uniformity, no self-pick, degenerate sizes)
- `app/src/test/java/com/lxmusic/android/player/RandomShuffleStateTest.kt` — 12 tests (history, pass counter, restart)
- Removed: `player/ShuffleBag.kt`, `player/BagShuffleOrder.kt`, `ShuffleBagTest.kt`, `BagShuffleOrderTest.kt`

## v5.5.2 (hotfix)


- **播放页闪退**：v5.5.1 已修掉 `LyricView` 的单色停点渐变，但反馈仍然复现，因此本轮把播放页上**唯一可能触发致命错误的 `RenderEffect`**也移除：根级封面不再做 1.25x `graphicsLayer` 过采样与 API31+ `Modifier.blur(24.dp, Unbounded)`，改回 `Modifier.fillMaxSize()`（alpha 0.30f -> 0.22f）。背景柔和度由原有的 alpha + 渐变遮罩提供，两者都不使用 RenderEffect。
- **崩溃日志器**：新增 `CrashLogger`（`Thread.setDefaultUncaughtExceptionHandler`，链式保留系统原 handler），把时间/线程/设备型号/Android 版本/应用版本 + 完整堆栈写入 `filesDir/last_crash.txt` 与 `getExternalFilesDir(null)/last_crash.txt`；新增 `CrashReportDialog`，下次启动以对话框展示，支持「复制」与「清除」，方便把真实堆栈回传定位。
- 版本 versionCode=12 / versionName=5.5.2，101 项测试 0 失败。

## v5.5.1 (hotfix)

- **进入播放页闪退修复**：`LyricView` 底部渐隐的 `Brush.verticalGradient` 只传了 1 个 colorStop，绘制时抛 `IllegalArgumentException: colors must have length of at least 2 if colorStops is omitted.`（仅在唱片皮肤+有歌词的共显态触发）。改为双色停点 `listOf(Color.Transparent, OledTokens.background.copy(alpha = 0.75f))` + `startY/endY`。
- **背景框框修复补回**：根级封面 `graphicsLayer { scaleX/scaleY = 1.25f }` 过采样 + API31+ `Modifier.blur(24.dp, BlurredEdgeTreatment.Unbounded)`（导包 `androidx.compose.ui.draw.BlurredEdgeTreatment`）。
- 版本 versionCode=11 / versionName=5.5.1，101 项测试 0 失败。

## v5.5

- **播放页背景框框修复**：根级封面1.25x过采样裁切+API31+ Modifier.blur 虚化（低版本自动降级）。
- **歌词底部预留**：唱片共显时歌词区列底部预留96dp并渐隐，下一句不被进度条遮盖。
- **随机播放修复**：prepareRestored 恢复上次播放后队列自动补全，随机不再困于单曲循环。
- 版本 versionCode=10 / versionName=5.5.0，测试 101 项 0 失败。

## v5.1

- **点击歌曲直达播放页**：本地/搜索/收藏/最近/歌单五处入口点击后自动打开全屏播放页；队列内跳转不触发。
- **歌词模式背景全屏化**：移除播放页主列左右留白，歌词皮肤背景直达屏幕边缘并加可读性渐变遮罩，右上角圆封面保留。
- **记住上次播放**：冷启动静默恢复上一首（暂停态、停在原进度），启动零联网；在线曲目/已删除曲目自动清除标记。
- **随机/循环持久化**：循环模式与随机开关即时写盘，重启后原样恢复。
- 版本 versionCode=7 / versionName=5.1.0。

## v3.1 (2026-10-11)

- 播放页收敛为唱片 / 歌词两种样式，旧样式值自动归一化为唱片
- 样式切换按钮移至右上角；底部歌名行可在设置中隐藏
- 设置页屏蔽默认音质与更新公告入口（数据层保留）

## v3 (2026-10-10)

- 黑胶样式唱片 + 歌词同屏（无歌词时自动全尺寸唱片）
- 其余 4 种样式新增歌词条带，单击直达全屏歌词
- 全新主图标：渐变画布 + 大号白色音符（iOS 风格）
- 随机播放卡住修复（原生 shuffle 顺序表）
- 纯文本内嵌歌词解析修复；唱臂几何重做
- 通用组件入驻各页（骨架屏 / 空态 / 确认弹层）

## v2.2 (2026-10-09)

- 播放页 6 种样式循环切换：唱片（含唱臂）/ 磁带 / 声波 / 极简 / 歌词 / 卡片
- 通知栏/锁屏显示封面并支持上一首/下一首；点通知打开播放页（流体云适配）
- 全新 iOS 风格应用图标（渐变画布 + 音符焦点 + 单色主题图标）
- 修复随机播放卡住、内嵌纯文本歌词无法显示
- 假设置清零：列表样式三档 / 启动扫描 / 记录历史 / 耳机拔出暂停 / 音质偏好全部生效
- 删除系统音乐库歌曲走系统确认对话框；深色模式状态栏图标自动适配
- 均衡器频段曲线可视化；通用组件库（EmptyState / SkeletonList / ConfirmSheet 等）

## v2.0 — Pure local player, theming & lyrics

The app pivoted from a dual online/local player into a **local-only minimal
player** (requirement: 屏蔽在线搜索 / 换名 极简music / 换图标). The online code
is still present but hidden behind `FeatureFlags.ONLINE_ENABLED = false`.

### Branding
- Renamed to **极简music** (`rootProject.name = JiJianMusic`); `applicationId`
  deliberately unchanged so existing installs upgrade in place without data loss.
- New adaptive launcher icon: diagonal blue→purple gradient with five white
  equalizer bars (`ic_launcher_foreground/background/monochrome` vectors plus
  5 PNG densities).

### Folder scanning (SAF) — `local/SafMusicScanner.kt`
- `ACTION_OPEN_DOCUMENT_TREE` picker; the granted URI is persisted with
  `takePersistableUriPermission(READ | WRITE)` so it survives reboots.
- Recursive `DocumentFile` walk, audio filtered by mime or a 10-extension
  whitelist, `MediaMetadataRetriever.setDataSource(context, uri)` for tags,
  embedded artwork cached to `cacheDir/covers/`.
- Manual single-file add (`ACTION_OPEN_DOCUMENT`, multi-select) merged and
  de-duplicated by document id.

### Local library cache — Room v3
- New `local_track` table (`LocalTrackEntity` + `LocalTrackDao`) with a real
  `MIGRATION_2_3` (the destructive fallback is no longer relied upon for v3).
- Startup paints instantly from cache, then rescans in the background and
  reconciles via `LocalLibrary.merge()` / `LocalLibrary.prune()`.

### Track deletion
- Bottom sheet gains **从音乐库移除** (cache row only) and **删除文件**
  (physical delete, behind a red confirmation dialog).
- `local/LocalFileDeleter.kt` handles SAF document URIs
  (`DocumentsContract.deleteDocument`) and legacy `file://` paths.

### Settings & theming
- `data/settings/SettingsStore.kt` on DataStore Preferences: theme mode,
  theme colour, list style, scan folders, manual URIs, auto scan, embedded
  cover, default quality, history, headset-unplug pause, lyric font scale /
  offset / prefer-`.lrc`.
- `ui/screens/SettingsScreen.kt` grouped into 外观 / 音乐库 / 播放 / 歌词 / 关于.
- 8 hand-built colour palettes in `ui/theme/ColorPalettes.kt` (light + dark
  `ColorScheme` each); Material You dynamic colour was **removed** on request.

### Lyrics (rewritten)
- `lyric/LyricEncoding.kt`: strict UTF-8 → strict GBK → ISO-8859-1 fallback,
  BOM stripping (fixes garbled Chinese `.lrc` files).
- `lyric/EmbeddedLyrics.kt`: reads unsynchronised ID3v2 USLT/ULT/TXXX,
  FLAC `VORBIS_COMMENT`, Ogg Vorbis / Opus tags and MP4 `©lyr` atoms.
- `lyric/LyricLoader.kt`: for SAF URIs the sibling `.lrc` is found via
  `DocumentFile.parentFile.listFiles()` — the old MediaStore `DATA` lookup
  never worked for SAF document URIs.
- `PlayerController.lyricOffsetMs` is applied in the progress loop, so offset
  changes take effect without reloading the lyric.

### Now-playing redesign
- Full-bleed artwork backdrop + gradient, and an explicit **歌词 / 唱片** pill
  switch (previously you had to guess that tapping the middle toggled views).
- `RotatingArtwork` rewritten with `Animatable`: the record now **freezes**
  when paused (the old code rotated either way), with vinyl grooves, a static
  highlight and a centre spindle.
- Buffering shows a ring on the play button; lyric font scale is configurable.

### Update announcement
> **已移除（历史留档）。** v2.0 曾用 `data/Changelog.kt` 存发布说明、用
> `ui/screens/AnnouncementScreen.kt` 渲染，靠 `SettingsStore.lastSeenVersion`
> 每版弹一次。这两个文件后来都删掉了（v3.1 起设置页屏蔽公告入口）。
> 版本历史现在的权威位置是：本文件 + `HANDOVER.md` + `UPDATE_PLAN.md`。

### UI polish
- `ui/components/SearchBar.kt`: a fixed **44dp** search field built on
  `BasicTextField` (Material's `OutlinedTextField` forces 56dp and grows with
  font scale), so toolbars line up with their icon buttons.
- `ui/components/Hints.kt`: `toast()` + `HintIconButton` — every icon button
  now shows a short toast describing what it does before acting.

### Source encoding
- All Kotlin sources are plain **UTF-8 without BOM**, with Chinese written as
  literal characters (389 `\uXXXX` escapes were un-escaped).
- Do **not** use PowerShell's `Set-Content -Encoding UTF8` (PS 5.1 writes a BOM
  that breaks the Kotlin/Gradle toolchain); use Python `io.open(..., 'w',
  encoding='utf-8', newline='\n')`. [HANDOVER.md](HANDOVER.md) §8 has the exact
  here-string recipe, including the termination-line trap.

### Next steps
See [`UPDATE_PLAN.md`](UPDATE_PLAN.md) — it lists the known gaps after v2.0
(settings that are stored but not yet honoured, MediaStore delete confirmation,
on-device lyric verification) and the planned v2.1 scope.
