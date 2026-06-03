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
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.node.EnvironmentNode
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
    val GlassBackground = Color(0xFF0A1525).copy(alpha = 0.75f)
    val GlassBorder = Color.White.copy(alpha = 0.2f)
    val AmaravatiTeal = Color(0xFF58DBB8)
    val AmaravatiGlow = Color(0xFF58DBB8).copy(alpha = 0.28f)
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

private fun formatMoney(m: Long): String = when {
    m >= 1_000_000 -> "${(m / 100000) / 10.0}M"
    m >= 10_000 -> "${m / 1000}k"
    else -> m.toString()
}

@Composable
private fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(18.dp),
    border: BorderStroke? = BorderStroke(1.dp, HUDColors.GlassBorder),
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
    val environmentLoader = rememberEnvironmentLoader(engine)
    val cameraManipulator = rememberCameraManipulator(orbitHomePosition = Position(-3.5f, 22f, -25f))

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

    val savedItems by viewModel.getSavedItemsFlow().collectAsStateWithLifecycle(initialValue = emptyList())
    var hasSyncLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(buildingCatalog, savedItems) {
        if (!hasSyncLoaded && buildingCatalog.isNotEmpty() && savedItems.isNotEmpty()) {
            viewModel.syncLoadedItems(savedItems, buildingCatalog)
            hasSyncLoaded = true
            viewModel.updateNews("City layout restored.")
        }
    }

    LaunchedEffect(assetPaths, buildingCatalog) {
        if (placedItems.isEmpty() && assetPaths.isNotEmpty() && buildingCatalog.isNotEmpty() && savedItems.isEmpty()) {
            val tLow = tileAssets.firstOrNull { it.contains("tile-low") } ?: tileAssets.firstOrNull() ?: ""
            val roadStraight = tileAssets.firstOrNull { it.contains("road-straight") } ?: tLow

            var id = 100L
            for (x in -3..3) {
                for (z in -2..4) {
                    val asset = if ((z == 1 || z == -1) && roadStraight.isNotBlank()) roadStraight else tLow
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
                viewModel.addPlacedItem(PlacedItem(id = id++, definition = def, position = Position((i-2)*5f, 0f, -6f), scale = 1.15f))
            }
            if (vehicles.isEmpty() && carAssets.isNotEmpty()) {
                carAssets.take(7).forEachIndexed { i, path ->
                    vehicles += AnimatedVehicle(id = 2000L + i, assetPath = path, lane = i % 3, speed = 4.5f + i, phase = (i * 0.14f) % 1f)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        var last = System.currentTimeMillis()
        while (true) {
            delay(100L)
            val now = System.currentTimeMillis()
            val dt = ((now - last) / 1000f).coerceIn(0.01f, 0.2f)
            last = now
            viewModel.advanceSimulation(dt)

            if (!isPaused && vehicles.isNotEmpty()) {
                val speedMul = simSpeed
                val roads = roadSegments.toList()
                val newVehicles = vehicles.map { v ->
                    var currentSpeed = v.speed
                    val ahead = vehicles.firstOrNull { other -> other.lane == v.lane && other.id != v.id && other.phase > v.phase && (other.phase - v.phase) < 0.05f }
                    if (ahead != null) currentSpeed *= 0.4f
                    var nextPhase = v.phase + (currentSpeed * 0.012f * dt * speedMul)
                    if (nextPhase > 1.05f) nextPhase = -0.08f
                    var roadId = v.currentRoadId
                    if (roads.isNotEmpty() && (roadId == null || (now % 5000 < 100))) roadId = roads.random().id
                    v.copy(phase = nextPhase, currentRoadId = roadId)
                }
                vehicles.clear(); vehicles.addAll(newVehicles)
            }
        }
    }

    LaunchedEffect(isPaused) {
        if (!isPaused) {
            while(true) {
                delay(15000)
                if (currentNews.contains("FIRE") || currentNews.contains("emergency")) soundManager.playDisasterSound()
            }
        }
    }

    val day = gameState.dayTime
    val isNight = day < 6.5f || day > 19.5f
    val dawnDusk = (day in 6.0f..7.5f) || (day in 18.5f..20.0f)
    LaunchedEffect(isNight) { soundManager.updateAmbiance(isNight) }

    val onBuild: (BuildingDefinition) -> Unit = { b ->
        if (gameState.money >= b.cost && b.assetPath.isNotBlank()) {
            val now = System.currentTimeMillis()
            if (now - lastPlacementTime >= 150) {
                lastPlacementTime = now
                soundManager.playBuildSound()
                val count = placedItems.size
                val px = (sin(count * 0.7) * (9f + count * 0.1f)).toFloat()
                val pz = (cos(count * 0.7) * (9f + count * 0.1f)).toFloat()
                val pos = snapPlacement(Position(px, 0f, pz))
                viewModel.addPlacedItem(PlacedItem(id = now, definition = b, position = pos, rotationY = (count * 15f) % 360f))
                viewModel.updateMoney(-b.cost)
                viewModel.updatePopulation(b.populationImpact)
                viewModel.updateHappiness(b.happinessImpact)
                if (b.category == BuildingCategory.Infrastructure) {
                    roadSegments += RoadSegment(id = now, position = Position(pos.x, 0.01f, pos.z), rotationY = (count * 15f) % 360f)
                }
            }
        }
    }

    val onDemolishLast: () -> Unit = {
        placedItems.lastOrNull { it.definition.cost > 0 }?.let { removable ->
            viewModel.removePlacedItem(removable)
            roadSegments.removeAll { it.id == removable.id }
            viewModel.updateMoney((removable.definition.cost * 0.5f).toLong())
        }
    }

    val onBuildInView: () -> Unit = {
        selectedBuilding?.let { b ->
            if (gameState.money >= b.cost) {
                val now = System.currentTimeMillis()
                val pos = snapPlacement(Position(0f, 0.02f, -12f))
                viewModel.addPlacedItem(PlacedItem(id = now, definition = b, position = pos))
                viewModel.updateMoney(-b.cost)
                if (b.category == BuildingCategory.Infrastructure) roadSegments += RoadSegment(id = now, position = Position(pos.x, 0.01f, pos.z))
            }
        }
    }

    val bgTop = when { isNight -> Color(0xFF01060F); dawnDusk -> Color(0xFF1F2A3D); else -> Color(0xFF051224) }
    val bgBot = when { isNight -> Color(0xFF031526); else -> Color(0xFF0C243D) }

    Box(modifier = modifier.fillMaxSize().background(Brush.verticalGradient(listOf(bgTop, bgBot)))) {
        SceneView(
            modifier = Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onLongPress = { onDemolishLast() }) },
            engine = engine, modelLoader = modelLoader, cameraManipulator = cameraManipulator,
            isTransparent = true
        ) {
            val sunAngle = (day - 7f) * 15f
            LightNode(
                type = LightManager.Type.DIRECTIONAL, 
                intensity = if (isNight) 3000f else 65000f, 
                color = if (isNight) Float4(0.6f, 0.7f, 1f, 1f) else Float4(1f, 0.98f, 0.9f, 1f), 
                direction = Float3(sin(Math.toRadians(sunAngle.toDouble())).toFloat(), -0.8f, cos(Math.toRadians(sunAngle.toDouble())).toFloat())
            )
            
            // Environment IBL
            val environment = remember(isNight) { environmentLoader.createHDRLEnvironment(assetFileLocation = "models/environment.hdr") }
            if (environment != null) EnvironmentNode(environment)

            if (isNight) {
                roadSegments.take(12).forEach { seg -> 
                    LightNode(type = LightManager.Type.POINT, intensity = 25000f, color = Float4(1f, 0.85f, 0.6f, 1f), position = Position(seg.position.x, 4.5f, seg.position.z)) 
                }
            }

            placedItems.forEach { item ->
                key(item.id) {
                    val mi = remember(item.id, item.definition.assetPath) { 
                        try { modelLoader.createModelInstance(item.definition.assetPath) } catch (_: Exception) { null } 
                    }
                    if (mi != null) {
                        ModelNode(modelInstance = mi, scaleToUnits = item.scale, centerOrigin = Position(0f, 0f, 0f), position = item.position, rotation = Position(0f, item.rotationY, 0f))
                    }
                }
            }
            vehicles.forEach { v ->
                val pos = computeVehiclePosition(v, roadSegments)
                key(v.id) {
                    val mi = remember(v.id, v.assetPath) { try { modelLoader.createModelInstance(v.assetPath) } catch (_: Exception) { null } }
                    if (mi != null) ModelNode(modelInstance = mi, scaleToUnits = v.scale, centerOrigin = Position(0f, 0f, 0f), position = pos, rotation = Position(0f, if(v.flip) 180f else 0f, 0f))
                }
            }
        }

        // --- TOP HUD SECTION ---
        Column(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            GlassTopBar(gameState, isNight, isPaused, simSpeed, onBack, { viewModel.setPaused(!isPaused) }, { viewModel.setSimSpeed(it) })
            Spacer(Modifier.height(8.dp))
            GlassNewsTicker(currentNews, isNight)
        }
        
        // --- GOAL TRACKER (Top-Start to avoid overlap) ---
        GlassGoalTracker(
            modifier = Modifier.align(Alignment.TopStart).padding(top = 100.dp, start = 16.dp), 
            activeGoal = activeGoal, 
            population = gameState.population
        )

        // --- RADAR MINIMAP (Top-End to avoid overlap) ---
        GlassMinimap(
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 100.dp, end = 16.dp).size(110.dp), 
            items = placedItems, 
            roads = roadSegments, 
            vehicles = vehicles, 
            center = Position(0f, 0f, 0f)
        )
        
        // --- ACTION BUTTONS (Right Edge) ---
        Column(
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp), 
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GlassPanel(shape = CircleShape) { IconButton(onClick = onBuildInView, Modifier.size(46.dp)) { Icon(Icons.Default.AddLocation, null, tint = HUDColors.AmaravatiTeal) } }
            GlassPanel(shape = CircleShape) { IconButton(onClick = { isPhotoMode = !isPhotoMode }, Modifier.size(46.dp)) { Icon(if(isPhotoMode) Icons.Default.Close else Icons.Default.CameraAlt, null, tint = if (isPhotoMode) Color.White else HUDColors.AmaravatiTeal) } }
            if (!isPhotoMode) {
                GlassPanel(shape = CircleShape) { IconButton(onClick = { showHeatmap = !showHeatmap }, Modifier.size(46.dp)) { Icon(Icons.Default.Map, null, tint = if (showHeatmap) HUDColors.AmaravatiTeal else Color.White) } }
                GlassPanel(shape = CircleShape) { IconButton(onClick = { isBulldozeMode = !isBulldozeMode; onDemolishLast() }, Modifier.size(46.dp)) { Icon(Icons.Default.Delete, null, tint = if (isBulldozeMode) Color.Red else Color.White) } }
                Spacer(Modifier.height(6.dp))
                GlassPanel(shape = CircleShape) { IconButton(onClick = { viewModel.saveGame() }, Modifier.size(40.dp)) { Icon(Icons.Default.Save, null, tint = Color.White.copy(0.7f)) } }
                GlassPanel(shape = CircleShape) { IconButton(onClick = { viewModel.loadGame() }, Modifier.size(40.dp)) { Icon(Icons.Default.Restore, null, tint = Color.White.copy(0.7f)) } }
            }
        }

        // --- PHOTO MODE ---
        if (isPhotoMode) {
            Box(Modifier.fillMaxSize()) {
                IconButton(onClick = { isSnapshotFlashing = true; viewModel.updateNews("Archive entry created.") }, Modifier.align(Alignment.BottomCenter).padding(bottom = 60.dp).size(80.dp).background(Color.White.copy(0.12f), CircleShape).border(2.5.dp, Color.White, CircleShape)) { Icon(Icons.Default.Camera, null, tint = Color.White, Modifier.size(40.dp)) }
                Text("C I N E M A T I C   M O D E", color = Color.White.copy(0.4f), fontWeight = FontWeight.Light, fontSize = 11.sp, modifier = Modifier.align(Alignment.TopCenter).padding(top = 180.dp), letterSpacing = 6.sp)
            }
        }

        if (isSnapshotFlashing) { Box(Modifier.fillMaxSize().background(Color.White)); LaunchedEffect(Unit) { delay(80); isSnapshotFlashing = false } }

        // --- BUILDING DOCK ---
        if (!isPhotoMode) {
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 8.dp), 
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                selectedBuilding?.let { b -> 
                    GlassInspectorCard(b, gameState.money >= b.cost, { onBuild(b) }, onBuildInView)
                    Spacer(Modifier.height(16.dp)) 
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
        
        Box(modifier = Modifier.align(Alignment.TopEnd).padding(top = 74.dp, end = 20.dp)) { 
            TimeOfDayBadge(gameState.dayTime, isNight) 
        }
    }
}

// ==================== HUD COMPONENT IMPLEMENTATIONS ====================

@Composable
private fun GlassTopBar(gameState: GameState, isNight: Boolean, isPaused: Boolean, simSpeed: Float, onBack: () -> Unit, onTogglePause: () -> Unit, onSpeedChange: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth(0.96f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        GlassPanel(shape = RoundedCornerShape(16.dp)) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(34.dp)) { Icon(Icons.Default.ArrowBack, null, tint = Color.White, Modifier.size(18.dp)) }
                Column { 
                    Text(gameState.cityName.uppercase(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp, letterSpacing = 1.sp)
                    Text(gameState.rank.uppercase(), color = HUDColors.AmaravatiTeal, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold) 
                }
                Spacer(Modifier.width(14.dp))
                Box(Modifier.size(34.dp).background(HUDColors.AmaravatiGlow, CircleShape), contentAlignment = Alignment.Center) { 
                    Text("${gameState.sustainabilityScore}", color = HUDColors.AmaravatiTeal, fontWeight = FontWeight.Black, fontSize = 12.sp) 
                }
            }
        }
        GlassPanel(shape = RoundedCornerShape(16.dp)) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassStatPill(Icons.Default.MonetizationOn, "₹${formatMoney(gameState.money)}", Color(0xFFFFD54F))
                GlassStatPill(Icons.Default.People, "${gameState.population}", Color.White)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ResourceIcon(Icons.Default.FlashOn, gameState.power, Color(0xFFFFD54F))
                    ResourceIcon(Icons.Default.WaterDrop, gameState.water, Color(0xFF4FC3F7))
                }
                VerticalDivider(Modifier.height(18.dp).width(0.5.dp), color = HUDColors.GlassBorder)
                IconButton(onClick = onTogglePause, modifier = Modifier.size(28.dp)) { Icon(if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause, null, tint = Color.White, Modifier.size(18.dp)) }
            }
        }
    }
}

@Composable
private fun GlassStatPill(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) { 
        Icon(icon, null, tint = color, Modifier.size(15.dp))
        Spacer(Modifier.width(5.dp))
        Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold) 
    }
}

@Composable
private fun ResourceIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, level: Int, color: Color) {
    Icon(icon, null, tint = if (level < 35) HUDColors.ResourceCritical else color.copy(alpha = 0.95f), Modifier.size(17.dp))
}

@Composable
private fun GlassNewsTicker(news: String, isNight: Boolean) {
    GlassPanel(modifier = Modifier.fillMaxWidth(0.88f), shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).background(if (isNight) Color(0xFF4FC3F7) else Color.Red, CircleShape))
            Spacer(Modifier.width(12.dp))
            Text(news.uppercase(), color = Color.White.copy(0.9f), fontSize = 10.sp, maxLines = 1, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun GlassGoalTracker(modifier: Modifier, activeGoal: String, population: Int) {
    GlassPanel(modifier.widthIn(max = 190.dp), RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { 
                Icon(Icons.Default.EmojiEvents, null, tint = HUDColors.AmaravatiTeal, Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text("DIRECTIVE", color = HUDColors.AmaravatiTeal, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) 
            }
            Spacer(Modifier.height(8.dp))
            Text(activeGoal, color = Color.White.copy(0.85f), fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(10.dp))
            val progress = (population.coerceIn(50, 1500) - 50) / 1450f
            Box(Modifier.fillMaxWidth().height(4.dp).background(Color.White.copy(0.1f), CircleShape)) { 
                Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(HUDColors.AmaravatiTeal, CircleShape)) 
            }
        }
    }
}

@Composable
private fun GlassInspectorCard(building: BuildingDefinition, canAfford: Boolean, onBuild: () -> Unit, onBuildInView: () -> Unit) {
    GlassPanel(modifier = Modifier.widthIn(max = 300.dp).padding(horizontal = 12.dp), shape = RoundedCornerShape(26.dp)) {
        Column(Modifier.padding(18.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column { 
                    Text(building.title.uppercase(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp, letterSpacing = 0.5.sp)
                    Text(building.category.displayName.uppercase(), color = HUDColors.AmaravatiTeal, fontSize = 9.sp, fontWeight = FontWeight.Bold) 
                }
                Box(Modifier.background(if(canAfford) HUDColors.AmaravatiGlow else Color.Red.copy(0.1f), CircleShape).padding(horizontal = 10.dp, vertical = 5.dp)) { 
                    Text("₹${formatMoney(building.cost)}", color = if (canAfford) HUDColors.AmaravatiTeal else HUDColors.ResourceCritical, fontSize = 11.sp, fontWeight = FontWeight.Black) 
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { 
                ImpactBadge(Icons.Default.People, building.populationImpact, Color(0xFF81D4FA))
                ImpactBadge(Icons.Default.SentimentSatisfied, building.happinessImpact, HUDColors.AmaravatiTeal)
                ImpactBadge(Icons.Default.FlashOn, building.powerImpact, Color(0xFFFFD54F)) 
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onBuild, enabled = canAfford, modifier = Modifier.weight(1.3f).height(46.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = HUDColors.AmaravatiTeal, contentColor = Color(0xFF02101F), disabledContainerColor = Color.White.copy(0.05f))) { 
                    Text("CONSTRUCT", fontWeight = FontWeight.Black, fontSize = 12.sp) 
                }
                OutlinedButton(onClick = onBuildInView, enabled = canAfford, modifier = Modifier.weight(1f).height(46.dp), shape = RoundedCornerShape(14.dp), border = BorderStroke(1.2.dp, HUDColors.AmaravatiTeal.copy(0.6f))) { 
                    Icon(Icons.Default.Visibility, null, modifier = Modifier.size(16.dp), tint = HUDColors.AmaravatiTeal)
                    Spacer(Modifier.width(6.dp))
                    Text("VIEW", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = HUDColors.AmaravatiTeal) 
                }
            }
        }
    }
}

@Composable
private fun ImpactBadge(icon: androidx.compose.ui.graphics.vector.ImageVector, value: Int, color: Color) {
    if (value == 0) return
    Surface(color = color.copy(0.15f), shape = RoundedCornerShape(8.dp)) { 
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) { 
            Icon(icon, null, tint = color, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(4.dp))
            Text(if (value > 0) "+$value" else "$value", color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold) 
        } 
    }
}

@Composable
private fun GlassBuildDock(catalog: List<BuildingDefinition>, selected: BuildingDefinition?, onSelect: (BuildingDefinition) -> Unit, onQuickRoad: () -> Unit, pop: Int) {
    val cats = remember(catalog) { catalog.map { it.category }.distinct() }
    var selCat by remember { mutableStateOf(cats.firstOrNull { it == BuildingCategory.Infrastructure } ?: cats.firstOrNull()) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 12.dp)) {
        GlassPanel(shape = CircleShape, border = BorderStroke(0.5.dp, Color.White.copy(0.1f))) {
            LazyRow(Modifier.padding(horizontal = 8.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                items(cats) { c -> 
                    val isSel = selCat == c
                    Box(Modifier.clip(CircleShape).background(if (isSel) HUDColors.AmaravatiTeal else Color.Transparent).clickable { selCat = c }.padding(horizontal = 14.dp, vertical = 8.dp)) { 
                        Text(c.displayName.uppercase(), color = if (isSel) Color(0xFF02101F) else Color.White.copy(0.6f), fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp) 
                    } 
                }
                Box(Modifier.size(34.dp).clip(CircleShape).background(HUDColors.AmaravatiGlow).clickable { onQuickRoad() }, contentAlignment = Alignment.Center) { 
                    Icon(Icons.Default.AddRoad, null, tint = HUDColors.AmaravatiTeal, Modifier.size(18.dp)) 
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        GlassPanel(modifier = Modifier.fillMaxWidth(0.94f), shape = RoundedCornerShape(28.dp)) {
            LazyRow(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(catalog.filter { it.category == selCat }) { b ->
                    val locked = pop < b.unlockPopulation
                    val isSelected = selected?.id == b.id
                    Column(
                        modifier = Modifier.width(90.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (isSelected) HUDColors.AmaravatiGlow else Color.White.copy(0.04f))
                            .border(if (isSelected) BorderStroke(2.dp, HUDColors.AmaravatiTeal) else BorderStroke(0.dp, Color.Transparent), RoundedCornerShape(18.dp))
                            .clickable(enabled = !locked) { onSelect(b) }
                            .padding(10.dp), 
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(Modifier.size(38.dp).background(Color.White.copy(0.06f), CircleShape), Alignment.Center) { 
                            Icon(Icons.Default.Apartment, null, tint = if (locked) Color.Gray else if(isSelected) HUDColors.AmaravatiTeal else Color.White.copy(0.75f), modifier = Modifier.size(20.dp))
                            if (locked) Icon(Icons.Default.Lock, null, tint = Color.White, modifier = Modifier.size(14.dp)) 
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(if (locked) "LOCKED" else b.title.uppercase(), color = if (locked) Color.Gray else Color.White, fontWeight = FontWeight.Black, fontSize = 9.sp, maxLines = 1, textAlign = TextAlign.Center)
                        Text(if (locked) "POP ${b.unlockPopulation}" else "₹${formatMoney(b.cost)}", color = if (locked) Color.Red.copy(0.6f) else HUDColors.AmaravatiTeal.copy(0.9f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeOfDayBadge(dayTime: Float, isNight: Boolean) {
    Surface(color = (if (isNight) Color(0xFF0D47A1) else Color(0xFFF9A825)).copy(0.25f), shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, Color.White.copy(0.25f))) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${dayTime.toInt()}:00", color = Color.White.copy(0.9f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(6.dp))
            Text(if (isNight) "NIGHT" else "DAY", color = if (isNight) Color(0xFF90CAF9) else Color(0xFFFFF59D), fontSize = 9.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun GlassMinimap(modifier: Modifier, items: List<PlacedItem>, roads: List<RoadSegment>, vehicles: List<AnimatedVehicle>, center: Position) {
    val scale = 0.055f; val cx = 55f; val cy = 55f
    Box(modifier = modifier.clip(CircleShape).border(2.dp, HUDColors.AmaravatiTeal.copy(0.4f), CircleShape)) {
        Canvas(Modifier.fillMaxSize().background(HUDColors.GlassBackground)) {
            roads.forEach { s -> 
                val x = cx + (s.position.x - center.x) * scale
                val y = cy + (s.position.z - center.z) * scale
                val r = Math.toRadians(s.rotationY.toDouble())
                drawLine(HUDColors.AmaravatiTeal.copy(0.5f), Offset(x - cos(r).toFloat() * 6, y - sin(r).toFloat() * 6), Offset(x + cos(r).toFloat() * 6, y + sin(r).toFloat() * 6), 3f) 
            }
            items.filter { it.definition.cost > 50 }.forEach { i -> 
                drawCircle(HUDColors.AmaravatiTeal.copy(0.8f), 3.5f, Offset(cx + (i.position.x - center.x) * scale, cy + (i.position.z - center.z) * scale)) 
            }
            vehicles.forEach { v -> 
                val p = computeVehiclePosition(v, roads)
                drawCircle(Color.White, 1.8f, Offset(cx + (p.x - center.x) * scale, cy + (p.z - center.z) * scale)) 
            }
            drawCircle(HUDColors.AmaravatiTeal.copy(0.12f), size.width/2, style = Stroke(1.5f))
        }
    }
}
