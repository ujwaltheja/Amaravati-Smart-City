package com.uc.amaravatismartcity.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uc.amaravatismartcity.game.GameState
import com.uc.amaravatismartcity.game.GameViewModel
import io.github.sceneview.Scene
import io.github.sceneview.math.Position
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNodes
import kotlinx.coroutines.delay

data class PlacedObject(val path: String, val position: Position)

@Composable
fun CityViewScreen(
    modifier: Modifier = Modifier,
    viewModel: GameViewModel = viewModel()
) {
    val gameState by viewModel.gameState.collectAsState()
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)

    var placedObjects by remember {
        mutableStateOf(
            listOf(
                PlacedObject("models/City-Commercial/building-a.glb", Position(-2f, 0f, -4f)),
                PlacedObject("models/City-Commercial/building-b.glb", Position(2f, 0f, -4f)),
                PlacedObject("models/Roads and Bridges/road-straight.glb", Position(-2f, 0f, -2f)),
                PlacedObject("models/Roads and Bridges/road-straight.glb", Position(0f, 0f, -2f)),
                PlacedObject("models/Roads and Bridges/road-straight.glb", Position(2f, 0f, -2f))
            )
        )
    }

    // Game loop
    LaunchedEffect(Unit) {
        while (true) {
            delay(5000)
            // Collect taxes: population * happiness / 100
            val taxes = (gameState.population * (gameState.happiness / 100f)).toLong()
            viewModel.updateMoney(taxes)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Scene(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            modelLoader = modelLoader,
            childNodes = rememberNodes(placedObjects) {
                placedObjects.forEach { obj ->
                    val instance = modelLoader.createModelInstance(obj.path)
                    if (instance != null) {
                        add(ModelNode(modelInstance = instance).apply {
                            position = obj.position
                        })
                    }
                }
            }
        )

        // HUD Overlay
        HUD(gameState = gameState, modifier = Modifier.align(Alignment.TopCenter))

        // Build Menu
        BuildMenu(
            modifier = Modifier.align(Alignment.BottomCenter),
            onBuild = { type ->
                when (type) {
                    "Commercial" -> {
                        if (gameState.money >= 500) {
                            viewModel.updateMoney(-500)
                            viewModel.updatePopulation(20)
                            viewModel.updateHappiness(5)
                            placedObjects = placedObjects + PlacedObject(
                                "models/City-Commercial/building-j.glb", 
                                Position((0..4).random().toFloat() - 2f, 0f, (0..4).random().toFloat() - 6f)
                            )
                        }
                    }
                    "Road" -> {
                        if (gameState.money >= 50) {
                            viewModel.updateMoney(-50)
                            placedObjects = placedObjects + PlacedObject(
                                "models/Roads and Bridges/road-straight.glb", 
                                Position((0..4).random().toFloat() - 2f, 0f, (0..4).random().toFloat() - 4f)
                            )
                        }
                    }
                    "Park" -> {
                        if (gameState.money >= 200) {
                            viewModel.updateMoney(-200)
                            viewModel.updateHappiness(15)
                            viewModel.updateSustainability(10)
                            // Assuming we have some low detail building or tree for park
                            placedObjects = placedObjects + PlacedObject(
                                "models/City-Commercial/low-detail-building-a.glb", 
                                Position((0..4).random().toFloat() - 2f, 0f, (0..4).random().toFloat() - 5f)
                            )
                        }
                    }
                }
            }
        )
    }
}

@Composable
fun HUD(gameState: GameState, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = gameState.cityName,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                HUDItem("Money", "₹${gameState.money}")
                HUDItem("Population", "${gameState.population}")
                HUDItem("Happiness", "${gameState.happiness}%")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                HUDItem("Water", "${gameState.water}%")
                HUDItem("Power", "${gameState.power}%")
                HUDItem("Sustainability", "${gameState.sustainabilityScore}")
            }
        }
    }
}

@Composable
fun HUDItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun BuildMenu(modifier: Modifier = Modifier, onBuild: (String) -> Unit) {
    val buildOptions = listOf(
        Pair("Commercial", 500),
        Pair("Road", 50),
        Pair("Park", 200)
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        shape = MaterialTheme.shapes.large
    ) {
        LazyRow(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(buildOptions) { option ->
                Button(onClick = { onBuild(option.first) }) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "Build ${option.first}", fontWeight = FontWeight.Bold)
                        Text(text = "₹${option.second}", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
