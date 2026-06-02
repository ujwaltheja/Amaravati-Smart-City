package com.uc.amaravatismartcity.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uc.amaravatismartcity.ui.theme.AmaravatiSmartCityTheme

@Composable
fun MainMenuScreen(
    onNewGame: () -> Unit,
    onAbout: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF06131D),
                            Color(0xFF0E2740),
                            Color(0xFF12405F)
                        )
                    )
                )
        ) {
            Card(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(20.dp)
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
                ),
                shape = RoundedCornerShape(28.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "అమరావతి రైజింగ్",
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "AMARAVATI RISING",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 3.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "కృష్ణా నది తీరంలో వాస్తవిక స్మార్ట్ సిటీని నిర్మించండి",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Build a living 3D city. Place real roads. Watch traffic flow. Shape the future capital.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.5.sp,
                        modifier = Modifier.padding(horizontal = 18.dp)
                    )
                    Spacer(modifier = Modifier.height(26.dp))
                    Button(
                        onClick = onNewGame,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 15.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("కొత్త నగరం ప్రారంభించండి  •  START BUILDING", fontWeight = FontWeight.Black)
                    }
                    Spacer(modifier = Modifier.height(9.dp))
                    OutlinedButton(
                        onClick = onAbout,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 14.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("LEARN MORE  /  సమాచారం")
                    }
                }
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
