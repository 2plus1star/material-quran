package app.wird

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.wird.ui.AppRoot
import app.wird.ui.WirdViewModel
import app.wird.ui.theme.WirdTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: WirdViewModel = viewModel()
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            settings?.let { current ->
                WirdTheme(current) {
                    AppRoot(viewModel)
                }
            }
        }
    }
}
