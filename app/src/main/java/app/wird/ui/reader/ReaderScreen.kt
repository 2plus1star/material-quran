package app.wird.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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

    // Follow the recitation: keep the sounding ayah on screen.
    LaunchedEffect(currentAudioAyah) {
        val id = currentAudioAyah ?: return@LaunchedEffect
        val index = reader.ayahs.indexOfFirst { it.id == id }
        if (index >= 0) {
            val visible = listState.layoutInfo.visibleItemsInfo.any { it.index == index }
            if (!visible) listState.animateScrollToItem(index, scrollOffset = -120)
        }
    }

    // Continuous last-read persistence (debounced scroll settle). Capture the
    // context + ayahs the effect was launched with so a save is never
    // mis-attributed to a context the user switched to mid-debounce.
    LaunchedEffect(listState, reader.context) {
        val ctx = reader.context
        val ayahs = reader.ayahs
        snapshotFlow {
            val info = listState.layoutInfo
            // first-visible = restore point; last-visible = how far you've read.
            Triple(
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset,
                info.visibleItemsInfo.lastOrNull()?.index ?: listState.firstVisibleItemIndex,
            )
        }
            .debounce(600)
            .distinctUntilChanged()
            .collect { (index, offset, lastVisible) ->
                // ayahId shows the furthest-read verse; if the whole surah fits
                // on screen, lastVisible is its final ayah → progress reads full.
                val furthest = ayahs.getOrNull(lastVisible) ?: ayahs.getOrNull(index) ?: return@collect
                vm.saveReadingPosition(ctx, furthest.id, index, offset, lastVisible)
            }
    }

    Box(Modifier.fillMaxSize().background(pageColor)) {
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
                PositionLabel(listState, reader.ayahs, mutedColor)
            }

            // Progress through the CURRENT surah/juz/hizb/page. Driven by the
            // LAST visible ayah (not the first), so a short surah that fits on
            // one screen reads as complete. Evaluated in the draw phase, so
            // scrolling never recomposes the reader.
            val readingProgress = {
                val info = listState.layoutInfo
                val total = reader.ayahs.size.coerceAtLeast(1)
                val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                ((last + 1f) / total).coerceIn(0f, 1f)
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
                    listState = listState,
                    settings = s,
                    inkColor = inkColor,
                    dark = dark,
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

        // LEFT sticky group — appears with a selected ayah: play this verse,
        // play settings, bookmark. Icons only.
        val selected = reader.ayahs.firstOrNull { it.id == selectedAyah }
        if (selected != null && !s.bookMode) {
            val bookmarked = s.bookmarks.any { it.ayahId == selected.id }
            HorizontalFloatingToolbar(
                expanded = true,
                modifier = Modifier
                    // Same baseline as the permanent pill on the right.
                    .align(Alignment.BottomStart)
                    .offset(x = 16.dp, y = (-16).dp),
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
                            val startId = selectedAyah
                                ?: reader.ayahs.getOrNull(listState.firstVisibleItemIndex)?.id
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
private fun PositionLabel(listState: LazyListState, ayahs: List<Ayah>, color: Color) {
    // Only recomposes when the top ayah changes.
    val label by remember(ayahs) {
        derivedStateOf {
            val ayah = ayahs.getOrNull(listState.firstVisibleItemIndex)
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
    val arabicColor = when {
        isSelected -> cs.secondaryContainer
        isSounding -> cs.tertiaryContainer
        sepia -> SepiaSurface.container
        else -> cs.surfaceContainerHigh
    }
    val transColor = when {
        isSelected -> cs.secondaryContainer.copy(alpha = 0.6f)
        isSounding -> cs.tertiaryContainer.copy(alpha = 0.6f)
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
 * Book mode: the ayahs flow continuously like a real page — one annotated
 * string, verse markers inline, tap any ayah's text to select it.
 */
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
    listState: LazyListState,
    settings: UserSettings,
    inkColor: Color,
    dark: Boolean,
    selectedAyah: Int?,
    currentAudioAyah: Int?,
    onSelect: (Int) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val arabicSize = 24.sp * settings.arabicScale

    // Consecutive ayahs grouped by surah. The flowing text used to be a single
    // AnnotatedString, which left nowhere to put a Basmala heading — and a juz
    // or page context routinely spans a surah boundary.
    val runs = remember(reader.ayahs) {
        buildList {
            var current = mutableListOf<Ayah>()
            reader.ayahs.forEach { ayah ->
                if (current.isNotEmpty() && current.last().surah != ayah.surah) {
                    add(current)
                    current = mutableListOf()
                }
                current.add(ayah)
            }
            if (current.isNotEmpty()) add(current)
        }
    }

    // selectedAyah and currentAudioAyah are deliberately NOT keys here.
    // Al-Baqarah is 55,317 characters in one AnnotatedString with 286 inline
    // placeholders and ~4,900 SpanStyles; keying on the sounding ayah rebuilt
    // and re-laid-out all of it on the main thread every time the recitation
    // advanced a verse — roughly every five seconds. The highlight is applied
    // as a cheap overlay pass below instead.
    val baseTexts = remember(runs, settings.showTajweed, reader.tajweed, dark) {
        runs.map { run ->
            // Where each ayah sits in this run's string, so the highlight can be
            // reapplied later without rebuilding the string itself.
            val ranges = HashMap<Int, IntRange>(run.size)
            val built = buildAnnotatedString {
                run.forEachIndexed { i, ayah ->
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
                    if (i != run.lastIndex) append(" ")
                }
            }
            built to ranges
        }
    }

    // Only this recomputes as playback advances, and it is two addStyle calls
    // over an already-built string rather than a full re-layout of 55k chars.
    val texts = remember(baseTexts, selectedAyah, currentAudioAyah, cs) {
        baseTexts.map { (built, ranges) ->
            val selected = selectedAyah?.let { ranges[it] }
            val sounding = currentAudioAyah?.let { ranges[it] }
            if (selected == null && sounding == null) {
                built
            } else {
                buildAnnotatedString {
                    append(built)
                    selected?.let {
                        addStyle(
                            SpanStyle(background = cs.secondaryContainer.copy(alpha = 0.8f)),
                            it.first, it.last + 1,
                        )
                    }
                    // Selection wins when both land on the same ayah.
                    if (sounding != null && sounding != selected) {
                        addStyle(
                            SpanStyle(background = cs.tertiaryContainer.copy(alpha = 0.55f)),
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
        runs.forEachIndexed { runIndex, run ->
        val first = run.first()
        if (first.num == 1 && Bismillah.hasHeading(first.surah)) {
            item(key = "basmala-${first.surah}") {
                BasmalaHeading(
                    basmala = reader.basmala,
                    settings = settings,
                    inkColor = inkColor,
                    dark = dark,
                )
            }
        }
        item(key = "run-${first.id}") {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Text(
                    text = texts[runIndex],
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
