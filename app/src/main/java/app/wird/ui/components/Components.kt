package app.wird.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wird.data.ReaderContextType

/**
 * The index badge language: the number lives INSIDE its division's shape, so the
 * shape itself names the division — and, for a surah, its revelation:
 *   Surah  → Square (Meccan) · Arch (Medinan)
 *   Juz    → VerySunny        Hizb → Sunny (8-pointed pair)
 *   Page   → Slanted
 */
@Composable
fun badgeShape(type: ReaderContextType, revelation: String? = null): Shape {
    // toShape() is @Composable in this alpha — call it in composable context.
    val square = MaterialShapes.Square.toShape()
    val arch = MaterialShapes.Arch.toShape()
    val juz = MaterialShapes.VerySunny.toShape()
    val hizb = MaterialShapes.Sunny.toShape()
    val slanted = MaterialShapes.Slanted.toShape()
    return when (type) {
        ReaderContextType.SURAH ->
            if (revelation.equals("Meccan", ignoreCase = true)) square else arch
        ReaderContextType.JUZ -> juz
        ReaderContextType.HIZB -> hizb
        ReaderContextType.PAGE -> slanted
    }
}

@Composable
fun NumberBadge(
    number: Int,
    type: ReaderContextType,
    modifier: Modifier = Modifier,
    revelation: String? = null,
    size: Dp = 40.dp,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    // The shape is the only thing that says Meccan or Medinan, and a shape is
    // not announced. Without this a screen reader read "2" and stopped.
    val spoken = buildString {
        append(type.name.lowercase().replaceFirstChar { it.uppercase() })
        append(' ')
        append(number)
        if (type == ReaderContextType.SURAH && !revelation.isNullOrBlank()) {
            append(", ")
            append(revelation)
        }
    }
    Box(
        modifier
            .size(size)
            .clip(badgeShape(type, revelation))
            .background(container)
            .semantics { contentDescription = spoken },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            number.toString(),
            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
            color = content,
            textAlign = TextAlign.Center,
            fontSize = if (number >= 100) 12.sp else 14.sp,
            maxLines = 1,
        )
    }
}

/** Hand-drawn 24dp glyphs for icons the core set lacks. */
object WirdIcons {

    val OpenBook: ImageVector by lazy {
        ImageVector.Builder(
            name = "OpenBook", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(fill = androidx.compose.ui.graphics.SolidColor(Color.Black)) {
                moveTo(12f, 6.6f)
                curveTo(10.4f, 5.2f, 8.2f, 4.7f, 6.2f, 5f)
                curveTo(5.5f, 5.1f, 5f, 5.7f, 5f, 6.4f)
                verticalLineTo(16.9f)
                curveTo(5f, 17.7f, 5.7f, 18.3f, 6.5f, 18.2f)
                curveTo(8.3f, 18f, 10.3f, 18.5f, 11.6f, 19.7f)
                curveTo(11.8f, 19.9f, 12.2f, 19.9f, 12.4f, 19.7f)
                curveTo(13.7f, 18.5f, 15.7f, 18f, 17.5f, 18.2f)
                curveTo(18.3f, 18.3f, 19f, 17.7f, 19f, 16.9f)
                verticalLineTo(6.4f)
                curveTo(19f, 5.7f, 18.5f, 5.1f, 17.8f, 5f)
                curveTo(15.8f, 4.7f, 13.6f, 5.2f, 12f, 6.6f)
                close()
                moveTo(11.25f, 8f)
                verticalLineTo(17.2f)
                curveTo(9.9f, 16.5f, 8.3f, 16.2f, 6.75f, 16.3f)
                verticalLineTo(6.8f)
                curveTo(8.4f, 6.7f, 10.1f, 7.1f, 11.25f, 8f)
                close()
                moveTo(12.75f, 8f)
                curveTo(13.9f, 7.1f, 15.6f, 6.7f, 17.25f, 6.8f)
                verticalLineTo(16.3f)
                curveTo(15.7f, 16.2f, 14.1f, 16.5f, 12.75f, 17.2f)
                close()
            }
        }.build()
    }

    val Bookmark: ImageVector by lazy {
        ImageVector.Builder(
            name = "Bookmark", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(fill = androidx.compose.ui.graphics.SolidColor(Color.Black)) {
                moveTo(6.5f, 4f)
                curveTo(6.5f, 3.45f, 6.95f, 3f, 7.5f, 3f)
                horizontalLineTo(16.5f)
                curveTo(17.05f, 3f, 17.5f, 3.45f, 17.5f, 4f)
                verticalLineTo(20.1f)
                curveTo(17.5f, 20.85f, 16.65f, 21.3f, 16.03f, 20.87f)
                lineTo(12f, 18.1f)
                lineTo(7.97f, 20.87f)
                curveTo(7.35f, 21.3f, 6.5f, 20.85f, 6.5f, 20.1f)
                close()
            }
        }.build()
    }

    val BookmarkOutline: ImageVector by lazy {
        ImageVector.Builder(
            name = "BookmarkOutline", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(fill = androidx.compose.ui.graphics.SolidColor(Color.Black)) {
                moveTo(6.5f, 4f)
                curveTo(6.5f, 3.45f, 6.95f, 3f, 7.5f, 3f)
                horizontalLineTo(16.5f)
                curveTo(17.05f, 3f, 17.5f, 3.45f, 17.5f, 4f)
                verticalLineTo(20.1f)
                curveTo(17.5f, 20.85f, 16.65f, 21.3f, 16.03f, 20.87f)
                lineTo(12f, 18.1f)
                lineTo(7.97f, 20.87f)
                curveTo(7.35f, 21.3f, 6.5f, 20.85f, 6.5f, 20.1f)
                close()
                moveTo(8.25f, 4.75f)
                verticalLineTo(18.8f)
                lineTo(12f, 16.22f)
                lineTo(15.75f, 18.8f)
                verticalLineTo(4.75f)
                close()
            }
        }.build()
    }

    val Repeat: ImageVector by lazy {
        ImageVector.Builder(
            name = "Repeat", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(fill = androidx.compose.ui.graphics.SolidColor(Color.Black)) {
                moveTo(7f, 7f)
                horizontalLineTo(17f)
                verticalLineTo(10f)
                lineTo(21f, 6f)
                lineTo(17f, 2f)
                verticalLineTo(5f)
                horizontalLineTo(5f)
                verticalLineTo(11f)
                horizontalLineTo(7f)
                close()
                moveTo(17f, 17f)
                horizontalLineTo(7f)
                verticalLineTo(14f)
                lineTo(3f, 18f)
                lineTo(7f, 22f)
                verticalLineTo(19f)
                horizontalLineTo(19f)
                verticalLineTo(13f)
                horizontalLineTo(17f)
                close()
            }
        }.build()
    }

    val Tune: ImageVector by lazy {
        ImageVector.Builder(
            name = "Tune", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(fill = androidx.compose.ui.graphics.SolidColor(Color.Black)) {
                moveTo(3f, 6.5f); horizontalLineTo(13f); verticalLineTo(8f); horizontalLineTo(3f); close()
                moveTo(17f, 6.5f); horizontalLineTo(21f); verticalLineTo(8f); horizontalLineTo(17f); close()
                moveTo(13f, 4.5f); horizontalLineTo(15f); verticalLineTo(10f); horizontalLineTo(13f); close()
                moveTo(3f, 16f); horizontalLineTo(7f); verticalLineTo(17.5f); horizontalLineTo(3f); close()
                moveTo(11f, 16f); horizontalLineTo(21f); verticalLineTo(17.5f); horizontalLineTo(11f); close()
                moveTo(9f, 14f); horizontalLineTo(11f); verticalLineTo(19.5f); horizontalLineTo(9f); close()
                moveTo(3f, 11.25f); horizontalLineTo(21f); verticalLineTo(12.75f); horizontalLineTo(3f); close()
            }
        }.build()
    }

    val Download: ImageVector by lazy {
        ImageVector.Builder(
            name = "Download", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(fill = androidx.compose.ui.graphics.SolidColor(Color.Black)) {
                moveTo(11f, 4f); horizontalLineTo(13f); verticalLineTo(12.2f)
                lineTo(16.2f, 9f); lineTo(17.6f, 10.4f); lineTo(12f, 16f)
                lineTo(6.4f, 10.4f); lineTo(7.8f, 9f); lineTo(11f, 12.2f); close()
                moveTo(5f, 18f); horizontalLineTo(19f); verticalLineTo(20f); horizontalLineTo(5f); close()
            }
        }.build()
    }
}
