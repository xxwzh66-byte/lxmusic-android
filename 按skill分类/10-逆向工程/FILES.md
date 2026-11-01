# 10-逆向工程 · 逆向工程

> 分析二进制、固件、Electron 应用

命中文件 13 项：

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