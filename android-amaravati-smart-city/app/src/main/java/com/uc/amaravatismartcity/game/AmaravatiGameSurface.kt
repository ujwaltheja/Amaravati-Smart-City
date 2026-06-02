package com.uc.amaravatismartcity.game

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uc.amaravatismartcity.models.BuildingCatalog
import com.uc.amaravatismartcity.models.BuildingCategory
import com.uc.amaravatismartcity.models.BuildingDefinition
import com.uc.amaravatismartcity.models.GlbAssetIndex
import io.github.sceneview.SceneView
import io.github.sceneview.math.Position
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelLoader
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

// BorderStroke is in foundation
import androidx.compose.foundation.BorderStroke

private data class PlacedItem(
    val id: Long,
    val definition: BuildingDefinition,
    val position: Position,
    val rotationY: Float = 0f,
    val scale: Float = 1f
)

private data class AnimatedVehicle(
    val id: Long,
    val assetPath: String,
    val lane: Int,          // 0,1,2 for different parallel paths
    val speed: Float,       // units per second
    val phase: Float,       // 0..1 along path
    val scale: Float = 0.85f,
    val flip: Boolean = false
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
    // Strong overhead starting view for realistic city building
    val cameraManipulator = rememberCameraManipulator(
        orbitHomePosition = io.github.sceneview.math.Position(-1.5f, 19f, -22f)
    )

    val gameState by viewModel.gameState.collectAsStateWithLifecycle()
    val currentNews by viewModel.currentNews.collectAsStateWithLifecycle()
    val activeGoal by viewModel.activeGoal.collectAsStateWithLifecycle()
    val isPaused by viewModel.isPaused.collectAsStateWithLifecycle()
    val simSpeed by viewModel.simSpeed.collectAsStateWithLifecycle()

    // Realistic city items (buildings + props + roads)
    val placedItems = remember { mutableStateListOf<PlacedItem>() }

    // Animated live traffic - realistic moving cars on roads
    val vehicles = remember { mutableStateListOf<AnimatedVehicle>() }

    var selectedBuilding by remember(assetPaths) {
        mutableStateOf(buildingCatalog.firstOrNull())
    }
    var isBulldozeMode by remember { mutableStateOf(false) }
    var lastPlacementTime by remember { mutableStateOf(0L) }

    // Preload good asset references for traffic (cars) and base tiles
    val carAssets = remember(assetPaths) {
        val preferred = listOf("sedan.glb", "suv.glb", "taxi.glb", "hatchback-sports.glb", "delivery.glb", "van.glb", "police.glb", "truck.glb", "race.glb", "ambulance.glb")
        preferred.mapNotNull { name ->
            assetPaths.firstOrNull { it.endsWith(name, ignoreCase = true) }
        }.ifEmpty { assetPaths.filter { it.contains("Cars/", ignoreCase = true) && !it.contains("debris", true) && !it.contains("wheel", true) }.take(6) }
    }

    val tileAssets = remember(assetPaths) {
        listOf("tile-low.glb", "tile-high.glb", "road-straight.glb", "road-straight-half.glb")
            .mapNotNull { name -> assetPaths.firstOrNull { it.endsWith(name, ignoreCase = true) } }
    }

    val characterAssets = remember(assetPaths) {
        assetPaths.filter { it.contains("Mini Characters/character-", true) }.take(5)
    }

    // Initialize rich realistic city base + roads + traffic
    LaunchedEffect(assetPaths, buildingCatalog) {
        if (placedItems.isEmpty() && assetPaths.isNotEmpty() && buildingCatalog.isNotEmpty()) {
            // === REALISTIC CITY FOUNDATION: Krishna riverfront style grid ===
            val tLow = tileAssets.firstOrNull { it.contains("tile-low") } ?: tileAssets.firstOrNull() ?: ""
            val tHigh = tileAssets.firstOrNull { it.contains("tile-high") } ?: tLow
            val roadStraight = tileAssets.firstOrNull { it.contains("road-straight") } ?: tLow

            // Large paved plaza / city blocks using tiles (ground)
            var id = 100L
            for (x in -3..3) {
                for (z in -2..4) {
                    val isRoadRow = z == 1 || z == -1
                    val asset = if (isRoadRow && roadStraight.isNotBlank()) roadStraight else if ((x + z) % 2 == 0) tLow else tHigh
                    if (asset.isNotBlank()) {
                        placedItems += PlacedItem(
                            id = id++,
                            definition = BuildingDefinition("tile-$id", BuildingCategory.Infrastructure, "Pavement", asset, 0),
                            position = Position(x * 3.8f, -0.02f, z * 3.6f),
                            scale = 1.05f
                        )
                    }
                }
            }

            // Add some initial roads as explicit infrastructure (visual + gameplay)
            if (roadStraight.isNotBlank()) {
                listOf(-2, 0, 2).forEach { x ->
                    placedItems += PlacedItem(
                        id = id++,
                        definition = BuildingDefinition("road-$id", BuildingCategory.Infrastructure, "Main Road", roadStraight, 900),
                        position = Position(x * 3.8f + 0.2f, 0.01f, 0.8f),
                        rotationY = 90f,
                        scale = 0.98f
                    )
                }
            }

            // Seed realistic starter buildings using good matches from catalog
            val starters = buildingCatalog.filter { it.assetPath.isNotBlank() }.take(7)
            val startOffsets = listOf(
                Position(-6.5f, 0f, -5.2f), Position(-2.2f, 0f, -6.1f), Position(3.8f, 0f, -5.8f),
                Position(-7.1f, 0f, 2.4f), Position(1.6f, 0f, 3.1f), Position(6.4f, 0f, 1.9f),
                Position(-3.9f, 0f, 7.8f)
            )
            starters.forEachIndexed { i, def ->
                val pos = startOffsets.getOrNull(i) ?: Position((i-3)*2.8f, 0f, -7f - i*0.6f)
                placedItems += PlacedItem(
                    id = id++,
                    definition = def,
                    position = pos,
                    scale = if (def.category == BuildingCategory.GreenSpace) 0.95f else 1.15f,
                    rotationY = if (i % 2 == 0) 12f else -8f
                )
            }

            // Decorative characters (pedestrians) for life
            characterAssets.take(4).forEachIndexed { i, path ->
                placedItems += PlacedItem(
                    id = id++,
                    definition = BuildingDefinition("citizen-$i", BuildingCategory.GreenSpace, "Citizen", path, 0),
                    position = Position(-4.5f + i * 2.8f, 0.05f, -1.6f + (i % 2) * 0.8f),
                    scale = 0.7f,
                    rotationY = (i * 37f) % 360f
                )
            }

            // Initial traffic vehicles - realistic moving
            if (vehicles.isEmpty() && carAssets.isNotEmpty()) {
                val speeds = listOf(4.2f, 5.1f, 3.7f, 6.3f, 4.8f, 3.9f, 5.6f)
                carAssets.take(7).forEachIndexed { i, carPath ->
                    vehicles += AnimatedVehicle(
                        id = 2000L + i,
                        assetPath = carPath,
                        lane = i % 3,
                        speed = speeds[i % speeds.size],
                        phase = (i * 0.19f) % 1f,
                        scale = if (carPath.contains("truck", true) || carPath.contains("ambulance", true)) 0.78f else 0.9f,
                        flip = i % 2 == 0
                    )
                }
            }
        }
    }

    // Drive the simulation + day/night + economy + random events
    LaunchedEffect(Unit) {
        var last = System.currentTimeMillis()
        while (true) {
            delay(110L)
            val now = System.currentTimeMillis()
            val dt = ((now - last) / 1000f).coerceIn(0.03f, 0.28f)
            last = now

            viewModel.advanceSimulation(dt, placedItems.size)

            // Update vehicle animation (realistic traffic)
            if (!isPaused && vehicles.isNotEmpty()) {
                val speedMul = simSpeed
                vehicles.replaceAll { v ->
                    var newPhase = v.phase + (v.speed * 0.011f * dt * speedMul)
                    if (newPhase > 1.05f) newPhase = -0.08f // loop around
                    v.copy(phase = newPhase)
                }
            }
        }
    }

    // Occasional city pulse / news when not paused
    LaunchedEffect(isPaused) {
        if (!isPaused) {
            delay(14000)
            val s = viewModel.gameState.value
            if (s.happiness > 88) viewModel.updateNews("Citizens celebrating record quality of life in Amaravati.")
            else if (s.trafficDensity > 78) viewModel.updateNews("Traffic advisory: Consider adding more road infrastructure.")
        }
    }

    val onBuild: (BuildingDefinition) -> Unit = { building ->
        if (gameState.money >= building.cost && building.assetPath.isNotBlank()) {
            val now = System.currentTimeMillis()
            if (now - lastPlacementTime >= 140) {
                lastPlacementTime = now

                // Smart placement around existing city - spiral/offset pattern for organic growth
                val count = placedItems.count { it.definition.cost > 0 }
                val ring = (count / 5)
                val angle = (count % 7) * 51.4f
                val rad = 7.5f + ring * 1.8f
                val px = (sin(Math.toRadians(angle.toDouble())) * rad).toFloat()
                val pz = (cos(Math.toRadians(angle.toDouble())) * (rad * 0.72f) - 2f).toFloat() + (ring % 2) * 1.4f

                placedItems += PlacedItem(
                    id = now,
                    definition = building,
                    position = Position(px, 0f, pz),
                    rotationY = ((count * 23) % 27 - 13).toFloat(),
                    scale = when (building.category) {
                        BuildingCategory.GreenSpace, BuildingCategory.Riverfront -> 0.92f
                        BuildingCategory.Infrastructure -> 0.96f
                        else -> 1.08f + (count % 3) * 0.03f
                    }
                )
                viewModel.updateMoney(-building.cost)
                viewModel.updatePopulation(building.populationImpact)
                viewModel.updateHappiness(building.happinessImpact)
                viewModel.updateSustainability(building.sustainabilityImpact)
                viewModel.updateTotalBuildings(placedItems.count { it.definition.cost > 50 })

                // Roads reduce traffic pressure
                if (building.category == BuildingCategory.Infrastructure) {
                    viewModel.updateTraffic(-11)
                }
                if (building.category == BuildingCategory.Industrial) {
                    viewModel.updatePollution(4)
                }
                if (building.title.contains("Metro", true) || building.title.contains("Transport", true)) {
                    viewModel.updateHappiness(4)
                    viewModel.updateTraffic(-6)
                }
            }
        }
    }

    val onDemolishLast: () -> Unit = {
        val removable = placedItems.lastOrNull { it.definition.cost > 10 }
        if (removable != null) {
            placedItems.remove(removable)
            // Refund partial
            viewModel.updateMoney((removable.definition.cost * 0.45).toLong())
            viewModel.updateTotalBuildings(placedItems.count { it.definition.cost > 50 })
        }
    }

    // "Place in front of camera" for more player agency (realistic feel)
    val onBuildInView: () -> Unit = {
        selectedBuilding?.let { b ->
            if (gameState.money >= b.cost && b.assetPath.isNotBlank()) {
                // Place in a nice forward arc from "city center"
                val idx = placedItems.size
                val spread = (idx % 5 - 2) * 1.6f
                val forward = -9.5f - (idx / 4) * 1.3f
                placedItems += PlacedItem(
                    id = System.currentTimeMillis(),
                    definition = b,
                    position = Position(spread * 0.9f, 0.02f, forward),
                    scale = 1.05f,
                    rotationY = spread * 1.6f
                )
                viewModel.updateMoney(-b.cost)
                viewModel.updatePopulation(b.populationImpact)
                viewModel.updateHappiness(b.happinessImpact)
                viewModel.updateSustainability(b.sustainabilityImpact)
                viewModel.updateTotalBuildings(placedItems.count { it.definition.cost > 50 })
            }
        }
    }

    // Day/night modulated background for realism
    val day = gameState.dayTime
    val isNight = day < 6.2f || day > 19.4f
    val dawnDusk = (day in 5.5f..7.2f) || (day in 18.0f..20.0f)

    val bgTop = when {
        isNight -> Color(0xFF01060F)
        dawnDusk -> Color(0xFF1F2A3D)
        else -> Color(0xFF020D1A)
    }
    val bgMid = when {
        isNight -> Color(0xFF02101F)
        dawnDusk -> Color(0xFF2A3B55)
        else -> Color(0xFF051524)
    }
    val bgBot = when {
        isNight -> Color(0xFF031526)
        else -> Color(0xFF081E36)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(colors = listOf(bgTop, bgMid, bgBot))
            )
    ) {
        // === 3D REALISTIC CITY SCENE ===
        SceneView(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = {
                            // Long press anywhere on 3D -> bulldoze last building (quick interaction)
                            if (placedItems.isNotEmpty()) onDemolishLast()
                        }
                    )
                },
            engine = engine,
            modelLoader = modelLoader,
            cameraManipulator = cameraManipulator
        ) {
            // Ground / city base tiles + placed buildings / props
            placedItems.forEach { item ->
                key(item.id) {
                    if (item.definition.assetPath.isNotBlank()) {
                        val modelInstance = remember(item.id, item.definition.assetPath) {
                            try {
                                modelLoader.createModelInstance(assetFileLocation = item.definition.assetPath)
                            } catch (_: Exception) { null }
                        }
                        if (modelInstance != null) {
                            ModelNode(
                                modelInstance = modelInstance,
                                scaleToUnits = item.scale,
                                centerOrigin = Position(0f, 0f, 0f),
                                position = item.position
                            )
                        }
                    }
                }
            }

            // LIVE ANIMATED TRAFFIC - the heart of realistic feel
            vehicles.forEach { v ->
                key(v.id) {
                    if (v.assetPath.isNotBlank()) {
                        val mi = remember(v.id, v.assetPath) {
                            try { modelLoader.createModelInstance(assetFileLocation = v.assetPath) } catch (_: Exception) { null }
                        }
                        if (mi != null) {
                            // Compute world position along lanes (two way avenues + cross)
                            val laneX = when (v.lane) {
                                0 -> -7.6f
                                1 -> 0.1f
                                else -> 7.3f
                            }
                            val progress = v.phase
                            // Main horizontal flow + slight curve simulation
                            val baseZ = -11f + progress * 27f
                            val sway = sin(progress * 6.28f * 1.6) * 0.4f
                            val x = laneX + sway.toFloat() * (if (v.lane == 1) 0.6f else 1f)
                            val z = baseZ + (if (v.lane == 2) (sin(progress * 3.4) * 1.8f).toFloat() else 0f)

                            val finalPos = Position(x, 0.12f, z)
                            val rotY = if (v.flip) 180f else 0f

                            ModelNode(
                                modelInstance = mi,
                                scaleToUnits = v.scale,
                                centerOrigin = Position(0f, 0f, 0f),
                                position = finalPos,
                                // rotation handled via simple property if supported; for visual it's acceptable
                            )
                        }
                    }
                }
            }
        }

        // === PREMIUM GAME HUD ===
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 10.dp)
        ) {
            GameTopBarRealistic(
                gameState = gameState,
                isNight = isNight,
                isPaused = isPaused,
                simSpeed = simSpeed,
                onBack = onBack,
                onTogglePause = { viewModel.setPaused(!isPaused) },
                onSpeedChange = { viewModel.setSimSpeed(it) }
            )

            Spacer(Modifier.height(6.dp))

            NewsTickerRealistic(news = currentNews, isNight = isNight)
        }

        // Mission / Goal on left
        GoalTrackerRealistic(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 10.dp, top = 110.dp),
            activeGoal = activeGoal,
            population = gameState.population,
            happiness = gameState.happiness
        )

        // Right side quick actions
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 10.dp, top = 140.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                onClick = onBuildInView,
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF58DBB8).copy(alpha = 0.9f),
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.AddLocation, null, tint = Color(0xFF02101F), modifier = Modifier.size(24.dp))
                }
            }
            Surface(
                onClick = { isBulldozeMode = !isBulldozeMode; if (isBulldozeMode && placedItems.isNotEmpty()) onDemolishLast() },
                shape = RoundedCornerShape(10.dp),
                color = if (isBulldozeMode) Color(0xFFFF4B4B).copy(0.9f) else Color.Black.copy(0.55f),
                border = if (isBulldozeMode) BorderStroke(1.5.dp, Color.White.copy(0.5f)) else null,
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Delete, null, tint = Color.White, modifier = Modifier.size(21.dp))
                }
            }
            Surface(
                onClick = { viewModel.triggerRandomEvent() },
                shape = RoundedCornerShape(10.dp),
                color = Color.Black.copy(alpha = 0.45f),
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.AutoAwesome, null, tint = Color(0xFF58DBB8).copy(0.9f), modifier = Modifier.size(19.dp))
                }
            }
        }

        // Bottom build + controls
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 8.dp, start = 8.dp, end = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Inspector for selected
            selectedBuilding?.let { building ->
                BuildingInspectorRealistic(
                    building = building,
                    canAfford = gameState.money >= building.cost,
                    onBuild = { onBuild(building) },
                    onBuildInView = onBuildInView
                )
                Spacer(Modifier.height(8.dp))
            }

            GameBuildBarRealistic(
                buildingCatalog = buildingCatalog,
                selectedBuilding = selectedBuilding,
                onSelectedBuilding = { selectedBuilding = it },
                onBuild = onBuild,
                onQuickRoad = {
                    val road = buildingCatalog.firstOrNull { it.category == BuildingCategory.Infrastructure }
                        ?: buildingCatalog.firstOrNull()
                    road?.let { onBuild(it) }
                }
            )
        }

        // Subtle time indicator (realism)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 72.dp, end = 14.dp)
        ) {
            TimeOfDayBadge(dayTime = gameState.dayTime, isNight = isNight)
        }
    }
}

// ==================== REALISTIC PREMIUM UI COMPONENTS ====================

@Composable
private fun GameTopBarRealistic(
    gameState: GameState,
    isNight: Boolean,
    isPaused: Boolean,
    simSpeed: Float,
    onBack: () -> Unit,
    onTogglePause: () -> Unit,
    onSpeedChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: Back + City identity
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .background(Color.Black.copy(0.55f), RoundedCornerShape(8.dp))
                    .size(36.dp)
            ) {
                Icon(Icons.Default.ArrowBack, "Back", tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(9.dp))
            Column {
                Text(
                    gameState.cityName.uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 17.sp,
                    letterSpacing = 1.5.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        gameState.rank,
                        color = Color(0xFF58DBB8),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("•", color = Color.White.copy(0.3f), fontSize = 9.sp)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "SMART ${gameState.sustainabilityScore}",
                        color = Color.White.copy(0.65f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Center/Right: Rich stats + time controls
        Surface(
            color = Color.Black.copy(0.62f),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color(0xFF58DBB8).copy(0.22f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatPill(icon = Icons.Default.MonetizationOn, value = "₹${formatMoney(gameState.money)}", tint = Color(0xFFFFD54F))
                StatPill(icon = Icons.Default.People, value = "${gameState.population}", tint = Color.White)
                StatPill(
                    icon = Icons.Default.SentimentSatisfied,
                    value = "${gameState.happiness}",
                    tint = if (gameState.happiness > 72) Color(0xFF58DBB8) else if (gameState.happiness > 48) Color(0xFFFFB74D) else Color(0xFFFF5252)
                )
                StatPill(
                    icon = if (isNight) Icons.Default.Nightlight else Icons.Default.WbSunny,
                    value = String.format("%.1f", gameState.dayTime),
                    tint = if (isNight) Color(0xFF90CAF9) else Color(0xFFFFE082)
                )

                // Simulation controls
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(onClick = onTogglePause, modifier = Modifier.size(28.dp)) {
                        Icon(if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause, null, tint = Color.White, modifier = Modifier.size(17.dp))
                    }
                    listOf(0.7f, 1f, 2.2f).forEach { s ->
                        val sel = simSpeed > s - 0.3f && simSpeed < s + 0.6f
                        Surface(
                            onClick = { onSpeedChange(s) },
                            shape = RoundedCornerShape(5.dp),
                            color = if (sel) Color(0xFF58DBB8).copy(0.25f) else Color.Transparent,
                            modifier = Modifier.padding(horizontal = 1.dp)
                        ) {
                            Text(
                                text = "${s}x",
                                color = if (sel) Color(0xFF58DBB8) else Color.White.copy(0.6f),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatMoney(m: Long): String = when {
    m >= 1_000_000 -> "${(m / 100000) / 10.0}M"
    m >= 10_000 -> "${m / 1000}k"
    else -> m.toString()
}

@Composable
private fun StatPill(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(3.dp))
        Text(value, color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun NewsTickerRealistic(news: String, isNight: Boolean) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 38.dp),
        color = (if (isNight) Color(0xFF1A237E) else Color(0xFFC62828)).copy(alpha = 0.18f),
        shape = RoundedCornerShape(5.dp),
        border = BorderStroke(0.7.dp, (if (isNight) Color(0xFF7986CB) else Color(0xFFFF5252)).copy(0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "LIVE",
                color = if (isNight) Color(0xFF9FA8DA) else Color(0xFFFF8A80),
                fontWeight = FontWeight.Black,
                fontSize = 8.sp,
                modifier = Modifier.padding(end = 6.dp)
            )
            Text(
                news,
                color = Color.White,
                fontSize = 10.5.sp,
                maxLines = 1,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun GoalTrackerRealistic(
    modifier: Modifier = Modifier,
    activeGoal: String,
    population: Int,
    happiness: Int
) {
    Column(modifier = modifier) {
        Text(
            "DIRECTIVE",
            color = Color(0xFF58DBB8).copy(0.75f),
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.8.sp
        )
        Spacer(Modifier.height(3.dp))
        Surface(
            color = Color(0xFF000000).copy(0.58f),
            shape = RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 13.dp, bottomEnd = 13.dp),
            border = BorderStroke(0.8.dp, Color(0xFF58DBB8).copy(0.38f))
        ) {
            Column(Modifier.padding(horizontal = 11.dp, vertical = 7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.EmojiEvents, null, tint = Color(0xFF58DBB8), modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(activeGoal, color = Color.White, fontSize = 10.5.sp, modifier = Modifier.widthIn(max = 155.dp))
                }
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { (population.coerceIn(50, 1500) - 50) / 1450f },
                    modifier = Modifier.fillMaxWidth(0.92f).height(2.5.dp).clip(RoundedCornerShape(2.dp)),
                    color = Color(0xFF58DBB8),
                    trackColor = Color.White.copy(0.12f)
                )
            }
        }
    }
}

@Composable
private fun BuildingInspectorRealistic(
    building: BuildingDefinition,
    canAfford: Boolean,
    onBuild: () -> Unit,
    onBuildInView: () -> Unit
) {
    Surface(
        modifier = Modifier.widthIn(max = 300.dp).padding(horizontal = 8.dp),
        color = Color(0xFF071422).copy(alpha = 0.95f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(0.8.dp, Color.White.copy(0.1f))
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(building.title.uppercase(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 13.sp)
                Text(building.category.displayName, color = Color.White.copy(0.45f), fontSize = 8.5.sp)
            }
            Spacer(Modifier.height(7.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ImpactBadge(Icons.Default.People, building.populationImpact, if (building.populationImpact >= 0) Color(0xFF81D4FA) else Color(0xFFFFAB91))
                ImpactBadge(Icons.Default.SentimentSatisfied, building.happinessImpact, if (building.happinessImpact >= 0) Color(0xFF58DBB8) else Color(0xFFFF8A65))
                ImpactBadge(Icons.Default.Park, building.sustainabilityImpact, if (building.sustainabilityImpact >= 0) Color(0xFF81C784) else Color(0xFFE57373))
            }

            Spacer(Modifier.height(9.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onBuild,
                    enabled = canAfford,
                    modifier = Modifier.weight(1f).height(38.dp),
                    shape = RoundedCornerShape(9.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (canAfford) Color(0xFF58DBB8) else Color(0xFF37474F),
                        contentColor = if (canAfford) Color(0xFF02101F) else Color.White.copy(0.7f)
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) {
                    Text(if (canAfford) "PLACE ₹${building.cost}" else "₹${building.cost}", fontWeight = FontWeight.Black, fontSize = 11.sp)
                }
                OutlinedButton(
                    onClick = onBuildInView,
                    enabled = canAfford,
                    modifier = Modifier.height(38.dp),
                    shape = RoundedCornerShape(9.dp),
                    border = BorderStroke(1.dp, Color(0xFF58DBB8).copy(0.6f)),
                    contentPadding = PaddingValues(horizontal = 9.dp)
                ) {
                    Icon(Icons.Default.Place, null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("IN VIEW", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ImpactBadge(icon: androidx.compose.ui.graphics.vector.ImageVector, value: Int, color: Color) {
    if (value == 0) return
    Surface(color = color.copy(0.13f), shape = RoundedCornerShape(7.dp)) {
        Row(Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = color, modifier = Modifier.size(11.dp))
            Spacer(Modifier.width(2.dp))
            Text(if (value > 0) "+$value" else "$value", color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun GameBuildBarRealistic(
    buildingCatalog: List<BuildingDefinition>,
    selectedBuilding: BuildingDefinition?,
    onSelectedBuilding: (BuildingDefinition) -> Unit,
    onBuild: (BuildingDefinition) -> Unit,
    onQuickRoad: () -> Unit
) {
    val categories = remember(buildingCatalog) { buildingCatalog.map { it.category }.distinct() }
    var selectedCategory by remember { mutableStateOf(categories.firstOrNull { it == BuildingCategory.Infrastructure } ?: categories.firstOrNull()) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF010A14).copy(0.93f),
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
        border = BorderStroke(0.6.dp, Color.White.copy(0.08f))
    ) {
        Column(Modifier.padding(top = 5.dp, bottom = 4.dp)) {
            // Categories + quick road
            Row(
                modifier = Modifier.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(categories) { cat ->
                        val sel = selectedCategory == cat
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (sel) Color(0xFF58DBB8).copy(0.22f) else Color.Transparent)
                                .clickable { selectedCategory = cat }
                                .padding(horizontal = 9.dp, vertical = 4.dp)
                        ) {
                            Text(
                                cat.displayName.uppercase().take(11),
                                color = if (sel) Color(0xFF58DBB8) else Color.White.copy(0.55f),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.6.sp
                            )
                        }
                    }
                }
                // Quick infrastructure button
                Surface(onClick = onQuickRoad, color = Color(0xFF58DBB8).copy(0.15f), shape = RoundedCornerShape(7.dp)) {
                    Text("ROAD", color = Color(0xFF58DBB8), fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }

            Spacer(Modifier.height(3.dp))

            // Items row - much more game-like
            LazyRow(
                modifier = Modifier.padding(horizontal = 4.dp),
                contentPadding = PaddingValues(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(buildingCatalog.filter { it.category == selectedCategory }) { b ->
                    val isSel = selectedBuilding?.id == b.id
                    val accent = Color(0xFF58DBB8)

                    Surface(
                        onClick = { onSelectedBuilding(b) },
                        shape = RoundedCornerShape(11.dp),
                        color = if (isSel) accent.copy(0.16f) else Color.White.copy(0.035f),
                        border = if (isSel) BorderStroke(1.5.dp, accent) else null,
                        modifier = Modifier.width(86.dp).height(52.dp)
                    ) {
                        Column(
                            Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                b.title,
                                color = if (isSel) accent else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                maxLines = 1,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(1.dp))
                            Text(
                                "₹${b.cost}",
                                color = if (isSel) accent.copy(0.85f) else Color.White.copy(0.5f),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Medium
                            )
                            // Mini impact line
                            if (b.populationImpact != 0 || b.happinessImpact != 0) {
                                Text(
                                    listOfNotNull(
                                        if (b.populationImpact != 0) "👥${if (b.populationImpact>0) "+" else ""}${b.populationImpact}" else null,
                                        if (b.happinessImpact != 0) "😊${if (b.happinessImpact>0) "+" else ""}${b.happinessImpact}" else null
                                    ).joinToString(" "),
                                    color = Color.White.copy(0.35f),
                                    fontSize = 7.5.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeOfDayBadge(dayTime: Float, isNight: Boolean) {
    val hour = dayTime.toInt()
    val label = when {
        isNight -> "NIGHT"
        dayTime in 5.5f..8f -> "DAWN"
        dayTime in 17f..19.5f -> "DUSK"
        dayTime in 11f..15f -> "NOON"
        else -> "DAY"
    }
    Surface(
        color = (if (isNight) Color(0xFF0D47A1) else Color(0xFFF9A825)).copy(0.2f),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(0.5.dp, Color.White.copy(0.2f))
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("$hour:00", color = Color.White.copy(0.9f), fontSize = 9.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(4.dp))
            Text(label, color = if (isNight) Color(0xFF90CAF9) else Color(0xFFFFF59D), fontSize = 8.sp, fontWeight = FontWeight.Black)
        }
    }
}
