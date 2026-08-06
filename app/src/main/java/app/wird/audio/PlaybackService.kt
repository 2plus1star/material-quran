package app.wird.audio

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import app.wird.MainActivity

/**
 * One ExoPlayer inside a MediaSessionService: background playback and the
 * media notification come from the framework; the UI talks MediaController.
 */
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        // WAKE_LOCK was declared in the manifest and never used. Without this,
        // streaming a surah and pocketing the phone lets the CPU and WiFi sleep
        // mid-verse and the recitation stalls. WAKE_MODE_NETWORK adds the WiFi
        // lock that a streamed source needs; a local file only needs the CPU.
        player.setWakeMode(C.WAKE_MODE_NETWORK)
        session = MediaSession.Builder(this, player)
            // Without a session activity, tapping the media notification does
            // nothing — the one gesture every user expects from a media card.
            .setSessionActivity(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
