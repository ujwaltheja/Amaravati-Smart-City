package com.uc.amaravatismartcity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation3.NavDisplay
import androidx.navigation3.rememberNavBackStack
import androidx.navigation3.entryProvider
import com.uc.amaravatismartcity.navigation.AppRoute
import com.uc.amaravatismartcity.ui.screens.MainMenuScreen
import com.uc.amaravatismartcity.ui.screens.CityViewScreen
import com.uc.amaravatismartcity.ui.theme.AmaravatiSmartCityTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AmaravatiSmartCityTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val backstack = rememberNavBackStack(initialKey = AppRoute.MainMenu)

                    NavDisplay(
                        backstack = backstack,
                        onBack = { if (backstack.size > 1) backstack.removeLast() },
                        entryProvider = entryProvider {
                            entry<AppRoute.MainMenu> {
                                MainMenuScreen(
                                    onNewGame = { backstack.add(AppRoute.CityView) },
                                    onAbout = { /* TODO */ }
                                )
                            }
                            entry<AppRoute.CityView> {
                                CityViewScreen()
                            }
                            entry<AppRoute.Dashboard> {
                                // Placeholder for Dashboard
                                Surface(color = MaterialTheme.colorScheme.secondary) { }
                            }
                        }
                    )
                }
            }
        }
    }
}
