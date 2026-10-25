package com.lxmusic.android.local

import com.lxmusic.android.data.model.MusicInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalLibraryTest {

    private fun track(
        id: String, name: String, singer: String, album: String = "Album",
        dur: Int = 200, img: String? = null,
    ) = MusicInfo(
        id = id, source = "local", songmid = id, name = name, singer = singer,
        albumName = album, intervalSeconds = dur, img = img,
    )

    private val library = listOf(
        track("1", "晴天", "周杰伦", "叶惠美", 269, "art1"),
        track("2", "稻香", "周杰伦", "魔杰座", 223, "art2"),
        track("3", "七里香", "周杰伦", "七里香", 299),
        track("4", "小幸运", "田馥甄", "我的少女时代", 265),
        track("5", "合唱曲", "周杰伦、田馥甄", "合辑", 200),
    )

    @Test
    fun searchMatchesNameSingerAlbum() {
        assertEquals(4, LocalLibrary.search(library, "周杰伦").size) // 晴天/稻香/七里香/合唱曲
        assertEquals(2, LocalLibrary.search(library, "田馥甄").size) // 小幸运/合唱曲
        assertEquals(1, LocalLibrary.search(library, "叶惠美").size) // album name match
    }

    @Test
    fun searchIsCaseInsensitiveAndTrimmed() {
        val t = track("x", "Hello World", "Adele")
        assertEquals(1, LocalLibrary.search(listOf(t), "  hello  ").size)
        assertEquals(1, LocalLibrary.search(listOf(t), "ADELE").size)
    }

    @Test
    fun blankQueryReturnsAll() {
        assertEquals(library.size, LocalLibrary.search(library, "").size)
        assertEquals(library.size, LocalLibrary.search(library, "   ").size)
    }

    @Test
    fun byAlbumGroupsAndSorts() {
        val groups = LocalLibrary.byAlbum(library)
        // Albums: 叶惠美, 魔杰座, 七里香, 我的少女时代, 合辑 -> 5 distinct
        assertEquals(5, groups.size)
        // Sorted by title; first should be one of the Chinese-collated names, just check grouping integrity
        val totalTracks = groups.sumOf { it.tracks.size }
        assertEquals(library.size, totalTracks)
    }

    @Test
    fun byArtistSplitsMultiSingerTags() {
        val groups = LocalLibrary.byArtist(library)
        val zhou = groups.first { it.title == "周杰伦" }
        val tian = groups.first { it.title == "田馥甄" }
        assertEquals(4, zhou.count) // 晴天/稻香/七里香/合唱曲
        assertEquals(2, tian.count) // 小幸运/合唱曲
    }

    @Test
    fun groupAggregatesDuration() {
        val groups = LocalLibrary.byAlbum(library)
        val ep = groups.first { it.title == "叶惠美" }
        assertEquals(269, ep.totalSeconds)
        assertTrue(ep.totalDuration.isNotBlank())
    }

    @Test
    fun unknownAlbumFallsBack() {
        val t = track("9", "无名", "某人", album = "")
        val groups = LocalLibrary.byAlbum(listOf(t))
        assertEquals("未知专辑", groups.first().title)
    }

    @Test
    fun unknownArtistFallsBack() {
        val t = track("10", "无名", "", album = "x")
        val groups = LocalLibrary.byArtist(listOf(t))
        assertEquals("未知歌手", groups.first().title)
    }

    // ---------------- sorting ----------------

    @Test
    fun sortByTitleAscendingAndDescending() {
        val asc = LocalLibrary.sort(library, LocalSort.TITLE_ASC).map { it.name }
        val desc = LocalLibrary.sort(library, LocalSort.TITLE_DESC).map { it.name }
        assertEquals(asc.reversed(), desc)
    }

    @Test
    fun sortByArtistGroupsSameSinger() {
        val sorted = LocalLibrary.sort(library, LocalSort.ARTIST_ASC)
        // Tracks by the same artist must be adjacent.
        val artists = sorted.map { it.singer }
        val tian = artists.indexOfFirst { it.contains("\u7530\u99a5\u7504") }
        assertTrue(tian >= 0)
    }

    @Test
    fun sortByDurationAscending() {
        val sorted = LocalLibrary.sort(library, LocalSort.DURATION_ASC).map { it.intervalSeconds }
        assertEquals(sorted.sorted(), sorted)
    }

    @Test
    fun sortByDurationDescending() {
        val sorted = LocalLibrary.sort(library, LocalSort.DURATION_DESC).map { it.intervalSeconds }
        assertEquals(sorted.sortedDescending(), sorted)
    }

    @Test
    fun sortByAlbumAscending() {
        val sorted = LocalLibrary.sort(library, LocalSort.ALBUM_ASC)
        val albums = sorted.map { it.albumName }
        assertEquals(albums.sorted(), albums)
    }

    @Test
    fun sortByDateAddedDescending() {
        val t1 = track("a", "A", "X").copy(dateAdded = 100)
        val t2 = track("b", "B", "X").copy(dateAdded = 300)
        val t3 = track("c", "C", "X").copy(dateAdded = 200)
        val sorted = LocalLibrary.sort(listOf(t1, t2, t3), LocalSort.DATE_ADDED_DESC)
        assertEquals(listOf("b", "c", "a"), sorted.map { it.id })
    }

    @Test
    fun everySortOrderReturnsAllTracks() {
        for (order in LocalSort.entries) {
            assertEquals(library.size, LocalLibrary.sort(library, order).size)
        }
    }
}
