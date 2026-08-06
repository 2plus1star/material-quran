package app.wird.data

import java.util.Locale

/**
 * Per-ayah recitations hosted by everyayah.com (mirrored at
 * verses.quran.com / audio.qurancdn.com). File pattern: {dir}/SSSAAA.mp3.
 * Directory names verified against everyayah's catalog.
 */
data class Reciter(
    val dirName: String,
    val name: String,
    val style: String = "Murattal",
)

object Reciters {
    // Directory names verified live against everyayah.com/data/ (2026-07).
    val ALL = listOf(
        Reciter("Alafasy_128kbps", "Mishary Alafasy"),
        Reciter("Husary_128kbps", "Mahmoud Al-Husary"),
        Reciter("Husary_Muallim_128kbps", "Al-Husary (Muallim)", "Teaching"),
        Reciter("Minshawy_Murattal_128kbps", "Mohamed Al-Minshawy"),
        Reciter("Minshawy_Mujawwad_192kbps", "Al-Minshawy (Mujawwad)", "Mujawwad"),
        Reciter("Abdul_Basit_Murattal_192kbps", "Abdul Basit"),
        Reciter("Abdul_Basit_Mujawwad_128kbps", "Abdul Basit (Mujawwad)", "Mujawwad"),
        Reciter("Abdurrahmaan_As-Sudais_192kbps", "Abdurrahman As-Sudais"),
        Reciter("Saood_ash-Shuraym_128kbps", "Saud Ash-Shuraym"),
        // NB: this one really has no underscores on everyayah.
        Reciter("MaherAlMuaiqly128kbps", "Maher Al-Muaiqly"),
        Reciter("Ghamadi_40kbps", "Saad Al-Ghamdi"),
        Reciter("Hani_Rifai_192kbps", "Hani Ar-Rifai"),
        Reciter("Hudhaify_128kbps", "Ali Al-Hudhaify"),
        Reciter("Muhammad_Ayyoub_128kbps", "Muhammad Ayyub"),
        Reciter("Abu_Bakr_Ash-Shaatree_128kbps", "Abu Bakr Ash-Shatri"),
        Reciter("Yasser_Ad-Dussary_128kbps", "Yasser Ad-Dussary"),
    )

    val DEFAULT = ALL.first()

    fun byId(dirName: String): Reciter = ALL.firstOrNull { it.dirName == dirName } ?: DEFAULT

    /**
     * Locale.ROOT is load-bearing, not decoration.
     *
     * `String.format` without an explicit locale uses the default one, and `%d`
     * renders with that locale's digits. On an Arabic, Persian or Bengali phone
     * — this app's core audience — "%03d%03d" yields "٠٠٢٢٥٥" rather than
     * "002255", so every audio URL 404s and every downloaded file is looked up
     * under a name that does not exist. Switching the phone's language would
     * also orphan every megabyte already on disk.
     */
    fun remoteUrl(reciter: Reciter, surah: Int, ayah: Int): String =
        "https://everyayah.com/data/${reciter.dirName}/${fileName(surah, ayah)}"

    /** The canonical `SSSAAA.mp3` name. Digits are always ASCII — see [remoteUrl]. */
    fun fileName(surah: Int, ayah: Int): String =
        String.format(Locale.ROOT, "%03d%03d.mp3", surah, ayah)

    /** Per-surah bundle of all ayah files — one request instead of hundreds. */
    fun surahZipUrl(reciter: Reciter, surah: Int): String =
        "https://everyayah.com/data/${reciter.dirName}/zips/" +
            String.format(Locale.ROOT, "%03d.zip", surah)
}
