# 极简music (JiJianMusic) for Android

`applicationId = com.lxmusic.android` · 当前版本 **1.0.0** (`versionCode = 1`)

一个从 [lyswhut/lx-music-desktop](https://github.com/lyswhut/lx-music-desktop) 的
音源架构出发、逐步转型为**纯本地极简音乐播放器**的 Android 应用。
在线搜索/自定义音源模块仍然完整保留在代码中，但默认通过
`FeatureFlags.ONLINE_ENABLED = false` 关闭（改一个常量即可恢复）。
