package dev.ishubhamsingh.shelfly

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.ishubhamsingh.shelfly.ui.navigation.Screen
import dev.ishubhamsingh.shelfly.ui.navigation.ShelflyNavHost
import dev.ishubhamsingh.shelfly.ui.theme.ShelflyTheme
import androidx.compose.runtime.getValue

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val notifItemId = intent.getStringExtra("ITEM_ID")
        setContent {
            val dynamicColor     by viewModel.dynamicColor.collectAsStateWithLifecycle()
            val startDestination by viewModel.startDestination.collectAsStateWithLifecycle()
            ShelflyTheme(dynamicColor = dynamicColor) {
                val dest = notifItemId?.let { Screen.Detail.createRoute(it) } ?: startDestination
                dest?.let { ShelflyNavHost(startDestination = it) }
            }
        }
    }
}
