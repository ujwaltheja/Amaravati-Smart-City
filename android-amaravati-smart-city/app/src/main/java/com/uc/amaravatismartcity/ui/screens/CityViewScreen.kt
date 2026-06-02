package com.uc.amaravatismartcity.ui.screens

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uc.amaravatismartcity.game.AmaravatiGameSurface
import com.uc.amaravatismartcity.game.GameViewModel

@Composable
fun CityViewScreen(
    viewModel: GameViewModel = viewModel()
) {
    AmaravatiGameSurface(viewModel = viewModel)
}
