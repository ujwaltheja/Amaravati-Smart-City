package com.uc.amaravatismartcity.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uc.amaravatismartcity.models.BuildingCatalog
import com.uc.amaravatismartcity.models.BuildingDefinition
import com.uc.amaravatismartcity.models.GlbAssetIndex
import io.github.sceneview.SceneView
import io.github.sceneview.math.Position
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelLoader
import kotlinx.coroutines.delay

private data class PlacedBuilding(
    val id: Long,
    val definition: BuildingDefinition,
    val position: Position,
    val scale: Float = 1f
)

@Composable
fun AmaravatiGameSurface(
    modifier: Modifier = Modifier,
    viewModel: GameViewModel = viewModel()
) {
    val context = LocalContext.current
    val assetPaths by produceState(initialValue = emptyList<String>(), context) {
        value = GlbAssetIndex.scan(context.assets)
    }
    val buildingCatalog = remember(assetPaths) { BuildingCatalog.defaultCatalog(assetPaths) }
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val cameraManipulator = rememberCameraManipulator()
    val gameState by viewModel.gameState.collectAsStateWithLifecycle()
    val currentNews by viewModel.currentNews.collectAsStateWithLifecycle()
    val activeGoal by viewModel.activeGoal.collectAsStateWithLifecycle()

    val placedBuildings = remember {
        mutableStateListOf<PlacedBuilding>()
    }
    var selectedBuilding by remember(assetPaths) {
        mutableStateOf<BuildingDefinition?>(buildingCatalog.firstOrNull())
    }

    LaunchedEffect(Unit) {
        val newsItems = listOf(
            "New IT park planned for the city center.",
            "Citizen happiness is on the rise!",
            "Sustainability initiative launched: More green spaces needed.",
            "Water supply stabilized in all sectors.",
            "Traffic congestion reported near the government node.",
            "New residents moving into the city every day."
        )
        var newsIndex = 0
        while (true) {
            delay(2500)
            val currentState = viewModel.gameState.value
            viewModel.updateMoney((currentState.population * 6L).coerceAtLeast(0L))
            viewModel.updateHappiness(if (currentState.pollution > 60) -1 else 1)
            viewModel.updatePollution(if (currentState.population > 300) 1 else 0)
            viewModel.recalculateSmartScore()
            viewModel.checkGoals(currentState)
            
            if (System.currentTimeMillis() % 10000 < 2500) {
                viewModel.updateNews(newsItems[newsIndex])
                newsIndex = (newsIndex + 1) % newsItems.size
            }
        }
    }

    LaunchedEffect(buildingCatalog) {
        if (placedBuildings.isEmpty() && buildingCatalog.isNotEmpty()) {
            buildingCatalog.take(3).forEachIndexed { index, definition ->
                placedBuildings += PlacedBuilding(
                    id = index.toLong() + 1L,
                    definition = definition,
                    position = Position((index - 1) * 2.5f, 0f, -3.5f),
                    scale = 1.2f
                )
            }
        }
    }

    val onBuild: (BuildingDefinition) -> Unit = { building ->
        if (gameState.money >= building.cost && building.assetPath.isNotBlank()) {
            val index = placedBuildings.size
            val offset = (index % 5) - 2
            placedBuildings += PlacedBuilding(
                id = System.currentTimeMillis(),
                definition = building,
                position = Position((offset * 2.25f), 0f, -8f - (index / 5) * 2.25f),
                scale = 1.1f
            )
            viewModel.updateMoney(-building.cost)
            viewModel.updatePopulation(building.populationImpact)
            viewModel.updateHappiness(building.happinessImpact)
            viewModel.updateSustainability(building.sustainabilityImpact)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF07111F),
                        Color(0xFF0F2237),
                        Color(0xFF15324A)
                    )
                )
            )
    ) {
        SceneView(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            modelLoader = modelLoader,
            cameraManipulator = cameraManipulator
        ) {
            // Add a ground plane
            ModelNode(
                modelInstance = modelLoader.createModelInstance(
                    assetFileLocation = "models/Roads and Bridges/road-square.glb"
                ),
                scaleToUnits = 50f,
                position = Position(0f, -0.1f, -10f),
                centerOrigin = Position(0f, 0f, 0f)
            )

            placedBuildings.forEach { building ->
                key(building.id) {
                    if (building.definition.assetPath.isNotBlank()) {
                        ModelNode(
                            modelInstance = remember(building.id, building.definition.assetPath) {
                                modelLoader.createModelInstance(assetFileLocation = building.definition.assetPath)
                            },
                            scaleToUnits = building.scale,
                            centerOrigin = Position(0f, 0f, 0f),
                            position = building.position
                        )
                    }
                }
            }
        }

        GameHud(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp, start = 12.dp, end = 12.dp),
            gameState = gameState,
            activeGoal = activeGoal
        )

        NewsTicker(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp),
            news = currentNews
        )

        GameBuildBar(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(12.dp),
            buildingCatalog = buildingCatalog,
            selectedBuilding = selectedBuilding,
            onSelectedBuilding = { selectedBuilding = it },
            onBuild = onBuild
        )

        selectedBuilding?.let { building ->
            Card(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(building.title, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Asset: ${building.assetPath}", fontSize = 12.sp)
                    Text("Cost: ₹${building.cost}", fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { onBuild(building) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Build")
                    }
                }
            }
        }
    }
}

@Composable
private fun GameHud(
    modifier: Modifier = Modifier,
    gameState: GameState,
    activeGoal: String
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.84f),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = gameState.cityName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "Smart Score: ${gameState.sustainabilityScore}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Text(
                    text = "Goal: $activeGoal",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatChip("₹${gameState.money}", "Money")
                StatChip("${gameState.population}", "Population")
                StatChip("${gameState.happiness}%", "Happiness")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatChip("${gameState.water}%", "Water")
                StatChip("${gameState.power}%", "Power")
                StatChip("${gameState.pollution}%", "Pollution")
            }
        }
    }
}

@Composable
private fun StatChip(value: String, label: String) {
    AssistChip(onClick = {}, label = { Text("$label: $value") })
}

@Composable
private fun NewsTicker(
    modifier: Modifier = Modifier,
    news: String
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        color = Color.Black.copy(alpha = 0.7f),
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "LIVE NEWS:",
                color = Color.Red,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(
                text = news,
                color = Color.White,
                fontSize = 11.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun GameBuildBar(
    modifier: Modifier = Modifier,
    buildingCatalog: List<BuildingDefinition>,
    selectedBuilding: BuildingDefinition?,
    onSelectedBuilding: (BuildingDefinition) -> Unit,
    onBuild: (BuildingDefinition) -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 6.dp
    ) {
        LazyRow(
            modifier = Modifier.padding(12.dp),
            contentPadding = PaddingValues(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(buildingCatalog) { building ->
                val selected = selectedBuilding?.id == building.id
                Button(
                    onClick = {
                        onSelectedBuilding(building)
                        onBuild(building)
                    }
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(building.title, fontWeight = FontWeight.SemiBold)
                        Text("₹${building.cost}", fontSize = 11.sp)
                        if (selected) {
                            Text("Selected", fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}
