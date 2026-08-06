package app.wird.data

import android.content.Context
import android.net.Uri
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloaded recitation audio lives under filesDir/audio/{reciterDir}/SSSAAA.mp3.
 * Playback prefers the local file and falls back to streaming.
 */
class AudioStore(private val context: Context) {

    fun localFile(reciter: Reciter, surah: Int, ayah: Int): File =
        File(context.filesDir, "audio/${reciter.dirName}/${Reciters.fileName(surah, ayah)}")

    fun playbackUri(reciter: Reciter, surah: Int, ayah: Int): Uri {
        val local = localFile(reciter, surah, ayah)
        return if (local.isUsable()) {
            Uri.fromFile(local)
        } else {
            Uri.parse(Reciters.remoteUrl(reciter, surah, ayah))
        }
    }

    /**
     * A zero-length or obviously truncated file is worse than a missing one: it
     * satisfies `exists()`, so the UI claims the surah is downloaded, playback
     * fails silently, and the repair loop skips it forever. The shortest real
     * ayah recording is comfortably over this bound.
     */
    private fun File.isUsable(): Boolean = exists() && length() > MIN_AUDIO_BYTES

    /** True when every ayah file of the surah is on disk and non-truncated. */
    fun isSurahDownloaded(reciter: Reciter, surah: Int, ayahCount: Int): Boolean =
        (1..ayahCount).all { localFile(reciter, surah, it).isUsable() }

    fun downloadedCount(reciter: Reciter, surah: Int, ayahCount: Int): Int =
        (1..ayahCount).count { localFile(reciter, surah, it).isUsable() }

    suspend fun deleteSurah(reciter: Reciter, surah: Int, ayahCount: Int) =
        withContext(Dispatchers.IO) {
            (1..ayahCount).forEach {
                val f = localFile(reciter, surah, it)
                f.delete()
                // Sweep any staging file an interrupted fetch left behind;
                // nothing else ever removes these.
                File(f.path + PART_SUFFIX).delete()
            }
        }

    fun enqueueSurahDownload(reciter: Reciter, surah: Int, ayahCount: Int) {
        val request = OneTimeWorkRequestBuilder<SurahDownloadWorker>()
            .setInputData(
                workDataOf(
                    SurahDownloadWorker.KEY_RECITER to reciter.dirName,
                    SurahDownloadWorker.KEY_SURAH to surah,
                    SurahDownloadWorker.KEY_COUNT to ayahCount,
                ),
            )
            // NOT setExpedited: an expedited CoroutineWorker that doesn't override
            // getForegroundInfo() throws "Worker was marked important but did not
            // provide ForegroundInfo" on every device below API 31, before
            // doWork() is ever entered. With minSdk 26 that silently broke every
            // download on Android 8-11.
            //
            // A network constraint is what we actually wanted anyway: in airplane
            // mode the old request ran immediately, burned its three attempts on
            // backoff and gave up, where this simply waits and then succeeds.
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
            )
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(workName(reciter, surah), ExistingWorkPolicy.KEEP, request)
    }

    fun cancelSurahDownload(reciter: Reciter, surah: Int) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(reciter, surah))
    }

    fun downloadRunning(reciter: Reciter, surah: Int): Flow<Boolean> =
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow(workName(reciter, surah))
            .map { infos -> infos.any { !it.state.isFinished } }

    /**
     * Whether the last attempt ended in failure. FAILED counts as "finished", so
     * without this a failed download is indistinguishable from one that was
     * never started — the spinner just disappears and the user is told nothing.
     */
    fun downloadFailed(reciter: Reciter, surah: Int): Flow<Boolean> =
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow(workName(reciter, surah))
            .map { infos ->
                val latest = infos.maxByOrNull { it.state.ordinal }
                latest?.state == WorkInfo.State.FAILED
            }

    /** Download progress 0..100 while running, or -1 when idle/finished. */
    fun downloadProgress(reciter: Reciter, surah: Int): Flow<Int> =
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow(workName(reciter, surah))
            .map { infos ->
                infos.firstOrNull { !it.state.isFinished }
                    ?.progress?.getInt(SurahDownloadWorker.KEY_PERCENT, 0) ?: -1
            }

    private fun workName(reciter: Reciter, surah: Int) = "dl-${reciter.dirName}-$surah"

    companion object {
        const val PART_SUFFIX = ".part"

        /** Below this, a file is a truncated write rather than a recitation. */
        const val MIN_AUDIO_BYTES = 1024L
    }
}

class SurahDownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val reciter = Reciters.byId(inputData.getString(KEY_RECITER) ?: return@withContext Result.failure())
        val surah = inputData.getInt(KEY_SURAH, 0)
        val count = inputData.getInt(KEY_COUNT, 0)
        if (surah !in 1..114 || count <= 0) return@withContext Result.failure()

        val store = AudioStore(applicationContext)
        val dir = store.localFile(reciter, surah, 1).parentFile ?: return@withContext Result.failure()
        dir.mkdirs()

        // Preferred path: everyayah's per-surah zip — one request, not hundreds,
        // with byte-level progress so the UI shows real movement.
        if (downloadZip(reciter, surah, dir) &&
            store.isSurahDownloaded(reciter, surah, count)
        ) {
            return@withContext Result.success()
        }

        // Fallback: fetch missing ayah files individually, progress = ayah index.
        var failures = 0
        for (ayah in 1..count) {
            val target = store.localFile(reciter, surah, ayah)
            // length check, not exists(): a truncated file left by an earlier
            // interrupted run must be re-fetched, not skipped forever.
            if (target.exists() && target.length() > AudioStore.MIN_AUDIO_BYTES) continue
            val ok = try {
                fetch(URL(Reciters.remoteUrl(reciter, surah, ayah)), target)
                true
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
            if (!ok) failures++
            setProgress(workDataOf(KEY_PERCENT to (ayah * 100 / count)))
        }
        if (failures == 0) Result.success() else if (runAttemptCount < 3) Result.retry() else Result.failure()
    }

    private suspend fun downloadZip(reciter: Reciter, surah: Int, dir: File): Boolean = try {
        val zip = File(applicationContext.cacheDir, "surah-$surah-${reciter.dirName}.zip")
        val conn = URL(Reciters.surahZipUrl(reciter, surah)).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 60_000
        try {
            if (conn.responseCode != 200) error("HTTP ${conn.responseCode}")
            val total = conn.contentLengthLong.coerceAtLeast(1L)
            val tmp = File(zip.path + AudioStore.PART_SUFFIX)
            conn.inputStream.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var read = input.read(buf)
                    var done = 0L
                    var lastPct = -1
                    while (read >= 0) {
                        out.write(buf, 0, read)
                        done += read
                        val pct = (done * 100 / total).toInt().coerceIn(0, 100)
                        if (pct != lastPct) {
                            lastPct = pct
                            setProgress(workDataOf(KEY_PERCENT to pct))
                        }
                        read = input.read(buf)
                    }
                }
            }
            tmp.renameTo(zip)
        } finally {
            conn.disconnect()
        }
        java.util.zip.ZipInputStream(zip.inputStream().buffered()).use { zin ->
            var entry = zin.nextEntry
            while (entry != null) {
                // File(entry.name).name discards any path component, so a crafted
                // archive cannot escape the directory (zip-slip).
                val name = File(entry.name).name
                if (!entry.isDirectory && name.endsWith(".mp3")) {
                    // Stage then rename, exactly as the single-file path does.
                    // Writing straight to the final path meant a worker killed
                    // mid-extraction left half-written mp3s that reported as
                    // "Downloaded", played silence, and could never self-heal.
                    val out = File(dir, name)
                    val tmp = File(out.path + AudioStore.PART_SUFFIX)
                    tmp.outputStream().use { zin.copyTo(it) }
                    if (!tmp.renameTo(out)) tmp.delete()
                }
                zin.closeEntry()
                entry = zin.nextEntry
            }
        }
        zip.delete()
        true
    } catch (e: CancellationException) {
        // runCatching would swallow this, and the worker would fall through into
        // the 286-file loop instead of stopping when WorkManager cancels it.
        throw e
    } catch (_: Exception) {
        false
    }

    private fun fetch(url: URL, target: File) {
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 60_000
        val tmp = File(target.path + AudioStore.PART_SUFFIX)
        try {
            if (conn.responseCode != 200) error("HTTP ${conn.responseCode}")
            conn.inputStream.use { input ->
                tmp.outputStream().use { input.copyTo(it) }
            }
            if (!tmp.renameTo(target)) error("rename failed")
        } finally {
            // Always clear the staging file, not only on a failed rename: an
            // IOException mid-copy used to orphan it in filesDir forever.
            tmp.delete()
            conn.disconnect()
        }
    }

    companion object {
        const val KEY_RECITER = "reciter"
        const val KEY_SURAH = "surah"
        const val KEY_COUNT = "count"
        const val KEY_PERCENT = "percent"
    }
}
