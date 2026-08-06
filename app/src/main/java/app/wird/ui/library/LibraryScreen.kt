package app.wird.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wird.data.LastRead
import app.wird.data.ReaderContext
import app.wird.data.ReaderContextType
import app.wird.ui.WirdViewModel
import app.wird.ui.components.NumberBadge
import app.wird.ui.theme.BalooBhaijaan2
import app.wird.ui.theme.NotoArabic

@Composable
fun LibraryScreen(vm: WirdViewModel, openReader: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val s = settings ?: return
    val cs = MaterialTheme.colorScheme
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabTypes = listOf(
        ReaderContextType.SURAH,
        ReaderContextType.JUZ,
        ReaderContextType.HIZB,
        ReaderContextType.PAGE,
    )

    Column(Modifier.fillMaxSize()) {
        CenterAlignedTopAppBar(
            title = {
                // "Al-Qur'an" masthead — Baloo Bhaijaan 2, centered.
                Text(
                    "القرآن",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = BalooBhaijaan2,
                        fontWeight = FontWeight.Bold,
                        textDirection = TextDirection.Rtl,
                    ),
                    color = cs.onSurface,
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = cs.surface,
                scrolledContainerColor = cs.surface,
            ),
        )

        LastReadCard(vm, s.lastRead, openReader)

        // Surah / Juz / Hizb / Page — the four ways into the text, each keyed
        // by its own shape so the badge itself names the division.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            listOf("Surah", "Juz", "Hizb", "Page").forEachIndexed { index, label ->
                ToggleButton(
                    checked = tab == index,
                    onCheckedChange = { if (it) tab = index },
                    modifier = Modifier.weight(1f),
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        3 -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                ) {
                    Text(label, maxLines = 1, style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        if (vm.surahs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LoadingIndicator()
            }
            return@Column
        }

        when (tabTypes[tab]) {
            ReaderContextType.SURAH -> SurahList(vm, openReader)
            else -> DivisionList(vm, tabTypes[tab], openReader)
        }
    }
}

/**
 * "Last read" — the top widget. Label AND fraction come from lastRead's OWN
 * persisted context (not the live reader), so they always agree. Tapping it
 * re-opens that exact context at its saved line.
 */
@Composable
private fun LastReadCard(vm: WirdViewModel, lastRead: LastRead, openReader: () -> Unit) {
    val cs = MaterialTheme.colorScheme

    // null = still resolving; gates the empty flash on cold start.
    val resolved by produceState<Pair<String, Float>?>(initialValue = null, lastRead) {
        val surah = vm.surahOf(lastRead.ayahId)
        val ayah = vm.ayahById(lastRead.ayahId)
        val label = if (surah != null && ayah != null) {
            "${surah.tname} · ${ayah.surah}:${ayah.num}"
        } else {
            ""
        }
        val ayahs = vm.ayahsFor(lastRead.context)
        val fraction = if (ayahs.isEmpty()) {
            0f
        } else {
            // progressIndex = furthest-read row, so a one-screen surah reads full.
            ((lastRead.progressIndex.coerceIn(0, ayahs.lastIndex) + 1f) / ayahs.size)
                .coerceIn(0f, 1f)
        }
        value = label to fraction
    }

    Surface(
        onClick = {
            vm.resumeLastRead()
            openReader()
        },
        shape = MaterialTheme.shapes.extraLarge,
        color = cs.primaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                "LAST READ",
                style = MaterialTheme.typography.labelMediumEmphasized,
                color = cs.onPrimaryContainer.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                resolved?.first?.ifEmpty { "Al-Faatiha · 1:1" } ?: " ",
                style = MaterialTheme.typography.titleLargeEmphasized,
                color = cs.onPrimaryContainer,
            )
            Spacer(Modifier.height(12.dp))
            // Static bar — the Library never runs an idle animation.
            LinearProgressIndicator(
                progress = { resolved?.second ?: 0f },
                color = cs.primary,
                trackColor = cs.onPrimaryContainer.copy(alpha = 0.2f),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SurahList(vm: WirdViewModel, openReader: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val surahs = vm.surahs
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        itemsIndexed(surahs, key = { _, s -> s.id }) { index, surah ->
            SegmentedListItem(
                onClick = {
                    vm.openContext(
                        ReaderContext(ReaderContextType.SURAH, surah.id),
                        scrollToAyahId = surah.startAyahId,
                    )
                    openReader()
                },
                shapes = ListItemDefaults.segmentedShapes(index = index, count = surahs.size),
                colors = ListItemDefaults.segmentedColors(
                    containerColor = cs.surfaceContainerLow,
                ),
                leadingContent = {
                    // Badge shape carries the revelation: Square = Meccan, Arch = Medinan.
                    NumberBadge(
                        number = surah.id,
                        type = ReaderContextType.SURAH,
                        revelation = surah.revelation,
                    )
                },
                supportingContent = {
                    Text(
                        "${surah.ayahCount} ayat",
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                trailingContent = {
                    // The Arabic surah name lives in a pill.
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .background(cs.secondaryContainer)
                            .padding(horizontal = 14.dp, vertical = 5.dp),
                    ) {
                        Text(
                            surah.nameAr,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = NotoArabic,
                                textDirection = TextDirection.Rtl,
                            ),
                            color = cs.onSecondaryContainer,
                        )
                    }
                },
            ) {
                Text(surah.tname, style = MaterialTheme.typography.titleMedium)
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun DivisionList(vm: WirdViewModel, type: ReaderContextType, openReader: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val items = when (type) {
        ReaderContextType.JUZ -> vm.juzList
        ReaderContextType.HIZB -> vm.hizbList
        else -> vm.pageList
    }
    val title = when (type) {
        ReaderContextType.JUZ -> "Juz"
        ReaderContextType.HIZB -> "Hizb"
        else -> "Page"
    }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        itemsIndexed(items, key = { _, d -> d.number }) { index, division ->
            SegmentedListItem(
                onClick = {
                    vm.openContext(
                        ReaderContext(type, division.number),
                        scrollToAyahId = division.startAyahId,
                    )
                    openReader()
                },
                shapes = ListItemDefaults.segmentedShapes(index = index, count = items.size),
                colors = ListItemDefaults.segmentedColors(
                    containerColor = cs.surfaceContainerLow,
                ),
                leadingContent = {
                    NumberBadge(number = division.number, type = type)
                },
                supportingContent = {
                    Text(
                        "begins at ${division.startSurahTname}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                trailingContent = {
                    Text(
                        division.startSurahAr,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontFamily = NotoArabic,
                            textDirection = TextDirection.Rtl,
                        ),
                        color = cs.onSurfaceVariant,
                    )
                },
            ) {
                Text("$title ${division.number}", style = MaterialTheme.typography.titleMedium)
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}
