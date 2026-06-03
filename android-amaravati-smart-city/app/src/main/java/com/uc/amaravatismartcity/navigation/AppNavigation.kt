package com.uc.amaravatismartcity.navigation

import kotlinx.serialization.Serializable
import androidx.navigation3.runtime.NavKey

@Serializable
sealed interface AppRoute : NavKey {
    @Serializable
    data object MainMenu : AppRoute
    
    @Serializable
    data object CityView : AppRoute
    
    @Serializable
    data object About : AppRoute
}
