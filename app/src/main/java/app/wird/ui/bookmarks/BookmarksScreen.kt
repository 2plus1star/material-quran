package app.wird.ui.bookmarks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wird.data.Ayah
import app.wird.data.ReaderContext
import app.wird.data.ReaderContextType
import app.wird.ui.WirdViewModel
import app.wird.ui.components.NumberBadge
import app.wird.ui.components.WirdIcons
import app.wird.ui.theme.NotoArabic

@Composable
fun BookmarksScreen(vm: WirdViewModel, openReader: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val s = settings ?: return
    val cs = MaterialTheme.colorScheme
    val bookmarks = s.bookmarks.sortedByDescending { it.createdAt }

    // null = still resolving (gates the empty-state flash on entry).
    val resolvedOrNull by produceState<List<Ayah>?>(initialValue = null, bookmarks) {
        value = bookmarks.mapNotNull { vm.ayahById(it.ayahId) }
    }
    val resolved = resolvedOrNull ?: emptyList()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Bookmarks") },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = cs.surface,
                scrolledContainerColor = cs.surface,
            ),
        )

        // Show the empty state only once resolution has actually completed.
        if (resolvedOrNull != null && resolved.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        WirdIcons.BookmarkOutline,
                        contentDescription = null,
                        tint = cs.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Select an ayah in the reader to bookmark it",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant,
                    )
                }
            }
            return@Column
        }
        if (resolvedOrNull == null) return@Column

        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            itemsIndexed(resolved, key = { _, a -> a.id }) { index, ayah ->
                SegmentedListItem(
                    onClick = {
                        // Open the ayah's surah in the reader, focused + selected.
                        vm.openContext(
                            ReaderContext(ReaderContextType.SURAH, ayah.surah),
                            scrollToAyahId = ayah.id,
                            selectAyahId = ayah.id,
                        )
                        openReader()
                    },
                    shapes = ListItemDefaults.segmentedShapes(index = index, count = resolved.size),
                    colors = ListItemDefaults.segmentedColors(
                        containerColor = cs.surfaceContainerLow,
                    ),
                    leadingContent = {
                        NumberBadge(number = ayah.num, type = ReaderContextType.SURAH)
                    },
                    supportingContent = {
                        Text(
                            ayah.translation,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    trailingContent = {
                        IconButton(
                            onClick = { vm.toggleBookmark(ayah.id) },
                            shapes = IconButtonDefaults.shapes(),
                        ) {
                            Icon(
                                WirdIcons.Bookmark,
                                contentDescription = "Remove bookmark",
                                tint = cs.tertiary,
                            )
                        }
                    },
                ) {
                    Text(
                        ayah.text,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = NotoArabic,
                            textDirection = TextDirection.Rtl,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}
