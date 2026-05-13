package dev.ishubhamsingh.shelfly

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import dev.ishubhamsingh.shelfly.ui.navigation.ShelflyNavHost
import dev.ishubhamsingh.shelfly.ui.theme.ShelflyTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShelflyTheme {
                ShelflyNavHost()
            }
        }
    }
}
