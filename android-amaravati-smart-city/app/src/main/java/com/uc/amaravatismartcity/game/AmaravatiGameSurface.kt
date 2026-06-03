package com.uc.amaravatismartcity.game

import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.filament.LightManager
import com.uc.amaravatismartcity.models.*
import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.Float4
import io.github.sceneview.SceneView
import io.github.sceneview.math.Position
import io.github.sceneview.node.LightNode
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelLoader
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/** Data classes for simulation logic */
private data class AnimatedVehicle(
    val id: Long,
    val assetPath: String,
    val lane: Int,
    val speed: Float,
    val phase: Float,
    val scale: Float = 0.85f,
    val flip: Boolean = false,
    val currentRoadId: Long? = null
)

private data class AnimatedPedestrian(
    val id: Long,
    val assetPath: String,
    val sidewalkLane: Int,
    val speed: Float = 1.5f,
    val phase: Float = 0f,
    val scale: Float = 0.6f
)

private data class RoadSegment(
    val id: Long,
    val position: Position,
    val rotationY: Float = 0f
)

/** Advanced Gaming HUD Constants */
private object HUDColors {
    val GlassBackground = Color(0xFF0A1525).copy(alpha = 0.72f)
    val GlassBorder = Color.White.copy(alpha = 0.18f)
    val AmaravatiTeal = Color(0xFF58DBB8)
    val AmaravatiGlow = Color(0xFF58DBB8).copy(alpha = 0.25f)
    val ResourceCritical = Color(0xFFFF7043)
}

/** Simulation Helpers */
private fun lerp(a: Float, b: Float, t: Float): Float = a + t * (b - a)

private fun computeVehiclePosition(v: AnimatedVehicle, roadSegments: List<RoadSegment>): Position {
    val laneX = when (v.lane) { 0 -> -7.6f; 1 -> 0.1f; else -> 7.3f }
    val progress = v.phase
    val baseZ = -11f + progress * 27f
    val sway = sin(progress * 6.28f * 1.6) * 0.4f
    var x = laneX + (sway.toFloat() * (if (v.lane == 1) 0.6f else 1f))
    var z = baseZ + (if (v.lane == 2) (sin(progress * 3.4) * 1.8f).toFloat() else 0f)

    if (roadSegments.isNotEmpty()) {
        val targetSeg = v.currentRoadId?.let { id -> roadSegments.firstOrNull { it.id == id } }
            ?: roadSegments.minByOrNull { seg ->
                val dx = seg.position.x - x
                val dz = seg.position.z - z
                dx * dx + dz * dz
            }
        if (targetSeg != null) {
            val attract = if (v.currentRoadId != null) 0.65f else 0.32f
            val rad = Math.toRadians(targetSeg.rotationY.toDouble())
            val targetX = targetSeg.position.x + cos(rad).toFloat() * 1.2f
            val targetZ = targetSeg.position.z + sin(rad).toFloat() * 0.2f
            x = lerp(x, targetX, attract)
            z = lerp(z, targetZ, attract)
        }
    }
    return Position(x, 0.12f, z)
}

private fun shouldRenderItem(itemPos: Position, maxDistance: Float = 45f): Boolean {
    val dist2 = itemPos.x * itemPos.x + itemPos.z * itemPos.z
    return dist2 < maxDistance * maxDistance
}

private fun snapPlacement(pos: Position, gridSize: Float = 2.0f): Position {
    return Position(
        (kotlin.math.round(pos.x / gridSize) * gridSize),
        pos.y,
        (kotlin.math.round(pos.z / gridSize) * gridSize)
    )
}

private fun formatMoney(m: Long): String = when {
    m >= 1_000_000 -> "${(m / 100000) / 10.0}M"
    m >= 10_000 -> "${m / 1000}k"
    else -> m.toString()
}

@Composable
private fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(18.dp),
    border: BorderStroke? = BorderStroke(0.6.dp, HUDColors.GlassBorder),
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        color = HUDColors.GlassBackground,
        shape = shape,
        border = border,
        content = content
    )
}

@Composable
fun AmaravatiGameSurface(
    modifier: Modifier = Modifier,
    viewModel: GameViewModel = viewModel(),
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val soundManager = remember { SoundManager(context) }
    DisposableEffect(Unit) { onDispose { soundManager.release() } }
    
    val assetPaths by produceState(initialValue = emptyList<String>(), context) {
        value = GlbAssetIndex.scan(context.assets)
    }
    val buildingCatalog = remember(assetPaths) { BuildingCatalog.defaultCatalog(assetPaths) }
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val cameraManipulator = rememberCameraManipulator(orbitHomePosition = Position(-1.5f, 19f, -22f))

    val gameState by viewModel.gameState.collectAsStateWithLifecycle()
    val currentNews by viewModel.currentNews.collectAsStateWithLifecycle()
    val activeGoal by viewModel.activeGoal.collectAsStateWithLifecycle()
    val isPaused by viewModel.isPaused.collectAsStateWithLifecycle()
    val simSpeed by viewModel.simSpeed.collectAsStateWithLifecycle()
    val placedItems by viewModel.placedItems.collectAsStateWithLifecycle()

    val vehicles = remember { mutableStateListOf<AnimatedVehicle>() }
    val roadSegments = remember { mutableStateListOf<RoadSegment>() }
    val pedestrians = remember { mutableStateListOf<AnimatedPedestrian>() }

    var selectedBuilding by remember(assetPaths) { mutableStateOf(buildingCatalog.firstOrNull()) }
    var isBulldozeMode by remember { mutableStateOf(false) }
    var lastPlacementTime by remember { mutableStateOf(0L) }
    var isPhotoMode by remember { mutableStateOf(false) }
    var isSnapshotFlashing by remember { mutableStateOf(false) }
    var showHeatmap by remember { mutableStateOf(false) }

    val carAssets = remember(assetPaths) {
        val preferred = listOf("sedan.glb", "suv.glb", "taxi.glb", "hatchback-sports.glb", "delivery.glb", "van.glb", "police.glb", "truck.glb", "race.glb", "ambulance.glb")
        preferred.mapNotNull { name -> assetPaths.firstOrNull { it.endsWith(name, ignoreCase = true) } }
            .ifEmpty { assetPaths.filter { it.contains("Cars/", ignoreCase = true) && !it.contains("debris", true) && !it.contains("wheel", true) }.take(6) }
    }
    val tileAssets = remember(assetPaths) {
        listOf("tile-low.glb", "tile-high.glb", "road-straight.glb").mapNotNull { name -> assetPaths.firstOrNull { it.endsWith(name, ignoreCase = true) } }
    }
    val characterAssets = remember(assetPaths) { assetPaths.filter { it.contains("Mini Characters/character-", true) }.take(5) }

    val savedItems by viewModel.getSavedItemsFlow().collectAsStateWithLifecycle(initialValue = emptyList())
    var hasSyncLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(buildingCatalog, savedItems) {
        if (!hasSyncLoaded && buildingCatalog.isNotEmpty() && savedItems.isNotEmpty()) {
            viewModel.syncLoadedItems(savedItems, buildingCatalog)
            hasSyncLoaded = true
            viewModel.updateNews("City layout restored from secure database.")
        }
    }

    LaunchedEffect(assetPaths, buildingCatalog) {
        if (placedItems.isEmpty() && assetPaths.isNotEmpty() && buildingCatalog.isNotEmpty() && savedItems.isEmpty()) {
            val tLow = tileAssets.firstOrNull { it.contains("tile-low") } ?: tileAssets.firstOrNull() ?: ""
            val tHigh = tileAssets.firstOrNull { it.contains("tile-high") } ?: tLow
            val roadStraight = tileAssets.firstOrNull { it.contains("road-straight") } ?: tLow

            var id = 100L
            for (x in -3..3) {
                for (z in -2..4) {
                    val asset = if ((z == 1 || z == -1) && roadStraight.isNotBlank()) roadStraight else if ((x + z) % 2 == 0) tLow else tHigh
                    if (asset.isNotBlank()) {
                        viewModel.addPlacedItem(PlacedItem(id = id++, definition = BuildingDefinition("tile-$id", BuildingCategory.Infrastructure, "Pavement", asset, 0), position = Position(x * 3.8f, -0.02f, z * 3.6f), scale = 1.05f))
                    }
                }
            }
            listOf(-2, 0, 2).forEach { x ->
                val roadPos = Position(x * 3.8f + 0.2f, 0.01f, 0.8f)
                val rId = id++
                viewModel.addPlacedItem(PlacedItem(id = rId, definition = BuildingDefinition("road-$rId", BuildingCategory.Infrastructure, "Main Road", roadStraight, 900), position = roadPos, rotationY = 90f, scale = 0.98f))
                roadSegments += RoadSegment(id = rId, position = roadPos, rotationY = 90f)
            }
            buildingCatalog.filter { it.assetPath.isNotBlank() }.take(5).forEachIndexed { i, def ->
                viewModel.addPlacedItem(PlacedItem(id = id++, definition = def, position = Position((i-2)*5f, 0f, -6f), scale = 1.1f))
            }
            if (vehicles.isEmpty() && carAssets.isNotEmpty()) {
                carAssets.take(6).forEachIndexed { i, path ->
                    vehicles += AnimatedVehicle(id = 2000L + i, assetPath = path, lane = i % 3, speed = 4f + i, phase = (i * 0.15f) % 1f)
                }
            }
            if (pedestrians.isEmpty() && characterAssets.isNotEmpty()) {
                characterAssets.take(3).forEachIndexed { i, path ->
                    pedestrians += AnimatedPedestrian(id = 3000L + i, assetPath = path, sidewalkLane = i % 3, speed = 1.5f, phase = (i * 0.3f) % 1f)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        var last = System.currentTimeMillis()
        while (true) {
            delay(110L)
            val now = System.currentTimeMillis()
            val dt = ((now - last) / 1000f).coerceIn(0.03f, 0.28f)
            last = now
            viewModel.advanceSimulation(dt)

            if (!isPaused && vehicles.isNotEmpty()) {
                val speedMul = simSpeed
                val roads = roadSegments.toList()
                val newVehicles = vehicles.map { v ->
                    var currentSpeed = v.speed
                    val ahead = vehicles.firstOrNull { other -> other.lane == v.lane && other.id != v.id && other.phase > v.phase && (other.phase - v.phase) < 0.05f }
                    if (ahead != null) currentSpeed *= 0.5f
                    var nextPhase = v.phase + (currentSpeed * 0.011f * dt * speedMul)
                    if (nextPhase > 1.05f) nextPhase = -0.08f
                    var roadId = v.currentRoadId
                    if (roads.isNotEmpty() && (roadId == null || sin(now.toDouble()).toFloat() > 0.98f)) roadId = roads.random().id
                    v.copy(phase = nextPhase, currentRoadId = roadId)
                }
                vehicles.clear()
                vehicles.addAll(newVehicles)
                val newPeds = pedestrians.map { p ->
                    var nextPhase = p.phase + (p.speed * 0.008f * dt * speedMul)
                    if (nextPhase > 1.05f) nextPhase = -0.08f
                    p.copy(phase = nextPhase)
                }
                pedestrians.clear()
                pedestrians.addAll(newPeds)
            }
        }
    }

    LaunchedEffect(isPaused) {
        if (!isPaused) {
            while(true) {
                delay(14000)
                if (currentNews.contains("FIRE") || currentNews.contains("emergency")) soundManager.playDisasterSound()
            }
        }
    }

    val day = gameState.dayTime
    val isNight = day < 6.2f || day > 19.4f
    val dawnDusk = (day in 5.5f..7.2f) || (day in 18.0f..20.0f)
    LaunchedEffect(isNight) { soundManager.updateAmbiance(isNight) }

    val onBuild: (BuildingDefinition) -> Unit = { b ->
        if (gameState.money >= b.cost && b.assetPath.isNotBlank()) {
            val now = System.currentTimeMillis()
            if (now - lastPlacementTime >= 140) {
                lastPlacementTime = now
                soundManager.playBuildSound()
                val count = placedItems.size
                val px = (sin(count * 0.6) * (8f + count * 0.2f)).toFloat()
                val pz = (cos(count * 0.6) * (8f + count * 0.2f)).toFloat()
                val pos = snapPlacement(Position(px, 0f, pz))
                viewModel.addPlacedItem(PlacedItem(id = now, definition = b, position = pos, rotationY = (count * 20f) % 360f))
                viewModel.updateMoney(-b.cost)
                viewModel.updatePopulation(b.populationImpact)
                viewModel.updateHappiness(b.happinessImpact)
                viewModel.updateSustainability(b.sustainabilityImpact)
                if (b.category == BuildingCategory.Infrastructure) {
                    viewModel.updateTraffic(-11)
                    roadSegments += RoadSegment(id = now, position = Position(pos.x, 0.01f, pos.z), rotationY = (count * 20f) % 360f)
                }
            }
        }
    }

    val onDemolishLast: () -> Unit = {
        placedItems.lastOrNull { it.definition.cost > 10 }?.let { removable ->
            viewModel.removePlacedItem(removable)
            roadSegments.removeAll { it.id == removable.id }
            viewModel.updateMoney((removable.definition.cost * 0.4f).toLong())
        }
    }

    val onBuildInView: () -> Unit = {
        selectedBuilding?.let { b ->
            if (gameState.money >= b.cost) {
                val now = System.currentTimeMillis()
                val pos = snapPlacement(Position(0f, 0.02f, -10f))
                viewModel.addPlacedItem(PlacedItem(id = now, definition = b, position = pos))
                viewModel.updateMoney(-b.cost)
                viewModel.updatePopulation(b.populationImpact)
                viewModel.updateHappiness(b.happinessImpact)
                if (b.category == BuildingCategory.Infrastructure) roadSegments += RoadSegment(id = now, position = Position(pos.x, 0.01f, pos.z))
            }
        }
    }

    val bgTop = when { isNight -> Color(0xFF01060F); dawnDusk -> Color(0xFF1F2A3D); else -> Color(0xFF020D1A) }
    val bgBot = when { isNight -> Color(0xFF031526); else -> Color(0xFF081E36) }

    Box(modifier = modifier.fillMaxSize().background(Brush.verticalGradient(listOf(bgTop, bgBot)))) {
        SceneView(
            modifier = Modifier.fillMaxSize().pointerInput(Unit) { 
                detectTapGestures(onLongPress = { if (placedItems.isNotEmpty()) onDemolishLast() }) 
            },
            engine = engine, modelLoader = modelLoader, cameraManipulator = cameraManipulator
        ) {
            val sunAngle = (day - 6f) * 15f
            LightNode(
                type = LightManager.Type.DIRECTIONAL, 
                intensity = if (isNight) 2500f else 40000f, 
                color = if (isNight) Float4(0.6f, 0.7f, 1f, 1f) else Float4(1f, 0.95f, 0.85f, 1f), 
                direction = Float3(sin(Math.toRadians(sunAngle.toDouble())).toFloat() * 0.4f, -0.9f, cos(Math.toRadians(sunAngle.toDouble())).toFloat() * 0.3f)
            )
            if (isNight) {
                roadSegments.take(8).forEach { seg -> 
                    LightNode(type = LightManager.Type.POINT, intensity = 15000f, color = Float4(1f, 0.9f, 0.7f, 1f), position = Position(seg.position.x, 3.5f, seg.position.z)) 
                }
            }

            placedItems.forEach { item ->
                if (!shouldRenderItem(item.position)) return@forEach
                key(item.id) {
                    val mi = remember(item.id, item.definition.assetPath) { 
                        try { modelLoader.createModelInstance(item.definition.assetPath) } catch (_: Exception) { null } 
                    }
                    if (mi != null) {
                        ModelNode(modelInstance = mi, scaleToUnits = item.scale, centerOrigin = Position(0f, 0f, 0f), position = item.position)
                    }
                }
            }
            vehicles.forEach { v ->
                val pos = computeVehiclePosition(v, roadSegments)
                if (!shouldRenderItem(pos, 55f)) return@forEach
                key(v.id) {
                    val mi = remember(v.id, v.assetPath) { 
                        try { modelLoader.createModelInstance(v.assetPath) } catch (_: Exception) { null } 
                    }
                    if (mi != null) {
                        ModelNode(modelInstance = mi, scaleToUnits = v.scale, centerOrigin = Position(0f, 0f, 0f), position = pos)
                    }
                }
            }
        }

        // --- GLASS HUD LAYOUT ---
        Column(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            GlassTopBar(
                gameState = gameState, 
                isNight = isNight, 
                isPaused = isPaused, 
                simSpeed = simSpeed, 
                onBack = onBack, 
                onTogglePause = { viewModel.setPaused(!isPaused) }, 
                onSpeedChange = { viewModel.setSimSpeed(it) }
            )
            Spacer(Modifier.height(6.dp))
            GlassNewsTicker(news = currentNews, isNight = isNight)
        }
        
        GlassGoalTracker(
            modifier = Modifier.align(Alignment.CenterStart).padding(top = 100.dp), 
            activeGoal = activeGoal, 
            population = gameState.population
        )
        
        Column(
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp, top = 140.dp), 
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            GlassPanel(shape = CircleShape) { 
                IconButton(onClick = onBuildInView, modifier = Modifier.size(44.dp)) { 
                    Icon(Icons.Default.AddLocation, null, tint = HUDColors.AmaravatiTeal) 
                } 
            }
            GlassPanel(shape = CircleShape) { 
                IconButton(onClick = { isPhotoMode = !isPhotoMode }, modifier = Modifier.size(44.dp)) { 
                    Icon(Icons.Default.CameraAlt, null, tint = if (isPhotoMode) HUDColors.AmaravatiTeal else Color.White) 
                } 
            }
            if (!isPhotoMode) {
                GlassPanel(shape = CircleShape) { 
                    IconButton(onClick = { showHeatmap = !showHeatmap }, modifier = Modifier.size(44.dp)) { 
                        Icon(Icons.Default.Map, null, tint = if (showHeatmap) HUDColors.AmaravatiTeal else Color.White) 
                    } 
                }
                GlassPanel(shape = CircleShape) { 
                    IconButton(onClick = { isBulldozeMode = !isBulldozeMode; onDemolishLast() }, modifier = Modifier.size(44.dp)) { 
                        Icon(Icons.Default.Delete, null, tint = if (isBulldozeMode) Color.Red else Color.White) 
                    } 
                }
                Spacer(Modifier.height(8.dp))
                GlassPanel(shape = CircleShape) { 
                    IconButton(onClick = { viewModel.saveGame() }, modifier = Modifier.size(38.dp)) { 
                        Icon(Icons.Default.Save, null, tint = Color.White.copy(0.6f)) 
                    } 
                }
                GlassPanel(shape = CircleShape) { 
                    IconButton(onClick = { viewModel.loadGame() }, modifier = Modifier.size(38.dp)) { 
                        Icon(Icons.Default.Restore, null, tint = Color.White.copy(0.6f)) 
                    } 
                }
            }
        }

        if (isPhotoMode) {
            Box(Modifier.fillMaxSize()) {
                IconButton(
                    onClick = { isSnapshotFlashing = true; viewModel.updateNews("Snapshot saved.") }, 
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 50.dp).size(72.dp).background(Color.White.copy(0.15f), CircleShape).border(2.dp, Color.White, CircleShape)
                ) { 
                    Icon(Icons.Default.Camera, null, tint = Color.White, modifier = Modifier.size(36.dp)) 
                }
                Text("PHOTO MODE ACTIVE", color = Color.White.copy(0.4f), fontWeight = FontWeight.Black, fontSize = 10.sp, modifier = Modifier.align(Alignment.TopCenter).padding(top = 24.dp), letterSpacing = 4.sp)
            }
        }

        if (isSnapshotFlashing) { 
            Box(Modifier.fillMaxSize().background(Color.White))
            LaunchedEffect(Unit) { delay(80); isSnapshotFlashing = false } 
        }

        if (!isPhotoMode) {
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(), 
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                selectedBuilding?.let { b -> 
                    GlassInspectorCard(b, gameState.money >= b.cost, { onBuild(b) }, onBuildInView)
                    Spacer(Modifier.height(14.dp)) 
                }
                GlassBuildDock(
                    catalog = buildingCatalog, 
                    selected = selectedBuilding, 
                    onSelect = { selectedBuilding = it }, 
                    onQuickRoad = { buildingCatalog.firstOrNull { it.category == BuildingCategory.Infrastructure }?.let { onBuild(it) } }, 
                    pop = gameState.population
                )
            }
        }
        
        Box(modifier = Modifier.align(Alignment.TopEnd).padding(top = 78.dp, end = 18.dp)) { 
            TimeOfDayBadge(gameState.dayTime, isNight) 
        }
        
        GlassMinimap(
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 100.dp).size(96.dp), 
            items = placedItems, 
            roads = roadSegments, 
            vehicles = vehicles, 
            center = Position(0f, 0f, 0f)
        )
    }
}

// ==================== HUD COMPONENTS ====================

@Composable
private fun GlassTopBar(
    gameState: GameState, 
    isNight: Boolean, 
    isPaused: Boolean, 
    simSpeed: Float, 
    onBack: () -> Unit, 
    onTogglePause: () -> Unit, 
    onSpeedChange: (Float) -> Unit
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        GlassPanel(shape = RoundedCornerShape(14.dp)) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.ArrowBack, null, modifier = Modifier.size(16.dp), tint = Color.White)
                }
                Column { 
                    Text(gameState.cityName.uppercase(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp)
                    Text(gameState.rank.uppercase(), color = HUDColors.AmaravatiTeal, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold) 
                }
                Spacer(Modifier.width(12.dp))
                Box(Modifier.size(32.dp).background(HUDColors.AmaravatiGlow, CircleShape), contentAlignment = Alignment.Center) { 
                    Text("${gameState.sustainabilityScore}", color = HUDColors.AmaravatiTeal, fontWeight = FontWeight.Black, fontSize = 11.sp) 
                }
            }
        }
        GlassPanel(shape = RoundedCornerShape(14.dp)) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassStatPill(Icons.Default.MonetizationOn, "₹${formatMoney(gameState.money)}", Color(0xFFFFD54F))
                GlassStatPill(Icons.Default.People, "${gameState.population}", Color.White)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ResourceIcon(Icons.Default.FlashOn, gameState.power, Color(0xFFFFD54F))
                    ResourceIcon(Icons.Default.WaterDrop, gameState.water, Color(0xFF4FC3F7))
                }
                IconButton(onClick = onTogglePause, modifier = Modifier.size(24.dp)) {
                    Icon(if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause, null, modifier = Modifier.size(16.dp), tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun GlassStatPill(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) { 
        Icon(icon, null, modifier = Modifier.size(14.dp), tint = color)
        Spacer(Modifier.width(4.dp))
        Text(value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) 
    }
}

@Composable
private fun ResourceIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, level: Int, color: Color) {
    Icon(icon, null, modifier = Modifier.size(15.dp), tint = if (level < 30) HUDColors.ResourceCritical else color.copy(alpha = 0.9f))
}

@Composable
private fun GlassNewsTicker(news: String, isNight: Boolean) {
    GlassPanel(modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp), shape = RoundedCornerShape(10.dp)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).background(if (isNight) Color(0xFF4FC3F7) else Color.Red, CircleShape))
            Spacer(Modifier.width(10.dp))
            Text(news.uppercase(), color = Color.White.copy(0.9f), fontSize = 10.sp, maxLines = 1, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun GlassGoalTracker(modifier: Modifier, activeGoal: String, population: Int) {
    GlassPanel(modifier, RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { 
                Icon(Icons.Default.EmojiEvents, null, modifier = Modifier.size(16.dp), tint = HUDColors.AmaravatiTeal)
                Spacer(Modifier.width(8.dp))
                Text("OBJECTIVE", color = HUDColors.AmaravatiTeal, fontSize = 9.sp, fontWeight = FontWeight.Black) 
            }
            Text(activeGoal, color = Color.White.copy(0.85f), fontSize = 11.sp, modifier = Modifier.widthIn(max = 160.dp))
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth(0.85f).height(3.dp).background(Color.White.copy(0.1f), CircleShape)) { 
                Box(Modifier.fillMaxWidth((population.coerceIn(50, 1500) - 50) / 1450f).fillMaxHeight().background(HUDColors.AmaravatiTeal, CircleShape)) 
            }
        }
    }
}

@Composable
private fun GlassInspectorCard(building: BuildingDefinition, canAfford: Boolean, onBuild: () -> Unit, onBuildInView: () -> Unit) {
    GlassPanel(modifier = Modifier.widthIn(max = 280.dp).padding(horizontal = 10.dp), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column { 
                    Text(building.title.uppercase(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp)
                    Text(building.category.displayName.uppercase(), color = HUDColors.AmaravatiTeal, fontSize = 8.sp, fontWeight = FontWeight.Bold) 
                }
                Box(Modifier.background(HUDColors.AmaravatiGlow, CircleShape).padding(horizontal = 8.dp, vertical = 4.dp)) { 
                    Text("₹${formatMoney(building.cost)}", color = if (canAfford) HUDColors.AmaravatiTeal else HUDColors.ResourceCritical, fontSize = 10.sp, fontWeight = FontWeight.Black) 
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { 
                ImpactBadge(Icons.Default.People, building.populationImpact, Color(0xFF81D4FA))
                ImpactBadge(Icons.Default.SentimentSatisfied, building.happinessImpact, HUDColors.AmaravatiTeal)
                ImpactBadge(Icons.Default.FlashOn, building.powerImpact, Color(0xFFFFD54F)) 
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onBuild, enabled = canAfford, modifier = Modifier.weight(1.3f).height(42.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = HUDColors.AmaravatiTeal, contentColor = Color(0xFF02101F))) { 
                    Text("CONSTRUCT", fontWeight = FontWeight.Black, fontSize = 11.sp) 
                }
                OutlinedButton(onClick = onBuildInView, enabled = canAfford, modifier = Modifier.weight(1f).height(42.dp), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, HUDColors.AmaravatiTeal.copy(0.5f))) { 
                    Icon(Icons.Default.Visibility, null, modifier = Modifier.size(14.dp), tint = HUDColors.AmaravatiTeal)
                    Spacer(Modifier.width(4.dp))
                    Text("VIEW", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = HUDColors.AmaravatiTeal) 
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
private fun GlassBuildDock(catalog: List<BuildingDefinition>, selected: BuildingDefinition?, onSelect: (BuildingDefinition) -> Unit, onQuickRoad: () -> Unit, pop: Int) {
    val cats = remember(catalog) { catalog.map { it.category }.distinct() }
    var selCat by remember { mutableStateOf(cats.firstOrNull { it == BuildingCategory.Infrastructure } ?: cats.firstOrNull()) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 12.dp)) {
        GlassPanel(shape = CircleShape) {
            LazyRow(Modifier.padding(horizontal = 6.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                items(cats) { c -> 
                    Box(Modifier.clip(CircleShape).background(if (selCat == c) HUDColors.AmaravatiTeal else Color.Transparent).clickable { selCat = c }.padding(horizontal = 12.dp, vertical = 6.dp)) { 
                        Text(c.displayName.uppercase(), color = if (selCat == c) Color(0xFF02101F) else Color.White.copy(0.6f), fontSize = 9.sp, fontWeight = FontWeight.Black) 
                    } 
                }
                item {
                    Box(Modifier.size(30.dp).clip(CircleShape).background(HUDColors.AmaravatiGlow).clickable { onQuickRoad() }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.AddRoad, null, modifier = Modifier.size(16.dp), tint = HUDColors.AmaravatiTeal)
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        GlassPanel(modifier = Modifier.fillMaxWidth(0.95f), shape = RoundedCornerShape(24.dp)) {
            LazyRow(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(catalog.filter { it.category == selCat }) { b ->
                    val locked = pop < b.unlockPopulation
                    Column(
                        modifier = Modifier.width(80.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (selected?.id == b.id) HUDColors.AmaravatiGlow else Color.White.copy(0.04f))
                            .border(if (selected?.id == b.id) BorderStroke(1.5.dp, HUDColors.AmaravatiTeal) else BorderStroke(0.dp, Color.Transparent), RoundedCornerShape(16.dp))
                            .clickable(enabled = !locked) { onSelect(b) }
                            .padding(8.dp), 
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(Modifier.size(34.dp).background(Color.White.copy(0.05f), CircleShape), Alignment.Center) { 
                            Icon(Icons.Default.Apartment, null, tint = if (locked) Color.Gray else Color.White.copy(0.7f), modifier = Modifier.size(18.dp))
                            if (locked) Icon(Icons.Default.Lock, null, tint = Color.White, modifier = Modifier.size(12.dp)) 
                        }
                        Text(if (locked) "LOCKED" else b.title.uppercase(), color = if (locked) Color.Gray else Color.White, fontWeight = FontWeight.Black, fontSize = 8.sp, maxLines = 1)
                        Text(if (locked) "POP ${b.unlockPopulation}" else "₹${formatMoney(b.cost)}", color = if (locked) Color.Red.copy(0.5f) else HUDColors.AmaravatiTeal.copy(0.8f), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeOfDayBadge(dayTime: Float, isNight: Boolean) {
    Surface(color = (if (isNight) Color(0xFF0D47A1) else Color(0xFFF9A825)).copy(0.2f), shape = RoundedCornerShape(20.dp), border = BorderStroke(0.5.dp, Color.White.copy(0.2f))) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${dayTime.toInt()}:00", color = Color.White.copy(0.9f), fontSize = 9.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(4.dp))
            Text(if (isNight) "NIGHT" else "DAY", color = if (isNight) Color(0xFF90CAF9) else Color(0xFFFFF59D), fontSize = 8.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun GlassMinimap(modifier: Modifier, items: List<PlacedItem>, roads: List<RoadSegment>, vehicles: List<AnimatedVehicle>, center: Position) {
    val scale = 0.055f; val cx = 48f; val cy = 48f
    Box(modifier = modifier.clip(CircleShape).border(1.5.dp, HUDColors.AmaravatiGlow, CircleShape)) {
        Canvas(Modifier.fillMaxSize().background(HUDColors.GlassBackground)) {
            roads.forEach { s -> 
                val x = cx + (s.position.x - center.x) * scale
                val y = cy + (s.position.z - center.z) * scale
                val r = Math.toRadians(s.rotationY.toDouble())
                drawLine(HUDColors.AmaravatiTeal.copy(0.4f), Offset(x - cos(r).toFloat() * 5, y - sin(r).toFloat() * 5), Offset(x + cos(r).toFloat() * 5, y + sin(r).toFloat() * 5), 2f) 
            }
            items.filter { it.definition.cost > 50 }.forEach { i -> 
                drawCircle(HUDColors.AmaravatiTeal.copy(0.7f), 2.5f, Offset(cx + (i.position.x - center.x) * scale, cy + (i.position.z - center.z) * scale)) 
            }
            vehicles.forEach { v -> 
                val p = computeVehiclePosition(v, roads)
                drawCircle(Color.White, 1.2f, Offset(cx + (p.x - center.x) * scale, cy + (p.z - center.z) * scale)) 
            }
            drawCircle(HUDColors.AmaravatiTeal.copy(0.1f), size.width/2, style = Stroke(1f))
        }
    }
}
