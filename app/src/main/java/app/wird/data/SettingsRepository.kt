package app.wird.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.core.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    private object Keys {
        val colorSource = stringPreferencesKey("color_source")
        val darkMode = stringPreferencesKey("dark_mode")
        val amoledBlack = booleanPreferencesKey("amoled_black")
        val sepiaReader = booleanPreferencesKey("sepia_reader")
        val showTranslation = booleanPreferencesKey("show_translation")
        val bookMode = booleanPreferencesKey("book_mode")
        val showTajweed = booleanPreferencesKey("show_tajweed")
        val arabicScale = floatPreferencesKey("arabic_scale")
        val reciter = stringPreferencesKey("reciter")
        val lastRead = stringPreferencesKey("last_read")
        val bookmarks = stringPreferencesKey("bookmarks")
    }

    val settings: Flow<UserSettings> = context.settingsDataStore.data
        // Without this, a corrupt preferences file throws inside the flow, and
        // because every screen collects it inside stateIn(viewModelScope) the
        // exception is uncaught: crash on launch, every launch.
        .catch { cause ->
            if (cause is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw cause
        }
        .map { p -> p.toSettings() }

    suspend fun current(): UserSettings = settings.first()

    private fun Preferences.toSettings(): UserSettings = UserSettings(
        colorSource = enum(this[Keys.colorSource], ColorSource.WIRD),
        darkMode = enum(this[Keys.darkMode], DarkMode.SYSTEM),
        amoledBlack = this[Keys.amoledBlack] ?: false,
        sepiaReader = this[Keys.sepiaReader] ?: true,
        showTranslation = this[Keys.showTranslation] ?: true,
        bookMode = this[Keys.bookMode] ?: false,
        showTajweed = this[Keys.showTajweed] ?: false,
        arabicScale = this[Keys.arabicScale] ?: 1.0f,
        reciterId = this[Keys.reciter] ?: Reciters.DEFAULT.dirName,
        lastRead = decode(this[Keys.lastRead]) ?: LastRead(),
        bookmarks = decode(this[Keys.bookmarks]) ?: emptyList(),
    )

    private inline fun <reified E : Enum<E>> enum(raw: String?, default: E): E =
        raw?.let { runCatching { enumValueOf<E>(it) }.getOrNull() } ?: default

    private inline fun <reified T> decode(raw: String?): T? =
        raw?.let { runCatching { json.decodeFromString<T>(it) }.getOrNull() }

    suspend fun setColorSource(v: ColorSource) = put(Keys.colorSource, v.name)
    suspend fun setDarkMode(v: DarkMode) = put(Keys.darkMode, v.name)
    suspend fun setAmoledBlack(v: Boolean) = put(Keys.amoledBlack, v)
    suspend fun setSepiaReader(v: Boolean) = put(Keys.sepiaReader, v)
    suspend fun setShowTranslation(v: Boolean) = put(Keys.showTranslation, v)
    suspend fun setBookMode(v: Boolean) = put(Keys.bookMode, v)
    suspend fun setShowTajweed(v: Boolean) = put(Keys.showTajweed, v)
    suspend fun setArabicScale(v: Float) = put(Keys.arabicScale, v.coerceIn(0.8f, 1.8f))
    suspend fun setReciter(dirName: String) = put(Keys.reciter, dirName)

    suspend fun setLastRead(value: LastRead) =
        put(Keys.lastRead, json.encodeToString(LastRead.serializer(), value))

    /**
     * @return true if the bookmark was added, false if removed or refused.
     *
     * The refusal case is the point. `decode` returns null on any parse failure
     * — a partial write, a schema change, an R8 serializer problem — and the old
     * code treated that as "no bookmarks", so the screen showed the empty state
     * and then the very next tap wrote a one-element list over the top,
     * destroying every bookmark the user had ever made. A stored value that
     * fails to parse is now left strictly alone.
     */
    suspend fun toggleBookmark(ayahId: Int): Boolean {
        var added = false
        context.settingsDataStore.edit { prefs ->
            val raw = prefs[Keys.bookmarks]
            val current: List<Bookmark>? = if (raw == null) emptyList() else decode(raw)
            if (current == null) return@edit
            val existing = current.firstOrNull { it.ayahId == ayahId }
            val next = if (existing != null) {
                current - existing
            } else {
                added = true
                current + Bookmark(ayahId, System.currentTimeMillis())
            }
            prefs[Keys.bookmarks] = json.encodeToString(next)
        }
        return added
    }

    private suspend fun put(key: Preferences.Key<String>, value: String) {
        context.settingsDataStore.edit { it[key] = value }
    }

    private suspend fun put(key: Preferences.Key<Boolean>, value: Boolean) {
        context.settingsDataStore.edit { it[key] = value }
    }

    private suspend fun put(key: Preferences.Key<Float>, value: Float) {
        context.settingsDataStore.edit { it[key] = value }
    }
}
