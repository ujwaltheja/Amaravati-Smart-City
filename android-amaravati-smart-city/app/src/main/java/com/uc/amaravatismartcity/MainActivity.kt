package com.uc.amaravatismartcity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.uc.amaravatismartcity.navigation.AppRoute
import com.uc.amaravatismartcity.ui.screens.AboutScreen
import com.uc.amaravatismartcity.ui.screens.CityViewScreen
import com.uc.amaravatismartcity.ui.screens.MainMenuScreen
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
                    val backStack = rememberNavBackStack(AppRoute.MainMenu)

                    NavDisplay(
                        backStack = backStack,
                        onBack = { backStack.removeLastOrNull() },
                        entryProvider = entryProvider {
                            entry<AppRoute.MainMenu> {
                                MainMenuScreen(
                                    onNewGame = { backStack.add(AppRoute.CityView) },
                                    onAbout = { backStack.add(AppRoute.Dashboard) }
                                )
                            }
                            entry<AppRoute.CityView> {
                                CityViewScreen(
                                    onBack = { backStack.removeLastOrNull() }
                                )
                            }
                            entry<AppRoute.Dashboard> {
                                AboutScreen(
                                    onStartGame = {
                                        backStack.add(AppRoute.CityView)
                                    }
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}
