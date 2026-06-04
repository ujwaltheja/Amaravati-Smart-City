package com.uc.amaravatismartcity.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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
            AmaravatiMenuBackdrop(Modifier.fillMaxSize())
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
                        text = "Amaravati Rising",
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
                        text = "Build a realistic smart city on the Krishna riverfront.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Place real roads, towers, transit, river assets, and traffic systems while shaping the future capital.",
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
                        Text("START BUILDING", fontWeight = FontWeight.Black)
                    }
                    Spacer(modifier = Modifier.height(9.dp))
                    OutlinedButton(
                        onClick = onAbout,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 14.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("LEARN MORE")
                    }
                }
            }
        }
    }
}

@Composable
private fun AmaravatiMenuBackdrop(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val ground = size.height * 0.72f
        val river = size.height * 0.56f
        drawCircle(
            color = Color(0xFFFFD54F).copy(alpha = 0.2f),
            radius = size.minDimension * 0.28f,
            center = Offset(size.width * 0.78f, size.height * 0.18f)
        )
        drawRect(Color(0xFF2F8FAD).copy(alpha = 0.55f), Offset(0f, river), Size(size.width, ground - river))
        repeat(7) { i ->
            val y = river + i * 18f
            drawLine(Color.White.copy(alpha = 0.16f), Offset(0f, y), Offset(size.width, y + 26f), strokeWidth = 2f)
        }

        val palette = listOf(Color(0xFFD6C7A3), Color(0xFFB8D6C7), Color(0xFFA7C5D8), Color(0xFFE2E6D8))
        repeat(14) { i ->
            val w = size.width / 18f
            val h = size.height * (0.1f + (i % 4) * 0.035f)
            val x = i * w * 1.35f - w
            val y = river - h
            drawRect(palette[i % palette.size].copy(alpha = 0.76f), Offset(x, y), Size(w, h))
            drawRect(Color(0xFF07131F).copy(alpha = 0.14f), Offset(x + w * 0.08f, y + h * 0.14f), Size(w * 0.84f, h * 0.12f))
        }

        drawRect(Color(0xFF263238).copy(alpha = 0.78f), Offset(0f, ground), Size(size.width, size.height - ground))
        repeat(9) { i ->
            val x = i * size.width / 8f
            drawLine(Color(0xFFFFF176).copy(alpha = 0.55f), Offset(x, ground + 18f), Offset(x + size.width * 0.12f, size.height), strokeWidth = 3f)
        }
        drawCircle(Color.White.copy(alpha = 0.18f), size.width * 0.42f, Offset(size.width * 0.5f, ground), style = Stroke(2f))
    }
}

@Preview(showBackground = true)
@Composable
fun MainMenuPreview() {
    AmaravatiSmartCityTheme {
        MainMenuScreen(onNewGame = {}, onAbout = {})
    }
}
