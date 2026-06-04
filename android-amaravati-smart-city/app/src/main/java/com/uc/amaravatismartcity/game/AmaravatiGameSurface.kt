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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.RotateRight
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import android.util.Log
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
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberEnvironment
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

private data class RoadSegment(
    val id: Long,
    val position: Position,
    val rotationY: Float = 0f
)

private data class AmbientMover(
    val id: Long,
    val assetPath: String,
    val phase: Float,
    val speed: Float,
    val route: AmbientRoute,
    val scale: Float = 1f
)

private enum class AmbientRoute {
    River,
    Metro
}

private fun Color.toFloat4(): Float4 = Float4(red, green, blue, alpha)

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

private fun computeAmbientPosition(mover: AmbientMover): Position {
    val p = mover.phase
    return when (mover.route) {
        AmbientRoute.River -> Position(-18f + p * 36f, 0.04f, 15.5f + sin(p * 6.28f).toFloat() * 0.7f)
        AmbientRoute.Metro -> Position(-17f + p * 34f, 0.35f, -15.2f)
    }
}

private fun formatMoney(m: Long): String = when {
    m >= 1_000_000 -> "${(m / 100000) / 10.0}M"
    m >= 10_000 -> "${m / 1000}k"
    else -> m.toString()
}

private fun snapPlacement(pos: Position, gridSize: Float = 2.0f): Position {
    return Position(
        (kotlin.math.round(pos.x / gridSize) * gridSize),
        pos.y,
        (kotlin.math.round(pos.z / gridSize) * gridSize)
    )
}

private fun screenTapToGrid(offset: Offset, size: IntSize): Position {
    if (size.width <= 0 || size.height <= 0) return Position(0f, 0.02f, 0f)
    val normalizedX = (offset.x / size.width) - 0.5f
    val normalizedY = (offset.y / size.height) - 0.5f
    return snapPlacement(
        Position(
            x = normalizedX * 34f,
            y = 0.02f,
            z = normalizedY * 28f
        )
    )
}

private fun nearestBuildableItem(position: Position, items: List<PlacedItem>): PlacedItem? {
    return items
        .filter { it.definition.cost > 0 }
        .minByOrNull { item ->
            val dx = item.position.x - position.x
            val dz = item.position.z - position.z
            dx * dx + dz * dz
        }
        ?.takeIf { item ->
            kotlin.math.hypot(item.position.x - position.x, item.position.z - position.z) < 4.5f
        }
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
        val scanned = GlbAssetIndex.scan(context.assets)
        Log.d("Amaravati", "Assets found: ${scanned.size}")
        scanned.take(50).forEach { Log.d("Amaravati", " - Path: $it") }
        value = scanned
    }
    val buildingCatalog = remember(assetPaths) { BuildingCatalog.defaultCatalog(assetPaths) }
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val cameraManipulator = rememberCameraManipulator(orbitHomePosition = Position(-3.5f, 22f, -25f))

    val gameState by viewModel.gameState.collectAsStateWithLifecycle()
    val currentNews by viewModel.currentNews.collectAsStateWithLifecycle()
    val activeGoal by viewModel.activeGoal.collectAsStateWithLifecycle()
    val isPaused by viewModel.isPaused.collectAsStateWithLifecycle()
    val simSpeed by viewModel.simSpeed.collectAsStateWithLifecycle()
    val placedItems by viewModel.placedItems.collectAsStateWithLifecycle()

    val vehicles = remember { mutableStateListOf<AnimatedVehicle>() }
    val ambientMovers = remember { mutableStateListOf<AmbientMover>() }
    val roadSegments = remember { mutableStateListOf<RoadSegment>() }

    var selectedBuilding by remember(assetPaths) { mutableStateOf(buildingCatalog.firstOrNull()) }
    var isBulldozeMode by remember { mutableStateOf(false) }
    var lastPlacementTime by remember { mutableLongStateOf(0L) }
    var isPhotoMode by remember { mutableStateOf(false) }
    var isSnapshotFlashing by remember { mutableStateOf(false) }
    var showHeatmap by remember { mutableStateOf(false) }
    var heatmapMode by remember { mutableStateOf(HeatmapMode.Traffic) }
    var sceneSize by remember { mutableStateOf(IntSize.Zero) }
    var placementPreview by remember { mutableStateOf<Position?>(null) }
    var placementRotation by remember { mutableFloatStateOf(0f) }
    var inspectedItem by remember { mutableStateOf<PlacedItem?>(null) }
    var pendingBulldoze by remember { mutableStateOf<PlacedItem?>(null) }
    val roadGraph = remember(placedItems) { buildRoadGraph(placedItems) }
    val placementIsValid = selectedBuilding?.let { selected ->
        placementPreview?.let { canPlaceOnGrid(selected, it, placedItems) && gameState.money >= selected.cost } ?: true
    } ?: false

    val carAssets = remember(assetPaths) {
        val preferred = listOf("sedan.glb", "suv.glb", "taxi.glb", "hatchback-sports.glb", "delivery.glb", "van.glb", "police.glb", "truck.glb", "race.glb", "ambulance.glb")
        preferred.mapNotNull { name -> assetPaths.firstOrNull { it.endsWith(name, ignoreCase = true) } }
            .ifEmpty { assetPaths.filter { it.contains("Cars/", ignoreCase = true) && !it.contains("debris", true) && !it.contains("wheel", true) }.take(6) }
    }
    val tileAssets = remember(assetPaths) {
        listOf("tile-low.glb", "tile-high.glb", "road-straight.glb").mapNotNull { name -> assetPaths.firstOrNull { it.endsWith(name, ignoreCase = true) } }
    }
    val riverAssets = remember(assetPaths) {
        listOf("boat-speed-a.glb", "boat-sail-a.glb", "ship-small.glb", "boat-tug-a.glb")
            .mapNotNull { name -> assetPaths.firstOrNull { it.endsWith(name, ignoreCase = true) } }
    }
    val trainAssets = remember(assetPaths) {
        listOf("train-tram-modern.glb", "train-electric-subway-a.glb", "train-electric-city-a.glb")
            .mapNotNull { name -> assetPaths.firstOrNull { it.endsWith(name, ignoreCase = true) } }
    }
    val emergencyAsset = remember(assetPaths, gameState.activeEmergency) {
        val desired = when {
            gameState.activeEmergency.contains("Fire", ignoreCase = true) -> "firetruck.glb"
            gameState.activeEmergency.contains("Medical", ignoreCase = true) -> "ambulance.glb"
            gameState.activeEmergency.contains("accident", ignoreCase = true) -> "police.glb"
            else -> "ambulance.glb"
        }
        assetPaths.firstOrNull { it.endsWith(desired, ignoreCase = true) }
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
        val validCatalog = buildingCatalog.all { it.assetPath.isNotBlank() }
        Log.d("Amaravati", "Seeder Check: placed=${placedItems.size}, assets=${assetPaths.size}, catalogValid=$validCatalog")
        
        if (placedItems.isEmpty() && assetPaths.isNotEmpty() && validCatalog && savedItems.isEmpty()) {
            Log.d("Amaravati", "Starting Seeder with ${assetPaths.size} assets")
            
            val tLow = assetPaths.firstOrNull { it.contains("tile-low", true) } ?: ""
            val roadStraight = assetPaths.firstOrNull { it.contains("road-straight", true) } ?: tLow
            val roadDef = buildingCatalog.firstOrNull { it.id == "road-basic" }

            Log.d("Amaravati", "Seeder Paths: tLow=$tLow, roadStraight=$roadStraight")
            
            // Verify seeder paths
            val tLowExists = if(tLow.isNotBlank()) try { context.assets.open(tLow).use { true } } catch(_:Exception) { false } else false
            val roadExists = if(roadStraight.isNotBlank()) try { context.assets.open(roadStraight).use { true } } catch(_:Exception) { false } else false
            
            Log.d("Amaravati", "Seeder Verification: tLowExists=$tLowExists, roadExists=$roadExists")

            if (!tLowExists) {
                Log.e("Amaravati", "SEEDER ABORTED: Valid pavement tile not found in assets.")
                return@LaunchedEffect
            }

            var id = 100L
            for (x in -6..6) {
                for (z in -4..5) {
                    viewModel.addPlacedItem(PlacedItem(
                        id = id++, 
                        definition = BuildingDefinition("tile-$id", BuildingCategory.Infrastructure, "Pavement", tLow, 0), 
                        position = Position(x * 2f, -0.02f, z * 2f), 
                        scale = 1.05f
                    ))
                }
            }

            ((-5)..5).forEach { x ->
                val roadPos = Position(x * 2f, 0.01f, 0f)
                val rId = id++
                val def = roadDef?.copy(assetPath = roadStraight) ?: BuildingDefinition("road-$rId", BuildingCategory.Infrastructure, "Main Road", roadStraight, 900, roadUpgrade = RoadUpgrade.Basic)
                viewModel.addPlacedItem(PlacedItem(id = rId, definition = def, position = roadPos, rotationY = 90f, scale = 0.98f))
                roadSegments += RoadSegment(id = rId, position = roadPos, rotationY = 90f)
            }
            ((-3)..3).forEach { z ->
                val roadPos = Position(0f, 0.01f, z * 2f)
                val rId = id++
                val def = roadDef?.copy(assetPath = roadStraight) ?: BuildingDefinition("road-$rId", BuildingCategory.Infrastructure, "Main Road", roadStraight, 900, roadUpgrade = RoadUpgrade.Basic)
                viewModel.addPlacedItem(PlacedItem(id = rId, definition = def, position = roadPos, rotationY = 0f, scale = 0.98f))
                roadSegments += RoadSegment(id = rId, position = roadPos, rotationY = 0f)
            }
            
            val starterIds = listOf("residential-house", "residential-apartment", "commercial-office", "green-central-park", "utility-water-tower", "utility-solar-farm")
            val starterPositions = listOf(Position(-8f, 0f, -6f), Position(-4f, 0f, -6f), Position(4f, 0f, -6f), Position(8f, 0f, -4f), Position(-8f, 0f, 4f), Position(6f, 0f, 4f))
            
            starterIds.mapNotNull { starterId -> buildingCatalog.firstOrNull { it.id == starterId } }
                .zip(starterPositions)
                .forEach { (def, position) ->
                    Log.d("Amaravati", "Adding starter: ${def.id} with path ${def.assetPath}")
                    viewModel.addPlacedItem(PlacedItem(id = id++, definition = def, position = position, scale = 1.15f))
            }

            if (vehicles.isEmpty() && carAssets.isNotEmpty()) {
                carAssets.take(7).forEachIndexed { i, path ->
                    vehicles += AnimatedVehicle(id = 2000L + i, assetPath = path, lane = i % 3, speed = 4.5f + i, phase = (i * 0.14f) % 1f)
                }
            }
            if (ambientMovers.isEmpty()) {
                riverAssets.take(2).forEachIndexed { i, path ->
                    ambientMovers += AmbientMover(id = 3000L + i, assetPath = path, phase = i * 0.42f, speed = 0.025f + i * 0.006f, route = AmbientRoute.River, scale = 1.05f)
                }
                trainAssets.firstOrNull()?.let { path ->
                    ambientMovers += AmbientMover(id = 3100L, assetPath = path, phase = 0.1f, speed = 0.045f, route = AmbientRoute.Metro, scale = 1.1f)
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
            if (!isPaused && ambientMovers.isNotEmpty()) {
                val next = ambientMovers.map { mover ->
                    val phase = (mover.phase + mover.speed * dt * simSpeed).let { if (it > 1f) it - 1f else it }
                    mover.copy(phase = phase)
                }
                ambientMovers.clear(); ambientMovers.addAll(next)
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

    val placeBuildingAt: (BuildingDefinition, Position) -> Unit = { b, rawPosition ->
        if (b.assetPath.isNotBlank()) {
            val now = System.currentTimeMillis()
            if (now - lastPlacementTime >= 150) {
                lastPlacementTime = now
                if (viewModel.placeBuilding(b, rawPosition, placementRotation)) {
                    soundManager.playBuildSound()
                    placementPreview = null
                }
            }
        }
    }

    LaunchedEffect(placedItems) {
        roadSegments.clear()
        roadSegments.addAll(
            placedItems.filter { isRoad(it.definition) }
                .map { RoadSegment(id = it.id, position = it.position, rotationY = it.rotationY) }
        )
    }

    val demolishItem: (PlacedItem) -> Unit = { removable ->
        if (removable.definition.cost >= 8000) {
            pendingBulldoze = removable
        } else {
            viewModel.bulldoze(removable)
        }
    }

    val onBuildInView: () -> Unit = {
        selectedBuilding?.let { b ->
            val pos = placementPreview ?: Position(0f, 0.02f, -12f)
            if (canPlaceOnGrid(b, pos, placedItems)) {
                placeBuildingAt(b, pos)
            } else {
                viewModel.updateNews("Placement blocked. Choose a clear grid tile.")
            }
        }
    }

    val bgTop = when { isNight -> Color(0xFF01060F); dawnDusk -> Color(0xFF1F2A3D); else -> Color(0xFF051224) }
    val bgBot = when { isNight -> Color(0xFF031526); else -> Color(0xFF0C243D) }

    val environmentLoader = rememberEnvironmentLoader(engine)
    val environment = rememberEnvironment(environmentLoader) {
        environmentLoader.createEnvironment()!!
    }

    Box(modifier = modifier.fillMaxSize().background(Brush.verticalGradient(listOf(bgTop, bgBot)))) {
        SceneView(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { sceneSize = it }
                .pointerInput(selectedBuilding, placedItems, isBulldozeMode, sceneSize) {
                    detectTapGestures(
                        onTap = { offset ->
                            val pos = screenTapToGrid(offset, sceneSize)
                            if (isBulldozeMode) {
                                nearestBuildableItem(pos, placedItems)?.let { demolishItem(it) }
                                    ?: viewModel.updateNews("No removable structure at that grid tile.")
                                return@detectTapGestures
                            }
                            inspectedItem = nearestBuildableItem(pos, placedItems)
                            val selected = selectedBuilding ?: return@detectTapGestures
                            placementPreview = pos
                            if (gameState.money < selected.cost) {
                                viewModel.updateNews("Insufficient funds for ${selected.title}.")
                            } else if (canPlaceOnGrid(selected, pos, placedItems)) {
                                placeBuildingAt(selected, pos)
                            } else {
                                viewModel.updateNews("Placement blocked. Choose a clear grid tile.")
                            }
                        },
                        onLongPress = { offset ->
                            val pos = screenTapToGrid(offset, sceneSize)
                            nearestBuildableItem(pos, placedItems)?.let { demolishItem(it) }
                        }
                    )
                },
            engine = engine,
            modelLoader = modelLoader,
            cameraManipulator = cameraManipulator,
            environment = environment,
            isOpaque = false
        ) {
            val sunAngle = (day - 7f) * 15f
            
            LightNode(
                type = LightManager.Type.DIRECTIONAL, 
                intensity = if (isNight) 10000f else 120000f, 
                color = (if (isNight) Color(0xFFAABBFF) else Color(0xFFFFFAEE)).toFloat4(), 
                direction = Float3(sin(Math.toRadians(sunAngle.toDouble())).toFloat(), -0.8f, cos(Math.toRadians(sunAngle.toDouble())).toFloat())
            )
            
            if (isNight) {
                for (seg in roadSegments.take(12)) {
                    LightNode(
                        type = LightManager.Type.POINT, 
                        intensity = 45000f, 
                        color = Color(0xFFFFD580).toFloat4(), 
                        position = Position(seg.position.x, 4.5f, seg.position.z)
                    ) 
                }
            }

            val renderDistanceSq = when (gameState.graphicsQuality) {
                0 -> 18f * 18f
                2 -> 55f * 55f
                else -> 34f * 34f
            }
            
            for (item in placedItems) {
                val farSq = item.position.x * item.position.x + item.position.z * item.position.z
                if (item.definition.cost > 0 && farSq > renderDistanceSq) continue
                
                key(item.id) {
                    val mi = rememberModelInstance(modelLoader, item.definition.assetPath)
                    if (mi != null) {
                        val finalScale = if (item.definition.id.startsWith("tile")) 2.1f else item.scale
                        ModelNode(
                            modelInstance = mi,
                            scaleToUnits = finalScale,
                            position = item.position,
                            rotation = Position(0f, item.rotationY, 0f)
                        )
                    } else {
                        LaunchedEffect(item.definition.assetPath) {
                            val path = item.definition.assetPath
                            if (path.isBlank()) {
                                Log.e("Amaravati", "Item ${item.id} has BLANK asset path")
                            } else {
                                val exists = try {
                                    context.assets.open(path).use { true }
                                } catch (e: Exception) {
                                    false
                                }
                                if (exists) {
                                    Log.e("Amaravati", "Model LOAD failure (Engine): $path")
                                } else {
                                    Log.e("Amaravati", "Model FILE NOT FOUND: $path")
                                }
                            }
                        }
                    }
                }
            }

            // Safety Test Model: Force load a known asset if anything is wrong
            val testAsset = remember(assetPaths) { assetPaths.firstOrNull { it.contains("building-a") } }
            if (testAsset != null) {
                val testMi = rememberModelInstance(modelLoader, testAsset)
                if (testMi != null) {
                    ModelNode(
                        modelInstance = testMi,
                        scaleToUnits = 2.0f,
                        position = Position(0f, 0f, 0f)
                    )
                }
            }
            
            val maxVehicles = when (gameState.graphicsQuality) { 0 -> 4; 2 -> 18; else -> 10 }
            for (v in vehicles.take(maxVehicles).takeIf { roadSegments.isNotEmpty() }.orEmpty()) {
                val pos = computeVehiclePosition(v, roadSegments)
                key(v.id) {
                    val mi = rememberModelInstance(modelLoader, v.assetPath)
                    if (mi != null) {
                        ModelNode(
                            modelInstance = mi,
                            scaleToUnits = v.scale,
                            position = pos,
                            rotation = Position(0f, if(v.flip) 180f else 0f, 0f)
                        )
                    } else {
                        LaunchedEffect(v.assetPath) {
                            Log.e("Amaravati", "Failed to load vehicle model: ${v.assetPath}")
                        }
                    }
                }
            }
            
            val maxAmbient = when (gameState.graphicsQuality) { 0 -> 1; 2 -> 4; else -> 3 }
            val hasRiverfront = placedItems.any { it.definition.category == BuildingCategory.Riverfront } || placedItems.size < 20
            val hasMetro = placedItems.any { it.definition.id.contains("metro") || it.definition.category == BuildingCategory.Transport }
            for (mover in ambientMovers
                .filter { it.route == AmbientRoute.River && hasRiverfront || it.route == AmbientRoute.Metro && hasMetro }
                .take(maxAmbient)
            ) {
                val pos = computeAmbientPosition(mover)
                key(mover.id) {
                    val mi = rememberModelInstance(modelLoader, mover.assetPath)
                    if (mi != null) {
                        val heading = if (mover.route == AmbientRoute.River) 90f else 90f
                        ModelNode(
                            modelInstance = mi,
                            scaleToUnits = mover.scale,
                            position = pos,
                            rotation = Position(0f, heading, 0f)
                        )
                    } else {
                        LaunchedEffect(mover.assetPath) {
                            Log.e("Amaravati", "Failed to load ambient model: ${mover.assetPath}")
                        }
                    }
                }
            }
            
            if (gameState.activeEmergency.isNotBlank()) {
                val target = placedItems.firstOrNull { it.definition.cost > 0 && it.definition.category != BuildingCategory.Infrastructure }?.position ?: Position(0f, 0f, -5f)
                LightNode(
                    type = LightManager.Type.POINT, 
                    intensity = 85000f, 
                    color = Color.Red.toFloat4(), 
                    position = Position(target.x, 6.5f, target.z)
                )
                emergencyAsset?.let { asset ->
                    key("emergency-${gameState.activeEmergency}") {
                        val mi = rememberModelInstance(modelLoader, asset)
                        if (mi != null) {
                            val road = roadSegments.firstOrNull()
                            val pos = road?.position ?: Position(target.x + 2f, 0.12f, target.z)
                            ModelNode(
                                modelInstance = mi,
                                scaleToUnits = 0.95f,
                                position = pos,
                                rotation = Position(0f, 90f, 0f)
                            )
                        }
                    }
                }
            }
            
            val preview = placementPreview
            val selected = selectedBuilding
            if (preview != null && selected != null && selected.assetPath.isNotBlank()) {
                val previewInstance = rememberModelInstance(modelLoader, selected.assetPath)
                if (previewInstance != null) {
                    ModelNode(
                        modelInstance = previewInstance,
                        scaleToUnits = 1f,
                        position = Position(preview.x, 0.04f, preview.z),
                        rotation = Position(0f, placementRotation, 0f)
                    )
                }
            }
        }

        if (assetPaths.isEmpty() || placedItems.isEmpty()) {
            CityLoadingBackdrop(
                modifier = Modifier.fillMaxSize(),
                isNight = isNight,
                assetCount = assetPaths.size
            )
        } else {
            if (showHeatmap) {
                HeatmapOverlay(
                    modifier = Modifier.fillMaxSize(),
                    items = placedItems,
                    graph = roadGraph,
                    state = gameState,
                    mode = heatmapMode
                )
            }

            placementPreview?.let { preview ->
                PlacementOverlay(
                    modifier = Modifier.fillMaxSize(),
                    preview = preview,
                    sceneSize = sceneSize,
                    isValid = placementIsValid,
                    selected = selectedBuilding
                )
            }

            // --- TOP HUD SECTION ---
            Column(
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                GlassTopBar(gameState, isNight, isPaused, simSpeed, onBack, { viewModel.setPaused(!isPaused) }, { viewModel.setSimSpeed(it) })
                Spacer(Modifier.height(8.dp))
                GlassNewsTicker(currentNews, isNight)
            }
            
            // --- GOAL TRACKER ---
            GlassGoalTracker(
                modifier = Modifier.align(Alignment.TopStart).padding(top = 100.dp, start = 16.dp).width(220.dp), 
                activeGoal = activeGoal, 
                population = gameState.population
            )

            // --- RADAR MINIMAP ---
            GlassMinimap(
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 100.dp, end = 16.dp).size(110.dp), 
                items = placedItems, 
                roads = roadSegments, 
                vehicles = vehicles, 
                center = Position(0f, 0f, 0f)
            )

            SystemPanel(
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 220.dp, end = 16.dp).width(190.dp),
                state = gameState,
                graph = roadGraph,
                heatmapMode = if (showHeatmap) heatmapMode else null,
                items = placedItems
            )

            inspectedItem?.let { item ->
                InspectPanel(
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 16.dp).width(210.dp),
                    item = item,
                    onClose = { inspectedItem = null },
                    onBulldoze = { demolishItem(item) },
                    allItems = placedItems
                )
            }
            
            // --- ACTION BUTTONS ---
            Column(
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp), 
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GlassPanel(shape = CircleShape) { IconButton(onClick = onBuildInView, Modifier.size(46.dp)) { Icon(Icons.Default.AddLocation, null, tint = HUDColors.AmaravatiTeal) } }
                GlassPanel(shape = CircleShape) { IconButton(onClick = { isPhotoMode = !isPhotoMode }, Modifier.size(46.dp)) { Icon(if(isPhotoMode) Icons.Default.Close else Icons.Default.CameraAlt, null, tint = if (isPhotoMode) Color.White else HUDColors.AmaravatiTeal) } }
                if (!isPhotoMode) {
                    GlassPanel(shape = CircleShape) { IconButton(onClick = {
                        showHeatmap = true
                        heatmapMode = HeatmapMode.entries[(heatmapMode.ordinal + 1) % HeatmapMode.entries.size]
                    }, Modifier.size(46.dp)) { Icon(Icons.Default.Map, null, tint = if (showHeatmap) HUDColors.AmaravatiTeal else Color.White) } }
                    GlassPanel(shape = CircleShape) { IconButton(onClick = { placementRotation = (placementRotation + 90f) % 360f }, Modifier.size(46.dp)) { Icon(Icons.AutoMirrored.Filled.RotateRight, null, tint = Color.White) } }
                    GlassPanel(shape = CircleShape) { IconButton(onClick = { placementPreview = null; showHeatmap = false }, Modifier.size(46.dp)) { Icon(Icons.Default.Cancel, null, tint = Color.White) } }
                    GlassPanel(shape = CircleShape) { IconButton(onClick = { isBulldozeMode = !isBulldozeMode }, Modifier.size(46.dp)) { Icon(Icons.Default.Delete, null, tint = if (isBulldozeMode) Color.Red else Color.White) } }
                    GlassPanel(shape = CircleShape) { IconButton(onClick = { viewModel.clearEmergency() }, Modifier.size(46.dp)) { Icon(Icons.Default.LocalHospital, null, tint = if (gameState.activeEmergency.isNotBlank()) Color(0xFFFF7043) else Color.White) } }
                    GlassPanel(shape = CircleShape) { IconButton(onClick = { viewModel.setGraphicsQuality((gameState.graphicsQuality + 1) % 3) }, Modifier.size(46.dp)) { Icon(Icons.Default.Tune, null, tint = Color.White) } }
                    Spacer(Modifier.height(6.dp))
                    GlassPanel(shape = CircleShape) { IconButton(onClick = { viewModel.saveGame() }, Modifier.size(40.dp)) { Icon(Icons.Default.Save, null, tint = Color.White.copy(0.7f)) } }
                    GlassPanel(shape = CircleShape) { IconButton(onClick = { viewModel.loadGame() }, Modifier.size(40.dp)) { Icon(Icons.Default.Restore, null, tint = Color.White.copy(0.7f)) } }
                }
            }

            // --- PHOTO MODE ---
            if (isPhotoMode) {
                Box(Modifier.fillMaxSize()) {
                    IconButton(onClick = { isSnapshotFlashing = true; viewModel.updateNews("Snapshot saved.") }, Modifier.align(Alignment.BottomCenter).padding(bottom = 60.dp).size(80.dp).background(Color.White.copy(0.12f), CircleShape).border(2.5.dp, Color.White, CircleShape)) { Icon(Icons.Default.Camera, null, modifier = Modifier.size(40.dp), tint = Color.White) }
                    Text("PHOTO MODE", color = Color.White.copy(0.4f), fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.align(Alignment.TopCenter).padding(top = 180.dp), letterSpacing = 6.sp)
                }
            }

            if (isSnapshotFlashing) { Box(Modifier.fillMaxSize().background(Color.White)); LaunchedEffect(Unit) { delay(80); isSnapshotFlashing = false } }

            // --- BUILDING DOCK ---
            if (!isPhotoMode) {
                Column(
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 8.dp), 
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (selectedBuilding != null) {
                        val b = selectedBuilding!!
                        GlassInspectorCard(
                            building = b,
                            canAfford = gameState.money >= b.cost,
                            previewPosition = placementPreview,
                            canPlacePreview = placementPreview?.let { canPlaceOnGrid(b, it, placedItems) } ?: true,
                            onPreview = { placementPreview = Position(0f, 0.02f, -12f) },
                            onBuildInView = onBuildInView
                        )
                        Spacer(Modifier.height(16.dp))
                    }
                    GlassBuildDock(
                        catalog = buildingCatalog, 
                        selected = selectedBuilding, 
                        onSelect = { selectedBuilding = it }, 
                        onQuickRoad = {
                            buildingCatalog.firstOrNull { it.category == BuildingCategory.Infrastructure }?.let {
                                selectedBuilding = it
                                placementPreview = Position(0f, 0.02f, -12f)
                            }
                        }, 
                        pop = gameState.population
                    )
                }
            }
            
            Box(modifier = Modifier.align(Alignment.TopEnd).padding(top = 74.dp, end = 20.dp)) { 
                TimeOfDayBadge(gameState.dayTime, isNight) 
            }

            pendingBulldoze?.let { item ->
                AlertDialog(
                    onDismissRequest = { pendingBulldoze = null },
                    title = { Text("Confirm bulldoze") },
                    text = { Text("${item.definition.title} is expensive. Bulldoze for a partial refund?") },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.bulldoze(item)
                            pendingBulldoze = null
                            inspectedItem = null
                        }) { Text("Bulldoze") }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingBulldoze = null }) { Text("Cancel") }
                    }
                )
            }
        }
    }
}

// ==================== HUD COMPONENTS ====================

@Composable
private fun CityLoadingBackdrop(
    modifier: Modifier,
    isNight: Boolean,
    assetCount: Int
) {
    Box(modifier = modifier.background(Color.Black.copy(alpha = 0.18f))) {
        Canvas(Modifier.fillMaxSize()) {
            val horizon = size.height * 0.52f
            val riverTop = size.height * 0.68f
            val skyGlow = if (isNight) Color(0xFF123D63) else Color(0xFF6BB7D8)
            drawCircle(skyGlow.copy(alpha = 0.18f), size.minDimension * 0.42f, Offset(size.width * 0.72f, size.height * 0.22f))
            drawRect(Color(0xFF163248).copy(alpha = 0.55f), topLeft = Offset(0f, horizon), size = Size(size.width, riverTop - horizon))
            drawRect(Color(0xFF123D4F).copy(alpha = 0.76f), topLeft = Offset(0f, riverTop), size = Size(size.width, size.height - riverTop))

            val buildingColors = listOf(Color(0xFF9FC6D8), Color(0xFFB7A889), Color(0xFFC9D5CC), Color(0xFF8EB2A9))
            for (i in 0 until 16) {
                val w = size.width / 18f
                val h = size.height * (0.12f + (i % 5) * 0.035f)
                val x = i * w * 1.15f
                val y = horizon - h
                drawRect(buildingColors[i % buildingColors.size].copy(alpha = 0.86f), Offset(x, y), Size(w * 0.82f, h))
                repeat(3) { col ->
                    repeat((h / 28f).toInt().coerceAtLeast(2)) { row ->
                        drawRect(
                            Color(0xFFFFE082).copy(alpha = if (isNight) 0.65f else 0.22f),
                            Offset(x + 8f + col * 16f, y + 12f + row * 22f),
                            Size(6f, 8f)
                        )
                    }
                }
            }

            for (i in 0 until 6) {
                val y = riverTop + i * 28f
                drawLine(Color.White.copy(alpha = 0.08f), Offset(0f, y), Offset(size.width, y + 18f), strokeWidth = 3f)
            }
            val roadY = size.height * 0.84f
            drawLine(Color(0xFF2C3036), Offset(0f, roadY), Offset(size.width, roadY - 70f), strokeWidth = 42f)
            drawLine(Color(0xFFFFF59D).copy(alpha = 0.7f), Offset(0f, roadY - 2f), Offset(size.width, roadY - 72f), strokeWidth = 3f)
        }

        GlassPanel(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(24.dp),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = HUDColors.AmaravatiTeal,
                    strokeWidth = 3.dp
                )
                Text(
                    text = if (assetCount == 0) "LOADING CITY ASSETS" else "BUILDING STARTER CITY",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

@Composable
private fun PlacementOverlay(
    modifier: Modifier,
    preview: Position,
    sceneSize: IntSize,
    isValid: Boolean,
    selected: BuildingDefinition?
) {
    if (sceneSize.width <= 0 || sceneSize.height <= 0) return
    val x = ((preview.x / 34f) + 0.5f) * sceneSize.width
    val y = ((preview.z / 28f) + 0.5f) * sceneSize.height
    val color = if (isValid) Color(0xFF46E28F) else Color(0xFFFF4F4F)
    Canvas(modifier) {
        val radius = ((selected?.width ?: 1).coerceAtLeast(selected?.depth ?: 1) * 22f).coerceIn(20f, 70f)
        drawCircle(color.copy(alpha = 0.25f), radius, Offset(x, y))
        drawCircle(color.copy(alpha = 0.95f), radius, Offset(x, y), style = Stroke(4f))
    }
}

@Composable
private fun HeatmapOverlay(
    modifier: Modifier,
    items: List<PlacedItem>,
    graph: RoadGraph,
    state: GameState,
    mode: HeatmapMode
) {
    Canvas(modifier.background(Color.Black.copy(alpha = 0.12f))) {
        items.filter { it.definition.cost > 0 }.forEach { item ->
            val score = heatScore(mode, item, graph, state)
            val x = ((item.position.x / 34f) + 0.5f) * size.width
            val y = ((item.position.z / 28f) + 0.5f) * size.height
            val color = when (mode) {
                HeatmapMode.Happiness -> Color(0xFF56E39F)
                HeatmapMode.Power -> Color(0xFFFFD54F)
                HeatmapMode.Water -> Color(0xFF4FC3F7)
                HeatmapMode.Emergency -> Color(0xFFFF7043)
                HeatmapMode.Pollution -> Color(0xFFDCE775)
                HeatmapMode.Traffic -> Color(0xFFFF5252)
            }
            drawCircle(color.copy(alpha = 0.12f + score * 0.5f), 28f + score * 38f, Offset(x, y))
        }
    }
}

@Composable
private fun SystemPanel(modifier: Modifier, state: GameState, graph: RoadGraph, heatmapMode: HeatmapMode?, items: List<PlacedItem>) {
    val upkeep = calculateUpkeep(items)
    val tax = state.taxIncome
    val net = tax - upkeep

    // RCI demand indicators
    val resJobs = items.filter { it.definition.category == BuildingCategory.Commercial || it.definition.category == BuildingCategory.Industrial }.sumOf { it.definition.jobs }
    val commJobs = items.filter { it.definition.category == BuildingCategory.Commercial }.sumOf { it.definition.jobs }
    val indJobs = items.filter { it.definition.category == BuildingCategory.Industrial }.sumOf { it.definition.jobs }
    
    val residentialDemand = (((resJobs - state.population) * 1.5f) + (state.population - state.housingCapacity) * 2f).toInt().coerceIn(-100, 100)
    val commercialDemand = ((state.population / 2.5f - commJobs) * 2.0f).toInt().coerceIn(-100, 100)
    val industrialDemand = ((state.population / 3.0f - indJobs) * 2.0f).toInt().coerceIn(-100, 100)

    GlassPanel(modifier = modifier, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("CITY SYSTEMS", color = HUDColors.AmaravatiTeal, fontSize = 10.sp, fontWeight = FontWeight.Black)
            MiniMetric("Power", state.powerBalance)
            MiniMetric("Water", state.waterBalance)
            MiniMetric("Waste", -state.wasteBalance)
            MiniMetric("Jobs", state.jobs - state.population / 3)
            MiniMetric("Housing", state.housingCapacity - state.population)
            
            HorizontalDivider(color = Color.White.copy(alpha = 0.1f), thickness = 1.dp)
            Text("BUDGET BREAKDOWN", color = HUDColors.AmaravatiTeal, fontSize = 9.sp, fontWeight = FontWeight.Black)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Taxes", color = Color.White.copy(0.7f), fontSize = 10.sp)
                Text("+₹${formatMoney(tax)}", color = HUDColors.AmaravatiTeal, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Upkeep", color = Color.White.copy(0.7f), fontSize = 10.sp)
                Text("-₹${formatMoney(upkeep)}", color = HUDColors.ResourceCritical, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Net Flow", color = Color.White.copy(0.9f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = if (net >= 0) "+₹${formatMoney(net)}" else "-₹${formatMoney(kotlin.math.abs(net))}",
                    color = if (net >= 0) HUDColors.AmaravatiTeal else HUDColors.ResourceCritical,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black
                )
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.1f), thickness = 1.dp)
            Text("RCI DEMAND", color = HUDColors.AmaravatiTeal, fontSize = 9.sp, fontWeight = FontWeight.Black)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RciBar("R", residentialDemand, Color(0xFF4CAF50))
                RciBar("C", commercialDemand, Color(0xFF2196F3))
                RciBar("I", industrialDemand, Color(0xFFFFEB3B))
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.1f), thickness = 1.dp)
            Text("Traffic ${graph.averageCongestion}% · Routes ${graph.routeCount}", color = Color.White.copy(0.82f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text("Emergency delay ${graph.emergencyDelay}%", color = if (graph.emergencyDelay > 65) HUDColors.ResourceCritical else Color.White.copy(0.82f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            if (state.activeEmergency.isNotBlank()) {
                Text(state.activeEmergency.uppercase(), color = HUDColors.ResourceCritical, fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
            heatmapMode?.let {
                Text("HEATMAP: ${it.displayName.uppercase()}", color = HUDColors.AmaravatiTeal, fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
            Text("Quality ${listOf("Saver", "Balanced", "High")[state.graphicsQuality]}", color = Color.White.copy(0.72f), fontSize = 10.sp)
        }
    }
}

@Composable
private fun RowScope.RciBar(label: String, value: Int, color: Color) {
    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color.White.copy(0.7f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Box(Modifier.fillMaxWidth().height(10.dp).background(Color.White.copy(0.1f), RoundedCornerShape(2.dp))) {
            val progress = ((value + 100) / 200f).coerceIn(0f, 1f)
            Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(color, RoundedCornerShape(2.dp)))
        }
    }
}

@Composable
private fun MiniMetric(label: String, value: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.White.copy(0.7f), fontSize = 10.sp)
        Text(if (value >= 0) "+$value" else "$value", color = if (value >= 0) HUDColors.AmaravatiTeal else HUDColors.ResourceCritical, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ConnectionRow(label: String, connected: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.White.copy(0.7f), fontSize = 10.sp)
        Text(if (connected) "CONNECTED" else "OFFLINE", color = if (connected) HUDColors.AmaravatiTeal else HUDColors.ResourceCritical, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun InspectPanel(modifier: Modifier, item: PlacedItem, onClose: () -> Unit, onBulldoze: () -> Unit, allItems: List<PlacedItem>) {
    val roadCells = allItems.filter { isRoad(it.definition) }.map { it.position.gridCell() }.toSet()
    val itemCell = item.position.gridCell()
    
    val hasRoad = roadCells.isEmpty() || listOf(
        GridCell(itemCell.x + 1, itemCell.z),
        GridCell(itemCell.x - 1, itemCell.z),
        GridCell(itemCell.x, itemCell.z + 1),
        GridCell(itemCell.x, itemCell.z - 1)
    ).any { it in roadCells }
    
    val solarGrids = allItems.filter { it.definition.id == "utility-solar-farm" }
    val hasPower = item.definition.powerImpact >= 0 || solarGrids.isEmpty() || solarGrids.any { solar ->
        kotlin.math.hypot(item.position.x - solar.position.x, item.position.z - solar.position.z) <= solar.definition.serviceCoverage * CITY_GRID_SIZE
    }
    
    val waterTowers = allItems.filter { it.definition.id == "utility-water-tower" }
    val hasWater = item.definition.waterImpact >= 0 || waterTowers.isEmpty() || waterTowers.any { tower ->
        kotlin.math.hypot(item.position.x - tower.position.x, item.position.z - tower.position.z) <= tower.definition.serviceCoverage * CITY_GRID_SIZE
    }
    
    val connected = hasRoad && hasPower && hasWater || item.definition.category == BuildingCategory.Infrastructure

    GlassPanel(modifier = modifier, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(item.definition.title.uppercase(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
            }
            Text(item.definition.category.displayName, color = HUDColors.AmaravatiTeal, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text("Footprint ${item.definition.width}x${item.definition.depth}", color = Color.White.copy(0.74f), fontSize = 10.sp)
            Text("Jobs ${item.definition.jobs} · Housing ${item.definition.housingCapacity}", color = Color.White.copy(0.74f), fontSize = 10.sp)
            
            val taxAmount = if (connected) item.definition.taxIncome else (item.definition.taxIncome * 0.1f).toLong()
            Text(
                text = "Tax ₹${formatMoney(taxAmount)}" + if (!connected && item.definition.category != BuildingCategory.Infrastructure) " (10% unconnected)" else "", 
                color = if (connected) Color.White.copy(0.74f) else HUDColors.ResourceCritical, 
                fontSize = 10.sp
            )
            
            if (item.definition.roadUpgrade != RoadUpgrade.None) {
                Text("${item.definition.roadUpgrade.displayName} capacity ${item.definition.roadUpgrade.capacity}", color = Color.White.copy(0.74f), fontSize = 10.sp)
            }
            
            if (item.definition.category != BuildingCategory.Infrastructure) {
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f), thickness = 1.dp)
                Text("UTILITIES LINK", color = HUDColors.AmaravatiTeal, fontSize = 9.sp, fontWeight = FontWeight.Black)
                ConnectionRow("Road Access", hasRoad)
                ConnectionRow("Power Connection", hasPower)
                ConnectionRow("Water Access", hasWater)
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f), thickness = 1.dp)
            }
            
            OutlinedButton(onClick = onBulldoze, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, HUDColors.ResourceCritical.copy(0.8f))) {
                Icon(Icons.Default.Delete, null, modifier = Modifier.size(14.dp), tint = HUDColors.ResourceCritical)
                Spacer(Modifier.width(6.dp))
                Text("BULLDOZE", color = HUDColors.ResourceCritical, fontSize = 11.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun GlassTopBar(gameState: GameState, isNight: Boolean, isPaused: Boolean, simSpeed: Float, onBack: () -> Unit, onTogglePause: () -> Unit, onSpeedChange: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth(0.96f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        GlassPanel(shape = RoundedCornerShape(16.dp)) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(34.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, modifier = Modifier.size(18.dp), tint = Color.White) }
                Column { 
                    Text(gameState.cityName.uppercase(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp)
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
                IconButton(onClick = onTogglePause, modifier = Modifier.size(28.dp)) { Icon(if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause, null, modifier = Modifier.size(18.dp), tint = Color.White) }
            }
        }
    }
}

@Composable
private fun GlassStatPill(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) { 
        Icon(icon, null, modifier = Modifier.size(15.dp), tint = color)
        Spacer(Modifier.width(5.dp))
        Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold) 
    }
}

@Composable
private fun ResourceIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, level: Int, color: Color) {
    Icon(icon, null, modifier = Modifier.size(17.dp), tint = if (level < 35) HUDColors.ResourceCritical else color.copy(alpha = 0.95f))
}

@Composable
private fun GlassNewsTicker(news: String, isNight: Boolean) {
    GlassPanel(modifier = Modifier.fillMaxWidth(0.88f), shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).background(if (isNight) Color(0xFF4FC3F7) else Color.Red, CircleShape))
            Spacer(Modifier.width(12.dp))
            Text(news.uppercase(), color = Color.White.copy(0.9f), fontSize = 10.sp, maxLines = 1, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun GlassGoalTracker(modifier: Modifier, activeGoal: String, population: Int) {
    GlassPanel(modifier, RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { 
                Icon(Icons.Default.EmojiEvents, null, modifier = Modifier.size(18.dp), tint = HUDColors.AmaravatiTeal)
                Spacer(Modifier.width(10.dp))
                Text("DIRECTIVE", color = HUDColors.AmaravatiTeal, fontSize = 10.sp, fontWeight = FontWeight.Black) 
            }
            Text(activeGoal, color = Color.White.copy(0.85f), fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(4.dp).background(Color.White.copy(0.1f), CircleShape)) { 
                Box(Modifier.fillMaxWidth((population.coerceIn(50, 1500) - 50) / 1450f).fillMaxHeight().background(HUDColors.AmaravatiTeal, CircleShape)) 
            }
        }
    }
}

@Composable
private fun GlassInspectorCard(
    building: BuildingDefinition,
    canAfford: Boolean,
    previewPosition: Position?,
    canPlacePreview: Boolean,
    onPreview: () -> Unit,
    onBuildInView: () -> Unit
) {
    GlassPanel(modifier = Modifier.widthIn(max = 300.dp).padding(horizontal = 12.dp), shape = RoundedCornerShape(26.dp)) {
        Column(Modifier.padding(18.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column { 
                    Text(building.title.uppercase(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
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
                Button(onClick = onBuildInView, enabled = canAfford && canPlacePreview, modifier = Modifier.weight(1.3f).height(46.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = HUDColors.AmaravatiTeal, contentColor = Color(0xFF02101F))) { 
                    Text(if (previewPosition == null) "PLACE" else "BUILD", fontWeight = FontWeight.Black, fontSize = 12.sp) 
                }
                OutlinedButton(onClick = onPreview, enabled = canAfford, modifier = Modifier.weight(1f).height(46.dp), shape = RoundedCornerShape(14.dp), border = BorderStroke(1.2.dp, HUDColors.AmaravatiTeal.copy(0.6f))) { 
                    Icon(Icons.Default.Visibility, null, modifier = Modifier.size(16.dp), tint = HUDColors.AmaravatiTeal)
                    Spacer(Modifier.width(6.dp))
                    Text("PREVIEW", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = HUDColors.AmaravatiTeal) 
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
        GlassPanel(shape = CircleShape) {
            LazyRow(Modifier.padding(horizontal = 8.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                items(cats) { c -> 
                    val isSel = selCat == c
                    Box(Modifier.clip(CircleShape).background(if (isSel) HUDColors.AmaravatiTeal else Color.Transparent).clickable { selCat = c }.padding(horizontal = 14.dp, vertical = 8.dp)) { 
                        Text(c.displayName.uppercase(), color = if (isSel) Color(0xFF02101F) else Color.White.copy(0.6f), fontSize = 10.sp, fontWeight = FontWeight.Black) 
                    } 
                }
                item {
                    Box(Modifier.size(34.dp).clip(CircleShape).background(HUDColors.AmaravatiGlow).clickable { onQuickRoad() }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.AddRoad, null, modifier = Modifier.size(18.dp), tint = HUDColors.AmaravatiTeal)
                    }
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
                drawLine(HUDColors.AmaravatiTeal.copy(0.4f), Offset(x - cos(r).toFloat() * 6, y - sin(r).toFloat() * 6), Offset(x + cos(r).toFloat() * 6, y + sin(r).toFloat() * 6), 3f) 
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
