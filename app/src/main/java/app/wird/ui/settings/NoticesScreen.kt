package app.wird.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The licence notices, read straight out of `assets/NOTICE.txt`.
 *
 * Not decoration. Tanzil's terms say the copyright block "shall be reproduced
 * appropriately in all files derived from or containing substantial portion of
 * this text" and require a link to tanzil.net; `build_db.py` strips those blocks
 * while parsing, so this screen is the only place they survive in the shipped
 * app. The OFL likewise requires the licence to travel with the fonts.
 */
@Composable
fun NoticesScreen() {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var notice by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        notice = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open("NOTICE.txt").bufferedReader().use { it.readText() }
            }.getOrElse { "Licence notices could not be loaded." }
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Tanzil requires a link, not merely a mention, "to enable users to
            // keep track of changes".
            listOf(
                "tanzil.net" to "https://tanzil.net",
                "quranenc.com" to "https://quranenc.com",
                "openfontlicense.org" to "https://openfontlicense.org/",
                "creativecommons.org/licenses/by/4.0/" to "https://creativecommons.org/licenses/by/4.0/",
            ).forEach { (label, url) ->
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            } catch (_: ActivityNotFoundException) {
                                // No browser; the text below is the operative part.
                            }
                        },
                )
            }

            Text(
                notice ?: "",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = cs.onSurfaceVariant,
            )
        }
    }
}
