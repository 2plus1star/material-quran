package app.wird.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wird.audio.RepeatCount
import app.wird.data.Ayah
import app.wird.data.Reciters
import app.wird.data.Bismillah
import app.wird.data.UserSettings
import app.wird.ui.WirdViewModel
import app.wird.ui.components.WirdIcons
import app.wird.ui.theme.NightArabic
import app.wird.ui.theme.NotoArabic
import app.wird.ui.theme.SepiaSurface
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.isSystemInDarkTheme
import app.wird.data.DarkMode

private fun easternDigits(n: Int): String =
    n.toString().map { ch -> ('٠' + (ch - '0')) }.joinToString("")

/** Consecutive ayahs grouped by surah; a juz or page routinely spans two. */
private fun surahRuns(ayahs: List<Ayah>): List<List<Ayah>> = buildList {
    var current = mutableListOf<Ayah>()
    ayahs.forEach { ayah ->
        if (current.isNotEmpty() && current.last().surah != ayah.surah) {
            add(current)
            current = mutableListOf()
        }
        current.add(ayah)
    }
    if (current.isNotEmpty()) add(current)
}

/**
 * How many ayahs of flowing text go in one book-mode row.
 *
 * A surah used to be a single row. Al-Baqarah is then one Text of 55,000
 * characters carrying 286 inline placeholders and some 4,900 SpanStyles, and
 * Compose lays that out — shaping every glyph — in one pass on the main thread
 * before it can draw anything. Measured on an emulator that was over thirty
 * seconds of frozen UI; on a low-end phone it is comfortably past the five
 * seconds that makes Android post "app isn't responding".
 *
 * Splitting on ayah boundaries means the LazyColumn only lays out the rows near
 * the viewport. The cost is a paragraph break every twentieth verse, which is
 * what a printed mushaf does at every page anyway. Most surahs are still one row.
 */
private const val BOOK_ROW_AYAHS = 20

/** One row of the book-mode list: a Basmala heading, or a chunk of flowing text. */
private class BookRow(val isBasmala: Boolean, val ayahs: List<Ayah>, val startIndex: Int)

private fun bookRows(ayahs: List<Ayah>): List<BookRow> = buildList {
    var index = 0
    surahRuns(ayahs).forEach { run ->
        val head = run.first()
        // The heading shares its start index with the chunk below it, so
        // resolving an ayah to a row with indexOfLast lands on the text.
        if (head.num == 1 && Bismillah.hasHeading(head.surah)) {
            add(BookRow(true, listOf(head), index))
        }
        run.chunked(BOOK_ROW_AYAHS).forEach { chunk ->
            add(BookRow(false, chunk, index))
            index += chunk.size
        }
    }
}

/**
 * Which ayahs a row of the LazyColumn covers, and how the row's height is
 * shared between them.
 *
 * In card mode a row is one ayah, so a list index *is* an ayah index and
 * everything worked by accident. Book mode puts a whole surah in a single row
 * with a Basmala heading as a row of its own, and every "where am I" question in
 * the reader was still answering with the row index: the header read 2:1 for the
 * whole of Al-Baqarah, the progress bar sat at 100% from the first frame,
 * follow-along scrolled to the top of the surah instead of the sounding verse,
 * and the autosave recorded ayah 1 forever no matter how long you read.
 *
 * [cumulative] is a running character count. Interpolating by ayah *count* is
 * wrong wherever verse lengths vary: Al-Baqarah opens with a three-letter ayah
 * and follows it with several of two hundred characters, which put the reported
 * position four verses behind by the third screen. Rendered height tracks
 * characters closely enough to fix that. Null in card mode, where a row is one
 * ayah and there is nothing to share.
 */
private class ItemSpan(val range: IntRange, val cumulative: IntArray?)

private fun itemAyahSpans(ayahs: List<Ayah>, rows: List<BookRow>?): List<ItemSpan> {
    if (rows == null) return ayahs.indices.map { ItemSpan(it..it, null) }
    return rows.map { row ->
        val start = row.startIndex
        if (row.isBasmala) return@map ItemSpan(start..start, null)
        val cumulative = IntArray(row.ayahs.size)
        var total = 0
        row.ayahs.forEachIndexed { i, ayah ->
            // +4 for the verse marker and the spaces around it.
            total += ayah.text.length + 4
            cumulative[i] = total
        }
        ItemSpan(start..(start + row.ayahs.size - 1), cumulative)
    }
}

private fun LazyListState.ayahIndexAt(spans: List<ItemSpan>, atTop: Boolean): Int {
    val info = layoutInfo
    val item = (if (atTop) info.visibleItemsInfo.firstOrNull() else info.visibleItemsInfo.lastOrNull())
        ?: return 0
    val span = spans.getOrNull(item.index) ?: return 0
    val range = span.range
    val count = range.last - range.first + 1
    if (count <= 1 || item.size <= 0) return range.first
    val edge = if (atTop) info.viewportStartOffset else info.viewportEndOffset
    val fraction = (edge - item.offset).coerceIn(0, item.size).toFloat() / item.size
    val cumulative = span.cumulative
        ?: return (range.first + (fraction * count).toInt()).coerceIn(range.first, range.last)

    val target = fraction * cumulative.last()
    var low = 0
    var high = cumulative.lastIndex
    while (low < high) {
        val mid = (low + high) / 2
        if (cumulative[mid] <= target) low = mid + 1 else high = mid
    }
    return (range.first + low).coerceIn(range.first, range.last)
}

/** Where inside its row an ayah begins, as a fraction of the row's height. */
private fun ItemSpan.fractionOf(ayahIndex: Int): Float {
    val cumulative = cumulative ?: return 0f
    val k = (ayahIndex - range.first).coerceIn(0, cumulative.lastIndex)
    val before = if (k == 0) 0 else cumulative[k - 1]
    return before.toFloat() / cumulative.last()
}

@Composable
fun ReaderScreen(vm: WirdViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val s = settings ?: return
    val reader by vm.reader.collectAsStateWithLifecycle()
    val cs = MaterialTheme.colorScheme

    val dark = when (s.darkMode) {
        DarkMode.SYSTEM -> isSystemInDarkTheme()
        DarkMode.LIGHT -> false
        DarkMode.DARK -> true
    }
    val sepia = s.sepiaReader && !dark
    val pageColor = if (sepia) SepiaSurface.background else cs.surface
    val inkColor = when {
        sepia -> SepiaSurface.onBackground
        dark && s.amoledBlack -> NightArabic
        else -> cs.onSurface
    }
    val mutedColor = if (sepia) SepiaSurface.muted else cs.onSurfaceVariant

    if (reader.ayahs.isEmpty()) {
        Box(Modifier.fillMaxSize().background(pageColor), contentAlignment = Alignment.Center) {
            LoadingIndicator()
        }
        return
    }

    val listState = remember(reader.context) {
        LazyListState(reader.restoreIndex, reader.restoreOffset)
    }
    var selectedAyah by rememberSaveable(reader.context) { mutableStateOf<Int?>(null) }
    val currentAudioAyah by vm.audio.currentAyahId.collectAsStateWithLifecycle()
    val isPlaying by vm.audio.isPlaying.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var showPlaySettings by remember { mutableStateOf(false) }
    var playSettingsForSelection by remember { mutableStateOf(false) }

    val rows = remember(reader.ayahs, s.bookMode) {
        if (s.bookMode) bookRows(reader.ayahs) else null
    }
    val itemSpans = remember(reader.ayahs, rows) { itemAyahSpans(reader.ayahs, rows) }

    // Scroll request from a list row / bookmark: jump there, consume.
    val pendingScroll by vm.pendingScrollAyah.collectAsStateWithLifecycle()
    LaunchedEffect(pendingScroll, reader.ayahs) {
        val target = pendingScroll ?: return@LaunchedEffect
        val index = reader.ayahs.indexOfFirst { it.id == target }
        if (index >= 0) {
            listState.scrollToItem(index)
            vm.consumeScroll()
        }
    }
    // Select request from a bookmark only.
    val pendingSelect by vm.pendingSelectAyah.collectAsStateWithLifecycle()
    LaunchedEffect(pendingSelect, reader.ayahs) {
        val target = pendingSelect ?: return@LaunchedEffect
        if (reader.ayahs.any { it.id == target }) {
            selectedAyah = target
            vm.consumeSelect()
        }
    }

    // Follow the recitation: keep the sounding ayah comfortably on screen.
    //
    // The old test was "is this row in visibleItemsInfo", which is true of a row
    // showing one pixel at the bottom edge — so the verse being recited could sit
    // permanently just off the screen and nothing would ever scroll. And the
    // lead-in was `scrollOffset = -120`, raw pixels: 60dp of breathing room on a
    // 2x phone, 30dp on a 4x one, and 120dp on an mdpi tablet.
    val density = androidx.compose.ui.platform.LocalDensity.current
    val leadPx = with(density) { 24.dp.roundToPx() }
    val minVisiblePx = with(density) { 72.dp.roundToPx() }
    LaunchedEffect(currentAudioAyah, itemSpans) {
        val id = currentAudioAyah ?: return@LaunchedEffect
        val ayahIndex = reader.ayahs.indexOfFirst { it.id == id }
        if (ayahIndex < 0) return@LaunchedEffect
        // indexOfLast, so a Basmala heading row (which covers the same single
        // ayah index as the run that follows it) never wins over the text.
        val item = itemSpans.indexOfLast { ayahIndex in it.range }
        if (item < 0) return@LaunchedEffect

        val info = listState.layoutInfo
        val visible = info.visibleItemsInfo.firstOrNull { it.index == item }
        // Where inside the row this ayah starts. Zero in card mode, since a row
        // is one ayah; in book mode it is the offset into the flowing surah.
        val within = if (visible == null) {
            0
        } else {
            (itemSpans[item].fractionOf(ayahIndex) * visible.size).toInt()
        }
        val top = visible?.let { it.offset + within }
        val comfortable = top != null &&
            top >= info.viewportStartOffset &&
            top + minVisiblePx <= info.viewportEndOffset
        if (!comfortable) listState.animateScrollToItem(item, within - leadPx)
    }

    // Continuous last-read persistence (debounced scroll settle). Capture the
    // context + ayahs the effect was launched with so a save is never
    // mis-attributed to a context the user switched to mid-debounce.
    LaunchedEffect(listState, reader.context, itemSpans) {
        val ctx = reader.context
        val ayahs = reader.ayahs
        val spans = itemSpans
        snapshotFlow {
            // The restore point stays a raw list index + pixel offset, which is
            // what LazyListState takes back. The *progress* is an ayah index, so
            // it has to be resolved through the row ranges — in book mode the
            // last visible row is the whole surah, and reporting its index said
            // "you have read one thing" no matter where you actually were.
            Triple(
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset,
                listState.ayahIndexAt(spans, atTop = false),
            )
        }
            .debounce(600)
            .distinctUntilChanged()
            .collect { (index, offset, furthestIndex) ->
                // ayahId shows the furthest-read verse; if the whole surah fits
                // on screen, that is its final ayah → progress reads full.
                val furthest = ayahs.getOrNull(furthestIndex) ?: return@collect
                vm.saveReadingPosition(ctx, furthest.id, index, offset, furthestIndex)
            }
    }

    // Landscape, foldables and tablets: a 1200dp-wide line of 24sp Arabic is
    // unreadable, so the page is capped and centred rather than stretched. The
    // same measurement decides whether the two floating groups can sit side by
    // side — on a 360dp phone they were overlapping in the middle of the screen.
    BoxWithConstraints(Modifier.fillMaxSize().background(pageColor)) {
        val sidePad = ((maxWidth - 720.dp) / 2).coerceAtLeast(0.dp)
        val narrow = maxWidth < 420.dp
        Box(Modifier.fillMaxSize().padding(horizontal = sidePad)) {
        Column(Modifier.fillMaxSize()) {
            // Slim header: context title + position; quiet by design.
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 10.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    reader.title,
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = inkColor,
                )
                if (reader.subtitle.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        reader.subtitle,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontFamily = NotoArabic,
                            textDirection = TextDirection.Rtl,
                        ),
                        color = mutedColor,
                    )
                }
                Spacer(Modifier.weight(1f))
                PositionLabel(listState, reader.ayahs, itemSpans, mutedColor)
            }

            // Progress through the CURRENT surah/juz/hizb/page. Driven by the
            // LAST visible ayah (not the first), so a short surah that fits on
            // one screen reads as complete. Evaluated in the draw phase, so
            // scrolling never recomposes the reader.
            val readingProgress = {
                val total = reader.ayahs.size.coerceAtLeast(1)
                ((listState.ayahIndexAt(itemSpans, atTop = false) + 1f) / total)
                    .coerceIn(0f, 1f)
            }
            if (isPlaying) {
                // Playing: the wave animates and *means* audio is sounding.
                LinearWavyProgressIndicator(
                    progress = readingProgress,
                    amplitude = { 1f },
                    color = cs.primary,
                    trackColor = mutedColor.copy(alpha = 0.18f),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                )
            } else {
                // Idle: a STATIC bar — no infinite animation runs while you are
                // simply reading, so it draws nothing until you scroll. Pure
                // battery win; the wavy bar only exists while audio plays.
                LinearProgressIndicator(
                    progress = readingProgress,
                    color = cs.primary,
                    trackColor = mutedColor.copy(alpha = 0.18f),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                )
            }

            if (s.bookMode) {
                BookModeText(
                    reader = reader,
                    rows = rows.orEmpty(),
                    listState = listState,
                    settings = s,
                    inkColor = inkColor,
                    dark = dark,
                    sepia = sepia,
                    selectedAyah = selectedAyah,
                    currentAudioAyah = currentAudioAyah,
                    onSelect = { selectedAyah = if (selectedAyah == it) null else it },
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp,
                    ),
                    // Larger gap BETWEEN ayahs than within an ayah's card pair.
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(reader.ayahs, key = { _, a -> a.id }) { _, ayah ->
                        // Drawn per-item rather than once at the top: a juz or
                        // page context can span several surahs, and each opening
                        // needs its own heading.
                        if (ayah.num == 1 && Bismillah.hasHeading(ayah.surah)) {
                            BasmalaHeading(
                                basmala = reader.basmala,
                                settings = s,
                                inkColor = inkColor,
                                dark = dark,
                            )
                        }
                        AyahBlock(
                            ayah = ayah,
                            settings = s,
                            tajweed = reader.tajweed[ayah.id],
                            dark = dark,
                            sepia = sepia,
                            inkColor = inkColor,
                            mutedColor = mutedColor,
                            isSelected = selectedAyah == ayah.id,
                            isSounding = currentAudioAyah == ayah.id,
                            onTap = {
                                selectedAyah = if (selectedAyah == ayah.id) null else ayah.id
                            },
                        )
                    }
                }
            }
        }

        // In book mode the selected ayah's translation has nowhere to go inline,
        // so it appears here rather than not at all. Turning book mode on used
        // to silently switch the translation off with no explanation.
        val selected = reader.ayahs.firstOrNull { it.id == selectedAyah }
        // A narrow screen cannot hold the selection group and the permanent pill
        // on one row, so the selection group moves up a row and everything above
        // it moves with it.
        val selectionLift = if (narrow) 88.dp else 16.dp
        if (s.bookMode && selected != null && s.showTranslation &&
            selected.translation.isNotBlank()
        ) {
            Surface(
                shape = RoundedCornerShape(CARD_OUTER),
                color = if (sepia) SepiaSurface.container else cs.surfaceContainerHigh,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp)
                    .offset(y = -(selectionLift + 72.dp))
                    .fillMaxWidth(),
            ) {
                Column(
                    Modifier
                        .heightIn(max = 200.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(
                        "${selected.surah}:${selected.num}",
                        style = MaterialTheme.typography.labelMedium,
                        color = mutedColor,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        selected.translation,
                        style = MaterialTheme.typography.bodyMedium,
                        color = inkColor,
                    )
                    if (selected.footnotes.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            selected.footnotes,
                            style = MaterialTheme.typography.labelSmall,
                            color = mutedColor,
                        )
                    }
                }
            }
        }

        // LEFT sticky group — appears with a selected ayah: play this verse,
        // play settings, bookmark. Icons only.
        //
        // Book mode used to be excluded, which meant an ayah could be tapped and
        // highlighted there but not played on its own and — the real loss — not
        // bookmarked, so the entire bookmarking feature vanished the moment you
        // preferred a flowing page.
        if (selected != null) {
            val bookmarked = s.bookmarks.any { it.ayahId == selected.id }
            HorizontalFloatingToolbar(
                expanded = true,
                modifier = Modifier
                    // Same baseline as the permanent pill on the right, unless
                    // the screen is too narrow to hold both without collision.
                    .align(Alignment.BottomStart)
                    .offset(x = 16.dp, y = -selectionLift),
                content = {
                    IconButton(
                        onClick = { vm.playSingle(selected) },
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Play this verse")
                    }
                    IconButton(
                        onClick = {
                            playSettingsForSelection = true
                            showPlaySettings = true
                        },
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(WirdIcons.Tune, contentDescription = "Play settings")
                    }
                    IconButton(
                        onClick = { vm.toggleBookmark(selected.id) },
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(
                            if (bookmarked) WirdIcons.Bookmark else WirdIcons.BookmarkOutline,
                            contentDescription = if (bookmarked) "Remove bookmark" else "Bookmark",
                            tint = if (bookmarked) cs.tertiary else androidx.compose.ui.graphics.Color.Unspecified,
                        )
                    }
                    IconButton(
                        onClick = { selectedAyah = null },
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Deselect")
                    }
                },
            )
        }

        // RIGHT sticky group — permanent: play/pause + play settings, as a
        // horizontal pill.
        HorizontalFloatingToolbar(
            expanded = true,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (-16).dp, y = (-16).dp),
            content = {
                IconButton(
                    onClick = {
                        if (isPlaying) {
                            vm.audio.togglePause()
                        } else {
                            // Not firstVisibleItemIndex: in book mode that is a
                            // surah row, so Play always restarted the surah from
                            // its first verse however far down the page you were.
                            val startId = selectedAyah
                                ?: reader.ayahs
                                    .getOrNull(listState.ayahIndexAt(itemSpans, atTop = true))?.id
                            vm.playFrom(startId ?: reader.ayahs.first().id)
                        }
                    },
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    if (isPlaying) {
                        // Two bars — pause, drawn simply.
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            Box(Modifier.size(4.dp, 16.dp).clip(CircleShape).background(cs.primary))
                            Box(Modifier.size(4.dp, 16.dp).clip(CircleShape).background(cs.primary))
                        }
                    } else {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                    }
                }
                IconButton(
                    onClick = {
                        playSettingsForSelection = false
                        showPlaySettings = true
                    },
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    Icon(WirdIcons.Tune, contentDescription = "Play settings")
                }
            },
        )
        }
    }

    if (showPlaySettings) {
        PlaySettingsSheet(
            vm = vm,
            settings = s,
            ayahs = reader.ayahs,
            selectedAyahId = if (playSettingsForSelection) selectedAyah else null,
            showRange = !playSettingsForSelection,
            onDismiss = { showPlaySettings = false },
        )
    }
}

@Composable
private fun PositionLabel(
    listState: LazyListState,
    ayahs: List<Ayah>,
    spans: List<ItemSpan>,
    color: Color,
) {
    // Only recomposes when the top ayah changes — the interpolated index moves
    // continuously while scrolling, but the derived label does not.
    val label by remember(ayahs, spans) {
        derivedStateOf {
            val ayah = ayahs.getOrNull(listState.ayahIndexAt(spans, atTop = true))
            if (ayah == null) "" else "${ayah.surah}:${ayah.num}"
        }
    }
    Text(
        label,
        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
        color = color,
    )
}

private val CARD_OUTER = 22.dp   // the very-rounded outer corners of the pair
private val CARD_INNER = 6.dp    // the gently-rounded corners flanking the gap

/**
 * One ayah as an M3E connected-card group: the Arabic sits in a card whose
 * outer corners are very round and whose bottom corners (at the gap) are
 * gently round; the translation card mirrors it below, with a small gap
 * between. Together they read as one big rounded tile. With the translation
 * hidden, the Arabic card is fully rounded on its own.
 */
@Composable
private fun AyahBlock(
    ayah: Ayah,
    settings: UserSettings,
    tajweed: app.wird.data.TajweedAyah?,
    dark: Boolean,
    sepia: Boolean,
    inkColor: Color,
    mutedColor: Color,
    isSelected: Boolean,
    isSounding: Boolean,
    onTap: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val showTranslation = settings.showTranslation && !settings.bookMode

    // Two-tone by state: Arabic card a touch deeper than the translation card.
    // On the sepia page the state colours come from the sepia palette, not the
    // scheme's containers: those are mosque-green, so tapping an ayah dropped a
    // green card into the middle of a cream page.
    val arabicColor = when {
        isSelected -> if (sepia) SepiaSurface.selection else cs.secondaryContainer
        isSounding -> if (sepia) SepiaSurface.sounding else cs.tertiaryContainer
        sepia -> SepiaSurface.container
        else -> cs.surfaceContainerHigh
    }
    val transColor = when {
        isSelected ->
            if (sepia) SepiaSurface.selection.copy(alpha = 0.6f)
            else cs.secondaryContainer.copy(alpha = 0.6f)
        isSounding ->
            if (sepia) SepiaSurface.sounding.copy(alpha = 0.6f)
            else cs.tertiaryContainer.copy(alpha = 0.6f)
        sepia -> SepiaSurface.container.copy(alpha = 0.55f)
        else -> cs.surfaceContainerLow
    }

    val arabicShape = if (showTranslation) {
        RoundedCornerShape(topStart = CARD_OUTER, topEnd = CARD_OUTER, bottomStart = CARD_INNER, bottomEnd = CARD_INNER)
    } else {
        RoundedCornerShape(CARD_OUTER)
    }
    val transShape = RoundedCornerShape(topStart = CARD_INNER, topEnd = CARD_INNER, bottomStart = CARD_OUTER, bottomEnd = CARD_OUTER)

    val arabicSize = 24.sp * settings.arabicScale
    val arabic = remember(ayah.id, settings.showTajweed, tajweed, dark) {
        buildAnnotatedString {
            if (settings.showTajweed && tajweed != null) {
                append(tajweed.text)
                tajweed.spans.forEach { span ->
                    TajweedPalette.color(span.rule, dark)?.let { color ->
                        addStyle(SpanStyle(color = color), span.start, span.end)
                    }
                }
            } else {
                append(ayah.text)
            }
            append(" ")
            appendInlineContent("marker", "۝${easternDigits(ayah.num)}")
        }
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Surface(onClick = onTap, shape = arabicShape, color = arabicColor, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                // Force RTL so every ayah is right-aligned and flows right-to-left,
                // even a short single-line one that wouldn't fill the width.
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Text(
                        text = arabic,
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = NotoArabic,
                            fontSize = arabicSize,
                            lineHeight = arabicSize * 2.05f,
                            textAlign = TextAlign.Right,
                            textDirection = TextDirection.Rtl,
                            color = inkColor,
                        ),
                        inlineContent = mapOf(
                            "marker" to androidx.compose.foundation.text.InlineTextContent(
                                androidx.compose.ui.text.Placeholder(
                                    width = arabicSize * 1.35f,
                                    height = arabicSize * 1.35f,
                                    placeholderVerticalAlign =
                                        androidx.compose.ui.text.PlaceholderVerticalAlign.TextCenter,
                                ),
                            ) {
                                VerseMarker(ayah.num, inkColor)
                            },
                        ),
                    )
                }
                if (ayah.sajdah) {
                    Text(
                        "۩ sajdah",
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.tertiary,
                    )
                }
            }
        }
        if (showTranslation) {
            Surface(onClick = onTap, shape = transShape, color = transColor, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        ayah.translation,
                        style = MaterialTheme.typography.bodyMedium,
                        color = mutedColor,
                    )
                    // The translation is republished unmodified, which means the
                    // publisher's "[2]" markers stay in the text. Showing the
                    // notes they point at is what stops them being dangling
                    // references — tap the verse to reveal them.
                    if (isSelected && ayah.footnotes.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            ayah.footnotes,
                            style = MaterialTheme.typography.labelSmall,
                            color = mutedColor.copy(alpha = 0.75f),
                        )
                    }
                }
            }
        }
    }
}

/** M3E verse marker: a circle with the number in Eastern Arabic numerals. */
@Composable
private fun VerseMarker(num: Int, ink: Color) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxSize()
            .clip(CircleShape)
            .background(cs.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            easternDigits(num),
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = NotoArabic),
            color = cs.onSecondaryContainer,
            fontSize = 11.sp,
            maxLines = 1,
        )
    }
}

/**
 * The Basmala, set apart above the first ayah of a surah.
 *
 * It is deliberately not a numbered verse here: [QuranDb] strips it from ayah 1
 * so the verse count and the verse markers stay correct. Al-Fatiha and
 * At-Tawbah never reach this composable — see [Bismillah.hasHeading].
 */
@Composable
private fun BasmalaHeading(
    basmala: app.wird.data.TajweedAyah?,
    settings: UserSettings,
    inkColor: Color,
    dark: Boolean,
) {
    val source = basmala ?: return
    val arabicSize = 21.sp * settings.arabicScale
    val annotated = remember(source, settings.showTajweed, dark) {
        buildAnnotatedString {
            append(source.text)
            if (settings.showTajweed) {
                source.spans.forEach { span ->
                    TajweedPalette.color(span.rule, dark)?.let { color ->
                        addStyle(SpanStyle(color = color), span.start, span.end)
                    }
                }
            }
        }
    }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Text(
            text = annotated,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp, bottom = 6.dp),
            style = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = NotoArabic,
                fontSize = arabicSize,
                lineHeight = arabicSize * 1.9f,
                textAlign = TextAlign.Center,
                textDirection = TextDirection.Rtl,
                color = inkColor,
            ),
        )
    }
}

@Composable
private fun BookModeText(
    reader: app.wird.ui.ReaderUiState,
    rows: List<BookRow>,
    listState: LazyListState,
    settings: UserSettings,
    inkColor: Color,
    dark: Boolean,
    sepia: Boolean,
    selectedAyah: Int?,
    currentAudioAyah: Int?,
    onSelect: (Int) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val arabicSize = 24.sp * settings.arabicScale
    // Same reason as the card reader: green highlights on cream paper.
    val selectionWash = if (sepia) SepiaSurface.selection else cs.secondaryContainer.copy(alpha = 0.8f)
    val soundingWash = if (sepia) SepiaSurface.sounding else cs.tertiaryContainer.copy(alpha = 0.55f)

    // The rows are computed by the caller: the reader has to know the same row
    // layout in order to say which ayah is on screen, and two independent
    // groupings that had to agree would eventually stop agreeing.
    //
    // selectedAyah and currentAudioAyah are deliberately NOT keys here. Keying
    // on the sounding ayah rebuilt and re-laid-out every row's text on the main
    // thread each time the recitation advanced a verse — roughly every five
    // seconds. The highlight is applied as a cheap overlay pass below instead.
    val baseTexts = remember(rows, settings.showTajweed, reader.tajweed, dark) {
        rows.map { row ->
            if (row.isBasmala) return@map null
            val ayahs = row.ayahs
            // Where each ayah sits in this row's string, so the highlight can be
            // reapplied later without rebuilding the string itself.
            val ranges = HashMap<Int, IntRange>(ayahs.size)
            val built = buildAnnotatedString {
                ayahs.forEachIndexed { i, ayah ->
                    val start = length
                    val body = if (settings.showTajweed) {
                        reader.tajweed[ayah.id]?.text ?: ayah.text
                    } else {
                        ayah.text
                    }
                    withLink(
                        LinkAnnotation.Clickable(
                            tag = "ayah:${ayah.id}",
                            styles = TextLinkStyles(),
                        ) { onSelect(ayah.id) },
                    ) {
                        append(body)
                    }
                    if (settings.showTajweed) {
                        reader.tajweed[ayah.id]?.spans?.forEach { span ->
                            TajweedPalette.color(span.rule, dark)?.let { color ->
                                addStyle(SpanStyle(color = color), start + span.start, start + span.end)
                            }
                        }
                    }
                    ranges[ayah.id] = start until length
                    append(" ")
                    appendInlineContent("m${ayah.id}", "۝${easternDigits(ayah.num)}")
                    if (i != ayahs.lastIndex) append(" ")
                }
            }
            built to ranges
        }
    }

    // Only this recomputes as playback advances, and it is two addStyle calls
    // over an already-built string rather than a full re-layout of the row.
    val texts = remember(baseTexts, selectedAyah, currentAudioAyah, selectionWash, soundingWash) {
        baseTexts.map { entry ->
            val (built, ranges) = entry ?: return@map null
            val selected = selectedAyah?.let { ranges[it] }
            val sounding = currentAudioAyah?.let { ranges[it] }
            if (selected == null && sounding == null) {
                built
            } else {
                buildAnnotatedString {
                    append(built)
                    selected?.let {
                        addStyle(SpanStyle(background = selectionWash), it.first, it.last + 1)
                    }
                    // Selection wins when both land on the same ayah.
                    if (sounding != null && sounding != selected) {
                        addStyle(
                            SpanStyle(background = soundingWash),
                            sounding.first, sounding.last + 1,
                        )
                    }
                }
            }
        }
    }
    val inline = remember(reader.ayahs, arabicSize) {
        reader.ayahs.associate { ayah ->
            "m${ayah.id}" to androidx.compose.foundation.text.InlineTextContent(
                androidx.compose.ui.text.Placeholder(
                    width = arabicSize * 1.3f,
                    height = arabicSize * 1.3f,
                    placeholderVerticalAlign =
                        androidx.compose.ui.text.PlaceholderVerticalAlign.TextCenter,
                ),
            ) {
                VerseMarker(ayah.num, inkColor)
            }
        }
    }
    // The reader's LazyListState was never passed in here, so in book mode it
    // reported its constructor index forever and visibleItemsInfo was empty.
    // That silently froze the header verse reference, the progress bar,
    // follow-along scrolling, the Play button's starting ayah, and — worst —
    // the autosave, which rewrote the same entry position every 600 ms so an
    // hour of reading was never recorded.
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp, end = 20.dp, top = 8.dp, bottom = 120.dp,
        ),
    ) {
        rows.forEachIndexed { rowIndex, row ->
            val first = row.ayahs.first()
            if (row.isBasmala) {
                item(key = "basmala-${first.surah}") {
                    BasmalaHeading(
                        basmala = reader.basmala,
                        settings = settings,
                        inkColor = inkColor,
                        dark = dark,
                    )
                }
                return@forEachIndexed
            }
            val text = texts[rowIndex] ?: return@forEachIndexed
            item(key = "row-${first.id}") {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Text(
                        text = text,
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = NotoArabic,
                            fontSize = arabicSize,
                            lineHeight = arabicSize * 2.05f,
                            textAlign = TextAlign.Right,
                            textDirection = TextDirection.Rtl,
                            color = inkColor,
                        ),
                        inlineContent = inline,
                    )
                }
            }
        }
    }
}
