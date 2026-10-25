package com.lxmusic.android.player

/**
 * Pure queue-manipulation helpers, extracted from [PlayerController] so the
 * index bookkeeping can be unit-tested without Android dependencies.
 */
object QueueLogic {

    /** Result of a reorder: the new list plus the index of the playing track. */
    data class ReorderResult<T>(val list: List<T>, val index: Int)

    /**
     * Moves the item at [from] to [to], keeping the item at [index] selected.
     * Returns the new list and the (possibly adjusted) index of that item.
     */
    fun <T> move(list: List<T>, index: Int, from: Int, to: Int): ReorderResult<T> {
        if (from !in list.indices || to !in list.indices || from == to) {
            return ReorderResult(list, index)
        }
        val mutable = list.toMutableList()
        val item = mutable.removeAt(from)
        mutable.add(to, item)

        val newIndex = when {
            index == from -> to
            from < index && to >= index -> index - 1
            from > index && to <= index -> index + 1
            else -> index
        }
        return ReorderResult(mutable, newIndex)
    }

    /** Result of removing a queue entry. */
    data class RemoveResult<T>(val list: List<T>, val index: Int, val shouldPlay: Boolean)

    /**
     * Removes the entry at [removeAt]. [index] is the currently-playing index.
     * [shouldPlay] is true when the caller must (re)start playback of [index].
     */
    fun <T> remove(list: List<T>, index: Int, removeAt: Int): RemoveResult<T> {
        if (removeAt !in list.indices) return RemoveResult(list, index, false)
        val mutable = list.toMutableList()
        mutable.removeAt(removeAt)
        if (mutable.isEmpty()) return RemoveResult(emptyList(), -1, false)
        return when {
            removeAt < index -> RemoveResult(mutable, index - 1, false)
            removeAt > index -> RemoveResult(mutable, index, false)
            else -> {
                val newIndex = index.coerceAtMost(mutable.size - 1)
                RemoveResult(mutable, newIndex, true)
            }
        }
    }

    /** Computes the index to play next. Returns -1 when playback should stop. */
    fun nextIndex(size: Int, index: Int, shuffle: Boolean, repeatAll: Boolean, random: () -> Int = { (0 until size).random() }): Int {
        if (size == 0) return -1
        if (shuffle) return random()
        if (index + 1 < size) return index + 1
        return if (repeatAll) 0 else -1
    }

    /** Computes the index to play for "previous". */
    fun prevIndex(size: Int, index: Int, shuffle: Boolean, random: () -> Int = { (0 until size).random() }): Int {
        if (size == 0) return -1
        if (shuffle) return random()
        return if (index - 1 >= 0) index - 1 else 0
    }
}
