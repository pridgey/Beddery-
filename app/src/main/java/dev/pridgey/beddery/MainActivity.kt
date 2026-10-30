package dev.pridgey.beddery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.pridgey.beddery.ui.screens.HomeScreen
import dev.pridgey.beddery.ui.screens.SettingsScreen
import dev.pridgey.beddery.ui.screens.SaverDetailScreen
import dev.pridgey.beddery.ui.theme.BedderyTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            BedderyTheme {
                val settings by viewModel.settings.collectAsStateWithLifecycle()
                val s = settings
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    if (s != null) {
                        LaunchedEffect(s.standbyEnabled) { viewModel.ensureStandbyRunning(s) }
                        val nav = rememberNavController()
                        NavHost(
                            navController = nav,
                            startDestination = "home",
                            enterTransition = { slideInHorizontally { it / 4 } + fadeIn() },
                            exitTransition = { fadeOut() },
                            popEnterTransition = { fadeIn() },
                            popExitTransition = { slideOutHorizontally { it / 4 } + fadeOut() },
                        ) {
                            composable("home") {
                                HomeScreen(
                                    settings = s,
                                    viewModel = viewModel,
                                    onOpenSaver = { id -> nav.navigate("saver/$id") },
                                    onOpenSettings = { nav.navigate("settings") },
                                )
                            }
                            composable("settings") {
                                SettingsScreen(settings = s, viewModel = viewModel, onBack = { nav.popBackStack() })
                            }
                            composable("saver/{id}") { entry ->
                                SaverDetailScreen(
                                    saverId = entry.arguments?.getString("id").orEmpty(),
                                    settings = s,
                                    viewModel = viewModel,
                                    onBack = { nav.popBackStack() },
                                    onOpenSaver = { id -> nav.navigate("saver/$id") },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
