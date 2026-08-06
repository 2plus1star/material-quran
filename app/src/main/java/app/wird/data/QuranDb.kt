package app.wird.data

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class Surah(
    val id: Int,
    val nameAr: String,
    val tname: String,
    val ename: String,
    val revelation: String,
    val ayahCount: Int,
    val startAyahId: Int,
    val startPage: Int,
)

data class Ayah(
    val id: Int,          // global 1..6236
    val surah: Int,
    val num: Int,
    val text: String,     // Uthmani minimal — cleanest with Noto Sans Arabic
    val translation: String,
    /**
     * Publisher footnotes for this ayah, or empty. The translation text carries
     * inline markers like "[2]" that QuranEnc's terms forbid us from removing,
     * so the notes they point at have to be shown somewhere.
     */
    val footnotes: String,
    val page: Int,
    val juz: Int,
    val hizb: Int,
    val sajdah: Boolean,
)

data class TajweedSpan(val rule: Int, val start: Int, val end: Int)

data class TajweedAyah(val text: String, val spans: List<TajweedSpan>)

/** A row for the Juz / Hizb / Page index tabs. */
data class DivisionInfo(
    val number: Int,
    val startSurahTname: String,
    val startSurahAr: String,
    val startAyahId: Int,
)

/**
 * Read-only access to the bundled Tanzil database (shipped verbatim,
 * attribution in Settings). The asset is copied once to the databases dir.
 */
class QuranDb(private val context: Context) {

    @Volatile
    private var db: SQLiteDatabase? = null

    /**
     * Opens the bundled database, healing a bad copy rather than dying on it.
     *
     * The old version copied the 7 MB asset straight to its final path and then
     * trusted `exists()` forever. Interrupt that copy — a low-memory kill on
     * first launch, a force-stop, a reboot, a full disk — and a truncated file
     * sits there permanently: `exists()` is true, `openDatabase` throws
     * `SQLiteDatabaseCorruptException`, and because every caller is a bare
     * `viewModelScope.launch` the app crashes on launch, every launch, until the
     * user clears data or reinstalls.
     *
     * Three changes: stage the copy and rename it (a rename is atomic, so the
     * final path is either absent or complete), re-copy when the asset's
     * `user_version` has moved on so a corrected database actually reaches
     * existing users, and delete-and-retry once if it still fails to open.
     */
    private fun open(): SQLiteDatabase {
        db?.let { return it }
        synchronized(this) {
            db?.let { return it }
            val file = File(context.getDatabasePath("quran.db").path)
            if (!file.exists()) copyAsset(file)
            return try {
                openChecked(file)
            } catch (_: Exception) {
                // Corrupt, truncated, or stale beyond repair — take the asset again.
                file.delete()
                copyAsset(file)
                openChecked(file)
            }.also { db = it }
        }
    }

    private fun copyAsset(target: File) {
        target.parentFile?.mkdirs()
        val staging = File(target.path + ".tmp")
        context.assets.open("quran.db").use { input ->
            staging.outputStream().use { input.copyTo(it) }
        }
        if (!staging.renameTo(target)) {
            staging.delete()
            error("could not install quran.db")
        }
    }

    /**
     * Opens and verifies. `build_db.py` stamps `PRAGMA user_version`; a mismatch
     * means the on-disk copy predates the shipped asset, which is exactly what
     * happens after an app update that corrects the text. Nothing read this
     * before, so a fixed database would never have reached anyone who had
     * already opened the app once.
     */
    private fun openChecked(file: File): SQLiteDatabase {
        val opened = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        val version = opened.rawQuery("PRAGMA user_version", null).use { c ->
            if (c.moveToFirst()) c.getInt(0) else -1
        }
        if (version != ASSET_VERSION) {
            opened.close()
            error("quran.db version $version, expected $ASSET_VERSION")
        }
        // Cheap sanity check that the file is whole: a truncated copy can still
        // open, and will then return short reads from the middle of the Quran.
        val ayahs = opened.rawQuery("SELECT COUNT(*) FROM ayah", null).use { c ->
            if (c.moveToFirst()) c.getInt(0) else 0
        }
        if (ayahs != AYAH_COUNT) {
            opened.close()
            error("quran.db holds $ayahs ayahs, expected $AYAH_COUNT")
        }
        return opened
    }

    /**
     * The canonical Basmala, read from the database rather than hardcoded.
     *
     * Ayah 1 of Al-Fatiha *is* the Basmala, so it is the authoritative spelling
     * for this exact text edition. A Kotlin string literal would not do: the
     * Uthmani text uses U+0671 ALEF WASLA where a typed literal uses U+0627
     * ALEF, so the comparison would silently never match.
     */
    private val basmalaText: String by lazy {
        open().rawQuery("SELECT textFull FROM ayah WHERE id = 1", null).use { c ->
            if (c.moveToFirst()) c.getString(0) else ""
        }
    }

    private val basmalaTajweedText: String by lazy {
        open().rawQuery("SELECT text FROM tajweed_text WHERE ayahId = 1", null).use { c ->
            if (c.moveToFirst()) c.getString(0) else ""
        }
    }

    /** The Basmala heading, with its own tajweed colouring, for surah openings. */
    suspend fun basmala(): TajweedAyah = withContext(Dispatchers.IO) {
        val spans = mutableListOf<TajweedSpan>()
        open().rawQuery(
            "SELECT rule, start, end FROM tajweed_span WHERE ayahId = 1",
            null,
        ).use { c ->
            while (c.moveToNext()) spans.add(TajweedSpan(c.getInt(0), c.getInt(1), c.getInt(2)))
        }
        TajweedAyah(basmalaTajweedText, spans)
    }

    suspend fun surahs(): List<Surah> = withContext(Dispatchers.IO) {
        open().rawQuery("SELECT * FROM surah ORDER BY id", null).use { c ->
            buildList { while (c.moveToNext()) add(c.surah()) }
        }
    }

    suspend fun surah(id: Int): Surah? = withContext(Dispatchers.IO) {
        open().rawQuery("SELECT * FROM surah WHERE id = ?", arrayOf("$id")).use { c ->
            if (c.moveToFirst()) c.surah() else null
        }
    }

    suspend fun ayah(id: Int): Ayah? = withContext(Dispatchers.IO) {
        open().rawQuery("SELECT * FROM ayah WHERE id = ?", arrayOf("$id")).use { c ->
            if (c.moveToFirst()) c.ayah() else null
        }
    }

    suspend fun ayahsFor(context: ReaderContext): List<Ayah> = withContext(Dispatchers.IO) {
        val column = when (context.type) {
            ReaderContextType.SURAH -> "surah"
            ReaderContextType.JUZ -> "juz"
            ReaderContextType.HIZB -> "hizb"
            ReaderContextType.PAGE -> "page"
        }
        open().rawQuery(
            "SELECT * FROM ayah WHERE $column = ? ORDER BY id",
            arrayOf("${context.number}"),
        ).use { c ->
            buildList { while (c.moveToNext()) add(c.ayah()) }
        }
    }

    suspend fun divisions(type: ReaderContextType): List<DivisionInfo> = withContext(Dispatchers.IO) {
        val column = when (type) {
            ReaderContextType.JUZ -> "juz"
            ReaderContextType.HIZB -> "hizb"
            ReaderContextType.PAGE -> "page"
            ReaderContextType.SURAH -> error("use surahs() for the surah index")
        }
        open().rawQuery(
            """
            SELECT a.$column AS n, MIN(a.id) AS start_id, s.tname, s.nameAr
            FROM ayah a JOIN surah s ON s.id = (
                SELECT surah FROM ayah WHERE $column = a.$column ORDER BY id LIMIT 1
            )
            GROUP BY a.$column ORDER BY n
            """.trimIndent(),
            null,
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        DivisionInfo(
                            number = c.getInt(0),
                            startAyahId = c.getInt(1),
                            startSurahTname = c.getString(2),
                            startSurahAr = c.getString(3),
                        ),
                    )
                }
            }
        }
    }

    /** Tajweed display text + colored spans, keyed by global ayah id. */
    suspend fun tajweedFor(ayahIds: List<Int>): Map<Int, TajweedAyah> = withContext(Dispatchers.IO) {
        if (ayahIds.isEmpty()) return@withContext emptyMap()
        val min = ayahIds.min()
        val max = ayahIds.max()
        val texts = HashMap<Int, String>()
        // How many characters were removed from the front of each text, so the
        // span offsets below can be re-based by the same amount. Span indices
        // are absolute into the tajweed text, so stripping the Basmala without
        // shifting them would paint every tajweed colour in the wrong place.
        val cuts = HashMap<Int, Int>()
        open().rawQuery(
            """
            SELECT t.ayahId, t.text, a.surah, a.num
            FROM tajweed_text t JOIN ayah a ON a.id = t.ayahId
            WHERE t.ayahId BETWEEN ? AND ?
            """.trimIndent(),
            arrayOf("$min", "$max"),
        ).use { c ->
            while (c.moveToNext()) {
                val id = c.getInt(0)
                val raw = c.getString(1)
                val surah = c.getInt(2)
                val num = c.getInt(3)
                val cut = if (num == 1 && Bismillah.hasHeading(surah)) {
                    Bismillah.cutIndex(raw, basmalaTajweedText).coerceAtLeast(0)
                } else {
                    0
                }
                cuts[id] = cut
                texts[id] = if (cut > 0) raw.substring(cut) else raw
            }
        }
        val spans = HashMap<Int, MutableList<TajweedSpan>>()
        open().rawQuery(
            "SELECT ayahId, rule, start, end FROM tajweed_span WHERE ayahId BETWEEN ? AND ?",
            arrayOf("$min", "$max"),
        ).use { c ->
            while (c.moveToNext()) {
                spans.getOrPut(c.getInt(0)) { mutableListOf() }
                    .add(TajweedSpan(c.getInt(1), c.getInt(2), c.getInt(3)))
            }
        }
        ayahIds.mapNotNull { id ->
            texts[id]?.let {
                id to TajweedAyah(it, Bismillah.shiftSpans(spans[id].orEmpty(), cuts[id] ?: 0))
            }
        }.toMap()
    }

    private fun Cursor.surah() = Surah(
        id = getInt(getColumnIndexOrThrow("id")),
        nameAr = getString(getColumnIndexOrThrow("nameAr")),
        tname = getString(getColumnIndexOrThrow("tname")),
        ename = getString(getColumnIndexOrThrow("ename")),
        revelation = getString(getColumnIndexOrThrow("revelation")),
        ayahCount = getInt(getColumnIndexOrThrow("ayahCount")),
        startAyahId = getInt(getColumnIndexOrThrow("startAyahId")),
        startPage = getInt(getColumnIndexOrThrow("startPage")),
    )

    private fun Cursor.ayah(): Ayah {
        val surah = getInt(getColumnIndexOrThrow("surah"))
        val num = getInt(getColumnIndexOrThrow("num"))
        // Full Uthmani: keeps the small waw/yeh/meem recitation helpers, which
        // HarfBuzz-shaped Noto Sans Arabic renders correctly (verified).
        val raw = getString(getColumnIndexOrThrow("textFull"))
        return Ayah(
            id = getInt(getColumnIndexOrThrow("id")),
            surah = surah,
            num = num,
            // Stripped here, once, so every reader mode inherits it: the card
            // list and the continuous mushaf flow would otherwise each have to
            // remember, and one of them would eventually forget.
            text = Bismillah.stripFrom(raw, surah, num, basmalaText),
            translation = getString(getColumnIndexOrThrow("translation")),
            footnotes = getString(getColumnIndexOrThrow("footnotes")),
            page = getInt(getColumnIndexOrThrow("page")),
            juz = getInt(getColumnIndexOrThrow("juz")),
            hizb = getInt(getColumnIndexOrThrow("hizb")),
            sajdah = getInt(getColumnIndexOrThrow("sajdah")) == 1,
        )
    }

    companion object {
        /**
         * Bump in lockstep with `PRAGMA user_version` in tools/build_db.py.
         * v2 replaced the unlicensed Tanzil en.sahih dump with QuranEnc's
         * licensed Noor International edition and added the footnotes column.
         */
        const val ASSET_VERSION = 2

        /** Ayahs in the Quran. A short count means a truncated copy. */
        const val AYAH_COUNT = 6236

        @Volatile private var instance: QuranDb? = null
        fun get(context: Context): QuranDb =
            instance ?: synchronized(this) {
                instance ?: QuranDb(context.applicationContext).also { instance = it }
            }
    }
}
