package app.wird.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fixtures copied verbatim out of the shipped `quran.db`, so these tests fail if
 * the database is ever rebuilt with a differently-normalised text.
 *
 * A wrong answer in this file is not a crash — it is a Quran rendered with the
 * Basmala silently attached to the wrong verse, or a verse truncated. Nothing
 * else in the app has that consequence, so the edge cases are pinned exhaustively.
 */
class BismillahTest {

    /** Ayah 1 of Al-Fatiha *is* the Basmala; it is the canonical prefix. */
    private val prefix = "بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ"

    private val baqarah = "بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ الٓمٓ"
    private val tawbah =
        "بَرَآءَةٌ مِّنَ ٱللَّهِ وَرَسُولِهِۦٓ إِلَى ٱلَّذِينَ عَٰهَدتُّم مِّنَ ٱلْمُشْرِكِينَ"
    private val naml2730 = "إِنَّهُۥ مِن سُلَيْمَٰنَ وَإِنَّهُۥ بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ"
    private val tin = "بِّسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ وَٱلتِّينِ وَٱلزَّيْتُونِ"
    private val qadr =
        "بِّسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ إِنَّآ أَنزَلْنَٰهُ فِى لَيْلَةِ ٱلْقَدْرِ"

    /**
     * The whole reason [Bismillah.cutIndex] compares skeletons instead of raw
     * strings. If this ever fails, the fixtures were normalised on the way into
     * the file and every other test here is testing the wrong thing.
     */
    @Test
    fun `the text uses alef wasla, not plain alef`() {
        assertTrue("expected U+0671 ALEF WASLA in the Basmala", prefix.contains('ٱ'))
        assertTrue(
            "a hand-typed literal with plain alef must NOT match — that is the trap",
            !prefix.contains("اللَّهِ"),
        )
    }

    @Test
    fun `strips the Basmala from ayah 1 of an ordinary surah`() {
        val body = Bismillah.stripFrom(baqarah, surah = 2, ayah = 1, prefix = prefix)
        assertEquals("الٓمٓ", body)
    }

    /** Al-Fatiha: the Basmala *is* ayah 1. Stripping it would empty the verse. */
    @Test
    fun `never strips Al-Fatiha`() {
        assertEquals(prefix, Bismillah.stripFrom(prefix, surah = 1, ayah = 1, prefix = prefix))
        assertTrue(!Bismillah.hasHeading(1))
    }

    /** At-Tawbah opens with no Basmala at all. */
    @Test
    fun `never touches At-Tawbah`() {
        assertEquals(tawbah, Bismillah.stripFrom(tawbah, surah = 9, ayah = 1, prefix = prefix))
        assertTrue(!Bismillah.hasHeading(9))
    }

    /**
     * An-Naml 27:30 quotes the Basmala inside the verse. A substring search
     * would mutilate scripture; only a *leading* match on ayah 1 may be cut.
     */
    @Test
    fun `never touches the Basmala inside An-Naml 27 30`() {
        assertEquals(naml2730, Bismillah.stripFrom(naml2730, surah = 27, ayah = 30, prefix = prefix))
        assertEquals(-1, Bismillah.cutIndex(naml2730, prefix))
    }

    /**
     * At-Tin and Al-Qadr spell it "بِّسْمِ" with a shadda on the bā'. A byte
     * comparison misses exactly these two and ships them still glued together.
     */
    @Test
    fun `handles the shadda spelling in At-Tin and Al-Qadr`() {
        assertNotEquals("fixture should differ from the canonical spelling", prefix, tin.take(38))
        assertEquals("وَٱلتِّينِ وَٱلزَّيْتُونِ", Bismillah.stripFrom(tin, 95, 1, prefix))
        assertEquals(
            "إِنَّآ أَنزَلْنَٰهُ فِى لَيْلَةِ ٱلْقَدْرِ",
            Bismillah.stripFrom(qadr, 97, 1, prefix),
        )
    }

    /** The cut offset is not a constant — 39 normally, 40 where the shadda is. */
    @Test
    fun `cut offset varies by spelling`() {
        assertEquals(39, Bismillah.cutIndex(baqarah, prefix))
        assertEquals(40, Bismillah.cutIndex(tin, prefix))
    }

    @Test
    fun `only ayah 1 is ever stripped`() {
        val notFirst = Bismillah.stripFrom(baqarah, surah = 2, ayah = 2, prefix = prefix)
        assertEquals(baqarah, notFirst)
    }

    @Test
    fun `a verse merely starting like the Basmala is not truncated`() {
        // No separating space after the Basmala: not an opening, do not cut.
        val glued = prefix + "ٱلْحَمْدُ"
        assertEquals(-1, Bismillah.cutIndex(glued, prefix))
    }

    @Test
    fun `an empty prefix never matches`() {
        assertEquals(-1, Bismillah.cutIndex(baqarah, ""))
    }

    // --- tajweed span re-basing -------------------------------------------

    @Test
    fun `spans after the cut shift by exactly the cut`() {
        val spans = listOf(TajweedSpan(13, 40, 42), TajweedSpan(13, 42, 44))
        val shifted = Bismillah.shiftSpans(spans, 39)
        assertEquals(listOf(TajweedSpan(13, 1, 3), TajweedSpan(13, 3, 5)), shifted)
    }

    @Test
    fun `spans inside the removed Basmala are dropped`() {
        val spans = listOf(TajweedSpan(0, 0, 6), TajweedSpan(0, 10, 20), TajweedSpan(13, 40, 42))
        val shifted = Bismillah.shiftSpans(spans, 39)
        assertEquals(listOf(TajweedSpan(13, 1, 3)), shifted)
    }

    @Test
    fun `a span straddling the cut is clamped rather than dropped`() {
        val shifted = Bismillah.shiftSpans(listOf(TajweedSpan(7, 35, 45)), 39)
        assertEquals(listOf(TajweedSpan(7, 0, 6)), shifted)
    }

    @Test
    fun `no cut leaves spans untouched`() {
        val spans = listOf(TajweedSpan(1, 3, 9))
        assertEquals(spans, Bismillah.shiftSpans(spans, 0))
    }

    @Test
    fun `shifted spans never carry a negative index`() {
        val shifted = Bismillah.shiftSpans(listOf(TajweedSpan(2, 0, 100)), 39)
        assertTrue(shifted.all { it.start >= 0 && it.end > it.start })
    }
}
