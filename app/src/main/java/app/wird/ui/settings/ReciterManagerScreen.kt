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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
    var confirmRemoveAll by remember { mutableStateOf(false) }
    var confirmDownloadAll by remember { mutableStateOf(false) }
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
                    // Queues the whole Quran in one voice: hundreds of megabytes
                    // over whatever connection happens to be up. Say so first.
                    onClick = { confirmDownloadAll = true },
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    Icon(WirdIcons.Download, contentDescription = "Download all")
                }
                IconButton(
                    // Confirm first. This deletes every downloaded surah for the
                    // reciter, which can be several gigabytes, and there is no undo.
                    onClick = { confirmRemoveAll = true },
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove all downloads")
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
                val failed by vm.audioStore
                    .downloadFailed(reciter, surah.id)
                    .collectAsStateWithLifecycle(initialValue = false)
                // null = not looked yet. It used to start at `false`, which is a
                // claim, not an absence: every already-downloaded surah rendered
                // a Download button for a frame or two before the check came
                // back, so opening this screen flashed 114 wrong buttons and
                // invited a tap that re-queued a download you already had.
                val downloaded by produceState<Boolean?>(
                    initialValue = null,
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
                                    // A running download had no way out short of
                                    // force-stopping the app: a queued surah just
                                    // spun until it finished.
                                    IconButton(
                                        onClick = { vm.audioStore.cancelSurahDownload(reciter, surah.id) },
                                        shapes = IconButtonDefaults.shapes(),
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Cancel download")
                                    }
                                }
                                // Nothing at all until the check returns, rather
                                // than a button that is probably wrong.
                                downloaded == null -> Spacer(Modifier.size(48.dp))
                                failed && downloaded == false -> {
                                    // downloadFailed() was already published and
                                    // nothing consumed it, so a download that gave
                                    // up looked exactly like one never started.
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = "Download failed",
                                        tint = cs.error,
                                        modifier = Modifier.padding(8.dp),
                                    )
                                    IconButton(
                                        onClick = {
                                            vm.audioStore.enqueueSurahDownload(
                                                reciter, surah.id, surah.ayahCount,
                                            )
                                        },
                                        shapes = IconButtonDefaults.shapes(),
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = "Retry download")
                                    }
                                }
                                downloaded == true -> {
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

    if (confirmRemoveAll) {
        // 114 stat() calls; never on the main thread. -1 until it resolves.
        val downloadedCount by produceState(-1, reciter, refresh) {
            value = withContext(Dispatchers.IO) {
                vm.surahs.count { vm.audioStore.isSurahDownloaded(reciter, it.id, it.ayahCount) }
            }
        }
        AlertDialog(
            onDismissRequest = { confirmRemoveAll = false },
            title = { Text("Remove all downloads?") },
            text = {
                Text(
                    "This deletes every surah you have downloaded for " +
                        "${reciter.name}" +
                        (if (downloadedCount > 0) " ($downloadedCount of 114)" else "") +
                        ". You can download them again later.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemoveAll = false
                    scope.launch {
                        vm.surahs.forEach { surah ->
                            vm.audioStore.deleteSurah(reciter, surah.id, surah.ayahCount)
                        }
                        refresh++
                    }
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemoveAll = false }) { Text("Cancel") }
            },
        )
    }

    if (confirmDownloadAll) {
        AlertDialog(
            onDismissRequest = { confirmDownloadAll = false },
            title = { Text("Download the whole Quran?") },
            text = {
                Text(
                    "This queues every surah ${reciter.name} has not already " +
                        "recorded to your device. A full reciter is several " +
                        "hundred megabytes and can exceed a gigabyte. It will " +
                        "use mobile data if you are not on Wi-Fi.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDownloadAll = false
                    scope.launch {
                        // The exists() sweep is IO; enqueueing is not, but doing
                        // both here keeps the 114 stat calls off the main thread.
                        val missing = withContext(Dispatchers.IO) {
                            vm.surahs.filterNot {
                                vm.audioStore.isSurahDownloaded(reciter, it.id, it.ayahCount)
                            }
                        }
                        missing.forEach {
                            vm.audioStore.enqueueSurahDownload(reciter, it.id, it.ayahCount)
                        }
                    }
                }) { Text("Download") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDownloadAll = false }) { Text("Cancel") }
            },
        )
    }
}
