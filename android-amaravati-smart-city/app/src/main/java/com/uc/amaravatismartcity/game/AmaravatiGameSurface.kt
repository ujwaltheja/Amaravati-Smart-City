package com.uc.amaravatismartcity.game

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    viewModel: GameViewModel = viewModel(),
    onBack: () -> Unit = {}
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
            
            if (currentState.happiness > 75) {
                viewModel.updatePopulation(2)
            }

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
                        Color(0xFF020811),
                        Color(0xFF05111E),
                        Color(0xFF081A2D)
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
            placedBuildings.forEach { building ->
                key(building.id) {
                    if (building.definition.assetPath.isNotBlank()) {
                        val modelInstance = remember(building.id, building.definition.assetPath) {
                            try {
                                modelLoader.createModelInstance(assetFileLocation = building.definition.assetPath)
                            } catch (e: Exception) {
                                null
                            }
                        }
                        if (modelInstance != null) {
                            ModelNode(
                                modelInstance = modelInstance,
                                scaleToUnits = building.scale,
                                centerOrigin = Position(0f, 0f, 0f),
                                position = building.position
                            )
                        }
                    }
                }
            }
        }

        // Top UI Layer
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
        ) {
            GameTopBar(
                gameState = gameState,
                onBack = onBack
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            NewsTicker(
                news = currentNews
            )
        }

        // Goal Tracker (Left Side)
        GoalTracker(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 16.dp),
            activeGoal = activeGoal
        )

        // Bottom UI Layer
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            selectedBuilding?.let { building ->
                BuildingInspector(
                    building = building,
                    canAfford = gameState.money >= building.cost,
                    onBuild = { onBuild(building) }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            GameBuildBar(
                buildingCatalog = buildingCatalog,
                selectedBuilding = selectedBuilding,
                onSelectedBuilding = { selectedBuilding = it },
                onBuild = onBuild
            )
        }
    }
}

@Composable
private fun GameTopBar(
    gameState: GameState,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.4f), MaterialTheme.shapes.small)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column {
                Text(
                    text = gameState.cityName.uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    letterSpacing = 1.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = gameState.rank,
                        color = Color(0xFF58DBB8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "LVL ${gameState.sustainabilityScore}",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    // Level / Smart Score bar
                    Box(
                        modifier = Modifier
                            .width(60.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.1f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(gameState.sustainabilityScore / 100f)
                                .fillMaxHeight()
                                .background(Color(0xFF58DBB8))
                        )
                    }
                }
            }
        }

        Surface(
            color = Color.Black.copy(alpha = 0.6f),
            shape = MaterialTheme.shapes.medium,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF58DBB8).copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GamingStatItem(Icons.Default.MonetizationOn, "₹${gameState.money}", Color(0xFFFFD700))
                GamingStatItem(Icons.Default.People, "${gameState.population}", Color.White)
                GamingStatItem(
                    icon = Icons.Default.SentimentSatisfied,
                    value = "${gameState.happiness}%",
                    color = if (gameState.happiness > 50) Color(0xFF58DBB8) else Color(0xFFFF4B4B)
                )
            }
        }
    }
}

@Composable
private fun GamingStatItem(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun NewsTicker(
    news: String
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp),
        color = Color(0xFFFF4B4B).copy(alpha = 0.15f),
        shape = RoundedCornerShape(4.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF4B4B).copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(vertical = 4.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SYSTEM BROADCAST",
                color = Color(0xFFFF4B4B),
                fontWeight = FontWeight.Black,
                fontSize = 9.sp,
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(
                text = news,
                color = Color.White,
                fontSize = 11.sp,
                maxLines = 1,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun GoalTracker(
    modifier: Modifier = Modifier,
    activeGoal: String
) {
    Column(modifier = modifier) {
        Text(
            text = "MISSION",
            color = Color(0xFF58DBB8).copy(alpha = 0.7f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Surface(
            color = Color.Black.copy(alpha = 0.5f),
            shape = RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 12.dp, bottomEnd = 12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF58DBB8).copy(alpha = 0.4f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = Color(0xFF58DBB8), modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = activeGoal,
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.widthIn(max = 160.dp)
                )
            }
        }
    }
}

@Composable
private fun BuildingInspector(
    building: BuildingDefinition,
    canAfford: Boolean,
    onBuild: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(280.dp)
            .padding(horizontal = 16.dp),
        color = Color(0xFF0A1929).copy(alpha = 0.9f),
        shape = MaterialTheme.shapes.large,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = building.title.uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp
                )
                Text(
                    text = building.category.displayName,
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 9.sp
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallStat(Icons.Default.People, building.populationImpact.toString(), Color.White)
                SmallStat(Icons.Default.SentimentSatisfied, "+${building.happinessImpact}", Color(0xFF58DBB8))
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = onBuild,
                modifier = Modifier.fillMaxWidth(),
                enabled = canAfford,
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (canAfford) Color(0xFF58DBB8) else Color.Gray.copy(alpha = 0.5f),
                    contentColor = Color(0xFF020811)
                )
            ) {
                Text(
                    text = if (canAfford) "DEPLOY (₹${building.cost})" else "INSUFFICIENT FUNDS",
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun SmallStat(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color.copy(alpha = 0.7f), modifier = Modifier.size(12.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(value, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
    val categories = remember(buildingCatalog) { buildingCatalog.map { it.category }.distinct() }
    var selectedCategory by remember { mutableStateOf(categories.firstOrNull()) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        color = Color(0xFF020811).copy(alpha = 0.85f),
        shape = MaterialTheme.shapes.extraLarge,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(bottom = 8.dp)) {
            // Category Selector
            LazyRow(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(categories) { category ->
                    val isSelected = selectedCategory == category
                    Box(
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.medium)
                            .background(if (isSelected) Color(0xFF58DBB8).copy(alpha = 0.2f) else Color.Transparent)
                            .clickable { selectedCategory = category }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = category.displayName.uppercase(),
                            color = if (isSelected) Color(0xFF58DBB8) else Color.White.copy(alpha = 0.6f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }

            // Building Selector
            LazyRow(
                modifier = Modifier.padding(horizontal = 12.dp),
                contentPadding = PaddingValues(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(buildingCatalog.filter { it.category == selectedCategory }) { building ->
                    val selected = selectedBuilding?.id == building.id
                    val color = Color(0xFF58DBB8)
                    
                    Surface(
                        onClick = { onSelectedBuilding(building) },
                        modifier = Modifier
                            .width(100.dp)
                            .height(60.dp),
                        shape = MaterialTheme.shapes.medium,
                        color = if (selected) color.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f),
                        border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, color) else null
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = building.title,
                                color = if (selected) color else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                            Text(
                                text = "₹${building.cost}",
                                color = if (selected) color.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.5f),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}
