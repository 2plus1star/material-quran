package app.wird.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wird.data.Reciters
import app.wird.ui.WirdViewModel
import app.wird.ui.components.WirdIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Per-surah audio management for one reciter: download / delete each surah,
 * plus download-all and remove-all. Icons carry the meaning; gold = resident.
 */
@Composable
fun ReciterManagerScreen(vm: WirdViewModel, reciterId: String, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val reciter = Reciters.byId(reciterId)
    val scope = rememberCoroutineScope()
    // Bumped to re-evaluate downloaded state after deletes/downloads.
    var refresh by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(reciter.name) },
            navigationIcon = {
                IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                IconButton(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                vm.surahs.forEach { surah ->
                                    if (!vm.audioStore.isSurahDownloaded(reciter, surah.id, surah.ayahCount)) {
                                        vm.audioStore.enqueueSurahDownload(reciter, surah.id, surah.ayahCount)
                                    }
                                }
                            }
                        }
                    },
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    Icon(WirdIcons.Download, contentDescription = "Download all")
                }
                IconButton(
                    onClick = {
                        scope.launch {
                            vm.surahs.forEach { surah ->
                                vm.audioStore.deleteSurah(reciter, surah.id, surah.ayahCount)
                            }
                            refresh++
                        }
                    },
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove all")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = cs.surface,
                scrolledContainerColor = cs.surface,
            ),
        )

        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            itemsIndexed(vm.surahs, key = { _, s -> s.id }) { index, surah ->
                val downloading by vm.audioStore
                    .downloadRunning(reciter, surah.id)
                    .collectAsStateWithLifecycle(initialValue = false)
                val percent by vm.audioStore
                    .downloadProgress(reciter, surah.id)
                    .collectAsStateWithLifecycle(initialValue = -1)
                val downloaded by produceState(
                    initialValue = false,
                    surah.id, downloading, refresh,
                ) {
                    value = withContext(Dispatchers.IO) {
                        vm.audioStore.isSurahDownloaded(reciter, surah.id, surah.ayahCount)
                    }
                }

                SegmentedListItem(
                    shapes = ListItemDefaults.segmentedShapes(index = index, count = vm.surahs.size),
                    colors = ListItemDefaults.segmentedColors(
                        containerColor = cs.surfaceContainerLow,
                    ),
                    supportingContent = {
                        Text(
                            "${surah.ayahCount} ayat",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    trailingContent = {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            when {
                                downloading -> {
                                    if (percent >= 0) {
                                        Text(
                                            "$percent%",
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontFeatureSettings = "tnum",
                                            ),
                                            color = cs.primary,
                                        )
                                        Spacer(Modifier.width(8.dp))
                                    }
                                    LoadingIndicator(Modifier.size(36.dp))
                                }
                                downloaded -> {
                                    // Gold check = resident on device.
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "Downloaded",
                                        tint = cs.tertiary,
                                        modifier = Modifier.padding(8.dp),
                                    )
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                vm.audioStore.deleteSurah(reciter, surah.id, surah.ayahCount)
                                                refresh++
                                            }
                                        },
                                        shapes = IconButtonDefaults.shapes(),
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                                    }
                                }
                                else -> IconButton(
                                    onClick = {
                                        vm.audioStore.enqueueSurahDownload(
                                            reciter, surah.id, surah.ayahCount,
                                        )
                                    },
                                    shapes = IconButtonDefaults.shapes(),
                                ) {
                                    Icon(WirdIcons.Download, contentDescription = "Download")
                                }
                            }
                        }
                    },
                ) {
                    Row {
                        Text(
                            "${surah.id}.",
                            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                            color = cs.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(surah.tname, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}
