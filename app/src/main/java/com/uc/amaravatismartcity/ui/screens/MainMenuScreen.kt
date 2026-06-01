package com.uc.amaravatismartcity.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.uc.amaravatismartcity.ui.theme.AmaravatiSmartCityTheme
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun MainMenuScreen(
    onNewGame: () -> Unit,
    onAbout: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Amaravati Rising",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "City Builder Tycoon",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.height(48.dp))
            Button(
                onClick = onNewGame,
                modifier = Modifier.fillMaxWidth(0.7f),
                shape = MaterialTheme.shapes.large
            ) {
                Text("New Game")
            }
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = onAbout,
                modifier = Modifier.fillMaxWidth(0.7f),
                shape = MaterialTheme.shapes.large
            ) {
                Text("About Amaravati")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainMenuPreview() {
    AmaravatiSmartCityTheme {
        MainMenuScreen(onNewGame = {}, onAbout = {})
    }
}
