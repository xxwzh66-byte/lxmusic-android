package com.lxmusic.android.source

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.lxmusic.android.data.model.MusicInfo
import com.lxmusic.android.data.model.QualityInfo
import com.lxmusic.android.net.HttpBridge
import java.net.URLEncoder

/**
 * Kuwo (酷我音乐) search provider.
 * Ported from lx-music-desktop src/renderer/utils/musicSdk/kw/musicSearch.js.
 */
class KwSearchProvider(private val http: HttpBridge) : SearchProvider {
    override val source = LxSource.KW

    private val mInfoRx = Regex("level:(\\w+),bitrate:(\\d+),format:(\\w+),size:([\\w.]+)")

    override suspend fun search(keyword: String, page: Int, limit: Int): SearchPage {
        val url = "http://search.kuwo.cn/r.s?client=kt&all=" + URLEncoder.encode(keyword, "UTF-8") +
            "&pn=${page - 1}&rn=$limit&uid=794762570&ver=kwplayer_ar_9.2.2.1&vipver=1" +
            "&show_copyright_off=1&newver=1&ft=music&cluster=0&strategy=2012&encoding=utf8" +
            "&rformat=json&vermerge=1&mobi=1&issubtitle=1"

        val result = http.execute(url, mapOf("method" to "GET"))
        val root = JsonParser.parseString(result.bodyAsString()).asJsonObject
        val list = handleResult(root.getAsJsonArray("abslist"))
        val total = root.get("TOTAL")?.asString?.toIntOrNull() ?: list.size
        return SearchPage(list, total, page, if (limit > 0) Math.ceil(total.toDouble() / limit).toInt() else 1, source)
    }

    private fun handleResult(arr: com.google.gson.JsonArray): List<MusicInfo> {
        val out = ArrayList<MusicInfo>()
        for (el in arr) {
            val info = el.asJsonObject
            val songId = info.get("MUSICRID")?.asString?.replace("MUSIC_", "") ?: continue
            val nMinfo = info.get("N_MINFO")?.asString ?: continue

            val qualitys = LinkedHashMap<String, QualityInfo>()
            for (part in nMinfo.split(";")) {
                val m = mInfoRx.find(part) ?: continue
                val size = m.groupValues[4]
                when (m.groupValues[2]) {
                    "4000" -> qualitys["flac24bit"] = QualityInfo("flac24bit", size.uppercase())
                    "2000" -> qualitys["flac"] = QualityInfo("flac", size.uppercase())
                    "320" -> qualitys["320k"] = QualityInfo("320k", size.uppercase())
                    "128" -> qualitys["128k"] = QualityInfo("128k", size.uppercase())
                }
            }

            val duration = info.get("DURATION")?.asString?.toIntOrNull() ?: 0
            val name = LxUtils.decodeName(info.get("SONGNAME")?.asString)
            val singer = LxUtils.decodeName(info.get("ARTIST")?.asString).replace("&", "、")
            val album = LxUtils.decodeName(info.get("ALBUM")?.asString)
            val albumId = LxUtils.decodeName(info.get("ALBUMID")?.asString)

            out.add(
                MusicInfo(
                    id = musicId(source.id, songId),
                    source = source.id,
                    songmid = songId,
                    name = name,
                    singer = singer,
                    albumName = album,
                    albumId = albumId,
                    interval = LxUtils.formatPlayTime(duration),
                    intervalSeconds = duration,
                    qualitys = qualitys,
                )
            )
        }
        return out
    }
}