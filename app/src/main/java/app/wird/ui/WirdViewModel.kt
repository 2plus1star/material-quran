package app.wird.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.wird.audio.AudioController
import app.wird.audio.RepeatCount
import app.wird.data.AudioStore
import app.wird.data.Ayah
import app.wird.data.ColorSource
import app.wird.data.DarkMode
import app.wird.data.DivisionInfo
import app.wird.data.LastRead
import app.wird.data.QuranDb
import app.wird.data.ReaderContext
import app.wird.data.ReaderContextType
import app.wird.data.Reciter
import app.wird.data.Reciters
import app.wird.data.SettingsRepository
import app.wird.data.Surah
import app.wird.data.TajweedAyah
import app.wird.data.UserSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ReaderUiState(
    val context: ReaderContext = ReaderContext(),
    val title: String = "",
    val subtitle: String = "",
    val ayahs: List<Ayah> = emptyList(),
    val tajweed: Map<Int, TajweedAyah> = emptyMap(),
    /**
     * The Basmala heading, drawn above ayah 1 of every surah that opens with
     * one. It is stripped out of the ayah text in [QuranDb] so that it is not
     * counted as part of the first verse.
     */
    val basmala: TajweedAyah? = null,
    /** Index+offset to restore scroll when (re)entering the reader. */
    val restoreIndex: Int = 0,
    val restoreOffset: Int = 0,
)

class WirdViewModel(application: Application) : AndroidViewModel(application) {

    private val db = QuranDb.get(application)
    private val settingsRepo = SettingsRepository(application)
    val audioStore = AudioStore(application)
    val audio = AudioController(application)

    val settings: StateFlow<UserSettings?> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    var surahs by mutableStateOf<List<Surah>>(emptyList())
        private set
    var juzList by mutableStateOf<List<DivisionInfo>>(emptyList())
        private set
    var hizbList by mutableStateOf<List<DivisionInfo>>(emptyList())
        private set
    var pageList by mutableStateOf<List<DivisionInfo>>(emptyList())
        private set

    val reader = MutableStateFlow(ReaderUiState())

    /** An ayah the reader should scroll to (list tap or bookmark); consumed once. */
    val pendingScrollAyah = MutableStateFlow<Int?>(null)

    /** An ayah the reader should also SELECT (bookmark tap only); consumed once. */
    val pendingSelectAyah = MutableStateFlow<Int?>(null)

    init {
        audio.connect()
        viewModelScope.launch {
            surahs = db.surahs()
            juzList = db.divisions(ReaderContextType.JUZ)
            hizbList = db.divisions(ReaderContextType.HIZB)
            pageList = db.divisions(ReaderContextType.PAGE)
            // Restore the reader to the persisted position so the tab is
            // instantly meaningful even before "Continue" is tapped.
            val last = settingsRepo.current().lastRead
            openContext(last.context, restore = last)
        }
    }

    private suspend fun titleFor(context: ReaderContext): Pair<String, String> = when (context.type) {
        ReaderContextType.SURAH -> {
            val s = db.surah(context.number)
            (s?.tname ?: "Surah ${context.number}") to (s?.nameAr ?: "")
        }
        ReaderContextType.JUZ -> "Juz ${context.number}" to ""
        ReaderContextType.HIZB -> "Hizb ${context.number}" to ""
        ReaderContextType.PAGE -> "Page ${context.number}" to ""
    }

    fun openContext(
        context: ReaderContext,
        scrollToAyahId: Int? = null,
        selectAyahId: Int? = null,
        restore: LastRead? = null,
    ) {
        viewModelScope.launch {
            val ayahs = db.ayahsFor(context)
            if (ayahs.isEmpty()) return@launch
            val basmala = db.basmala()
            val tajweed = if (settingsRepo.current().showTajweed) {
                db.tajweedFor(ayahs.map { it.id })
            } else {
                emptyMap()
            }
            val (title, subtitle) = titleFor(context)
            reader.value = ReaderUiState(
                context = context,
                title = title,
                subtitle = subtitle,
                ayahs = ayahs,
                tajweed = tajweed,
                basmala = basmala,
                restoreIndex = restore?.firstVisibleIndex ?: 0,
                restoreOffset = restore?.firstVisibleOffset ?: 0,
            )
            if (scrollToAyahId != null) pendingScrollAyah.value = scrollToAyahId
            if (selectAyahId != null) pendingSelectAyah.value = selectAyahId
        }
    }

    /** Reload tajweed map when the toggle flips while the reader is populated. */
    fun refreshTajweed() {
        viewModelScope.launch {
            val state = reader.value
            if (state.ayahs.isEmpty()) return@launch
            val tajweed = if (settingsRepo.current().showTajweed) {
                db.tajweedFor(state.ayahs.map { it.id })
            } else {
                emptyMap()
            }
            reader.value = state.copy(tajweed = tajweed)
        }
    }

    fun consumeScroll() {
        pendingScrollAyah.value = null
    }

    fun consumeSelect() {
        pendingSelectAyah.value = null
    }

    /**
     * Continuous position save — survives force-quit and dead batteries. The
     * context is captured at the call site (not re-read here) so a save is
     * always attributed to the context it was measured in. Also updates the
     * in-memory restore point so a re-created reader (tab switch) resumes at
     * the live scroll rather than a stale index.
     */
    fun saveReadingPosition(
        context: ReaderContext,
        ayahId: Int,
        firstVisibleIndex: Int,
        firstVisibleOffset: Int,
        progressIndex: Int,
    ) {
        val state = reader.value
        if (state.context == context) {
            reader.value = state.copy(
                restoreIndex = firstVisibleIndex,
                restoreOffset = firstVisibleOffset,
            )
        }
        viewModelScope.launch {
            settingsRepo.setLastRead(
                LastRead(
                    context = context,
                    ayahId = ayahId,
                    firstVisibleIndex = firstVisibleIndex,
                    firstVisibleOffset = firstVisibleOffset,
                    progressIndex = progressIndex,
                ),
            )
        }
    }

    suspend fun ayahById(id: Int): Ayah? = db.ayah(id)

    suspend fun surahOf(ayahId: Int): Surah? = db.ayah(ayahId)?.let { db.surah(it.surah) }

    suspend fun ayahsFor(context: ReaderContext): List<Ayah> = db.ayahsFor(context)

    /** Tapping the Library's Last-read card: re-open that context at its position. */
    fun resumeLastRead() {
        viewModelScope.launch {
            val last = settingsRepo.current().lastRead
            openContext(last.context, restore = last)
        }
    }

    // ---- audio ----

    fun currentReciter(): Reciter = Reciters.byId(settings.value?.reciterId ?: Reciters.DEFAULT.dirName)

    /**
     * Surah name for the media notification and lock screen. Without it the
     * notification reads "2:1", which is a pair of numbers rather than a place
     * in the Quran.
     */
    private fun surahName(id: Int): String =
        surahs.firstOrNull { it.id == id }?.tname ?: ""

    fun playFrom(ayahId: Int) {
        val state = reader.value
        audio.play(state.ayahs, currentReciter(), ayahId, ::surahName)
    }

    fun playRange(startIndex: Int, endIndex: Int) {
        val state = reader.value
        val slice = state.ayahs.subList(
            startIndex.coerceIn(0, state.ayahs.lastIndex),
            (endIndex + 1).coerceIn(1, state.ayahs.size),
        )
        if (slice.isNotEmpty()) audio.play(slice, currentReciter(), slice.first().id, ::surahName)
    }

    fun playSingle(ayah: Ayah) = audio.playSingle(ayah, currentReciter(), ::surahName)

    fun setRepeat(value: RepeatCount) = audio.setRepeat(value)

    // ---- settings ----

    fun setColorSource(v: ColorSource) = viewModelScope.launch { settingsRepo.setColorSource(v) }
    fun setDarkMode(v: DarkMode) = viewModelScope.launch { settingsRepo.setDarkMode(v) }
    fun setAmoledBlack(v: Boolean) = viewModelScope.launch { settingsRepo.setAmoledBlack(v) }
    fun setSepiaReader(v: Boolean) = viewModelScope.launch { settingsRepo.setSepiaReader(v) }
    fun setShowTranslation(v: Boolean) = viewModelScope.launch { settingsRepo.setShowTranslation(v) }
    fun setBookMode(v: Boolean) = viewModelScope.launch { settingsRepo.setBookMode(v) }
    fun setShowTajweed(v: Boolean) = viewModelScope.launch {
        settingsRepo.setShowTajweed(v)
        refreshTajweed()
    }
    fun setArabicScale(v: Float) = viewModelScope.launch { settingsRepo.setArabicScale(v) }
    fun setReciter(dirName: String) = viewModelScope.launch { settingsRepo.setReciter(dirName) }

    fun toggleBookmark(ayahId: Int) = viewModelScope.launch { settingsRepo.toggleBookmark(ayahId) }

    override fun onCleared() {
        audio.release()
        super.onCleared()
    }
}
