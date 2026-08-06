package app.wird.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import app.wird.ui.bookmarks.BookmarksScreen
import app.wird.ui.components.WirdIcons
import app.wird.ui.library.LibraryScreen
import app.wird.ui.reader.ReaderScreen
import app.wird.ui.settings.ReciterManagerScreen
import app.wird.ui.settings.SettingsScreen
import kotlinx.serialization.Serializable

@Serializable data object LibraryKey : NavKey
@Serializable data object ReaderKey : NavKey
@Serializable data object BookmarksKey : NavKey
@Serializable data object SettingsKey : NavKey
@Serializable data class ReciterManagerKey(val reciterId: String) : NavKey
@Serializable data object NoticesKey : NavKey

@Composable
fun AppRoot(vm: WirdViewModel) {
    val backStack = rememberNavBackStack(LibraryKey)
    val currentKey = backStack.lastOrNull() ?: LibraryKey
    val isTab = currentKey !is ReciterManagerKey

    fun openTab(key: NavKey) {
        if (currentKey == key) return
        while (backStack.size > 1) backStack.removeLastOrNull()
        if (key != LibraryKey) backStack.add(key)
    }

    /** List rows and bookmarks land here: load context, focus, switch tab. */
    val openReader: () -> Unit = { openTab(ReaderKey) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (isTab) {
                // Icon-first by design: shapes and glyphs carry the meaning.
                ShortNavigationBar {
                    ShortNavigationBarItem(
                        selected = currentKey == LibraryKey,
                        onClick = { openTab(LibraryKey) },
                        icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Library") },
                        label = null,
                    )
                    ShortNavigationBarItem(
                        selected = currentKey == ReaderKey,
                        onClick = { openTab(ReaderKey) },
                        icon = { Icon(WirdIcons.OpenBook, contentDescription = "Reader") },
                        label = null,
                    )
                    ShortNavigationBarItem(
                        selected = currentKey == BookmarksKey,
                        onClick = { openTab(BookmarksKey) },
                        icon = { Icon(WirdIcons.Bookmark, contentDescription = "Bookmarks") },
                        label = null,
                    )
                    ShortNavigationBarItem(
                        selected = currentKey == SettingsKey,
                        onClick = { openTab(SettingsKey) },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = null,
                    )
                }
            }
        },
    ) { padding ->
        NavDisplay(
            backStack = backStack,
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
            onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
            transitionSpec = {
                fadeIn(tween(200, delayMillis = 40)) togetherWith fadeOut(tween(110))
            },
            popTransitionSpec = {
                fadeIn(tween(200, delayMillis = 40)) togetherWith fadeOut(tween(110))
            },
            predictivePopTransitionSpec = { _ ->
                fadeIn(tween(200)) togetherWith fadeOut(tween(110))
            },
            entryProvider = entryProvider {
                entry<LibraryKey> {
                    LibraryScreen(vm, openReader = openReader)
                }
                entry<ReaderKey>(
                    metadata = NavDisplay.transitionSpec {
                        (slideInVertically(tween(240)) { it / 10 } + fadeIn(tween(240))) togetherWith
                            fadeOut(tween(120))
                    },
                ) {
                    ReaderScreen(vm)
                }
                entry<BookmarksKey> {
                    BookmarksScreen(vm, openReader = openReader)
                }
                entry<SettingsKey> {
                    SettingsScreen(
                        vm = vm,
                        openReciterManager = { backStack.add(ReciterManagerKey(it)) },
                        onOpenNotices = { backStack.add(NoticesKey) },
                    )
                }
                entry<ReciterManagerKey> { key ->
                    ReciterManagerScreen(
                        vm,
                        reciterId = key.reciterId,
                        onBack = { backStack.removeLastOrNull() },
                    )
                }
                entry<NoticesKey> { app.wird.ui.settings.NoticesScreen() }
            },
        )
    }
}
