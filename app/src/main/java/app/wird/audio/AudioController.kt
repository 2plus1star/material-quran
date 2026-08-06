package app.wird.audio

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.wird.data.Ayah
import app.wird.data.AudioStore
import app.wird.data.Reciter
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** How many times to recite each ayah before moving on. -1 = forever. */
enum class RepeatCount(val times: Int, val label: String) {
    ONCE(1, "1×"),
    THRICE(3, "3×"),
    FIVE(5, "5×"),
    FOREVER(-1, "∞"),
}

/**
 * Wraps a MediaController to the playback service. Every ayah is one
 * MediaItem whose mediaId is its global ayah id — follow-along highlighting
 * falls out of onMediaItemTransition for free.
 */
class AudioController(private val context: Context) {

    private var controller: MediaController? = null
    private val store = AudioStore(context)

    /** Global ayah id currently sounding, or null when idle. */
    val currentAyahId = MutableStateFlow<Int?>(null)
    val isPlaying = MutableStateFlow(false)
    val repeat = MutableStateFlow(RepeatCount.ONCE)

    private var repeatsDone = 0

    fun connect(onReady: () -> Unit = {}) {
        if (controller != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            val c = runCatching { future.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(listener)
            // Seed from the live player before anything else.
            //
            // A MediaController does not replay onIsPlayingChanged or
            // onMediaItemTransition to a listener added after the fact. So if
            // playback was already running in the service — the Activity was
            // evicted, or the user swiped it away and came back — the UI showed
            // ▶ with no ayah highlighted while audio was audibly playing, and
            // tapping ▶ rebuilt the playlist and threw the recitation backwards
            // to wherever the list happened to be scrolled.
            isPlaying.value = c.isPlaying
            currentAyahId.value = c.currentMediaItem?.mediaId?.toIntOrNull()
            onReady()
        }, MoreExecutors.directExecutor())
    }

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            currentAyahId.value = mediaItem?.mediaId?.toIntOrNull()
            val c = controller ?: return
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
                repeatsDone++
                val wanted = repeat.value.times
                if (wanted != -1 && repeatsDone >= wanted - 1) {
                    c.repeatMode = Player.REPEAT_MODE_OFF
                }
            } else {
                // New ayah (auto/seek/playlist): re-arm the per-ayah repeat.
                repeatsDone = 0
                applyRepeatMode()
            }
        }

        override fun onIsPlayingChanged(playing: Boolean) {
            isPlaying.value = playing
        }

        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_ENDED) {
                currentAyahId.value = null
                isPlaying.value = false
            }
        }

        /**
         * Without this, a failure was completely invisible: no network, a dead
         * host, or one truncated local file stops the whole playlist,
         * onIsPlayingChanged flips the button back to ▶, but currentAyahId was
         * never cleared — so the ayah that failed stayed tinted as though it
         * were still reciting, indefinitely, with nothing said.
         */
        override fun onPlayerError(error: PlaybackException) {
            currentAyahId.value = null
            isPlaying.value = false
            lastError.value = error.errorCodeName
        }
    }

    /** Set when playback fails; the UI shows it once and calls [clearError]. */
    val lastError = MutableStateFlow<String?>(null)

    fun clearError() {
        lastError.value = null
    }

    private fun applyRepeatMode() {
        val c = controller ?: return
        c.repeatMode = if (repeat.value != RepeatCount.ONCE) {
            Player.REPEAT_MODE_ONE
        } else {
            Player.REPEAT_MODE_OFF
        }
    }

    fun setRepeat(value: RepeatCount) {
        repeat.value = value
        repeatsDone = 0
        applyRepeatMode()
    }

    /**
     * Plays [ayahs] (already the desired range) starting at [startAyahId].
     *
     * [surahName] reaches the media notification and the lock screen, where
     * "2:255" alone read as a cryptic pair of numbers.
     */
    fun play(
        ayahs: List<Ayah>,
        reciter: Reciter,
        startAyahId: Int? = null,
        surahName: (Int) -> String = { "" },
    ) {
        val c = controller ?: return
        lastError.value = null
        val items = ayahs.map { ayah ->
            val name = surahName(ayah.surah)
            MediaItem.Builder()
                .setUri(store.playbackUri(reciter, ayah.surah, ayah.num))
                .setMediaId(ayah.id.toString())
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(if (name.isBlank()) "${ayah.surah}:${ayah.num}" else "$name ${ayah.num}")
                        .setArtist(reciter.name)
                        .build(),
                )
                .build()
        }
        val start = ayahs.indexOfFirst { it.id == startAyahId }.coerceAtLeast(0)
        repeatsDone = 0
        c.setMediaItems(items, start, 0L)
        applyRepeatMode()
        c.prepare()
        c.play()
    }

    fun playSingle(ayah: Ayah, reciter: Reciter, surahName: (Int) -> String = { "" }) =
        play(listOf(ayah), reciter, ayah.id, surahName)

    fun togglePause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun stop() {
        controller?.run {
            stop()
            clearMediaItems()
        }
        currentAyahId.value = null
        isPlaying.value = false
    }

    fun release() {
        controller?.release()
        controller = null
    }
}
