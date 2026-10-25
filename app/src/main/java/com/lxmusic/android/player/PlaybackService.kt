package com.lxmusic.android.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.lxmusic.android.MainActivity
import com.lxmusic.android.R

/** Foreground media playback service backed by ExoPlayer + MediaSession. */
@UnstableApi
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null

    override fun onCreate() {
        super.onCreate()

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(30_000, 60_000, 1_500, 3_000)
            .build()

        val exo = ExoPlayer.Builder(this)
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            // V1.0: a headset / bluetooth / lockscreen "previous" must always go
            // to the previous track, never restart the current one.
            //
            // `BasePlayer.seekToPrevious()` is not `seekToPreviousMediaItem()`:
            // it first checks `getCurrentPosition() <= getMaxSeekToPreviousPosition()`
            // and, when the current track has been playing for longer than that
            // (media3's default: 3000 ms), it calls `seekToCurrentItem(0, ...)`
            // instead - i.e. it restarts the track from the top. That default
            // suits podcast skip-back UIs and is exactly the wrong behaviour for
            // a music player, where the hardware previous button is expected to
            // behave like the on-screen one.
            //
            // Raising the threshold above any track length makes the condition
            // unreachable in practice, so every previous press takes the
            // `seekToPreviousMediaItemInternal` branch and moves one item back.
            // The in-app button was never affected: it calls
            // PlayerController.prev() directly and never goes through
            // BasePlayer.seekToPrevious().
            .setMaxSeekToPreviousPositionMs(Long.MAX_VALUE)
            .build()

        player = exo

        // Media notification: custom channel ("playing now") + monochrome small icon.
        val provider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(CHANNEL_ID)
            .setChannelName(R.string.notification_channel_playback)
            .setNotificationId(NOTIFICATION_ID)
            .build()
        provider.setSmallIcon(R.drawable.ic_notification)
        setMediaNotificationProvider(provider)

        // Tap on the media notification / fluid-cloud capsule opens the app.
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            ),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        mediaSession = MediaSession.Builder(this, exo)
            .setSessionActivity(openApp)
            .setBitmapLoader(DataSourceBitmapLoader(this))
            .build()
        PlayerHolder.player = exo
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = mediaSession?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        player = null
        PlayerHolder.player = null
        super.onDestroy()
    }

    private companion object {
        const val CHANNEL_ID = "jijian_playback"
        const val NOTIFICATION_ID = 1001
    }
}