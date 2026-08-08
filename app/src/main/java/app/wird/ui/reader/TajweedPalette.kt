package app.wird.ui.reader

import androidx.compose.ui.graphics.Color

/**
 * The Dar al-Maarifah digital tajweed palette (the de-facto standard),
 * index-aligned with the rule ids baked into quran.db by tools/build_db.py:
 * ghunnah, idghaam_ghunnah, idghaam_no_ghunnah, idghaam_mutajaanisain,
 * idghaam_mutaqaaribain, idghaam_shafawi, ikhfa, ikhfa_shafawi, iqlab,
 * madd_2, madd_246, madd_muttasil, madd_munfasil, madd_6, qalqalah,
 * hamzat_wasl, lam_shamsiyyah, silent.
 */
object TajweedPalette {

    private val light = listOf(
        Color(0xFFFF7E1E), // ghunnah
        Color(0xFF169200), // idghaam_ghunnah
        Color(0xFF169200), // idghaam_no_ghunnah
        Color(0xFFA1A1A1), // idghaam_mutajaanisain
        Color(0xFFA1A1A1), // idghaam_mutaqaaribain
        Color(0xFF58B800), // idghaam_shafawi
        Color(0xFF9400A8), // ikhfa
        Color(0xFFD500B7), // ikhfa_shafawi
        Color(0xFF26BFFD), // iqlab
        Color(0xFF537FFF), // madd_2
        Color(0xFF4050FF), // madd_246
        Color(0xFF2144C1), // madd_muttasil
        Color(0xFF2144C1), // madd_munfasil
        Color(0xFF000EBC), // madd_6
        Color(0xFFDD0008), // qalqalah
        Color(0xFF8A8A8A), // hamzat_wasl
        Color(0xFF8A8A8A), // lam_shamsiyyah
        Color(0xFF8A8A8A), // silent
    )

    /** Dark/AMOLED variant: greys lifted, deep blues brightened for contrast. */
    private val dark = listOf(
        Color(0xFFFF9A4D), // ghunnah
        Color(0xFF4FC22E), // idghaam_ghunnah
        Color(0xFF4FC22E), // idghaam_no_ghunnah
        Color(0xFF9E9E9E), // idghaam_mutajaanisain
        Color(0xFF9E9E9E), // idghaam_mutaqaaribain
        Color(0xFF7ED255), // idghaam_shafawi
        Color(0xFFCE5BE0), // ikhfa
        Color(0xFFF163DD), // ikhfa_shafawi
        Color(0xFF55CBFF), // iqlab
        Color(0xFF7D9DFF), // madd_2
        Color(0xFF7580FF), // madd_246
        Color(0xFF6E8CE8), // madd_muttasil
        Color(0xFF6E8CE8), // madd_munfasil
        Color(0xFF5A6CFF), // madd_6
        Color(0xFFFF6B6B), // qalqalah
        Color(0xFF9E9E9E), // hamzat_wasl
        Color(0xFF9E9E9E), // lam_shamsiyyah
        Color(0xFF9E9E9E), // silent
    )

    fun color(rule: Int, isDark: Boolean): Color? =
        (if (isDark) dark else light).getOrNull(rule)

    /**
     * What each colour means, for the legend in Settings.
     *
     * Tajweed colouring shipped with no key at all, which made it decoration:
     * eighteen colours appeared in the text and nothing anywhere in the app said
     * what any of them were. Rules that share a colour share a line, so the list
     * is thirteen entries rather than eighteen identical-looking ones.
     */
    data class Entry(val label: String, val rule: Int)

    val legend = listOf(
        Entry("Ghunnah", 0),
        Entry("Idghaam", 1),
        Entry("Idghaam mutajaanisain and mutaqaaribain", 3),
        Entry("Idghaam shafawi", 5),
        Entry("Ikhfa", 6),
        Entry("Ikhfa shafawi", 7),
        Entry("Iqlab", 8),
        Entry("Madd, 2 counts", 9),
        Entry("Madd, 2 4 or 6 counts", 10),
        Entry("Madd muttasil and munfasil", 11),
        Entry("Madd, 6 counts", 13),
        Entry("Qalqalah", 14),
        Entry("Hamzat wasl, lam shamsiyyah, silent letters", 15),
    )
}
