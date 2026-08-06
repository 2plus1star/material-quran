package app.wird.data

import kotlinx.serialization.Serializable

enum class ColorSource { WIRD, SYSTEM_DYNAMIC }

enum class DarkMode { SYSTEM, LIGHT, DARK }

enum class ReaderContextType { SURAH, JUZ, HIZB, PAGE }

/** What the reader is currently showing — a surah, juz, hizb, or page. */
@Serializable
data class ReaderContext(
    val type: ReaderContextType = ReaderContextType.SURAH,
    val number: Int = 1,
)

/**
 * The reading position, persisted continuously so a force-quit or dead
 * battery never loses the spot. firstVisibleIndex/offset restore the exact
 * scroll; ayahId is the furthest-read ayah shown on the Library "last read"
 * card; progressIndex (the last-visible row) drives the progress fraction, so
 * a surah that fits on one screen reads as fully complete.
 */
@Serializable
data class LastRead(
    val context: ReaderContext = ReaderContext(),
    val ayahId: Int = 1,
    val firstVisibleIndex: Int = 0,
    val firstVisibleOffset: Int = 0,
    val progressIndex: Int = 0,
)

@Serializable
data class Bookmark(
    val ayahId: Int,
    val createdAt: Long,
)

data class UserSettings(
    val colorSource: ColorSource = ColorSource.WIRD,
    val darkMode: DarkMode = DarkMode.SYSTEM,
    val amoledBlack: Boolean = false,
    /** Warm paper reading surface in light themes — the app's signature look. */
    val sepiaReader: Boolean = true,
    val showTranslation: Boolean = true,
    val bookMode: Boolean = false,
    val showTajweed: Boolean = false,
    val arabicScale: Float = 1.0f,
    val reciterId: String = Reciters.DEFAULT.dirName,
    val lastRead: LastRead = LastRead(),
    val bookmarks: List<Bookmark> = emptyList(),
)
