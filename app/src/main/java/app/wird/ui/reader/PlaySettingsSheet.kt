package app.wird.ui.reader

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wird.audio.RepeatCount
import app.wird.data.Ayah
import app.wird.data.Reciters
import app.wird.data.UserSettings
import app.wird.ui.WirdViewModel
import kotlin.math.roundToInt

/**
 * Play settings: repeat count, reciter, and (from the permanent group) the
 * range of the current context to play.
 */
@Composable
fun PlaySettingsSheet(
    vm: WirdViewModel,
    settings: UserSettings,
    ayahs: List<Ayah>,
    selectedAyahId: Int?,
    showRange: Boolean,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val repeat by vm.audio.repeat.collectAsStateWithLifecycle()
    var range by remember(ayahs) { mutableStateOf(1f..ayahs.size.toFloat()) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            val selected = ayahs.firstOrNull { it.id == selectedAyahId }
            Text(
                if (selected != null) "Play verse ${selected.surah}:${selected.num}" else "Playback",
                style = MaterialTheme.typography.titleLargeEmphasized,
            )

            // Repeat each verse N times.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Repeat", style = MaterialTheme.typography.titleSmall)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                ) {
                    RepeatCount.entries.forEachIndexed { index, option ->
                        ToggleButton(
                            checked = repeat == option,
                            onCheckedChange = { if (it) vm.setRepeat(option) },
                            modifier = Modifier.weight(1f),
                            shapes = when (index) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                RepeatCount.entries.lastIndex ->
                                    ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            },
                        ) {
                            Text(option.label, maxLines = 1)
                        }
                    }
                }
            }

            if (showRange && ayahs.size > 1) {
                // The slider works in list positions, but a position is only the
                // same thing as a verse number inside a single surah. In a juz,
                // hizb or page the picker was labelling ayah 12 of An-Nisa as
                // "12" while sitting on a row that was actually 4:23 — and the
                // range it produced had nothing to do with the numbers shown.
                val crossesSurahs = remember(ayahs) {
                    ayahs.first().surah != ayahs.last().surah
                }
                val labelFor: (Int) -> String = remember(ayahs, crossesSurahs) {
                    { position ->
                        val ayah = ayahs.getOrNull(position - 1)
                        when {
                            ayah == null -> "$position"
                            crossesSurahs -> "${ayah.surah}:${ayah.num}"
                            else -> "${ayah.num}"
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Range", style = MaterialTheme.typography.titleSmall)
                    AyahRangeSlider(
                        range = range,
                        max = ayahs.size,
                        labelFor = labelFor,
                        onRange = { range = it },
                    )
                }
            }

            // Reciter — selecting here also becomes the default.
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Reciter", style = MaterialTheme.typography.titleSmall)
                Reciters.ALL.forEach { reciter ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .selectable(
                                selected = settings.reciterId == reciter.dirName,
                                onClick = { vm.setReciter(reciter.dirName) },
                            )
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = settings.reciterId == reciter.dirName,
                            onClick = null,
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(reciter.name, style = MaterialTheme.typography.bodyLarge)
                            if (reciter.style != "Murattal") {
                                Text(
                                    reciter.style,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = cs.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            FilledIconButton(
                onClick = {
                    if (selected != null) {
                        vm.playSingle(selected)
                    } else {
                        vm.playRange(
                            range.start.roundToInt() - 1,
                            range.endInclusive.roundToInt() - 1,
                        )
                    }
                    onDismiss()
                },
                shapes = IconButtonDefaults.shapes(),
                modifier = Modifier.align(Alignment.CenterHorizontally).size(64.dp),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Play", Modifier.size(30.dp))
            }
        }
    }
}

/**
 * The ayah range picker (Material 3 Expressive). Two number steppers (From / To,
 * each with up/down tickers) sit above a two-thumb RangeSlider and share one
 * state, so dragging a thumb updates its stepper and vice-versa. While a thumb
 * is dragged it enlarges and a LARGE value bubble rises directly above it, so
 * the exact ayah is always legible. Integer snapping fires a haptic tick per
 * step; the tick dots themselves are hidden (over a long surah they'd smear
 * into a solid line) — the steppers and bubble carry the numbers. Track and
 * thumbs are coloured to sit clearly above the sheet surface, not blend in.
 */
@Composable
private fun AyahRangeSlider(
    range: ClosedFloatingPointRange<Float>,
    max: Int,
    labelFor: (Int) -> String,
    onRange: (ClosedFloatingPointRange<Float>) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val startSource = remember { MutableInteractionSource() }
    val endSource = remember { MutableInteractionSource() }
    val startDragged by startSource.collectIsDraggedAsState()
    val endDragged by endSource.collectIsDraggedAsState()

    val startVal = range.start.roundToInt()
    val endVal = range.endInclusive.roundToInt()
    fun update(newStart: Int, newEnd: Int) = onRange(newStart.toFloat()..newEnd.toFloat())

    // Haptic tick on every snapped step change (drag or ticker).
    var lastSnapped by remember { mutableStateOf(startVal to endVal) }
    LaunchedEffect(startVal, endVal) {
        val snapped = startVal to endVal
        if (snapped != lastSnapped) {
            lastSnapped = snapped
            haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }
    }

    // Active + thumbs in the accent; the inactive track a neutral tint of
    // onSurface so it stays clearly visible in ANY theme (dynamic included)
    // and never blends into the sheet.
    val sliderColors = SliderDefaults.colors(
        thumbColor = cs.primary,
        activeTrackColor = cs.primary,
        inactiveTrackColor = cs.onSurface.copy(alpha = 0.18f),
    )

    // Thumbs report their real centre-x (window space) so the value bubble sits
    // EXACTLY over the active thumb, independent of the slider's inset maths.
    var containerLeftPx by remember { mutableFloatStateOf(0f) }
    var startCenterPx by remember { mutableFloatStateOf(0f) }
    var endCenterPx by remember { mutableFloatStateOf(0f) }
    val bubble = 50.dp
    val startLabel = labelFor(startVal)
    val endLabel = labelFor(endVal)

    Column(
        modifier = Modifier.onGloballyPositioned { containerLeftPx = it.positionInWindow().x },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Fixed-height strip holds the value bubble above the slider, so grabbing
        // a thumb never reflows the slider mid-drag.
        Box(Modifier.fillMaxWidth().height(56.dp)) {
            DragBubble(startDragged, startLabel, bubble, startCenterPx - containerLeftPx)
            DragBubble(endDragged, endLabel, bubble, endCenterPx - containerLeftPx)
        }

        RangeSlider(
            value = range,
            onValueChange = onRange,
            valueRange = 1f..max.toFloat(),
            steps = (max - 2).coerceAtLeast(0),
            colors = sliderColors,
            startInteractionSource = startSource,
            endInteractionSource = endSource,
            startThumb = { RangeThumb(startDragged, cs.primary) { startCenterPx = it } },
            endThumb = { RangeThumb(endDragged, cs.primary) { endCenterPx = it } },
            // Standard M3 track minus the stop-indicator dots (the faint "two
            // circles") and tick dots; snapping is kept.
            track = { state ->
                SliderDefaults.Track(
                    rangeSliderState = state,
                    colors = sliderColors,
                    drawStopIndicator = {},
                    drawTick = { _, _ -> },
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )

        // Steppers — From / To, each with up/down tickers — directly below the slider.
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StepperField(
                label = "From",
                value = startLabel,
                modifier = Modifier.weight(1f),
                onUp = { if (startVal < endVal) update(startVal + 1, endVal) },
                onDown = { if (startVal > 1) update(startVal - 1, endVal) },
                upEnabled = startVal < endVal,
                downEnabled = startVal > 1,
            )
            StepperField(
                label = "To",
                value = endLabel,
                modifier = Modifier.weight(1f),
                onUp = { if (endVal < max) update(startVal, endVal + 1) },
                onDown = { if (endVal > startVal) update(startVal, endVal - 1) },
                upEnabled = endVal < max,
                downEnabled = endVal > startVal,
            )
        }
    }
}

/**
 * A large circular value bubble that scales up out of the thumb while dragged
 * (and back down on release), placed by the thumb's measured [centerXPx] so it
 * stays exactly aligned. A BoxScope extension so `align` resolves cleanly.
 */
@Composable
private fun BoxScope.DragBubble(visible: Boolean, value: String, size: Dp, centerXPx: Float) {
    val cs = MaterialTheme.colorScheme
    val p by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "bubble",
    )
    // A "2:255" reference does not fit a circle sized for "255", so the bubble
    // becomes a pill when the label is long rather than clipping it.
    val width = if (value.length > 3) size * 1.7f else size
    val halfPx = with(LocalDensity.current) { width.toPx() / 2f }
    Box(
        Modifier
            .align(Alignment.BottomStart)
            .offset { IntOffset((centerXPx - halfPx).roundToInt(), 0) }
            .graphicsLayer {
                scaleX = p
                scaleY = p
                alpha = p
                transformOrigin = TransformOrigin(0.5f, 1f)
            }
            .size(width = width, height = size)
            .clip(if (width == size) CircleShape else RoundedCornerShape(50))
            .background(cs.primary),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            value,
            color = cs.onPrimary,
            style = MaterialTheme.typography.titleMediumEmphasized.copy(fontFeatureSettings = "tnum"),
            maxLines = 1,
        )
    }
}

/** A vertical pill handle that enlarges while dragged. Fixed 24dp-wide measured
 *  slot keeps the track inset stable; reports its centre-x for bubble alignment. */
@Composable
private fun RangeThumb(dragged: Boolean, color: Color, onCenterX: (Float) -> Unit) {
    val w by animateDpAsState(
        if (dragged) 14.dp else 8.dp,
        MaterialTheme.motionScheme.fastSpatialSpec(), label = "thumbW",
    )
    val h by animateDpAsState(
        if (dragged) 36.dp else 28.dp,
        MaterialTheme.motionScheme.fastSpatialSpec(), label = "thumbH",
    )
    Box(
        Modifier
            .size(width = 24.dp, height = 40.dp)
            .onGloballyPositioned { onCenterX(it.positionInWindow().x + it.size.width / 2f) },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(width = w, height = h).clip(RoundedCornerShape(50)).background(color))
    }
}

/**
 * A number field with an up/down ticker pair.
 *
 * The tickers sit side by side rather than stacked. Stacked, each one measured
 * 44×30dp — under the 48dp minimum touch target in both axes, doubly so because
 * two of them shared a 60dp column, so a miss did not just do nothing, it
 * stepped the value the wrong way.
 */
@Composable
private fun StepperField(
    label: String,
    value: String,
    onUp: () -> Unit,
    onDown: () -> Unit,
    upEnabled: Boolean,
    downEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = cs.surfaceContainerHighest,
        modifier = modifier,
    ) {
        Column(Modifier.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)) {
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    value,
                    style = MaterialTheme.typography.headlineSmallEmphasized.copy(
                        fontFeatureSettings = "tnum",
                    ),
                    color = cs.onSurface,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Ticker(Icons.Default.KeyboardArrowDown, "Decrease $label", onDown, downEnabled)
                Ticker(Icons.Default.KeyboardArrowUp, "Increase $label", onUp, upEnabled)
            }
        }
    }
}

@Composable
private fun Ticker(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    onClick: () -> Unit,
    enabled: Boolean,
) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = desc,
            tint = if (enabled) cs.primary else cs.onSurface.copy(alpha = 0.3f),
        )
    }
}
