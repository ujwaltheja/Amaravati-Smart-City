# AGENTS.md — Amaravati Smart City

Android city-builder game (Kotlin + Jetpack Compose + SceneView/Filament 3D). All source lives under `android-amaravati-smart-city/app/src/main/java/com/uc/amaravatismartcity/`.

## Build & Test

```powershell
# from repo root (Windows)
cd android-amaravati-smart-city
.\gradlew.bat assembleDebug                          # build debug APK
.\gradlew.bat test                                   # all unit tests
.\gradlew.bat testDebugUnitTest --tests com.uc.amaravatismartcity.ExampleUnitTest  # single class
.\gradlew.bat connectedDebugAndroidTest              # instrumented (device/emulator required)
.\gradlew.bat lint
```

On macOS/Linux replace `.\gradlew.bat` with `./gradlew`.

## Architecture Overview

```
MainActivity  →  NavDisplay (Navigation 3)
                 ├── AppRoute.MainMenu  → MainMenuScreen
                 ├── AppRoute.CityView  → CityViewScreen → AmaravatiGameSurface
                 └── AppRoute.About     → AboutScreen
```

- **Navigation**: `navigation/AppNavigation.kt` — `AppRoute` is a `@Serializable sealed interface : NavKey`. Add new destinations here; register them in `MainActivity`'s `entryProvider` block.
- **Game state**: `game/GameState.kt` is an immutable `data class`. The only place allowed to mutate it is `game/GameViewModel.kt` via `_gameState.update { it.copy(...) }`.
- **Simulation tick**: `GameViewModel.advanceSimulation(deltaSeconds: Float)` drives all per-frame city logic (income, drifts, events, goal checks). Called from `AmaravatiGameSurface`.
- **City logic (pure functions)**: `game/CitySystems.kt` — grid snapping, road graph BFS, balance calculations, service coverage, heatmap scoring, missions list, and rank thresholds. No side effects; safe to unit test directly.
- **3D rendering**: `game/AmaravatiGameSurface.kt` (1 071 lines) — SceneView `4.17.0` + Filament. Contains all Compose UI overlays for the gameplay screen. Avoid splitting unless necessary.
- **Persistence**: Room `v2.7.0`, DB version **3**. Two tables: `game_state` (single row) and `placed_items`. Always add an explicit `Migration` object in `GameDatabase.kt` when changing an entity — destructive migration is disabled.

## Key Patterns & Conventions

### GLB Asset Resolution
Assets live at `app/src/main/assets/models/` mirroring the top-level `Asset/` folder structure (Cars/, City-Commercial/, Roads and Bridges/, etc.). Paths are **never hardcoded** in `BuildingDefinition`; instead `BuildingCatalog.defaultCatalog(assetPaths)` receives a runtime list from `GlbAssetIndex.scan(assetManager)` and resolves them with a `pick()` scorer:

```kotlin
pick("building-a", "building-b", preferFolder = "City-Commercial")
// returns the best-matching path from the scanned list
```

When adding a new building, add a `BuildingDefinition` entry in `models/BuildingCatalog.kt` and use `pick()` with tokens matching actual `.glb` filenames.

### Grid System
`CITY_GRID_SIZE = 2f` (world units per cell). Use `snapToGrid(position)` and `position.gridCell()` for all placement logic. Collision is checked by `canPlaceOnGrid(definition, position, placedItems)` using `footprintCells()`.

### Building Definitions
`models/BuildingDefinition.kt` — `id` is the stable DB key; changing it breaks save compatibility. `assetPath` is the resolved `.glb` asset-relative string. Impacts are **signed deltas** (positive `powerImpact` = production, negative = consumption).

### State Mutation Checklist
When a building is placed, these calls happen in order (see `GameViewModel.placeBuilding`):
1. `addPlacedItem()` → updates `_placedItems`
2. `updateMoney(-cost)`, `updatePopulation(...)`, `updateHappiness(...)`, `updateSustainability(...)`
3. `refreshCitySystems()` — recalculates all balance/graph fields from scratch

### Persistence Round-Trip
`saveGame()` serialises both `GameState` fields and `_placedItems` to Room. On restore, `loadGame()` only rehydrates `GameState`; `syncLoadedItems(entities, catalog)` must be called separately (from `CityViewScreen`) to reconstruct `PlacedItem` objects by matching `buildingId` back to `BuildingCatalog`.

### City Rank & Missions
`rankForPopulation(Int)` in `CitySystems.kt` maps population → rank string (e.g., `"Rising Settlement"` → `"Smart Capital"`). The 7 sequential `cityMissions` list drives the in-game goal system; `activeMissionIndex` in `GameState` tracks progress.

### sustainabilityScore dual role
`sustainabilityScore` is both a building-impact accumulator and the recalculated *Smart Score* (`recalculateSmartScore()` overwrites it from happiness/pollution/traffic). Always call `recalculateSmartScore()` after event processing.

## Key Files Reference

| File | Purpose |
|---|---|
| `game/GameState.kt` | Immutable state snapshot; all values are bounded |
| `game/GameViewModel.kt` | Single mutation point; `advanceSimulation()` is the tick |
| `game/CitySystems.kt` | Pure game logic — grid, road BFS, balances, missions |
| `game/AmaravatiGameSurface.kt` | SceneView 3D surface + all gameplay Compose UI |
| `models/BuildingCatalog.kt` | All building definitions with asset path resolution |
| `models/BuildingDefinition.kt` | Schema for a building type; `RoadUpgrade` enum |
| `models/GlbAssetIndex.kt` | Runtime `.glb` scanner under `assets/models/` |
| `db/GameDatabase.kt` | Room DB; add migrations here for every schema change |
| `navigation/AppNavigation.kt` | Sealed `AppRoute` destinations |

