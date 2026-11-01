package com.lxmusic.android.player

import com.lxmusic.android.data.model.MusicInfo
import com.lxmusic.android.lyric.Lyric
import com.lxmusic.android.LxApp
import com.lxmusic.android.lyric.LyricLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * V1.1.0: 歌词装载与偏移，从 [PlayerController] 拆出来——与 [EqualizerController]
 * 对称的第二个"设备外设控制器"。播放器只说"这一首开始了"，歌词怎么读（.lrc
 * 还是内嵌标签）、偏移多少毫秒、行号怎么算，都是这里的职责。
 *
 * 与 EqualizerController 一样持有一个状态快照；[PlayerController] 在换歌与
 * 进度循环里各调一次，[PlayerState.lyric]/[PlayerState.lyricIndex] 只是它的投影。
 */
class LyricController(
    private val scope: CoroutineScope,
    private val loader: LyricLoader = LyricLoader(LxApp.instance),
) {

    /** .lrc 文件优先。由设置页驱动，换值即刻生效（下一次装载就用到）。 */
    @Volatile var preferLrcFile: Boolean = true

    /** 歌词整体提前/推迟的毫秒数（正 = 歌词提前）。 */
    @Volatile var offsetMs: Int = 0

    /** 是否读取音频文件的内嵌歌词标签。 */
    @Volatile var readEmbedded: Boolean = true

    /**
     * 异步装载一首歌的歌词。完成后把结果交给 [onLoaded]（内部仍在 [scope]，
     * 因此回调里直接写 PlayerState 是安全的）。装载失败给空歌词，不报错——
     * 没有歌词是常态，不是异常。
     */
    fun load(music: MusicInfo, onLoaded: (Lyric) -> Unit) {
        scope.launch {
            val lyric = try {
                if (music.isLocal) {
                    loader.loadLocal(
                        music.localPath ?: music.extra["uri"].orEmpty(),
                        preferLrcFile = preferLrcFile,
                        readEmbedded = readEmbedded,
                    )
                } else {
                    Lyric(emptyList())
                }
            } catch (_: Exception) {
                Lyric(emptyList())
            }
            onLoaded(lyric)
        }
    }

    /** 当前进度对应的歌词行号（进度已含偏移）。 */
    fun indexAt(lyric: Lyric, positionMs: Long): Int = lyric.indexAt(positionMs + offsetMs)
}
