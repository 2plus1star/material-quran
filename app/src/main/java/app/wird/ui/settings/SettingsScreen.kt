package app.wird.ui.settings

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wird.data.ColorSource
import app.wird.data.DarkMode
import app.wird.data.Reciters
import app.wird.ui.WirdViewModel
import app.wird.ui.reader.TajweedPalette
import app.wird.ui.theme.NotoArabic

/** Must stay in sync with the published page; a dead link is a Play rejection. */
const val PRIVACY_POLICY_URL = "https://2plus1star.github.io/material-quran/privacy/"

@Composable
fun SettingsScreen(
    vm: WirdViewModel,
    openReciterManager: (String) -> Unit,
    onOpenNotices: () -> Unit = {},
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val s = settings ?: return
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Settings") },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = cs.surface,
                scrolledContainerColor = cs.surface,
            ),
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionLabel("Reading")

            SwitchRow(
                title = "English translation",
                // Book mode has no room for a translation between verses, so it
                // suppresses this. Saying so stops the switch looking broken:
                // it stays on, and nothing changes on the page.
                subtitle = if (s.bookMode) {
                    "In book mode, tap an ayah to read its translation"
                } else {
                    "Noor International (Saheeh), after each ayah"
                },
                checked = s.showTranslation,
                onChecked = vm::setShowTranslation,
            )
            SwitchRow(
                title = "Book mode",
                subtitle = "Ayahs flow continuously, like a real page",
                checked = s.bookMode,
                onChecked = vm::setBookMode,
            )
            SwitchRow(
                title = "Tajweed colors",
                subtitle = "Recitation rules tinted in the classic palette",
                checked = s.showTajweed,
                onChecked = vm::setShowTajweed,
            )
            if (s.showTajweed) {
                val dark = when (s.darkMode) {
                    DarkMode.SYSTEM -> isSystemInDarkTheme()
                    DarkMode.LIGHT -> false
                    DarkMode.DARK -> true
                }
                Block("What the colors mean") {
                    TajweedPalette.legend.forEach { entry ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(
                                        TajweedPalette.color(entry.rule, dark) ?: cs.onSurface,
                                    ),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(entry.label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            Block("Arabic size") {
                // Local state drives the preview synchronously; DataStore is
                // written once, on release — not on every drag pixel.
                var scale by remember(s.arabicScale) { mutableFloatStateOf(s.arabicScale) }
                Text(
                    "بِسْمِ ٱللَّهِ ٱلرَّحْمَـٰنِ ٱلرَّحِيمِ",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = NotoArabic,
                        fontSize = 24.sp * scale,
                        lineHeight = 24.sp * scale * 2.05f,
                        textDirection = TextDirection.Rtl,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Slider(
                    value = scale,
                    onValueChange = { scale = it },
                    onValueChangeFinished = { vm.setArabicScale(scale) },
                    valueRange = 0.8f..1.8f,
                )
            }

            SectionLabel("Appearance")

            Block("Color") {
                ConnectedRow(
                    options = ColorSource.entries,
                    selected = s.colorSource,
                    label = { if (it == ColorSource.WIRD) "Wird green" else "System theme" },
                    onSelect = vm::setColorSource,
                )
            }
            Block("Dark mode") {
                ConnectedRow(
                    options = DarkMode.entries,
                    selected = s.darkMode,
                    label = {
                        when (it) {
                            DarkMode.SYSTEM -> "System"
                            DarkMode.LIGHT -> "Light"
                            DarkMode.DARK -> "Dark"
                        }
                    },
                    onSelect = vm::setDarkMode,
                )
                SwitchInline(
                    title = "AMOLED black",
                    checked = s.amoledBlack,
                    onChecked = vm::setAmoledBlack,
                )
                SwitchInline(
                    title = "Sepia reading surface",
                    checked = s.sepiaReader,
                    onChecked = vm::setSepiaReader,
                )
            }

            SectionLabel("Audio")

            Block("Reciters") {
                Reciters.ALL.forEach { reciter ->
                    val isDefault = s.reciterId == reciter.dirName
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(reciter.name, style = MaterialTheme.typography.bodyLarge)
                            if (reciter.style != "Murattal") {
                                Text(
                                    reciter.style,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = cs.onSurfaceVariant,
                                )
                            }
                        }
                        // Icons, not labels: check = default reciter, chevron = manage.
                        IconButton(
                            onClick = { vm.setReciter(reciter.dirName) },
                            shapes = IconButtonDefaults.shapes(),
                        ) {
                            Icon(
                                Icons.Default.Check,
                                // Which reciter is the default was carried by the
                                // tint alone, so a screen reader announced the
                                // same thing sixteen times and anyone who cannot
                                // separate green from grey saw sixteen ticks.
                                contentDescription = if (isDefault) {
                                    "${reciter.name} is the default reciter"
                                } else {
                                    "Make ${reciter.name} the default reciter"
                                },
                                tint = if (isDefault) cs.primary else cs.outlineVariant,
                            )
                        }
                        IconButton(
                            onClick = { openReciterManager(reciter.dirName) },
                            shapes = IconButtonDefaults.shapes(),
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowRight,
                                contentDescription = "Manage ${reciter.name} downloads",
                                tint = cs.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Text(
                "Quran text: Tanzil Project (tanzil.net), shipped verbatim under CC BY 3.0. " +
                    "Translation: Noor International (Saheeh) via QuranEnc.com. " +
                    "Tajweed data: cpfair/quran-tajweed (CC BY 4.0). " +
                    "Recitations are downloaded from everyayah.com and are not bundled. " +
                    "Free, ad-free, forever.",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
            // Play requires a privacy policy link inside the app, not only in
            // Console. Keep this pointing at a live, publicly reachable page.
            Text(
                "Privacy policy",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, PRIVACY_POLICY_URL.toUri()),
                            )
                        }
                    }
                    .padding(vertical = 8.dp),
            )
            Text(
                "Licences and notices",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenNotices)
                    .padding(vertical = 8.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.5.sp),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp),
    )
}

@Composable
private fun Block(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            content()
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = checked, onCheckedChange = onChecked)
        }
    }
}

@Composable
private fun SwitchInline(title: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun <T> ConnectedRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, option ->
            ToggleButton(
                checked = option == selected,
                onCheckedChange = { if (it) onSelect(option) },
                modifier = Modifier.weight(1f),
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
            ) {
                Text(label(option), maxLines = 1, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
