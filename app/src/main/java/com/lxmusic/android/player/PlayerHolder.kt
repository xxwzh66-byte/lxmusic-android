package com.lxmusic.android.player

import androidx.media3.exoplayer.ExoPlayer

/** Simple process-wide accessor so the UI can talk to the ExoPlayer instance. */
object PlayerHolder {
    @Volatile var player: ExoPlayer? = null
}