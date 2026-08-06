package app.wird.data

/**
 * Separating the Basmala from the first ayah of a surah.
 *
 * The Tanzil source prefixes "بسم الله الرحمن الرحيم " onto the text of ayah 1
 * for every surah that opens with it, so rendering the row verbatim shows
 * Al-Baqarah 2:1 as "بسم الله الرحمن الرحيم الٓمٓ" — the Basmala silently
 * absorbed into the verse and counted as part of it. Every serious mushaf sets
 * it apart as a heading.
 *
 * Four cases the naive fix gets wrong, all of them verified against the shipped
 * database rather than assumed:
 *
 *  1. **Al-Fatiha (1).** Here the Basmala *is* ayah 1 under the Kufan numbering
 *     this text uses. Stripping it would leave an empty verse and shift the
 *     surah to six ayahs. Never split surah 1.
 *  2. **At-Tawbah (9).** Opens with no Basmala at all. There is nothing to strip
 *     and no heading to draw.
 *  3. **An-Naml 27:30.** Contains the Basmala *inside* the verse, quoting
 *     Sulayman's letter. A substring search would mutilate it; matching only a
 *     *leading* occurrence, and only on ayah 1, leaves it alone.
 *  4. **At-Tin (95) and Al-Qadr (97).** The Uthmani text spells these
 *     "بِّسْمِ" — with a shadda on the bā' that no other surah carries. A byte
 *     comparison against the canonical Basmala misses exactly these two and
 *     ships them with the Basmala still glued on.
 *
 * Case 4 is why [cutIndex] compares *skeletons* — letters with all diacritics
 * removed — rather than raw strings. It is also why the cut offset is computed
 * per ayah instead of being a constant: it is 39 for most surahs and 40 for
 * those two.
 */
object Bismillah {

    /**
     * Arabic combining marks: harakat, superscript alef, hamza forms, tatweel,
     * and the Quranic annotation signs. Removing these leaves the consonantal
     * skeleton, which is stable across the spelling variants above.
     */
    private fun isMark(ch: Char): Boolean {
        val cp = ch.code
        return cp in 0x064B..0x065F ||   // harakat, shadda, sukun, hamza marks
            cp == 0x0670 ||              // superscript alef
            cp == 0x0640 ||              // tatweel
            cp in 0x06D6..0x06ED         // Quranic annotation signs, small letters
    }

    private fun skeleton(text: String): String =
        buildString(text.length) { for (ch in text) if (!isMark(ch)) append(ch) }

    /**
     * Whether this surah opens with a Basmala that should be drawn as a heading.
     * Excludes Al-Fatiha (where it is ayah 1) and At-Tawbah (where it is absent).
     */
    fun hasHeading(surah: Int): Boolean = surah != 1 && surah != 9

    /**
     * The index in [text] just past a leading Basmala and its separating space,
     * or -1 if [text] does not begin with one.
     *
     * [prefix] is the canonical Basmala read from the database itself (ayah 1 of
     * Al-Fatiha) rather than a Kotlin string literal — the text uses U+0671
     * ALEF WASLA where a hand-typed literal uses U+0627 ALEF, so a literal would
     * silently never match and the bug would look unfixed.
     */
    fun cutIndex(text: String, prefix: String): Int {
        val want = skeleton(prefix)
        if (want.isEmpty()) return -1

        var matched = 0
        var i = 0
        while (i < text.length && matched < want.length) {
            val ch = text[i]
            if (!isMark(ch)) {
                if (ch != want[matched]) return -1
                matched++
            }
            i++
        }
        if (matched != want.length) return -1

        // Trailing marks belong to the Basmala's final letter, not to the verse.
        while (i < text.length && isMark(text[i])) i++
        // Require a separator, so a verse that merely *starts* like the Basmala
        // without being it is never truncated.
        return if (i < text.length && text[i] == ' ') i + 1 else -1
    }

    /**
     * Strips a leading Basmala from ayah 1 of a heading surah. Any other ayah,
     * and any surah in [hasHeading]'s exclusion list, is returned untouched.
     */
    fun stripFrom(text: String, surah: Int, ayah: Int, prefix: String): String {
        if (ayah != 1 || !hasHeading(surah)) return text
        val cut = cutIndex(text, prefix)
        return if (cut > 0) text.substring(cut) else text
    }

    /**
     * Re-bases tajweed spans after [cut] characters have been removed from the
     * front of the text. Spans lying wholly inside the removed Basmala are
     * dropped (they are drawn on the heading instead, from Al-Fatiha's own
     * spans); a span straddling the boundary is clamped rather than discarded.
     */
    fun shiftSpans(spans: List<TajweedSpan>, cut: Int): List<TajweedSpan> {
        if (cut <= 0) return spans
        return spans.mapNotNull { span ->
            when {
                span.end <= cut -> null
                span.start >= cut -> TajweedSpan(span.rule, span.start - cut, span.end - cut)
                else -> TajweedSpan(span.rule, 0, span.end - cut)
            }
        }
    }
}
