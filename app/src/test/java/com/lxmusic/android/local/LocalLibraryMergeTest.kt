package com.lxmusic.android.local

import com.lxmusic.android.data.model.MusicInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests for the library merge / prune helpers backing the C2 cache. */
class LocalLibraryMergeTest {

    private fun track(id: String, origin: String = "folder", name: String = id) = MusicInfo(
        id = id, source = "local", songmid = id, name = name, singer = "s",
        extra = mapOf("origin" to origin),
    )

    @Test
    fun mergeDeduplicatesById() {
        val a = listOf(track("1"), track("2"))
        val b = listOf(track("2"), track("3"))
        val merged = LocalLibrary.merge(a, b)
        assertEquals(3, merged.size)
        assertEquals(setOf("1", "2", "3"), merged.map { it.id }.toSet())
    }

    @Test
    fun mergePrefersLaterList() {
        val stale = track("1", name = "old")
        val fresh = track("1", name = "new")
        val merged = LocalLibrary.merge(listOf(stale), listOf(fresh))
        assertEquals(1, merged.size)
        assertEquals("new", merged.first().name)
    }

    @Test
    fun mergeOfEmptyListsIsEmpty() {
        assertTrue(LocalLibrary.merge(emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun pruneKeepsTracksStillPresent() {
        val cached = listOf(track("1"), track("2"))
        val fresh = listOf(track("1"))
        val kept = LocalLibrary.prune(cached, fresh)
        assertEquals(listOf("1"), kept.map { it.id })
    }

    @Test
    fun pruneKeepsManualTracksEvenWhenMissing() {
        val cached = listOf(track("1"), track("m", origin = "manual"))
        val fresh = listOf(track("1"))
        val kept = LocalLibrary.prune(cached, fresh)
        assertEquals(setOf("1", "m"), kept.map { it.id }.toSet())
    }

    @Test
    fun pruneDropsFolderTracksThatDisappeared() {
        val cached = listOf(track("gone"))
        assertTrue(LocalLibrary.prune(cached, emptyList()).isEmpty())
    }
}
